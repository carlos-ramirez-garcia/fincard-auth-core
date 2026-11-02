# Diagrama de secuencia — Flujo de autorización

> Representa el camino completo de una petición de pago desde el front (Angular) hasta que el evento queda registrado en la tabla de auditoría (Kafka → Oracle). Cada bloque corresponde a una capa del sistema y a las interacciones asíncronas garantizadas por la Transactional Outbox.

```mermaid
sequenceDiagram
    participant M as "Merchant / POS (Front Angular)"
    participant FE as "Frontend (Angular 22, signals)"
    participant TG as "DevTokenService (cache + singleflight)"
    participant CO as "Authorization Core (Spring Boot 3.21, JWT)"
    participant DB as "Oracle 23ai (Procedure FIC_PROCESS_AUTHORIZATION)"
    participant RC as "Redis Cache (cache‑aside / write‑through)"
    participant OB as "Event Outbox (Transactional Outbox)"
    participant KF as "Kafka (topic transaction-events, 3 partitions)"
    participant AU as "Audit Service (KafkaListener)"
    participant TD as "Oracle Audit Table"

    M->>FE: Usuario llena formulario y envía
    FE->>TG: getToken() (POST /api/v1/dev/tokens, cacheado 60 s antes expiración)
    TG-->>FE: accessToken (Bearer) + expiresIn
    FE->>CO: POST /api/v1/cards/authorizations (body: DTO, JWT Bearer)
    activate CO
    CO->>RC: getCardSnapshot(PAN) — cache‑miss?
    RC-->>CO: hit / miss (TTL 180 s)
    alt cache‑hit
        CO-->>FE: respuesta rápida (desde Redis)
    else cache‑miss
        CO->>DB: FIC_PROCESS_AUTHORIZATION (SELECT … FOR UPDATE, decide + bitácora + outbox en 1 txn)
        DB-->>CO: decisión (APPROVED/DECLINED), saldo restante, código ISO‑8583
        CO->>RC: write‑through UPDATE fincard:card:<PAN> (TTL renovado)
        CO->>OB: recordAndPublishApproved (solo si APPROVED) → insertPending outbox
        OB-->>CO: OK
    end
    deactivate CO
    CO-->>FE: CardAuthorizationResponse (decision, responseCode, authorizationCode?, remainingBalance?)

    alt APPROVED
        CO->>OB: Ya insertado; OB publica a Kafka
        OB->>KF: publish transaction-event (key=transactionId)
        KF->>AU: KafkaListener idempotente
        AU->>TD: INSERT INTO audit_transactions (idempotent check)
        TD-->>AU: guardado OK
        AU-->>M: evento persisted (visible en /audit)
    else DECLINED
        note right of M: (por diseño ADR‑0003) los declines **no** se publican a Kafka ni a la tabla de auditoría
    end
```

### Cómo leerlo en la demo

1. **Contexto**: el merchant (POS) solicita una autorización; el front captura JWT de `DevTokenController` (perfil `local`).
2. **Flujo crítico**: si hay caché en Redis se salta la consulta a la BD; de lo contrario la procedure PL/SQL resuelve la decisión, actualiza el cache y, **solo para APPROVED**, registra un evento en el outbox que más tarde será publicado a Kafka.
3. **Audit**: el consumidor Kafka (idempotente) escribe en la tabla `audit_transactions`; los eventos DECLINED se descartan explícitamente por la política de outbox (ADR‑0003).
4. **Cierre**: el front muestra el veredicto (badge APPROVED/DECLINED) y, si corresponde, el evento aparece al instante (o en ~15 s por el polling del servicio de auditoría).