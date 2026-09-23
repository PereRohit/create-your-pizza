# CreateYourPizza — Agentic SDLC handoff

**Audience:** Next agent continuing HIFL — **all six stages APPROVED**. Build **01–15** plus bug tickets **16–17** are `DONE-`; [Verify](verify.md) **APPROVED 2026-09-23** with an empty defect table. **v1 is delivered and verified.**  
**Owner:** PereRohit  
**As of:** 2026-09-23 (Verify approved; BUG-01 and BUG-02 both closed after full regressions; one accepted residual, recorded in [verify.md](verify.md) §10)  
**Repo root:** local `create-your-pizza`  
**Git:** branch **`fix/17-openapi-error-responses`**, cut from `main`; stories **01–15** and bug **16** already merged. Ticket-17 code + docs are committed on this branch and **not yet merged to `main`** — open the PR when the owner asks.

**How to resume:** read [docs/hifl-playbook.md](hifl-playbook.md) → this file → open linked artifacts as needed. After every stage **Approve**: **compress** completed stages here, then refresh next-stage items — do **not** wipe and fully rewrite.

## Current state

| Stage | Artifact | Status |
|-------|----------|--------|
| 1 Intent | [docs/intent.md](intent.md) | **APPROVED** (aligned 2026-09-18) |
| 2 Spec / PRD | [docs/spec.md](spec.md) | **APPROVED** (aligned 2026-09-18) |
| 3 Design / TRD | [docs/design.md](design.md) | **APPROVED** 2026-09-18 |
| 4 Build plan | [docs/build-plan.md](build-plan.md) | **APPROVED** 2026-09-18; **Revise APPROVED** 2026-09-21 (§5.1) |
| 5 Build | Spring Boot + Compose in this repo | **DONE** — `DONE-01`–`DONE-15` on `main`; bug tickets `DONE-16`, `DONE-17` |
| 6 Verify | [verify.md](verify.md) + [bugs.md](bugs.md) | **APPROVED** 2026-09-23 — BUG-01 and BUG-02 both closed by full regression; **0 open defects**; one accepted residual |

Process: [docs/hifl-playbook.md](hifl-playbook.md) · Decisions: [docs/project-context.md](project-context.md) · Docs index: [docs/README.md](README.md)

## Past stages (compressed)

### 1 Intent — APPROVED 2026-09-17

- Problem: single catalog for Simple / Combo / Pizza; public PDF; trusted JWT APIs; no orders/payments/delivery in v1.
- Actors: Admin, public PDF consumer, trusted system; future CUSTOMER provisioned only.
- Stack locked: Java Spring Boot + Maven; owner Initializr; deps at Build; full Dockerize.
- Detail: [docs/intent.md](intent.md)

### 2 Spec / PRD — APPROVED 2026-09-17 (revise c); **aligned 2026-09-18**

- FRs/NFRs + acceptance for catalog, option entities, queries, PDF, auth, Docker/OpenAPI/tests.
- Binding: JWT claims/scopes; envelope + pagination; page size 10 max 100; types `simple`/`combo`/`pizza-base`/`pizza-spec`.
- **2026-09-18 alignment:** trusted pending+approve+token; bootstrap admin; PDF history + `?version=`; pizza-spec on PDF and list API; Redis locks; one DB per service; JWKS.
- Detail: [docs/spec.md](spec.md) · log: [docs/project-context.md](project-context.md)

### 3 Design / TRD — APPROVED 2026-09-18

