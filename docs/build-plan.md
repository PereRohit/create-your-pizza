# Build plan — CreateYourPizza

**Status:** DRAFT — 2026-09-18 (owner said **start**). Awaiting Build-plan gate: **Approve** / **Revise: …** / **Park**.

**Upstream:** [Intent](intent.md) (**APPROVED**) · [Spec / PRD](spec.md) (**APPROVED**) · [Design / TRD](design.md) (**APPROVED**)

**Related:** [Project context](project-context.md) · [HIFL playbook](hifl-playbook.md) · [Handoff](handoff.md) · [Stories](stories/README.md)

**Not in this stage:** Application code, `docs/stories/*.md` files (except README), Spring Initializr zips, `AGENTS.md`, OpenAPI YAML. Those start **after** this plan is **APPROVED**.

---

## 1. Status and scope

This plan turns APPROVED Design + Spec into an ordered Build: stack confirmation, Initializr packaging, story order, tests, Docker, and Definition of Done.

**In scope for Build plan**

- Confirm stack and two Maven apps (`auth-service`, `catalog-service`)
- Suggested Spring Initializr dependencies (owner still clicks Initializr)
- Ordered user-story list (files created only after **Approve**)
- Test plan mapped to Design §9
- Config properties, JWT `iss`/`aud`, Compose layout
- What Build must not do

**Out of scope for Build plan**

- Writing story markdown files (playbook: after Approve)
- Implementing Java, Flyway SQL, or Compose YAML
- Creating `AGENTS.md` or springdoc artifacts (Build stories)

---

## 2. Stack confirmation (locked)

| Item | Choice |
|------|--------|
| Language / build | **Java** + **Maven** (owner Initializr; do not hand-roll `pom.xml` from scratch if Initializr can emit it) |
| Framework | **Spring Boot 3** (owner picks the current 3.x on Initializr; stay on 3.x, not Boot 2) |
| Java | **21** preferred on Initializr (17 acceptable if owner prefers) |
| Apps | Two jars: **`auth-service`**, **`catalog-service`** — separate Maven projects (sibling modules or sibling folders; owner Initializr twice unless they prefer a parent POM — either is fine) |
| Auth DB | **auth-postgres** — Flyway in auth-service |
| Catalog DB | **catalog-postgres** — Flyway in catalog-service |
| Cache / locks | **Redis** — catalog-service only |
| JWT | **RS256**; catalog verifies locally from **JWKS HTTP** |
| API docs | **springdoc-openapi** per service (Postman-importable) |
| Tests | JUnit 5 + Spring Boot Test; Testcontainers for Postgres/Redis where a story needs a real store; `POST /test/pdf/generate` only on **test/dev profile** |
| Runtime | Docker Compose: `auth-postgres`, `catalog-postgres`, `redis`, `auth-service`, `catalog-service` |

**JWT identifiers (locked here — Design left them to Build)**

| Claim | Value |
|-------|--------|
| `iss` | `create-your-pizza-auth` |
| `aud` | `create-your-pizza-catalog` |

Catalog rejects tokens that fail `iss` / `aud` / signature / `exp` / required `scope`.

---

## 3. Owner Initializr (Build start)

Owner creates **two** projects (or one multi-module with two apps). Agent does **not** invent a third service.

### 3.1 Shared Initializr options

- Project: Maven · Language: Java · Spring Boot: 3.x · Packaging: Jar
- Java: 21 (or 17)
- Group / artifact: owner choice; suggested `com.createyourpizza` / `auth-service` and `catalog-service`

### 3.2 Suggested dependencies — `auth-service`

| Initializr (if listed) | Why |
|------------------------|-----|
| Spring Web | HTTP APIs |
| Spring Security | JWT issue; protect admin routes |
| Spring Data JPA | `users`, credentials, `verification_keys` |
| PostgreSQL Driver | auth-postgres |
| Flyway Migration | DDL |
| Validation | Request bodies |
| Lombok | Optional; skip if owner dislikes it |

**Add in Maven after Initializr (not always on start.spring.io):** `nimbus-jose-jwt` or Spring Authorization Server **is not required** — issue RS256 JWTs with Nimbus or `jjwt-api`/`jjwt-impl`/`jjwt-jackson`. Prefer **Nimbus JOSE JWT** (JWKS-shaped public keys). **springdoc-openapi-starter-webmvc-ui**. BCrypt via Spring Security (no extra dep).

**Do not add:** Redis, Spring Session, OAuth2 Authorization Server (overkill for v1 token issue).

