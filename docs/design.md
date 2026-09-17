# Design / TRD — CreateYourPizza

**Status:** DRAFT — 2026-09-17. Awaiting Design gate (**Approve / Revise / Park**).

**Upstream:** [Intent](intent.md) (**APPROVED**) · [Spec / PRD](spec.md) (**APPROVED**)

**Related:** [Project context](project-context.md) · [HIFL playbook](hifl-playbook.md) · [Handoff](handoff.md)

**Not in this stage:** Spring Boot scaffold, Initializr dependency lists, `AGENTS.md`, full OpenAPI YAML, or application code. Those follow Design **Approve** then Build-plan **Approve**. DTOs are **coding-time** (Spec lock); this TRD locks tables, wire JSON, and mapping rules.

---

## 1. Status and scope

This TRD translates APPROVED Intent + Spec into service boundaries, schema, API contracts, JWT/key handling, PDF job + lock, Redis, and Docker runtime.

**In scope for Design**

- Two-service architecture (auth-service + catalog-service)
- Shared Postgres (+ Redis for catalog/PDF cache only)
- Data model (tables / columns / mappings — not a full SQL dump)
- JWT issue/verify, DB-only public keys, `kid` rotation procedure
- HTTP capabilities, envelope, pagination, public PDF binary
- PDF dirty/5-min job and minimal status-table lock
- Docker Compose topology
- OpenAPI / tests **notes** (artifacts at Build)

**Out of scope for Design**

- Application / Spring Boot implementation
- Suggested Spring Initializr dependency packaging (Build-plan / Build)
- Creating `AGENTS.md` (Build)
- Reopening locked product decisions in Intent/Spec

---

## 2. Architecture

Two Spring Boot + Maven applications. Owner scaffolds via Spring Initializr at **Build**. One Postgres instance, one Redis instance, Docker Compose bring-up.

```text
Public client          GET /api/menu.pdf (raw application/pdf)
        |
        v
catalog-service  ----reads---->  Postgres schema catalog
        |                         (products, options, PDF, status, dirty)
        |                    ----reads---->  Postgres schema auth.verification_keys
        |                    ----cache---->  Redis (catalog + current menu)
        |
Admin / Trusted        JWT on catalog APIs
        |
        +------------> auth-service  ----writes-->  Postgres schema auth
                                                  (users, credentials, public keys)
                                                  private signing keys stay in-process on auth only
```

| Service | Responsibility |
|---------|----------------|
| **auth-service** | Register, login, trusted API-key+secret → JWT (30m). Owns `users` + roles, hashed secrets, **writes** public verify keys. **Private signing keys never leave auth** (memory/config of auth only — not catalog, not Redis). |
| **catalog-service** | Product and option-entity CRUD, catalog queries, PDF job, public GET PDF. **Reads** public keys from DB and verifies JWT **locally**. **Must not** call auth per request to validate a token. |

**Shared runtime**

- **One Postgres** with two schemas: `auth` and `catalog`. Same physical database so “same user table + roles” is one table, not two stores.
- **One Redis** used **only** by catalog-service (catalog cache + current menu PDF). Not JWT keys, not token comparison.
- Services are independently deployable containers; they share the DB and (catalog only) Redis via Compose network.

**Trust boundaries**

- Public: PDF GET only.
- Auth public endpoints: register / login / token exchange.
- Catalog JSON APIs: valid JWT + required `scope` (and role as coarse gate).
- Catalog never holds private signing material.

---

## 3. Data model

Logical tables. Column types are indicative. Build owns Flyway/Liquibase DDL. UUID primary keys unless noted.

### 3.1 Schema `auth`

#### `users`

| Column | Type | Notes |
|--------|------|--------|
| `id` | UUID PK | JWT `sub` |
| `role` | text | `ADMIN` \| `TRUSTED_SYSTEM`. `CUSTOMER` reserved, unused in v1 |
| `username` | text unique | Admin login identifier |
| `password_hash` | text nullable | Admin password; null for trusted-system rows that use API credentials only |
| `created_at` | timestamptz | |
| `updated_at` | timestamptz | |

