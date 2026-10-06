---
description: Exporta openapi.yaml, lo lint con redocly y genera el PDF de la documentación API siguiendo la skill openapi-docs.
agent: build
---

Genera la **documentación de la API** (spec + PDF) de **MantiInmobiliaria**.

Entrada del usuario: $ARGUMENTS (ámbito opcional; vacío = API completa).

Pasos:

1. Lee `.opencode/skills/openapi-docs/SKILL.md` (y `api-rest` si hace falta) y sigue ese pipeline al pie de la letra.
2. Levanta el backend si no está corriendo (`cd backend && ./mvnw -q spring-boot:run`, con `docker compose up -d` para la BD).
3. **Exporta la spec**:
   ```
   curl -s http://localhost:8080/v3/api-docs -o docs/openapi/openapi.yaml
   ```
   (ajusta ruta/extensión a lo que indique la skill y al directorio real `docs/openapi/`).
4. **Lint**: `npx @redocly/cli lint docs/openapi/openapi.yaml` → corrige lo que esté en tu alcance (anotaciones springdoc en controllers) y lista lo que no.
5. **PDF**: ejecuta el pipeline de la skill — `openapi-generator` a AsciiDoc + `asciidoctorj`, o el comando `/docs-pdf` definido en el proyecto.
6. **Verifica** que el PDF existe, tiene tamaño > 0 y fecha reciente; si no, diagnóstica y reporta el fallo.
7. Commits convencionales en inglés solo si el usuario lo pide (`docs(api): refresh openapi spec`).

**Salida esperada**:
- Rutas (absolutas) de `openapi.yaml` y del **PDF** con tamaño.
- Resultado del lint (errores/warnings restantes y si son bloqueantes).
- Comandos exactos para regenerar spec y PDF.
- Endpoints sin documentar o con contrato pendiente de decisión.
