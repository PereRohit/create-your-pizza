# Intent — CreateYourPizza pizza catalog

**Status:** DRAFT — revised after owner HIFL Revise (2026-09-17); awaiting Intent gate

**Source of truth for this revision:** [internal/intent-revise-2026-09-17.md](../internal/intent-revise-2026-09-17.md) (owner HIFL Revise). Prior brief: [internal/product-idea.md](../internal/product-idea.md).

**Related:** [HIFL playbook](hifl-playbook.md) · [Project context](project-context.md) · [Spec (draft)](spec.md)

---

## Problem

A pizza delivery store needs one maintained product catalog that covers simple items, combos, and customizable pizzas (veg / non-veg). Staff must keep prices and offerings current; customers need an open printable PDF menu; trusted integrated systems need filtered, paginated catalog APIs. Without clear product-type rules, public vs authenticated boundaries, pizza option catalogs, and a read-heavy cache/PDF strategy with safe concurrency, menus drift and integrations stay unsafe or ad hoc.

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
  - **Pizza** = veg/non-veg with documented crust sizes, crust types, toppings (olive base), and free-text non-chargeable customisations (detail in Spec)
- Anyone can download/view the **public PDF menu card** with no login. PDF is basic: header **Create Your Pizza**; table rows of **item name + base price** only.
- A **registered trusted system** can query the catalog via REST with filters: veg/non-veg, product type (simple / combo / pizza), price under Rs. X, and page size / pagination.
- Callers authenticate via a **generic, extensible central auth service** that issues a **JWT with 30-minute TTL**. Customer auth is **provisioned for later**, not implemented in v1. Live-session invalidation is out of scope.
- Catalog APIs validate JWTs via **industry-standard local signature verification** (and expiry/claims checks) so the catalog service does **not** call auth just to validate tokens. **Redis** remains the **catalog read cache**, not a required token store.
- Catalog data lives in **Postgres**; **Redis** serves as the read-heavy catalog cache.
- PDF generation runs **asynchronously every 5 minutes**, and **only when the catalog is dirty** (add / update / delete), with **DB-level concurrency** rules: lock during generation (admin writes → **HTTP 503** retry); if a catalog save is in progress when the job fires, the job **skips** that cycle.
- Stack is **Java Spring Boot + Maven**. Owner scaffolds via **Spring Initializr**; suggested Initializr dependencies are a **Build-stage** task (not scaffolding now).
- Runtime is **fully dockerized**: Postgres + Redis with **volume persistence** and **sample data** per product type; one command (e.g. `docker compose up`) brings the stack up.
- Deliverables include **OpenAPI / Swagger** (integration list + Postman import), **AGENTS.md** (for agent-assisted clone/setup), and **automated tests**.
- v1 stays catalog + public PDF + secured admin/catalog APIs (no orders, payments, or delivery).

## In scope for v1

Incorporated from the owner brief and HIFL Revise:

- Catalog of three product types: **Simple**, **Combo**, **Pizza**
- Pizza categories: **vegetarian** and **non-vegetarian**
- **Pizza option catalog** (documented in Spec; not only PDF): crust sizes (10 / 12 / 15 inch), crust types (thin base, cheese burst, deep dish), toppings (chicken, mushrooms, pepperoni, **olive base**), free-text **non-chargeable** customisations
- Admin maintenance of products, prices, and combos; **combo price = admin-defined**, not derived sum
- Public, unauthenticated **PDF menu card** — header **Create Your Pizza**; tabular **name + base price**
- **Generic extensible central auth** + **JWT (30m TTL)** for admin and trusted-system API access; customer auth **provisioned**, not built now
- Trusted-system catalog queries: veg/non-veg, type, price under Rs. X, pagination
- **Postgres** primary store + **Redis** catalog cache (read-heavy)
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

- **Auth:** generic extensible central auth; JWT TTL **30 minutes**; catalog validates JWT **locally** (signature + claims/expiry); no auth round-trip for validation; session invalidation out of scope; customer auth provisioned for later
- **Public surface:** PDF menu card requires **no** authentication; layout constrained to header **Create Your Pizza** and name + base price table
- **Protected surfaces:** admin catalog writes and trusted-system catalog APIs require valid JWT at the appropriate level
- **Storage:** Postgres (products + user information) and Redis (**catalog** cache only for this Intent’s locked Redis role)
- **PDF:** async, fixed **5-minute** cadence, only when catalog actually changed; concurrency per Spec product decision (DB lock → 503; skip if save in progress)
- **Stack:** **Java Spring Boot with Maven** (locked); owner creates project via Spring Initializr
- **Ops:** entire system dockerized for clone + `docker compose up`-style bring-up; Postgres/Redis volumes + sample data for each product type
- **Quality bar:** OpenAPI/Swagger + automated tests + AGENTS.md are success criteria / delivery artifacts
- **Process:** HIFL gates; no application code before Design and Build plan are approved

## Open ideas (truly unresolved only)

- Exact pizza option persistence / API shape (entities vs config) — Spec lists options; Design decides schema
- Trusted-system registration details / claim shape in JWT — Design
- PDF storage medium (DB vs filesystem/object path) — Design
- Pagination defaults/max and sort order — Design
- Whether veg/non-veg applies to Simple/Combo or Pizza-only — Design (Pizza required)

## Risks / unknowns

- Pizza option catalog + free-text customisations can expand API/data model if Design under-specifies
- Dirty-flag / PDF job races at the 5-minute boundary — mitigated by locked concurrency rules (DB lock / skip), still needs careful Design
- Cache invalidation on admin writes must stay consistent with Redis + PDF dirty flag
- Extensible auth without building customer flows now requires clear principal/role seams in Design
- Sample data + full dockerize must stay aligned with product types and seed expectations

## Gate ask

Please review this Intent and reply with one of:

- **Approve** — accept Intent; Spec draft may proceed to Spec gate (Spec already revised in parallel for review after Intent Approve)
- **Revise: …** — tell us what to change in this document
- **Park** — pause Intent work

See the [gate checklist](hifl-playbook.md#gate-review-checklist-for-the-human) in the HIFL playbook.
