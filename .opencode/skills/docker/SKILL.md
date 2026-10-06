---
name: docker
description: Use when the user mentions docker, Dockerfile, docker-compose, imagen, container, despliegue, healthcheck, .dockerignore o entorno de desarrollo del backend.
---

# Docker en MantiInmobiliaria

Stack fijo: Java 25, Spring Boot 4.1, Maven (`./mvnw`), PostgreSQL 18. Backend en `backend/`, frontend en `frontend/`, compose de desarrollo en la raíz (`docker-compose.yml`).

## Reglas

1. **Dockerfile multi-stage en `backend/Dockerfile`** con dos fases: `builder` (Maven + JDK 25) y `runtime` (JRE 25). La imagen final no contiene Maven, código fuente ni `target/`.

2. **Capa de caché de dependencias:** copiar primero `pom.xml`, ejecutar `dependency:go-offline`, y solo después copiar el resto. Si solo cambia código, la capa de dependencias se reutiliza.

```dockerfile
# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:25-jre-alpine AS runtime
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-Djava.security.egd=file:/dev/urandom","-jar","app.jar"]
```

3. **Tag fijo siempre.** Prohibido `latest` y etiquetas implícitas: `eclipse-temurin:25-jre-alpine`, `maven:3.9-eclipse-temurin-25`, `postgres:18-alpine`. En CI, además, etiquetar con la versión/git-sha: `manti-backend:0.0.1-abc1234`.

4. **Secrets nunca en build args, ni en `ENV` de build, ni en la imagen.** Solo variables de entorno en tiempo de ejecución (`environment:` / `env_file:` en compose, secret manager en prod). Un `ARG` con contraseña queda en el historial de capas: es un leak.

```yaml
# BAD
build:
  args: { DB_PASSWORD: manti_dev }
# GOOD
environment:
  DB_PASSWORD: ${DB_PASSWORD}
```

5. **`.dockerignore` obligatorio** en `backend/`:

```
target/
node_modules/
.git/
.gitignore
*.md
frontend/
.opencode/
```

6. **Compose de desarrollo en la raíz** (ampliar el existente, no duplicarlo): `postgres:18-alpine` con healthcheck, volumen con nombre y `depends_on: condition: service_healthy` para el backend.

```yaml
services:
  postgres:
    image: postgres:18-alpine
    environment:
      POSTGRES_DB: manti
      POSTGRES_USER: manti
      POSTGRES_PASSWORD: ${DB_PASSWORD:-manti_dev}
    ports: ["5432:5432"]
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U manti -d manti"]
      interval: 5s
      timeout: 3s
      retries: 10

  backend:
    build: ./backend
    image: manti-backend:dev
    depends_on:
      postgres: { condition: service_healthy }
    environment:
      DB_HOST: postgres
      DB_USER: manti
      DB_PASSWORD: ${DB_PASSWORD:-manti_dev}
    ports: ["8080:8080"]
```

7. **Tamaño objetivo: < 250 MB** para la imagen del backend (JRE Alpine + jar solo). Si se supera: revisar que el builder no se haya colado en la fase final, quitar `spring-boot-devtools` del empaquetado final y evitar `COPY src` en la imagen runtime.

8. **Seguridad de runtime:** usuario no-root (`USER app`), `read_only: true` sobre el filesystem si es viable, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`, sin `--privileged`.

```yaml
backend:
  cap_drop: [ALL]
  security_opt: ["no-new-privileges:true"]
  read_only: true
  tmpfs: [/tmp]
```

9. **Sin volúmenes de código en producción.** El bind-mount `./src:/app/src` es solo para desarrollo con `spring-boot-devtools`/live-reload; nunca en la imagen de release.

10. **Debug (JPDA) solo en dev:** exponer el puerto 5005 y arrancar con el agente.

```yaml
# compose dev
environment:
  JAVA_TOOL_OPTIONS: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
ports: ["8080:8080", "5005:5005"]
```

11. **Imágenes oficiales y mínimas.** Prohibido `ubuntu`/`openjdk` completos como base de runtime; prohibido instalar `curl`, `bash` o paquetes innecesarios (usar `wget` de busybox en Alpine para el healthcheck).

12. **Compose no guarda estado de app.** Solo el volumen de PostgreSQL; los datos de la app (subidas) van en un volumen dedicado con ruta explícita.

13. **Flujo de comprobación sin builds del proyecto completo cuando no toca:** si el trabajo es solo de Docker, validar sintaxis (`docker compose config`) y, si se pide explícitamente, `docker build`. No tocar `pom.xml` ni código fuente.

## Checklist de revisión

- [ ] `backend/Dockerfile` es multi-stage: builder `maven:3.9-eclipse-temurin-25` → runtime `eclipse-temurin:25-jre-alpine`.
- [ ] La capa de dependencias copia `pom.xml` + `dependency:go-offline` antes de `COPY src`.
- [ ] Runtime con usuario no-root, `EXPOSE 8080`, `HEALTHCHECK` contra `/actuator/health`, `-XX:MaxRAMPercentage=75.0`.
- [ ] Ninguna imagen usa tag `latest`; todos los tags son fijos.
- [ ] Ningún secreto en `ARG`, `ENV` de build, Dockerfile o commit del repo.
- [ ] `.dockerignore` excluye `target/`, `node_modules/`, `.git/`, `frontend/`.
- [ ] Compose de dev: `postgres:18-alpine` con healthcheck y `depends_on: service_healthy`.
- [ ] Imagen final < 250 MB y sin binarios de build.