Future CUSTOMER columns (`phone`, `name`, `email`) are **not** added in v1; leave room by not overloading `username` as email-only.

#### `trusted_client_credentials`

| Column | Type | Notes |
|--------|------|--------|
| `id` | UUID PK | |
| `user_id` | UUID FK → users | Role must be `TRUSTED_SYSTEM` |
| `api_key` | text unique | Public identifier at token exchange |
| `secret_hash` | text | Secret hashed at rest; **never** in JWT |
| `created_at` | timestamptz | |

#### `verification_keys`

Central **public** verify material. **DB only**. Catalog reads; auth writes.

| Column | Type | Notes |
|--------|------|--------|
| `kid` | text PK | JWT header `kid` |
| `alg` | text | e.g. `RS256` |
| `public_jwk` | jsonb | Public JWK (or PEM stored in a text column if Build prefers one form — pick **one** at coding; TRD default = JWK JSON) |
| `active` | boolean | Inactive keys kept for verifying in-flight tokens until expiry |
| `created_at` | timestamptz | |
| `updated_at` | timestamptz | |

Private keys: auth-service config/secret only. Never inserted here.

### 3.2 Schema `catalog`

#### `products`

Admin product types: `SIMPLE`, `COMBO`, `PIZZA`. Veg/non-veg on all three. Combo **price is admin-set**, not a sum.

| Column | Type | Notes |
|--------|------|--------|
| `id` | UUID PK | Wire `productId` |
| `name` | text | Wire `productName` |
| `admin_type` | text | `SIMPLE` \| `COMBO` \| `PIZZA` |
| `category` | text | `veg` \| `non-veg` |
| `price` | numeric(12,2) | Base/list price (Rs.); combo = admin-set |
| `customisation_notes` | text nullable | Pizza only; free-text **non-chargeable**; null for simple/combo |
| `active` | boolean | Soft-hide from consumer list if false (Build may still return on admin get) |
| `created_at` | timestamptz | Default consumer sort |
| `updated_at` | timestamptz | |

#### `combo_items`

| Column | Type | Notes |
|--------|------|--------|
| `combo_id` | UUID FK → products | Parent must be `COMBO` |
| `simple_id` | UUID FK → products | Member must be `SIMPLE` |
| PK | (`combo_id`, `simple_id`) | |

Membership is catalog structure; it does **not** drive combo price.

#### `option_entities`

First-class pizza-spec catalog (not free-form strings).

| Column | Type | Notes |
|--------|------|--------|
| `id` | UUID PK | Exposed as `productId` on consumer **pizza-spec** rows |
| `kind` | text | `CRUST_SIZE` \| `CRUST_TYPE` \| `TOPPING` |
| `name` | text | Display name |
| `is_base` | boolean | Seed: 10-inch size, thin crust, olive topping |
| `created_at` | timestamptz | |
| `updated_at` | timestamptz | |

Seed (Build): sizes 10 / 12 / 15 inch (10 base); types thin / cheese burst / deep dish (thin base); toppings chicken, mushrooms, pepperoni, olive (olive base).

Pizza **sellable** products do not require a join table to options in v1: options are a shared spec catalog (`pizza-spec`); pizzas are `pizza-base` products with optional `customisation_notes`.

#### `menu_pdf`

Single current-artifact row is enough (history optional; Spec locks version + bytea, not a version history product).

| Column | Type | Notes |
|--------|------|--------|
| `id` | smallint PK | Constant `1` (singleton current menu) |
| `version` | integer | Matches Redis `version` |
| `pdf` | bytea | Raw PDF bytes |
| `generated_at` | timestamptz | UTC; Redis `updatedAt` |

#### `catalog_meta`

Dirty / last-change marker so the job does not scan all products.

