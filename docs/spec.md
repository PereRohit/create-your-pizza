# Spec / PRD — CreateYourPizza pizza catalog

**Status:** DRAFT — awaiting Spec gate (depends on Intent Approve)

**Upstream:** [Intent](intent.md) · Owner brief [internal/product-idea.md](../internal/product-idea.md)

**Related:** [Project context](project-context.md) · [HIFL playbook](hifl-playbook.md)

**Not in this stage:** full OpenAPI YAML, SQL DDL, or Design/TRD — those follow Spec Approve.

---

## 1. Overview & goals

### Product

CreateYourPizza is a **pizza delivery store product catalog** service. Administrators maintain Simple products, Combos, and Pizzas. The public can download a PDF menu card without logging in. Trusted registered systems authenticate via a central auth service and consume filtered, paginated catalog REST APIs.

### Goals (v1)

1. Single source of truth for catalog items across three product types.
2. Clear public vs authenticated boundaries (PDF public; admin + trusted APIs JWT-protected).
3. Read-heavy performance via Postgres + Redis.
4. Efficient PDF generation (async, dirty-driven, 5-minute cadence).
5. Shareable **OpenAPI** and automated **tests** as delivery criteria.

### Non-goals

See [§9 Out of scope](#9-out-of-scope).

---

## 2. Personas / actors

| Actor | Description | Primary interactions |
|-------|-------------|----------------------|
| **Admin** | Store staff who maintain the menu | Authenticate; CRUD catalog (prices, combos, products, pizzas) |
| **Public customer / menu consumer** | End customer or anyone viewing the menu | Fetch PDF menu card — **no auth** |
| **Trusted system / API consumer** | Registered frontend or partner application | Authenticate; query catalog with filters + pagination |

Central auth issues JWTs used by Admin and Trusted system. Public customer does not receive or need a JWT for the PDF.

---

## 3. Functional requirements

### Catalog & product types

| ID | Requirement |
|----|-------------|
| **FR-1** | System supports three product types: **Simple**, **Combo**, **Pizza**. |
| **FR-2** | **Simple** products are sold as standalone items with a **fixed price** (e.g. cold drinks, chips). |
| **FR-3** | **Combo** products represent a **combination of multiple Simple products** and are maintainable by Admin (membership and catalog price rules as decided in Design; see open questions). |
| **FR-4** | **Pizza** products have **veg / non-veg** category, a **predefined crust**, **crust size**, and **toppings** (count / set), and are **customizable** to customer taste (option model detailed in Design). |
| **FR-5** | Admin can **create, update, and delete** catalog entries (products, prices, combo definitions) when authenticated with an Admin-capable JWT. |
| **FR-6** | Catalog writes that add, update, or delete products **mark the catalog dirty** so the PDF job knows regeneration is needed. |

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

### Auth

| ID | Requirement |
|----|-------------|
| **FR-17** | A **central auth service** allows users (Admin and trusted-system principals) to **register and authenticate**. |
| **FR-18** | On successful auth, the service issues a **JWT with 30-minute TTL** for subsequent API calls. |
| **FR-19** | Protected APIs validate the JWT before authorizing the requested action (Admin vs trusted-system capabilities). |
| **FR-20** | **Invalidating live sessions / revoking outstanding JWTs** is **out of scope** for v1. |

### Delivery quality

| ID | Requirement |
|----|-------------|
| **FR-21** | System publishes an **OpenAPI (Swagger)** description importable by Postman and other consumers. |
| **FR-22** | Automated **test cases** cover core system and API behaviors (auth boundaries, catalog CRUD, filters/pagination, public PDF access). |

---

## 4. Non-functional requirements

| ID | Requirement |
|----|-------------|
| **NFR-1** | **Read-heavy:** after Admin writes, cached data serves readers until the next Admin change; Redis is the central cache layer. |
| **NFR-2** | **Postgres** stores product and user information as the system of record. |
| **NFR-3** | **JWT TTL = 30 minutes**; no live-session invalidation required in v1. |
| **NFR-4** | **OpenAPI** is a release artifact, kept consistent with implemented endpoints. |
| **NFR-5** | **Tests** are part of Definition of Done for Build/Verify. |
| **NFR-6** | **PDF job:** async, **5-minute** poll/schedule, skip work when catalog is not dirty; avoid busy-spinning the system. |
| **NFR-7** | Cache invalidation (or versioned keys) on Admin writes must keep Redis coherent with Postgres for catalog reads. |

---

## 5. Auth & authorization matrix

| Capability | Public | Admin JWT | Trusted-system JWT |
|------------|--------|-----------|--------------------|
| Get PDF menu card | ✅ | ✅ (allowed, not required) | ✅ (allowed, not required) |
| Authenticate / obtain JWT | Public auth endpoints | — | — |
| Create / update / delete products, prices, combos | ❌ | ✅ | ❌ |
| Query catalog with filters + pagination | ❌ | ✅ (optional convenience; Design may allow) | ✅ |
| Register as user / trusted client | Per Design (auth service) | — | — |

Notes:

- Exact role claim names (`ADMIN`, `TRUSTED_CLIENT`, etc.) are Design concerns.
- v1 does **not** require a separate “customer account” for viewing the menu PDF.
- Session/JWT **revocation** is out of scope; expiry at 30m is the control.

---

## 6. API capabilities sketch

Full OpenAPI lands in Design/Build. Resources and query params for Spec:

### Auth (central)

| Method / resource (sketch) | Auth | Purpose |
|----------------------------|------|---------|
| `POST /auth/register` (or equivalent) | Public | Register user / trusted client |
| `POST /auth/login` (or equivalent) | Public | Authenticate; returns JWT (30m TTL) |

### Catalog (admin)

| Method / resource (sketch) | Auth | Purpose |
|----------------------------|------|---------|
| `POST /api/products` | Admin JWT | Create Simple / Combo / Pizza |
| `PUT /api/products/{id}` | Admin JWT | Update product / price / combo membership / pizza options |
| `DELETE /api/products/{id}` | Admin JWT | Delete product |
| `GET /api/products/{id}` | Admin or Trusted JWT | Fetch one product (optional symmetry) |

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
| **User / Principal** | Identity for Admin and trusted systems; stored in Postgres; authenticated via central auth |
| **Product** | Base catalog entry: name, type (`SIMPLE` \| `COMBO` \| `PIZZA`), price, active flag, timestamps |
| **SimpleProduct** | Fixed-price item; may include veg/non-veg if applicable |
| **Combo** | Links to multiple Simple products; has catalog price (fixed vs derived — open question) |
| **Pizza** | Veg/non-veg; crust; crust size; toppings options / max count; customization rules |
| **CatalogVersion / DirtyFlag** | Marker updated on any catalog mutation; PDF job checks this (or a `lastCatalogChangeAt` vs `lastPdfGeneratedAt`) |
| **MenuPdfArtifact** | Stored PDF bytes or object path + generation timestamp |

**PDF job suggestion:** On Admin write → set `catalog_dirty = true` (or bump `catalog_version`). Every 5 minutes: if dirty (or version > last PDF version), regenerate PDF, store artifact, clear dirty / record version. If not dirty, no-op.

**Cache:** Redis caches product list/detail responses (and optionally JWT validation material per Design). Invalidate or bump cache keys on Admin writes.

---

## 8. Acceptance criteria (by major FR)

| Area | Acceptance |
|------|------------|
| **FR-1–4 Product types** | Admin can persist and retrieve Simple, Combo, and Pizza with type-specific fields; Pizza supports veg/non-veg and crust/size/toppings customization fields agreed in Design. |
| **FR-5 Admin CRUD** | With valid Admin JWT, create/update/delete succeed; without JWT or with Trusted-only JWT, writes return 401/403. |
| **FR-6 Dirty flag** | Any successful catalog mutation sets dirty/version so PDF job will regenerate within one 5-minute cycle. |
| **FR-7–11 Queries** | Trusted JWT can filter by veg/non-veg, type, maxPrice, and paginate; results match filters; unauthenticated → 401. |
| **FR-13–16 PDF** | Unauthenticated GET returns PDF; after catalog change, regenerated PDF appears within ~5 minutes without continuous busy work when idle. |
| **FR-17–20 Auth** | Register/login yields JWT; JWT expires at 30m; no revoke API required in v1. |
| **FR-21–22 Quality** | OpenAPI document imports into Postman; automated tests cover auth matrix, CRUD, filters, public PDF. |

---

## 9. Out of scope

- Orders, carts, checkout, payments, delivery, franchising
- **Live session / JWT invalidation** (revoke, denylist enforcement as a product requirement — Design may note a future denylist hook)
- Real-time PDF regeneration on every write (v1 is dirty + 5-minute async)
- Full OpenAPI/SQL as part of *this* Spec gate (sketch only here)
- Customer accounts required to view the menu PDF
- Loyalty, marketing CMS, POS sync

---

## 10. Open questions / decisions for Design

1. **JWT validation:** Redis-backed token store vs standard JWT signature/expiry verify now, with optional denylist later (brief invites industry-best practice suggestion).
2. **Pizza customization model:** option entities vs JSON config; how “customizable to taste” is exposed on read APIs vs admin-defined defaults.
3. **Combo pricing:** fixed Admin-set price vs sum of component Simple prices (or both: list price + components).
4. **Trusted-system registration:** same user table with roles vs separate client credentials; claim shape in JWT.
5. **Admin query access:** whether Admin JWT may call the same list/filter endpoints as Trusted (recommended yes for operability).
6. **PDF storage:** DB bytea vs filesystem/object store path; how public URL is served.
7. **Pagination defaults/max** and sort order for catalog list.
8. **Veg/non-veg on Simple/Combo:** required for all types or Pizza-only (+ optional on others).

---

## 11. Gate ask

Please review this Spec / PRD and reply with one of:

- **Approve** — accept Spec; proceed to Design / TRD draft
- **Revise: …** — tell us what to change in this document
- **Park** — pause Spec work

Prerequisite: Intent should be **Approve**d (or explicitly co-approved) before Spec is treated as accepted.

See the [gate checklist](hifl-playbook.md#gate-review-checklist-for-the-human) in the HIFL playbook.
