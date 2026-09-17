# Project context — CreateYourPizza

Durable product decisions for the CreateYourPizza pizza store catalog. Update this file when the owner locks a decision; do not treat chat alone as source of truth.

**Related:** [HIFL playbook](hifl-playbook.md) · [Intent](intent.md) · [Spec](spec.md) · Spec revise [internal/spec-revise-2026-09-17-c.md](../internal/spec-revise-2026-09-17-c.md) · Prior Spec revise [internal/spec-revise-2026-09-17.md](../internal/spec-revise-2026-09-17.md) · Intent revise [internal/intent-revise-2026-09-17-b.md](../internal/intent-revise-2026-09-17-b.md) · Prior revise [internal/intent-revise-2026-09-17.md](../internal/intent-revise-2026-09-17.md) · Owner brief [internal/product-idea.md](../internal/product-idea.md)

## Project name

**CreateYourPizza** — pizza store product catalog (create-your-pizza / catalog + public PDF + trusted APIs). PDF header text: **Create Your Pizza** (with spaces).

## Problem statement

A pizza delivery store needs one catalog of **Simple**, **Combo**, and **Pizza** products that admins maintain; customers open a **public unauthenticated PDF** menu card (**raw binary** HTTP); and **trusted registered systems** (and **admins** on the same query APIs) consume filtered, paginated catalog REST APIs after central-auth JWT login — without orders, payments, delivery, or live-session revocation in v1. Auth stays **generic and extensible** (same user table + roles) for future customer login when order flow arrives. Veg/non-veg applies to all product types; pizza options are **option entities**; consumer listing is a **flat `data` array** with **`pagination` sibling**; PDF is **version + bytea** with Redis key/JSON locked in Spec; JWT public keys are **DB only**.

## Actors and capabilities

| Actor | Capability (v1) |
|-------|-----------------|
| **Admin** | Maintain catalog: products, prices, combos, pizzas, **option entities** (CRUD) with Admin JWT; **same catalog query APIs** as Trusted (`catalog:read` / `catalog:write`) |
| **Public customer** | Open/download PDF menu card — **no authentication** |
| **Trusted registered system** | Authenticate via central auth (incl. API key/secret → JWT with locked claims + scopes); query catalog with filters + pagination |
| **Future customer** | Same **user table + roles**; profile fields phone/name/email later; orders **separate** later — **not implemented** in v1 |

## Locked facts

### From owner brief (2026-09-17)

| Fact | Detail |
|------|--------|
| Product types | **Simple** (fixed price), **Combo** (combination of simples), **Pizza** (veg/non-veg; crust, size, toppings; customizable) |
| Public PDF | Menu card PDF is open; no auth required |
| Admin | Updates prices, defines combos, adds/maintains products |
| Trusted APIs | Catalog REST with filters: veg/non-veg, type, price under Rs. X, pagination |
| Auth | Central auth; **JWT 30m TTL**; **session invalidation out of scope** |
| Storage | **Postgres** (products + users) + **Redis** (read-heavy **catalog** cache) |
| PDF job | Async every **5 minutes**, **only when catalog dirty** |
| Quality | **OpenAPI** + **automated tests** required |
| Process | HIFL gates; no application code before Design + Build plan Approve |

### From owner HIFL Revise (2026-09-17, first)

| Fact | Detail |
|------|--------|
| Central auth | **Generic + extensible**; customer register/auth **provisioned**, not built in v1 |
| PDF content | Header **Create Your Pizza**; table of **name + base price** only |
| Pizza options | Crust sizes 10/12/15 (10 base); crust types thin (base) / cheese burst / deep dish; toppings chicken, mushrooms, pepperoni, **olive base**; free-text customisations **non-chargeable** |
| Combo price | **Admin-set**; **not** sum of simples |
| JWT validation | **Local signature verification** (industry standard); catalog **does not** call auth to validate; Redis **not** primary token store |
| PDF concurrency | DB lock during generation → admin writes **HTTP 503** (retry); if catalog save in progress, job **skips** cycle |
| Stack | **Java Spring Boot + Maven**; owner uses **Spring Initializr**; dependency suggestions = **Build-stage** task |
| Docker | Postgres + Redis **volumes** + **sample data** per product type; **full stack** dockerized; one-command bring-up |
| AGENTS.md | Planned **delivery artifact** (create at Build) |
| OpenAPI/Swagger | API list for integrations; **Postman-importable** |