### 3.3 Suggested dependencies — `catalog-service`

| Initializr (if listed) | Why |
|------------------------|-----|
| Spring Web | Catalog + PDF GET |
| Spring Security | Bearer JWT filter (resource server style, **local** verify) |
| Spring Data JPA | products, options, `menu_pdf`, `catalog_meta` |
| PostgreSQL Driver | catalog-postgres |
| Flyway Migration | DDL + seed |
| Spring Data Redis | cache + locks (`SET NX EX`) |
| Validation | Query/body |
| Lombok | Optional |

**Add in Maven after Initializr:** Nimbus (verify JWT from JWKS); **springdoc-openapi-starter-webmvc-ui**; PDF library **OpenPDF** (or Apache PDFBox) — basic text PDF only.

**Do not add:** Spring Session; a second DataSource to auth-postgres; `POST /auth/validate` client.

### 3.4 Agent wait

Until the owner drops Initializr trees into this repo, Build stories that need Java wait. Story **01** (Compose + properties contract) can start as YAML/docs in-repo **after** Approve even before jars exist, but coding stories attach to the Initializr trees.

---

## 4. Story order (create files after Approve)

Filenames under `docs/stories/`. Implement **one story at a time**. Record the in-progress file in [handoff.md](handoff.md). Rename to `DONE-` when finished. Every **coding** story: **>80% LoC** of that story’s new/changed code **and** full behaviour tests of its acceptance criteria.

| File | Depends on | Outcome |
|------|------------|---------|
| `01-compose-config.md` | — | Compose five services (apps may be stubs); volumes; env for DB/Redis/JWKS URL; **all MUST config keys** with defaults (PDF 5m, catalog TTL 3m, JWT 30m, lock 120s/30s) |
| `02-auth-schema-bootstrap.md` | 01 + Initializr auth | Flyway `users`, `trusted_client_credentials`, `verification_keys`; startup: if zero `ADMIN` → insert + **stdout** username/password; skip if ≥1 admin |
| `03-auth-jwks-jwt.md` | 02 | RS256 key in process; upsert public JWK; `GET /auth/.well-known/jwks.json`; issue JWT with locked claims + `iss`/`aud`/`app.jwt.ttl` |
| `04-auth-login-register-token.md` | 03 | Public `POST /auth/login`, `POST /auth/register` (trusted PENDING only; ignore/400 `role`), `POST /auth/token`; secret hashed; **never** in JWT |
| `05-auth-admin-users.md` | 04 | `POST /auth/admins`; paginated `GET /auth/users` (`role`, `status`, `page`, `size`); approve (secret **once**), deny, revoke; `DELETE` other users; **403 DELETE self** |
| `06-catalog-schema-seed.md` | 01 + Initializr catalog | Flyway products, combo_items, option_entities, menu_pdf, catalog_meta; seed simples/combo/pizza + option entities with **per-row prices** (FR-4a–c); `catalog_meta` dirty=true; **no** first-admin SQL; **no** `system_status` |
| `07-catalog-jwt-jwks.md` | 03, 06 | Memory JWKS from `AUTH_JWKS_URL`; startup load; unknown `kid` refetch; local verify; **no** auth DB; **no** `/validate`; 401/403 |
| `08-catalog-writes.md` | 07 | Admin CRUD products + options; pizza `optionsEnabled`; combo membership; dirty on success; Redis write lock; if PDF lock → **503** + `Retry-After: 60`; no Redis catalog-key delete; no `menu_pdf` bump on write |
| `09-catalog-queries.md` | 08 | `GET /api/products` union + filters (`category`, `type`, `maxPrice`, `page`/`size` default 10 max 100 clamp); pizza-spec rows; get-by-id product/option; Admin **and** Trusted same reads; Trusted cannot write |
| `10-catalog-redis-cache.md` | 09 | Redis-first `create-your-pizza/catalog:*`; TTL `app.cache.catalog-ttl`; fill on DB hit; **no** invalidation-on-write |
| `11-pdf-job-locks.md` | 08, 06 | Interval job + skip-not-queue; PDF lock 120s; generate header **Create Your Pizza**, **vN**, sellable + **options available** note + options **own space**; insert history; Redis latest JSON; version **only** on successful insert |
| `12-public-pdf.md` | 11 | `GET /api/menu.pdf` raw binary; latest Redis-first; `?version=` history DB (Redis OK if latest); 404 envelope if missing |
| `13-test-pdf-trigger.md` | 11 | `POST /test/pdf/generate` unauthenticated; **test/dev profile only**; same job algorithm |
| `14-openapi-agents.md` | 05, 12, 13 | springdoc both services; `AGENTS.md` (compose, first-admin **logs**, Swagger URLs, JWKS URL) |

