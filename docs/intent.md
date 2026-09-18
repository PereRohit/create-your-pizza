# Intent — CreateYourPizza pizza catalog

**Status:** APPROVED — 2026-09-17 (owner HIFL Approve). Intent gate passed; next stage is Spec.

**Status note:** Intent is APPROVED. Product decisions live in this file, [spec.md](spec.md), and [project-context.md](project-context.md).

**Related:** [HIFL playbook](hifl-playbook.md) · [Project context](project-context.md) · [Spec (APPROVED)](spec.md) · [Handoff](handoff.md)

---

## Problem

A pizza delivery store needs one maintained product catalog that covers simple items, combos, and customizable pizzas. Staff must keep prices and offerings current; customers need an open printable PDF menu; trusted integrated systems need filtered, paginated catalog APIs. Without clear product-type rules (including pizza-base vs pizza-spec in the consumer API), veg/non-veg on all sellable types, public vs authenticated boundaries with identity-bearing JWTs, and a read-heavy PDF strategy (DB+version + Redis) with safe concurrency, menus drift and integrations stay unsafe or ad hoc.

## Who it's for

| Who | Need |
|-----|------|
| **Admin (staff)** | Maintain the catalog; **login** with username/password; approve/deny/revoke trusted systems; list users |
| **Public customer** | Open the menu card as PDF **without** authentication |
| **Trusted registered system** | Register (pending) → admin approve → API key+secret → JWT via `/auth/token`; then same catalog **read** APIs as admin |
| **Future customer (provisioned, not built in v1)** | Register/authenticate via the same **generic central auth** when order flow is integrated later |

## Desired outcomes / success criteria

- Admins can create, update, and delete catalog entries for **Simple**, **Combo**, and **Pizza** products under admin authorization.
- Product rules are honored:
  - **Simple** = fixed-price sellable items
  - **Combo** = combination of simple products with an **admin-set catalog price** (not the sum of component simples)
  - **Pizza** = customizable with documented crust sizes, crust types, toppings (olive base), and free-text non-chargeable customisations (detail in Spec)
  - **Veg / non-veg** applies to **Simple, Combo, and Pizza** (all three types)
- Anyone can download/view the **public PDF menu card** with no login. PDF is basic: header **Create Your Pizza**; printed **vN**; table rows of **item name + base price** for sellable products **and pizza-spec options**. Default GET is latest (raw binary); optional numeric `version` query for history.
- PDF artifacts are stored **in the database as version history** (version bumps **only when a PDF is generated**, not on each product write). **Redis holds the latest menu only**. Dirty job + Redis locks: admin writes during generation → 503; if a write is in progress the job **skips** (not queued).
- A **registered trusted system** (and **Admin** on the same query APIs) can query the catalog via REST with filters: veg/non-veg, product type, price under Rs. X, and pagination (**default page size 10**). Consumer listing product types: **simple**, **combo**, **pizza-base**, **pizza-spec**. Wire format: products as a **flat array** (Spec); sorted in **creation order** by default; if a **filter is specified, the filter takes precedence**.
- Callers authenticate via a **generic, extensible central auth service** that issues a **JWT with 30-minute TTL**. **Admins login** (username/password). **Trusted systems do not login**; they get an API key **only after admin approval**, then **`/auth/token`** → JWT so catalog uses **one JWT path**. First admin is **bootstrapped** on empty admin table (credentials printed to the terminal). Customer auth is **provisioned for later** (self-register, no approval, visible to admins). Live **JWT denylist** is out of scope; **credential revoke** for trusted systems is in v1.
- Catalog APIs validate JWTs via **local signature verification** using **JWKS from auth over HTTP** (one DB per service; catalog does **not** read auth-postgres and does **not** call `/validate`). **Redis** is catalog cache + latest PDF + **locks**.
- Catalog data lives in **Postgres**; **Redis** serves as the read-heavy catalog cache (including current PDF).
- PDF generation runs **asynchronously** (interval from config, default 5 minutes), **only when dirty**, with Redis locks: admin writes → **HTTP 503**; job **skips** if a write is in progress (not queued).
- Stack is **Java Spring Boot + Maven**. Owner scaffolds via **Spring Initializr**; suggested Initializr dependencies are a **Build-stage** task (not scaffolding now).
- Runtime is **fully dockerized**: Postgres + Redis with **volume persistence** and **sample data** per product type; one command (e.g. `docker compose up`) brings the stack up.
- Deliverables include **OpenAPI / Swagger** (integration list + Postman import), **AGENTS.md** (for agent-assisted clone/setup), and **automated tests**.
- v1 stays catalog + public PDF + secured admin/catalog APIs (no orders, payments, or delivery).

