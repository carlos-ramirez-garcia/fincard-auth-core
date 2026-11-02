# Contrato de evento: `transaction-events`

Interfaz **asíncrona** del authorization-core hacia el servicio de auditoría.
(Este documento es el equivalente "async" del contrato REST: lo complementa,
no lo reemplaza. OpenAPI solo cubre la superficie HTTP síncrona.)

## Propiedades del canal

| Aspecto | Valor |
|---|---|
| Topic | `transaction-events` |
| Formato | JSON (UTF-8) |
| Key de partición | `transactionId` ⇒ TODOS los eventos de una misma transacción van a la misma partición (orden local) |
| Productor | `authorization-core` |
| Consumidores | `transaction-audit` (auditoría + notificaciones) |

## Semántica de entrega

- **At-least-once**: el core persiste primero el evento (outbox en base de
  datos) con estado `PENDING`, luego lo publica a Kafka y marca `SENT`. Si la
  publicación falla, un scheduler reintenta.
- Consecuencia directa: **pueden llegar duplicados** (publicado + reintentado).
  El consumidor **DEBE ser idempotente**: si `transactionId` ya fue
  procesado, ignorar silenciosamente.

## Contrato del body (JSON)

```json
{
  "eventId":               "550e8400-e29b-41d4-a716-446655440001",
  "transactionId":         "8c1f5f4e-6f9a-4b3c-9b0a-2d4e6f8a1b2c",
  "maskedCardNumber":      "4539****6467",
  "decision":              "APPROVED",
  "responseCode":          "00",
  "authorizationCode":     "SXRRDE",
  "amount":                100.00,
  "currency":              "USD",
  "merchantId":            "MERCH-001",
  "merchantName":          "Amazon",
  "timestamp":             "2026-10-31T12:00:00Z"
}
```

Campos:
| Campo | Nota |
|---|---|
| `eventId` | identificador de la **entrega** — distinto en cada reintento |
| `transactionId` | identificador de la **transacción** — estable entre reintentos; ES la clave de idempotencia |
| `maskedCardNumber` | PAN siempre enmascarado; el PAN completo jamás circula por Kafka |
| `decision` | `APPROVED` \| `DECLINED` |
| `responseCode` | `00` \| `51` \| `07` \| `54` \| `14` \| `96` |
| `authorizationCode` | solo presente si `decision=APPROVED` |
| `declineCode` | solo presente si `decision=DECLINED` |
| `amount` | decimal exacto (máx. 2 decimales), nunca float en el productor |
| `timestamp` | UTC, `yyyy-MM-ddTHH:mm:ssZ` |

## Garantías del productor

1. Se emite un evento **solo para transacciones APPROVED**. Las DECLINED
   quedan únicamente en la bitácora Oracle (tabla `transactions`, con su
   `declineCode`): la auditoría asíncrona es para aprobadas.
   (Verificado en pruebas en vivo 2026-11-01: DECLINED → fila en
   `transactions`, cero filas en `event_outbox`.)
2. `authorizationCode` va presente solo si `decision=APPROVED`.
3. `declineCode` va presente solo si `decision=DECLINED`.
4. El evento se persiste **antes** de la publicación (outbox): si Kafka está
   caído, el evento no se pierde, queda `PENDING` y se reintenta.
5. `transactionId` es estable entre reintentos; `eventId` cambia en cada
   intento — la dedup del consumidor usa SIEMPRE `transactionId`.

## Obligación del consumidor (transaction-audit)

- **Idempotencia** por `transactionId` (ver sección Semántica de entrega).
- No debe afectar la operación del core: cualquier falla del audit NO debe
  bloquear nuevas autorizaciones (consumidor desacoplado / fire-and-forget).
