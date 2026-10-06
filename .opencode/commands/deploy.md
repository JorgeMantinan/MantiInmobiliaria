---
description: Verifica CI verde y despliega (Vercel frontend, contenedor backend, Neon) con deploy-agent, y comprueba el health final.
agent: build
---

Ejecuta el **despliegue** de **MantiInmobiliaria** con el subagente **`deploy-agent`**.

Entrada del usuario: $ARGUMENTS (entorno: `preview`/`production`; si está vacío, asume `preview` y pregunta antes de tocar `production`).

Pasos:

1. **Estado actual**: rama (`git branch --show-current`), CI en GitHub (`gh run list --limit 5` si `gh` está autenticado; si no, pide los logs o el estado). **No despliegues con CI rojo.**
2. Lanza **`deploy-agent`** con estas responsabilidades:
   - **Diagnóstico** de fallos de CI/despliegue (workflow `.github/workflows/ci.yml`: secret-scan, backend Java 25 + PG 18, frontend Node 24) y arreglo solo en su ámbito (`.github/`, Dockerfiles, `docker-compose*.yml`, `vercel.json`).
   - **Verificación local**: `docker build` del backend y `docker compose up -d` + `docker compose ps`.
   - **Pasos de despliegue descritos**:
     - *Frontend → Vercel*: proyecto apuntando a `frontend/`, build `npm run build`, output `.next`, dominos/entorno según `preview`/`production`.
     - *Backend → contenedor*: build de la imagen, migraciones Flyway al arrancar, variable `DB_*`/`SPRING_DATASOURCE_*` apuntando a Neon.
     - *Base de datos → Neon*: credenciales **solo como secrets** (Vercel/GitHub), backup previo confirmado, sin valores en claro en ningún archivo.
   - **Nunca exponer secretos** en logs, workflows ni informes.
3. **Verificación post-deploy (health)**:
   - Backend: `curl -fsS https://<host>/actuator/health` → `{"status":"UP"}`.
   - Frontend: `curl -fsS https://<frontend>/` → 200.
   - Smoke test: un endpoint real (p. ej. `GET /api/v1/...`) y una ruta de UI.
4. Si algo falla, diagnostica con `deploy-agent` y vuelve al paso 2 (sin repetir deploy a `production` sin permiso).

**Salida esperada**:
- **Estado**: CI (verde/rojo) + entorno desplegado (URL) + timestamp.
- **Pasos ejecutados** y **cambios aplicados** (archivos, en su ámbito).
- **Health checks**: comando + resultado de cada uno.
- **Secretos requeridos** (nombres, nunca valores) y cuáles faltan.
- **Rollback**: cómo revertir si aplica.