## In scope for v1

Incorporated from the owner brief and HIFL Revises (first + second):

- Catalog of three admin product types: **Simple**, **Combo**, **Pizza**
- **Veg / non-veg** on **Simple, Combo, and Pizza**
- Consumer API listing types: **simple**, **combo**, **pizza-base**, **pizza-spec** (pizza base products vs specification/option catalog items as distinct types)
- Pizza option catalog as **option entities** (Spec): three `kind`s only; **each option has its own price**; free-text customisations **non-chargeable**
- Admin maintenance of products, prices, and combos; **combo price = admin-defined**, not derived sum; Admin may use **same** catalog query APIs as Trusted
- Public, unauthenticated **PDF menu card** — header **Create Your Pizza**; **vN**; tabular **name + base price** including **pizza-spec**
- PDF **history in DB**; Redis **latest only**; GET default latest raw binary; `?version=` numeric for past (DB)
- **Generic extensible central auth**; admin login; trusted pending+approve+token; bootstrap first admin; JWT 30m; customer auth **provisioned** (no approval later)
- Catalog queries: veg/non-veg, type, price under Rs. X, pagination (default **10** per page); **flat array** response; **filter overrides** default presentation
- **Postgres** primary store (including versioned PDF) + **Redis** catalog cache / current PDF fetch
- JWT: **local signature verification** preferred (standards over Redis-as-token-store); verify-key material per Spec
- Async PDF job: **5-minute** interval, dirty-flag / only-when-updated, with concurrency rules in Spec
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
- **JWT denylist / invalidating outstanding JWTs** (trusted **credential revoke** is in v1)
- Full marketing site, loyalty, or POS integration
- Application / Spring Boot implementation before Design + Build plan approval
- Writing suggested Spring Initializr dependency lists before Build stage
- Creating **AGENTS.md** file before Build (document as planned artifact only until then)

## Constraints

- **Auth:** generic extensible central auth; JWT TTL **30 minutes**; admin **login**; trusted **register → admin approve → `/auth/token`**; catalog validates JWT **locally**; JWT denylist out of scope; trusted credential revoke in v1; customer auth provisioned (no approval); **API secrets stay out of the JWT**
- **Public surface:** PDF menu card requires **no** authentication; header **Create Your Pizza**, **vN**, name + base price (including pizza-spec); optional `version` query
- **Protected surfaces:** admin catalog writes and trusted-system catalog APIs require valid JWT at the appropriate level
- **Storage:** Postgres (products + user information + **versioned PDF**) and Redis (**catalog** cache + **current menu PDF** fetch path)
- **PDF:** async, dirty-only; DB **history** (version from **generation**, not writes); Redis **latest**; writes 503 during gen; job **skips** if write in progress (not queued); lock TTLs 120s / 30s
- **Consumer API:** default page size **10**; types **simple / combo / pizza-base / pizza-spec**; **flat array** wire format + creation order unless filter takes precedence (Spec)
- **Veg/non-veg:** required concept on **all** product types (Simple, Combo, Pizza)
- **Stack:** **Java Spring Boot with Maven** (locked); owner creates project via Spring Initializr
- **Ops:** entire system dockerized for clone + `docker compose up`-style bring-up; Postgres/Redis volumes + sample data for each product type
- **Quality bar:** OpenAPI/Swagger + automated tests + AGENTS.md are success criteria / delivery artifacts
- **Process:** HIFL gates; no application code before Design and Build plan are approved

## Open ideas (truly unresolved only)

Product decisions are locked in Spec + Design DRAFT (2026-09-18 Design Revise). No remaining Intent open ideas.

## Risks / unknowns

- Pizza option catalog + free-text customisations can expand API/data model if Design under-specifies
- Dirty-flag / PDF job races at the 5-minute boundary — mitigated by `pdf_generation` lock + 503
- Catalog Redis TTL (3 min) means readers can be briefly stale vs Postgres after admin writes
- Extensible auth without building customer flows now requires clear principal/role/scope seams in Design
- Consumer API dual type model (admin Pizza vs consumer pizza-base / pizza-spec) needs clear Design mapping
- Sample data + full dockerize must stay aligned with product types and seed expectations

## Approved — do not edit without new Revise gate

Intent is **APPROVED** (2026-09-17). Light consistency edits **2026-09-18** for owner Design Revise. Spec is **APPROVED** (aligned same date). Design is **DRAFT** ([design.md](design.md)).
