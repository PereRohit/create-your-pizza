# Intent — ThinkWithMe pizza catalog

**Status:** DRAFT — awaiting Intent gate

**Related:** [HIFL playbook](hifl-playbook.md) · [Project context](project-context.md)

---

## Problem

Pizza store operators need a reliable product catalog: staff can maintain items, customers can see a clean printable menu, and partner/external systems can read the catalog securely. Without a shared catalog and clear auth boundaries, menus drift, PDF exports are ad hoc, and integrations are unsafe or manual.

## Who it's for

| Who | Need |
|-----|------|
| **Admin** | Create, edit, and delete catalog items so the menu stays current |
| **Customer** | View items presented as a PDF menu card suitable for reading/printing |
| **External system** | Machine-to-machine read access to the catalog using an API key |

## Desired outcomes / success criteria

- Admins can fully manage catalog items (create / edit / delete) under ADMIN auth.
- Customers can obtain a PDF menu card reflecting current catalog items.
- An authorized external caller with a valid API key can access catalog data (EXTERNAL / M2M).
- Unauthorized callers cannot perform admin writes or external catalog access.
- v1 stays limited to catalog + PDF + external read API (no orders/payments/delivery/franchising).
- Decisions and scope are captured in Context docs so later Spec/Design can proceed without rediscovering Intent.

## In scope for v1

- Product catalog for a pizza store (items with fields to be detailed in Spec)
- Admin CRUD for catalog items
- Customer-facing PDF menu card generation/view
- External catalog access via API key (authorized M2M)
- Auth with multiple levels: at least **ADMIN**, **CUSTOMER**, **EXTERNAL**
- REST-oriented API surface (shape finalized in Spec/Design)
- Documentation under HIFL (this Intent → Spec → Design → Build plan → Build → Verify)

## Out of scope / non-goals

- Orders, carts, or checkout
- Payments or invoicing
- Delivery tracking or logistics
- Multi-store / franchising
- Full marketing site, loyalty, or POS integration (unless later promoted from Open ideas)
- Implementing the Spring Boot application before Design + Build plan approval

## Constraints

- **Auth levels:** at least ADMIN, CUSTOMER, EXTERNAL
- **External access:** API key for authorized M2M catalog access
- **Customer delivery format:** PDF menu card
- **Process:** HIFL gates; no application code before Design and Build plan are approved
- **Stack (future Build decision, not this stage):** Java Spring Boot 3, Spring Security, JPA, OpenPDF, REST — provisional until Design / Build-plan gates ([project-context](project-context.md))

## Open ideas (pending owner input)

> Owner indicated more ideas will be shared once setup is done. Capture them here; do not treat as in-scope until promoted and Intent/Spec are revised and re-gated.

- _(empty — awaiting owner input)_

## Risks / unknowns

- Exact catalog item schema (sizes, toppings, prices, allergens, images) not yet specified
- Whether CUSTOMER PDF access is public, account-gated, or otherwise
- API key lifecycle (issuance, rotation, revocation, scoping to read-only)
- Single-store assumption vs future multi-location (franchising is out of v1)
- PDF layout/branding expectations for the menu card
- Open ideas may expand or reshape v1 after owner input — Intent may need Revise

## Gate ask

Please review this Intent and reply with one of:

- **Approve** — accept Intent; proceed to Spec draft
- **Revise: …** — tell us what to change in this document
- **Park** — pause Intent work

See the [gate checklist](hifl-playbook.md#gate-review-checklist-for-the-human) in the HIFL playbook.