| Column | Type | Notes |
|--------|------|--------|
| `id` | smallint PK | Constant `1` |
| `dirty` | boolean | Set true on successful catalog mutation |
| `last_catalog_change_at` | timestamptz | |
| `last_pdf_version` | integer | Last version written to `menu_pdf` |

Alternative at coding: omit `dirty` and compare `last_catalog_change_at` vs PDF `generated_at`. TRD default: boolean `dirty` **and** timestamps for debugging.

#### `system_status`

Minimal lock table (Spec). No history, no state machine.

| Column | Type | Notes |
|--------|------|--------|
| `lock_name` | text PK | `pdf_generation` \| `catalog_write` |
| `busy` | boolean | |
| `holder` | text nullable | e.g. job instance id / request id |
| `updated_at` | timestamptz | |

Seed two rows with `busy = false`.

### 3.3 Consumer listing mapping

| Wire `productType` | Source |
|--------------------|--------|
| `simple` | `products` where `admin_type = SIMPLE` |
| `combo` | `products` where `admin_type = COMBO` |
| `pizza-base` | `products` where `admin_type = PIZZA` |
| `pizza-spec` | `option_entities` (all kinds) |

**pizza-spec wire fields:** reuse the product envelope fields where they fit: `productId` = option id, `productName` = option name, `productType` = `pizza-spec`, `productPrice` = `0` (options are not priced sellables in v1), `productCategory` = omit or empty string — **proposal:** include `"productCategory": ""` and add coding-time extra fields `optionKind` (`CRUST_SIZE` / `CRUST_TYPE` / `TOPPING`) and `isBase` (boolean). Spec allows additional fields.

**pizza-base extra fields (coding-time, allowed):** `customisationNotes` (string, may be empty).

List `GET /api/products` unions product rows and option rows **only when** the filter does not restrict to a single type. Default sort: `created_at` ascending across the union. If `type` (or other filters) is set, **filter takes precedence** (only matching rows; still creation order within the filtered set).

`maxPrice` means `price < maxPrice`. Sellable products use `products.price`. **pizza-spec** rows use wire price `0`, so they match whenever `0 < maxPrice`. **Proposal:** ignore `maxPrice` when `type=pizza-spec` (type filter takes precedence). On an untyped list, include pizza-spec rows that satisfy `0 < maxPrice`.

Veg/non-veg filter: applies to `products.category`. **pizza-spec** has no veg flag — **proposal:** exclude pizza-spec from results when `category`/`veg` filter is present, unless `type=pizza-spec` (then category filter is ignored because it does not apply). When `type=pizza-spec` and category is also sent: type filter wins (filter precedence) and category is ignored for that query.

---

## 4. Auth, JWT, and key store

### 4.1 Issuance (auth-service)

| Endpoint | Behavior |
|----------|----------|
| `POST /auth/register` | Create `users` row. v1 bodies: admin (username+password, role `ADMIN`) or trusted system (role `TRUSTED_SYSTEM` + generate/store `api_key` + hashed secret). Customer register **not** implemented; do not reject the role enum in schema — simply do not expose a customer path. |
| `POST /auth/login` | Admin username+password → JWT. |
| `POST /auth/token` | Body: `api_key` + `api_secret` → JWT for `TRUSTED_SYSTEM`. Secret verified against hash; **not** copied into claims. |

JWT **TTL = 30 minutes** (`exp` = `iat` + 1800). Signing: asymmetric (RS256). Auth holds the private key; on boot (and on rotation) upserts the matching **public** JWK into `verification_keys` with a new `kid`.

### 4.2 Binding claims (unchanged from Spec)

Header: `alg`, `kid`, `typ=JWT`.

Payload: `sub`, `iss`, `aud`, `exp`, `iat`, `scope` (space-delimited), `roles` (array), `client_id` when trusted, optional `jti`.

| Role | `scope` |
|------|---------|
| `ADMIN` | `catalog:read catalog:write menu:read` |
| `TRUSTED_SYSTEM` | `catalog:read menu:read` |

