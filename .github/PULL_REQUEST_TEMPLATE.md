<!-- Plantilla de Pull Request — MantiInmobiliaria -->
<!-- Base: develop (master solo para hotfixes/release). Commits: Conventional Commits en inglés. -->

## Qué cambia

<!-- Describe el cambio concreto: backend, frontend, docs, infra. Una o dos frases. -->

## Por qué

<!-- Problema que resuelve o ticket/issue que cierra. Ej: "Cierra #42". -->

## Cómo se prueba

<!-- Pasos reproducibles. Incluye comandos y una llamada curl o ruta de UI. -->

```bash
# backend
cd backend && ./mvnw -q test
# frontend
cd frontend && npm run lint && npm run build
```

1.
2.

## Checklist

- [ ] Tests locales OK (`./mvnw test` backend / `npm test` frontend si existe)
- [ ] Lint OK (`npm run lint`)
- [ ] Build OK (`./mvnw -DskipTests package` / `npm run build`)
- [ ] Sin secretos ni credenciales en el diff
- [ ] Docs API actualizadas si aplica (`openapi.yaml`, anotaciones springdoc)
- [ ] Migración Flyway nueva añadida si cambia el esquema (sin editar migraciones aplicadas)
- [ ] Screenshots si hay cambios de UI

## Riesgo/despliegue

<!-- Riesgo: bajo / medio / alto. ¿Requiere migración, seeds, configuración de secrets, pasos manuales en Vercel/Neon? ¿Rollback? -->

- Riesgo:
- Migración/datos:
- Despliegue:
