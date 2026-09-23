# AGENTS.md — CreateYourPizza

Guidance for agents and humans cloning this repo. Stack: **Java 26**, **Spring Boot 4.1.1**, **Maven** (JAR), two independent sibling services.

## Layout

| Path | Role |
|------|------|
| `auth-service/` | Auth + JWKS + admin/trusted principals (port **8080**) |
| `catalog-service/` | Catalog CRUD/queries, Redis cache/locks, menu PDF (port **8081**) |
| `docker-compose.yml` | Root Compose: `auth-db`, `catalog-db`, `redis`, both apps |
| `docs/` | HIFL artifacts (`handoff.md`, playbook, stories, …). `hifl-playbook.md` is the stack-agnostic Agentic SDLC process — do not add this product’s stack or domain to it. |

There is **no** parent POM. Build each sibling with Maven `-f`.

## Prerequisites

- JDK **26**
- Maven 3.9+
- Docker (for Compose)

## Compose

```bash
docker compose up --build
```

Services:

| Compose service | Host ports |
|-----------------|------------|
| `auth-db` | `5432` |
| `catalog-db` | `5433` → container `5432` |
| `redis` | `6379` |
| `auth-service` | `8080` |
| `catalog-service` | `8081` |

Catalog reaches JWKS at `http://auth-service:8080/auth/.well-known/jwks.json` (`AUTH_JWKS_URL` in Compose).

## First admin credentials

On first start, if **no ADMIN** exists, **auth-service** bootstraps one and prints **username + password to stdout** (auth-service logs / terminal). Read Compose logs:

```bash
docker compose logs auth-service | grep -i admin
```

Use those credentials with `POST /auth/login`. Further admins: `POST /auth/admins` (admin JWT).

## Swagger / OpenAPI (Postman import)

**Hard requirement:** committed static OpenAPI files under [`docs/openapi/`](docs/openapi/) are the only integrator contract. Import them into Postman (or any OpenAPI tool) **without starting services**. Apps do **not** expose Swagger UI or live `/v3/api-docs`.

| Service | Static YAML | Static JSON |
|---------|-------------|-------------|
| auth-service | [docs/openapi/auth-service.yaml](docs/openapi/auth-service.yaml) | [docs/openapi/auth-service.json](docs/openapi/auth-service.json) |
| catalog-service | [docs/openapi/catalog-service.yaml](docs/openapi/catalog-service.yaml) | [docs/openapi/catalog-service.json](docs/openapi/catalog-service.json) |

### Regenerate when APIs change

**Requires Docker** (JDK/Maven run inside a temporary container — not needed on the host):

```bash
./scripts/generate-openapi.sh
```

Then commit updated `docs/openapi/*`. Details: [docs/openapi/README.md](docs/openapi/README.md).

## JWKS

```
GET http://localhost:8080/auth/.well-known/jwks.json
```

Catalog verifies JWTs locally via this JWKS URL. There is **no** `/auth/validate`.

## Maven

```bash
# Auth
mvn -f auth-service test
mvn -f auth-service -DskipTests package

# Catalog
mvn -f catalog-service test
mvn -f catalog-service -DskipTests package
```

## Local run (without Compose apps)

Start `auth-db`, `catalog-db`, and `redis` (or full Compose), then:

```bash
mvn -f auth-service spring-boot:run
mvn -f catalog-service spring-boot:run
```

Defaults: auth JDBC `localhost:5432/auth`, catalog JDBC `localhost:5433/catalog`, Redis `localhost:6379`, JWKS `http://localhost:8080/auth/.well-known/jwks.json`.

## HIFL / git

- Resume: [docs/handoff.md](docs/handoff.md) → [docs/hifl-playbook.md](docs/hifl-playbook.md)
- One ticket per branch: `feat/<id>-<summary>` for stories, `fix/<id>-<summary>` for bugs; **ask before commit**
- Stories under [docs/stories/](docs/stories/); defects + RCA in [docs/bugs.md](docs/bugs.md)

## Locked highlights (v1)

- Product types: simple / combo / pizza; consumer types include `pizza-base` / `pizza-spec`
- Public PDF: `GET /api/menu.pdf` (raw binary; `?version=` history)
- Auth: admin login vs trusted register → approve → token; cannot DELETE self
- Redis locks: PDF 120s, write 30s; writes during PDF lock → **503** + `Retry-After: 60`
