# Intent — CreateYourPizza pizza catalog

**Status:** DRAFT — revised after owner product brief; awaiting Intent gate

**Source of truth for this revision:** [internal/product-idea.md](../internal/product-idea.md) (owner brief, Aidev Tool10, 2026-09-17)

**Related:** [HIFL playbook](hifl-playbook.md) · [Project context](project-context.md) · [Spec (draft)](spec.md)

---

## Problem

A pizza delivery store needs one maintained product catalog that covers simple items, combos, and customizable pizzas (veg / non-veg). Staff must keep prices and offerings current; customers need an open printable PDF menu; trusted integrated systems need filtered, paginated catalog APIs. Without clear product-type rules, public vs authenticated boundaries, and a read-heavy cache/PDF strategy, menus drift and integrations stay unsafe or ad hoc.

## Who it's for

| Who | Need |
|-----|------|
| **Admin (staff)** | Maintain the catalog: prices, simple products, combos, and pizzas |
| **Public customer** | Open the menu card as PDF **without** authentication |
| **Trusted registered system** | Consume catalog REST APIs (with filters and pagination) after authenticating via central auth |

## Desired outcomes / success criteria

- Admins can create, update, and delete catalog entries for **Simple**, **Combo**, and **Pizza** products under admin authorization.
- Product rules are honored: Simple = fixed-price sellable items; Combo = combination of simple products; Pizza = veg/non-veg with predefined crust, crust size, and toppings count, customizable to taste (detail in Spec).
- Anyone can download/view the **public PDF menu card** with no login.
- A **registered trusted system** can query the catalog via REST with filters: veg/non-veg, product type (simple / combo / pizza), price under Rs. X, and page size / pagination.
- Callers authenticate via a **central auth service** that issues a **JWT with 30-minute TTL** for subsequent API calls; live-session invalidation is out of scope.
- Catalog data lives in **Postgres**; **Redis** serves as the read-heavy cache layer (and may support JWT validation patterns decided in Design).
- PDF generation runs **asynchronously every 5 minutes**, and **only when the catalog is dirty** (add / update / delete).
- Deliverables include **OpenAPI (Swagger)** suitable for Postman/import and **automated tests** for the system and APIs.
- v1 stays catalog + public PDF + secured admin/catalog APIs (no orders, payments, or delivery).

## In scope for v1

Incorporated from the owner brief (former vague “open ideas” now promoted):

- Catalog of three product types: **Simple**, **Combo**, **Pizza**
- Pizza categories: **vegetarian** and **non-vegetarian**; pizzas defined with crust, crust size, toppings (customizable)
- Admin maintenance of products, prices, and combos
- Public, unauthenticated **PDF menu card**
- Central auth + **JWT (30m TTL)** for admin and trusted-system API access
- Trusted-system catalog queries: veg/non-veg, type, price under Rs. X, pagination
- **Postgres** primary store + **Redis** cache (read-heavy)
- Async PDF job: **5-minute interval**, dirty-flag / only-when-updated
- **OpenAPI** spec and **test cases** for system/APIs
- HIFL documentation path: Intent → Spec → Design → Build plan → Build → Verify

## Out of scope / non-goals

- Orders, carts, checkout
- Payments or invoicing
- Delivery tracking / logistics
- Multi-store / franchising
- **Invalidating live user sessions / JWT revocation** at this stage
- Full marketing site, loyalty, or POS integration
- Application / Spring Boot implementation before Design + Build plan approval

## Constraints

- **Auth:** central auth service; JWT TTL **30 minutes**; session invalidation out of scope
- **Public surface:** PDF menu card requires **no** authentication
- **Protected surfaces:** admin catalog writes and trusted-system catalog APIs require valid JWT at the appropriate level
- **Storage:** Postgres (products + user information) and Redis (cache; JWT validation approach open for Design)
- **PDF:** async, fixed **5-minute** cadence, only when catalog actually changed
- **Quality bar:** OpenAPI + automated tests are success criteria, not optional extras
- **Process:** HIFL gates; no application code before Design and Build plan are approved
- **Stack:** provisional for later Design / Build-plan gates (see [project-context](project-context.md)); not locked by this Intent

## Open ideas (truly unresolved only)

- Exact pizza customization persistence model (how crust / size / toppings options are stored and exposed) — Spec outlines; Design decides schema
- JWT validation via Redis vs standard JWT signature verify (+ optional denylist later) — Design decision
- PDF layout / branding expectations beyond “menu card reflecting catalog”
- Whether Combo pricing is fixed list price vs derived sum of simples — Design/Spec open question

## Risks / unknowns

- Pizza customization complexity can expand API and data model if under-specified
- Dirty-flag / PDF job race conditions if catalog updates are frequent near the 5-minute boundary
- Cache invalidation strategy on admin writes must stay consistent with Redis + PDF dirty flag
- Trusted-system registration / client onboarding flow details not fully specified in the brief
- Stack remains provisional until Design / Build-plan gates

## Gate ask

Please review this Intent and reply with one of:

- **Approve** — accept Intent; Spec draft may proceed to Spec gate (Spec already drafted in parallel for review after Intent Approve)
- **Revise: …** — tell us what to change in this document
- **Park** — pause Intent work

See the [gate checklist](hifl-playbook.md#gate-review-checklist-for-the-human) in the HIFL playbook.