`iss` / `aud`: concrete strings chosen at Build (e.g. `iss=create-your-pizza-auth`, `aud=create-your-pizza-catalog`). Catalog rejects mismatch.

### 4.3 Local verify (catalog-service)

On each protected request:

1. Parse JWT; read `kid`.
2. Resolve public JWK from an in-memory map loaded from `auth.verification_keys` where `active = true` (see rotation).
3. Verify signature, `exp`, `iss`, `aud`.
4. Authorize: required `scope` for the route; `catalog:write` routes also require `roles` contains `ADMIN`.

**No HTTP call to auth-service** for validation. Redis is not used for keys or tokens.

### 4.4 `kid` rotation (no JWKS refresh interval)

Product: **no** scheduled JWKS HTTP refresh.

**Procedure:**

1. Auth generates a new keypair, inserts `verification_keys` row (`kid` = new id, `active=true`), starts signing new tokens with that `kid`. Previous row remains `active=true` until its last tokens expire (or auth sets `active=false` after 30m+skew).
2. Catalog loads **all active** keys at **startup**.
3. If a token’s `kid` is **unknown**, catalog **lazily SELECTs** that `kid` from DB, caches it if found and active, else 401. This is on-demand lookup, not a timer.
4. Optional: if verify fails because key was deactivated, 401. No denylist (out of Spec).

---

## 5. HTTP APIs

JSON responses use the **standard envelope**. Paginated lists include `pagination` sibling. Success `error` is `""`. Public GET PDF is **raw binary** — not the envelope.

DTO classes are **not** specified here.

### 5.1 Envelope (locked)

```json
{
  "status": 200,
  "message": "success",
  "error": "",
  "data": {},
  "pagination": {
    "current": 1,
    "next": 2,
    "total": 10
  }
}
```

- `pagination` only on paginated JSON.
- 503 busy: `{ "status": 503, "message": "please try after sometime", "error": "system busy" }` — **omit** `data` and `pagination`. Also send header **`Retry-After: 60`**.

### 5.2 Pagination

| Rule | Value |
|------|--------|
| Default `size` | **10** |
| Max `size` | **100** (reject or clamp — **proposal: clamp** to 100, still 200 with clamped size) |
| `page` | 1-based |
| `pagination.current` | Current page |
| `pagination.next` | Next page number, or **-1** if none |
| `pagination.total` | **Total matching items** (products + pizza-spec rows in that query), not total pages |

Query params: `page`, `size` (Spec also allows limit/offset; TRD standardizes **`page` + `size`**).

### 5.3 Auth-service

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/auth/register` | Public | Register ADMIN or TRUSTED_SYSTEM |
| `POST` | `/auth/login` | Public | Admin → JWT |
| `POST` | `/auth/token` | Public (key+secret) | Trusted → JWT |

Auth JSON also uses the standard envelope (`data` holds `access_token`, `token_type=Bearer`, `expires_in=1800`). Non-paginated: no `pagination` key.

### 5.4 Catalog-service — admin writes (`catalog:write` + `ADMIN`)

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/products` | Create Simple / Combo / Pizza (`adminType` in body) |
| `PUT` | `/api/products/{id}` | Update product / price / combo membership / pizza notes |
| `DELETE` | `/api/products/{id}` | Delete (or deactivate — **proposal: hard delete** if no FK block; combo_items cascade) |
| `POST` | `/api/options` | Create option entity |
| `PUT` | `/api/options/{id}` | Update option entity |
| `DELETE` | `/api/options/{id}` | Delete option entity |

While `pdf_generation.busy = true`, these return **503** busy envelope + `Retry-After: 60`.

Successful mutation: set `catalog_meta.dirty = true`, bump `last_catalog_change_at`, invalidate Redis catalog keys (see §7).

