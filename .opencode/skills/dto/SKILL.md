---
name: dto
description: Use when the user mentions dto, records, request, response, validacion, mapeo MapStruct, exponer entidades JPA o versionado de API en el backend Spring Boot.
---

# DTOs en Spring Boot (MantiInmobiliaria)

Stack fijo: Java 25, Spring Boot 4.1 MVC, MapStruct 1.6.3 + Lombok, Jakarta Validation, springdoc-openapi 3.1.1.
Paquete raíz: `es.manti.<modulo>.{api,application,domain,infra}`. Los DTOs viven SIEMPRE en `es.manti.<modulo>.api.dto`.

## Reglas

1. **Todo payload de API es un `record` de Java.** Nada de clases con Lombok (`@Data`, `@Getter/@Setter`) ni POJOs mutables en `api.dto`.

```java
// GOOD
public record InmuebleCreateRequest(
        @NotBlank @Size(max = 200) String titulo,
        @NotNull @Positive BigDecimal precio,
        @NotNull Long ciudadId) {}

// BAD
@Data public class InmuebleCreateRequest { private String titulo; private BigDecimal precio; }
```

2. **Naming obligatorio:** `XRequest` (entrada), `XResponse` (salida de un recurso), `XSummary` (proyección ligera de listado), `XPage` (envoltorio de paginación). Ejemplos: `InmuebleCreateRequest`, `InmuebleResponse`, `InmuebleSummary`, `PageResponse<InmuebleSummary>`.

3. **Request y response se separan aunque tengan los mismos campos.** Nunca se reutiliza el mismo record para lectura y escritura: evolucionan distinto (el response añade `id`, `createdAt`; el request añade `password`, `token`).

```java
// GOOD: mismo aspecto, dos tipos
public record UsuarioCreateRequest(@Email @NotBlank String email, @Size(min = 8) String password) {}
public record UsuarioResponse(Long id, String email, Instant createdAt) {}
```

4. **Las entidades JPA (`@Entity`) jamás salen de `domain`/`infra`.** No se devuelven en controllers, ni se anidan en otros DTOs, ni se serializan en errores. El mapeo entidad↔DTO ocurre en un mapper MapStruct del propio módulo.

```java
// BAD: el controller expone la entidad
@GetMapping("/inmuebles") List<Inmueble> listar() { return servicio.listar(); }
```

5. **Mapeo con MapStruct, siempre `componentModel = "spring"`** para inyectarlo como bean. El mapper se sitúa en `api.dto` o en `application` (si lo usan varios controllers); la entidad solo aparece en la firma de sus métodos, nunca como tipo de respuesta.

```java
package es.manti.inmueble.api.dto;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InmuebleMapper {
    InmuebleResponse toResponse(Inmueble entity);
    InmuebleSummary toSummary(Inmueble entity);
    List<InmuebleSummary> toSummaryList(List<Inmueble> entities);
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "estado", ignore = true)
    Inmueble toEntity(InmuebleCreateRequest request);
}
```

Configuración Maven necesaria: `lombok-mapstruct-binding:0.2.0` y anotación `@Mapper` generada tras `./mvnw compile`.

6. **Validación Jakarta en el record de request**, con `@Valid` en el parámetro del controller. El response nunca lleva validación.

```java
@RestController
@RequestMapping("/api/v1/inmuebles")
@Validated
@RequiredArgsConstructor
public class InmuebleController {
    private final InmuebleService service;
    private final InmuebleMapper mapper;

    @PostMapping
    public ResponseEntity<InmuebleResponse> crear(@Valid @RequestBody InmuebleCreateRequest request) {
        Inmueble guardado = service.crear(mapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(guardado));
    }
}
```

Anotaciones habituales: `@NotBlank`, `@NotEmpty`, `@Size(min,max)`, `@Positive`, `@PositiveOrZero`, `@DecimalMin("0")`, `@Pattern` (teléfonos, códigos postales), `@Email`, `@NotNull` en `@RequestParam` obligatorios (añadir `@Validated` en la clase del controller).

7. **Los responses son inmutables:** records sin métodos mutadores, campos ya transformados (nada de entidades, `EntityManager`, `Optional`, proxies Hibernate). Fecha/hora como `Instant` o `LocalDate` serializados en ISO-8601 (formato por defecto de Jackson en Spring Boot; no desactivar `write-dates-as-timestamps`).

```java
// GOOD
public record InmuebleResponse(Long id, String titulo, BigDecimal precio,
                               String estado, Instant createdAt) {}
// BAD
public record InmuebleResponse(Long id, Optional<String> titulo, LocalDateTime fecha, Map<String,Object> extra) {}
```

8. **Paginación con envoltorio propio:** `XPage<T>` con `content`, `page`, `size`, `totalElements`, `totalPages`, y `nextCursor` cuando el listado sea grande. No devolver `Page<...>` de Spring Data directamente (expone serialización interna frágil y acopla el contrato).

```java
public record PageResponse<T>(List<T> content, int page, int size,
                              long totalElements, int totalPages, String nextCursor) {}
```

9. **Evolución sin romper API:**
   - Cambios aditivos: campo nuevo solo en response y **opcional** en request (record permite `null` si no lleva `@NotNull`).
   - Nunca renombrar ni eliminar un campo publicado: marcar `@Deprecated` y mantener al menos una versión.
   - Cambios incompatibles → nueva versión de ruta: `/api/v1/...` → `/api/v2/...` (mantener v1 mientras haya clientes).
   - El cliente puede enviar campos desconocidos sin romper (Spring Boot desactiva `FAIL_ON_UNKNOWN_PROPERTIES` por defecto); documentar en springdoc con `@Schema(description = ...)`.

10. **Anti-patrones prohibidos:**
    - `Map<String, Object>` como tipo de respuesta o request: sin contrato, sin validación, sin documentación springdoc.
    - Lombok en DTOs (`@Data` genera setters y equals mutables).
    - Exponer `Optional<T>` (Jackson lo serializa raro) → usar `null` o un campo presente/ausente documentado.
    - `LocalDateTime` sin zona horaria en API pública → `Instant`/`OffsetDateTime` (o `LocalDate` si es solo fecha civil).
    - Entidades JPA, `Serializable` de Hibernate o tipos de `infra` dentro de un DTO de `api`.
    - Un único `XDto` genérico usado para todo el módulo.

11. **Los DTOs no contienen lógica de negocio.** Cálculos, reglas y transacciones viven en `application` (servicios). El DTO solo transporta y valida formato.

## Checklist de revisión

- [ ] Todos los payloads de `api.dto` son `record` y siguen el naming `XRequest`/`XResponse`/`XSummary`/`XPage`.
- [ ] Ninguna `@Entity` aparece en un controller, en un response o en la firma de un endpoint.
- [ ] Todos los `XRequest` llevan anotaciones Jakarta Validation y el controller usa `@Valid`.
- [ ] El mapeo se hace con MapStruct `@Mapper(componentModel = "spring")`, sin asignaciones manuales campo a campo.
- [ ] Request y response son tipos separados (no hay record compartido).
- [ ] Respuestas sin `Map<String,Object>`, sin `Optional`, sin `LocalDateTime` sin formato ISO-8601.
- [ ] Listados devuelven `XPage<T>` con `content` + `totalPages` + `nextCursor`.
- [ ] Cambios de contrato evaluados: aditivos/ opcionales o versión `/api/v2`.
