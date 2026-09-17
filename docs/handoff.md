# CreateYourPizza — Agentic SDLC handoff

**Audience:** Next agent continuing HIFL after Spec Approve.  
**Owner:** Aidev Tool10  
**As of:** 2026-09-17  
**Repo root:** local `create-your-pizza` (all links below are relative to that root)

**How to resume:** read [docs/hifl-playbook.md](hifl-playbook.md) → this file (past memory + next checklist) → open linked APPROVED artifacts only as needed. After every stage **Approve**: **compress** completed stages here, then refresh next-stage handoff items — do **not** wipe and fully rewrite. Living resume memory — not Cursor rules / chat.

## Current state

| Stage | Artifact | Status |
|-------|----------|--------|
| 1 Intent | [docs/intent.md](docs/intent.md) | **APPROVED** |
| 2 Spec / PRD | [docs/spec.md](docs/spec.md) | **APPROVED** (revise c locked) |
| 3 Design / TRD | [docs/design.md](docs/design.md) | **NOT STARTED — ON HOLD** until owner says start |
| 4 Build plan | [docs/build-plan.md](docs/build-plan.md) | Not started (after Design Approve) |
| 5 Build | Spring Boot code in this repo | Not started (after Build-plan Approve) |
| 6 Verify | [docs/verify.md](docs/verify.md) | Not started (after Build) |

Process: [docs/hifl-playbook.md](docs/hifl-playbook.md) · Decisions: [docs/project-context.md](docs/project-context.md) · Docs index: [docs/README.md](docs/README.md)

## Past stages (compressed)

### 1 Intent — APPROVED 2026-09-17

- Problem: single catalog for Simple / Combo / Pizza; public PDF; trusted JWT APIs; no orders/payments/delivery in v1.
- Actors: Admin, public PDF consumer, trusted system; future CUSTOMER provisioned only.
- Stack locked: Java Spring Boot + Maven; owner Initializr; deps at Build; full Dockerize.
- Outcomes: veg/non-veg on all types; combo admin-set price; option entities; PDF dirty/5-min + concurrency; local JWT verify; envelope + pagination; OpenAPI/tests/AGENTS.md.
- Detail: [docs/intent.md](docs/intent.md)

### 2 Spec / PRD — APPROVED 2026-09-17 (revise c)

- FRs/NFRs + acceptance criteria for catalog, option entities, queries, PDF, auth, Docker/OpenAPI/tests.
- Binding: JWT claims/scopes; DB-only public keys (no JWKS refresh); status-table lock + 503 envelope; PDF version+bytea + Redis `create-your-pizza/menu`; GET PDF raw binary; flat `data` + `pagination` sibling; page size 10; types `simple`/`combo`/`pizza-base`/`pizza-spec`.
- Design on hold until owner says go. Detail: [docs/spec.md](docs/spec.md) · log: [docs/project-context.md](docs/project-context.md)

## Owner preferences (must follow)

1. **Design/TRD:** Do **not** start until owner explicitly says to start (e.g. “start Design” / “draft TRD”).
2. **Git commits:** Do **not** commit unless the owner **confirms**. After changes, **ask** whether to include a git commit. Preferred branch when committing docs: **`cursor/sync-spec-prd-revise-efa1`** only — no new branches for doc syncs.
3. **Stage-end handoff:** On every stage **Approve**, **compress** that stage into **Past stages** above and refresh next-agent sections — do not blank-rewrite this file (see playbook).
4. **Stack:** Java Spring Boot + Maven; owner Initializr; Initializr deps suggested at **Build** only.
5. **Gate language:** Approve / Revise: … / Park — no silent skips.

## Checklist for the next agent

- [ ] Read this handoff + playbook; skim APPROVED Intent/Spec only if needed beyond **Past stages**
- [ ] **Wait** for owner to say start Design (do not draft TRD until then)
- [ ] Draft [docs/design.md](docs/design.md) (TRD) from APPROVED Intent + Spec; status DRAFT; Design gate
- [ ] **Ask** owner whether to git-commit the Design draft (branch `cursor/sync-spec-prd-revise-efa1` if yes)
- [ ] After Design **Approve**: mark design APPROVED; **compress Design into Past stages** + refresh this handoff for Build-plan; ask about commit; then draft [docs/build-plan.md](docs/build-plan.md) (incl. Initializr deps); Build-plan gate
- [ ] After Build-plan **Approve**: compress Build-plan into Past stages; refresh handoff for Build; ask about commit; owner Initializr → implement slice
- [ ] After Build ready: draft [docs/verify.md](docs/verify.md); Verify gate; on Approve compress Build/Verify memory + closeout notes; ask about commit

## Steps (detail) — next stage focus: Design

### A. When owner says start Design

1. Use **Past stages** + APPROVED Intent/Spec/project-context — do not reopen locked product decisions.
2. Draft [docs/design.md](docs/design.md) (TRD): architecture (auth-service + catalog-service), data model, JWT/key store (DB-only public keys), APIs/envelope/pagination, PDF job + status-table lock, Redis cache, Docker Compose, OpenAPI/tests notes, engineering open questions only.
3. Status: DRAFT — Design gate (Approve / Revise / Park).
4. **Ask** before any commit on `cursor/sync-spec-prd-revise-efa1`.

### B. After Design Approve

1. Mark [docs/design.md](docs/design.md) APPROVED.
2. **Compress Design** into **Past stages**; refresh checklist/steps for Build plan.
3. Draft [docs/build-plan.md](docs/build-plan.md) (slices, tests, Initializr deps, Docker/AGENTS.md).
4. Build-plan gate. **Ask** before commit.

### C. After Build-plan Approve

1. Compress Build-plan into Past stages; refresh handoff for Build.
2. Owner Initializr → implement approved slice; ask before commits.

### D. After Build ready for Verify

1. Draft [docs/verify.md](docs/verify.md); Verify gate.
2. On Approve: compress into Past stages / closeout; **ask** before commit.

## Locked product highlights (do not rediscover)

- Product types: Simple, Combo, Pizza; consumer types `simple` / `combo` / `pizza-base` / `pizza-spec`
- Veg/non-veg on all three; combo price admin-set; pizza option **entities**
- Public PDF: header **Create Your Pizza**, name+base price; GET **raw binary**; version+bytea + Redis `create-your-pizza/menu`; 5‑min dirty job; status-table → 503
- Auth: central extensible auth; JWT 30m; local verify; **DB-only** public keys; scopes `catalog:read` / `catalog:write` / `menu:read`
- JSON envelope + `pagination` sibling; flat `data`; page size 10
- Full Dockerize; OpenAPI/Swagger; tests; AGENTS.md at Build

## What NOT to do

- Do not draft Design until owner says so
- Do not scaffold Spring Boot before Design + Build-plan Approve
- Do not git-commit unless the owner confirms
- Do not create new git branches for doc syncs
- Do not blank-rewrite this handoff — compress past stages, then update next-stage items
- Do not add Cursor rules for HIFL handoff
- Do not call auth service per-request to validate JWTs
- Do not put API secrets in JWT claims
