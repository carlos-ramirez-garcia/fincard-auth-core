# ADR-0001 — Las declinaciones de negocio responden HTTP 200

- **Estado:** Aceptado
- **Fecha:** 2026-11-01

## Contexto

Una autorización puede declinar por razones de negocio (fondos, tarjeta
bloqueada, vencida, inexistente). La pregunta de diseño: ¿esos casos son
*errores HTTP* (4xx) o respuestas exitosas con decisión negativa?

## Decisión

Toda evaluación con resultado de negocio responde **HTTP 200** con:

```json
{ "decision": "DECLINED", "responseCode": "51", "declineCode": "INSUFFICIENT_FUNDS" }
```

Los códigos de estado HTTP quedan reservados para problemas de **contrato/transporte**:
- `400` — request inválido (validación de DTO)
- `404` — recurso inexistente en GET (los paths, no las tarjetas)
- `401` — token ausente/inválido
- `500` — fallo técnico no esperado

Caso especial: `CARD_NOT_FOUND` en el **POST** de autorización es una decisión
de negocio (200 + decline 14); el 404 queda solo para rutas desconocidas y el
GET de tarjeta.

## Consecuencias

+: El cliente distingue *fue evaluado y declinado* (200/DECLINED) de *nunca
  llegó a evaluarse* (4xx) — los dos casos exigen reacciones distintas en el
  POS (mostrar mensaje vs corregir el request).
+: El log de acceso (nginx/APM) cuenta "respuestas 2xx" sin ruido de negocio.
−: Un monitoreo ingenuo por status codes no ve las declinaciones — hay que
  mirar el campo `decision` (o las métricas de negocio).
−: Requiere documentación explícita para clientes acostumbrados al enfoque
  "4xx para todo lo negativo".
