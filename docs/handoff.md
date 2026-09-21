# CreateYourPizza — Agentic SDLC handoff

**Audience:** Next agent continuing HIFL — Build plan **APPROVED**; **Build** in progress.  
**Owner:** PereRohit  
**As of:** 2026-09-21 (Build story 03 done)  
**Repo root:** local `create-your-pizza`  
**Git:** work on **`feat/03-auth-schema-bootstrap`**; **ask** before commit.

**How to resume:** read [docs/hifl-playbook.md](hifl-playbook.md) → this file → open linked artifacts as needed. After every stage **Approve**: **compress** completed stages here, then refresh next-stage items — do **not** wipe and fully rewrite.

## Current state

| Stage | Artifact | Status |
|-------|----------|--------|
| 1 Intent | [docs/intent.md](docs/intent.md) | **APPROVED** (aligned 2026-09-18) |
| 2 Spec / PRD | [docs/spec.md](docs/spec.md) | **APPROVED** (aligned 2026-09-18) |
| 3 Design / TRD | [docs/design.md](docs/design.md) | **APPROVED** 2026-09-18 |
| 4 Build plan | [docs/build-plan.md](docs/build-plan.md) | **APPROVED** 2026-09-18 |
| 5 Build | Spring Boot + Compose in this repo | **In progress** — `DONE-01`–`DONE-03`; next **04** (needs 03) or **07** (independent) |
| 6 Verify | [docs/verify.md](docs/verify.md) | Not started (after Build) |

Process: [docs/hifl-playbook.md](hifl-playbook.md) · Decisions: [docs/project-context.md](docs/project-context.md) · Docs index: [docs/README.md](docs/README.md)

## Past stages (compressed)

### 1 Intent — APPROVED 2026-09-17

- Problem: single catalog for Simple / Combo / Pizza; public PDF; trusted JWT APIs; no orders/payments/delivery in v1.
- Actors: Admin, public PDF consumer, trusted system; future CUSTOMER provisioned only.
- Stack locked: Java Spring Boot + Maven; owner Initializr; deps at Build; full Dockerize.
- Detail: [docs/intent.md](docs/intent.md)

### 2 Spec / PRD — APPROVED 2026-09-17 (revise c); **aligned 2026-09-18**

- FRs/NFRs + acceptance for catalog, option entities, queries, PDF, auth, Docker/OpenAPI/tests.
- Binding: JWT claims/scopes; envelope + pagination; page size 10 max 100; types `simple`/`combo`/`pizza-base`/`pizza-spec`.
- **2026-09-18 alignment:** trusted pending+approve+token; bootstrap admin; PDF history + `?version=`; pizza-spec on PDF and list API; Redis locks; one DB per service; JWKS.
- Detail: [docs/spec.md](docs/spec.md) · log: [docs/project-context.md](docs/project-context.md)

### 3 Design / TRD — APPROVED 2026-09-18

- Two services: **auth-service** + **catalog-service**; **one Postgres each** (Compose/DB names **`auth-db`**, **`catalog-db`**); catalog Redis (cache, latest PDF, locks); Compose those five.
- Auth: admin login vs trusted `POST /auth/register` → PENDING → approve (API key+secret once) → `POST /auth/token`; bootstrap first admin; **cannot DELETE self**; paginated `/auth/users`; principal type from **URL**; JWKS `GET /auth/.well-known/jwks.json`; **no** `/validate`; **no** catalog reading auth DB.
- Catalog: `product_type` simple|combo|pizza; **option_entities** pizza-only; shared catalog; pizza **`optionsEnabled`**; combo price admin-set; veg/non-veg all three.
- PDF: header Create Your Pizza; vN; sellable name+price; pizza with options: note **options available**; pizza-spec in **own space**; history in catalog DB; Redis latest only; GET raw PDF; `?version=` history; version **only on successful generate**.
- Locks: Redis `create-your-pizza/lock:pdf-generation` TTL **120s**, `create-your-pizza/lock:catalog-write` TTL **30s**; `finally` DEL + expiry; writes during PDF lock → 503 + Retry-After 60; job **skips not queued** if write lock; dirty stays true.
- Catalog Redis `create-your-pizza/catalog:*` TTL 3m Redis-first; no invalidation-on-write. Menu key `create-your-pizza/menu`.
- Config MUST: `app.pdf.interval` 5m, `app.cache.catalog-ttl` 3m, `app.jwt.ttl` 30m, lock TTLs. Test `POST /test/pdf/generate` test profile only.
- Stories: `docs/stories/{priority}-{slug}.md` after Build-plan Approve; coding stories >80% LoC + full behaviour tests.
- Detail: [docs/design.md](docs/design.md)

### 4 Build plan — APPROVED 2026-09-18

- Stack: Spring Boot **4.1.1**, Maven JAR, Java **26** (Initializr **25** then pin POM), `.properties`, Lombok both apps.
- Independent siblings: `groupId` **`com.createyourpizza`**; artifacts **`auth-service`**, **`catalog-service`**; packages `com.createyourpizza.auth` / `com.createyourpizza.catalog`.
- Catalog: OAuth2 Resource Server + JWKS URL. Tests: mock ports. One root Compose. Graph of stories **01–15** (enabler **02** = owner Initializr).
- Detail: [docs/build-plan.md](docs/build-plan.md) §6

