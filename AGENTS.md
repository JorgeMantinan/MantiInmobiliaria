# AGENTS.md — MantiInmobiliaria

Webapp inmobiliaria: portal de viviendas con mapa geolocalizado, modo swipe, login con Google y solicitudes a profesionales notificadas por email/Telegram. Monorepo con backend Java + frontend Next.js.

## Stack y versiones

- **Backend**: Java 25 LTS · Spring Boot 4.1 (MVC) · Maven wrapper `backend/mvnw.cmd` · PostgreSQL 18 · Flyway · Spring Data JPA · MapStruct + Lombok · springdoc-openapi 3.1.1 · Spring Security 7 (OAuth2 Google + JWT refresh cookie).
- **Frontend**: Next.js (App Router) + TypeScript strict + Tailwind, en `frontend/`.
- **Arquitectura**: Monolito modular (`es.manti.<modulo>.{api,application,domain,infra}`), NO microservicios.
- **DB local**: `docker compose up -d` → PostgreSQL 18 en `localhost:5433` (user/pass/db: `manti`/`manti_dev`/`manti`). **Ojo**: el equipo tiene un PostgreSQL 12 nativo en el 5432 — el proyecto usa 5433.

## Comandos

```bash
# Backend (desde /backend)
./mvnw.cmd spring-boot:run        # arrancar API (:8080)
./mvnw.cmd test                   # tests
./mvnw.cmd -q -DskipTests package # compilar rápido

# Frontend (desde /frontend)
npm run dev | npm run lint | npm run build

# Infra
docker compose up -d | docker compose down
```

**Windows (importante)**: cada sesión de shell nueva arranca con PATH antiguo. Antes de usar `java`, `gh` o `mvnw`:
```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path','User')
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\<jdk-25.x>'   # ver carpeta real
```

## Estructura

```
backend/     Spring Boot (módulos: usuarios, inmuebles, swipes, profesionales, notificaciones, shared)
frontend/    Next.js (src/app rutas, src/features, src/components, src/lib)
docs/        openapi/ (spec + PDF), adr/ (decisiones), guias/
.opencode/   skills/, agents/, commands/   ← equipo IA del proyecto
.github/     workflows/ci.yml, plantilla PR
```

## Git Flow (obligatorio)

- `master` = producción (etiquetada `vX.Y.Z`; solo merges de `release/*` o `hotfix/*`). `develop` = integración.
- Ramas: `feature/NN-slug`, `fix/NN-slug`, `hotfix/`, `release/X.Y.Z`, `refactor/`, `perf/`, `security/`, `test/`, `docs/`, `chore/`, `debug/` (spike desechable).
- Todo cambio: rama nueva desde `develop` → commits **Conventional Commits en inglés** (`feat(inmuebles): ...`) → PR con plantilla → revisión IA (`clean-code-reviewer` + `security-auditor`) → CI verde → merge.
- Prohibido: force-push en `master`/`develop`, commitear secretos, tocar `master` fuera de release/hotfix.

## Convenciones (leer la skill completa antes de actuar)

Skills en `.opencode/skills/<nombre>/SKILL.md`: `dto`, `docker`, `microservicios`, `base-de-datos`, `consultas-eficientes`, `seguridad`, `clean-code-servicios`, `rendimiento`, `clean-code-componentes`, `estructura-spring-boot`, `estructura-nextjs`, `api-rest`, `testing`, `git-flow`, `openapi-docs`, `config-secrets`, `diseno-madera`.

- **Antes de escribir código de un tema, leer su skill.**
- Identificadores de dominio en español (`inmuebles`, `precio`), sufijos técnicos en inglés (`XController`, `XService`, `XRequest`/`XResponse`).
- Módulos aislados: sin imports entre módulos (ArchUnit lo verifica). `domain` no depende de `api` ni `infra`.
- API REST en `/api/v1`, errores RFC 9457 (`application/problem+json`), todos los endpoints anotados con springdoc.
- Secretos solo en variables de entorno (`.env` está en `.gitignore`); nunca en código, YAML ni commits.
- UI: paleta y bordes según skill `diseno-madera` (madera clara `#FAF7F2`, bordes 1px `#E4D9C9`).

## Agentes (`.opencode/agents/`)

`research-agent`, `feature-developer`, `frontend-developer`, `security-auditor`, `perf-auditor`, `db-auditor`, `test-engineer`, `docs-api-agent`, `clean-code-reviewer`, `git-flow-agent`, `deploy-agent`.

- **Solo informan (edit deny)**: `security-auditor`, `perf-auditor`, `db-auditor`, `clean-code-reviewer`, `research-agent`.
- Delegación con la herramienta `task`: implementación → `feature-developer`/`frontend-developer`; auditorías → `security-auditor`/`perf-auditor`/`db-auditor`; revisión de PR → `clean-code-reviewer` + `security-auditor`.

## Comandos (`/`)

`/feature` `/fix` `/release` `/security-audit` `/db-review` `/perf-review` `/docs-pdf` `/test-all` `/review` `/deploy`

## Gates de calidad (definición de "hecho")

1. `./mvnw.cmd -q test` verde y `npm run lint && npm run build` sin errores.
2. Código nuevo con tests (objetivo coverage backend ≥80 %).
3. Sin secretos (gitleaks) ni vulnerabilidades críticas (OWASP dep-check / npm audit).
4. Cambio de esquema ⇒ migración Flyway nueva (`V<n>__desc.sql`); nunca editar una `V*` ya aplicada.
5. Cambio de API ⇒ `openapi.yaml` y docs actualizados en el mismo PR.
6. Commit convencional en rama con naming correcto.

## Prohibiciones

- No commitear: `.env`, `backend/target/`, `node_modules/`, `.next/`, credenciales.
- No usar WebFlux, JWT en localStorage, CORS `*`, `ddl-auto: update/create` en ningún perfil.
- No romper compatibilidad de la API sin subir a `/api/v2`.
## E2E (Playwright)

Smoke del portal con navegador real (chromium) contra backend + seed dev. NO hay `webServer` en `frontend/playwright.config.ts`: los servidores se levantan fuera.

```bash
# 1. Backend dev con seed (el CORS debe incluir el origen del frontend en :3010;
#    el .env de la raiz solo trae :3000, hay que ampliarlo)
set ALLOWED_ORIGINS=http://localhost:3000,http://localhost:3010
cd backend && ./mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

# 2. Frontend de produccion
cd frontend && npm ci && NEXT_PUBLIC_API_URL=http://localhost:8080 npm run build
npx next start -p 3010

# 3. Smoke (en otra terminal)
cd frontend && E2E_BASE_URL=http://localhost:3010 npm run test:e2e
```

En CI el job `e2e` de `.github/workflows/ci.yml` hace todo esto solo (postgres service + jar dev + `next start` + chromium). Tests en `frontend/e2e/`; van contra datos del seed dev (35 inmuebles), sin datos frágiles.