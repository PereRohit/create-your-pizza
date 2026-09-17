# Intent — CreateYourPizza pizza catalog

**Status:** APPROVED — 2026-09-17 (owner HIFL Approve). Intent gate passed; next stage is Spec.

**Source of truth for this revision:** [internal/intent-revise-2026-09-17-b.md](../internal/intent-revise-2026-09-17-b.md) (owner HIFL Revise, second). Prior revise: [internal/intent-revise-2026-09-17.md](../internal/intent-revise-2026-09-17.md). Prior brief: [internal/product-idea.md](../internal/product-idea.md).

**Related:** [HIFL playbook](hifl-playbook.md) · [Project context](project-context.md) · [Spec (draft)](spec.md)

---

## Problem

A pizza delivery store needs one maintained product catalog that covers simple items, combos, and customizable pizzas. Staff must keep prices and offerings current; customers need an open printable PDF menu; trusted integrated systems need filtered, paginated catalog APIs. Without clear product-type rules (including pizza-base vs pizza-spec in the consumer API), veg/non-veg on all sellable types, public vs authenticated boundaries with identity-bearing JWTs, and a read-heavy PDF strategy (DB+version + Redis) with safe concurrency, menus drift and integrations stay unsafe or ad hoc.

## Who it's for

| Who | Need |
|-----|------|
| **Admin (staff)** | Maintain the catalog: prices, simple products, combos, pizzas, and pizza option catalogs |
| **Public customer** | Open the menu card as PDF **without** authentication |
| **Trusted registered system** | Consume catalog REST APIs (with filters and pagination) after authenticating via central auth |
| **Future customer (provisioned, not built in v1)** | Register/authenticate via the same **generic central auth** when order flow is integrated later |

## Desired outcomes / success criteria

- Admins can create, update, and delete catalog entries for **Simple**, **Combo**, and **Pizza** products under admin authorization.
- Product rules are honored:
  - **Simple** = fixed-price sellable items
  - **Combo** = combination of simple products with an **admin-set catalog price** (not the sum of component simples)
  - **Pizza** = customizable with documented crust sizes, crust types, toppings (olive base), and free-text non-chargeable customisations (detail in Spec)
  - **Veg / non-veg** applies to **Simple, Combo, and Pizza** (all three types)
- Anyone can download/view the **public PDF menu card** with no login. PDF is basic: header **Create Your Pizza**; table rows of **item name + base price** only.
- PDF artifacts are stored **in the database with versioning**. On generation/update, the current menu is also written to **Redis** so customers can fetch the current menu efficiently. Dirty/5-min job + concurrency rules from the prior revise remain in force.
- A **registered trusted system** can query the catalog via REST with filters: veg/non-veg, product type, price under Rs. X, and pagination (**default page size 10**). Consumer listing product types: **simple**, **combo**, **pizza-base**, **pizza-spec**. Default presentation: results **grouped by type**, sorted in **creation order** within groups; if a **filter is specified, the filter takes precedence** over default grouping/presentation.
- Callers authenticate via a **generic, extensible central auth service** that issues a **JWT with 30-minute TTL**. Claims carry **user/system identity** and **scope/permissions**. Admin JWTs include **admin user id**. External/trusted systems may authenticate with API key + API secret exchanged for a JWT (identity maps to claims such as `sub` / `client_id` — industry practice detailed in Spec). Customer auth is **provisioned for later**, not implemented in v1. Live-session invalidation is out of scope.
- Catalog APIs validate JWTs via **industry-standard local signature verification** (and expiry/claims checks) so the catalog service does **not** call auth just to validate tokens. **Redis** remains the **catalog read cache** (and current-menu PDF fetch path), not a required token store.
- Catalog data lives in **Postgres**; **Redis** serves as the read-heavy catalog cache (including current PDF).
- PDF generation runs **asynchronously every 5 minutes**, and **only when the catalog is dirty** (add / update / delete), with **DB-level concurrency** rules: lock during generation (admin writes → **HTTP 503** retry); if a catalog save is in progress when the job fires, the job **skips** that cycle.
- Stack is **Java Spring Boot + Maven**. Owner scaffolds via **Spring Initializr**; suggested Initializr dependencies are a **Build-stage** task (not scaffolding now).
- Runtime is **fully dockerized**: Postgres + Redis with **volume persistence** and **sample data** per product type; one command (e.g. `docker compose up`) brings the stack up.
- Deliverables include **OpenAPI / Swagger** (integration list + Postman import), **AGENTS.md** (for agent-assisted clone/setup), and **automated tests**.
- v1 stays catalog + public PDF + secured admin/catalog APIs (no orders, payments, or delivery).

## In scope for v1

Incorporated from the owner brief and HIFL Revises (first + second):

