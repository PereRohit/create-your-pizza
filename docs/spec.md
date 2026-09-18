# Spec / PRD — CreateYourPizza pizza catalog

**Status:** APPROVED 2026-09-17. **Aligned 2026-09-18** (Design Revise: one DB per service + JWKS, Redis locks, registration/login flows). Design is **APPROVED**.

**Upstream:** [Intent](intent.md) (**APPROVED** 2026-09-17)

**Related:** [Project context](project-context.md) · [HIFL playbook](hifl-playbook.md) · [Handoff](handoff.md)

**Not in this stage:** full OpenAPI YAML, SQL DDL, or Spring Boot scaffold — those follow Design Approve (scaffold/deps at Build).

---

## 1. Overview & goals

### Product

**CreateYourPizza** is a **pizza delivery store product catalog** service. Administrators maintain Simple products, Combos, and Pizzas (including first-class **pizza option entities**). The public can download a basic PDF menu card without logging in. Trusted registered systems authenticate via a **generic, extensible central auth service** and consume filtered, paginated catalog REST APIs. Admins may call the **same** catalog query APIs.

### Goals (v1)

1. Single source of truth for catalog items across three admin product types, with **option entities** for pizza options and consumer listing types (**simple**, **combo**, **pizza-base**, **pizza-spec**).
2. Clear public vs authenticated boundaries (PDF public; admin + trusted APIs JWT-protected with **standard registered claims** + **binding OAuth2-style scopes**).
3. Read-heavy performance via Postgres + Redis **catalog** cache (including efficient current-menu PDF fetch from Redis).
4. Efficient PDF generation (async, dirty-driven, interval from **config** default 5 min) with **DB version history + bytea**, Redis **latest** menu, and **Redis locks** (writes 503 while generating; job **skips** if a write is in progress).
5. **Java Spring Boot + Maven**, fully **dockerized** one-command bring-up.
6. Shareable **OpenAPI/Swagger**, **AGENTS.md**, and automated **tests** as delivery criteria.

### Non-goals