### 5.5 Catalog-service — queries (`catalog:read`; Admin **or** Trusted)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/products` | List/search, filters + pagination |
| `GET` | `/api/products/{id}` | One **product** (simple/combo/pizza-base) |
| `GET` | `/api/options/{id}` | One **pizza-spec** option entity |

**`GET /api/products` query parameters**

| Param | Meaning |
|-------|---------|
| `category` | `veg` \| `non-veg` (alias `veg` accepted if Build wants one name — TRD canonical = **`category`**) |
| `type` | `simple` \| `combo` \| `pizza-base` \| `pizza-spec` |
| `maxPrice` | Price **under** Rs. X (`price < maxPrice`) |
| `page` | Default 1 |
| `size` | Default 10, max 100 |

Response `data`: **flat array**. Each product object includes at least Spec example fields: `productName`, `productId`, `productType`, `productPrice`, `productCategory`, `productCreatedAt`, `productUpdatedAt` (ISO-8601 UTC). Extra fields allowed at coding.

Unauthenticated → 401. Wrong scope → 403.

### 5.6 Public PDF

| Method | Path | Auth | Response |
|--------|------|------|----------|
| `GET` | `/api/menu.pdf` | **None** | Raw PDF bytes, `Content-Type: application/pdf` |

Lookup: Redis key `create-your-pizza/menu` first; on miss, `catalog.menu_pdf` bytea; optionally backfill Redis. If neither exists yet (before first successful job), **404** with JSON envelope is acceptable for an empty catalog — **proposal:** JSON 404 envelope for “no menu yet”; once generated, always raw PDF.

---

## 6. PDF job and status-table lock

**Content:** header **Create Your Pizza**; table rows **name + base price** for sellable products (Simple, Combo, Pizza / pizza-base). **Do not** list pizza-spec options on the PDF.

**Cadence:** Spring scheduled task every **5 minutes** on catalog-service (single instance in v1 Compose; if scaled later, lock still serializes).

**Algorithm**

1. If `system_status` row `catalog_write` has `busy = true` → **skip** cycle (no generation).
2. Else read `catalog_meta`; if `dirty = false` → no-op.
3. Else try to set `pdf_generation` busy (`busy=false` → `true`, set `holder`, `updated_at`). If already busy → skip (defensive).
4. Generate PDF from current sellable products (active only).
5. `version = last_pdf_version + 1` (or 1 if none). Write `menu_pdf` (version + bytea + generated_at) **and** Redis JSON in the same success path:
   ```json
   {
     "pdf": "<base64 PDF bytes>",
     "version": 2,
     "updatedAt": "2006-01-02T00:00Z"
   }
   ```
6. Set `dirty = false`, `last_pdf_version = version`, clear `pdf_generation.busy`.

**Admin write path**

1. If `pdf_generation.busy` → 503 envelope + `Retry-After: 60` (do not write).
2. Set `catalog_write.busy = true` for the duration of the transaction; clear in `finally`. If job fires meanwhile, it **skips**.
3. Persist mutation; set dirty; invalidate catalog cache.

Keep the status table **minimal** — do not add queues or history.

---

## 7. Redis cache

Used by **catalog-service only**.

| Key | Value | Role |
|-----|--------|------|
| `create-your-pizza/menu` | JSON `{pdf, version, updatedAt}` UTC | Current menu; **Redis-first**, DB fallback |
| `create-your-pizza/catalog:*` | Implementation-defined (e.g. hashed query string → envelope JSON) | List/detail read cache |

**Invalidation:** on any successful admin write, delete `create-your-pizza/catalog:*` (SCAN/pattern or version prefix — Build picks). PDF Redis key is **overwritten by the job**, not deleted on every product write (stale PDF until next dirty cycle is **specified** behavior).

**Not stored in Redis:** JWT public keys, sessions, API secrets.

---

## 8. Docker Compose runtime

v1 `docker compose up` brings up:

