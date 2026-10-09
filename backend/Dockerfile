# syntax=docker/dockerfile:1
# Backend Spring Boot (skill docker del repo).
# - Builder: Maven 3.9 + JDK 25 (temurin). Capa de dependencias cacheada:
#   pom.xml + mvnw ANTES del código; sólo cambia si cambia el pom.
# - Runtime: JRE 25 Alpine + relay socat, usuario no-root, sin Maven ni fuentes.
#
# POR QUÉ un relay delante de Java: el Container Runtime de Vercel exige que
# algo acepte TCP en $PORT en ~28 s (startup timeout); el arranque de Spring
# Boot (JVM + Flyway + Hibernate + Neon) lo supera y Vercel mata la
# invocación con FUNCTION_INVOCATION_FAILED. socat escucha en $PORT al instante
# (el probe de la plataforma pasa) y cada petición se RETIENE en
# /app/relay.sh hasta que Tomcat escuche en el puerto interno 8081; el relay
# es TCP crudo, así que los X-Forwarded-* que inyecta el proxy de Vercel
# viajan intactos. Vercel fija PORT=8080 en project settings. Ver
# .github/DEPLOY.md.

FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -ntp -q dependency:go-offline
COPY src ./src
RUN ./mvnw -B -ntp -q package -DskipTests

FROM eclipse-temurin:25-jre-alpine AS runtime
RUN addgroup -S app && adduser -S app -G app \
 && apk add --no-cache socat
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
# Relay de arranque: acepta en 8080 ya (sondeo de arranque de Vercel) y
# retiene cada peticion hasta que Tomcat escuche en 8081.
RUN printf '%s\n' \
    '#!/bin/sh' \
    'until nc -w1 127.0.0.1 8081 </dev/null 2>/dev/null; do sleep 1; done' \
    'exec socat - TCP:127.0.0.1:8081' > /app/relay.sh \
 && chmod 755 /app/relay.sh
USER app
EXPOSE 8080
ENV PORT=8080
# Salud contra la APP (8081), no contra el relay: si Java no arranca, rojo.
HEALTHCHECK --interval=30s --timeout=3s --start-period=120s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:8081/actuator/health" | grep -q '"status":"UP"' || exit 1
# El relay acepta en $PORT (8080) de inmediato; Java escucha en el interno 8081.
ENTRYPOINT ["sh","-c","socat TCP-LISTEN:${PORT:-8080},fork,reuseaddr SYSTEM:/app/relay.sh & exec java -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/urandom -Dserver.port=8081 -jar app.jar"]
