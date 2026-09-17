# Spec / PRD — CreateYourPizza pizza catalog

**Status:** DRAFT — revised 2026-09-17 (Spec/PRD Revise c — pagination / PDF / keys); awaiting Spec gate (Approve / Revise / Park)

**Upstream:** [Intent](intent.md) (**APPROVED** 2026-09-17) · Spec revise [internal/spec-revise-2026-09-17-c.md](../internal/spec-revise-2026-09-17-c.md) · Prior Spec revise [internal/spec-revise-2026-09-17.md](../internal/spec-revise-2026-09-17.md) · Intent revise [internal/intent-revise-2026-09-17-b.md](../internal/intent-revise-2026-09-17-b.md) · Prior revise [internal/intent-revise-2026-09-17.md](../internal/intent-revise-2026-09-17.md) · Owner brief [internal/product-idea.md](../internal/product-idea.md)

**Related:** [Project context](project-context.md) · [HIFL playbook](hifl-playbook.md)

**Not in this stage:** full OpenAPI YAML, SQL DDL, Spring Boot scaffold, or Design/TRD — those follow Spec Approve (scaffold/deps at Build).

---

## 1. Overview & goals

### Product

**CreateYourPizza** is a **pizza delivery store product catalog** service. Administrators maintain Simple products, Combos, and Pizzas (including first-class **pizza option entities**). The public can download a basic PDF menu card without logging in. Trusted registered systems authenticate via a **generic, extensible central auth service** and consume filtered, paginated catalog REST APIs. Admins may call the **same** catalog query APIs.

### Goals (v1)

1. Single source of truth for catalog items across three admin product types, with **option entities** for pizza options and consumer listing types (**simple**, **combo**, **pizza-base**, **pizza-spec**).
2. Clear public vs authenticated boundaries (PDF public; admin + trusted APIs JWT-protected with **standard registered claims** + **binding OAuth2-style scopes**).
3. Read-heavy performance via Postgres + Redis **catalog** cache (including efficient current-menu PDF fetch from Redis).
4. Efficient PDF generation (async, dirty-driven, 5-minute cadence) with **DB version column + bytea**, Redis refresh (key/JSON locked below), and **status-table** concurrency vs admin writes.
5. **Java Spring Boot + Maven**, fully **dockerized** one-command bring-up.
6. Shareable **OpenAPI/Swagger**, **AGENTS.md**, and automated **tests** as delivery criteria.

### Non-goals

