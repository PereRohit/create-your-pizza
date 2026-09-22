# Static OpenAPI (Swagger) specs

**Hard requirement:** these committed files are the only OpenAPI delivery artifact for Postman / integrators. Import **without starting services**. Runtime apps do **not** serve Swagger UI or `/v3/api-docs`.

| File | Service |
|------|---------|
| [auth-service.yaml](auth-service.yaml) / [auth-service.json](auth-service.json) | auth-service |
| [catalog-service.yaml](catalog-service.yaml) / [catalog-service.json](catalog-service.json) | catalog-service |

## Regenerate after API changes

**Host needs Docker only** (no local JDK/Maven):

```bash
./scripts/generate-openapi.sh
```

Runs a temporary `eclipse-temurin:26-jdk` container (`--rm`), exports via each service’s `mvnw` + test-scoped SpringDoc API, writes into this directory on the host, then removes the container.

| Env var | Default |
|---------|---------|
| `OPENAPI_DOCKER_IMAGE` | `eclipse-temurin:26-jdk` |
| `OPENAPI_M2_VOLUME` | `create-your-pizza-m2` |

Commit the updated files after regeneration.