- Two services: **auth-service** + **catalog-service**; **one Postgres each** (Compose/DB names **`auth-db`**, **`catalog-db`**); **HikariCP** JDBC pool per app (`spring.datasource.hikari.*`); catalog Redis (cache, latest PDF, locks); Compose those five.
- Auth: admin login vs trusted `POST /auth/register` → PENDING → approve (API key+secret once) → `POST /auth/token`; bootstrap first admin; **cannot DELETE self**; paginated `/auth/users`; principal type from **URL**; JWKS `GET /auth/.well-known/jwks.json`; **no** `/validate`; **no** catalog reading auth DB.
- Catalog: `product_type` simple|combo|pizza; **option_entities** pizza-only; shared catalog; pizza **`optionsEnabled`**; combo price admin-set; veg/non-veg all three.
- PDF: header Create Your Pizza; vN; sellable name+price; pizza with options: note **options available**; pizza-spec in **own space**; history in catalog DB; Redis latest only; GET raw PDF; `?version=` history; version **only on successful generate**.
- Locks: Redis `create-your-pizza/lock:pdf-generation` TTL **120s**, `create-your-pizza/lock:catalog-write` TTL **30s**; `finally` DEL + expiry; writes during PDF lock → 503 + Retry-After 60; job **skips not queued** if write lock; dirty stays true.
- Catalog Redis `create-your-pizza/catalog:*` TTL 3m Redis-first; no invalidation-on-write. Menu key `create-your-pizza/menu`.
- Config MUST: `app.pdf.interval` 5m, `app.cache.catalog-ttl` 3m, `app.jwt.ttl` 30m, lock TTLs. Test `POST /test/pdf/generate` test profile only.
- Stories: `docs/stories/{priority}-{slug}.md` after Build-plan Approve; coding stories >80% LoC + full behaviour tests.
- Detail: [docs/design.md](design.md)

### 4 Build plan — APPROVED 2026-09-18; Revise APPROVED 2026-09-21 §5.1

- Stack: Spring Boot **4.1.1**, Maven JAR, Java **26** (Initializr **25** then pin POM), `.properties`, Lombok both apps; **HikariCP** pool defaults in both `application.properties` (max **10**, min idle **2**); Compose may override via `SPRING_DATASOURCE_HIKARI_*`.
- Independent siblings: `groupId` **`com.createyourpizza`**; artifacts **`auth-service`**, **`catalog-service`**; packages `com.createyourpizza.auth` / `com.createyourpizza.catalog`.
- Catalog: OAuth2 Resource Server + JWKS URL. Tests: mock ports. One root Compose. Graph of stories **01–15** (enabler **02** = owner Initializr).
- **§5.1 (2026-09-21):** Auth service-ready Compose smoke = last task on **06** before `DONE-`; Catalog read-path service-ready smoke = last task on **10** before `DONE-` (**11–14** still later). **08** waits for **04**, **07**, and **06** `DONE-`; **11** waits for **10** `DONE-`. PDF **12** may follow **09** without waiting on **10**. Story `mvn test` stays mocked; Stage 6 Verify remains full residual pack.
- Candid review **loop cap = 3** review→fix cycles ([playbook](hifl-playbook.md)).
- Detail: [docs/build-plan.md](build-plan.md) §5.1 · §6

### 5 Build — DONE (stories 01–15 merged to `main`)

- Built one story at a time along the [§6 graph](build-plan.md), each on its own `feat/<id>-<slug>` branch with a candid review loop before its `DONE-` rename. Index: [stories/README.md](stories/README.md).
- Enablers **01** Compose and **02** owner Initializr first; then auth **03–06** (schema + bootstrap, JWKS/JWT, login/register/token, admin+users), then catalog **07–14** (schema + seed, JWT via JWKS, writes, queries, Redis cache, PDF job + locks, public PDF, test trigger), then **15** static OpenAPI + `AGENTS.md`.
- §5.1 service-ready Compose smokes passed as the last task on **06** (auth) and **10** (catalog read path).
- Last merge: PR #24 `feat/15-openapi-agents`.

### 6 Verify — APPROVED 2026-09-23

