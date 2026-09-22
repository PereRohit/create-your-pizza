# Verify — CreateYourPizza

**Status:** DRAFT — 2026-09-22. Awaiting owner HIFL gate (**Approve** / **Revise** / **Park**). Defect table in **§8**: **BUG-01 closed**, **BUG-02 open** (docs contract, no runtime impact).

**Upstream:** [Intent](intent.md) (**APPROVED**) · [Spec / PRD](spec.md) (**APPROVED**) · [Design / TRD](design.md) (**APPROVED**) · [Build plan](build-plan.md) (**APPROVED**, §5.1 Revise **APPROVED**) · Build stories **01–15** `DONE-` on **`main`**

**Related:** [HIFL playbook](hifl-playbook.md) · [Handoff](handoff.md) · [Project context](project-context.md)

**This stage:** evidence that the built system meets Spec §8 acceptance and the residual pack (Design §9, Build plan §8–§9). Not new product scope.

---

## 1. Environment

| Item | Value |
|------|--------|
| Git | `main` @ `6c8a895` (PR #24 `feat/15-openapi-agents` merged); working tree clean at start |
| Host | macOS; JDK **26.0.2.1**; Maven **3.9.16** |
| Mocked tests | `mvn -f auth-service test`; `mvn -f catalog-service test` (H2 + fakes; no Docker) |
| Live stack | `docker compose down -v && docker compose up -d --build` from repo root (fresh volumes) |
| Ports | auth **8080**, catalog **8081**, `auth-db` **5432**, `catalog-db` **5433**, Redis **6379** |
| Live config (first pass) | committed defaults (`app.pdf.interval=5m`, `app.cache.catalog-ttl=3m`, `app.jwt.ttl=30m`). First PDF after **initialDelay = interval** (~5 min). |
| Live config (TTL follow-up) | Compose override only (not committed): `APP_JWT_TTL=1m`, `APP_PDF_INTERVAL=1m`, `APP_CACHE_CATALOG_TTL=30s`. Fresh `down -v` then `up`. First PDF ~**58s**. JWT `expiresIn=60`. |
| Diagnostic run (RCA, BUG-01) | `catalog-service` recreated with `DEBUG=true` to capture Spring Boot's condition evaluation report; reverted afterwards. Override only, not committed. |
| **Full regression run (BUG-01 closure, 2026-09-22)** | Branch `fix/16-redis-bean-wiring`, merged to `main` as **PR #25** (`b34614d`). Both suites re-run (auth **62**, catalog **115**, 1 skipped each, BUILD SUCCESS) **and** the entire residual pack re-run on a fresh stack: `docker compose down -v --remove-orphans` then `up -d --build` with the TTL overrides. Ready in **9s**; first PDF at ~**58s**. |

Compose services up: `auth-db`, `catalog-db`, `redis`, `auth-service`, `catalog-service`.

---

## 2. Mocked automated tests

| Suite | Result (2026-09-22) |
|-------|---------------------|
| `mvn -f auth-service test` | **BUILD SUCCESS** — Tests run: **62**, Failures: **0**, Errors: **0**, Skipped: **1** |
| `mvn -f catalog-service test` | **BUILD SUCCESS** — Tests run: **115**, Failures: **0**, Errors: **0**, Skipped: **1** (was 106; BUG-01's fix adds 9 wiring/lock tests) |

The skipped tests are Docker-only OpenAPI exporters (`-Dopenapi.export=true` via `scripts/generate-openapi.sh`), not runtime product tests.

These suites cover (via H2 / fakes / local JWKS stub): bootstrap admin; trusted pending → approve (secret once) / deny / revoke; login vs token JWTs; cannot DELETE self; paginated `/auth/users`; catalog CRUD + dirty without PDF version bump; trusted write **403**; PDF lock → **503** + `Retry-After: 60` busy envelope; job skip when write lock held; PDF content model; public GET PDF Redis-first / DB fallback / `?version=` 404; Resource Server JWKS (no `/auth/validate`); catalog cache TTL + no invalidation-on-write; test PDF trigger on `test`/`dev` only.

---

## 3. Live Compose residual pack (2026-09-22)

Hot-path and edge checks against the running stack. Unless noted, calls used `curl` + `jq` on published ports.

### 3.1 Auth

| Check | Result |
|-------|--------|
| Bootstrap admin printed once in `auth-service` logs (`BOOTSTRAP ADMIN username=… password=…`) | **PASS** (`admin-ea079a16` on first pass; `admin-ef0eebca` on TTL follow-up) |
| `POST /auth/login` → JWT; claims `iss=create-your-pizza-auth`, `aud` includes `create-your-pizza-catalog`, `roles=ADMIN`, scope includes `catalog:write` | **PASS** |
| `POST /auth/register` → **201** `PENDING` | **PASS** |
| `POST /auth/token` with bogus key/secret before approve | **401** **PASS** |
| Approve → `apiKey` + `apiSecret` once; second approve **400**; secret **not** in trusted JWT; `client_id` = api key; `roles=TRUSTED_SYSTEM` | **PASS** |
| Deny pending → `DENIED`, no `apiKey` | **PASS** |
| Revoke approved partner → further `/auth/token` **401** | **PASS** |
| `GET /auth/users` envelope + `pagination` sibling; `size=1000` returned **4** (≤ 100) | **PASS** (full max-100 clamp with >100 users is mocked, not live) |
| `DELETE /auth/users/{self}` → **403** | **PASS** |
| `POST /auth/admins` `{username,password}` → **201**, no `pagination` | **PASS** |
| Trusted JWT `GET /auth/users` → **403** | **PASS** |
| `POST /auth/validate` | **404** **PASS** (not implemented) |
| Auth restart: bootstrap line count unchanged | **PASS** |

### 3.2 Catalog queries and writes

| Check | Result |
|-------|--------|
| Unauthenticated `GET /api/products` | **401** **PASS** |
| Invalid Bearer | **401** **PASS** |
| Admin and trusted `GET /api/products` both **200**; same `pagination.total`; `data` flat array; default `size` **10**, `current=1`, `next=2`, `total≥14` | **PASS** |
| Trusted `POST /api/products` | **403** **PASS** |
| `type=pizza-spec`: all rows `pizza-spec`, per-row `productPrice`, no `productCategory` | **PASS** |
| `type=pizza-base`: `optionsEnabled=true`; get-by-id omits `pagination`; `GET /api/options/{id}` omits `pagination` | **PASS** |
| `category=veg` excludes `pizza-spec` | **PASS** |
| `maxPrice=80` excludes Garlic Bread priced **80** (strictly less-than) | **PASS** |
| `size=1000` returned **14** (≤ 100; dataset smaller than max) | **PASS** (clamp-to-100 with huge pages is mocked) |
| Seed: simple + combo + pizza; option kinds `CRUST_SIZE` / `CRUST_TYPE` / `TOPPING`; no `system_status` table | **PASS** |
| Admin create product → **201**; `catalog_meta.dirty=true`; `last_pdf_version` unchanged (**0** before first PDF, **1** after) | **PASS** |
| Admin delete product → **200** | **PASS** |

### 3.3 Public PDF

| Check | Result |
|-------|--------|
| Before first job: `GET /api/menu.pdf` JSON **404** | **PASS** |
| After ~5 min (defaults): `GET /api/menu.pdf` **200** `Content-Type: application/pdf`, magic `%PDF-1.5` | **PASS** |
| PDF text: header **Create Your Pizza**; **v1**; sellable name+price (Garlic Bread, Snack Combo, Margherita Pizza); pizza row **options available**; options in own **Pizza spec** block with prices (olive 15, thin crust 10, …) | **PASS** |
| `catalog_meta.last_pdf_version=1`, `dirty=false` after generate; `menu_pdf.version=1` | **PASS** |
| `GET /api/menu.pdf?version=1` **200** raw PDF | **PASS** |
| `GET /api/menu.pdf?version=99` JSON **404** envelope | **PASS** |
| Product write after v1: `last_pdf_version` stays **1**, `dirty=true` | **PASS** |
| Unauthenticated PDF (no JWT) | **PASS** |

### 3.4 OpenAPI / AGENTS.md / live Swagger

| Check | Result |
|-------|--------|
| Committed `docs/openapi/{auth,catalog}-service.{yaml,json}` exist; JSON parses; locked paths present (login/register/token/users/approve/revoke/JWKS; products/options/menu.pdf); envelope + `Pagination` schemas present; `?version=` documented | **PASS** |
| **503 busy documented on catalog writes** | **FAIL** — every one of the 20 operations declares only `200`; no `503`/`401`/`403`/`404` anywhere. Raised as [BUG-02](bugs.md#bug-02--static-openapi-omits-503-and-all-error-responses). The earlier DRAFT recorded PASS here from a substring spot check; re-running it as a real assertion exposed the gap |
| `GET /api/menu.pdf` 200 content type in the spec | **FAIL** — declared `*/*`, not `application/pdf` binary (Design §9 requires PDF binary in the contract) — part of BUG-02 |
| Live `GET /v3/api-docs` and Swagger UI URLs on 8080 and 8081 | **404** **PASS** (static files only) |
| `AGENTS.md` at repo root (Compose, bootstrap logs, static OpenAPI, JWKS) | **PASS** |

### 3.5 Redis cache / locks (live) — re-run after BUG-01 fix

Every row below is from the post-fix regression run on a fresh stack. The pre-fix results (Redis `DBSIZE` stuck at **0**, injected lock ignored) are preserved in the [BUG-01 RCA](bugs.md#bug-01--catalog-redis-beans-never-wired).

| Check | Result |
|-------|--------|
| Catalog container env `SPRING_DATA_REDIS_URL=redis://redis:6379`; Redis service healthy | Observed |
| Redis `DBSIZE` at boot, before any traffic | **0** |
| After authenticated `GET /api/products`, `KEYS create-your-pizza/catalog:*` | **8 keys** (e.g. `create-your-pizza/catalog:list:\|\|\|1\|100`) — **PASS** (NFR-1 / FR-26) |
| Cache key `TTL` vs `app.cache.catalog-ttl=30s` | **29s** — **PASS** |
| After a successful generate, `create-your-pizza/menu` | **Present**, `TTL = -1` (no expiry, job replaces), JSON has `pdf` / integer `version` / `updatedAt` — **PASS** (FR-16d) |
| Redis menu `version` vs DB `max(version)` | Equal — **PASS** |
| Delete `create-your-pizza/menu`, then `GET /api/menu.pdf` | **200** from catalog-db, then key **backfilled** — **PASS** (FR-16f Redis-first / DB fallback / backfill) |
| **Inject** `create-your-pizza/lock:pdf-generation`, then admin write | **503** + `Retry-After: 60`; envelope `{status:503, message:"please try after sometime", error:"system busy"}`; no `data` / `pagination`; **no row written** — **PASS** (FR-16b) — *this is the check that failed before the fix* |
| Release the injected PDF lock, then admin write | **201** — **PASS** |
| **Inject** `create-your-pizza/lock:catalog-write` and hold it across a full job interval | Job **skipped**: `max(version)` frozen, `dirty` stays `t` — **PASS** (FR-16c, **live for the first time**) |
| Release the write lock, wait one tick | Generated: version advanced, `dirty=f`, `last_pdf_version` follows, Redis menu version follows — **PASS** |
| Historical version still retrievable after the new generate | **200** — **PASS** |
| Admin write immediately after a generate | Version **not** bumped, `dirty=t` — **PASS** (version only on generate) |
| App-held lock keys after writes complete | Absent — `finally` DEL works |
| `POST /test/pdf/generate` on default Compose profile | **404** — expected (Design §5.6: test/dev profile only) |
| Final keyspace | `create-your-pizza/menu` only (`DBSIZE=1`); catalog cache keys already expired by TTL |

Mocked tests also **PASS** for 503, job skip-not-queue, Redis-first menu, and cache TTL. They exclude `DataRedisAutoConfiguration`, which is why they could not catch BUG-01; the fix adds `CatalogStoreWiringTest` and `CatalogRedisWiringApplicationTest`, which do **not** inherit that exclusion.

### 3.6 Shortened TTL follow-up (Compose override, not committed)

`APP_JWT_TTL=1m`, `APP_PDF_INTERVAL=1m`, `APP_CACHE_CATALOG_TTL=30s`; fresh volumes.

| Check | Result |
|-------|--------|
| Login `expiresIn=60`; JWT `exp − iat = 60` | **PASS** |
| `GET /api/products` with that JWT immediately | **200** |
| Same JWT ~2s after `exp` | **200** (JWT clock-skew leeway) |
| Same JWT ~71s after `exp` | **401** — **PASS** (expiry enforced) |
| Fresh login after that window | **200**, `expiresIn=60` |
| Repeat of the **identical** query right after a create | Stale: `total` unchanged, new product **absent**, DB row present — **PASS** (no invalidation on write, Design §7) |
| Cache key still present immediately after that write | **PASS** — the write does not delete it |
| Same identical query 32s later (30s TTL) | Fresh: `total+1`, product visible — **PASS** (TTL from config) |
| Redis `KEYS` / `DBSIZE` during the above | Catalog keys **present** with TTL ≤ 30s — **PASS** post-fix (was empty pre-fix) |
| `GET /api/menu.pdf` before the first job | **404** — **PASS** |
| First PDF with 1m interval | **200**, `menu_pdf` v1 — **PASS** |
| Write overlapping a generate / injected PDF lock | **503** as in §3.5 — **PASS** |

---

## 4. Spec §8 acceptance mapping

| Area | Spec acceptance | Evidence | Verdict |
|------|-----------------|----------|---------|
| **FR-1–4 / 4a–4g** Product types & option entities | Pizza-only options, per-row price, shared catalog, `optionsEnabled`, pizza-base vs pizza-spec | Live list filters + seed SQL + PDF options block; mocked mapping tests | **Met** |
| **FR-5** Admin CRUD | Admin JWT writes succeed; no JWT / trusted → 401/403; PDF busy → 503 envelope without `data`/`pagination` | Live CRUD + 401/403; live **503** from both a real generate and an **externally injected** Redis lock, envelope omits `data`/`pagination`, no row written | **Met** |
| **FR-6** Dirty flag | Successful mutation sets dirty; version not bumped on write | Live `catalog_meta` before/after write **PASS** | **Met** |
| **FR-7–11 / 7a / 11a–11b** Queries | Admin and trusted `catalog:read`; veg/type/maxPrice; default 10; pizza-spec rows; envelope + pagination | Live **PASS** | **Met** |
| **FR-13–16 / 16a–16g** PDF | Raw binary; vN; name+price; options available; pizza-spec own space; history `?version=`; Redis latest; skip/503 locks; test trigger profile-gated | Content, versioning, public GET, live **503** **PASS**. Redis latest key **present** + backfill on DB fallback; injected-lock **503**; job skip-if-write proven **live**. Test trigger absent on default profile is **by Design** | **Met** |
| **FR-17–20 / 17a–17b / 18a** Auth | Bootstrap; login; pending+approve; paginated users; cannot DELETE self; `/auth/token`; local JWKS; no `/validate` | Live **PASS**; JWT expiry **PASS** at 1m override | **Met** |
| **FR-21–25** Quality & ops | Static OpenAPI importable; tests; AGENTS.md; Compose both Postgres + Redis + apps; sample data | `mvn test` + Compose + sample data + `AGENTS.md` **PASS**; Redis now genuinely used. Static OpenAPI imports but omits **503** and all error responses | **Partial** — [BUG-02](bugs.md#bug-02--static-openapi-omits-503-and-all-error-responses) (contract completeness, FR-21 / NFR-4 / NFR-12) |
| **NFR-1 / FR-26** Catalog Redis TTL, Redis-first, no invalidation-on-write | Mocked cache tests **PASS**. Live: `create-your-pizza/catalog:*` keys present, TTL 29s vs 30s config, stale-on-write then refresh after TTL | **Met** |
| **NFR-3 / FR-18** JWT TTL from config (default 30 min) | Config default present; live override `1m` issued `expiresIn=60` and catalog **401** after expiry | **Met** |
| **NFR-6** Job interval from config; skip if write in progress; version only on generate | Interval **5m** and **1m** overrides both generated; version not bumped on write; live **503**; **skip-if-write-lock demonstrated live** by holding an injected write lock across a full interval (version frozen, `dirty` preserved), then generating on release | **Met** |

---

## 5. Design §9 / Build-plan §8 residual (summary)

| Required proof | Mocked | Live Compose |
|----------------|--------|--------------|
| Bootstrap only when zero admins; stdout credentials | PASS | PASS (restart does not mint another) |
| Trusted pending; token fails until approve; secret once; deny; revoke blocks token | PASS | PASS |
| Admin and trusted same `GET /api/products` | PASS | PASS |
| Trusted cannot write | PASS | PASS |
| `type=pizza-spec`; untyped union; filters; size 10; max 100 clamp; flat `data`; `next=-1` | PASS | PASS (live `next=2` on default page; last-page `next=-1` mocked / not re-hit live) |
| JWT path = JWKS HTTP / Resource Server; no `/auth/validate`; no shared DB | PASS | PASS (`/auth/validate` 404; separate `auth-db` / `catalog-db`) |
| PDF vN, options available; pizza-spec own space, `?version=`, unknown 404, version not on write | PASS | PASS (Redis latest key present; DB fallback + backfill verified) |
| Write during PDF lock → 503; job skips if write lock; dirty stays | PASS | **PASS** — injected Redis PDF lock → **503** + `Retry-After: 60`; injected write lock held across an interval → job **skipped**, `dirty` preserved; generate on release |
| Catalog cache TTL from config | PASS | **PASS** — Redis keys present, TTL 29s vs `app.cache.catalog-ttl=30s` |
| Option per-row price on list and PDF | PASS | PASS |
| Admin cannot DELETE self; `/auth/users` paginated | PASS | PASS |
| Test trigger profile-gated; same skip/lock rules | PASS | Trigger **404** on default profile (by Design); skip via trigger not live |
| Static OpenAPI; no Swagger UI; AGENTS.md | PASS (committed files) | Paths/schemas PASS, no live `/v3/api-docs` PASS; **503 + error responses FAIL** → [BUG-02](bugs.md#bug-02--static-openapi-omits-503-and-all-error-responses) |
| `docker compose up` five services; sample catalog; first job **v1** | n/a | PASS (defaults ~5 min; 1m override ~58s) |
| No `system_status` | n/a | PASS |

Build-plan DoD line “Swagger UI per service” is **superseded** by the locked static OpenAPI rule (Design §9 / project-context 2026-09-22).

---

## 6. Demo notes

1. `docker compose up --build` from repo root.
2. Read first admin from `docker compose logs auth-service` (`BOOTSTRAP ADMIN`).
3. `POST /auth/login` → admin JWT; `POST /auth/register` → approve → `POST /auth/token` for trusted JWT.
4. `GET /api/products` with Bearer (filters `category`, `type`, `maxPrice`, `page`/`size`).
5. Public `GET http://localhost:8081/api/menu.pdf` — first successful body after one PDF interval (`catalog_meta` starts `dirty=true`). Defaults: ~5 minutes. This Verify also ran `APP_PDF_INTERVAL=1m` (~58s to v1). Optional `?version=1`.
6. Import Postman collections from `docs/openapi/*.yaml` or `*.json` **without** starting apps.
7. Shorter UAT waits (follow-up pass): Compose env `APP_JWT_TTL=1m`, `APP_PDF_INTERVAL=1m`, `APP_CACHE_CATALOG_TTL=30s`. Committed defaults stay 30m / 5m / 3m.

---

## 7. Open gaps vs Spec

Genuine mismatches between Spec/Design and the delivered artifacts. See the defect table in §8 for status.

**Closed 2026-09-22 — BUG-01 (was gaps 1–3):** catalog-service not using Redis at runtime; PDF/write exclusion being process-local rather than distributed; FR-16c job-skip proven only in mocked tests. All three were one root cause, fixed on `fix/16-redis-bean-wiring` and re-verified live (§3.5). Redis cache keys, the latest-menu key with DB fallback and backfill, the injected-lock **503**, and a live job-skip across a held write lock are all now demonstrated.

**Open — BUG-02: the published OpenAPI contract is incomplete.** Every one of the 20 operations in `docs/openapi/*` declares only HTTP `200` — no **503**, `401`, `403`, or `404` — and `GET /api/menu.pdf` declares `*/*` rather than `application/pdf` binary. Design §9 marks the static files **Hard (Build delivery)** and names 503 and PDF binary explicitly; Spec **FR-21 / NFR-4 / NFR-12** require the contract to match the implemented endpoints and lock the 503 shape. **No runtime impact** — the services genuinely return the 503 envelope with `Retry-After: 60` and genuinely serve raw PDF bytes; only the document integrators import is wrong. Ticketed as [17-openapi-error-responses.md](stories/17-openapi-error-responses.md).

Cache TTL behaviour (stale read on an identical query, refresh after TTL, no invalidation on write) matches Design §7 line 743 and is **correct** — and, since the fix, it is Redis-backed.

---

## 8. Defects raised

Root cause analysis lives in the [defect register](bugs.md), not in this evidence document. This table is the live status board: **this stage stays open until every row reads closed**, and each row gains its fix branch and resolution date as the ticket lands.

| Bug | Title | Severity | RCA | Ticket | Branch | Status | Resolved |
|-----|-------|----------|-----|--------|--------|--------|----------|
| **BUG-01** | Catalog Redis beans never wired | **Blocking** | [BUG-01](bugs.md#bug-01--catalog-redis-beans-never-wired) | [DONE-16](stories/DONE-16-fix-redis-wiring.md) | `fix/16-redis-bean-wiring` | **closed** | **2026-09-22** |
| **BUG-02** | Static OpenAPI omits 503 and all error responses | Docs contract; **no runtime impact** | [BUG-02](bugs.md#bug-02--static-openapi-omits-503-and-all-error-responses) | [17](stories/17-openapi-error-responses.md) | `fix/17-openapi-error-responses` | **open** — analysed, not started | — |

**Open:** 1 (BUG-02, documentation contract — analysed and ticketed, fix deferred by owner). **Closed:** 1 (BUG-01). Severity of BUG-02 for gate purposes is the owner's call: it breaches a locked Design §9 delivery requirement but changes no runtime behaviour.

BUG-01 closed after the full regression below: real `create-your-pizza/catalog:*` keys appear after a list GET, `create-your-pizza/menu` is written and backfilled, and an externally injected `create-your-pizza/lock:pdf-generation` now produces **503** + `Retry-After: 60`. BUG-02 was **found by that regression** — re-running §3.4 as a real assertion instead of a substring match showed the committed contract documents only HTTP 200. See [How a bug is closed](bugs.md#how-a-bug-is-closed).

---

## 9. Spec / product decisions (not risks)

- **JWT TTL default 30 min** and **PDF interval default 5 min** are Spec MUST config defaults. Live expiry and a one-minute generate were demonstrated with Compose overrides (`APP_JWT_TTL=1m`, `APP_PDF_INTERVAL=1m`).
- **`POST /test/pdf/generate` absent on default Compose** — Design §5.6 / Spec FR-16g: test/dev profile only, not production.
- **No live Swagger UI / `/v3/api-docs`** — locked: static `docs/openapi/*` only.
- **OpenAPI export tests skipped** in ordinary `mvn test` — they run only with `./scripts/generate-openapi.sh` (Docker).
- **Page-size max 100** — implemented; live seed is smaller than 100 so clamp was not stressed on Compose (mocked tests cover `size=1000` → 100).
- **API secret shown once on approve** — Design §11: out-of-band partner handoff is operational, not a v1 product gate.
- **Out of scope:** customer self-register, JWT denylist, orders/payments/delivery.

---

## 10. Gate

Verify is **DRAFT** (updated 2026-09-22 after BUG-01 closure and its full regression).

**BUG-01 is closed.** The Redis wiring defect is fixed on `fix/16-redis-bean-wiring`, the candid review loop returned **Findings: none**, both suites are green (auth **62**, catalog **115**), and the entire residual pack was re-run on a fresh stack. The checks that previously failed now pass, including the externally injected lock producing **503** + `Retry-After: 60` and — for the first time live — the PDF job skipping while a write lock is held.

**BUG-02 is open**, and it was found *by* that regression: the committed OpenAPI contract documents only HTTP 200, so Design §9's "503 + PDF binary" requirement and Spec FR-21 / NFR-4 / NFR-12 are unmet in the published artifact. It is documentation-only with **no runtime impact** — every 503 and raw-PDF behaviour it fails to describe was verified working on the live stack. It is fully analysed in the register and ticketed as [17-openapi-error-responses.md](stories/17-openapi-error-responses.md); **the owner has deferred the fix**, so that ticket is handed over unstarted and will be picked up as a normal ticket plus the bug-closure full regression.

Per playbook hard rule 10, this stage stays open while the §8 defect table has an open row. The owner may instead Approve-with-residual, carrying BUG-02 as a known documentation gap.

Please respond with **Approve**, **Revise: …**, or **Park**.
