# Guía de despliegue a producción (FASE 10)

Arquitectura de despliegue de MantiInmobiliaria en Vercel (plan Hobby) + Neon
(PostgreSQL managed). Documentación de la fase `chore/10-vercel-deploy`.

## 1. Arquitectura resultante

**Dos proyectos Vercel en la org `manti`, desde el mismo repo GitHub
`JorgeMantinan/MantiInmobiliaria`:**

| Proyecto | Root Directory | Contenido | URL |
| --- | --- | --- | --- |
| `mantiinmobiliaria` | `frontend/` | Next.js (App Router), integración Git existente | https://mantiinmobiliaria.vercel.app |
| `mantiinmobiliaria-api` | `backend/` | Contenedor OCI del backend Spring Boot (`Dockerfile.vercel` auto-detectado) | https://mantiinmobiliaria-api.vercel.app |

- **Front y API en dominios distinto** ⇒ arquitectura *cross-site*:
  - Cookie de refresh: `COOKIE_SAMESITE=None` + `COOKIE_SECURE=true` (obligatorio
    `None` con `Secure`).
  - CORS en el backend: `ALLOWED_ORIGINS=https://mantiinmobiliaria.vercel.app`
    (allowlist explícita, nunca `*`).
  - El frontend llama a la API con `NEXT_PUBLIC_API_URL=https://mantiinmobiliaria-api.vercel.app`.
- **Base de datos**: Neon (PostgreSQL managed). La cadena JDBC completa va en
  `DB_URL` (pooler de Neon); `DB_USER`/`DB_PASSWORD` son las credenciales de
  `neondb_owner`. `DB_NAME` y `NEON_POOLER_URI` NO se configuran en Vercel: la
  app sólo lee `DB_URL`/`DB_USER`/`DB_PASSWORD`.
- Cada push a una rama con preview despliega frontend y contenedor en preview;
  el push a `master` (sólo vía `release/*` o `hotfix/*`, Git Flow) despliega
  producción en ambos proyectos.

### Por qué no un solo proyecto con Services (dominio único)