- Catalog of three admin product types: **Simple**, **Combo**, **Pizza**
- **Veg / non-veg** on **Simple, Combo, and Pizza**
- Consumer API listing types: **simple**, **combo**, **pizza-base**, **pizza-spec** (pizza base products vs specification/option catalog items as distinct types)
- **Pizza option catalog** (documented in Spec; not only PDF): crust sizes (10 / 12 / 15 inch), crust types (thin base, cheese burst, deep dish), toppings (chicken, mushrooms, pepperoni, **olive base**), free-text **non-chargeable** customisations
- Admin maintenance of products, prices, and combos; **combo price = admin-defined**, not derived sum
- Public, unauthenticated **PDF menu card** — header **Create Your Pizza**; tabular **name + base price**
- PDF **stored in DB with versioning**; on generate/update also refresh **Redis** for efficient current-menu fetch; dirty/5-min + lock/skip concurrency preserved
- **Generic extensible central auth** + **JWT (30m TTL)** with identity + scope/permissions claims; admin user id on admin tokens; trusted systems may use key+secret → JWT; customer auth **provisioned**, not built now
- Trusted-system catalog queries: veg/non-veg, type, price under Rs. X, pagination (default **10** per page); default **group by type** + creation order; **filter overrides** default presentation
- **Postgres** primary store (including versioned PDF) + **Redis** catalog cache / current PDF fetch
- JWT: **local signature verification** preferred (standards over Redis-as-token-store)
- Async PDF job: **5-minute** interval, dirty-flag / only-when-updated, with **DB lock / skip** concurrency rules
- **Java Spring Boot + Maven**; Initializr by owner; dependency suggestions at Build
- **Docker Compose**: Postgres + Redis volumes + sample data (Simple, Combo, Pizza); full stack one-command bring-up
- **OpenAPI / Swagger**, **AGENTS.md**, and **test cases** as delivery artifacts
- HIFL documentation path: Intent → Spec → Design → Build plan → Build → Verify

## Out of scope / non-goals

- Orders, carts, checkout
- Payments or invoicing
- Delivery tracking / logistics
- Multi-store / franchising
- **Implementing customer register/login** in v1 (auth service must remain **extensible** for it)
- **Invalidating live user sessions / JWT revocation** at this stage
- Full marketing site, loyalty, or POS integration
- Application / Spring Boot implementation before Design + Build plan approval
- Writing suggested Spring Initializr dependency lists before Build stage
- Creating **AGENTS.md** file before Build (document as planned artifact only until then)

## Constraints

- **Auth:** generic extensible central auth; JWT TTL **30 minutes**; claims include **identity** (admin user id; trusted `sub`/`client_id` after key+secret exchange) and **scope/permissions**; catalog validates JWT **locally** (signature + claims/expiry); no auth round-trip for validation; session invalidation out of scope; customer auth provisioned for later; **API secrets stay out of the JWT** (used only at token issuance)
- **Public surface:** PDF menu card requires **no** authentication; layout constrained to header **Create Your Pizza** and name + base price table
- **Protected surfaces:** admin catalog writes and trusted-system catalog APIs require valid JWT at the appropriate level
- **Storage:** Postgres (products + user information + **versioned PDF**) and Redis (**catalog** cache + **current menu PDF** fetch path)
- **PDF:** async, fixed **5-minute** cadence, only when catalog actually changed; store in DB with versioning and update Redis on generate; concurrency per Spec product decision (DB lock → 503; skip if save in progress)
- **Consumer API:** default page size **10**; types **simple / combo / pizza-base / pizza-spec**; group by type + creation order unless filter takes precedence
- **Veg/non-veg:** required concept on **all** product types (Simple, Combo, Pizza)
- **Stack:** **Java Spring Boot with Maven** (locked); owner creates project via Spring Initializr
- **Ops:** entire system dockerized for clone + `docker compose up`-style bring-up; Postgres/Redis volumes + sample data for each product type
- **Quality bar:** OpenAPI/Swagger + automated tests + AGENTS.md are success criteria / delivery artifacts
- **Process:** HIFL gates; no application code before Design and Build plan are approved

## Open ideas (truly unresolved only)

- Exact pizza option persistence / API shape (entities vs config) — Spec lists options; Design decides schema
- Exact JWT claim names / scope vocabulary beyond industry-practice suggestion in Spec — Design
- DB lock mechanics (advisory vs row vs status table) and 503 retry headers — Design
- How catalog obtains auth signing public key/secret at runtime — Design

## Risks / unknowns

- Pizza option catalog + free-text customisations can expand API/data model if Design under-specifies
- Dirty-flag / PDF job races at the 5-minute boundary — mitigated by locked concurrency rules (DB lock / skip), still needs careful Design
- Cache invalidation on admin writes must stay consistent with Redis catalog + Redis current PDF + DB PDF version
- Extensible auth without building customer flows now requires clear principal/role/scope seams in Design
- Consumer API dual type model (admin Pizza vs consumer pizza-base / pizza-spec) needs clear Design mapping
- Sample data + full dockerize must stay aligned with product types and seed expectations

## Approved — do not edit without new Revise gate

Intent is **APPROVED** (2026-09-17). Do **not** edit this document without a new owner HIFL **Revise** gate. Next stage: **Spec** ([spec.md](spec.md)).
