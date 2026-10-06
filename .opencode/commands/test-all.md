---
description: "Ejecuta la suite completa: tests y build de backend y frontend, lint, y resume resultados y coverage."
agent: build
---

Ejecuta **toda la verificación** de **MantiInmobiliaria** y resume el estado.

Entrada del usuario: $ARGUMENTS (ámbito opcional: `backend`, `frontend`; vacío = ambos).

Pasos y comandos (ejecutar en orden y no saltarte ninguno):

**Backend** (`backend/`, Java 25 + Maven):
1. `./mvnw -q -DskipTests package` (Windows: `mvnw.cmd -q -DskipTests package`) — compila.
2. `./mvnw -q test` — suite completa (JUnit + Testcontainers; requiere `docker compose up -d` para la BD si los tests usan contenedores).
3. Coverage: lee `backend/target/site/jacoco/index.html` si el plugin está activo; si no, indícalo.

**Frontend** (`frontend/`, Node 24):
4. `npm ci` (si `node_modules` no está sincronizado).
5. `npm run lint`
6. `npm test` — **si el script no existe en `package.json`**, repórtalo como hueco (no inventes el script).
7. `npm run build`

Si algo falla: captura el error, identifica el archivo responsable y propón la corrección (o delega en `feature-developer`/`test-engineer`) — **sin dejar la suite roja** si te autorizan a arreglarlo.

**Salida esperada**:
- Tabla: `ámbito | comando | resultado (OK/FALLA) | duración aprox.`.
- **Failures**: archivo + error + causa probable (los primeros, no el ruido).
- **Coverage backend**: % actual vs meta >= 80% + huecos restantes.
- **Lint/build frontend**: resultado de cada uno.
- **Estado global**: VERDE / ROJO con el siguiente paso concreto.
