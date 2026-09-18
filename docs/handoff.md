# CreateYourPizza — Agentic SDLC handoff

**Audience:** Next agent continuing HIFL — Design DRAFT (revised).  
**Owner:** PereRohit
**As of:** 2026-09-18  
**Repo root:** local `create-your-pizza`  
**Git branch until Design Approve:** **`feat/design`**

**How to resume:** read [docs/hifl-playbook.md](hifl-playbook.md) → this file → open linked artifacts as needed. After every stage **Approve**: **compress** completed stages here, then refresh next-stage items — do **not** wipe and fully rewrite.

## Current state

| Stage | Artifact | Status |
|-------|----------|--------|
| 1 Intent | [docs/intent.md](docs/intent.md) | **APPROVED** (aligned 2026-09-18) |
| 2 Spec / PRD | [docs/spec.md](docs/spec.md) | **APPROVED** (aligned 2026-09-18 to Design Revise) |
| 3 Design / TRD | [docs/design.md](docs/design.md) | **DRAFT — revised 2026-09-18**; awaiting **Approve / Revise / Park** |
| 4 Build plan | [docs/build-plan.md](docs/build-plan.md) | Not started (after Design Approve) |
| 5 Build | Spring Boot code in this repo | Not started (after Build-plan Approve) |
| 6 Verify | [docs/verify.md](docs/verify.md) | Not started (after Build) |

Process: [docs/hifl-playbook.md](docs/hifl-playbook.md) · Decisions: [docs/project-context.md](docs/project-context.md) · Docs index: [docs/README.md](docs/README.md)

## Past stages (compressed)

### 1 Intent — APPROVED 2026-09-17

- Problem: single catalog for Simple / Combo / Pizza; public PDF; trusted JWT APIs; no orders/payments/delivery in v1.
- Actors: Admin, public PDF consumer, trusted system; future CUSTOMER provisioned only.
- Stack locked: Java Spring Boot + Maven; owner Initializr; deps at Build; full Dockerize.
- Detail: [docs/intent.md](docs/intent.md)

### 2 Spec / PRD — APPROVED 2026-09-17 (revise c); **aligned 2026-09-18**

- FRs/NFRs + acceptance for catalog, option entities, queries, PDF, auth, Docker/OpenAPI/tests.
- Binding: JWT claims/scopes; DB-only public keys; envelope + pagination; page size 10; types `simple`/`combo`/`pizza-base`/`pizza-spec`.
- **2026-09-18 alignment:** trusted pending+approve+token; bootstrap admin; PDF history + `?version=`; pizza-spec on PDF and list API; `pdf_generation` only; catalog Redis TTL 3 min.
- Detail: [docs/spec.md](docs/spec.md) · log: [docs/project-context.md](docs/project-context.md)

## Owner preferences (must follow)

1. **Design/TRD:** Stay on **`feat/design`** until Design **Approve**. Then follow owner git instructions.
2. **Git commits:** Do **not** commit unless the owner **confirms**. After changes, **ask**.
3. **Stage-end handoff:** On every stage **Approve**, **compress** past stages and refresh next-agent sections — do not blank-rewrite.
4. **Stack:** Java Spring Boot + Maven; owner Initializr; Initializr deps suggested at **Build** only.
5. **Gate language:** Approve / Revise: … / Park — no silent skips.

## Checklist for the next agent

- [x] Read playbook + handoff; Design started
- [x] Draft / revise [docs/design.md](docs/design.md); Design gate
- [ ] Owner **Approve / Revise / Park** Design
- [ ] **Ask** whether to git-commit this Revise on `feat/design`
- [ ] After Design **Approve**: mark design APPROVED; **compress Design into Past stages** + refresh for Build-plan; ask about commit; then draft [docs/build-plan.md](docs/build-plan.md)
- [ ] After Build-plan **Approve**: compress; owner Initializr → implement slice
- [ ] After Build ready: draft [docs/verify.md](docs/verify.md)

## Steps (detail) — next stage focus: Design gate

### A. Now (Design DRAFT revised)

1. Owner reviews [docs/design.md](docs/design.md).
2. Gate: Approve / Revise / Park.
3. Ask before commit on **`feat/design`**.

### B. After Design Approve

1. Mark [docs/design.md](docs/design.md) APPROVED.
2. **Compress Design** into **Past stages**; refresh for Build plan.
3. Draft [docs/build-plan.md](docs/build-plan.md).
4. Build-plan gate. **Ask** before commit.

### C. After Build-plan Approve

1. Compress Build-plan; refresh for Build.
2. Owner Initializr → implement approved slice.

### D. After Build ready for Verify

1. Draft [docs/verify.md](docs/verify.md); Verify gate.

## Locked product highlights (do not rediscover)

- Product types: Simple, Combo, Pizza (`product_type` `simple`/`combo`/`pizza`); consumer `simple` / `combo` / `pizza-base` / `pizza-spec`
- Veg/non-veg on all three; combo price admin-set; pizza option **entities** on **API and PDF**
- Public PDF: header **Create Your Pizza**, **vN**, name+base price; GET **raw binary**; default latest; `?version=` history; Redis **latest only**
- Auth: admin **login**; trusted **pending → approve → `/auth/token`**; bootstrap first admin; JWT 30m; local verify; **DB-only** public keys; credential **revoke** (no JWT denylist)
- Same catalog **GET** for admin JWT and trusted JWT; writes admin-only
- Envelope + `pagination` sibling; flat `data`; page size 10 (max 100)
- Lock: **`pdf_generation` only** → 503; catalog Redis **TTL 3 min** (no invalidation-on-write)
- Full Dockerize; OpenAPI/Swagger; tests; AGENTS.md at Build

## What NOT to do

- Do not start Build-plan or Spring Boot until Design **Approve**
- Do not git-commit unless the owner confirms
- Do not leave `feat/design` for Design work until Approve (unless owner says otherwise)
- Do not blank-rewrite this handoff
- Do not add Cursor rules for HIFL handoff
- Do not call auth service per-request to validate JWTs
- Do not put API secrets in JWT claims
- Do not give trusted systems a catalog API-key handler — JWT only after `/auth/token`
