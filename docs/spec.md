# Spec / PRD — CreateYourPizza pizza catalog

**Status:** DRAFT — revised after owner HIFL Revise (2026-09-17); awaiting Spec gate (depends on Intent Approve)

**Upstream:** [Intent](intent.md) · Revise [internal/intent-revise-2026-09-17.md](../internal/intent-revise-2026-09-17.md) · Owner brief [internal/product-idea.md](../internal/product-idea.md)

**Related:** [Project context](project-context.md) · [HIFL playbook](hifl-playbook.md)

**Not in this stage:** full OpenAPI YAML, SQL DDL, Spring Boot scaffold, or Design/TRD — those follow Spec Approve (scaffold/deps at Build).

---

## 1. Overview & goals

### Product

**CreateYourPizza** is a **pizza delivery store product catalog** service. Administrators maintain Simple products, Combos, and Pizzas (including pizza option catalogs). The public can download a basic PDF menu card without logging in. Trusted registered systems authenticate via a **generic, extensible central auth service** and consume filtered, paginated catalog REST APIs.

### Goals (v1)

1. Single source of truth for catalog items across three product types, with documented pizza options.
2. Clear public vs authenticated boundaries (PDF public; admin + trusted APIs JWT-protected).
3. Read-heavy performance via Postgres + Redis **catalog** cache.
4. Efficient PDF generation (async, dirty-driven, 5-minute cadence) with **safe concurrency** vs admin writes.
5. **Java Spring Boot + Maven**, fully **dockerized** one-command bring-up.
6. Shareable **OpenAPI/Swagger**, **AGENTS.md**, and automated **tests** as delivery criteria.

### Non-goals

