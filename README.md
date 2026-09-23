# CreateYourPizza

A pizza-store **product catalog**: staff maintain simple items, combos, and customizable pizzas; anyone can download a public PDF menu; trusted systems query the same catalog over JWT-protected APIs.

v1 is catalog + public menu + auth. There are **no** orders, carts, payments, or delivery.

## What you get

| Who | What they can do |
|-----|------------------|
| **Admin (staff)** | Log in with username/password; create, update, and delete products and pizza options; approve or revoke trusted systems; list users |
| **Public** | `GET /api/menu.pdf` — raw PDF, no login. Optional `?version=` for history |
| **Trusted system** | Register (pending) → admin approve → API key + secret → `POST /auth/token` → same catalog **read** APIs as admin |

**Product rules (v1)**

- **Simple** — fixed-price sellable item
- **Combo** — combination of simples; price is **admin-set**, not the sum of components
- **Pizza** — customizable; shared option catalog (crust size / crust type / toppings), per-option price, `optionsEnabled` flag. Consumer listing types are `simple`, `combo`, `pizza-base`, and `pizza-spec`
- **Veg / non-veg** on all three types

The PDF is headed **Create Your Pizza**, prints version **vN**, and lists sellable name + price. Pizzas with options note **options available**; pizza-spec options sit in their own section. Version bumps **only when a PDF is generated**, not on every write.

## Architecture

Two independent Spring Boot services (no parent POM), each with its own Postgres. Catalog never reads the auth database and never calls `/auth/validate` — it verifies JWTs locally against auth JWKS.

```
                    ┌─────────────────┐
   admin / trusted  │  auth-service   │  :8080
   login / token    │  JWT + JWKS     │──── GET /auth/.well-known/jwks.json
                    │  auth-db :5432  │
                    └────────┬────────┘
                             │ JWKS (HTTP)
                    ┌────────▼────────┐
   catalog CRUD /   │ catalog-service │  :8081
   queries + PDF    │  catalog-db     │  :5433 (host)
                    │  Redis :6379    │  cache, latest menu, locks
                    └─────────────────┘
                             ▲
                    GET /api/menu.pdf  (public, no auth)
```

**Redis** holds the catalog cache (`create-your-pizza/catalog:*`, TTL 3m), the latest menu PDF, and two locks: PDF generation **120s**, catalog write **30s**. Writes while a PDF is generating return **503** with `Retry-After: 60`. If a write is in progress the PDF job **skips** (it is not queued); the dirty flag stays true.

## Stack

Java **26**, Spring Boot **4.1.1**, Maven (JAR), PostgreSQL 16, Redis 7. Config is `application.properties`. Lombok on both apps.

| Path | Role |
|------|------|
| `auth-service/` | Auth, JWKS, admin and trusted principals — port **8080** |
| `catalog-service/` | Catalog CRUD/queries, Redis, menu PDF — port **8081** |
| `docker-compose.yml` | `auth-db`, `catalog-db`, `redis`, both apps |
| `docs/openapi/` | Committed static OpenAPI (the integrator contract) |
| `docs/` | Spec, design, stories, verify evidence, HIFL process |

## Run the stack

**Prerequisites:** JDK 26, Maven 3.9+, Docker.

```bash
docker compose up --build
```

| Service | Host port |
|---------|-----------|
| `auth-db` | `5432` |
| `catalog-db` | `5433` → container `5432` |
| `redis` | `6379` |
| `auth-service` | `8080` |
| `catalog-service` | `8081` |

On first start, if no ADMIN exists, **auth-service bootstraps one and prints username + password to stdout**:

```bash
docker compose logs auth-service | grep -i admin
```

Use those credentials with `POST /auth/login`. Add further admins with `POST /auth/admins` (admin JWT).

To run the apps on the host instead, start the three data services (or full Compose) and:

```bash
mvn -f auth-service spring-boot:run
mvn -f catalog-service spring-boot:run
```

Defaults: auth JDBC `localhost:5432/auth`, catalog JDBC `localhost:5433/catalog`, Redis `localhost:6379`, JWKS `http://localhost:8080/auth/.well-known/jwks.json`.

## APIs

There is **no** Swagger UI and **no** live `/v3/api-docs`. Import the committed files into Postman (or any OpenAPI tool) without starting the services:

| Service | YAML | JSON |
|---------|------|------|
| auth-service | [docs/openapi/auth-service.yaml](docs/openapi/auth-service.yaml) | [docs/openapi/auth-service.json](docs/openapi/auth-service.json) |
| catalog-service | [docs/openapi/catalog-service.yaml](docs/openapi/catalog-service.yaml) | [docs/openapi/catalog-service.json](docs/openapi/catalog-service.json) |

After endpoint changes, regenerate with `./scripts/generate-openapi.sh` (Docker; JDK/Maven run in a temporary container) and commit `docs/openapi/*`.

**Auth (high level)**

- `POST /auth/login` — admin username/password → JWT (30-minute TTL)
- `POST /auth/register` → admin approve → API key + secret (once) → `POST /auth/token` → JWT
- `GET /auth/.well-known/jwks.json` — public keys for local verification
- Admins cannot DELETE themselves. Revoking a trusted credential stops **new** tokens; existing JWTs work until `exp`. There is no JWT denylist.

**Catalog (high level)**

- `GET /api/menu.pdf` — public raw PDF; `?version=` for a past generation
- `GET /api/products` — filtered, paginated list (default page size 10, max 100): veg/non-veg, type, price under Rs. X
- `POST` / `PUT` / `DELETE` `/api/products` and `/api/options` — admin JWT; writes during PDF lock → **503** + `Retry-After: 60`

## Tests

```bash
mvn -f auth-service test
mvn -f catalog-service test
```

Suites are mocked (no Compose required). `POST /test/pdf/generate` exists only on the test profile.

## Docs

Product decisions, design, and the integrator contract live under [`docs/`](docs/README.md). Clone/setup notes for agents: [`AGENTS.md`](AGENTS.md).
