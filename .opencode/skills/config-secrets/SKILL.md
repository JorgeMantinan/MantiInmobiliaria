---
name: config-secrets
description: Use when configurando perfiles Spring, variables de entorno, secretos, .env/.env.example, application-dev/prod.yml o revisando que no haya claves filtradas en git y logs.
---

# Configuración y secretos

## Reglas

1. **Perfiles Spring.** `application.yml` (base, sin secretos) + `application-dev.yml` + `application-prod.yml`; activación con `SPRING_PROFILES_ACTIVE` (dev local, prod en el hosting).

```yaml
# application.yml (base)
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/manti}
    username: ${DB_USER:manti}
    password: ${DB_PASSWORD:manti_dev}   # default sólo para dev
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
```

2. **Todos los secrets por variables de entorno.** El valor por defecto sólo existe en dev y nunca es real. Prohibido secretos reales en `application*.yml` o en git.

```yaml
# application-prod.yml — SIN valores reales, todo${...}
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
security:
  jwt-secret: ${JWT_SECRET}
oauth:
  google:
    client-id: ${GOOGLE_CLIENT_ID}
    client-secret: ${GOOGLE_CLIENT_SECRET}
brevo:
  api-key: ${BREVO_API_KEY}
telegram:
  bot-token: ${TELEGRAM_BOT_TOKEN}
```

3. **`.env.example` en la raíz** documentando CADA variable con descripción y valor de ejemplo ficticio (sin secretos reales); el `.env` real está en `.gitignore`. El `.env` local lo cargan docker-compose e IDE.

```bash
# .env.example
DB_URL=jdbc:postgresql://localhost:5432/manti
DB_USER=manti
DB_PASSWORD=cambiar_local
JWT_SECRET=generar-con-openssl-rand-base64-48
GOOGLE_CLIENT_ID=xxx.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=
BREVO_API_KEY=
TELEGRAM_BOT_TOKEN=
SPRING_PROFILES_ACTIVE=dev
NEXT_PUBLIC_API_URL=http://localhost:8080
```

4. **Secretos del proyecto** (Google OAuth id/secret, JWT secret, URLs de DB, Brevo API key, Telegram bot token): en prod, variables de entorno del hosting; en local, `.env`. Nunca en el repo, nunca en issues/PRs, nunca en el front.

5. **Rotación y logs.** Rotar cualquier secreto expuesto (y revocar credenciales). La configuración de logging NO debe imprimir datasources ni cabeceras de auth: revisar que no existan `logging.level` que viertan credenciales ni loggers de `spring.datasource`.

```yaml
# BAD
logging:
  level:
    org.springframework.web: DEBUG   # puede volcar bodies/tokens
```

6. **Frontend: sólo `NEXT_PUBLIC_*` público** (URL base de la API, flags públicos). NADA sensible en el cliente: cualquier `NEXT_PUBLIC_*` es visible en el bundle; todo secreto vive en el backend.

```ts
// GOOD
const BASE = process.env.NEXT_PUBLIC_API_URL ?? '/api';

// BAD
const KEY = process.env.NEXT_PUBLIC_BREVO_KEY; // expuesto en el navegador
```

7. **Verificación antes de cada release.**

```bash
git log -p | grep -iE "password|secret|api[_-]?key|token"   # búsqueda histórica
gitleaks detect --source . --verbose                        # escaneo de secretos (también en CI)
git status --porcelain                                      # confirmar que .env no está staged
```

8. **Checklist de configuración.**
    1. Ningún valor real en `application*.yml` ni en git.
    2. Todo `${VAR}` con default sólo en dev.
    3. `.env.example` completo y actualizado; `.env` gitignored.
    4. Variables de prod configuradas en el hosting (no en el repo).
    5. Front sin secretos; sólo `NEXT_PUBLIC_*` públicos.
    6. Sin logs de credenciales.
    7. `gitleaks` y grep histórico limpios.

### Checklist de revisión

- [ ] Perfiles `dev`/`prod` separados y activados por `SPRING_PROFILES_ACTIVE`
- [ ] Secrets sólo en variables de entorno; defaults sólo para dev
- [ ] `application.yml` sin credenciales reales; nada sensible commiteado
- [ ] `.env.example` documenta cada var sin valores reales; `.env` gitignored
- [ ] Prod configurado en variables de entorno del hosting
- [ ] Frontend sólo expone `NEXT_PUBLIC_*` públicos
- [ ] Sin logs que impriman datasources/passwords/tokens
- [ ] `git log -p | grep -i password` y `gitleaks` limpios antes de release
