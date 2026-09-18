# CreateYourPizza — Agentic SDLC handoff

**Audience:** Next agent continuing HIFL — Design **APPROVED**; Build plan **DRAFT** (owner started 2026-09-18).  
**Owner:** PereRohit  
**As of:** 2026-09-18 (Build-plan draft)  
**Repo root:** local `create-your-pizza`  
**Git:** last Design work on **`feat/design`**; owner said **do not commit** this Build-plan start.

**How to resume:** read [docs/hifl-playbook.md](hifl-playbook.md) → this file → open linked artifacts as needed. After every stage **Approve**: **compress** completed stages here, then refresh next-stage items — do **not** wipe and fully rewrite.

## Current state

| Stage | Artifact | Status |
|-------|----------|--------|
| 1 Intent | [docs/intent.md](docs/intent.md) | **APPROVED** (aligned 2026-09-18) |
| 2 Spec / PRD | [docs/spec.md](docs/spec.md) | **APPROVED** (aligned 2026-09-18) |
| 3 Design / TRD | [docs/design.md](docs/design.md) | **APPROVED** 2026-09-18 |
| 4 Build plan | [docs/build-plan.md](docs/build-plan.md) | **DRAFT** 2026-09-18 — awaiting Approve / Revise / Park |
| 5 Build | Spring Boot code in this repo | Not started (after Build-plan Approve) |
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

- Two services: **auth-service** + **catalog-service**; **one Postgres each**; catalog Redis (cache, latest PDF, locks); Compose those five.
- Auth: admin login vs trusted `POST /auth/register` → PENDING → approve (API key+secret once) → `POST /auth/token`; bootstrap first admin; **cannot DELETE self**; paginated `/auth/users`; principal type from **URL**; JWKS `GET /auth/.well-known/jwks.json`; **no** `/validate`; **no** catalog reading auth DB.
- Catalog: `product_type` simple|combo|pizza; **option_entities** pizza-only; shared catalog; pizza **`optionsEnabled`**; combo price admin-set; veg/non-veg all three.
- PDF: header Create Your Pizza; vN; sellable name+price; pizza with options: note **options available**; pizza-spec in **own space**; history in catalog DB; Redis latest only; GET raw PDF; `?version=` history; version **only on successful generate**.
- Locks: Redis `create-your-pizza/lock:pdf-generation` TTL **120s**, `create-your-pizza/lock:catalog-write` TTL **30s**; `finally` DEL + expiry; writes during PDF lock → 503 + Retry-After 60; job **skips not queued** if write lock; dirty stays true.
- Catalog Redis `create-your-pizza/catalog:*` TTL 3m Redis-first; no invalidation-on-write. Menu key `create-your-pizza/menu`.
- Config MUST: `app.pdf.interval` 5m, `app.cache.catalog-ttl` 3m, `app.jwt.ttl` 30m, lock TTLs. Test `POST /test/pdf/generate` test profile only.
- Stories: `docs/stories/{priority}-{slug}.md` after Build-plan Approve; coding stories >80% LoC + full behaviour tests.
- Detail: [docs/design.md](docs/design.md)

## Owner preferences (must follow)

1. **Git commits:** Do **not** commit unless the owner **confirms**. After changes, **ask**.
2. **Stage-end handoff:** On every stage **Approve**, **compress** past stages and refresh next-agent sections — do not blank-rewrite.
3. **Stack:** Java Spring Boot + Maven; owner Initializr; Initializr deps suggested at **Build** only.
4. **Gate language:** Approve / Revise: … / Park — no silent skips.
5. **Build plan:** owner started 2026-09-18; **do not** write `docs/stories/*.md` or code until Build-plan **Approve**.

## Checklist for the next agent

- [x] Design drafted, revised, and **APPROVED**
- [x] Owner said **start** Build plan; [docs/build-plan.md](docs/build-plan.md) **DRAFT**
- [x] Owner said **do not git-commit** this Build-plan start
- [ ] Build-plan gate: Approve / Revise / Park
- **Picked stories (Build):** none yet — after Build-plan Approve, create `docs/stories/*.md`; record in-progress filenames here; rename to `DONE-` when finished
- [ ] After Build-plan **Approve**: compress; write stories; owner Initializr → implement slice
- [ ] After Build ready: draft [docs/verify.md](docs/verify.md)

## Steps (detail) — next stage focus: Build-plan gate

### A. Now (Build-plan DRAFT)

1. Owner reviews [docs/build-plan.md](docs/build-plan.md).
2. Do **not** write story files or application code until **Approve**.
3. Do **not** git-commit unless the owner confirms (this start: no commit).

### B. After Build-plan Approve

1. Compress Build-plan into Past stages; refresh for Build.
2. Write ordered stories under `docs/stories/` (see [build-plan.md](build-plan.md) §4); record the picked file(s) in this handoff.
3. Owner Initializr → implement **one story at a time** with tests.

### C. After Build ready for Verify

1. Draft [docs/verify.md](docs/verify.md); Verify gate.

## Locked product highlights (do not rediscover)

- Product types: Simple, Combo, Pizza (`product_type` `simple`/`combo`/`pizza`); consumer `simple` / `combo` / `pizza-base` / `pizza-spec`
- Veg/non-veg on all three; combo price admin-set; pizza options **shared**, **pizzas only**, **`optionsEnabled`**; PDF note **options available** + options in **own space**
- Public PDF: header **Create Your Pizza**, **vN**, name+base price; GET **raw binary**; default latest; `?version=` history; Redis **latest only**
- Auth: admin **login** vs trusted **`/auth/register` only**; cannot **DELETE self**; paginated `/auth/users`; JWKS HTTP
- Redis locks: PDF **120s**, write **30s** (`finally` + expiry); writes 503; job **skips not queued**; version **only on generate**
- Config MUST: PDF interval (default 5m), catalog TTL (default 3m), JWT TTL (default 30m), lock TTLs 120s / 30s
- Stories under `docs/stories/` before coding (playbook)
- Full Dockerize; OpenAPI/Swagger; tests; AGENTS.md at Build

## What NOT to do

- Do **not** write `docs/stories/0*.md` or application code until Build-plan **Approve**
- Do not git-commit unless the owner confirms
- Do not blank-rewrite this handoff
- Do not add Cursor rules for HIFL handoff
- Do not call **`/auth/validate`** per catalog request — JWKS + local verify
- Do not share one Postgres across auth and catalog
- Do not start coding without `docs/stories/` files (after Build-plan Approve)
- Do not put API secrets in JWT claims
- Do not give trusted systems a catalog API-key handler — JWT only after `/auth/token`
