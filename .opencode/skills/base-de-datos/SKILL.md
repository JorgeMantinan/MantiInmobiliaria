---
name: base-de-datos
description: Use when the user mentions base de datos, PostgreSQL, Flyway, migracion, tabla, indice, constraint, esquema, DDL o modelado de datos.
---

# Modelado PostgreSQL 18 + Flyway (MantiInmobiliaria)

Stack fijo: PostgreSQL 18, Flyway (`spring-boot-starter-flyway`), `spring.jpa.hibernate.ddl-auto: validate` (ya en `application.yml`), Testcontainers en tests.

## Reglas

1. **Naming snake_case** en tablas, columnas e índices. Tablas en plural (`inmuebles`, `imagenes_inmueble`); sin prefijos `tbl_`; sin mayúsculas (habría que citar siempre el identificador).

```sql
-- GOOD
CREATE TABLE inmuebles (...);
-- BAD
CREATE TABLE "Inmueble" (...); CREATE TABLE tbl_inmueble (...);
```

2. **PK `bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY`.** Alternativa **UUID v7** solo si se justifica: identificadores expuestos en URLs públicas que no deben ser enumerables, o claves generadas en cliente/offline. No usar UUID v4 como default (peor locality en índices). Documentar la elección en la migración.

```sql
id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY
-- o, justificado:
id uuid PRIMARY KEY DEFAULT gen_random_uuid()   -- v7: extensión uuidv7() en PG18
```

3. **Siempre `timestamptz`, nunca `timestamp` sin zona.** `timestamp` guarda el literal sin offset y rompe con usuarios en varias zonas.

4. **Enums de aplicación:** preferir tabla lookup o `CHECK (col IN (...))`. Prohibido `CREATE TYPE ... AS ENUM` salvo justificación (tipos inmutables, no se puede añadir valor sin migración de tipo). Si el dominio cambia con frecuencia → lookup con FK.

```sql
estado text NOT NULL CHECK (estado IN ('BORRADOR','PUBLICADO','RESERVADO','VENDIDO'))
-- o
CREATE TABLE estados_inmueble (codigo text PRIMARY KEY, descripcion text NOT NULL);
```

5. **`NOT NULL` por defecto.** Solo `NULL` cuando el concepto es "desconocido/no aplica" (fecha de venta de un inmueble en alquiler). `UNIQUE` donde el dominio lo exige (`email usuarios`).

6. **`CHECK` para rangos y coherencia** (siempre con nombre, para poder diagnosticarlo):

```sql
CONSTRAINT ck_inmuebles_precio_positivo CHECK (precio > 0),
CONSTRAINT ck_inmuebles_superficie CHECK (superficie_m2 > 0)
```

7. **FK con `ON DELETE` deliberado: siempre se escribe explícito.**
   - `RESTRICT` (o `NO ACTION`) por defecto: impedir borrados que dejen huérfanos.
   - `CASCADE` solo cuando el hijo no tiene sentido solo (imágenes del inmueble, líneas de una reserva cancelada).
   - `SET NULL` solo si el padre es opcional por negocio.
   Prohibido olvidar la cláusula (queda `NO ACTION` implícito y la intención no queda documentada).

8. **Índices:**
   - Toda FK usada en JOIN o filtro lleva índice (PG **no** indexa FKs automáticamente).
   - Índices compuestos según patrones de query reales, en el **orden** de la query: filtro igualdad primero, rango/tiempo después (`(estado, precio)`, `(inmueble_id, created_at)`).
   - Índice parcial para soft-delete: `CREATE INDEX ... WHERE deleted_at IS NULL`.
   - Índices únicos parciales para constraints de negocio (`UNIQUE (usuario_id) WHERE activo`).

```sql
CREATE INDEX idx_inmuebles_ciudad_estado_precio ON inmuebles (ciudad_id, estado, precio)
  WHERE deleted_at IS NULL;
```