## Owner preferences (must follow)

1. **Git commits:** Do **not** commit unless the owner **confirms**. After changes, **ask**.
2. **Stage-end handoff:** On every stage **Approve**, **compress** past stages and refresh next-agent sections — do not blank-rewrite.
3. **Stack:** Java Spring Boot **4.1.1** + Maven + JAR; Java **26** (Initializr **25** then pin POM); independent siblings `auth-service` / `catalog-service`; Lombok; properties files; owner Initializr. Prefer Spring / Hibernate / Lombok / JDK and Build-plan libraries over hand-rolled boilerplate.
4. **Gate language:** Approve / Revise: … / Park — no silent skips.
5. **Build:** one story at a time; follow [build-plan.md](build-plan.md) §6 graph; rename to `DONE-` when finished.
6. **Story git branches:** `feat/<story-id>-<max-5-word-summary>` (example `feat/01-compose-and-config`); **only** that story’s changes on the branch; still ask before commit.
7. **Owner stories:** filename `{id}-OWNER-{slug}.md` when the owner must act; task lines prefixed **`Owner:`**.
8. **Candid review loop** ([playbook](hifl-playbook.md#candid-review-loop)): before every stage gate and before a story is renamed `DONE-`. Fresh reviewer, then fresh fix agent. Ephemeral handoff is prompt-only and must not enter this file. Later stages and later stories are not findings. The loop does not replace Approve / Revise / Park.
9. **No reinventing the wheel:** Prefer Spring Boot / Spring Security / Spring Data / Hibernate / Lombok / JDK APIs and approved libraries (Nimbus, OpenPDF, SpringDoc per Build plan) over hand-rolled equivalents. Prefer annotations and framework injection over boilerplate constructors, getters, timestamp/id callbacks, and custom wrappers when the framework already provides them. Do not invent a utility or abstraction that duplicates a library or Spring feature.

## Checklist for the next agent

- [x] Build plan **APPROVED**; stories `01`–`15` under `docs/stories/`
- [x] Implement **01** on `feat/01-compose-and-config` — [`DONE-01-compose-config.md`](stories/DONE-01-compose-config.md)
- [x] Owner **02** Initializr on `feat/02-maven-initializr` — [`DONE-02-OWNER-maven-initializr.md`](stories/DONE-02-OWNER-maven-initializr.md)
- [x] Implement **03** on `feat/03-auth-schema-bootstrap` — [`DONE-03-auth-schema-bootstrap.md`](stories/DONE-03-auth-schema-bootstrap.md)
- **Picked stories (Build):** none in progress — **next** [`04-auth-jwks-jwt.md`](stories/04-auth-jwks-jwt.md) (after 03) or [`07-catalog-schema-seed.md`](stories/07-catalog-schema-seed.md) (independent)
- [ ] After all stories `DONE-`: draft [docs/verify.md](verify.md)

## Steps (detail) — next stage focus: Build

### A. Now

1. **01**, **02**, and **03** are `DONE-`. Auth Flyway schema + first-admin bootstrap exist on `feat/03-auth-schema-bootstrap` (ask before commit).
2. **Ask** before git commit.
3. Next: pick **04** (auth JWKS/JWT, needs 03) or **07** (catalog schema) on its own `feat/…` branch.

### B. After picking 04 or 07

1. **04→05→06** on auth; **07** may proceed in parallel with auth; **08** after **04** and **07**; never start **09** before **08**.

### C. After Build ready for Verify

1. Draft [docs/verify.md](verify.md); Verify gate.

## Locked product highlights (do not rediscover)

- Product types: Simple, Combo, Pizza (`product_type` `simple`/`combo`/`pizza`); consumer `simple` / `combo` / `pizza-base` / `pizza-spec`
- Veg/non-veg on all three; combo price admin-set; pizza options **shared**, **pizzas only**, **`optionsEnabled`**; PDF note **options available** + options in **own space**
- Public PDF: header **Create Your Pizza**, **vN**, name+base price; GET **raw binary**; default latest; `?version=` history; Redis **latest only**
- Auth: admin **login** vs trusted **`/auth/register` only**; cannot **DELETE self**; paginated `/auth/users`; JWKS HTTP
- Redis locks: PDF **120s**, write **30s** (`finally` + expiry); writes 503; job **skips not queued**; version **only on generate**
- Config MUST: PDF interval (default 5m), catalog TTL (default 3m), JWT TTL (default 30m), lock TTLs 120s / 30s
- Stories under `docs/stories/` before coding (playbook)
- Full Dockerize; OpenAPI/Swagger; tests (mock ports); AGENTS.md at Build
- DB Compose names: **`auth-db`**, **`catalog-db`** (engine still PostgreSQL in v1)
- Maven: `com.createyourpizza` / `auth-service` + `catalog-service`

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
