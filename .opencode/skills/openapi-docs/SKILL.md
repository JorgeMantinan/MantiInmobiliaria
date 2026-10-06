---
name: openapi-docs
description: Use when documentando la API con springdoc-openapi, exportando y lintando openapi.yaml, generando el PDF de referencia con Redocly y asciidoctor y manteniendo la spec sincronizada.
---

# Documentación OpenAPI y PDF

## Reglas

1. **springdoc-openapi 3.1.1 en el backend.** Configuración mínima en `es.manti.config.OpenApiConfig`:

```java
@Bean OpenAPI mantiOpenApi() {
  return new OpenAPI()
    .info(new Info().title("MantiInmobiliaria API").version("1.0.0")
      .description("API REST del portal inmobiliario"))
    .servers(List.of(new Server().url("http://localhost:8080")))
    .components(new Components().addSecuritySchemes("bearerAuth",
      new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
}
```

2. **Anotaciones obligatorias en cada controller.**
   - `@Tag(name = "Inmuebles", description = ...)` por módulo.
   - `@Operation(summary = "...")` en ESPAÑOL.
   - `@ApiResponse(responseCode = "200" | "201" | "400" | "401" | "403" | "404" | "409", description = ..., content = @Content(schema = @Schema(implementation = ProblemDetail.class)))` por cada código relevante.
   - `@Parameter(description = ...)` en path/query params.
   - `@Valid` en request bodies y `@RequestBody` tipado.

```java
@Tag(name = "Inmuebles", description = "CRUD y búsqueda de inmuebles")
@GetMapping("/{id}")
@Operation(summary = "Obtiene un inmueble por su identificador")
@ApiResponse(responseCode = "200", description = "Inmueble encontrado")
@ApiResponse(responseCode = "404", description = "No encontrado",
  content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
ResponseEntity<InmuebleDto> obtener(@Parameter(description = "ID del inmueble") @PathVariable Long id) { ... }
```

3. **No documentar internamente.** Sólo se expone lo público bajo `/api/v1`; endpoints de actuadores/health o internos se ocultan (`@Hidden()`).

4. **Exportar la spec** y mantenerla sincronizada:

```bash
curl -s http://localhost:8080/v3/api-docs -o docs/openapi/openapi.yaml
# o con el plugin springdoc-openapi-maven-plugin en un perfil maven
```

La spec versionada en git se actualiza en el MISMO PR que añade o cambia endpoints (regla dura del repo).

5. **Lint en CI** antes de generar nada:

```bash
npx @redocly/cli lint docs/openapi/openapi.yaml
```

Mínimo: sin cruces de operaciones entre paths, `operationId` únicos, `summary`/`description` presentes, referencias `$ref` resolubles.

6. **Generar el PDF — pipeline exacto.**

```bash
# 1) asciidoc desde la spec (openapi-generator-cli v7)
java -jar openapi-generator-cli.jar generate \
  -i docs/openapi/openapi.yaml -g asciidoc -o docs/openapi/gen

# 2) perfil maven `docs` con asciidoctorj-maven-plugin (backend):
#    entradas docs/openapi/gen/*.adoc -> target
mvn -Pdocs package
# salida final copiada a:
# docs/openapi/api-reference.pdf
```

En CI/local se invoca con el comando de proyecto `/docs-pdf` (exporta spec → lint → genera adoc → asciidoctor → PDF).

7. **Alternativa visual Redoc** cuando sólo se necesita HTML navegable:

```bash
npx @redocly/cli build-docs docs/openapi/openapi.yaml -o docs/openapi/site
# PDF opcional con Puppeteer: page.pdf({ path: 'docs/openapi/api-reference.pdf', format: 'a4', printBackground: true })
```

Cuándo usar cada una: **Redocly + asciidoctor → PDF** es el artefacto canónico versionado (`api-reference.pdf`); **Redoc build-docs** sólo para vista web rápida/revisión visual, sin sustituir al PDF.

8. **Mantener sincronía.** Si cambia un endpoint → cambian anotaciones → se reexporta `openapi.yaml` → lint verde → se regenera el PDF en el mismo PR. Un PR que cambie endpoints sin tocar la spec se rechaza.

### Checklist de documentación ante cambio de API

- [ ] `@Tag`, `@Operation` en español y `@ApiResponse` por cada código nuevo/cambiado
- [ ] `@Parameter` en todos los path/query params; `@Valid` en bodies
- [ ] Internos marcados con `@Hidden()`
- [ ] `docs/openapi/openapi.yaml` reexportado desde `/v3/api-docs` en el mismo PR
- [ ] `npx @redocly/cli lint` sin errores
- [ ] `operationId` únicos y sin cruces de operaciones
- [ ] PDF regenerado (`/docs-pdf` o perfil `-Pdocs`) cuando cambia la spec
- [ ] springdoc y anotaciones consistentes con los códigos HTTP reales (skill api-rest)
