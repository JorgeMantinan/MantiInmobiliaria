---
name: api-rest
description: Use when diseñando o revisando endpoints API REST: prefijos, códigos HTTP, errores RFC 9457 ProblemDetail, paginación, filtros, versionado, springdoc y checklists de endpoint nuevo.
---

# Diseño de la API REST

## Reglas

1. **Prefijo y recursos.** Todo bajo `/api/v1`. Recursos en plural kebab-case; subrecursos sólo si aportan (`/api/v1/inmuebles/{id}/favoritos`).

```
GET    /api/v1/inmuebles
GET    /api/v1/inmuebles/{id}
POST   /api/v1/inmuebles
PUT    /api/v1/inmuebles/{id}
DELETE /api/v1/inmuebles/{id}
POST   /api/v1/inmuebles/{id}/favoritos
```

2. **Códigos HTTP correctos.**

| Código | Uso |
|---|---|
| 200 | OK / lista / actualización sin cuerpo nuevo |
| 201 + `Location` | creación (POST) |
| 204 | DELETE sin cuerpo |
| 400 | validación/errores de campo (ProblemDetail con `errors[]`) |
| 401 | no autenticado (sin token o expirado) |
| 403 | autenticado sin permiso |
| 404 | recurso inexistente |
| 409 | conflicto (slug duplicado, estado inválido) |
| 422 | NO se usa → se responde 400 con ProblemDetail |
| 429 | rate limit |

3. **Errores RFC 9457** con `Content-Type: application/problem+json`, generados por un `@RestControllerAdvice` global.

```json
{
  "type": "https://api.manti.dev/problems/validacion",
  "title": "Validación fallida",
  "status": 400,
  "detail": "3 campos inválidos",
  "instance": "/api/v1/inmuebles",
  "errors": [{ "field": "precio", "message": "debe ser mayor que 0" }]
}
```

4. **Paginación.**
   - Admin/backoffice: `?page=0&size=20` (page zero-based).
   - Listados públicos: cursor `?cursor=<opaque>&limit=20`.
   - Respuesta de lista SIEMPRE en el mismo envelope:

```json
{ "content": [], "page": 0, "totalElements": 0, "nextCursor": null }
```

5. **Filtros y orden por query params.** `?precioMin=&precioMax=&tipo=&ciudad=`; orden `?sort=precio,desc` (campo,dir). Los nombres en camelCase y consistentes con los DTOs.

6. **Versionado.** Cambio romper ⇒ `/api/v2` (paralelo). Añadir campos opcionales ⇒ compatible, sin versión nueva. Nunca renombrar/eliminar campos en v1 sin deprecación.

7. **HATEOAS** no obligatorio: sólo si un caso de uso real lo necesita. No inventar links.

8. **Documentación springdoc obligatoria en TODO endpoint**: `@Tag` por módulo, `@Operation(summary = "...")` en español, `@ApiResponse` por cada código (con schema ProblemDetail en errores), `@Parameter` en path/query, `@Valid` en request bodies. Nada de rutas internas expuestas (ver skill openapi-docs).

9. **Idempotencia.** POST críticos (pagos, envíos de contacto profesional, swipe) aceptan cabecera `Idempotency-Key` y no deben duplicar efectos.

10. **HTTPS/SSL obligatorio** en prod; redirigir HTTP → HTTPS; cookies `Secure; HttpOnly; SameSite`.

### Ejemplo completo: CRUD de inmuebles

```json
// POST /api/v1/inmuebles  → 201 + Location: /api/v1/inmuebles/42
// Request
{ "titulo": "Piso céntrico", "tipo": "PISO", "precio": 185000, "ciudad": "Madrid", "m2": 96 }

// Response 201
{ "id": 42, "slug": "piso-cenetrico-madrid", "titulo": "Piso céntrico", "precio": 185000, "estado": "ACTIVO" }

// GET /api/v1/inmuebles?precioMax=200000&sort=precio,desc&limit=20 → 200
{ "content": [ { "id": 42, "titulo": "Piso céntrico", "precio": 185000 } ],
  "page": 0, "totalElements": 1, "nextCursor": null }

// GET /api/v1/inmuebles/999 → 404
{ "type": "about:blank", "title": "No encontrado", "status": 404, "detail": "Inmueble 999 inexistente", "instance": "/api/v1/inmuebles/999" }

// PUT /api/v1/inmuebles/42 (sin cambios) → 200 ; DELETE /api/v1/inmuebles/42 → 204
```

### Checklist de endpoint nuevo

- [ ] Ruta `/api/v1/<recurso>` plural kebab + subrecursos justificados
- [ ] Códigos correctos (201+Location, 204, 400/401/403/404/409, 429)
- [ ] Errores vía `@RestControllerAdvice` con ProblemDetail y `errors[]` de campos
- [ ] Paginación (page/size admin, cursor/limit público) con envelope uniforme
- [ ] Filtros/orden por query params documentados
- [ ] `@Tag`, `@Operation` (español), `@ApiResponse` por código, `@Parameter`, `@Valid`
- [ ] Idempotencia en POST críticos; SSL en prod
- [ ] openapi.yaml actualizado y lintado en el MISMO PR
