---
name: seguridad
description: Use when the user mentions seguridad, OWASP, autorizacion, JWT, OAuth2, passwords, CORS, CSRF, secrets, vulnerabilidades o auditoria de seguridad del backend.
---

# Seguridad: checklist ejecutable (Spring Security 7)

Stack: Spring Security 7 (OAuth2 Google + JWT con refresh cookie), Jakarta Validation, Actuator, springdoc 3.1.1. Objetivo: lista de verificación ejecutable por un agente auditor.

## OWASP Top 10 (2021) mapeado a este proyecto

1. **A01 Broken Access Control.**
   - *Cómo comprobarlo:* buscar controllers sin `@PreAuthorize`; comprobar que hay `@EnableMethodSecurity` en la clase de configuración; revisar el `SecurityFilterChain` para ver qué rutas quedan `permitAll` (¿solo `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`?); verificar que un usuario normal no accede a recursos de otro (`/api/v1/inmuebles/{id}` ajeno).
   - *Cómo se arregla:* `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` / `@PreAuthorize("@auth.puedeGestionar(#id)")` en métodos de servicio/controller; reglas por rol en el `SecurityFilterChain` con `requestMatchers(...).hasRole(...)`; `.anyRequest().authenticated()` al final (nunca `permitAll` global).

```java
@EnableMethodSecurity
@Configuration class MethodSecurityConfig {}

@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/{id}") public void eliminar(@PathVariable Long id) { ... }
```

2. **A02 Criptografía rota.**
   - *Cómo comprobarlo:* grep de `MD5`, `SHA-1`, `MessageDigest` para contraseñas, `Cipher.getInstance("DES")`, claves fijas; comprobar que no hay passwords guardados en texto plano.
   - *Cómo se arregla:* `BCryptPasswordEncoder` (o Argon2) vía `PasswordEncoder` bean; HTTPS obligatorio en prod (cookies `Secure`).

3. **A03 Injection.**
   - *Cómo comprobarlo:* grep de `+` o `concat` dentro de `@Query`/`@Modifying`/`createNativeQuery`/`Statement`; de `jpql` construido con formato (`String.format`); de SQL en el frontend.
   - *Cómo se arregla:* parámetros posicionados/ named (`:id`, `?1`) siempre; JPA ya parameteriza; validación Jakarta en toda entrada.

```java
// BAD
@Query("FROM Inmueble i WHERE i.titulo LIKE '%" + "%' || :q || '%'")   // jamás concatenar
// GOOD
@Query("SELECT i FROM Inmueble i WHERE LOWER(i.titulo) LIKE LOWER(CONCAT('%', :q, '%'))")
```

4. **A04 Diseño inseguro.**
   - *Cómo comprobarlo:* endpoints de login/búsqueda sin límite de tasa; sin límite de tamaño de body; sin límite de páginas (`size=100000`).
   - *Cómo se arregla:* rate limiting (Bucket4j o filtro simple por IP/usuario), `server.tomcat.max-http-form-post-size`/`spring.servlet.multipart.max-file-size` acotados, `size` máximo fijado en la paginación.

5. **A05 Configuración incorrecta.**
   - *Cómo comprobarlo:* grep de credenciales en `application.yml`/`application-*.yml` y de `.env` versionado; comprobar `management.endpoints.web.exposure.include` en prod (solo `health,info`); CORS: grep de `allowedOriginPatterns("*")`, `@CrossOrigin` y `allowCredentials(true)` con `*`; CSRF deshabilitado sin justificación; perfiles activos.
   - *Cómo se arregla:* secretos solo por variables de entorno (`${DB_PASSWORD}`), `.env` en `.gitignore`; allowlist explícita de orígenes (`https://app.manti.es`); CSRF se mantiene **habilitado** si la autenticación va por cookie (refresh token); si se usa JWT bearer, `csrf.disable()` solo con comentario/ADR que lo justifique; cabeceras de seguridad (ver lista final).

```java
// GOOD
cors.configurationSource(cfg -> {
    cfg.setAllowedOrigins("https://app.manti.es");
    cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE"));
    cfg.setAllowCredentials(true);
});
// BAD
cfg.addAllowedOrigin("*"); cfg.setAllowCredentials(true);   // robo de cookies cross-site
```

6. **A06 Componentes vulnerables.**
   - *Cómo comprobarlo:* en CI, `./mvnw org.owasp:dependency-check-maven:check` (backend) y `npm audit --audit-level=high` (frontend); revisar notas de Spring Boot 4.1.x / Spring Security 7.
   - *Cómo se arregla:* actualizar versión afectada, excluir solo con riesgo documentado.