### From owner HIFL Revise (2026-09-17, second)

| Fact | Detail |
|------|--------|
| PDF storage | Store PDF **in DB with versioning**; on generate/update also update **Redis** for efficient current-menu fetch; dirty/5-min + concurrency preserved |
| JWT claims | Must carry **user/system identity** + **scope/permissions**; admin JWT includes **admin user id**; trusted systems may use API key/secret → JWT (`sub` / `client_id`); secrets **out of JWT**; Spec includes industry-practice suggestion |
| Consumer API | Default page size **10**; types **simple / combo / pizza-base / pizza-spec**; each item includes type + details; default **group by type** + creation order; **filter takes precedence** — **superseded** by Spec Revise (flat array) below |
| Veg / non-veg | Applies to **Simple, Combo, and Pizza** (not pizzas only) |

### From owner HIFL Spec/PRD Revise (2026-09-17)

| Fact | Detail |
|------|--------|
| Pizza options | **Option entities** (first-class) for crust size/type/toppings — not free-form-string-only catalog |
| Users | **Same user table with roles** (`ADMIN`, `TRUSTED_SYSTEM`, future `CUSTOMER`); future CUSTOMER fields **phone, name, email**; orders **created separately** later |
| JWT claims | **Binding** names: `sub`, `iss`, `aud`, `exp`, `iat`, `scope`, `roles`, `client_id` (optional `jti`) |
| Admin queries | Admin **may** call the **same** catalog list/query APIs as Trusted |
| PDF schema | **Version column + bytea**; job writes **both** Redis and DB; **Redis-first / DB fallback**; key `create-your-pizza/menu`; JSON `{pdf: base64, version, updatedAt}` UTC |
| Consumer list | Always **flat array** over HTTP; standard envelope `{status, message, error, data}`; success `error` = `""` — **extended** by Spec Revise c (`pagination` sibling) |
| Lock / 503 | **Status table** for busy; 503 envelope **without `data`**: message `please try after sometime`, error `system busy` — **extended** by Spec Revise c (minimal schema; also omit `pagination`) |
| Verify keys | **Central store** (Redis/DB) for **public** verify material; JWKS industry suggestion; private keys only on auth — **superseded** by Spec Revise c (**DB only**; no JWKS refresh) |
| Scopes | **Binding** OAuth2-style: `catalog:read`, `catalog:write`, `menu:read` + role→scope map |

### From owner HIFL Spec/PRD Revise c (2026-09-17 — pagination / PDF / keys)

| Fact | Detail |
|------|--------|
| Envelope + pagination | Paginated JSON APIs include **`pagination` sibling to `data`**: `current`, `next` (−1 if none), `total` = **total products**; `data` = flat product array; 503 omits `data` and `pagination` |
| Status table | **Extremely simple / minimal** — only fields to lock the other process (e.g. lock name/key + busy/holder + `updated_at`) |
| DTOs | **Determined during coding**; Spec does not prescribe DTO class designs beyond wire JSON examples |
| JWT key material | **DB only** for public verify keys — **not Redis**; **no JWKS refresh interval**; private signing keys on auth only; local verify preserved |
| GET PDF | HTTP response = **raw binary PDF** (`application/pdf`) only — **not** JSON envelope; Redis/DB internal storage shapes unchanged |

## Auth principals (v1)