La doc actual de Vercel ([Services](https://vercel.com/docs/services) +
[Container Images](https://vercel.com/docs/functions/container-images)) permite
meter el contenedor y Next.js en el MISMO proyecto/dominio con `vercel.json`
raíz (`services` + `rewrites` con `destination: {service: ...}`), rutas
`/api/v1/**` al contenedor y el resto a Next, manteniendo cookies Lax. Es la
arquitectura a la que se debe migrar cuando se añada `vercel.json` en la raíz
del repo (lo bloqueó la configuración de permisos del agente de la FASE 10:
sólo podía escribir ficheros `Dockerfile*` y `.github/**`). La variante con
proyecto separado era la alternativa explícitamente prevista en el plan de fase
y funciona igual de bien; su coste es el modo cross-site ya configurado.

Fuentes consultadas (julio–agosto 2026):

- https://vercel.com/blog/dockerfile-on-vercel (30-06-2026)
- https://vercel.com/docs/functions/container-images (últ. act. 07-07-2026)
- https://vercel.com/docs/services y /docs/services/routing, /docs/services/config-reference
- https://vercel.com/changelog/bring-your-dockerfile-to-vercel-functions
- https://github.com/vercel/examples/tree/main/container-images/demo
- https://community.vercel.com/t/dockerfile-vercel-is-not-detected-in-standalone-pro-project/47791.md
  y https://community.vercel.com/t/possibly-missing-steps-on-run-any-dockerfile-on-vercel-blog-post/44957
  (preset Framework=Container obligatorio cuando la auto-detección no corre)

## 2. Cómo se construye y despliega

- El proyecto `mantiinmobiliaria-api` tiene Root Directory `backend/`, **Framework
  Preset = `Container`** y la integración Git de Vercel apunta al mismo repo.
  Con ese preset, `vercel build` usa `backend/Dockerfile.vercel`, construye la
  imagen OCI en cada push y la sirve como Vercel Function en Fluid compute
  (Container Runtime, beta disponible en todos los planes).
  > **OJO**: en proyectos creados vacíos (p. ej. `vercel project add`) la
  > auto-detección NO asigna el preset Container y los builds de Git terminan
  > en 169 ms sin construir nada (404 en el dominio). Hay que fijar el preset a
  > mano: `vercel project update mantiinmobiliaria-api --framework container`.
  > Los overrides de Build/Install/Output Command deben quedar en "None".
- El despliegue "Production" inicial del proyecto (creado por la conexión Git)
  está vacío (404): no sirve nada hasta el primer release real desde `master`;
  la rama de producción de AMBOS proyectos está confirmada en `master`.
- **Región de funciones = `fra1` (Fráncfort)**, la misma región de Neon
  (`eu-central-1`): menos latencia por roundtrip durante el arranque y respuestas
  más rápidas para los usuarios. Configurada vía
  `vercel api /v9/projects/<id> -X PATCH -f serverlessFunctionRegion=fra1`.
- **Arranque y el relay socat**: el Container Runtime exige que algo acepte
  TCP en `$PORT` en ~28 s (startup timeout); el arranque medido de este backend
  en Vercel es de **~35 s** (`Started MantiBackendApplication in 34.5 s`), así
  que sin más, cada invocación en frío devolvía
  `FUNCTION_INVOCATION_FAILED`. La imagen fronta Java con un relay socat:
  escucha en `$PORT` (8080) al instante — el probe de la plataforma pasa — y
  cada petición se RETIENE en `relay.sh` (bucle `nc` + `socat -`) hasta que
  Tomcat escuche en el interno **8081**. Al ser TCP crudo, los `X-Forwarded-*`
  que inyecta el proxy de Vercel viajan intactos. La PRIMERA petición en frío
  tarda ~35-45 s en responder; las siguientes, instantáneas. Con la
  inactividad (`scale-to-zero`: 5 min en prod, 30 s en preview) el ciclo se
  repite. Medido en preview: `GET /actuator/health` ⇒ `UP` (primera petición
  ~43 s) y `GET /api/v1/gremios` ⇒ 200 con el catálogo.
- `HEALTHCHECK` de la imagen contra la APP en `8081` (no contra el relay: si
  Java no arranca, el health del contenedor queda en rojo; sólo afecta a
  Docker local, Vercel no lo usa).
- El CI de GitHub Actions (`.github/workflows/ci.yml`) NO cambia: gitleaks +
  tests backend (JDK 25 + PG 18) + lint/test/build frontend. Los despliegues
  los hace la integración Git de Vercel, no Actions.

### Sincronización de ficheros Docker (deuda conocida)

`backend/Dockerfile` (canónico, para builds locales/CI) y
`backend/Dockerfile.vercel` (para Vercel) son copias idénticas; sus
`.dockerignore` gemelos son `Dockerfile.dockerignore` y
`Dockerfile.vercel.dockerignore`. Al cambiar uno, cambiar los cuatro. Cuando se
migre a Services con `vercel.json` raíz, `Dockerfile.vercel` y sus gemelos se
eliminan y sólo queda `backend/Dockerfile`.

## 3. Variables de entorno (sólo nombres; valores NUNCA en el repo)

### Proyecto `mantiinmobiliaria-api` (producción y preview)

| Nombre | Origen del valor | Notas |
| --- | --- | --- |
| `PORT` | literal `8080` | Puerto del contenedor; el proxy de Vercel apunta aquí. |
| `SPRING_PROFILES_ACTIVE` | literal `prod` | Activa `application-prod.yml` (Flyway sólo `db/migration`, springdoc OFF, actuator recortado). |
| `SERVER_FORWARD_HEADERS_STRATEGY` | literal `framework` | Para que `{baseUrl}` del OAuth2 vea `https://` tras el proxy (redirect-uri correcto). |
| `DB_URL` | `.env.prod` | JDBC del pooler de Neon. |
| `DB_USER` | `.env.prod` | `neondb_owner`. |
| `DB_PASSWORD` | `.env.prod` | Secreto. |
| `JWT_SECRET` | `.env` | Secreto HS256. |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | `.env` | Secreto. |
| `FRONTEND_URL` | literal `https://mantiinmobiliaria.vercel.app` | Redirección tras login con Google. |
| `ALLOWED_ORIGINS` | literal `https://mantiinmobiliaria.vercel.app` | Allowlist CORS (cross-site con el front). |
| `COOKIE_SAMESITE` | literal `None` | Obligatorio cross-site (con `Secure`). |
| `COOKIE_SECURE` | literal `true` | HTTPS en ambos dominios. |
| `BREVO_API_KEY` | `.env` | Secreto. |
| `TELEGRAM_BOT_TOKEN` | `.env` | Secreto. |
| `TELEGRAM_CHAT_ID` | `.env` | Chat del grupo. |
| `NOTIF_EMAIL_FROM` | `.env` | Remitente. |
| `NOTIFICACIONES_ENABLED` | literal `true` | Activa el despachador de outbox. |
| `SPRING_JPA_PROPERTIES_HIBERNATE_BOOT_ALLOW_JDBC_METADATA_ACCESS` | literal `false` | Hibernate no sondea el `DatabaseMetaData` al arrancar: ahorra varios roundtrips a Neon en el arranque. |

Omitidas a propósito: `SPRINGDOC_ENABLED` (el perfil `prod` ya la apaga),
`DB_NAME` y `NEON_POOLER_URI` (la app no las lee).

### Proyecto `mantiinmobiliaria` (frontend)

| Nombre | Entorno | Notas |
| --- | --- | --- |
| `NEXT_PUBLIC_API_URL` | production y preview | `https://mantiinmobiliaria-api.vercel.app` (build-time: cambiarla exige redeploy). |

Verificación: `vercel env ls` (sólo nombres), jamás imprimir valores.

## 4. Comandos

```bash
# Link local del proyecto API (raíz del repo; .vercel/ está gitignored)
vercel link --yes --project mantiinmobiliaria-api -S manti

# Añadir una variable SIN imprimir el valor (lee de stdin)
Get-Content .env | ... | vercel env add JWT_SECRET production   # PowerShell

# Build y smoke local del contenedor
docker build -t manti-backend -f backend/Dockerfile backend/
docker run --rm -p 8080:8080 --env-file <envs de prod> manti-backend

# Salud del backend (la PRIMERA petición en frío tarda ~35-45 s: el relay
# retiene la petición mientras arranca la JVM; las siguientes son instantáneas)
curl https://mantiinmobiliaria-api.vercel.app/actuator/health   # {"status":"UP"}

# Los PREVIEWS del API tienen Deployment Protection (SSO de Vercel):
# para probarlos desde CLI hay que usar el bypass automático del CLI
vercel curl https://mantiinmobiliaria-api-git-<rama>-manti.vercel.app/actuator/health
```

## 5. Checklist de producción (Git Flow)

1. `develop` verde (CI + revisión IA) → rama `release/X.Y.Z`.
2. Smoke en preview: frontend responde y
   `https://mantiinmobiliaria-api.vercel.app/actuator/health` ⇒ `UP`
   (permitirse la primera petición en frío: ~35-45 s; en previews probar con
   `vercel curl`, van protegidos por SSO).
3. PR `release/X.Y.Z` → `master`, CI verde, merge.
4. Vercel despliega ambos proyectos desde `master` (production).
5. Smoke de producción (ver §7) y etiquetado `vX.Y.Z`.

## 6. Pasos manuales del usuario

1. **Google Cloud Console** (credenciales OAuth del cliente de
   `mantiinmobiliaria`): añadir en *Authorized redirect URIs* la URI de
   PRODUCCIÓN de la API:
   `https://mantiinmobiliaria-api.vercel.app/login/oauth2/code/google`
   (la calcula `{baseUrl}` con `SERVER_FORWARD_HEADERS_STRATEGY=framework`).
   Las URIs de preview (`https://mantiinmobiliaria-api-git-*-manti.vercel.app/...`)
   sólo hace falta añadirlas si se quiere probar login con Google desde un
   preview concreto.
2. **Vercel dashboard** → proyecto `mantiinmobiliaria` → Settings → Git:
   confirmar rama de producción = `master`; repetir en `mantiinmobiliaria-api`.
3. Tras el primer release (§5): smoke test completo (§7).

## 7. Smoke test de producción

1. `GET https://mantiinmobiliaria.vercel.app/` ⇒ 200 (HTML del portal).
2. `GET https://mantiinmobiliaria-api.vercel.app/actuator/health` ⇒ `{"status":"UP"}`
   (si lleva >5 min sin tráfico, la primera petición en frío tarda ~35-45 s).
3. Listado público: `GET https://mantiinmobiliaria-api.vercel.app/api/v1/inmuebles` ⇒ 200/404-vacío (sin error 5xx).
4. Login con Google completo desde el front (callback + cookie + refresh).
5. Una solicitud a profesional ⇒ email/Telegram en los minutos siguientes.

## 8. Riesgos y límites conocidos

- **Plan Hobby**: Container Runtime sigue los límites y la pricing Active CPU
  de Vercel Functions (cuota de CPU-hours incluida; el contenedor escala a
  cero a los 5 min sin tráfico en producción y 30 s en preview; máximo 300 s
  por request).
- **Cold start de ~35-45 s** (medido: `Started MantiBackendApplication in
  34.5 s` en fra1): el arranque en frío se paga en la PRIMERA petición de cada
  ciclo de vida del contenedor (el relay la retiene, no la falla). Mejoras
  futuras, por orden de rentabilidad: Spring AOT (`process-aot` + runtime
  `-Dspring.aot.enabled=true`) o imagen nativa GraalVM para bajar el arranque
  a <10 s; sonda de keep-alive cada <5 min en producción si el tráfico es
  muy esporádico.
- **Cuota de builds de contenedores**: cada push construye la imagen del
  backend (capa de dependencias cacheada por Vercel). Si se agota la cuota de
  builds del plan, los pushes no despliegan hasta el reset.
- **Previews y base de datos**: los previews del API usan las MISMAS variables
  de entorno de producción (incluida Neon): un preview ejecuta el código de la
  rama contra la BD de producción (Flyway no aplica nada nuevo si no hay
  migraciones, pero un PR que borre datos puede hacerlo). Mitigación futura:
  rama de Neon por preview (Neon branching) con API key.
- **Previews del frontend**: `NEXT_PUBLIC_API_URL` es estática y apunta al API
  de producción; las llamadas desde un preview (`*-git-*.vercel.app`) al API
  son cross-origin y el backend sólo permite el origen de producción ⇒ la API
  quedará bloqueada por CORS en previews hasta que (a) se amplíe
  `ALLOWED_ORIGINS` con patrones de preview en el backend, o (b) el frontend
  soporte base relativa (same-origin) en preview. La API es usable directamente
  por URL (health, smoke) sin CORS.
- **Hobby no comercial**: los planes Hobby de Vercel y Neon prohíben el uso
  comercial; migrar a Pro al monetizar.
- **Imagen duplicada** (`Dockerfile`/`Dockerfile.vercel`): deuda de
  sincronización descrita en §2.
