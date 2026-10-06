---
name: consultas-eficientes
description: Use when the user mentions consultas lentas, N+1, paginacion, rendimiento de queries, EXPLAIN, indices, cache, JPA Hibernate o listados que tardan.
---

# Consultas eficientes (JPA/Hibernate + PostgreSQL 18)

Contexto: Spring Data JPA + Hibernate sobre PostgreSQL 18, `open-in-view: false` y `ddl-auto: validate` ya configurados en `application.yml`.

## Reglas

1. **Detectar N+1 siempre.** Señal: log con una `select` por fila del listado, o lista que tarda más cuanto más crece. Comprobar con estadísticas de Hibernate en dev:

```yaml
spring:
  jpa:
    properties:
      hibernate.generate_statistics: true   # solo dev
logging:
  level:
    org.hibernate.SQL: DEBUG
```

Solución preferida, por orden: `JOIN FETCH` (una query), `@EntityGraph` (query generated), `@BatchSize(size = 20)` en la colección (ronda extra, pero batcheada).

```java
// GOOD: una sola query
@Query("SELECT i FROM Inmueble i LEFT JOIN FETCH i.imagenes WHERE i.estado = :estado")
List<Inmueble> buscarActivos(@Param("estado") Estado estado);

// GOOD: fetch por grafo en el repositorio
@EntityGraph(attributePaths = {"imagenes", "ciudad"})
Page<Inmueble> findByEstado(Estado estado, Pageable pageable);

// BAD: recorrer lazy dentro de un bucle de presentación
listado.forEach(i -> i.getImagenes().size());   // N selects
```

2. **`open-in-view: false` es obligatorio y ya está.** Con `true` Hibernate mantiene la sesión abierta durante toda la petición HTTP: oculta el N+1, acopla la vista a JPA y agota conexiones. Cualquier `LazyInitializationException` se arregla arreglando el fetch (regla 1), no reactivando open-in-view.

3. **Proyecciones a DTOs, nunca entidades completas en listados.** Hibernate trae todas las columnas (`SELECT *` implícito) y todas las relaciones para luego desharlar. Usar constructor expression en `@Query` o proyección de interfaz.

```java
public interface InmuebleRow {
    Long getId(); String getTitulo(); BigDecimal getPrecio(); String getCiudad();
}

@Query("""
    SELECT i.id, i.titulo, i.precio, c.nombre
    FROM Inmueble i JOIN i.ciudad c
    WHERE i.estado = 'PUBLICADO' AND i.deletedAt IS NULL
    """)
List<InmuebleProjection> listarParaPortada(Pageable pageable);
```

Menos red, menos memoria, menos sesión abierta.

4. **Listado de inmuebles: cargar solo lo necesario, SIN imágenes.** La portada devuelve `InmuebleSummary` (id, título, precio, ciudad, 1ª imagen como URL opcional resuelta en una segunda query agrupada). Nunca `JOIN FETCH` de colecciones pesadas en un listado de 20 filas que luego se pagina.

5. **Paginación:**
   - Offset (`Pageable`, `LIMIT/OFFSET`) solo para páginas cortas y accesibles (admin, hasta ~página 20).
   - Keyset/cursor para feeds y cargas grandes: coste estable sin importar la profundidad.

```sql
SELECT id, titulo, precio, created_at
FROM inmuebles
WHERE deleted_at IS NULL
  AND (created_at, id) < (?, ?)      -- cursor: tuple comparison
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

Respuesta de API paginada: `content` + `totalPages` + `totalElements` (count solo si hace falta) + `nextCursor`. Evitar `COUNT(*)` en cada petición de feed: es la mitad del coste.

6. **`EXPLAIN (ANALYZE, BUFFERS)` obligatorio** en toda query nueva o modificada sobre tabla con datos. Qué mirar:
   - `Seq Scan` sobre tabla grande sin justificación → falta índice o el filtro no es sargable.
   - `rows estimadas` vs `rows actual` muy distintas → `ANALYZE` o estadísticas desactualizadas/falta índice por columna.
   - Uso de `Index Scan`/`Index Only Scan` y `Buffers: shared read` razonables.
   - `Sort` con `external merge` (spill a disco) → falta índice de orden o `work_mem` insuficiente.

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT ... ;
```

7. **Sin `SELECT *` de negocio.** `select *` en JPQL no existe, pero `findAll()` equivale: prohibido en endpoints; usar `findBy...` con criterio o proyección.

8. **Fetch de colecciones:** nunca iterar una colección lazy en código de presentación (controller, mapper, template). El fetch se resuelve en la query. Regla dura: cada `getX().getY()` dentro de un bucle sobre el resultado = posible N+1.

9. **Caché, en este orden:**
   1. Índice y query correcta (siempre primero).
   2. Caché HTTP/CDN (`Cache-Control`, ETag) para recursos públicos de inmuebles.
   3. Caffeine/Spring Cache solo con medida (p.ej. lookups baratos y muy repetidos: `GET /ciudades`).

```java
@Cacheable(cacheNames = "ciudades", key = "#paisId", unless = "#result.isEmpty()")
public List<Ciudad> porPais(Long paisId) { ... }

@CacheEvict(cacheNames = "ciudades", key = "#dto.paisId")
public Ciudad guardar(Ciudad dto) { ... }
```

Reglas de invalidación: **toda escritura que afecte al dato evicta la clave correspondiente** (`@CacheEvict` en el servicio transaccional); TTL corto (5-15 min) como red de seguridad; cachear solo respuestas estables (nada de datos de usuario autenticado).

10. **Hikari:** el pool de Spring Boot (`maximum-pool-size` 10) es correcto por defecto. No subir "para que vaya más": subir conexiones no arregla queries lentas y satura PostgreSQL. Subir solo con métricas que demuestren agotamiento (`hikaricp_connections_pending`).

11. **Auditoría de un listado lento, paso a paso:**
    1. `spring.jpa.properties.hibernate.generate_statistics: true` + `org.hibernate.SQL: DEBUG` en dev → contar queries y tiempos.
    2. Identificar la query exacta y pegarla en `EXPLAIN (ANALYZE, BUFFERS)` sobre datos reales (o de staging).
    3. Activar `pg_stat_statements` (`shared_preload_libraries = 'pg_stat_statements'`) → `SELECT query, calls, mean_exec_time FROM pg_stat_statements ORDER BY mean_exec_time * calls DESC LIMIT 20;`
    4. Corregir en este orden: N+1 → proyección/fetch → índice → paginación keyset → caché.
    5. Re-medir y dejar el `EXPLAIN` antes/después en la PR.

## Checklist de revisión

- [ ] Cero lazy recorridos en bucle; `@EntityGraph`/`JOIN FETCH`/`@BatchSize` donde había N+1.
- [ ] Ningún `findAll()` ni entidad completa devuelta en listados: proyección a DTO.
- [ ] El listado de inmuebles no precarga imágenes ni colecciones pesadas.
- [ ] Paginación real: `content` + `totalPages` + `nextCursor`; offset solo en páginas cortas.
- [ ] `open-in-view` sigue en `false`; ningún `LazyInitializationException` "arreglado" reabriendo la sesión.
- [ ] `EXPLAIN (ANALYZE, BUFFERS)` ejecutado y adjunto para toda query nueva/modificada.
- [ ] Caché solo con medida y con `@CacheEvict` en la escritura correspondiente.
- [ ] Hikari no ampliado sin métrica que lo justifique.