Stories 08–13 are catalog-service; 02–05 are auth-service. Do not start 08 before 07.

---

## 5. Implementation notes (do not rediscover)

- Envelope + pagination as Spec/Design; docs omit empty keys — **coding may still emit** success `error: ""`.
- DTOs invented at coding time; wire JSON locked.
- Principal type from **URL** (`/auth/register` vs `/auth/admins` vs bootstrap).
- Catalog **never** opens auth-postgres.
- Redis keys and lock algorithm: Design §6–7 exactly (`finally` DEL + TTL).
- Option kinds only `CRUST_SIZE` \| `CRUST_TYPE` \| `TOPPING`.
- Consumer types: `simple` / `combo` / `pizza-base` / `pizza-spec`.
- `maxPrice` is **strictly less than** on product **and** option prices.
- `category` filter excludes pizza-spec unless `type=pizza-spec` (then ignore category).
- Default list sort: `created_at` ascending across the union; filter wins.
- Outstanding JWTs valid until `exp` after revoke.

**MUST properties**

| Property | Default |
|----------|---------|
| `app.pdf.interval` | 5 minutes |
| `app.cache.catalog-ttl` | 3 minutes |
| `app.jwt.ttl` | 30 minutes |
| `app.lock.pdf-ttl` | 120 seconds |
| `app.lock.write-ttl` | 30 seconds |

Plus datasource URLs, Redis URL, `AUTH_JWKS_URL` (catalog), `iss`/`aud` as constants or properties matching §2.

---

## 6. Test plan

Cover Design §9. Prefer tests **inside the story** that introduces the behaviour.

| Area | Must prove |
|------|------------|
| Bootstrap | Admin created only when zero admins; credentials on stdout; second start does not mint another |
| Trusted | Register PENDING; `/auth/token` fails until approve; secret once on approve; deny; revoke blocks new tokens |
| Same reads | Admin login JWT and trusted token JWT both `GET /api/products` |
| Writes | Trusted 403 on catalog writes; Admin CRUD; cannot DELETE self (403) |
| Users list | `GET /auth/users` paginated like catalog |
| Listing | `type=pizza-spec` all options; untyped list can include them; filters; size 10; max 100 clamp; flat `data`; `pagination.next=-1` |
| JWT path | Catalog uses JWKS HTTP only |
| PDF | **vN**; **options available** when flag true; pizza-spec own space; latest Redis; `?version=` historical; unknown 404; version **not** bumped on product write |
| Locks | Write during PDF lock → 503; job **skips** if write lock; dirty stays true |
| Cache | Catalog Redis TTL from config |
| Options | Per-row `price` on list and PDF |
| Test trigger | Profile-gated; same skip/lock rules |

---

## 7. Definition of Done (Build, after stories)

- [ ] All `docs/stories/` coding stories `DONE-` with tests as required
- [ ] `docker compose up` brings auth-postgres, catalog-postgres, redis, both apps
- [ ] First admin visible in **auth-service logs**
- [ ] Sample catalog + options in catalog-postgres; first PDF job can produce **v1**
- [ ] Swagger UI per service; OpenAPI importable
- [ ] `AGENTS.md` at repo root
- [ ] No `/auth/validate`; no shared Postgres; no `system_status`

Then Stage 6: draft [docs/verify.md](verify.md).

---

## 8. What NOT to do

- Do not write `docs/stories/0*.md` until this plan is **APPROVED**
- Do not git-commit unless the owner confirms
- Do not scaffold Java before owner Initializr (except waiting)
- Do not implement customer register/login
- Do not put API secrets in JWT
- Do not share one Postgres
- Do not call `/auth/validate` or read auth DB from catalog
- Do not add JWT denylist
- Do not derive combo price from simples
- Do not queue skipped PDF runs
- Do not increment PDF version on admin writes

---

## 9. Gate

Build plan is **DRAFT**.

Please respond with exactly one of:

- **Approve** — accepted; next: write `docs/stories/` in the table above, then owner Initializr → implement story 01…
- **Revise: \<feedback\>** — this file only; no stories, no code
- **Park** — pause

**Git:** no commit unless you confirm (you asked not to commit this start).
