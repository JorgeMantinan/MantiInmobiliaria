---
name: rendimiento
description: Use when the user mentions rendimiento, performance, endpoint lento, lentitud, threads, virtual threads, cache, async, batching, profiling o auditoria perf del backend.
---

# Rendimiento de código backend (auditoría)

Stack fijo: Spring Boot 4.1 MVC (servlet), Java 25, Hibernate, Hikari, Actuator + Micrometer. Regla base: **cachear y optimizar solo tras medir**.

## Reglas

1. **Complejidad y O(n²) accidental.** Prohibido bucles anidados sobre colecciones con búsqueda lineal dentro. Usar `HashSet`/`HashMap` (o `Collectors.toMap`) para join en memoria.

```java
// BAD: O(n*m)
for (Inmueble i : inmuebles)
    for (Ciudad c : ciudades) if (c.getId().equals(i.getCiudadId())) { ... }

// GOOD: O(n+m)
Map<Long, Ciudad> porId = ciudades.stream().collect(toMap(Ciudad::getId, identity()));
for (Inmueble i : inmuebles) { Ciudad c = porId.get(i.getCiudadId()); ... }
```

Revisar también `List.contains()` en bucle (→ `HashSet`), `String +` en bucle (→ `StringBuilder`) y copias repetidas de listas.

2. **Timeout obligatorio en toda llamada externa.** Nada de `RestClient`/`RestClient.Builder` sin timeout (por defecto esperan indefinidamente y agotan el pool de hilos HTTP).

```java
@Bean
RestClient restClient(RestClient.Builder b) {
    return b.requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build())).build();
}
// Spring Boot también: spring.http.client.connect-timeout / read-timeout
```

3. **Nunca llamada externa dentro de `@Transactional`.** Mantener la transacción abierta mientras se llama a un tercero multiplica el tiempo de ocupación de conexiones de Hikari y provoca deadlocks. Patrón: persistir + commit, y el efecto externo después (outbox o `TransactionSynchronization.afterCommit`).

```java
// BAD
@Transactional public void guardar() { repo.save(x); proveedor.enviar(x); } // red dentro de la tx
// GOOD
public void guardar() { transaccionalInterno(x); outbox.encolar(x); }
```

4. **Virtual threads (`spring.threads.virtual.enabled=true`):** ayudan cuando los hilos HTTP esperan I/O bloqueante (llamadas externas, disco). **No** ayudan con CPU-bound ni con consumo de memoria de Hibernate; no sustituyen a `@Async` con cola. Activarlo en dev/prod y medir; prohibido `new Thread(...)`.

5. **Batching de escrituras.** `saveAll` sobre listas grandes + JDBC batch:

```yaml
spring:
  jpa:
    properties:
      hibernate.jdbc.batch_size: 50
      hibernate.order_inserts: true
      hibernate.order_updates: true
```

Prohibido `save()` dentro de bucle sin flush/refresh por lotes (1 insert = 1 round-trip). En importaciones masivas, JDBC directo o `stateless` por tramos de 500.

6. **`@Async` con executor configurado y acotado.** Nunca `@Async` sin bean `ThreadPoolTaskExecutor` (usa el simple de 8 hilos) ni sobre métodos `public` llamados desde la misma clase (no pasa por proxy). Prohibido `@Async` con `@Transactional` (el hilo devuelve antes del commit: ver eventos `AFTER_COMMIT`).

```java
@Bean("notificaciones")
ThreadPoolTaskExecutor notificaciones() {
    var ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(4); ex.setMaxPoolSize(8); ex.setQueueCapacity(200);
    ex.setThreadNamePrefix("notif-"); ex.initialize();
    return ex;
}
@Async("notificaciones") public void enviar(Correo c) { ... }
```

Cola acotada (`queueCapacity`) = backpressure: mejor rechazar/encolar que OOM.

7. **Streaming para respuestas grandes** (exportes CSV/Excel de inmuebles): `StreamingResponseBody` + `fetchSize` en JDBC, nunca acumular millones de filas en `List`.

```java
@GetMapping(value = "/export", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
StreamingResponseBody exportar() { return out -> exportador.escribir(out); }
```

```yaml
spring.datasource.hikari.maximum-pool-size: 10   # no subir sin medir
spring.jpa.properties.hibernate.jdbc.fetch_size: 100
```

8. **Caché con claves bien definidas y TTL.** `@Cacheable` solo tras medir (ver skill `consultas-eficientes`): `cacheNames` por dominio, `key` explícita (nunca el `toString` del objeto), `unless`/`condition` para no cachear vacíos, TTL corto en la configuración de Caffeine, `@CacheEvict` en toda escritura que afecte.

```java
@Cacheable(cacheNames = "inmueble", key = "#id", unless = "#result.isEmpty()")
public Optional<Inmueble> porId(Long id) { ... }
```

9. **Métricas para encontrar el endpoint lento:** Actuator + Micrometer ya incluidos.

```
GET /actuator/metrics/http.server.requests
GET /actuator/metrics/http.server.requests?tag=uri:/api/v1/inmuebles
GET /actuator/metrics/http.server.requests?tag=uri:/api/v1/inmuebles&tag=method=GET
GET /actuator/prometheus   # si se añade micrometer-registry-prometheus
```

Exigir percentiles (`management.metrics.distribution.percentiles: [0.5, 0.95, 0.99]`) y mirar `p99`, no la media. También `hikaricp_connections_active`, `jvm.memory.used` y `process.cpu.usage`.

10. **Profiling:**
    - Actuator: métricas y `/actuator/health` en dev.
    - JFR: `-XX:StartFlightRecording=duration=120s,filename=app.jfr` en local o en un contenedor de diagnóstico; abrir con JDK Mission Control.
    - VisualVM/`jcmd` para heap y hilos; `jcmd <pid> Thread.print` si hay bloqueos.
    - Hibernate `generate_statistics: true` solo en dev (ver skill `consultas-eficientes`).

11. **Prohibiciones duras en código de producción:**
    - `new Thread(...)` o `Executors.newFixedThreadPool` sueltos (usar los beans de Spring).
    - `Thread.sleep` como sincronización/espera de estado.
    - Leer ficheros sin buffer (`Files.readAllLines` sobre ficheros grandes → streams con buffer).
    - Logs en bucles de hot path; `debug` con concatenación cara (`log.debug("x " + obj)`).
    - Clase `@Component` singleton con estado mutable (los virtual threads lo comparten).

## Checklist de auditoría perf de un endpoint

1. [ ] Métrica de `http.server.requests` obtenida: `p50/p95/p99` y nº de llamadas del endpoint concreto.
2. [ ] Queries revisadas (N+1, `EXPLAIN (ANALYZE, BUFFERS)`, índices) — skill `consultas-eficientes`.
3. [ ] Sin llamadas externas dentro de `@Transactional` y con timeout configurado.
4. [ ] Sin bucles anidados O(n²) sobre colecciones (mapas/sets en su lugar).
5. [ ] Listado sin colecciones lazy cargadas y con proyección a DTO; streaming si el volumen es grande.
6. [ ] Escrituras en lote (`saveAll` + `hibernate.jdbc.batch_size`) cuando son masivas.
7. [ ] `@Async` con executor propio acotado; sin `new Thread` ni `Thread.sleep`.
8. [ ] Caché solo si hubo medida previa, con `key` explícita, TTL y `@CacheEvict` en la escritura; re-medir p99 después del cambio.
