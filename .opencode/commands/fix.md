---
description: Corrige un bug con rama fix/, test rojo primero, fix y suite verde; commits convencionales.
agent: build
---

Corrige el siguiente bug en **MantiInmobiliaria**.

Entrada del usuario: $ARGUMENTS

Flujo obligatorio (TDD a la inversa: primero el test que falla):

1. **Parsea la entrada**: número de ticket (si existe) y descripción del bug. Si falta lo esencial, pregunta antes de tocar nada.
2. **Reproduce**: localiza el código implicado (`backend/` o `frontend/`), arranca lo necesario (`docker compose up -d`, backend/frontend en local) y confirma el comportamiento erróneo. Si no se puede reproducir, dilo y detente.
3. **Crea la rama desde `develop`**:
   ```
   git checkout develop
   git checkout -b fix/NN-slug
   ```
   (sin `NN`, `fix/<slug>`; slug kebab-case).
4. **Escribe primero un test que falle** por el bug:
   - Backend: JUnit/Testcontainers/MockMvc en `backend/src/test/` → `./mvnw -q test` debe fallar.
   - Frontend: Vitest/Playwright en `frontend/` → debe fallar.
5. **Fix mínimo**: solo lo necesario para corregir la causa raíz; sigue las skills relevantes (`clean-code-servicios`, `clean-code-componentes`, `testing`).
6. **Suite verde**: `cd backend && ./mvnw -q test`; en frontend `npm run lint && npm run build` (+ `npm test` si existe el script).
7. **Commits convencionales atómicos en inglés**: `fix(inmueble): prevent null price on create`.
8. **NO hagas merge ni PR** salvo petición explícita.

**Salida esperada**: rama, bug y causa raíz (1-3 líneas), test añadido (archivo), fix aplicado (archivo:línea), resultado de la suite, commits y cómo reproducir la verificación.
