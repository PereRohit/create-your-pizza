# Project context — ThinkWithMe

Durable product decisions for the ThinkWithMe pizza store catalog. Update this file when the owner locks a decision; do not treat chat alone as source of truth.

**Related:** [HIFL playbook](hifl-playbook.md) · [Intent](intent.md)

## Project name

**ThinkWithMe** — pizza store product catalog.

## Problem statement

A pizza store needs a single catalog of menu items that admins can manage, customers can view as a printable PDF menu card, and authorized external systems can read via an API key — without building orders, payments, or delivery in v1.

## Actors and capabilities

| Actor | Capability (v1) |
|-------|-----------------|
| **Admin** | Create, edit, and delete catalog items |
| **Customer** | View catalog items as a PDF menu card |
| **External system** | Access the catalog via API key (authorized machine-to-machine) |

## Auth principals

At least three levels / principal types:

- **ADMIN** — catalog write operations (create / edit / delete)
- **CUSTOMER** — customer-facing catalog/PDF access as designed for that role
- **EXTERNAL** — M2M access authenticated with an API key

Exact endpoints, token vs session mechanics, and key lifecycle are Spec/Design concerns; the principal set above is locked for Intent.

## Non-goals for v1

Keep v1 to **catalog + PDF menu + external read API**. Explicitly out of scope for v1:

- Orders / checkout
- Payments
- Delivery tracking
- Multi-store franchising

## Tech direction (provisional — Build stage)

**Not implemented yet.** Confirmed as the intended stack for the future Build stage; final lock happens at Design / Build-plan gates per the [HIFL playbook](hifl-playbook.md):

- Java Spring Boot 3
- Spring Security
- JPA
- OpenPDF
- REST APIs

Do not start application code until Design and Build plan are approved.

## Decision log

| Date | Decision | Status |
|------|----------|--------|
| 2026-09-17 | Product is a pizza store product catalog (ThinkWithMe) | Locked |
| 2026-09-17 | Admin can create / edit / delete catalog items | Locked |
| 2026-09-17 | Customer views items as a PDF menu card | Locked |
| 2026-09-17 | External systems access catalog via API key (authorized M2M) | Locked |
| 2026-09-17 | Auth principals include at least ADMIN, CUSTOMER, EXTERNAL | Locked |
| 2026-09-17 | v1 excludes orders, payments, delivery tracking, multi-store franchising | Locked |
| 2026-09-17 | Intended Build stack: Spring Boot 3, Spring Security, JPA, OpenPDF, REST | Provisional until Design gate |
| 2026-09-17 | HIFL Stages 1–6 with Approve / Revise / Park gates; no code before Design + Build plan | Locked (process) |
| 2026-09-17 | Owner has more ideas to share after setup — tracked in Intent “Open ideas” | Locked (process) |

## Document map

| Doc | Role |
|-----|------|
| [hifl-playbook.md](hifl-playbook.md) | Process: stages, gates, handoffs |
| [intent.md](intent.md) | Stage 1 Intent draft |
| This file | Durable decisions and decision log |
