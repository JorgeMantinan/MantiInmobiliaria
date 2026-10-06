---
name: microservicios
description: Use when the user mentions microservicios, outbox, eventos de dominio, saga, idempotencia, anti-corruption layer, strangler o extraer un servicio del monolito.
---

# Patrones de microservicios aplicados a un monolito modular

Decisión de arquitectura (ADR): este proyecto es un **monolito modular** con Spring Modulith sobre Spring Boot 4.1 MVC, no microservicios. Se aplica la disciplina de módulos sin pagar el coste de la red.

## Reglas

1. **No extraer un servicio hasta que se cumpla al menos un umbral real.** Criterios de extracción (todos medidos, no intuición):
   - Escala desigual sostenida: un componente necesita ~10x los recursos del resto y vertical scaling no basta.
   - Aislamiento de fallos exigido por negocio (el proveedor externo no puede tumbar el alta de inmuebles).
   - Despliegues independientes con >50-100 desarrolladores y cadencias de release incompatibles.
   - Requisito regulatorio/latencia geográfica explícito.
   Si no hay ninguno, la respuesta es **no extraer**: red = latencia, timeouts, sagas distribuidas, CI/CD duplicado.

2. **Módulos aislados en código: prohibido importar entre módulos internos.** Cada módulo expone una `api` pública (clases Java del paquete `api`); todo lo demás (`application`, `domain`, `infra` internos) es privado. ArchUnit lo verifica:

```java
@AnalyzeClasses(packages = "es.manti", importOptions = ImportOption.DoNotIncludeTests.class)
class ModularityTest {
    @ArchTest
    static final ArchRule modules_no_importan_internos =
        noClasses().that().resideInAPackage("..application..")
            .and().resideOutsideOfPackage("..api..")
            .should().bePublic();
}
```

3. **Sin RPC/HTTP entre módulos.** Un módulo llama a otro solo vía su API pública (`api`), como llamada de método en proceso. Nada de `RestTemplate`/`RestClient` internos, nada de colas internas innecesarias: los problemas de red son los que los microservicios traen.

4. **Eventos de dominio en proceso, publicados SOLO tras el commit.** Uso de `ApplicationEvents` + `@TransactionalEventListener(phase = AFTER_COMMIT)`; si no, un rollback deja notificaciones fantasma.

```java
@Service @RequiredArgsConstructor
public class InmuebleService {
    private final ApplicationEventPublisher events;
    @Transactional
    public Inmueble publicar(Long id) {
        Inmueble i = repo.findById(id).orElseThrow(() -> new InmuebleNoEncontrado(id));
        i.cambiarEstado(estado.Publicado);
        events.publishEvent(new InmueblePublicado(i.getId(), i.getPrecio())); // dentro de la tx
        return i;
    }
}

@Component
class NotificacionListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("notificaciones") // executor acotado, ver skill rendimiento
    void on(InmueblePublicado e) { ... }
}
```

5. **Transactional Outbox para efectos externos** (email, Telegram, webhooks): la fila de outbox se escribe **en la misma transacción** que el cambio de negocio; un poller/`@Scheduled` la envía y marca `processed_at`. Evita perder notificaciones si el proceso muere entre commit y envío.

```sql
CREATE TABLE outbox_event (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  aggregate_type text NOT NULL,
  aggregate_id   bigint NOT NULL,
  event_type     text NOT NULL,
  payload        jsonb NOT NULL,
  created_at     timestamptz NOT NULL DEFAULT now(),
  processed_at   timestamptz,
  attempts       int NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_pendientes ON outbox_event (id) WHERE processed_at IS NULL;
```

6. **Anti-Corruption Layer para proveedores externos** (portales, APIs de terceros): definir un **puerto** en `domain` y el adaptador en `infra`. El dominio nunca ve tipos del proveedor.

```java
// domain: puerto
public interface ListingProvider {
    List<ListingDraft> fetchPending();
}
// infra: adaptador (RestClient con timeout) — único sitio que conoce el DTO externo
@Component
class IdealistaAdapter implements ListingProvider { ... }
```

7. **Idempotencia de endpoints que crean recursos:** cabecera `Idempotency-Key` + tabla de claves con respuesta congelada; repetir la misma petición devuelve la misma respuesta sin duplicar el efecto.

```java
@PostMapping("/reservas", headers = "Idempotency-Key")
public ResponseEntity<ReservaResponse> reservar(@RequestHeader("Idempotency-Key") String key, ...) {
    Optional<ReservaResponse> previa = idempotency.lookup(key);
    if (previa.isPresent()) return ResponseEntity.ok(previa.get()); // replay
    ...
}
```

8. **Contratos versionados de API** (`/api/v1`), documentados con springdoc 3.1.1 y semántica de cambio: aditivo dentro de v1, rompimiento → v2. El contrato es la frontera entre futuros servicios.

9. **Saga local con compensación** para procesos multi-módulo: un orquestador en `application` encadena pasos y ejecuta la compensación si un paso falla (p.ej. publicación + pago + notificación). Sin dos fases globales: cada paso es su propia `@Transactional`.

```java
try { paso1(); paso2(); paso3(); }
catch (RuntimeException e) { compensar2(); compensar1(); throw e; }
```

10. **Cómo sería la extracción futura (strangler fig):** primero métricas (`http.server.requests` por endpoint, tiempos de CPU/heap por paquete), después acoplamiento (ArchUnit + análisis de imports y eventos entre módulos), luego duplicar el módulo tras un adaptador remoto que implemente el mismo puerto, y desviar el tráfico gradualmente. Nunca reescribir en paralelo sin feature flag.

11. **Advertencias duras:**
    - Prohibido `@Transactional` sobre métodos privados o en bucles (la transacción no aplica).
    - Prohibido abrir una transacción por módulo "por si acaso": coordinar en `application` una sola transacción mientras el monolito sea monolito.
    - Prohibido compartir entidades JPA entre módulos (solo DTOs/eventos de la `api`).
    - Prohibido levantar RabbitMQ/Kafka "para parecer microservicios": el outbox + poller en proceso cubre el caso real.

## Checklist de revisión

- [ ] No se ha propuesto extraer ningún servicio sin umbral medido (escala, fallo, >50-100 devs, regulatorio).
- [ ] ArchUnit falla si un módulo importa el interior de otro módulo.
- [ ] Ninguna llamada interna pasa por HTTP/RPC.
- [ ] Los eventos se consumen con `@TransactionalEventListener(AFTER_COMMIT)`.
- [ ] Efectos externos (email/Telegram) salen de una tabla outbox en la misma transacción.
- [ ] Proveedores externos tras un puerto en `domain` + adaptador en `infra` (ACL).
- [ ] Endpoints de creación con efecto aceptan `Idempotency-Key`.
- [ ] Procesos multi-paso con compensación explícita y rutas versionadas `/api/v1`.
