---
description: Ejecuta la auditoría de seguridad con security-auditor y resume hallazgos priorizados con fixes propuestos.
agent: build
---

Lanza la **auditoría de seguridad** de **MantiInmobiliaria**.

Entrada del usuario: $ARGUMENTS (ámbito opcional: backend, frontend, CI, dependencias; si está vacío, audita todo).

Pasos:

1. Lanza el subagente **`security-auditor`** con el ámbito indicado (o el completo). Pásale:
   - Leer `.opencode/skills/seguridad/SKILL.md` antes de empezar.
   - Ejecutar solo comandos de lectura/análisis: `gitleaks detect --source . --no-git`, `cd frontend && npm audit`, dependency-check de Maven si es viable, `grep` sobre `backend/src`, `frontend/src` y `.github/`.
   - **No aplicar fixes** (`edit: deny`); máscara los secretos reales en el informe.
2. Recibe el informe y **resume** aquí.

**Salida esperada**:
- **Resumen ejecutivo**: nº de hallazgos por severidad (Crítico / Alto / Medio / Bajo) y riesgo global.
- **Hallazgos priorizados** en tabla: `severidad | archivo:línea | hallazgo | OWASP | fix propuesto`.
- **Hallazgos Crítico/Alto** con el detalle completo (evidencia + fix).
- **Comandos ejecutados** y los no ejecutados (y el motivo).
- **Siguiente paso sugerido**: quién debería aplicar cada fix (p. ej. `feature-developer`, `git-flow-agent`) — sin aplicarlo.
