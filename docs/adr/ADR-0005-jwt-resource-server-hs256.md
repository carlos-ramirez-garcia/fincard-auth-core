# ADR-0005 — JWT HS256 con resource-server; emisor dev solo en perfil local

- **Estado:** Aceptado
- **Fecha:** 2026-11-01

## Contexto

El API de autorización no puede quedar abierta. En producción, el emisor de
tokens es un IdP (Keycloak, Auth0). En un prototipo local no hay IdP — pero la
seguridad del core no debe "apagarse" para probar.

## Decisión

1. **El core NO emite tokens** — es un **OAuth2 resource server**
   (`spring-security-oauth2-resource-server`): valida todo JWT con la secret
   compartida HS256 (`fincard.security.jwt.secret`, externalizada por
   env/secret manager — nunca en código).
2. `SecurityFilterChain` stateless: CSRF off, `STATELESS`,
   `anyRequest().authenticated()`; paths públicos mínimos:
   `/actuator/health/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/error`,
   `/api/v1/dev/**`.
3. **`DevTokenController` con `@Profile("local")`**: emite un JWT HS256 con
   LA MISMA secreta del validador, 1 h de vida. Solo existe si el profile
   `local` está activo (en el deployment real no se pide el profile → el bean
   NI SIQUIERA nace: la ruta queda expuesta pero sin listener).
4. HS256 (simétrico) aceptado para el prototipo: un solo backend, sin clave
   pública en rotación. Para multi-servicio real: RS256/ES256 con JWKs (el
   JwtDecoder cambiaría a issuer-uri — el resto del diseño NO cambia).

## Consecuencias

+: El flujo completo (token → candado → negocio) es demostrable local sin IdP.
+: La seguridad "por defecto cerrada" no se negocia en ningún entorno.
−: HS256 exige proteger la secret: quien la tenga VÁLIDA tokens — por eso
  externalizada y rotada (en compose vía env).
−: Un endpoint dev autenticable pasa a "arcángel" (conocido). → documentado
  como ADR; en deployment real no existe.
Hallazgo de construcción (2026-11-01): el primer boot real dejó al descubierto
el *chicken-and-egg* — el propio `/api/v1/dev/tokens` quedaba protegido (401
para obtener un token). Lección: los paths de infra de seguridad son un punto
de diseño (no un "bug a parchear").