- **ADMIN** — same user table; `roles: ["ADMIN"]`; scopes `catalog:read catalog:write menu:read`; `sub` = admin user id; may use **same** catalog query APIs as Trusted
- **TRUSTED_SYSTEM** (registered client) — same user table; `roles: ["TRUSTED_SYSTEM"]`; scopes `catalog:read menu:read`; `sub` + `client_id`; may authenticate via API key + secret exchange
- **PUBLIC** — PDF menu only (no account required for menu)
- **CUSTOMER** — **future** same user table; profile **phone / name / email** later; orders **separate** later; not shipped in v1

Former Intent mention of a distinct CUSTOMER principal for PDF access is **superseded**: PDF is public. Customer accounts for ordering remain out of scope for v1 but are **provisioned** in auth extensibility.

## Non-goals for v1

- Orders / checkout / payments / delivery / franchising
- Implementing customer register/login flows (extensibility only)
- Invalidating live user sessions / JWT revocation as a product feature
- Application implementation before Design + Build plan approval
- Spring Initializr dependency packaging / scaffold before Build
- Writing `design.md` before Spec Approve; writing AGENTS.md before Build

## Tech direction (locked stack; Build owns scaffolding)

**Locked:** **Java Spring Boot** with **Maven**. Owner creates the initial project via **Spring Initializr**. Agent supplies suggested Initializr dependencies as a **Build-stage** task — **do not scaffold now**.

Also locked for runtime: **Postgres** (incl. version+bytea PDF, option entities, **minimal** status table, user/roles, **DB-only JWT public keys**), **Redis** (catalog cache + current PDF key — **not** JWT key material), **JWT** (local verify from DB public keys; binding claims + scopes; **no JWKS refresh interval**), **OpenAPI/Swagger**, **tests**, async PDF behavior, **Docker Compose** full-stack bring-up with volumes and sample data. Paginated JSON uses envelope + **`pagination` sibling**; public GET PDF = **raw binary**.

Do not start application code until Design and Build plan are approved. Spec is **APPROVED**; Design/TRD is **on hold** until the owner says go — do not draft `design.md` yet.

## Decision log

