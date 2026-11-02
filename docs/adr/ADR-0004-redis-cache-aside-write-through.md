# ADR-0004 — Redis: cache-aside en lectura + write-through al cobro

- **Estado:** Aceptado
- **Fecha:** 2026-11-01

## Contexto

Cada autorización consulta el estado de la tarjeta (status, expiry, saldo).
Preguntarlo SIEMPRE a Oracle es lento y hace de Oracle el cuello de botella.
Pero: el saldo debe ser coherente con el cobro real, sobre todo en escritura.

## Decisión

**Lectura — cache-aside:**
- `GET key fincard:card:<PAN>` en Redis; si hit, úsese el snapshot.
- Si miss, SELECT en Oracle, se arma `CardSnapshot` y se publica en Redis
  (TTL 180 s) — se llena solo con lo que se consulta, sin precacheo.

**Escritura — write-through del cobro:**
- El procedure (fuente de verdad) devuelve el `remainingBalance` como OUT.
- Tras COMMIT, el `AuthorizationService` reparte **write-through**:
  `updateAfterCharge(PAN, remainingBalance)` actualiza el cache con el SALDO
  REAL que quedó en Oracle — nunca un saldo "que el Java cree".
- El TTL corto protege de ambas desincronización haciendo que expire este
  valor pronto en cualquier caso.

**Degradación elegante:** si Redis está caído, el flujo DEGRADA a Oracle
(excepciones de Redis se log-ean, el flujo continúa — la disponibilidad se
sacrifica en la ruta lenta, no en la respuesta).

## Consecuencias

+: Lecturas frecuentes de tarjetas repetidas NO golpean Oracle.
+: Saldo en cache SIEMPRE el valor del cargo real (write-through), no una
  copia "que se decide a su criterio".
+: Redis caído = más lento, no roto.
−: La incoherencia si un UPDATE llega por fuera del core (preámbulo TTL) —
  180 s máx. en el peor caso (mitigable con invalidación por Pub/Sub).
−: Cada APPROVED genera una escritura extra en Redis (tráfico ampliado).
