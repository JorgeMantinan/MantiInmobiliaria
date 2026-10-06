---
name: clean-code-servicios
description: Use when the user mentions clean code, servicio, service, estructura de modulo, capas, dependencias, excepciones, God Service o revisar calidad de un backend.
---

# Clean code en servicios y componentes backend

## Convención de estructura (proyecto híbrido, válida y única)

Cada módulo `es.manti.<modulo>` se organiza así:

| Paquete | Contenido | Regla |
|---|---|---|
| `api` | controllers, `api.dto` records, excepciones de presentación | solo orquestación HTTP; **sin lógica de negocio** |
| `application` | servicios / casos de uso | transaccionalidad, coordinación entre domain e infra |
| `domain` | **entidades JPA**, interfaces de repositorio (extienden `CrudRepository`/`JpaRepository`), puertos, excepciones de dominio, eventos | sin dependencia de HTTP ni de proveedores externos |
| `infra` | adaptadores (APIs externas, email, Telegram), config (Security, Jackson, Caffeine), implementaciones de puertos | único sitio que conoce tecnologías concretas |

Los repositorios se declaran en `domain` y Spring Data genera la implementación automáticamente; los **adaptadores** (proveedor externo, correo) van en `infra`. Esta es la única convención: no inventar variantes por módulo.

## Reglas

1. **Controller = traducción HTTP.** 3-4 líneas por método: leer request, llamar a un servicio, devolver response. Nada de `@Transactional`, nada de repositorios, nada de `if` de negocio.

```java
// GOOD
@PostMapping
@PreAuthorize("hasRole('AGENTE')")
public ResponseEntity<InmuebleResponse> crear(@Valid @RequestBody InmuebleCreateRequest req) {
    Inmueble guardado = service.crear(mapper.toEntity(req));
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(guardado));
}

// BAD: lógica de negocio y persistencia en el controller
@Transactional
@PostMapping public Inmueble crear(@RequestBody Inmueble i) {
    if (i.getPrecio() < 0) i.setPrecio(0);
    return repo.save(i);
}
```

2. **Controllers NO llaman a repositories.** La única dependencia de un controller es su servicio (`application`) + su mapper. Si un controller necesita dos servicios, el caso de uso está mal delimitado.

3. **Un servicio = una responsabilidad.** Límites duros: **≤ ~150 líneas** por clase y **≤ 5 dependencias inyectadas**. Si se pasa, es un God Service: dividir en servicios por caso de uso (`PublicacionService`, `BusquedaService`) o extraer lógica a `domain` como métodos de entidad.

4. **Transaccionalidad en el método público del servicio**, nunca en controllers ni métodos privados:

```java
@Service @RequiredArgsConstructor
public class ReservaService {
    @Transactional
    public Reserva confirmar(Long inmuebleId, NuevaReservaCmd cmd) { ... }
}
```

`@Transactional(readOnly = true)` en métodos de solo lectura. Nunca `@Transactional` en bucles ni sobre `this.` interno (no corta por proxy).

5. **Identificadores consistentes en todo el proyecto:** nombres de clases, métodos y campos **en español, verbosos y sin abreviar** (`confirmarReserva`, `buscarInmueblesDisponibles`); sufijos técnicos en inglés (`InmuebleCreateRequest`, `InmuebleResponse`, `Row`); API de Spring en inglés (`@Service`, `save`). Comentarios, Javadoc y documentación OpenAPI **en español**. Prohibido mezclar idiomas dentro del mismo módulo.

```java
// GOOD
public Optional<Inmueble> porIdYActivo(Long id)
// BAD: abreviado / inglés suelto
public Inmueble getIt(Long id)
```

6. **Retornos tipados, nunca `null`.** `Optional<T>` en búsquedas de dominio, resultado explícito o excepción tipada. Los parámetros pueden ser `null` solo desde la capa HTTP (ya validados).

```java
// GOOD
public Optional<Inmueble> porId(Long id) { return repo.findById(id); }
// BAD
public Inmueble porId(Long id) { return repo.findById(id).orElse(null); }
```

7. **Errores con excepciones de dominio + `@RestControllerAdvice` → ProblemDetail (RFC 9457).** El servicio lanza, no construye respuestas HTTP.