- [verify.md](verify.md) drafted from Spec §8 + Design §9 / Build-plan §8: mocked suites plus a live residual pack on a fresh Compose stack, run with `APP_JWT_TTL=1m` / `APP_PDF_INTERVAL=1m` / `APP_CACHE_CATALOG_TTL=30s` so time-dependent behaviour is observable.
- Two defects found, both registered with full RCA in [bugs.md](bugs.md), each fixed on its own `fix/` branch and closed only after a **full** regression (both suites **and** the entire live pack), per [hard rule 10](hifl-playbook.md#defects-found-at-verify-bug-tickets).
  - **[BUG-01](bugs.md#bug-01--catalog-redis-beans-never-wired)** (blocking) — catalog silently ran in-memory fallbacks for cache, latest-menu, and locks: `@ConditionalOnBean(StringRedisTemplate.class)` sat on application `@Configuration`, evaluated before `DataRedisAutoConfiguration` registers the template. Fixed via `ObjectProvider` per port. Ticket [DONE-16](stories/DONE-16-fix-redis-wiring.md), PR #25, closed 2026-09-22.
  - **[BUG-02](bugs.md#bug-02--static-openapi-omits-503-and-all-error-responses)** (docs contract, no runtime impact) — the committed OpenAPI documented only HTTP 200. Fixed with one `OpenApiCustomizer` per service and a committed-file guard. Ticket [DONE-17](stories/DONE-17-openapi-error-responses.md), closed 2026-09-23.
- Final state: auth **83** tests, catalog **138** (1 skipped each); live residual pack **120 checks, 0 failures**; defect table empty; one accepted residual (unreferenced `bearerAdminJwt` security scheme in the auth spec) recorded in [verify.md](verify.md) §10.

## Owner preferences (must follow)

1. **Git commits:** Do **not** commit unless the owner **confirms**. After changes, **ask**.
2. **Stage-end handoff:** On every stage **Approve**, **compress** past stages and refresh next-agent sections — do not blank-rewrite.
3. **Stack:** Java Spring Boot **4.1.1** + Maven + JAR; Java **26** (Initializr **25** then pin POM); independent siblings `auth-service` / `catalog-service`; Lombok; properties files; owner Initializr. Prefer Spring / Hibernate / Lombok / JDK and Build-plan libraries over hand-rolled boilerplate.
4. **Gate language:** Approve / Revise: … / Park — no silent skips.
5. **Build:** one story at a time; follow [build-plan.md](build-plan.md) §6 graph (incl. §5.1 smokes on **06** / **10**); rename to `DONE-` when finished.
6. **Story git branches:** `feat/<story-id>-<max-5-word-summary>` (example `feat/01-compose-and-config`); **only** that story’s changes on the branch; still ask before commit.
7. **Owner stories:** filename `{id}-OWNER-{slug}.md` when the owner must act; task lines prefixed **`Owner:`**.
8. **Candid review loop** ([playbook](hifl-playbook.md#candid-review-loop)): before every stage gate and before a story is renamed `DONE-`. Fresh reviewer, then fresh fix agent. **Loop cap = 3** review→fix cycles. Ephemeral handoff is prompt-only and must not enter this file. Later stages and later stories are not findings. The loop does not replace Approve / Revise / Park.
9. **No reinventing the wheel:** Prefer Spring Boot / Spring Security / Spring Data / Hibernate / Lombok / JDK APIs and approved libraries (Nimbus, OpenPDF per Build plan) over hand-rolled equivalents. Prefer annotations and framework injection over boilerplate constructors, getters, timestamp/id callbacks, and custom wrappers when the framework already provides them. Do not invent a utility or abstraction that duplicates a library or Spring feature.
10. **Service-ready smoke (§5.1):** last task before `DONE-` on **06** (auth) and **10** (catalog read-path); do not skip; do not require Compose for every story’s `mvn test`.
11. **Static OpenAPI (hard):** committed specs under [`docs/openapi/`](openapi/) are the **only** Postman/import contract (no Swagger UI / live `/v3/api-docs` in apps). After endpoint changes run [`scripts/generate-openapi.sh`](../scripts/generate-openapi.sh) (**Docker-only**; temporary JDK container) and commit the regenerated files.

## Checklist for the next agent

All six HIFL stages are **APPROVED** and every ticket is `DONE-`. Nothing is in flight. What remains is housekeeping:

- [ ] Merge `fix/17-openapi-error-responses` to `main` — the branch is committed but **not** merged. Ask the owner before opening the PR.
- [ ] v1 is verified; there is no next stage. Any further work starts a **new** HIFL cycle at Stage 1 Intent, or arrives as a defect via [bugs.md](bugs.md).

## Steps (detail) — v1 complete

### A. Before anything else

1. **Merge the ticket-17 branch.** It carries the BUG-02 fix, the regenerated `docs/openapi/*`, and the Verify-approval doc updates. Until it lands, `main` still publishes the incomplete contract.
2. **Ask before any commit or push** (owner preference 1). It applies to docs-only edits too.

### B. Traps worth knowing before touching the OpenAPI export

1. Swagger's `removeBrokenReferenceDefinitions` prunes unreferenced component schemas **before** `OpenApiCustomizer`s run, so a schema registered on the `OpenAPI` bean but referenced only from a customiser ends up dangling. `ApiEnvelope` therefore lives inside each customiser; `Pagination` stays on the bean because SpringDoc's generated schemas reference it. The first regeneration on the ticket-17 branch shipped 10 dangling refs exactly this way, and the first version of the new guard did not catch it.
2. `StaticOpenApiContractTest` in each service reads the **committed** `docs/openapi/*` files, not the exporter's in-memory output. If you change an endpoint and forget `./scripts/generate-openapi.sh`, the build fails — that is deliberate.
3. SpringDoc is **test-scoped**. Do not reach for `io.swagger` annotations on controllers; that would put it on the runtime classpath and break the static-files-only rule.

### C. Running the live residual pack again

1. Use `APP_JWT_TTL=1m` / `APP_PDF_INTERVAL=1m` / `APP_CACHE_CATALOG_TTL=30s` so time-dependent behaviour is observable within a run.
2. Under a 1-minute TTL, mint a **fresh JWT per authenticated call** — a token reused across a multi-minute run silently starts returning 401 and the failure surfaces as a confusing assertion elsewhere.
3. Spring's default `JwtTimestampValidator` allows **60s** clock skew, so a token stays accepted past `exp`; wait beyond that before asserting expiry.

### D. Accepted residual (not scheduled)

1. `auth-service.{yaml,json}` declares a `bearerAdminJwt` security scheme that no operation references, so importers get no Authorize affordance on admin routes. Accepted by the owner at the Verify gate; see [verify.md](verify.md) §10.

## Locked product highlights (do not rediscover)

- Product types: Simple, Combo, Pizza (`product_type` `simple`/`combo`/`pizza`); consumer `simple` / `combo` / `pizza-base` / `pizza-spec`
- Veg/non-veg on all three; combo price admin-set; pizza options **shared**, **pizzas only**, **`optionsEnabled`**; PDF note **options available** + options in **own space**
- Public PDF: header **Create Your Pizza**, **vN**, name+base price; GET **raw binary**; default latest; `?version=` history; Redis **latest only**
- Auth: admin **login** vs trusted **`/auth/register` only**; cannot **DELETE self**; paginated `/auth/users`; JWKS HTTP
- Redis locks: PDF **120s**, write **30s** (`finally` + expiry); writes 503; job **skips not queued**; version **only on generate**
- Config MUST: PDF interval (default 5m), catalog TTL (default 3m), JWT TTL (default 30m), lock TTLs 120s / 30s
- Stories under `docs/stories/` before coding (playbook)
- Full Dockerize; OpenAPI/Swagger (**static** `docs/openapi/*.yaml|json` only); tests (mock ports); AGENTS.md at Build
- DB Compose names: **`auth-db`**, **`catalog-db`** (engine still PostgreSQL in v1)
- Maven: `com.createyourpizza` / `auth-service` + `catalog-service`
- §5.1: Auth service-ready smoke on **06**; Catalog read-path smoke on **10**; **08** after **06** `DONE-`; **11** after **10** `DONE-`
- Static OpenAPI: committed `docs/openapi/*`; regenerate via `./scripts/generate-openapi.sh` (Docker-only; no live Swagger)

## What NOT to do

- Do not skip the §6 story graph
- Do not mix two stories on one branch; use `feat/<story-id>-<max-5-word-summary>`
- Do not git-commit unless the owner confirms
- Do not blank-rewrite this handoff
- Do not add Cursor rules for HIFL handoff
- Do not skip the candid review loop, and do not pass the author’s plan or this handoff’s “how we built it” notes to the reviewer
- Do not write the ephemeral review handoff into the repo
- Do not treat “Findings: none” as owner **Approve**
- Do not reinvent Spring, Hibernate, Lombok, or approved-library features as hand-written boilerplate when a dependency or annotation already does the job
- Do not call **`/auth/validate`** per catalog request — JWKS + local verify
- Do not share one Postgres across auth and catalog
- Do not start a coding story without its markdown file (rename to `DONE-` when finished)
- Do not put API secrets in JWT claims
- Do not give trusted systems a catalog API-key handler — JWT only after `/auth/token`
- Do not add a parent POM
- Do not skip §5.1 Auth service-ready smoke on **06** or Catalog read-path smoke on **10**
- Do not start **08** before **06** `DONE-`, or **11** before **10** `DONE-`
- Do not change API endpoints without regenerating and committing `docs/openapi/*` (`./scripts/generate-openapi.sh`)
- Do not add runtime SpringDoc / Swagger UI / live `/v3/api-docs` — static OpenAPI files are the only contract (test-scoped export only)