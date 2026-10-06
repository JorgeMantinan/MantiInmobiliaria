---
description: Revisa la rama actual o el PR indicado con clean-code-reviewer y security-auditor, y emite un veredicto único.
agent: plan
---

Revisa el código de **MantiInmobiliaria** y emite un **veredicto de revisión**.

Entrada del usuario: $ARGUMENTS (PR número/URL o rama; si está vacío, la rama actual contra `develop`).

Fase 1 — **Preparación** (solo lectura):
1. Identifica el diff:
   - Si se indica PR → `gh pr view <n> --json title,body,headRefName,baseRefName` y `gh pr diff <n>` (si `gh` no está autenticado, pide la rama).
   - Si no → rama actual: `git status`, `git branch --show-current`, `git diff develop...HEAD`, `git log --oneline develop..HEAD`.
2. Si `develop` no existe o el diff está vacío, repórtalo y detente.

Fase 2 — **Revisiones en paralelo**:
3. **`clean-code-reviewer`** sobre ese diff, contra las skills (`clean-code-servicios`, `clean-code-componentes`, `estructura-*`, `api-rest`, `testing`, `git-flow`), con checklist, hallazgos `archivo:línea` + severidad y veredicto `APPROVE`/`REQUEST_CHANGES`.
4. **`security-auditor`** sobre el mismo diff (ámbito: lo que cambia el PR): OWASP/Spring, secrets, auth, CORS, dependencias; sin aplicar fixes.

Fase 3 — **Síntesis** (esto es lo que se emite):

**Salida esperada**:
- **Contexto**: rama/PR, alcance (3 líneas).
- **Hallazgos bloqueantes**: `archivo:línea | problema | fix concreto` (agrupando ambos agentes, sin duplicados).
- **Hallazgos mayores/menores**: tabla resumida con severidad.
- **Checklist de calidad**: tests/lint/secretos/commits/docs — OK o KO.
- **Veredicto final en una línea**: `Veredicto: APPROVE` o `Veredicto: REQUEST_CHANGES` + motivo en una frase.
- Recuerda: estricto con lógica, seguridad, contratos y tests; **no pidas cambios cosméticos subjetivos**. No edites nada.
