---
description: "Prepara release/X.Y.Z desde develop: verifica CI, actualiza CHANGELOG, merge a master, tag y checklist de deploy."
agent: plan
---

Prepara la release **release/X.Y.Z** de **MantiInmobiliaria**.

Entrada del usuario: $ARGUMENTS (versión deseada; si no se indica, propón la siguiente versión semántica a partir de los commits de `develop`).

Fase 1 — **Plan** (no toques nada todavía):
1. Estado del repo: `git status`, `git branch --show-current`, `git log --oneline develop -15`. Si hay cambios sucios, repórtalos y detente.
2. Verifica que `develop` está **verde en CI** (workflow `.github/workflows/ci.yml`: secret-scan + backend + frontend). Si `gh` está autenticado, comprueba los checks del último push; si no, indícalo.
3. Revisa el alcance: `git log --oneline master..develop`, detecta breaking changes (commits `feat!:`/`BREAKING CHANGE`) y decide el incremento (mayor/media/patch).
4. Presenta el **plan**: versión, conmutadores de changelog, pasos exactos de merge/tag/deploy y riesgos. Espera confirmación del usuario.

Fase 2 — **Build** (solo tras confirmación):
5. Desde `develop`: `git checkout develop` → `git checkout -b release/X.Y.Z`.
6. Cambios de release: actualizar versión del proyecto (`backend/pom.xml`, `frontend/package.json` si aplica) y **CHANGELOG** (Keep a Changelog, entradas derivadas de los commits Conventional Commits, en inglés como los commits).
7. Verificación final: `cd backend && ./mvnw -q test` y `cd frontend && npm run lint && npm run build`. Nada de features nuevas en la release (solo fixes de release).
8. Merge a producción y tag:
   ```
   git checkout master
   git merge --no-ff release/X.Y.Z -m "release: X.Y.Z"
   git tag -a vX.Y.Z -m "vX.Y.Z"
   ```
   (si el flujo del proyecto hace merge vía PR, créalo con `gh pr create --base master` y repórtalo).
9. Vuelve a integración: `git checkout develop` → `git merge --no-ff release/X.Y.Z`.
10. **NO hagas push ni publicación de tags** salvo petición explícita del usuario.

**Checklist de deploy** (inclúyelo siempre en la salida):
- [ ] CI verde en `develop` y en el merge a `master`
- [ ] CHANGELOG con la versión y fecha
- [ ] Tag `vX.Y.Z` creado
- [ ] Frontend → Vercel (build `frontend/`, entorno preview/prod)
- [ ] Backend → contenedor (build imagen, migraciones Flyway al arrancar)
- [ ] Base de datos → Neon (backup previo confirmado)
- [ ] Health checks: `/actuator/health` backend y `/` frontend
- [ ] Secretos vigentes en GitHub/Vercel (sin valores en claro)

**Salida esperada**: versión propuesta, plan/fases ejecutadas, rama y tag resultantes, commits, estado de CI y checklist de deploy con su estado.
