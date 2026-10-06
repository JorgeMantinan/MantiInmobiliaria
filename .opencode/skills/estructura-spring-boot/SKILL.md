---
name: estructura-spring-boot
description: Use when creating or reviewing estructura del backend Java 25 / Spring Boot 4.1 modular monolith: paquetes api/application/domain/infra, reglas de dependencia, ArchUnit, configuración y módulos nuevos.
---

# Estructura del backend (monolito modular Spring Boot)

## Reglas

1. **Paquetes por módulo de dominio.** Módulos: `inmuebles`, `usuarios`, `swipes`, `profesionales`, `notificaciones`, `shared`. Cada módulo se subdivide en 4 capas:

```
es.manti.inmuebles
├── api           # controllers + dtos (público)
├── application   # services, casos de uso (público)
├── domain        # entidades, repositorios, puertos, excepciones (núcleo)
└── infra         # adaptadores externos, config, repos JPA (interno)
```

2. **Reglas de dependencia.** `api → application → domain ← infra`. El `domain` NO depende de `api` ni de `infra`; `infra` implementa los puertos definidos en `domain`.

```java
// GOOD: infra implementa un puerto de domain
package es.manti.inmuebles.infra.adapter;
public class TelegramNotificador implements NotificadorPort { ... }

// BAD: domain importa infra
package es.manti.inmuebles.domain;
import es.manti.inmuebles.infra.adapter.TelegramNotificador; // prohibido
```

3. **API pública de módulo.** Sólo `api` (o `application`) es visible para otros módulos. Usa `@NamedInterface` o paquetes `...internal` para marcar lo interno; está prohibido el import cruzado de `internal`.

4. **Compartido solo en `shared`.** Utilidades genéricas (`es.manti.shared.*`: auditoría, errores comunes, paginación). Nunca poner lógica de dominio específico en `shared` ni copiar código entre módulos.

5. **Controllers.** 1 controller por recurso, delgados: validan, mapean y delegan siempre al service. Nunca lógica de negocio ni JPA en el controller.

```java
@RestController @RequestMapping("/api/v1/inmuebles")
class InmuebleController {
  private final InmuebleService service; // delega siempre
  @GetMapping ResponseEntity<PageResponse<InmuebleDto>> buscar(...) { return ResponseEntity.ok(service.buscar(q)); }
}
```

6. **Configuración.** Paquete `config/` con un archivo por tema: `SecurityConfig`, `OpenApiConfig`, `AsyncConfig`, `JacksonConfig`. Nada de `@Configuration` suelto fuera de `config/`.

7. **Main class en la raíz.** `es.manti.MantiApplication` (una sola), con `@SpringBootApplication` y `@EntityScan`/`@EnableJpaRepositories` amplios si los paquetes se escalonan.

8. **ArchUnit como test.** Reglas automáticas de arquitectura en `src/test/java`:

```java
@AnalyzeClasses(packages = "es.manti", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {
  @Test void domain_no_depende_de_api_ni_infra() {
    noClasses().that().resideInAPackage("..domain..")
      .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infra..")
      .check(CLASSES);
  }
  @Test void infra_no_es_visible_para_api_de_otros_modulos() {
    noClasses().that().resideInAPackage("..inmuebles.api..")
      .should().dependOnClassesThat().resideInAPackage("..usuarios.infra..")
      .check(CLASSES);
  }
}
```

9. **Convención de nombres.** `XController`, `XService`, `XMapper` (MapStruct), `XRepository` (puerto en domain / adaptador JPA en infra), `XEntity` + `@Table(name = "x")` en snake_case, `XDto`/`XRequest`/`XResponse`, `XPort` para puertos, `XException` + `XErrorHandler`.

10. **Añadir un MÓDULO nuevo — checklist de archivos.**
    1. `es.manti.<modulo>.<ApplicationService>` (application)
    2. `es.manti.<modulo>.<Recurso>Controller` + dtos de request/response (api)
    3. `es.manti.<modulo>.domain.<Entidad>` (@Entity), `<Recurso>Port`, `<Recurso>Repository` (domain)
    4. `es.manti.<modulo>.infra.adapter.<X>JpaRepository` + `<X>Mapper` (infra)
    5. `es.manti.<modulo>.domain.<X>Exception` y su manejo en el `@RestControllerAdvice` compartido
    6. Entrada en `OpenApiConfig` (`@Tag` del módulo) y en `SecurityConfig` (reglas de autorización)
    7. Test de ArchUnit de módulo + tests de servicio/API
    8. Spec OpenAPI actualizada en el MISMO PR (ver skill openapi-docs)

11. **Árbol de ejemplo (2 módulos).**

```
src/main/java/es/manti/
├── MantiApplication.java
├── config/{SecurityConfig,OpenApiConfig,AsyncConfig}.java
├── shared/{error,pagination,audit}/...
├── inmuebles/
│   ├── api/{InmuebleController, dto/}
│   ├── application/InmuebleService.java
│   ├── domain/{Inmueble, InmueblePort, InmuebleSpecification, InmuebleException}
│   └── infra/{adapter/InmuebleJpaRepository, config/InmuebleConfig}
└── usuarios/
    ├── api/{UsuarioController, dto/}
    ├── application/UsuarioService.java
    ├── domain/{Usuario, UsuarioPort}
    └── infra/adapter/UsuarioJpaRepository.java
```

## Checklist de revisión

- [ ] Capas `api/application/domain/infra` por módulo, `domain` sin dependencias hacia `api`/`infra`
- [ ] Imports cruzados de `internal` prohibidos; compartido sólo en `shared`
- [ ] Controller por recurso, delgado, delegando al service
- [ ] Configuración agrupada en `config/` por tema; main class en `es.manti`
- [ ] ArchUnit verde (reglas de capa y de módulo)
- [ ] Nombres según convención (`XController`, `XService`, `XEntity`/tabla snake_case)
- [ ] Módulo nuevo completo (archivos 1-8 del paso 10)
