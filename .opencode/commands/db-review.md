---
description: Ejecuta db-auditor y perf-auditor sobre entidades/consultas con EXPLAIN ANALYZE local y resume hallazgos con SQL propuesto.
agent: build
---

Revisa la **capa de datos** de **MantiInmobiliaria** (modelado, consultas y planes de ejecución).

Entrada del usuario: $ARGUMENTS (módulo, tabla o consulta a revisar; vacío = todo el backend).

Pasos:

1. Prepara la BD local: `docker compose up -d` (PostgreSQL 18, `manti`/`manti_dev`, puerto 5432).
2. Lanza en paralelo (o secuencialmente) los subagentes:
   - **`db-auditor`**: entidades JPA, JPQL/SQL, migraciones Flyway (`backend/src/main/resources/db/migration/`), índices y modelado. Exígele **`EXPLAIN (ANALYZE, BUFFERS)`** real contra la BD local vía `docker exec -it manti-postgres psql -U manti -d manti` (solo lectura) y que proponga la migración `Vn__...sql` **sin aplicarla**.
   - **`perf-auditor`**: N+1, O(n²), llamadas externas sin timeout en transacciones, cargas innecesarias, listados sin índice y paginación offset en feeds; también con EXPLAIN local.
   - Ambos con `edit: deny`: nada se modifica.
3. Consolida y **resume**.

**Salida esperada**:
- **Estado de la BD**: migraciones aplicadas, nº de tablas, si había datos para medir (si no, indicarlo).
- **Hallazgos priorizados** (Crítico/Alto/Medio/Bajo): `archivo:línea | hallazgo | evidencia (plan EXPLAIN) | impacto | fix propuesto`.
- **Migración propuesta**: nombre `V<n>__...sql` + SQL completo, **no aplicada**.
- **Comandos ejecutados** y consultas no medibles (motivo).
- **Plan de aplicación sugerido**: orden por impacto (sin ejecutar).
