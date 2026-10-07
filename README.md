# MantiInmobiliaria

Webapp inmobiliaria: portal público de viviendas con mapa geolocalizado, modo swipe tipo Tinder, login con Google, y enlace con profesionales (fontaneros, albañiles, etc.) mediante solicitudes de trabajo notificadas por email/Telegram.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 25 LTS · Spring Boot 4.1 (MVC) · Maven · Modular Monolith (Spring Modulith + ArchUnit) |
| Base de datos | PostgreSQL 18 · Flyway · Spring Data JPA |
| Frontend | Next.js (App Router) · TypeScript · Tailwind CSS |
| Auth | OAuth2 Google + email/password (JWT: access en memoria + refresh cookie httpOnly) |
| Docs | springdoc-openapi → `openapi.yaml` → PDF |
| Tests | JUnit 6 · Testcontainers · Vitest · Playwright |
| CI/CD | GitHub Actions · Vercel (frontend + contenedor backend) · Neon (PostgreSQL prod) |

## Estructura

```
backend/    API Spring Boot (monolito modular por dominios)
frontend/   Next.js (portal público + backoffice)
docs/       openapi/, adr/ (decisiones de arquitectura), guias/
.opencode/  skills/, agents/, commands/ (agentes IA del proyecto)
```

## Desarrollo local

```bash
docker compose up -d          # PostgreSQL en :5433 (manti/manti_dev/manti)
cd backend && ./mvnw spring-boot:run
cd frontend && npm run dev
```

### Datos de desarrollo (perfil `dev`)

Para cargar el seed de inmuebles de ejemplo (35 anuncios) y el usuario admin,
arranca el backend con el perfil `dev`:

```bash
cd backend && ./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

El seed vive en `backend/src/main/resources/db/dev/` y sólo se aplica con ese
perfil (`spring.flyway.locations` en `application-dev.yml`); en producción nunca
se ejecuta.

| Cuenta (sólo dev) | Valor |
|---|---|
| Email | `admin@manti.dev` |
| Password | la facilita el equipo (en el repo sólo está su hash, en la semilla del perfil `dev`) |
| Rol | `ADMIN` (CRUD en `/api/v1/admin/inmuebles`) |

> Estas credenciales son de desarrollo: **no están en ningún fichero del
> repositorio en claro ni en `application.yml` ni en la configuración de
> producción** — sólo el hash vive en `db/dev/V901__seed_usuario_admin_dev.sql`,
> que además sólo se aplica con el perfil `dev`. Pídesela al equipo.


## Ramas (Git Flow)

- `master` — producción, siempre etiquetada (`vX.Y.Z`), solo merges de `release/*` o `hotfix/*`.
- `develop` — integración diaria; base de todas las features.
- `feature/*`, `fix/*`, `hotfix/*`, `release/*`, `refactor/*`, `perf/*`, `security/*`, `test/*`, `docs/*`, `chore/*`, `debug/*`.

Cada cambio: rama nueva desde `develop` → commit convencional → PR → revisión IA (`clean-code-reviewer` + `security-auditor`) → merge.

## Documentación API

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Spec: `GET /v3/api-docs` → `docs/openapi/openapi.yaml`
- PDF: `/docs-pdf` (ver skill `openapi-docs`)
