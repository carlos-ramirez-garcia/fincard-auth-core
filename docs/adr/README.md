# ADRs — FICARD (Architecture Decision Records)

Registro de decisiones de diseño significativas. Formato ligero:
Estado / Contexto / Decisión / Consecuencias (+ alternativa descartada).

| # | Decisión | Estado |
|---|---|---|
| [ADR-0001](ADR-0001-declines-de-negocio-http-200.md) | Declines de negocio = HTTP 200 + `decision`/`declineCode` | Aceptado |
| [ADR-0002](ADR-0002-fondos-en-el-procedure.md) | Decisión final de fondos dentro del procedure PL/SQL (`FOR UPDATE`) | Aceptado |
| [ADR-0003](ADR-0003-transactional-outbox.md) | Transactional Outbox para la emisión de eventos a Kafka | Aceptado |
| [ADR-0004](ADR-0004-redis-cache-aside-write-through.md) | Redis: cache-aside lectura + write-through del cargo | Aceptado |
| [ADR-0005](ADR-0005-jwt-resource-server-hs256.md) | JWT HS256 resource-server; emisor dev solo en perfil `local` | Aceptado |
| [ADR-0006](ADR-0006-early-declines-sin-bitacora.md) | Early-declines (BLOCKED/EXPIRED) sin bitácora — trade-off documentado | Aceptado (deuda técnica conocida) |

*Criterio de uso: un ADR existe cuando la decisión (a) fue difícil de tomar,
(b) es costosa de revertir o (c) la explicación puede que se cite en un
code review — como los included aquí.*
