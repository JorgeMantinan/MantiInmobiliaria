---
description: Crea una feature completa (backend y/o UI) desde un ticket, con rama feature/, tests y commits; sin merge.
agent: build
---

Implementa la siguiente feature en el monorepo **MantiInmobiliaria**.

Entrada del usuario: $ARGUMENTS

Pasos obligatorios:

1. **Parsea la entrada**:
   - Si trae ticket/rama (p. ej. `12 - gestión de inmuebles`, `feature/12-gestion-inmuebles`), extrae `NN` y el slug.
   - Si no trae número, deriva un slug corto en inglés (kebab-case) y usa `feature/<slug>`.
   - Si no hay entrada clara, pide el ticket antes de tocar nada.
2. **Define el alcance**: ¿toca backend, frontend o ambos? Revisa el repo (endpoints, pantallas implicadas) y el presupuesto de cambios.
3. **Delega en subagentes**:
   - Backend → `feature-developer`
   - UI/pantallas/componentes → `frontend-developer`
   - Lánzalos en paralelo si el alcance es mixto.
4. **Recuerda el flujo** (déjaselo explícito a quien lo ejecute):
   - Rama **desde `develop`**: `git checkout develop` → `git checkout -b feature/NN-slug`.
   - Seguir las skills de `.opencode/skills/` (`estructura-spring-boot`, `dto`, `api-rest`, `base-de-datos`, `testing`, `clean-code-componentes`, `estructura-nextjs`, `diseno-madera`).
   - Migración Flyway **nueva** si cambia el esquema; nunca editar migraciones aplicadas.
   - Compilar: `cd backend && ./mvnw -q -DskipTests package` → tests: `./mvnw -q test`.
   - Frontend: `npm run lint` y `npm run build` en `frontend/`.
   - Commits convencionales atómicos **en inglés** (`feat(alcance): ...`).
5. **NO hagas merge ni PR** salvo que el usuario lo pida explícitamente.

**Salida esperada**: rama creada, archivos modificados agrupados por capa, cómo probar (comandos + curl/rutas), estado de tests y lint, commits realizados y puntos pendientes.