| Service | Notes |
|---------|--------|
| `postgres` | Volume for data; init scripts create schemas `auth` + `catalog`, tables, seed |
| `redis` | Volume persistence |
| `auth-service` | App container; depends on postgres healthy |
| `catalog-service` | App container; depends on postgres + redis healthy |

**Seed (sample data)**

- At least one ADMIN user (documented default password in AGENTS.md at Build)
- At least one TRUSTED_SYSTEM + API key/secret (dev only)
- One `verification_keys` row matching auth’s boot key (or auth inserts on first start)
- Sample **Simple**, **Combo** (with combo_items), **Pizza** (veg and non-veg coverage)
- All locked option entities (sizes, crusts, toppings with base flags)
- `system_status` two rows idle; `catalog_meta` dirty=true so first job can produce a PDF **or** seed a menu_pdf row

No application code in this stage — Compose file lands at Build.

---

## 9. OpenAPI and tests (notes for Build)

**OpenAPI / Swagger**

- Each service exposes Springdoc/OpenAPI 3 (or one gateway later — v1: two specs is fine).
- Catalog spec is the integration list; **Postman-importable**.
- Document envelope, pagination, 503 busy, PDF as `application/pdf` binary.
- Keep spec aligned with implemented routes.

**Tests (Definition of Done at Build/Verify)**

- Auth: register/login/token; claims/scopes; secret not in JWT
- Catalog: JWT local verify (valid, expired, bad sig, missing scope)
- Admin CRUD; Trusted cannot write
- Filters, default page size 10, max 100, flat `data`, `pagination` sibling, `next=-1`
- pizza-base vs pizza-spec mapping
- Public PDF unauthenticated; Content-Type PDF
- Status-table: write during fake PDF busy → 503 body + `Retry-After`; job skips when catalog_write busy
- Dirty job writes DB + Redis together

**AGENTS.md:** create at Build (clone, compose, default users, how to hit Swagger).

**Initializr deps:** suggested at Build-plan, not here.

---

## 10. Engineering decisions (Spec §10 leftovers)

| Topic | Decision (this TRD) |
|-------|---------------------|
| Pagination max | **100**; default **10**; oversized `size` **clamped** to 100 |
| Status columns | `lock_name` PK, `busy` boolean, `holder` nullable text, `updated_at` |
| Lock names | `pdf_generation`, `catalog_write` |
| 503 `Retry-After` | **Yes** — HTTP header `Retry-After: 60`; JSON body unchanged |
| pizza-base vs pizza-spec | pizza-base = `products` `PIZZA`; pizza-spec = `option_entities`; extra wire fields `optionKind`, `isBase`, `customisationNotes` as in §3.3 |
| Category vs pizza-spec | Veg filter excludes specs unless `type=pizza-spec` (then category ignored) |
| List pagination params | `page` + `size` (1-based) |
| `kid` rotation | JWT `kid`; load active keys at catalog startup; **lazy DB lookup** on unknown kid; **no** JWKS poll |
| GET PDF | Raw `application/pdf`; 404 JSON envelope only if no artifact exists yet |
| Combo delete | Hard delete; `combo_items` cascade |
| PDF rows | Sellable products only (not option entities) |
| Key format | Public **JWK JSON** in `verification_keys.public_jwk`; alg RS256 |
| Cache vs PDF freshness | Catalog Redis invalidated on write; PDF Redis updated only by the 5-min dirty job |

These are Design proposals. Owner **Revise** if any should change before Approve.

---

## 11. Residual open questions

None that block coding if this TRD is Approved. Optional later (not v1): JSON metadata sibling for menu (would use the standard envelope); JWT denylist; customer register; second catalog instance (needs lock fencing beyond single Compose replica).

---

## 12. Gate

Design / TRD is **DRAFT**.

Please respond with one of:

- **Approve** — accept this TRD; next stage is Build plan (`docs/build-plan.md`)
- **Revise: …** — specific feedback; this file is updated, Design does not advance
- **Park** — pause Design

Do not start Build-plan or application code until Design **Approve**.