| Date | Decision | Status |
|------|----------|--------|
| 2026-09-17 | Product is a pizza store product catalog (CreateYourPizza) | Locked |
| 2026-09-17 | Product types: Simple, Combo, Pizza (veg/non-veg; crust, size, toppings customizable) | Locked (owner brief) |
| 2026-09-17 | Admin maintains catalog (prices, combos, products) | Locked |
| 2026-09-17 | PDF menu card is **public / unauthenticated** | Locked (owner brief; supersedes ambiguous CUSTOMER PDF auth) |
| 2026-09-17 | Trusted registered system consumes catalog REST with filters + pagination | Locked (owner brief; **supersedes API-key-only M2M**) |
| 2026-09-17 | Central auth issues **JWT 30m TTL**; live session invalidation **out of scope** | Locked (owner brief) |
| 2026-09-17 | Postgres + Redis (read-heavy **catalog** cache) | Locked (owner brief; Redis role clarified by Revise) |
| 2026-09-17 | Async PDF every 5 min only when catalog dirty | Locked (owner brief) |
| 2026-09-17 | OpenAPI + tests are success criteria | Locked (owner brief) |
| 2026-09-17 | v1 excludes orders, payments, delivery, franchising | Locked |
| 2026-09-17 | HIFL Stages 1–6; no code before Design + Build plan | Locked (process) |
| 2026-09-17 | Owner brief captured in `internal/product-idea.md`; Intent + Spec revised from brief | Locked (process) |
| 2026-09-17 | Former “open ideas awaiting owner input” promoted into Intent/Spec scope | Locked |
| 2026-09-17 | **Revise:** Central auth is **generic + extensible**; customer auth **provisioned**, not built in v1 | Locked (HIFL Revise) |
| 2026-09-17 | **Revise:** PDF header **Create Your Pizza**; rows = **name + base price** only | Locked (HIFL Revise) |
| 2026-09-17 | **Revise:** Pizza option catalog — sizes 10/12/15 (10 base); crust thin (base) / cheese burst / deep dish; toppings chicken, mushrooms, pepperoni, olive **base**; free-text customisations **non-chargeable** | Locked (HIFL Revise) |
| 2026-09-17 | **Revise:** Combo price is **admin-set**, **not** sum of simples | Locked (HIFL Revise) |
| 2026-09-17 | **Revise:** JWT validation = **local signature verification** (standards > Redis-as-token-store); catalog does **not** call auth to validate; Redis stays catalog cache | Locked (HIFL Revise) |
| 2026-09-17 | **Revise:** PDF job concurrency — DB lock during generation → admin writes **HTTP 503** retry; if catalog save in progress, job **skips** cycle (PRD product decision) | Locked (HIFL Revise) |
| 2026-09-17 | **Revise:** Stack = **Java Spring Boot + Maven**; owner **Spring Initializr**; dependency suggestions = **Build-stage** task | Locked (HIFL Revise; supersedes “provisional stack”) |
| 2026-09-17 | **Revise:** Docker Compose Postgres + Redis **volumes** + sample data per product type; **full stack** dockerized one-command bring-up | Locked (HIFL Revise) |
| 2026-09-17 | **Revise:** **AGENTS.md** is a planned delivery artifact (create at Build) | Locked (HIFL Revise) |
| 2026-09-17 | **Revise:** OpenAPI/Swagger for integration listing + Postman import | Locked (HIFL Revise; reinforces brief) |
| 2026-09-17 | Intent + Spec remain **DRAFT** with gate asks after first Revise; **do not write design.md yet** | Locked (process) |
| 2026-09-17 | **Revise (2nd):** PDF stored **in DB with versioning**; on generate/update also update **Redis** for current-menu fetch; dirty/5-min + lock/skip preserved | Locked (HIFL Revise b) |
| 2026-09-17 | **Revise (2nd):** JWT claims = identity (`sub`/`client_id`; admin user id) + **scope/permissions**; API key/secret → JWT for trusted systems; secrets out of JWT; Spec records industry-practice suggestion | Locked (HIFL Revise b) |
| 2026-09-17 | **Revise (2nd):** Consumer API default page size **10**; types **simple / combo / pizza-base / pizza-spec**; group by type + creation order; **filter takes precedence** | Locked (HIFL Revise b) |
| 2026-09-17 | **Revise (2nd):** Veg/non-veg applies to **Simple, Combo, and Pizza** | Locked (HIFL Revise b; supersedes “Pizza-only or Design”) |
| 2026-09-17 | Intent + Spec remain **DRAFT** after second Revise; Intent awaiting Intent gate; Spec awaiting Spec gate after Intent; **do not write design.md yet** | Locked (process) — **superseded** by Intent Approve below |
| 2026-09-17 | **Intent APPROVED** (owner HIFL Approve); Intent gate passed; **Spec** is next (Spec remains DRAFT, awaiting Spec gate); **do not write design.md yet** | Locked (process) |
| 2026-09-17 | **Spec Revise:** Pizza options = **option entities** (not free-form-string-only catalog) | Locked (HIFL Spec Revise) |
| 2026-09-17 | **Spec Revise:** **Same user table with roles**; binding JWT claims (`sub`,`iss`,`aud`,`exp`,`iat`,`scope`,`roles`,`client_id`); future CUSTOMER **phone/name/email**; orders **separate** later | Locked (HIFL Spec Revise) |
| 2026-09-17 | **Spec Revise:** Admin may call the **same** catalog query/list APIs as Trusted | Locked (HIFL Spec Revise) |
| 2026-09-17 | **Spec Revise:** PDF = **version column + bytea**; job writes Redis+DB; Redis-first/DB fallback; key `create-your-pizza/menu`; JSON Base64 `pdf` + `version` + UTC `updatedAt` | Locked (HIFL Spec Revise) |
| 2026-09-17 | **Spec Revise:** Consumer products always **flat array**; standard envelope; success `error=""`; 503 omits `data` | Locked (HIFL Spec Revise; supersedes “group by type” wire format) |
| 2026-09-17 | **Spec Revise:** Concurrency lock via **status table**; 503 message `please try after sometime`, error `system busy` | Locked (HIFL Spec Revise) |
| 2026-09-17 | **Spec Revise:** JWT verify keys in **central store** (public only); JWKS industry suggestion; private keys on auth only | Locked (HIFL Spec Revise) |
| 2026-09-17 | **Spec Revise:** Binding scopes `catalog:read`, `catalog:write`, `menu:read` + ADMIN/TRUSTED_SYSTEM/(future) CUSTOMER mapping | Locked (HIFL Spec Revise) |
| 2026-09-17 | Spec remains **DRAFT — revised**; awaiting Spec gate (Approve / Revise / Park); Intent stays **APPROVED**; **do not write design.md yet** | Locked (process) — **superseded** in detail by Spec Revise c below (status still DRAFT awaiting gate) |
| 2026-09-17 | **Spec Revise c:** Paginated JSON envelope adds **`pagination` sibling** (`current`, `next`/−1, `total` = total products); `data` = flat product array; 503 omits `data` + `pagination` | Locked (HIFL Spec Revise c) |
| 2026-09-17 | **Spec Revise c:** Status table must be **extremely simple / minimal** (lock key + busy/holder + `updated_at` sketch) | Locked (HIFL Spec Revise c) |
| 2026-09-17 | **Spec Revise c:** **DTOs determined during coding** — Spec locks wire JSON only | Locked (HIFL Spec Revise c) |
| 2026-09-17 | **Spec Revise c:** JWT public-key store = **DB only** (supersedes Redis and/or DB); **no JWKS refresh interval**; private keys on auth; local verify preserved | Locked (HIFL Spec Revise c; supersedes Redis/DB key-store language) |
| 2026-09-17 | **Spec Revise c:** GET PDF HTTP = **raw binary** `application/pdf` only (not JSON envelope); Redis/DB storage shapes preserved | Locked (HIFL Spec Revise c) |
| 2026-09-17 | Spec remains **DRAFT — revised** (c); awaiting Spec gate (Approve / Revise / Park); Intent stays **APPROVED**; **do not write design.md yet** | Locked (process) — **superseded** by Spec Approve below |
| 2026-09-17 | **Spec APPROVED** (owner HIFL Approve); Spec gate passed; Design/TRD **on hold** until owner says go; **do not write design.md** | Locked (process) |

