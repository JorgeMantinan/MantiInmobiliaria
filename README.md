# MantiInmobiliaria

[![CI](https://github.com/JorgeMantinan/MantiInmobiliaria/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/JorgeMantinan/MantiInmobiliaria/actions/workflows/ci.yml)

Webapp inmobiliaria: portal público de viviendas con mapa geolocalizado, modo swipe tipo Tinder, login con Google y enlace con profesionales (fontaneros, albañiles, etc.) mediante solicitudes de trabajo notificadas por email/Telegram. Monorepo con backend Spring Boot (Java 25) y frontend Next.js.

## Características

- Portal público de inmuebles con listado, detalle, mapa (Leaflet/React-Leaflet) y modo swipe con favoritos
- Login con OAuth2 de Google y registro/email-password; JWT con access en memoria y refresh cookie httpOnly
- Backoffice de administración de inmuebles (perfil `ADMIN`)
- Solicitudes de trabajo a profesionales con notificaciones transaccionales (outbox) por email (Brevo) y Telegram
- API REST versionada en `/api/v1` con errores RFC 9457 (`application/problem+json`) y documentación OpenAPI
- Rate limiting por endpoint, endurecimiento de login (backoff, HIBP) y cookies `__Host-` en producción
- Job diario de purga de refresh tokens

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 25 LTS · Spring Boot 4.1 (MVC) · Maven wrapper · Spring Security 7 (OAuth2 Google + JWT) · MapStruct · Lombok |
| Base de datos | PostgreSQL 18 · Flyway · Spring Data JPA |
| Frontend | Next.js (App Router) · TypeScript · Tailwind CSS · React Leaflet · framer-motion |
| Docs | springdoc-openapi 3.1.1 → `openapi.yaml` → PDF |
| Tests | JUnit · Vitest · Playwright |
| CI/CD | GitHub Actions · Vercel (frontend + backend en contenedor) · Neon (PostgreSQL en producción) |
| Arquitectura | Monolito modular (`es.manti.<modulo>.{api,application,domain,infra}`), módulos aislados verificados con ArchUnit |

## Estructura

```
MantiInmobiliaria/
├── backend/               API Spring Boot
│   └── src/main/java/es/manti/   módulos: usuarios, inmuebles, swipes, profesionales, mercado, config, shared
├── frontend/              Next.js (portal público + backoffice)
│   └── src/app/           rutas: inmuebles, swipe, favoritos, login, registro, profesionales, admin, alertas, auth, recuperar...
├── docs/                  openapi/ (spec + PDF), adr/ (decisiones), guias/
├── docker/                Composes y utilidades Docker
├── docker-compose.yml     PostgreSQL 18 local en :5433
└── .opencode/             skills/, agents/, commands/ (equipo IA del proyecto)
```

## Requisitos

- Java 25 (JDK) y Maven wrapper (`backend/mvnw.cmd`; no requiere Maven instalado)
- Node.js y npm (frontend)
- Docker (PostgreSQL 18 local en `localhost:5433`; ojo: el puerto 5432 puede estar ocupado por otra instancia)
- Cuenta Google Cloud con credenciales OAuth2
- Cuentas opcionales: Brevo (email), Bot de Telegram

En Windows, antes de usar `java` o `mvnw` en una shell nueva puede hacer falta refrescar el PATH y `JAVA_HOME`:

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path','User')
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\<jdk-25.x>'   # ver carpeta real
```

## Puesta en marcha

```bash
docker compose up -d                          # PostgreSQL 18 en :5433 (manti/manti_dev/manti)
cd backend && ./mvnw.cmd spring-boot:run      # API en :8080
cd frontend && npm ci && npm run dev          # Next.js en :3000
```

Para cargar el seed de desarrollo (35 inmuebles de ejemplo y usuario admin), arrancar el backend con el perfil `dev`:

```bash
cd backend && ./mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

El seed vive en `backend/src/main/resources/db/dev/` y solo se aplica con ese perfil; en producción no se ejecuta. La contraseña del usuario admin de desarrollo no está en el repo (solo su hash BCrypt en la semilla); la facilita el equipo.

## Tests

```bash
# Backend
cd backend && ./mvnw.cmd test

# Frontend (unit)
cd frontend && npm run test

# E2E (Playwright, smoke del portal; los servidores se levantan fuera)
cd frontend && E2E_BASE_URL=http://localhost:3010 npm run test:e2e
```

Para los E2E en local: backend con perfil `dev` y CORS que incluya el origen del frontend en :3010, y frontend de producción en ese puerto (ver receta en `AGENTS.md`). En CI el job `e2e` de `.github/workflows/ci.yml` lo hace todo (postgres service, jar dev, `next start`, chromium). Objetivo de coverage backend: ≥ 80 %.

## Variables de entorno

Backend (`.env` en la raíz del monorepo; ver `.env.example`):

| Grupo | Variables |
|---|---|
| Obligatorias | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `JWT_SECRET`, `FRONTEND_URL`, `ALLOWED_ORIGINS` |
| Base de datos | `DB_URL`, `DB_USER`, `DB_PASSWORD` |
| Cookies / HSTS | `COOKIE_SAMESITE`, `COOKIE_SECURE`, `HSTS_ENABLED`, `HSTS_MAX_AGE_SEGUNDOS` |
| Rate limiting login | `AUTH_RATE_LOGIN_PER_MINUTE`, `AUTH_RATE_REGISTER_PER_MINUTE`, `AUTH_LOGIN_FALSOS_BACKOFF`, `AUTH_LOGIN_PARES_POR_MINUTO` |
| Notificaciones | `NOTIFICACIONES_ENABLED`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, `BREVO_API_KEY`, `NOTIF_EMAIL_FROM` |
| Operaciones | `SPRINGDOC_ENABLED`, `PURGAS_ENABLED`, `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE`, `HIKARI_MAXIMUM_POOL_SIZE`, `FORWARD_HEADERS_STRATEGY` |

Frontend (`frontend/.env.example`):

| Variable | Descripción |
|---|---|
| `NEXT_PUBLIC_API_URL` | URL base de la API (local: `http://localhost:8080`) |
| `NEXT_PUBLIC_CARTO_KEY` (opcional) | Clave pública de tiles CARTO para mapas |

`.env` está en `.gitignore`: nunca se commitea. Los valores reales no van en el repo.

## Despliegue

- Frontend y backend en Vercel (proyectos `mantiinmobiliaria` y `mantiinmobiliaria-api`); el backend va empaquetado en contenedor (`backend/Dockerfile.vercel`)
- Base de datos de producción en Neon (PostgreSQL)
- Las ramas `master`/`develop` y el flujo de release están descritos más abajo; `master` es producción etiquetada (`vX.Y.Z`)

### Ramas (Git Flow)

- `master` — producción, siempre etiquetada (`vX.Y.Z`), solo merges de `release/*` o `hotfix/*`
- `develop` — integración diaria; base de todas las features
- Cada cambio: rama desde `develop` → commit convencional → PR → revisión IA → CI verde → merge

## Documentación de la API

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Spec (OpenAPI 3.1): `GET /v3/api-docs` → [`docs/openapi/openapi.yaml`](docs/openapi/openapi.yaml)
- PDF de referencia: [`docs/openapi/api-reference.pdf`](docs/openapi/api-reference.pdf)

## Estado

- En desarrollo activo bajo Git Flow (`develop`); CI con gitleaks, tests de backend y frontend y smoke E2E
- Fases de notificaciones (outbox, email/Telegram), rate limiting por endpoint y endurecimiento de login ya implementadas
- Gates de calidad: `./mvnw.cmd -q test` y `npm run lint && npm run build` en verde; cambios de esquema con migración Flyway nueva; cambios de API con `openapi.yaml` actualizado en el mismo PR
