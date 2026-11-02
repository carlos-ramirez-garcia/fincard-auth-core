# ADR-0006 — Los declines tempranos (BLOCKED/EXPIRED) no se bitacoran en Oracle (hoy)

- **Estado:** Aceptado (con deuda técnica documentada)
- **Fecha:** 2026-11-01

## Contexto

Los declines que deciden ANTES del procedure (CARD_BLOCKED, CARD_EXPIRED,
CARD_NOT_FOUND — todo checks optimistas en Java, cuando ni siquiera es
necesario abrir transacción) no generan fila en `transactions`. Las
declinaciones que SÍ pasan por el procedure (INSUFFICIENT_FUNDS, etc.) sí
quedan bitacoradas.

## Alternativa

Que el procedure reciba un paramétrico `p_decision` "declinar por X" y sea él
quien INSERTe la bitácora en TODOS los casos → pero el objetivo del early-
decline es PRECISAMENTE no abrir transacción/lock para algo que ni siquiera
llega a cobro (AHORRO del FOR UPDATE) — mandarlo al procedure lo destruye.

## Decisión

Se acepta el trade-off: los early-declines **hoy NO viven en la bitácora
Oracle**; su rastro queda en el log de la aplicación (y, si con el tiempo se
necesita, el fix es BARATO: un INSERT a `transactions` con `card_id` resoluble
por el `CardRepository` que el service ya trae — mini-insert sin transacción
de cobro, fuera del procedure).

## Consecuencias

+: El fast-path se mantiene: las declinaciones tempranas NO abren
  transacción ni lock sobre `cards` (la ventana del `FOR UPDATE` se reserva
  para los que pueden cobrar).
−: Auditoría tiene un hueco para las declinaciones tempranas (el auditor no
  las ve en Oracle — solo en logs de la app).
−: Si un día se pide "reporte de todos los declines", ese reporte se
  encontrado incompleto — siendo el fix barato y documentado arriba, es deuda
  TÉCNICA CONOCIDA, no un bug oculto.