## Document map

| Doc | Role |
|-----|------|
| [hifl-playbook.md](hifl-playbook.md) | Process: stages, gates, handoffs |
| [intent.md](intent.md) | Stage 1 Intent — **APPROVED** 2026-09-17 |
| [spec.md](spec.md) | Stage 2 Spec / PRD — **APPROVED** 2026-09-17; Design on hold |
| [internal/spec-revise-2026-09-17-c.md](../internal/spec-revise-2026-09-17-c.md) | Owner HIFL Spec/PRD Revise c source |
| [internal/spec-revise-2026-09-17.md](../internal/spec-revise-2026-09-17.md) | Prior owner HIFL Spec/PRD Revise source |
| [internal/intent-revise-2026-09-17-b.md](../internal/intent-revise-2026-09-17-b.md) | Prior owner HIFL Intent Revise |
| [internal/intent-revise-2026-09-17.md](../internal/intent-revise-2026-09-17.md) | Prior owner HIFL Revise |
| [internal/product-idea.md](../internal/product-idea.md) | Original owner product brief |
| This file | Durable decisions and decision log |
| `design.md` | Stage 3 — **on hold** until owner says go (not started) |
| `AGENTS.md` | Build-stage delivery artifact (not created yet) |
<!-- local-sync-stamp: 2026-09-17-spec-approved -->
