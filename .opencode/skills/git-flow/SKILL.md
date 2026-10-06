---
name: git-flow
description: Use when trabajando con git y ramas en el monorepo: git flow, ramas develop/master/feature/fix/hotfix, commits convencionales, PRs, revisión obligatoria, merges y prohibiciones.
---

# Flujo Git del proyecto

## Reglas

1. **Ramas exactas.**

| Rama | Uso |
|---|---|
| `master` | producción, PROTEGIDA (sólo releases/hotfix) |
| `develop` | integración, rama por defecto de los PRs |
| `feature/NN-slug` | nueva funcionalidad (NN = issue) |
| `fix/NN-slug` | corrección de bug |
| `hotfix/NN-slug` | arreglo urgente en producción |
| `release/X.Y.Z` | preparación de release |
| `refactor/`, `perf/`, `security/`, `test/`, `docs/`, `chore/` | cambios tipados |
| `debug/` | spike/descubrimiento desechable (no se mergea sin convertirse en otra rama) |

2. **Flujo.** Crear siempre desde `develop`:

```bash
git checkout develop && git pull
git checkout -b feature/42-buscador-avanzado
```

3. **Commits convencionales en inglés**: `type(scope): mensaje` (imperativo, minúscula, sin punto final).

```
types: feat, fix, perf, refactor, test, docs, build, ci, chore
scopes: inmuebles, auth, swipes, profesionales, notif, api, db, ui, ci, deps

feat(inmuebles): add price range filter to search endpoint
fix(auth): return 401 when refresh token expired
test(swipes): cover swipe limit per user
```

4. **Tamaño de commit:** lógico y atómico — cada commit compila y deja los tests en verde. No mezclar refactor con feature; no commitear "wip".

5. **PR hacia `develop` con plantilla:** qué cambia, por qué, cómo se prueba (comandos), riesgo, screenshot si hay UI. Revisión obligatoria por `clean-code-reviewer` y `security-auditor` antes del merge; CI verde obligatorio.

6. **Nunca commitear:** `.env`, `target/`, `node_modules/`, `.next/`, claves/secretos (gitleaks lo verifica en CI), `.idea/`. Revisar con `git status --porcelain` y `git diff --staged` antes de cada commit.

7. **Merge preferido: rebase de la feature sobre `develop` + merge commit (`--no-ff`) en `develop`** para trazabilidad de la historia. No squash-merge (perdemos el detalle de los commits atómicos).

```bash
git fetch origin
git rebase origin/develop          # en la feature, resolver conflictos aquí
git checkout develop && git merge --no-ff feature/42-buscador-avanzado
```

8. **Etiquetas:** `vX.Y.Z` en `master` en cada release (`git tag -a v1.4.0 -m "..."`); el `release/X.Y.Z` se crea desde develop, se prueba, se mergea en master (release) y en develop.

9. **Comandos por operación.**

```bash
git checkout -b fix/17-login-redirect            # crear rama
git commit -m "fix(auth): redirect to admin after login"
gh pr create --base develop --title "fix(auth): redirect to admin after login" --body-file PR.md
gh pr checks                                      # CI verde
gh pr merge <n> --merge                            # merge commit
git push origin v1.4.0                            # etiqueta
```

10. **Conflictos.** Rebasear la feature sobre `develop`, resolver a favor de la feature SIN perder los tests nuevos de develop; nunca resolver borrando código de develop. Tras resolver: `git rebase --continue` y ejecutar la suite completa.

11. **Prohibiciones.** Force-push en `develop`/`master`; amend de commits ya compartidos; commitear `.idea/` o configs personales; merge con CI rojo; saltarse la revisión de los dos agentes.

### Checklist de PR

- [ ] Rama correcta creada desde `develop` con el prefijo y slug adecuados
- [ ] Commits convencionales en inglés, atómicos, compilan y testean
- [ ] `git status` limpio: sin `.env`, `target/`, `.next/`, secretos ni `.idea/`
- [ ] Plantilla de PR completa (qué, por qué, cómo se prueba, riesgo, screenshot si UI)
- [ ] CI verde (`gh pr checks`)
- [ ] Aprobaciones de `clean-code-reviewer` y `security-auditor`
- [ ] Rebase sobre `develop` sin conflictos pendientes
- [ ] Merge con `--no-ff` hacia develop (nunca force-push en develop/master)