See [§9 Out of scope](#9-out-of-scope).

---

## 2. Personas / actors

| Actor | Description | Primary interactions |
|-------|-------------|----------------------|
| **Admin** | Store staff who maintain the menu | Authenticate; CRUD catalog (prices, combos, products, pizzas, option entities); **same catalog list/query APIs** as Trusted |
| **Public customer / menu consumer** | End customer or anyone viewing the menu | Fetch PDF menu card — **no auth** |
| **Trusted system / API consumer** | Registered frontend or partner application | Authenticate (incl. API key/secret → JWT); query catalog with filters + pagination |
| **Future customer (not v1)** | End customer when order flow lands later | Same **user table + roles**; profile fields **phone, name, email** later; order flows **created separately** — **not implemented** in v1 |

Central auth issues JWTs used by Admin and Trusted system in v1. Public customer does not receive or need a JWT for the PDF. Customer principal paths must remain extensible in auth design without shipping customer auth or orders now.

---

## 3. Functional requirements

### Catalog & product types

| ID | Requirement |
|----|-------------|
| **FR-1** | System supports three admin product types: **Simple**, **Combo**, **Pizza**. Consumer listing exposes four types: **simple**, **combo**, **pizza-base**, **pizza-spec** (see FR-9 / FR-11a). |
| **FR-2** | **Simple** products are sold as standalone items with a **fixed price** (e.g. cold drinks, chips) and a **veg / non-veg** classification. |
| **FR-3** | **Combo** products represent a **combination of multiple Simple products**. Combo **catalog price is admin-defined** and is **not** derived from the sum of component Simple prices. Combos have a **veg / non-veg** classification. |
| **FR-4** | **Pizza** products have **veg / non-veg** category and use the **pizza option entities** below; pizzas are **customizable** to taste within those options plus free-text non-chargeable customisations. |
| **FR-4e** | **Veg / non-veg applies to Simple, Combo, and Pizza** — not pizzas only. |
| **FR-5** | Admin can **create, update, and delete** catalog entries (products, prices, combo definitions, pizza option entities as applicable) when authenticated with an Admin-capable JWT. |
| **FR-6** | Catalog writes that add, update, or delete products **mark the catalog dirty** so the PDF job knows regeneration is needed. |

### Pizza option catalog (product specification — option entities)

Separate section of catalog product rules (admin/API/data concepts). PDF does **not** need to list these options; they apply to Pizza products in the system. In the **consumer API**, pizza **base** sellable products and pizza **specification/option catalog** items are distinct listing types (**pizza-base** vs **pizza-spec**).

**Locked approach:** pizza options (crust size, crust type, toppings, etc.) are modeled as **first-class option entities** — not only free-form strings for the catalog of sizes/types/toppings. Design details schema; Spec locks the entity approach.

| ID | Requirement |
|----|-------------|
| **FR-4a** | **Crust size** option entities: **10 inch (small)** — base; **12 inch (medium)**; **15 inch (large)**. |
| **FR-4b** | **Crust type** option entities: **thin crust** — base; **cheese burst**; **deep dish**. |
| **FR-4c** | **Topping** option entities: **chicken**, **mushrooms**, **pepperoni**, **olive** — **olive is the base** topping. |
| **FR-4d** | **Customisations:** free-text field(s); **non-chargeable** (no price impact in v1). |
| **FR-4f** | Option catalog (sizes, types, toppings) is persisted and administered as **option entities** (CRUD via admin APIs as Design maps). Consumer **pizza-spec** listing surfaces these option entities; **pizza-base** surfaces sellable pizza products. |

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
| **FR-16** | PDF generation runs **asynchronously** on a **fixed ~5-minute interval**, and **only when** the catalog dirty flag (or equivalent version marker) indicates updates since the last PDF. |
| **FR-16a** | PDF is **very basic**: header text **Create Your Pizza** (with spaces); body is a **table** where each row is **product/item name + base price** only. |
| **FR-16b** | **Product decision — concurrency:** While the PDF job is generating, enforce lock via a **status table** so catalog updates do not proceed; admin/catalog write APIs return **HTTP 503** with the [busy envelope](#locked-product-decision--status-table-lock--503-envelope) so clients **retry later**. |
| **FR-16c** | **Product decision — concurrency:** If a **catalog save is in progress** when the 5-minute job fires, the job **skips** generation for that cycle and picks up on a later cycle with updated data. |
| **FR-16d** | **PDF storage:** Postgres row(s) with a **version column** and **bytea** PDF payload. On generation, the job writes **directly to both Redis and DB**. Public fetch is **Redis-first** with **DB fallback**. See [§4 PDF Redis shape](#locked-product-decision--pdf-storage-version--bytea--redis). |
| **FR-16e** | **GET PDF HTTP response:** the public menu/PDF endpoint returns **raw binary PDF bytes only** (`Content-Type: application/pdf`). It does **not** use the JSON envelope. Internal Redis/DB storage shapes remain as locked; only the **HTTP response to clients** is raw bytes. |

### Auth

| ID | Requirement |
|----|-------------|
| **FR-17** | A **generic, extensible central auth service** allows principals to **register and authenticate**. v1 uses it for **Admin** and **trusted-system** principals on a **single user table with roles**. It must be **extensible** so **customers** can register/authenticate when order flow is integrated later — **customer auth need not be implemented in v1**, but must be **provisioned for**. Future **CUSTOMER** profile fields (when implemented later): **phone**, **name**, **email**. **User orders / order flows**, if needed later, are **created separately** (out of v1). |
| **FR-18** | On successful auth, the service issues a **JWT with 30-minute TTL** for subsequent API calls. |
| **FR-18a** | JWTs use **exact industry-standard claim names** locked in [§4 JWT claims](#locked-product-decision--jwt-claim-names-binding). Claims carry identity + **binding OAuth2-style scopes** ([§4 scopes](#locked-product-decision--oauth2-style-scope-vocabulary-binding)). **API secrets must not appear in the JWT** — used only at token issuance. |
| **FR-19** | Protected APIs validate the JWT **locally** using **industry-standard signature verification** (plus expiry/claims/scope checks) before authorizing the requested action. The **catalog service must not call the auth service solely to validate** a presented token. Verification **public-key** material comes from a **central DB-only store** per [§4 key material](#locked-product-decision--jwt-verification-key-material). **No JWKS refresh-interval** implementation. |
| **FR-20** | **Invalidating live sessions / revoking outstanding JWTs** is **out of scope** for v1. |

### Delivery quality & ops

| ID | Requirement |
|----|-------------|
| **FR-21** | System publishes an **OpenAPI (Swagger)** description that serves as the **API list for integrations** and is **importable by Postman** (and similar tools) to build a test collection. |
| **FR-22** | Automated **test cases** cover core system and API behaviors (auth boundaries, catalog CRUD, filters/pagination/flat array, public PDF access, concurrency/503 envelope where practical). |
| **FR-23** | Repository includes **AGENTS.md** as a **delivery artifact** so cloners using an agent for development/setup have project guidance. **Create the file at Build** (document here; do not require the file before Build). |
| **FR-24** | **Postgres** and **Redis** run via **Docker** with **volume persistence** and **sample data** for each product type (**Simple**, **Combo**, **Pizza**) and option entities. |
| **FR-25** | The **entire stack** (application service(s) + Postgres + Redis) is **dockerized** so anyone can clone the repo and bring the system up with a simple command (e.g. `docker compose up`). |

---

## 4. Non-functional requirements

| ID | Requirement |
|----|-------------|
| **NFR-1** | **Read-heavy:** after Admin writes, cached data serves readers until the next Admin change; Redis is the **catalog** cache layer and the **current menu PDF** fetch path (Redis-first, DB fallback). |
| **NFR-2** | **Postgres** stores product, **option entities**, user/role information, **version + bytea** PDF artifacts, and **status-table** lock rows as the system of record. |
| **NFR-3** | **JWT TTL = 30 minutes**; no live-session invalidation required in v1; claims per locked claim table + binding scopes. |
| **NFR-4** | **OpenAPI/Swagger** is a release artifact, kept consistent with implemented endpoints; suitable for Postman import. |
| **NFR-5** | **Tests** are part of Definition of Done for Build/Verify. |
| **NFR-6** | **PDF job:** async, **5-minute** poll/schedule, skip work when catalog is not dirty; apply **FR-16b / FR-16c** with **status table**; on success write **version+bytea to DB and Redis JSON** in the same generation path; avoid busy-spinning. |
| **NFR-7** | Cache invalidation (or versioned keys) on Admin writes must keep Redis coherent with Postgres for catalog reads and current PDF. |
| **NFR-8** | **Stack:** **Java Spring Boot** with **Maven**. Owner creates the initial project via **Spring Initializr**. Suggested Spring dependencies for Initializr packaging are a **Build-stage development task** (document in Build plan when Build starts; **do not scaffold application code now**). |
| **NFR-9** | **Ops:** Docker Compose (or equivalent) one-command bring-up with persistent volumes and seeded sample catalog data. |
| **NFR-10** | **AGENTS.md** ships with the built repo for agent-assisted setup/dev (see FR-23). |
| **NFR-11** | Consumer list default **page size = 10**; HTTP body products = **flat array** in `data` with **`pagination` sibling**; filters override default presentation/ordering. |
| **NFR-12** | All success and error **JSON** API responses use the **standard envelope**; paginated lists include `pagination`; 503 busy responses omit `data` and `pagination`. Public GET PDF is **raw binary**, not the envelope. |

### Locked product decision — JWT validation (rationale)

**Decision:** Prefer **industry-standard local JWT signature verification** (verify signature with auth’s public key material, check `exp` and required claims/roles/scopes). Catalog/API services **must not** call the auth service on every request just to validate the token.

**Rationale:**

- Local verification is the common industry pattern for short-lived JWTs and avoids coupling catalog availability to an auth round-trip on every call.
- Owner suggestion of Redis as a token comparison store is **not** chosen as the primary validation mechanism; **standards take precedence**.
- **Redis remains** for **catalog caching** and **current menu PDF** — **not** for JWT public-key material and **not** for comparing opaque session tokens.
- Live revocation / denylist remains **out of scope** for v1 (JWT expiry is the control). Design may note a future denylist hook without making Redis the token store now.

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

**Decision (product — binding):** Do **not** duplicate auth signing public key/secret across many config copies. The **central store for public verification key material is Postgres (DB) only** — **not Redis**.

**Rules:**

1. Maintain a **DB-only** central store of **public verification material only** (JWK/JWKS JSON or PEM public key) with **rotation metadata** as Design needs (e.g. `kid`, `alg`, `updated_at`).
2. **Private signing keys stay only on the auth service** — never copied into catalog config or Redis as signing secrets.
3. Catalog service **reads public key material from the DB central store** and verifies locally — **no per-request auth call**.
4. **No JWKS refresh-interval** implementation in v1 (do not schedule periodic JWKS endpoint refreshes as a product requirement).
5. Redis is **not** used for JWT key material (Redis remains catalog cache + current menu PDF only).

### Locked product decision — PDF storage (version + bytea + Redis)

**Decision (product):**

1. Persist generated PDF in Postgres with a **version column** and **bytea** column for PDF bytes (Design may wrap in a dedicated table; Spec locks **version + bytea**, not “version table vs column” as an open choice).
2. PDF job on successful generation writes **directly to both Redis and DB**.
3. Public/current fetch: **Redis-first**, **DB fallback**.
4. Preserve dirty/5-minute job and concurrency rules (FR-16, FR-16b, FR-16c).

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

1. During PDF generation at the 5-minute boundary, set busy via a **status table** so catalog mutations fail fast with **HTTP 503** (clients retry).
2. If catalog save is already in progress when the job fires, the job **skips** that cycle and regenerates later with fresher data.

### Locked product decision — Status table lock + 503 envelope

**Decision (product):**

- Lock/busy state is coordinated with a **status table** (not left open between advisory lock vs row lock vs status table).
- **Schema must be extremely simple and minimal** — only fields required to lock the other process. Sketch (Design may rename columns; do not over-model):

| Column (sketch) | Purpose |
|-----------------|---------|
| `lock_name` / key | Which lock (e.g. `pdf_generation` / `catalog_write`) |
| `busy` flag **or** `holder` | Whether busy / who holds the lock |
| `updated_at` | Last change timestamp |

- Do **not** add extra workflow, history, or multi-state machine columns unless a later Spec Revise requires them.
- **503** responses use the **same standard envelope**, **without** `data` and **without** `pagination`:

```json
{
  "status": 503,
  "message": "please try after sometime",
  "error": "system busy"
}
```

### Locked product decision — Standard JSON envelope

**Decision (product — binding for coding):** JSON API responses use:

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

**Paginated catalog/list example** (`data` = flat array of products; `pagination` sibling):

```json
{
  "status": 200,
  "message": "success",
  "error": "",
  "data": [
    {
      "productName": "",
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
| `error` | Error detail when applicable; on **success**, use empty string `""` (not null) |
| `data` | Payload. For catalog/list: **array of products** (flat). Other JSON APIs may use object or array as appropriate. **Omitted** on 503 busy responses. |
| `pagination` | **Sibling to `data`** when the response is paginated. **Omitted** on 503 busy (prefer omit both `data` and `pagination`). Not required on non-paginated JSON responses. |

**Pagination field rules:**

| Field | Meaning |
|-------|---------|
| `pagination.current` | Current page number |
| `pagination.next` | Next page number, or **-1** if no more pages |
| `pagination.total` | **Total products** (not total pages) |

Product fields as exemplified (`productName`, `productId`, `productType`, `productPrice`, `productCategory`, `productCreatedAt`, `productUpdatedAt`); Design/coding may add further product fields (`...`). `productType` values remain **simple / combo / pizza-base / pizza-spec**.

**DTOs:** Concrete DTO / class designs are **determined during coding** — Spec does **not** freeze DTO class shapes beyond these **wire JSON** examples.

**Exception — GET PDF:** public menu/PDF returns **raw binary** (`application/pdf`), **not** this envelope.

### Locked product decision — Consumer API listing presentation

**Decision (product):**

1. Default page size: **10** products per page.
2. Each product includes **product type** and other details (wire fields as in envelope example; additional fields allowed at Design/coding).
3. Listing types: **simple**, **combo**, **pizza-base**, **pizza-spec**.
4. **Wire format:** products always returned as a **flat array** in `data`. Never nested-by-type groups over HTTP.
5. **Pagination:** include `pagination` object **sibling to `data`** with `current`, `next` (−1 if none), `total` = total products.
6. Internal SQL may group for ordering/pagination logic; external systems always see a flat list.
7. Default ordering: **creation order** (Design may apply stable type-aware internal ordering before flattening).
8. **If a filter is specified, filter takes precedence** over default presentation/ordering.
9. **Admin** may call the **same** list/query APIs as Trusted systems.
10. **DTOs** for request/response mapping are left to coding — Spec locks wire JSON only.

### Locked product decision — Option entities

**Decision (product):** Pizza option catalog (crust size, crust type, toppings, etc.) is modeled as **first-class option entities**. Spec locks this approach; Design details schema and how **pizza-base** vs **pizza-spec** map on read APIs. Free-text customisations remain non-entity free-text and non-chargeable.

### Locked product decision — User table + roles + future CUSTOMER

**Decision (product):**

1. **Same user table with roles** for Admin, Trusted system, and future Customer (`ADMIN`, `TRUSTED_SYSTEM`, `CUSTOMER`).
2. Trusted clients may still hold API key/secret credentials (hashed secret at rest) linked to the principal/role used at token exchange.
3. Future **CUSTOMER** profile fields (later): **phone**, **name**, **email** — not built in v1.
4. **User orders / other order flows**, if needed later, are **created separately** — out of v1; note only for extensibility.

### Locked product decision — Veg / non-veg scope

**Decision (product):** Veg / non-veg applies to **Simple, Combo, and Pizza** — not pizzas only.

---

## 5. Auth & authorization matrix

| Capability | Public | Admin JWT | Trusted-system JWT | Future customer JWT |
|------------|--------|-----------|--------------------|---------------------|
| Get PDF menu card | ✅ | ✅ (allowed, not required) | ✅ (allowed, not required) | N/A in v1 (PDF still public) |
| Authenticate / obtain JWT | Public auth endpoints | — | — | Provisioned later |
| Trusted client key+secret → JWT | — | — | ✅ (registered clients) | — |
| Create / update / delete products, prices, combos, option entities | ❌ | ✅ (`catalog:write` + `ADMIN`) | ❌ | ❌ |
| Query catalog with filters + pagination (same APIs) | ❌ | ✅ (`catalog:read`) | ✅ (`catalog:read`) | Out of scope v1 |
| Register as user / trusted client | Per Design (auth service; same user table + roles) | — | — | Extensible later |

Notes:

- Claim names and scope strings in §4 are **binding**.
- v1 does **not** implement customer register/login or orders, but must not hard-block adding that principal later.
- Session/JWT **revocation** is out of scope; expiry at 30m is the control.
- Validation is **local signature verify** using **DB-only central-stored public** key material (see §4); **no JWKS refresh interval**.
- Trusted systems may present API key + API secret to auth; issued JWT carries `sub` / `client_id` / `roles` / `scope` — **not** the secret.

---

## 6. API capabilities sketch

Full OpenAPI lands in Design/Build (Swagger UI / OpenAPI artifact for Postman). Resources and query params for Spec:

### Auth (central, generic/extensible)

| Method / resource (sketch) | Auth | Purpose |
|----------------------------|------|---------|
| `POST /auth/register` (or equivalent) | Public | Register Admin / trusted client (v1) into **same user table + roles**; customer registration path provisioned for later |
| `POST /auth/login` (or equivalent) | Public | Authenticate admin (or equivalent); returns JWT (30m TTL) with locked claims + scopes |
| `POST /auth/token` (or equivalent client credentials) | Public (with API key + secret) | Trusted system exchanges credentials for JWT (`sub`, `client_id`, `roles`, `scope`); secret not returned in token |

### Catalog (admin)

| Method / resource (sketch) | Auth | Purpose |
|----------------------------|------|---------|
| `POST /api/products` | Admin JWT (`catalog:write`) | Create Simple / Combo / Pizza |
| `PUT /api/products/{id}` | Admin JWT (`catalog:write`) | Update product / price / combo membership / pizza options |
| `DELETE /api/products/{id}` | Admin JWT (`catalog:write`) | Delete product |
| `GET /api/products/{id}` | Admin or Trusted JWT (`catalog:read`) | Fetch one product |
| Option-entity admin routes (sketch) | Admin JWT (`catalog:write`) | CRUD crust size / crust type / topping option entities |

Admin writes during PDF generation lock (status table busy) → **503** busy envelope (FR-16b).

### Catalog (trusted / consumer query — Admin uses same)

| Method / resource (sketch) | Auth | Purpose |
|----------------------------|------|---------|
| `GET /api/products` | Trusted **or Admin** JWT (`catalog:read`) | List/search |

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
| `GET /api/menu.pdf` (or `/public/menu`) | **None** | Download/view current menu PDF (**Redis-first**; **DB fallback**; key `create-your-pizza/menu`) |

**HTTP response:** **raw binary PDF** only (`Content-Type: application/pdf`). **Not** the JSON envelope. Internal Redis JSON / DB bytea storage shapes stay as locked for job/storage; clients receive raw bytes.

Path names are illustrative; Design may rename while preserving the capability matrix. JSON APIs use the standard envelope (+ `pagination` when paginating); PDF GET is the binary exception.

---

## 7. Data concepts (product level)

Not full SQL DDL — Design owns schema detail. Conceptual entities:

| Concept | Notes |
|---------|--------|
| **User / Principal** | **Single user table with roles** (`ADMIN`, `TRUSTED_SYSTEM`, future `CUSTOMER`); Postgres; generic central auth. Future CUSTOMER fields: **phone**, **name**, **email** (not v1). Orders later = **separate** features/tables — out of v1 |
| **TrustedClientCredentials** | API key (+ secret hashed at rest) for registered systems; exchanged for JWT — secret never in JWT |
| **Product** | Base catalog entry: name, type, **veg/non-veg**, **base/list price**, active flag, timestamps (creation order for consumer sort) |
| **SimpleProduct** | Fixed-price item; **veg/non-veg required** |
| **Combo** | Links to multiple Simple products; **admin-set catalog price** (not sum of simples); **veg/non-veg required** |
| **Pizza** | Veg/non-veg; references **option entities** (size, crust type, toppings); free-text customisations (non-chargeable). Consumer: **pizza-base** vs **pizza-spec** |
| **OptionEntity (pizza-spec)** | First-class entities for crust sizes (10/12/15), crust types (thin / cheese burst / deep dish), toppings (chicken, mushrooms, pepperoni, olive base) — **not** free-form-string-only catalog |
| **CatalogVersion / DirtyFlag** | Marker updated on any catalog mutation; PDF job checks this (or a `lastCatalogChangeAt` vs `lastPdfGeneratedAt`) |
| **MenuPdfArtifact** | **version column + bytea** in Postgres; generation timestamp; content = header **Create Your Pizza** + name/price table; **current** copy also in Redis at `create-your-pizza/menu` |
| **SystemStatus / lock status table** | **Extremely simple / minimal** status-table row(s) for PDF-generation / catalog-write busy state — e.g. lock name/key + busy flag or holder + `updated_at` only (FR-16b / FR-16c). Do not over-model. |
| **VerificationKeyMaterial** | **DB-only** central store of **public** JWK/JWKS (or PEM) + rotation metadata for local JWT verify — **not Redis**; **no JWKS refresh-interval** product requirement |

**PDF job (product behavior):** On Admin write → set `catalog_dirty = true` (or bump `catalog_version`). Every 5 minutes: if a catalog save is in progress (status table) → **skip** cycle; else if dirty (or version > last PDF version) → mark busy in **status table** → regenerate PDF (header + name/price table) → **write version+bytea to DB and Redis JSON** (`create-your-pizza/menu`) → clear dirty / record version → clear busy. Concurrent admin writes while busy → **HTTP 503** busy envelope (omit `data` and `pagination`). If not dirty and no skip condition, no-op.

**Cache:** Redis caches product list/detail responses and the **current menu PDF**. Invalidate or bump cache keys on Admin writes / PDF regen. Redis is **not** the JWT validation store and **not** the public-key store (keys are **DB only**).

**Seed / sample data:** Dockerized Postgres must include sample rows for **Simple**, **Combo**, and **Pizza** product types and **option entities**, each with veg/non-veg as applicable.

---

## 8. Acceptance criteria (by major FR)

| Area | Acceptance |
|------|------------|
| **FR-1–4 / 4a–4f Product types & option entities** | Admin can persist and retrieve Simple, Combo, Pizza, and **option entities**; Combo price is admin-set (not sum); **veg/non-veg on all three types**; Pizza supports documented crust size/type/toppings as entities + non-chargeable free-text customisations; consumer listing distinguishes **pizza-base** vs **pizza-spec**. |
| **FR-5 Admin CRUD** | With valid Admin JWT (`sub` + `catalog:write`), create/update/delete succeed; without JWT or with Trusted-only JWT, writes return 401/403; during PDF busy (status table), writes return **503** busy envelope (no `data`, no `pagination`). |
| **FR-6 Dirty flag** | Any successful catalog mutation sets dirty/version so PDF job will regenerate within one successful 5-minute cycle (accounting for skip rules). |
| **FR-7–11 / 7a / 11a–11b Queries** | Trusted **and Admin** JWT with `catalog:read` can filter by veg/non-veg, type (`simple`/`combo`/`pizza-base`/`pizza-spec`), maxPrice, and paginate (default size **10**); response uses **standard envelope** with **flat** `data` array + **`pagination` sibling** (`current` / `next`/−1 / `total` products); filter overrides default ordering; each item includes type + details; unauthenticated → 401. |
| **FR-13–16 / 16a–16e PDF** | Unauthenticated GET returns **raw binary** PDF (`application/pdf`, not JSON envelope) with header **Create Your Pizza** and name+base-price table; dirty-driven async regen; **version + bytea** in DB; Redis key `create-your-pizza/menu` with Base64 `pdf` + `version` + UTC `updatedAt`; Redis-first / DB fallback; job writes both; status-table busy → 503 envelope on write APIs. |
| **FR-17–20 / 18a Auth** | Same user table + roles; register/login (and trusted key+secret exchange) yields JWT with locked claims (`sub`,`iss`,`aud`,`exp`,`iat`,`scope`,`roles`,…); scopes per binding vocabulary; local signature verify using **DB-only central public** key store; **no JWKS refresh interval**; secrets not in JWT; CUSTOMER phone/name/email and orders not required in v1; no revoke API required. |
| **FR-21–25 Quality & ops** | OpenAPI/Swagger imports into Postman; tests cover auth matrix, CRUD, filters/flat pagination, public PDF, concurrency/503 where practical; AGENTS.md present at Build delivery; Docker Compose brings up app + Postgres + Redis with volumes and sample data. |

---

## 9. Out of scope

- Orders, carts, checkout, payments, delivery, franchising (order flows **created separately** later if needed)
- **Implementing customer auth UI/flows** in v1 (extensibility required; future profile fields phone/name/email not built now)
- **Live session / JWT invalidation** (revoke, denylist enforcement as a product requirement — Design may note a future denylist hook)
- Deriving combo price from sum of simples
- Rich PDF branding beyond header + name/price table (option entities are system data, not PDF content)
- Real-time PDF regeneration on every write (v1 is dirty + 5-minute async)
- Putting API secrets into JWT claims
- Duplicating private signing keys into catalog service config
- **JWKS refresh-interval** implementation; Redis as JWT public-key store
- Prescribing concrete DTO class designs in Spec (wire JSON examples only; DTOs at coding)
- Full OpenAPI/SQL as part of *this* Spec gate (sketch only here)
- Spring Initializr dependency packaging / application scaffold before Build
- Creating AGENTS.md before Build (planned artifact only until then)
- Loyalty, marketing CMS, POS sync

---

## 10. Open questions / decisions for Design

Resolved by Spec Revises (see §4 and decision log in project-context) — **not** open:

- JWT validation approach → **local signature verification**; Redis = catalog cache (+ current PDF) only — **not** key store
- Combo pricing → **admin-set**, not sum
- PDF layout minimum → header **Create Your Pizza**; name + base price table
- PDF/catalog concurrency → **status table** (minimal columns) busy / 503 envelope; skip if save in progress
- PDF storage → **version column + bytea**; Redis key `create-your-pizza/menu`; JSON with Base64 `pdf`, `version`, UTC `updatedAt`; job writes both; Redis-first / DB fallback
- **GET PDF HTTP** → **raw binary** `application/pdf` (not JSON envelope)
- JWT claim names → **binding** table in §4 (`sub`, `iss`, `aud`, `exp`, `iat`, `scope`, `roles`, `client_id`, optional `jti`)
- Scope vocabulary → **binding** OAuth2-style strings + role mapping in §4
- Consumer pagination default → **10**; **flat `data` array** + **`pagination` sibling** (`current` / `next`/−1 / `total` products); filter precedence; **Admin same query APIs**
- Consumer listing types → **simple / combo / pizza-base / pizza-spec**
- Pizza options → **option entities** (not free-form-string-only catalog)
- User model → **same user table with roles**; future CUSTOMER phone/name/email; orders separate later
- Standard API envelope → locked (success `error: ""`; 503 omits `data` and `pagination`; pagination sibling when paginating)
- Key material → **DB-only** central store of **public** verify material; **no JWKS refresh interval**; private keys on auth only
- DTOs → **determined during coding**; Spec locks wire JSON examples only
- Veg/non-veg → **all types** (Simple, Combo, Pizza)
- Stack → **Java Spring Boot + Maven**; Initializr by owner; deps at Build
- Dockerize + volumes + sample data → required
- AGENTS.md + OpenAPI/Swagger → delivery artifacts

Still for Design (implementation detail only):

1. **Pagination max** (beyond default 10).
2. Exact status-table **column names** within the minimal sketch (lock key + busy/holder + `updated_at`) and whether 503 includes `Retry-After` header (body text is locked).
3. Mapping details of admin Pizza / option-entity tables → consumer **pizza-base** vs **pizza-spec** read shapes (wire fields; DTO classes at coding).
4. `kid` / rotation procedure for the **DB** public-key store (no JWKS refresh-interval product requirement).
5. Optional future JSON metadata sibling for menu (if ever added) would use the standard envelope; **v1 public GET PDF is raw binary only**.

---

## 11. Build-stage notes (not Build yet)

When Build starts (after Design + Build plan Approve):

- Owner creates the initial project via **Spring Initializr** (Java, Spring Boot, **Maven**).
- Agent provides **suggested Spring dependencies** for Initializr packaging as a Build-stage task.
- Produce **OpenAPI/Swagger**, **AGENTS.md**, Docker Compose (app + Postgres + Redis + volumes + sample data), and tests per this Spec.
- Do **not** scaffold application code in Intent/Spec/Design stages.

---

## 12. Gate ask

**Intent is APPROVED** (2026-09-17). Spec is **DRAFT — revised** (2026-09-17-c: pagination / PDF / keys); please review this Spec / PRD and reply with one of:

- **Approve** — accept Spec; proceed to Design / TRD draft
- **Revise: …** — tell us what to change in this document
- **Park** — pause Spec work

See the [gate checklist](hifl-playbook.md#gate-review-checklist-for-the-human) in the HIFL playbook.