9. **`CREATE INDEX CONCURRENTLY` no funciona dentro de una transacción Flyway** (Flyway envuelve cada migración). Estrategia: en tablas ya pobladas y con tráfico, crear el índice en una migración y ejecutarla con `spring.flyway.execute-in-transaction: false` solo si es puntual, o (preferido) crearlo `CONCURRENTLY` en consola y registrarlo; en desarrollo/tablas nuevas, índice normal en la misma migración. Nunca bloquear producción con un `CREATE INDEX` sin evaluar tamaño.

10. **Flyway:**
    - Rutas: `backend/src/main/resources/db/migration/`.
    - Migraciones versionadas inmutables: `V1__crear_tabla_inmuebles.sql`, `V2__indice_precio.sql`. **NUNCA editar** una migración ya aplicada (ni siquiera en dev compartido): se rompe el checksum → `FlywayValidateException`. Correcciones = migración nueva (`V3__...`).
    - `R__` repeatable solo para vistas, funciones, triggers e índices que conviene regenerar (`R01__vistas_listados.sql`); se re-ejecutan al cambiar su hash.
    - Orden por número (`V10` después de `V9`); usar padding cómodo para no quedarse sin números.
    - Un archivo = un cambio coherente con mensaje descriptivo en snake_case.

```sql
-- V3__inmuebles_add_deleted_at.sql
ALTER TABLE inmuebles ADD COLUMN deleted_at timestamptz;
CREATE INDEX idx_inmuebles_activos ON inmuebles (id) WHERE deleted_at IS NULL;
```

11. **Seeds separados del esquema:** los datos de ejemplo van a un perfil `dev` (`db/dev/seed.sql` ejecutado por un `ApplicationRunner` condicionado con `@Profile("dev")` o `spring.flyway.locations: classpath:db/migration,classpath:db/dev`), nunca en `V*` de producción. Los tests usan Testcontainers con sus propias fixtures.

12. **`ddl-auto: validate` SIEMPRE, en todos los perfiles y entornos.** Prohibido `update`/`create`/`create-drop` en cualquier entorno (genera esquema divergente, pierde constraints, rompe con `validate` en prod). El esquema lo define exclusivamente Flyway.

13. **Auditoría de columnas en toda tabla de negocio:**

```sql
created_at  timestamptz NOT NULL DEFAULT now(),
updated_at  timestamptz NOT NULL DEFAULT now(),
deleted_at  timestamptz            -- soft-delete opcional
```

`updated_at` se actualiza con trigger o `@UpdateTimestamp`/`@PreUpdate` de Hibernate. Soft-delete: filtrar siempre con `deleted_at IS NULL` (ámbito JPA `@Where` o `@SQLRestriction`) e índice parcial.

14. **Sin tipos de dominio del ORM en la BD:** `@Column(name = "precio", precision = 12, scale = 2)` alineado con `numeric(12,2)`; moneda nunca en `float`/`double` (error de redondeo) → `numeric` o `bigint` céntimos.

## Checklist de modelado de una tabla nueva

- [ ] Nombre snake_case plural, sin prefijos, PK `bigint GENERATED ALWAYS AS IDENTITY` (o UUID v7 justificado).
- [ ] Todas las fechas `timestamptz`; moneda `numeric`/céntimos enteros, nunca `float`.
- [ ] `NOT NULL` en todo lo obligatorio; `CHECK` con nombre para rangos; `UNIQUE` donde aplique.
- [ ] Toda FK con `ON DELETE` explícito (`RESTRICT`/`CASCADE`) y su índice creado.
- [ ] `created_at`, `updated_at` y `deleted_at` (+ índice parcial `WHERE deleted_at IS NULL` si hay soft-delete).
- [ ] Índices diseñados sobre los `EXPLAIN` de las queries reales de listado.
- [ ] Migración nueva `V<n>__descripcion.sql` inmutable; ningún `V*` anterior tocado.
- [ ] `ddl-auto: validate` intacto; seeds fuera de `V*` (perfil `dev`).