See [§9 Out of scope](#9-out-of-scope).

---

## 2. Personas / actors

| Actor | Description | Primary interactions |
|-------|-------------|----------------------|
| **Admin** | Store staff who maintain the menu | **Login** (username/password → JWT); CRUD catalog; approve/deny/revoke trusted systems; list users; **same catalog list/query APIs** as Trusted |
| **Public customer / menu consumer** | End customer or anyone viewing the menu | Fetch PDF menu card — **no auth** |
| **Trusted system / API consumer** | Partner/integration; **no password login** | Public register → **admin approve/deny**; on approve, API key+secret; `POST /auth/token` → JWT; same catalog **read** APIs as Admin |
| **Future customer (not v1)** | End customer when order flow lands later | Same **user table + roles**; profile fields **phone, name, email** later; order flows **created separately** — **not implemented** in v1 |

Central auth issues JWTs used by Admin and Trusted system in v1. Public customer does not receive or need a JWT for the PDF. Customer principal paths must remain extensible: later, customers **self-register without admin approval** but remain **visible** on the admin user list (delete later). First admin is **bootstrapped** on auth-service start if no admin exists (credentials printed to the server terminal).

---

## 3. Functional requirements

### Catalog & product types

| ID | Requirement |
|----|-------------|
| **FR-1** | System supports three admin product types: **Simple**, **Combo**, **Pizza**. Consumer listing exposes four types: **simple**, **combo**, **pizza-base**, **pizza-spec** (see FR-9 / FR-11a). |
| **FR-2** | **Simple** products are sold as standalone items with a **fixed price** (e.g. cold drinks, chips) and a **veg / non-veg** classification. |
| **FR-3** | **Combo** products represent a **combination of multiple Simple products**. Combo **catalog price is admin-defined** and is **not** derived from the sum of component Simple prices. Combos have a **veg / non-veg** classification. |
| **FR-4** | **Pizza** products have **veg / non-veg**, optional **`optionsEnabled`**, and use the **shared pizza option entities** when that flag is true; pizzas are **customizable** only when options are enabled, plus free-text non-chargeable customisations. |
| **FR-4e** | **Veg / non-veg applies to Simple, Combo, and Pizza** — not pizzas only. |
| **FR-5** | Admin can **create, update, and delete** catalog entries (products, prices, combo definitions, pizza option entities as applicable) when authenticated with an Admin-capable JWT. |
| **FR-6** | Catalog writes that add, update, or delete products **mark the catalog dirty** so the PDF job knows regeneration is needed. |

### Pizza option catalog (product specification — option entities)

Separate section of catalog product rules. Options are **for pizzas only**. Each pizza has **`optionsEnabled`**: if true, it uses the **same shared** option catalog (no per-pizza option lists). The **PDF** lists pizza-spec options in **their own space** (name + option price) **and**, on pizza product rows with options enabled, a note **options available**. Consumer listing: **pizza-base** vs **pizza-spec**. Options on **`GET /api/products?type=pizza-spec`**. Each option has its **own price**; `kind` remains only **CRUST_SIZE**, **CRUST_TYPE**, **TOPPING**.

**Locked approach:** pizza options (crust size, crust type, toppings, etc.) are modeled as **first-class option entities** — not only free-form strings for the catalog of sizes/types/toppings. Design details schema; Spec locks the entity approach.

| ID | Requirement |
|----|-------------|
| **FR-4a** | **Crust size** option entities: **10 inch (small)** — base; **12 inch (medium)**; **15 inch (large)**. Each has its **own price**. |
| **FR-4b** | **Crust type** option entities: **thin crust** — base; **cheese burst**; **deep dish**. Each has its **own price** (e.g. thin 10, deep dish 25). |
| **FR-4c** | **Topping** option entities: **chicken**, **mushrooms**, **pepperoni**, **olive** — **olive is the base** topping. Each has its **own price**. |
| **FR-4d** | **Customisations:** free-text field(s); **non-chargeable** (no price impact in v1). |
| **FR-4f** | Option catalog is **pizza-only**, persisted as **option entities**. Admin sets **`optionsEnabled`** on create/update pizza. Enabled pizzas all share the **same** option set. Consumer **pizza-spec** lists those entities; **pizza-base** lists sellable pizzas (with `optionsEnabled`). Simple/combo have no options. |
| **FR-4g** | There is **no** per-pizza option catalog (no pizza-specific subset of crusts/toppings). |

### Queries (trusted system / consumer API)

| ID | Requirement |
|----|-------------|
| **FR-7** | Authenticated trusted systems can **list/search** catalog products via REST. |
| **FR-7a** | **Admin** JWTs may call the **same** catalog query/list APIs as Trusted systems (same filters, pagination, envelope). |
| **FR-8** | Queries support filter by **veg / non-veg** (applicable to **all** product types). |
| **FR-9** | Queries support filter by **product type**. Consumer listing types: **simple**, **combo**, **pizza-base**, **pizza-spec**. Each product in the response includes **product type** and other product details. |
| **FR-10** | Queries support filter by **price under Rs. X**. |
| **FR-11** | Queries support **pagination**. **Default page size = 10** products per page. Max limits may still be set in Design. |
| **FR-11a** | **HTTP response products are always a flat array** to external systems (never nested-by-type groups in the JSON body). SQL/internal logic may group for ordering/pagination, but the wire format is a **flat** `data` array inside the standard envelope. Default **sort: creation order** (and type-aware ordering may be applied internally). **If a filter is specified, the filter takes precedence** over default presentation/ordering rules. |
| **FR-11b** | **JSON API responses** use the **standard JSON envelope** (see [§4 locked envelope](#locked-product-decision--standard-json-envelope)). Paginated catalog/list responses include a **`pagination` object sibling to `data`**. Exception: public **GET PDF** returns **raw binary** (`application/pdf`) — not the JSON envelope. |
| **FR-12** | Unauthenticated callers **cannot** access protected catalog query or admin write APIs. |

### PDF menu

| ID | Requirement |
|----|-------------|
| **FR-13** | System exposes a **public** endpoint (or equivalent) to obtain the **menu card PDF**. |
| **FR-14** | PDF access requires **no authentication**. |
| **FR-15** | PDF content reflects the catalog as of the last successful dirty-triggered generation. |
| **FR-16** | PDF generation runs **asynchronously** on a configurable interval (**default 5 minutes**, **MUST** be a properties/config value), and **only when** the catalog is dirty. |
| **FR-16a** | PDF is **very basic**: header **Create Your Pizza**; printed **vN**. **Sellable block:** name + price (simple, combo, pizza). Pizza with options enabled: add note **options available**. **Options block (own space):** pizza-spec entities, name + that option’s `price`. |
| **FR-16b** | While PDF generation is in progress, catalog **writes** return **HTTP 503** busy envelope. Coordination is **not** a `system_status` table (Design: Redis locks). |
| **FR-16c** | If a **catalog write is in progress** when the job (or test trigger) fires, generation **skips that run**. It is **not queued**. `dirty` stays true so a later cycle may run. **No extra PDF versions** are created for skipped runs. |
| **FR-16d** | **PDF storage:** Postgres **history** — one row per numeric **version** + **bytea**. **Version increments only when generation succeeds** (not when an admin writes products). On generation, **insert** a new version and write **latest** to Redis. Public fetch of **latest**: **Redis-first** with **DB fallback**. Fetch of a **past version**: **DB only**. See [§4 PDF Redis shape](#locked-product-decision--pdf-storage-version--bytea--redis). |
| **FR-16e** | **GET PDF HTTP response:** **raw binary PDF** (`application/pdf`) only — **not** the JSON envelope. Default = **latest**. Optional query param **`version`** (numeric) selects a specific historical or current version. |
| **FR-16f** | Redis stores **only the latest** generated menu. Past menu bytes **always** come from Postgres. |
| **FR-16g** | A **test-only** unauthenticated route may trigger PDF generation on demand (same lock/dirty/skip rules). Not for production. |

### Auth

| ID | Requirement |
|----|-------------|
| **FR-17** | A **generic, extensible central auth service** allows principals to **register and authenticate**. v1 uses it for **Admin** and **trusted-system** principals on a **single user table with roles**. **Admins authenticate with username/password login** (`POST /auth/login`) → JWT. **Trusted/external systems do not login**; they **register** (pending), receive an **API key + secret only after admin approve**, then call **`POST /auth/token`** for a JWT. All catalog protected APIs use **JWT only** (no separate API-key handler on catalog). It must be **extensible** so **customers** can **self-register without admin approval** later (still **visible** to admins; admin **delete** later). Future **CUSTOMER** profile fields: **phone**, **name**, **email**. **User orders / order flows** are **created separately** (out of v1). |
| **FR-17a** | If **no ADMIN** exists in the user table at **auth-service startup**, the service **creates one** and **prints username + password to the terminal/stdout**. If at least one admin exists, **do not** create another bootstrap admin. |
| **FR-17b** | Admins can **list users** (**paginated**), **approve or deny** pending trusted registrations, **revoke** trusted access, and **delete other admins**. A logged-in admin **cannot remove their own** user record (**403**). That keeps at least the caller as an admin. Additional admins are created by an existing admin, **not** via public register. |
| **FR-18** | On successful auth, the service issues a **JWT**. TTL **MUST** be a config/properties value (**default 30 minutes**). |
| **FR-18a** | JWTs use **exact industry-standard claim names** locked in [§4 JWT claims](#locked-product-decision--jwt-claim-names-binding). Claims carry identity + **binding OAuth2-style scopes** ([§4 scopes](#locked-product-decision--oauth2-style-scope-vocabulary-binding)). **API secrets must not appear in the JWT** — used only at token issuance. |
| **FR-19** | Protected catalog APIs validate the JWT **locally** (signature + expiry/claims/scope). Catalog **must not** call auth **`/validate`** per request and **must not** read auth’s database. Public verify material is published by auth as **JWKS**; catalog caches JWKs in memory and refetches on unknown `kid`. **No JWKS refresh-interval** (no timer). **One Postgres per service.** |
| **FR-20** | **Invalidating outstanding JWTs / denylist** is **out of scope** for v1 (expiry is the control). **Admin revoke of trusted API credentials** **is in scope** — further `/auth/token` calls fail; existing JWTs work until `exp`. |

### Delivery quality & ops

| ID | Requirement |
|----|-------------|
| **FR-21** | System publishes an **OpenAPI (Swagger)** description that serves as the **API list for integrations** and is **importable by Postman** (and similar tools) to build a test collection. |
| **FR-22** | Automated **test cases** cover core system and API behaviors (auth boundaries, catalog CRUD, filters/pagination/flat array, public PDF access, concurrency/503 envelope where practical). |
| **FR-23** | Repository includes **AGENTS.md** as a **delivery artifact** so cloners using an agent for development/setup have project guidance. **Create the file at Build** (document here; do not require the file before Build). |
| **FR-24** | **Postgres per service** and **Redis** (catalog) run via **Docker** with **volume persistence** and **sample data** for each product type (**Simple**, **Combo**, **Pizza**) and option entities. |
| **FR-25** | The **entire stack** is **dockerized** (`docker compose up`). |
| **FR-26** | Catalog Redis list/detail **TTL MUST** be a config/properties value (**default 3 minutes**). Redis-first, DB fallback, write Redis on DB hit; no invalidation-on-write. |

---

## 4. Non-functional requirements

| ID | Requirement |
|----|-------------|
| **NFR-1** | **Read-heavy:** Redis catalog cache (**TTL from config, default 3 minutes**; Redis-first, DB fallback, write Redis on DB fetch; **no** invalidation-on-write) and **latest menu PDF** (Redis-first, DB fallback). Historical PDFs are catalog-DB only. Redis also holds **PDF/write locks**. |
| **NFR-2** | **auth-db** stores users/roles/credentials/public JWKs. **catalog-db** stores products, option entities, PDF history, dirty meta. **No shared database. No status-table lock rows.** |
| **NFR-3** | **JWT TTL from config, default 30 minutes**; no live JWT denylist; **credential revoke** for trusted systems is in v1. |
| **NFR-4** | **OpenAPI/Swagger** is a release artifact, kept consistent with implemented endpoints; suitable for Postman import. |
| **NFR-5** | **Tests** are part of Definition of Done for Build/Verify. |
| **NFR-6** | **PDF job:** async; interval **from config (default 5 min)**; skip when not dirty; **skip if write in progress (not queued)**; 503 on writes during generation; version **only** on successful generate. Redis lock TTLs **MUST** be config: PDF lock **default 120s**, write lock **default 30s** (`finally` DEL + Redis expiry so a crash cannot hold generation forever). |
| **NFR-7** | Catalog Redis keys expire via **config TTL (default 3 minutes)**. Latest PDF Redis key is replaced on successful generation. |
| **NFR-8** | **Stack:** **Java Spring Boot** with **Maven**. Owner creates the initial project via **Spring Initializr**. Suggested Spring dependencies for Initializr packaging are a **Build-stage development task** (document in Build plan when Build starts; **do not scaffold application code now**). |
| **NFR-9** | **Ops:** Docker Compose one-command bring-up: **auth-db**, **catalog-db**, Redis, both apps, volumes, sample catalog data. |
| **NFR-10** | **AGENTS.md** ships with the built repo for agent-assisted setup/dev (see FR-23). |
| **NFR-11** | Consumer list default **page size = 10**; **every JSON list** (catalog **and** `/auth/users`) uses flat `data` + **`pagination` sibling**; get-by-id has no `pagination`. |
| **NFR-12** | All success and error **JSON** API responses use the **standard envelope**; paginated lists include `pagination`; 503 busy responses omit `data` and `pagination`. Public GET PDF is **raw binary**, not the envelope. |

### Locked product decision — JWT validation (rationale)

**Decision:** Prefer **industry-standard local JWT signature verification** (verify signature with auth’s public key material, check `exp` and required claims/roles/scopes). Catalog/API services **must not** call the auth service on every request just to validate the token.

**Rationale:**

- Local verification is the common industry pattern for short-lived JWTs and avoids coupling catalog availability to an auth round-trip on every call.
- Owner suggestion of Redis as a token comparison store is **not** chosen as the primary validation mechanism; **standards take precedence**.
- **Redis** remains for **catalog caching**, **latest menu PDF**, and **generation/write locks** — **not** JWT public keys and **not** opaque session tokens.
- Live JWT **denylist** remains **out of scope** for v1 (JWT expiry is the control). **Admin revoke of trusted API keys** is in v1 and stops **new** token issuance only.

### Locked product decision — JWT claim names (binding)

**Decision (product — binding for coding):** Use these **exact claim names** (industry registered + OAuth2-style):

| Claim | Type / form | Purpose |
|-------|-------------|---------|
| `sub` | string | Subject — admin user id, trusted-client subject, or future customer id |
| `iss` | string | Issuer (auth service identifier) |
| `aud` | string or array of strings | Audience (catalog / API resource) |
| `exp` | numeric date | Expiration (enforce 30m TTL from `iat`) |
| `iat` | numeric date | Issued-at |
| `jti` | string (optional) | Unique token id — optional in v1; useful if denylist added later |
| `scope` | string | Space-delimited OAuth2-style scopes (see scope vocabulary below) |
| `roles` | array of strings | Coarse role(s): `ADMIN`, `TRUSTED_SYSTEM`, and later `CUSTOMER` |
| `client_id` | string (when applicable) | Registered trusted-system client id after API key/secret exchange |

**Rules:**

- **Admin** tokens: `sub` = admin user id; `roles` includes `ADMIN`; `scope` includes write (+ read) catalog scopes.
- **Trusted system** tokens: `sub` = client subject; `client_id` set; `roles` includes `TRUSTED_SYSTEM`; `scope` includes read catalog scopes. Map API key (+ secret exchange) → JWT; **do not put the API secret in the token**.
- **Future CUSTOMER:** `sub` = customer user id; `roles` includes `CUSTOMER`; profile fields **phone**, **name**, **email** live on the **user** record when that principal is implemented — **not built in v1**.
- Prefer **fine-grained `scope`** for API authorization; `roles` are coarse gates and do **not** replace scopes where API-level permission is required.
- Catalog validates signature + `exp` + required identity/scope claims **locally**.

### Locked product decision — OAuth2-style scope vocabulary (binding)

**Decision (product — binding for coding):** Use these concrete scope strings:

| Scope | Meaning |
|-------|---------|
| `catalog:read` | List/search/get catalog products (consumer query APIs) |
| `catalog:write` | Create/update/delete catalog products and option entities (admin write APIs) |
| `menu:read` | Authenticated menu/PDF metadata access if ever gated; **public PDF remains unauthenticated** and does not require this scope |

**Role → scope mapping (v1 + future):**

| Role | Issued scopes (minimum) |
|------|-------------------------|
| `ADMIN` | `catalog:read` `catalog:write` `menu:read` |
| `TRUSTED_SYSTEM` | `catalog:read` `menu:read` |
| `CUSTOMER` (future) | `menu:read` (+ order-related scopes when order APIs exist — **out of v1**) |

Design/Build must use these strings unchanged unless a later Spec Revise changes them.

### Locked product decision — JWT verification key material

**Decision (product):** **One Postgres per service.** Public JWKs live **only in auth-db**. Catalog **never** reads auth tables.

**Rules:**

1. Auth stores **public** JWKs in its own DB and exposes **`GET /auth/.well-known/jwks.json`**.
2. **Private signing keys stay only on the auth service.**
3. Catalog **GETs JWKS over HTTP**, caches in **memory**, verifies JWT **locally**. Unknown `kid` → refetch JWKS. **No** scheduled JWKS poll.
4. **No** `POST /auth/validate` (not needed for short-lived JWTs).
5. Redis is **not** used for JWT key material.

### Locked product decision — PDF storage (version history + bytea + Redis latest)

**Decision (product):**

1. Persist **every** generated PDF in Postgres as a **history** of numeric **version** + **bytea** (one row per version; do not overwrite past bytes).
2. PDF **content** includes header **Create Your Pizza**, **v{version}**, sellable name+price rows (pizza with options: note **options available**), and pizza-spec options in **a separate space**.
3. On generation, **insert** the new DB row and write **latest** to Redis (same JSON shape).
4. **Latest** public fetch: **Redis-first**, **DB fallback** (`max(version)`).
5. **Historical** fetch (`version` query param ≠ latest): **Postgres only**.
6. Preserve dirty job (interval from config), **FR-16b** (writes 503 while generating), **FR-16c** (skip job if write in progress). Locks: **Redis**, not a status table.

**Redis (binding):**

| Field | Value |
|-------|--------|
| **Key** | `create-your-pizza/menu` |
| **Value** | JSON-encoded object (see below) |

```json
{
  "pdf": "<base64-encoded PDF bytes>",
  "version": 2,
  "updatedAt": "2006-01-02T00:00Z"
}
```

- **`pdf`:** PDF file contents encoded as a **Base64 string** inside JSON (Spec guidance for Design/Build — JSON cannot hold raw binary safely).
- **`version`:** integer matching the DB version column for the current menu.
- **`updatedAt`:** UTC, ISO-8601 (as in the example).

### Locked product decision — PDF job vs catalog write concurrency

**Decision (product):**

1. During PDF generation, catalog mutations fail fast with **HTTP 503**.
2. If a catalog write is in progress when the job fires, the job **skips** that run (**not queued**). `dirty` remains true.
3. Implement with **Redis locks** on catalog-service (Design). Lock TTLs: PDF **120s**, write **30s** (config; `finally` + expiry). **Do not** use a `system_status` table or extra lock columns on `catalog_meta` / `menu_pdf`.
4. **PDF version** increments only on a **successful generation insert**, never on admin product writes.

### Locked product decision — Status table lock + 503 envelope

**Decision (product):** **No status table.** 503 JSON body is unchanged:

```json
{
  "status": 503,
  "message": "please try after sometime",
  "error": "system busy"
}
```

Omit `data` and `pagination`. Optional header `Retry-After: 60` (Design).

### Locked product decision — Standard JSON envelope

**Decision (product — binding for coding):** JSON API responses use `status`, `message`, `data` when present, `pagination` on **lists**, `error` when there is an error.

**Docs layout convention (not a Build/omit-empty implementation rule):** examples in this Spec **omit empty fields** (no `"error": ""`, no empty-string product fields, no `pagination` on get-by-id). Coding **may** still send success `error: ""` if the stack always includes the key; **do not** treat omit-empty as a Definition of Done.

**Paginated list example:**

```json
{
  "status": 200,
  "message": "success",
  "data": [
    {
      "productName": "Cola",
      "productId": "uuid",
      "productType": "simple",
      "productPrice": 233.44,
      "productCategory": "non-veg",
      "productCreatedAt": "UTC",
      "productUpdatedAt": "UTC"
    }
  ],
  "pagination": {
    "current": 1,
    "next": 2,
    "total": 10
  }
}
```

| Field | Meaning |
|-------|---------|
| `status` | HTTP status code (number) |
| `message` | Human-readable success/info |
| `error` | Error detail when applicable. **Omitted** in Spec/Design **examples** when there is no error (**docs only** — not a required omit-empty serializer). |
| `data` | Payload. For catalog/list: **array of products** (flat). **Omitted** on 503 busy responses. |
| `pagination` | **Sibling to `data` on every list.** **Omitted** on 503, on get-by-id, and on other non-list JSON. |

**Pagination field rules:**

| Field | Meaning |
|-------|---------|
| `pagination.current` | Current page number |
| `pagination.next` | Next page number, or **-1** if no more pages |
| `pagination.total` | **Total matching items** (not total pages) |

Product fields as exemplified (`productName`, `productId`, `productType`, `productPrice`, `productCategory`, `productCreatedAt`, `productUpdatedAt`); Design/coding may add further product fields (`...`). `productType` values remain **simple / combo / pizza-base / pizza-spec**.

**DTOs:** Concrete DTO / class designs are **determined during coding** — Spec does **not** freeze DTO class shapes beyond these **wire JSON** examples.

**Exception — GET PDF:** public menu/PDF returns **raw binary** (`application/pdf`), **not** this envelope.

### Locked product decision — Consumer API listing presentation

**Decision (product):**

1. Default page size: **10** products per page.
2. Each product includes **product type** and other details (wire fields as in envelope example; additional fields allowed at Design/coding).
3. Listing types: **simple**, **combo**, **pizza-base**, **pizza-spec**.
4. **Wire format:** products always returned as a **flat array** in `data`. Never nested-by-type groups over HTTP.
5. **Pagination:** include `pagination` object **sibling to `data`** with `current`, `next` (−1 if none), `total` = **total matching items** (products or users, depending on the list).
6. Internal SQL may group for ordering/pagination logic; external systems always see a flat list.
7. Default ordering: **creation order** (Design may apply stable type-aware internal ordering before flattening).
8. **If a filter is specified, filter takes precedence** over default presentation/ordering.
9. **Admin** may call the **same** list/query APIs as Trusted systems.
10. **All JSON lists** use this pagination (including **`GET /auth/users`**). Get-by-id does not.
11. **DTOs** for request/response mapping are left to coding — Spec locks wire JSON only. **Omitting empty keys is a docs layout convention, not a coding mandate.**

### Locked product decision — Option entities

**Decision (product):** Pizza option catalog is **first-class option entities**, **pizzas only**. Each pizza has **`optionsEnabled`**. If true, the pizza uses the **one shared** option catalog (not a per-pizza set). Each entity has its **own price**. `kind` is only **CRUST_SIZE**, **CRUST_TYPE**, **TOPPING**. Free-text customisations remain non-entity and non-chargeable.

### Locked product decision — User table + roles + future CUSTOMER

**Decision (product):**

1. **Same user table with roles** for Admin, Trusted system, and future Customer (`ADMIN`, `TRUSTED_SYSTEM`, `CUSTOMER`).
2. **Admin:** username/password **login** → JWT. First admin **bootstrapped** at auth startup if none exist (credentials to stdout). Further admins: **`POST /auth/admins`**. An admin **cannot delete their own** user row.
3. **Trusted:** public **`POST /auth/register`** only (server sets `TRUSTED_SYSTEM` + `PENDING`) → admin **approve** (`/auth/token` after). Principal type is the **URL**, not a client-supplied `role`.
4. **Future CUSTOMER:** self-register **without** approval; **visible** to admins; admin **delete** later — not built in v1. Profile fields later: **phone**, **name**, **email**.
5. API secret hashed at rest; **never** in JWT.
6. **User orders / other order flows** later — out of v1.

### Locked product decision — Veg / non-veg scope

**Decision (product):** Veg / non-veg applies to **Simple, Combo, and Pizza** — not pizzas only.

### Locked product decision — Catalog Redis TTL

**Decision (product):** Catalog list/detail Redis keys use **TTL 3 minutes**. Read path is **Redis-first**, **DB fallback**, **write Redis on successful DB fetch**. **Do not** manually invalidate those keys on admin write. Latest PDF Redis key is separate (job overwrite; historical PDFs not cached).

---

## 5. Auth & authorization matrix

| Capability | Public | Admin JWT | Trusted-system JWT | Future customer JWT |
|------------|--------|-----------|--------------------|---------------------|
| Get PDF menu card (latest or `?version=`) | ✅ | ✅ (allowed, not required) | ✅ (allowed, not required) | N/A in v1 (PDF still public) |
| Admin login → JWT | Public `/auth/login` | — | — | — |
| Trusted register (pending) | ✅ | — | — | — |
| Trusted key+secret → JWT | — | — | ✅ **after admin approve** (`/auth/token`) | — |
| List users; approve/deny/revoke trusted; create/delete admins | ❌ | ✅ | ❌ | ❌ |
| Create / update / delete products, prices, combos, option entities | ❌ | ✅ (`catalog:write` + `ADMIN`) | ❌ | ❌ |
| Query catalog with filters + pagination **including pizza-spec options** (same APIs) | ❌ | ✅ (`catalog:read`) | ✅ (`catalog:read`) | Out of scope v1 |
| Customer self-register | — | — | — | Later: no admin approval; visible to admins |

Notes:

- Claim names and scope strings in §4 are **binding**.
- v1 does **not** implement customer register/login or orders, but must not hard-block adding that principal later (no-approval register + admin visibility).
- Live JWT **denylist** is out of scope; **credential revoke** for trusted systems is in scope.
- Validation is **local signature verify** using **DB-only central-stored public** key material (see §4); **no JWKS refresh interval**.
- Trusted systems present API key + API secret to **auth** `/auth/token` only; catalog sees **JWT**. Secret is **not** in the token.

---

## 6. API capabilities sketch

Full OpenAPI lands in Design/Build (Swagger UI / OpenAPI artifact for Postman). Resources and query params for Spec:

### Auth (central, generic/extensible)

| Method / resource (sketch) | Auth | Purpose |
|----------------------------|------|---------|
| `GET /auth/.well-known/jwks.json` | Public | Catalog (and anyone) reads **public** JWKs. Not a validate endpoint. |
| `POST /auth/register` | Public | Trusted **pending** registration only. Not admin self-register. |
| `POST /auth/login` | Public | Admin username/password → JWT |
| `POST /auth/token` | Public (API key + secret) | **Approved** trusted system → JWT (`sub`, `client_id`, `roles`, `scope`); secret not in token |
| `GET /auth/users` | Admin JWT | Paginated list (`page`/`size`); filter `role`, `status` |
| `POST /auth/users/{id}/approve` | Admin JWT | Issue API key+secret **once**; activate trusted |
| `POST /auth/users/{id}/deny` | Admin JWT | Deny pending trusted |
| `POST /auth/users/{id}/revoke` | Admin JWT | Revoke trusted credentials |
| `POST /auth/admins` | Admin JWT | Create another admin |
| `DELETE /auth/users/{id}` | Admin JWT | Delete another admin. **403 if id is the caller**. Customer delete later |

### Catalog (admin)

| Method / resource (sketch) | Auth | Purpose |
|----------------------------|------|---------|
| `POST /api/products` | Admin JWT (`catalog:write`) | Create Simple / Combo / Pizza (pizza: **`optionsEnabled`**) |
| `PUT /api/products/{id}` | Admin JWT (`catalog:write`) | Update product / price / combo membership / pizza options |
| `DELETE /api/products/{id}` | Admin JWT (`catalog:write`) | Delete product |
| `GET /api/products/{id}` | Admin or Trusted JWT (`catalog:read`) | Fetch one product |
| Option-entity admin routes (sketch) | Admin JWT (`catalog:write`) | CRUD crust size / crust type / topping option entities |

Admin writes during PDF generation (Redis PDF lock) → **503** busy envelope (FR-16b). Job skips if write lock is held (FR-16c).

### Catalog (trusted / consumer query — Admin uses same)

| Method / resource (sketch) | Auth | Purpose |
|----------------------------|------|---------|
| `GET /api/products` | Trusted **or Admin** JWT (`catalog:read`) | List/search. **pizza-spec option entities are rows in this list** (`type=pizza-spec` for options only) |

**Query parameters (required capability):**

| Param | Meaning |
|-------|---------|
| `veg` / `category` | Filter vegetarian / non-vegetarian (all types) |
| `type` | `simple` \| `combo` \| `pizza-base` \| `pizza-spec` |
| `maxPrice` | Products with price **under** Rs. X |
| `page` / `size` (or `limit` / `offset`) | Pagination; **default size = 10** |

**Response presentation:**

- Standard envelope; `data` is a **flat array** of products; **`pagination`** object is a **sibling** to `data` (`current`, `next` = next page or **-1**, `total` = **total products**).
- Each item includes **product type** and other product details (wire example fields in §4; coding may add more).
- **Filter takes precedence** over default ordering/presentation when specified.
- **DTOs** are coding-time; Spec locks wire JSON only.

### Menu PDF (public)

| Method / resource (sketch) | Auth | Purpose |
|----------------------------|------|---------|
| `GET /api/menu.pdf` | **None** | Latest menu PDF (**Redis-first**; **DB fallback**; key `create-your-pizza/menu`) |
| `GET /api/menu.pdf?version={n}` | **None** | Specific numeric version (**DB** if not latest; Redis allowed if latest) |

**HTTP response:** **raw binary PDF** only. PDF: **v{n}**; sellable rows; pizza **options available** note when enabled; pizza-spec in **own space**.

Path names are illustrative; Design may rename while preserving the capability matrix. JSON APIs use the standard envelope (+ `pagination` when paginating); PDF GET is the binary exception.

---

## 7. Data concepts (product level)

Not full SQL DDL — Design owns schema detail. Conceptual entities:

| Concept | Notes |
|---------|--------|
| **User / Principal** | **Single user table with roles**; `status` PENDING/ACTIVE/DENIED/REVOKED for trusted. First **ADMIN** bootstrapped if none. Future CUSTOMER fields: **phone**, **name**, **email** (not v1). Orders later = **separate** |
| **TrustedClientCredentials** | API key + hashed secret **after admin approve**; `/auth/token` → JWT; admin **revoke**; secret never in JWT |
| **Product** | `product_type` `simple` \| `combo` \| `pizza`; veg/non-veg; base price; timestamps |
| **SimpleProduct** | Fixed-price item; **veg/non-veg required** |
| **Combo** | Links to multiple Simple products; **admin-set catalog price**; **veg/non-veg required** |
| **Pizza** | Veg/non-veg; **`optionsEnabled`** to use the **shared** option catalog; free-text customisations (non-chargeable). Consumer: **pizza-base** |
| **OptionEntity (pizza-spec)** | Shared pizza-only catalog; **per-row price**; three `kind`s; on **`GET /api/products?type=pizza-spec`** and **PDF options section** |
| **CatalogVersion / DirtyFlag** | Marker on catalog mutation; PDF job checks dirty |
| **MenuPdfArtifact** | **History**: numeric version PK + bytea; PDF shows **vN**; **latest** also in Redis `create-your-pizza/menu`; past versions DB-only |
| **SystemStatus** | **Removed.** Redis locks on catalog-service. |
| **VerificationKeyMaterial** | Public JWKs in **auth-db only**; published as **JWKS HTTP**; catalog memory cache |

**PDF job (product behavior):** On Admin write → Redis write lock → set dirty (**do not** bump PDF version) → drop write lock. Every interval: if write lock → **skip (not queued)**; if not dirty → no-op; else Redis PDF lock → generate → insert **next** version+bytea + Redis latest → clear dirty → drop PDF lock. Writes during PDF lock → **503**. Lock TTLs 120s / 30s self-heal if `DEL` never runs.

**Cache:** Redis catalog JSON **TTL from config (default 3 min)**; latest PDF; **locks**. Not JWT keys.

**Seed / sample data:** Dockerized Postgres must include sample rows for **Simple**, **Combo**, and **Pizza** product types and **option entities**, each with veg/non-veg as applicable.

---

## 8. Acceptance criteria (by major FR)

| Area | Acceptance |
|------|------------|
| **FR-1–4 / 4a–4g Product types & option entities** | Option entities **pizza-only**, **per-row price**, shared catalog; pizza **`optionsEnabled`**; no per-pizza option set; pizza-base vs pizza-spec. |
| **FR-5 Admin CRUD** | With valid Admin JWT (`sub` + `catalog:write`), create/update/delete succeed; without JWT or with Trusted-only JWT, writes return 401/403; during PDF busy, writes return **503** busy envelope (no `data`, no `pagination`). |
| **FR-6 Dirty flag** | Any successful catalog mutation sets dirty so PDF job will regenerate within one successful cycle (skip if write in progress). |
| **FR-7–11 / 7a / 11a–11b Queries** | Trusted **and Admin** JWT with `catalog:read` can filter by veg/non-veg, type, maxPrice (**including option prices**), paginate (default **10**); pizza-spec on `GET /api/products`; envelope + `pagination`. |
| **FR-13–16 / 16a–16g PDF** | Raw binary PDF; **vN**; sellable name+price; pizza with options: note **options available**; pizza-spec in **own space** with option prices. |
| **FR-17–20 / 17a–17b / 18a Auth** | Bootstrap first admin; admin login; trusted pending+approve; **paginated** `GET /auth/users`; **cannot DELETE self**; `/auth/token`; local JWKS verify. |
| **FR-21–25 Quality & ops** | OpenAPI/Swagger imports into Postman; tests cover auth matrix, CRUD, filters/flat pagination, public PDF, concurrency/503 where practical; AGENTS.md at Build; Docker Compose: both Postgres + Redis. |

---

## 9. Out of scope

- Orders, carts, checkout, payments, delivery, franchising (order flows **created separately** later if needed)
- **Implementing customer auth UI/flows** in v1 (extensibility required: no-approval register + admin visibility/delete later)
- **Live JWT denylist** (credential **revoke** for trusted systems **is** in v1)
- Deriving combo price from sum of simples
- Rich PDF branding beyond header + **vN** + name/price table (options **are** listed)
- Real-time PDF regeneration on every write (v1 is dirty + 5-minute async)
- Putting API secrets into JWT claims
- Duplicating private signing keys into catalog service config
- **JWKS refresh-interval timer**; Redis as JWT public-key store; catalog reading auth DB; **`/auth/validate` per request**
- `system_status` table / lock columns on `catalog_meta`
- Manual invalidation of catalog Redis keys on write (TTL instead)
- Prescribing omit-empty JSON serialization at Build (docs examples omit empty keys only)
- Full OpenAPI/SQL as part of *this* Spec gate (sketch only here)
- Spring Initializr dependency packaging / application scaffold before Build
- Creating AGENTS.md before Build (planned artifact only until then)
- Loyalty, marketing CMS, POS sync

---

## 10. Open questions / decisions for Design

Resolved by Spec Revises (see §4 and decision log in project-context) — **not** open:

- JWT validation approach → **local signature verification**; Redis = catalog cache (+ current PDF) only — **not** key store
- Combo pricing → **admin-set**, not sum
- PDF layout minimum → header **Create Your Pizza**; **vN**; sellable table + pizza **options available** note; pizza-spec in **own space**
- PDF/catalog concurrency → Redis locks; writes 503; job skips if write in progress; **no status table**
- Key material → **auth-db + JWKS HTTP**; catalog memory; **no shared DB**; **no /validate**
- One Postgres per service
- Config MUST exist for PDF interval (default 5m), catalog TTL (default 3m), JWT TTL (default 30m)
- PDF storage → **version history + bytea**; Redis **latest only**; `GET ?version=` numeric; default latest raw binary
- User model → same table + roles; **admin login**; **trusted pending+approve**; bootstrap first admin; future CUSTOMER no-approval
- Catalog Redis → **TTL 3 min**; Redis-first; fill on DB fetch; no invalidation-on-write
- Options on consumer API → **`GET /api/products`** (`type=pizza-spec`)
- **GET PDF HTTP** → **raw binary** `application/pdf` (not JSON envelope)
- JWT claim names → **binding** table in §4 (`sub`, `iss`, `aud`, `exp`, `iat`, `scope`, `roles`, `client_id`, optional `jti`)
- Scope vocabulary → **binding** OAuth2-style strings + role mapping in §4
- Consumer pagination default → **10**; **flat `data` array** + **`pagination` sibling** (`current` / `next`/−1 / `total` products); filter precedence; **Admin same query APIs**
- Consumer listing types → **simple / combo / pizza-base / pizza-spec**
- Pizza options → **option entities** (not free-form-string-only catalog)
- Standard API envelope → locked (success `error: ""`; 503 omits `data` and `pagination`; pagination sibling when paginating)
- DTOs → **determined during coding**; Spec locks wire JSON examples only
- Veg/non-veg → **all types** (Simple, Combo, Pizza)
- Stack → **Java Spring Boot + Maven**; Initializr by owner; deps at Build
- Dockerize + volumes + sample data → required
- AGENTS.md + OpenAPI/Swagger → delivery artifacts

Implementation detail (locked in [design.md](design.md) **APPROVED**):

1. Pagination max → **100**
2. Status columns + `Retry-After: 60`
3. pizza-base / pizza-spec mapping + options on `/api/products`
4. `kid` rotation → JWKS startup + refetch on unknown kid (not auth DB)
5. GET PDF raw binary + version query; no JSON metadata sibling in v1
6. Redis locks; one DB per service; registration by URL

---

## 11. Build-stage notes (not Build yet)

When Build starts (after Design + Build plan Approve):

- Owner creates the initial project via **Spring Initializr** (Java, Spring Boot, **Maven**).
- Agent provides **suggested Spring dependencies** for Initializr packaging as a Build-stage task.
- Produce **OpenAPI/Swagger**, **AGENTS.md**, Docker Compose (**auth-db** + **catalog-db** + Redis + apps), tests, **`docs/stories/`** before coding.
- Do **not** scaffold application code in Intent/Spec/Design stages.

---

## 12. Approved — aligned to Design

Spec is **APPROVED** (2026-09-17). Product locks **updated 2026-09-18**. Design/TRD is **APPROVED** ([design.md](design.md)). Build plan is **APPROVED** ([build-plan.md](build-plan.md)). Build: `docs/stories/`.
