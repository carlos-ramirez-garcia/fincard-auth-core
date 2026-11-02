# ADR-0002 — La decisión final de fondos vive SOLO en el procedure PL/SQL

- **Estado:** Aceptado
- **Fecha:** 2026-11-01

## Contexto

El requisito exige lógica PL/SQL. Un diseño ingenuo haría `SELECT saldo FROM cards` en Java,
compararía con el monto y ejecutaría el `UPDATE` por separado: si dos transacciones concurrentes
leen el mismo saldo, ambas aprueban y el saldo queda negado (doble gasto).

## Decisión

La decisión de fondos se toma **únicamente dentro** del procedure
`FIC_PROCESS_AUTHORIZATION`:

```sql
-- (esquema del diseño; ver 02_stored_procedures.sql.sql)
SELECT balance INTO v_balance FROM cards
 WHERE card_number = p_card_number
   FOR UPDATE;                      -- ← lock pesimista de fila
IF p_amount <= v_balance THEN ...   -- decide bajo lock
UPDATE ... ; INSERT transactions ... (todo en la misma transacción → 1 COMMIT)
```

Java hace solo **checks optimistas** (BLOCKED, EXPIRED — datos sin riesgo de
evolución concurrente) y delega toda decisión que pueda verse alterada por
concurrencia.

## Consecuencias

+: Anti doble-gasto por construcción: el `FOR UPDATE` serializa el acceso a
  la fila de la tarjeta.
+: Un solo COMMIT: decisión + cobro + bitácora son atómicos.
−: La lógica de negocio vive en PL/SQL (menos testeable en JUnit, requiere
  versionar el SQL y probarlo con escenarios SQL — se hace en `sql/`).
−: El lock mantiene la fila reservada solo durante la transacción (escape
  rápido: el procedure no hace I/O externo).
Consideración alternativa descartada: bloqueo optimista con versión (tabla
`versions`) — la carrera de autorización es demasiado corta y frecuente como
para compensarse con reintentos.