See [§9 Out of scope](#9-out-of-scope).

---

## 2. Personas / actors

| Actor | Description | Primary interactions |
|-------|-------------|----------------------|
| **Admin** | Store staff who maintain the menu | Authenticate; CRUD catalog (prices, combos, products, pizzas, options) |
| **Public customer / menu consumer** | End customer or anyone viewing the menu | Fetch PDF menu card — **no auth** |
| **Trusted system / API consumer** | Registered frontend or partner application | Authenticate; query catalog with filters + pagination |
| **Future customer (not v1)** | End customer when order flow lands later | Will use same **generic central auth** to register/authenticate — **provisioned, not implemented** in v1 |

Central auth issues JWTs used by Admin and Trusted system in v1. Public customer does not receive or need a JWT for the PDF. Customer principal paths must remain extensible in auth design without shipping customer auth now.

---

## 3. Functional requirements

### Catalog & product types

| ID | Requirement |
|----|-------------|
| **FR-1** | System supports three product types: **Simple**, **Combo**, **Pizza**. |
| **FR-2** | **Simple** products are sold as standalone items with a **fixed price** (e.g. cold drinks, chips). |
| **FR-3** | **Combo** products represent a **combination of multiple Simple products**. Combo **catalog price is admin-defined** and is **not** derived from the sum of component Simple prices. |
| **FR-4** | **Pizza** products have **veg / non-veg** category and use the **pizza option catalog** below; pizzas are **customizable** to taste within those options plus free-text non-chargeable customisations. |
| **FR-5** | Admin can **create, update, and delete** catalog entries (products, prices, combo definitions, pizza options as applicable) when authenticated with an Admin-capable JWT. |
| **FR-6** | Catalog writes that add, update, or delete products **mark the catalog dirty** so the PDF job knows regeneration is needed. |

### Pizza option catalog (product specification — catalog/docs, not PDF-only)

Separate section of catalog product rules (admin/API/data concepts). PDF does **not** need to list these options; they apply to Pizza products in the system.

| ID | Requirement |
|----|-------------|
| **FR-4a** | **Crust size** options: **10 inch (small)** — base; **12 inch (medium)**; **15 inch (large)**. |
| **FR-4b** | **Crust type** options: **thin crust** — base; **cheese burst**; **deep dish**. |
| **FR-4c** | **Topping** options: **chicken**, **mushrooms**, **pepperoni**, **olive** — **olive is the base** topping. |
| **FR-4d** | **Customisations:** free-text field(s); **non-chargeable** (no price impact in v1). |

### Queries (trusted system)

| ID | Requirement |
|----|-------------|
| **FR-7** | Authenticated trusted systems can **list/search** catalog products via REST. |
| **FR-8** | Queries support filter by **veg / non-veg** (applicable products). |
| **FR-9** | Queries support filter by **product type**: Simple, Combo, Pizza. |
| **FR-10** | Queries support filter by **price under Rs. X**. |
| **FR-11** | Queries support **pagination** (page size / page or equivalent; default and max limits set in Design). |
| **FR-12** | Unauthenticated callers **cannot** access protected catalog query or admin write APIs. |

### PDF menu

| ID | Requirement |
|----|-------------|
| **FR-13** | System exposes a **public** endpoint (or equivalent) to obtain the **menu card PDF**. |
| **FR-14** | PDF access requires **no authentication**. |
| **FR-15** | PDF content reflects the catalog as of the last successful dirty-triggered generation. |
| **FR-16** | PDF generation runs **asynchronously** on a **fixed ~5-minute interval**, and **only when** the catalog dirty flag (or equivalent version marker) indicates updates since the last PDF. |
| **FR-16a** | PDF is **very basic**: header text **Create Your Pizza** (with spaces); body is a **table** where each row is **product/item name + base price** only. |
| **FR-16b** | **Product decision — concurrency:** While the PDF job is generating, enforce a **DB-level lock** so catalog updates do not proceed; admin/catalog write APIs return **HTTP 503** so clients **retry later**. |
| **FR-16c** | **Product decision — concurrency:** If a **catalog save is in progress** when the 5-minute job fires, the job **skips** generation for that cycle and picks up on a later cycle with updated data. |

### Auth

| ID | Requirement |
|----|-------------|
| **FR-17** | A **generic, extensible central auth service** allows principals to **register and authenticate**. v1 uses it for **Admin** and **trusted-system** principals. It must be **extensible** so **customers** can register/authenticate when order flow is integrated later — **customer auth need not be implemented in v1**, but must be **provisioned for**. |
| **FR-18** | On successful auth, the service issues a **JWT with 30-minute TTL** for subsequent API calls. |
| **FR-19** | Protected APIs validate the JWT **locally** using **industry-standard signature verification** (plus expiry/claims checks) before authorizing the requested action. The **catalog service must not call the auth service solely to validate** a presented token. |
| **FR-20** | **Invalidating live sessions / revoking outstanding JWTs** is **out of scope** for v1. |

### Delivery quality & ops

| ID | Requirement |
|----|-------------|
| **FR-21** | System publishes an **OpenAPI (Swagger)** description that serves as the **API list for integrations** and is **importable by Postman** (and similar tools) to build a test collection. |
| **FR-22** | Automated **test cases** cover core system and API behaviors (auth boundaries, catalog CRUD, filters/pagination, public PDF access, concurrency/503 where practical). |
| **FR-23** | Repository includes **AGENTS.md** as a **delivery artifact** so cloners using an agent for development/setup have project guidance. **Create the file at Build** (document here; do not require the file before Build). |
| **FR-24** | **Postgres** and **Redis** run via **Docker** with **volume persistence** and **sample data** for each product type (**Simple**, **Combo**, **Pizza**). |
| **FR-25** | The **entire stack** (application service(s) + Postgres + Redis) is **dockerized** so anyone can clone the repo and bring the system up with a simple command (e.g. `docker compose up`). |

---

## 4. Non-functional requirements

| ID | Requirement |
|----|-------------|
| **NFR-1** | **Read-heavy:** after Admin writes, cached data serves readers until the next Admin change; Redis is the **catalog** cache layer. |
| **NFR-2** | **Postgres** stores product and user information as the system of record. |
| **NFR-3** | **JWT TTL = 30 minutes**; no live-session invalidation required in v1. |
| **NFR-4** | **OpenAPI/Swagger** is a release artifact, kept consistent with implemented endpoints; suitable for Postman import. |
| **NFR-5** | **Tests** are part of Definition of Done for Build/Verify. |
| **NFR-6** | **PDF job:** async, **5-minute** poll/schedule, skip work when catalog is not dirty; apply **FR-16b / FR-16c** concurrency rules; avoid busy-spinning. |
| **NFR-7** | Cache invalidation (or versioned keys) on Admin writes must keep Redis coherent with Postgres for catalog reads. |
| **NFR-8** | **Stack:** **Java Spring Boot** with **Maven**. Owner creates the initial project via **Spring Initializr**. Suggested Spring dependencies for Initializr packaging are a **Build-stage development task** (document in Build plan when Build starts; **do not scaffold application code now**). |
| **NFR-9** | **Ops:** Docker Compose (or equivalent) one-command bring-up with persistent volumes and seeded sample catalog data. |
| **NFR-10** | **AGENTS.md** ships with the built repo for agent-assisted setup/dev (see FR-23). |

### Locked product decision — JWT validation (rationale)

**Decision:** Prefer **industry-standard local JWT signature verification** (verify signature with auth’s public key / shared secret, check `exp` and required claims/roles). Catalog/API services **must not** call the auth service on every request just to validate the token.

**Rationale:**

- Local verification is the common industry pattern for short-lived JWTs and avoids coupling catalog availability to an auth round-trip on every call.
- Owner suggestion of Redis as a token comparison store is **not** chosen as the primary validation mechanism; **standards take precedence**.
- **Redis remains** for **catalog caching** (read-heavy product data).
- Live revocation / denylist remains **out of scope** for v1 (JWT expiry is the control). Design may note a future denylist hook without making Redis the token store now.

Design/TRD will detail key distribution, claim names, and filter wiring; this PRD locks the approach and rationale.

### Locked product decision — PDF job vs catalog write concurrency

**Decision (product):**

1. During PDF generation at the 5-minute boundary, take a **DB-level lock** so catalog mutations wait or fail fast with **HTTP 503** (clients retry).
2. If catalog save is already in progress when the job fires, the job **skips** that cycle and regenerates later with fresher data.

Recorded here as a **PRD product decision** (not deferred solely to Design). Design implements the lock/skip mechanics.

---

## 5. Auth & authorization matrix

| Capability | Public | Admin JWT | Trusted-system JWT | Future customer JWT |
|------------|--------|-----------|--------------------|---------------------|
| Get PDF menu card | ✅ | ✅ (allowed, not required) | ✅ (allowed, not required) | N/A in v1 (PDF still public) |
| Authenticate / obtain JWT | Public auth endpoints | — | — | Provisioned later |
| Create / update / delete products, prices, combos | ❌ | ✅ | ❌ | ❌ |
| Query catalog with filters + pagination | ❌ | ✅ (optional convenience; Design may allow) | ✅ | Out of scope v1 |
| Register as user / trusted client | Per Design (auth service) | — | — | Extensible later |

Notes:

- Exact role claim names (`ADMIN`, `TRUSTED_CLIENT`, future `CUSTOMER`, etc.) are Design concerns; auth must stay **generic/extensible**.
- v1 does **not** implement customer register/login, but must not hard-block adding that principal later.
- Session/JWT **revocation** is out of scope; expiry at 30m is the control.
- Validation is **local signature verify** on the resource service (see §4).

---

## 6. API capabilities sketch

Full OpenAPI lands in Design/Build (Swagger UI / OpenAPI artifact for Postman). Resources and query params for Spec:

### Auth (central, generic/extensible)

| Method / resource (sketch) | Auth | Purpose |
|----------------------------|------|---------|
| `POST /auth/register` (or equivalent) | Public | Register Admin / trusted client (v1); customer registration path provisioned for later |
| `POST /auth/login` (or equivalent) | Public | Authenticate; returns JWT (30m TTL) |

### Catalog (admin)

| Method / resource (sketch) | Auth | Purpose |
|----------------------------|------|---------|
| `POST /api/products` | Admin JWT | Create Simple / Combo / Pizza |
| `PUT /api/products/{id}` | Admin JWT | Update product / price / combo membership / pizza options |
| `DELETE /api/products/{id}` | Admin JWT | Delete product |
| `GET /api/products/{id}` | Admin or Trusted JWT | Fetch one product (optional symmetry) |

Admin writes during PDF generation lock → **503** with retry semantics (FR-16b).

### Catalog (trusted query)

| Method / resource (sketch) | Auth | Purpose |
|----------------------------|------|---------|
| `GET /api/products` | Trusted JWT (and optionally Admin) | List/search |

**Query parameters (required capability):**

| Param | Meaning |
|-------|---------|
| `veg` / `category` | Filter vegetarian / non-vegetarian |
| `type` | `SIMPLE` \| `COMBO` \| `PIZZA` |
| `maxPrice` | Products with price **under** Rs. X |
| `page` / `size` (or `limit` / `offset`) | Pagination |

### Menu PDF (public)

| Method / resource (sketch) | Auth | Purpose |
|----------------------------|------|---------|
| `GET /api/menu.pdf` (or `/public/menu`) | **None** | Download/view current menu PDF |

Path names are illustrative; Design may rename while preserving the capability matrix.

---

## 7. Data concepts (product level)

Not full SQL DDL — Design owns schema. Conceptual entities:

| Concept | Notes |
|---------|--------|
| **User / Principal** | Identity for Admin and trusted systems in v1; stored in Postgres; authenticated via generic central auth; **extensible** to future customer principals |
| **Product** | Base catalog entry: name, type (`SIMPLE` \| `COMBO` \| `PIZZA`), **base/list price**, active flag, timestamps |
| **SimpleProduct** | Fixed-price item; may include veg/non-veg if applicable |
| **Combo** | Links to multiple Simple products; **admin-set catalog price** (not sum of simples) |
| **Pizza** | Veg/non-veg; references pizza option catalog (size, crust type, toppings); free-text customisations (non-chargeable) |
| **PizzaOptionCatalog** | Crust sizes (10/12/15), crust types (thin / cheese burst / deep dish), toppings (chicken, mushrooms, pepperoni, olive base) |
| **CatalogVersion / DirtyFlag** | Marker updated on any catalog mutation; PDF job checks this (or a `lastCatalogChangeAt` vs `lastPdfGeneratedAt`) |
| **MenuPdfArtifact** | Stored PDF bytes or object path + generation timestamp; content = header **Create Your Pizza** + name/price table |
| **PdfGenerationLock / CatalogWriteLock** | DB-level coordination for FR-16b / FR-16c |

**PDF job (product behavior):** On Admin write → set `catalog_dirty = true` (or bump `catalog_version`). Every 5 minutes: if a catalog save is in progress → **skip** cycle; else if dirty (or version > last PDF version) → acquire DB lock → regenerate PDF (header + name/price table) → store artifact → clear dirty / record version → release lock. Concurrent admin writes during lock → **HTTP 503** (retry). If not dirty and no skip condition, no-op.

**Cache:** Redis caches product list/detail responses. Invalidate or bump cache keys on Admin writes. Redis is **not** the primary JWT validation store.

**Seed / sample data:** Dockerized Postgres must include sample rows for **Simple**, **Combo**, and **Pizza** product types (and pizza options as needed).

---

## 8. Acceptance criteria (by major FR)

| Area | Acceptance |
|------|------------|
| **FR-1–4 / 4a–4d Product types & pizza options** | Admin can persist and retrieve Simple, Combo, and Pizza; Combo price is admin-set (not sum); Pizza supports veg/non-veg and documented crust size/type/toppings + non-chargeable free-text customisations. |
| **FR-5 Admin CRUD** | With valid Admin JWT, create/update/delete succeed; without JWT or with Trusted-only JWT, writes return 401/403; during PDF lock, writes return **503**. |
| **FR-6 Dirty flag** | Any successful catalog mutation sets dirty/version so PDF job will regenerate within one successful 5-minute cycle (accounting for skip rules). |
| **FR-7–11 Queries** | Trusted JWT can filter by veg/non-veg, type, maxPrice, and paginate; results match filters; unauthenticated → 401. |
| **FR-13–16 / 16a–16c PDF** | Unauthenticated GET returns PDF with header **Create Your Pizza** and name+base-price table; dirty-driven async regen; lock → 503 on writes; skip when save in progress. |
| **FR-17–20 Auth** | Register/login yields JWT for v1 principals; JWT expires at 30m; local signature verify on catalog APIs (no auth call for validate); customer auth not required in v1 but extensibility documented; no revoke API required. |
| **FR-21–25 Quality & ops** | OpenAPI/Swagger imports into Postman; tests cover auth matrix, CRUD, filters, public PDF, concurrency where practical; AGENTS.md present at Build delivery; Docker Compose brings up app + Postgres + Redis with volumes and sample data. |

---

## 9. Out of scope

- Orders, carts, checkout, payments, delivery, franchising
- **Implementing customer auth UI/flows** in v1 (extensibility required)
- **Live session / JWT invalidation** (revoke, denylist enforcement as a product requirement — Design may note a future denylist hook)
- Deriving combo price from sum of simples
- Rich PDF branding beyond header + name/price table (options catalogs are system data, not PDF content)
- Real-time PDF regeneration on every write (v1 is dirty + 5-minute async)
- Full OpenAPI/SQL as part of *this* Spec gate (sketch only here)
- Spring Initializr dependency packaging / application scaffold before Build
- Creating AGENTS.md before Build (planned artifact only until then)
- Loyalty, marketing CMS, POS sync

---

## 10. Open questions / decisions for Design

Resolved by this Revise (see §4 and decision log in project-context) — **not** open:

- JWT validation approach → **local signature verification**; Redis = catalog cache
- Combo pricing → **admin-set**, not sum
- PDF layout minimum → header **Create Your Pizza**; name + base price table
- PDF/catalog concurrency → DB lock / 503; skip if save in progress
- Stack → **Java Spring Boot + Maven**; Initializr by owner; deps at Build
- Dockerize + volumes + sample data → required
- AGENTS.md + OpenAPI/Swagger → delivery artifacts

Still for Design:

1. **Pizza option persistence:** option entities vs JSON config; how defaults vs per-order customisations are exposed on read APIs (customisations free-text, non-chargeable).
2. **Trusted-system registration:** same user table with roles vs separate client credentials; claim shape in JWT; how future CUSTOMER fits without rework.
3. **Admin query access:** whether Admin JWT may call the same list/filter endpoints as Trusted (recommended yes for operability).
4. **PDF storage:** DB bytea vs filesystem/object store path; how public URL is served.
5. **Pagination defaults/max** and sort order for catalog list.
6. **Veg/non-veg on Simple/Combo:** required for all types or Pizza-only (+ optional on others).
7. **DB lock mechanics:** advisory lock vs row lock vs status table; exact 503 response body/retry headers.
8. **Key material for JWT verify:** how catalog service obtains auth signing public key/secret at runtime (without calling auth per request).

---

## 11. Build-stage notes (not Build yet)

When Build starts (after Design + Build plan Approve):

- Owner creates the initial project via **Spring Initializr** (Java, Spring Boot, **Maven**).
- Agent provides **suggested Spring dependencies** for Initializr packaging as a Build-stage task.
- Produce **OpenAPI/Swagger**, **AGENTS.md**, Docker Compose (app + Postgres + Redis + volumes + sample data), and tests per this Spec.
- Do **not** scaffold application code in Intent/Spec/Design stages.

---

## 12. Gate ask

Please review this Spec / PRD and reply with one of:

- **Approve** — accept Spec; proceed to Design / TRD draft
- **Revise: …** — tell us what to change in this document
- **Park** — pause Spec work

Prerequisite: Intent should be **Approve**d (or explicitly co-approved) before Spec is treated as accepted.

See the [gate checklist](hifl-playbook.md#gate-review-checklist-for-the-human) in the HIFL playbook.