```java
public class InmuebleNoEncontrado extends RuntimeException {
    public InmuebleNoEncontrado(Long id) { super("Inmueble " + id + " no encontrado"); }
}
// en application:
throw new InmuebleNoEncontrado(id);
// en api:
@ExceptionHandler(InmuebleNoEncontrado.class)
ProblemDetail noEncontrado(InmuebleNoEncontrado e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Recurso no encontrado");
}
```

Prohibido devolver `null`, `Optional.empty` como "error", o códigos HTTP inventados en el servicio.

8. **Logging con SLF4J, nunca `System.out`/`printStackTrace`:**

```java
@Slf4j                       // Lombok, preferido en el proyecto
@Service @RequiredArgsConstructor
public class PagoService {
    public void reembolsar(Long id) {
        log.warn("Reembolso solicitado para reserva {}", id);
        ...
    }
}
```

Si no se usa Lombok: `private static final Logger log = LoggerFactory.getLogger(PagoService.class);`. Los mensajes en español, con `{}` en lugar de concatenación, sin datos sensibles (tokens, contraseñas, DNI completos).

9. **Sin código muerto:** nada de métodos privados sin llamadas, imports sin usar, `TODO` viejos, código comentado bloque a bloque, flags de feature nunca usados. Si se comenta >2 líneas, se borra (git lo guarda).

10. **Funciones puras cuando sea posible:** lógica de cálculo (precios, filtros, conversión) como métodos sin efectos secundarios, testeables sin contexto Spring. Los efectos (BD, red) quedan al final del método.

11. **Imports entre módulos:** un servicio solo importa `api` de otros módulos (o nada). Prohibido importar `domain`/`infra` de otro módulo (ArchUnit lo verifica). Los servicios no importan DTOs de otro módulo: los datos cruzan como registros de la `api` o eventos de dominio.

12. **Sin dependencias de presentación:** `domain`/`application` no conocen `ResponseEntity`, `HttpServletRequest`, ni tipos de springdoc. Los DTOs viven en `api`.

13. **Llamadas externas fuera de `@Transactional`** (timeout no aborta la transacción) y tras un puerto de `domain` con adaptador en `infra` — ver skills `rendimiento` y `microservicios`.

## Ejemplo completo

```java
// domain
@Entity @Table(name = "inmuebles")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inmueble {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String titulo;
    private BigDecimal precio;
    @Enumerated(EnumType.STRING) private EstadoInmueble estado;
    private OffsetDateTime deletedAt;

    public void publicar() {
        if (estado != EstadoInmueble.BORRADOR) throw new ReglaDeNegocio("Solo se publican borradores");
        this.estado = EstadoInmueble.PUBLICADO;
    }
}
public interface InmuebleRepository extends JpaRepository<Inmueble, Long> {
    Optional<Inmueble> findByIdAndDeletedAtIsNull(Long id);
}

// application
@Service @RequiredArgsConstructor
@Slf4j
public class PublicacionService {
    private final InmuebleRepository repo;

    @Transactional
    public Inmueble publicar(Long id) {
        Inmueble inmueble = repo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new InmuebleNoEncontrado(id));
        inmueble.publicar();                       // regla en domain, no aquí
        log.info("Inmueble {} publicado", id);
        return inmueble;
    }
}

// api
@RestController @RequestMapping("/api/v1/inmuebles/{id}/publicacion")
@RequiredArgsConstructor
@Validated
public class PublicacionController {
    private final PublicacionService service;
    private final InmuebleMapper mapper;

    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<InmuebleResponse> publicar(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(service.publicar(id)));
    }
}
```

## Checklist de revisión de un servicio

- [ ] Controller con ≤ 4 líneas por método, sin `@Transactional` ni repositorios.
- [ ] Servicio con una responsabilidad, ≤ ~150 líneas y ≤ 5 dependencias inyectadas.
- [ ] `@Transactional` solo en métodos públicos del servicio (`readOnly = true` en lecturas).
- [ ] Retornos sin `null` (`Optional` o excepción tipada); errores con excepciones de dominio → `ProblemDetail`.
- [ ] Logging con SLF4J/`@Slf4j`; cero `System.out`, `printStackTrace`, código muerto o comentarios muertos.
- [ ] Nombres de identificadores en español y consistentes; comentarios y docs en español.
- [ ] Sin imports del interior de otros módulos ni DTOs ajenos en servicios.
- [ ] Entidades/repositorios en `domain`, adaptadores en `infra`, orquestación en `application`.