7. **A07 Fallos de autenticación.**
   - *Cómo comprobarlo:* login sin límite de intentos; refresh token sin rotación; cookie de refresh sin `HttpOnly; Secure; SameSite`; token en `localStorage`.
   - *Cómo se arregla:* contador de intentos con bloqueo temporal; rotación de refresh token (invalidar el anterior, detectar reuso); cookie `HttpOnly; Secure; SameSite=Lax` con `Path`; access token en memoria (header `Authorization: Bearer`) o en cookie de acceso corta.

8. **A08 Integridad de datos.**
   - *Cómo comprobarlo:* endpoints con `@RequestBody` sin `@Valid`; campos numéricos/rangos sin `@Positive`/`@Size`; concurrencia sin control (lost update).
   - *Cómo se arregla:* Jakarta Validation en todos los request records, `@Validated` en controllers, control optimista (`@Version`) donde importe.

9. **A09 Fallos de logging y manejo de errores.**
   - *Cómo comprobarlo:* grep de `e.printStackTrace()`, `System.out`, respuestas con stacktrace (`server.error.include-stacktrace: always`), mensajes con SQL/credenciales; comprobar `@RestControllerAdvice` global.
   - *Cómo se arregla:* RFC 9457 `ProblemDetail` con `type`, `title`, `status`, `detail` genérico y `traceId`; detalle interno solo en log SLF4J; `include-stacktrace: never`, `include-message: never` en prod.

```java
@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(ReglaDeNegocio.class)
    ProblemDetail regla(ReglaDeNegocio e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Operación no permitida");
        pd.setTitle("Conflicto de negocio");
        return pd;   // NUNCA e.getMessage() ni la traza
    }
}
```

10. **A10 SSRF.**
    - *Cómo comprobarlo:* toda URL recibida de cliente que se descarga (`RestClient`, `ImageIO`, webhooks de proveedores); `redirect_uri` dinámico en OAuth2.
    - *Cómo se arregla:* allowlist de hosts/protocolos (`https`), bloqueo de rangos privados (`127.0.0.0/8`, `10.0.0.0/8`, `169.254.0.0/16`), sin redirects abiertos.

## Top-10 errores que debe buscar un agente en ESTE código

1. JWT o refresh token almacenado en `localStorage`/`sessionStorage` del frontend → robo por XSS; mover a cookie `HttpOnly` o memoria.
2. CORS `*` (o `allowedOriginPatterns("*")`) con `allowCredentials(true)`, o `@CrossOrigin` disperso en controllers → allowlist explícita única.
3. Queries concatenadas en `@Query` nativos o JPQL con `+`/`String.format` → parámetros.
4. Falta de `@PreAuthorize` en endpoints de escritura/borrado (solo `SecurityFilterChain` sin `.anyRequest().authenticated()`).
5. Secrets en `application.yml`, `application-prod.yml` o `.env` commiteado → `${VAR}` y `.gitignore`.
6. Endpoints sin autenticación por olvido (`permitAll` ampliado, `/api/**` abierta, springdoc/actuator expuestos en prod).
7. CSRF deshabilitado sin justificación mientras hay cookie de refresh.
8. Cabeceras de seguridad ausentes: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` (o CSP `frame-ancestors 'none'`), `Referrer-Policy`, `Strict-Transport-Security` (prod), CSP. Añadirlas en el SecurityFilterChain con los headers de Spring Security.
9. `include-stacktrace`/`include-message` en `always`, o `ProblemDetail` que filtra `e.getMessage()` interno.
10. Login sin límite de intentos, refresh sin rotación, cookies sin `HttpOnly/Secure/SameSite`.

## Checklist de revisión

- [ ] `@EnableMethodSecurity` activo y `@PreAuthorize` en todo endpoint de escritura/borrado.
- [ ] Zero concatenación en queries; toda entrada pasa por Jakarta Validation con `@Valid`.
- [ ] CORS con allowlist explícita; sin `*` ni `@CrossOrigin` redundante.
- [ ] CSRF coherente con el modo de autenticación (cookie ⇒ habilitado) y documentado si se desactiva.
- [ ] Secrets solo en variables de entorno; actuator en prod limitado a `health,info`.
- [ ] Cookies de refresh `HttpOnly; Secure; SameSite`; rotación de refresh; sin tokens en `localStorage`.
- [ ] Errores como RFC 9457 sin stacktrace ni mensajes internos; logging con SLF4J, sin `printStackTrace`.
- [ ] Cabeceras `X-Content-Type-Options`, `X-Frame-Options`/CSP `frame-ancestors` presentes; OWASP dependency-check y `npm audit` en CI.
