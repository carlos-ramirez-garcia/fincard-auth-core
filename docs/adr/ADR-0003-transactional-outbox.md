# ADR-0003 — Transactional Outbox para la emisión de eventos Kafka

- **Estado:** Aceptado
- **Fecha:** 2026-11-01

## Contexto

Al aprobarse una transacción se debe publicar un evento a Kafka. El problema
clásico de dualidad: COMMIT de DB y envío a Kafka no comparten transacción.
- COMMIT primero → si Kafka falla, el evento se pierde.
- Kafka primero → si el COMMIT falla, se emitió un evento de algo que no ocurrió.

## Decisión

**Outbox**, en dos pasos:

1. En la **misma transacción** del procedure (y del resto de escrituras), la
   fila se inserta en `event_outbox` con `status='PENDING'`.
2. Un scheduler (`@Scheduled(fixedDelay=10s)`) toma los `PENDING` (lote
   acotado), los publica a Kafka y marca `SENT`.

Propiedades:
- **At-least-once:** puede publicarse un evento dos veces (cayó el ACK); el
  consumidor DEDUPLICA por `transactionId` (clave de idempotencia estable
  entre reintentos; el `eventId` sí cambia en cada intento).
- **Solo se emiten eventos APPROVED** (las declinadas quedan en la bitácora
  Oracle — ver contrato `docs/events/transaction-events.md`).

## Consecuencias

+: Ningún evento se pierde ni se emite "falsa".
+: El flujo de autorización NO depende de Kafka estar vivo (degradación
  elegante: eventos quedan PENDING y salen luego).
−: Retardo de hasta ~10 s en el evento si Kafka está caído (aceptable para
  auditoría; con CDC/Debezium sería near-real-time).

−: Registra la tabla outbox (creciente si Kafka cae de largo: en prod, purga
  de `SENT` viejos por job de limpieza).
Alternativa descartada: publicar directo `KafkaTemplate.send()` tras el COMMIT
(se pierde el evento si Kafka falla; viola la garantía de auditoría).
