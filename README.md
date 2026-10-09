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

> Estas credenciales son de desarrollo: la contraseña **no está en ningún
> fichero del repositorio** (ni en el código, ni en `application.yml`, ni en la
> configuración de producción, ni siquiera en los comentarios de la semilla).
> En el repo sólo vive el hash BCrypt en
> `db/dev/V901__seed_usuario_admin_dev.sql`, que además sólo se aplica con el
> perfil `dev`. La contraseña en claro la facilita el equipo.


## Ramas (Git Flow)

- `master` — producción, siempre etiquetada (`vX.Y.Z`), solo merges de `release/*` o `hotfix/*`.
- `develop` — integración diaria; base de todas las features.
- `feature/*`, `fix/*`, `hotfix/*`, `release/*`, `refactor/*`, `perf/*`, `security/*`, `test/*`, `docs/*`, `chore/*`, `debug/*`.

Cada cambio: rama nueva desde `develop` → commit convencional → PR → revisión IA (`clean-code-reviewer` + `security-auditor`) → merge.

## Documentación de la API

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Spec (OpenAPI 3.1): `GET /v3/api-docs` → [`docs/openapi/openapi.yaml`](docs/openapi/openapi.yaml)
- **PDF de referencia**: [`docs/openapi/api-reference.pdf`](docs/openapi/api-reference.pdf) (~31 páginas)
- AsciiDoc intermedio: [`docs/openapi/gen/`](docs/openapi/gen/) (generado, versionado para regenerar el PDF sin red)

### Regenerar spec + PDF

Pipeline completo (skill `openapi-docs`, comando `/docs-pdf`):

```bash
# 1) Sólo si cambió la API: exportar la spec (backend arrancado)
curl -s http://localhost:8080/v3/api-docs -o docs/openapi/openapi.yaml

# 2) Lint — debe terminar con 0 errores (3 warnings preexistentes son aceptables)
npx @redocly/cli lint docs/openapi/openapi.yaml

# 3) AsciiDoc desde la spec (versión de openapi-generator fijada en openapitools.json)
npx @openapitools/openapi-generator-cli generate -i docs/openapi/openapi.yaml -g asciidoc -o docs/openapi/gen

# 4) PDF (perfil Maven `docs`; desde backend/)
.\mvnw.cmd -Pdocs -DskipTests prepare-package
```

El paso 4 convierte `docs/openapi/gen/index.adoc` en
`docs/openapi/api-reference.pdf` con `asciidoctor-maven-plugin` +
`asciidoctorj-pdf`, y renombra el `index.pdf` resultante. El perfil sólo se
activa con `-Pdocs`: el build normal (`.\mvnw.cmd test` / `package`) no lo toca.

