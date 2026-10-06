---
description: Ejecuta perf-auditor sobre el código y consultas, y resume cuellos de botella priorizados con su fix propuesto.
agent: build
---

Lanza la **auditoría de rendimiento** de **MantiInmobiliaria**.

Entrada del usuario: $ARGUMENTS (funcionalidad/listado/endpoint a revisar; vacío = el repo completo).

Pasos:

1. Lanza el subagente **`perf-auditor`** con el ámbito indicado. Recuérdale:
   - Leer `.opencode/skills/rendimiento/SKILL.md` y `.opencode/skills/consultas-eficientes/SKILL.md` antes de empezar.
   - Buscar: N+1, O(n²), llamadas externas sin timeout dentro de transacciones, cargas innecesarias, falta de índices para los listados, paginación `OFFSET` en feeds.
   - Evidenciar con `docker compose up -d` + `docker exec -it manti-postgres psql -U manti -d manti` → `EXPLAIN (ANALYZE, BUFFERS) <sql>` (solo lectura; si no hay datos, marcar "sin medición").
   - `edit: deny`: no aplicar ningún cambio.
2. **Resume** el informe.

**Salida esperada**:
- **Resumen ejecutivo**: nº de hallazgos por severidad y el top 3 de impacto.
- **Hallazgos priorizados**: `severidad | archivo:línea | hallazgo | evidencia (EXPLAIN/tiempo) | impacto | fix propuesto`.
- **Métricas clave**: tiempos de consulta medidos antes (los "después" quedan pendientes de aplicar los fixes).
- **Comandos ejecutados** y métricas no obtenidas.
- **Plan de optimización** ordenado por ROI, sin aplicar.
