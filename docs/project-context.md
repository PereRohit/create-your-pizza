# Project context — CreateYourPizza

Durable product decisions for the CreateYourPizza pizza store catalog. Update this file when the owner locks a decision; do not treat chat alone as source of truth.

**Related:** [HIFL playbook](hifl-playbook.md) · [Intent](intent.md) · [Spec](spec.md) · Owner brief [internal/product-idea.md](../internal/product-idea.md)

## Project name

**CreateYourPizza** — pizza store product catalog (create-your-pizza / catalog + public PDF + trusted APIs).

## Problem statement

A pizza delivery store needs one catalog of **Simple**, **Combo**, and **Pizza** products that admins maintain; customers open a **public unauthenticated PDF** menu card; and **trusted registered systems** consume filtered, paginated catalog REST APIs after central-auth JWT login — without orders, payments, delivery, or live-session revocation in v1.

## Actors and capabilities

| Actor | Capability (v1) |
|-------|-----------------|
| **Admin** | Maintain catalog: products, prices, combos, pizzas (CRUD) with Admin JWT |
| **Public customer** | Open/download PDF menu card — **no authentication** |
| **Trusted registered system** | Authenticate via central auth; query catalog with filters + pagination |

## Locked facts (from owner brief, 2026-09-17)

| Fact | Detail |
|------|--------|
| Product types | **Simple** (fixed price), **Combo** (combination of simples), **Pizza** (veg/non-veg; crust, size, toppings; customizable) |
| Public PDF | Menu card PDF is open; no auth required |
| Admin | Updates prices, defines combos, adds/maintains products |
| Trusted APIs | Catalog REST with filters: veg/non-veg, type, price under Rs. X, pagination |
| Auth | Central auth; **JWT 30m TTL**; **session invalidation out of scope** |
| Storage | **Postgres** (products + users) + **Redis** (read-heavy cache; JWT validation approach open) |
| PDF job | Async every **5 minutes**, **only when catalog dirty** |
| Quality | **OpenAPI** + **automated tests** required |
| Process | HIFL gates; no application code before Design + Build plan Approve |

## Auth principals (v1)

- **ADMIN** — catalog write operations
- **TRUSTED_SYSTEM** (registered client) — catalog read/query APIs with JWT
- **PUBLIC** — PDF menu only (no account required for menu)

Former Intent mention of a distinct CUSTOMER principal for PDF access is **superseded**: PDF is public. Customer accounts for ordering remain out of scope.

## Non-goals for v1

- Orders / checkout / payments / delivery / franchising
- Invalidating live user sessions / JWT revocation as a product feature
- Application implementation before Design + Build plan approval

## Tech direction (provisional — Design / Build-plan gates)

**Not implemented yet.** Direction from earlier setup remains provisional; owner brief locks **Postgres**, **Redis**, **JWT**, **OpenAPI**, **tests**, and async PDF behavior — not a specific framework in the brief.

Do not start application code until Design and Build plan are approved. Design/TRD must not be drafted until Spec is Approved.

## Decision log

| Date | Decision | Status |
|------|----------|--------|
| 2026-09-17 | Product is a pizza store product catalog (CreateYourPizza) | Locked |
| 2026-09-17 | Product types: Simple, Combo, Pizza (veg/non-veg; crust, size, toppings customizable) | Locked (owner brief) |
| 2026-09-17 | Admin maintains catalog (prices, combos, products) | Locked |
| 2026-09-17 | PDF menu card is **public / unauthenticated** | Locked (owner brief; supersedes ambiguous CUSTOMER PDF auth) |
| 2026-09-17 | Trusted registered system consumes catalog REST with filters + pagination | Locked (owner brief; **supersedes API-key-only M2M**) |
| 2026-09-17 | Central auth issues **JWT 30m TTL**; live session invalidation **out of scope** | Locked (owner brief) |
| 2026-09-17 | Postgres + Redis (read-heavy cache); Redis-for-JWT validation is a Design suggestion, not locked | Locked storage; JWT-in-Redis **open** |
| 2026-09-17 | Async PDF every 5 min only when catalog dirty | Locked (owner brief) |
| 2026-09-17 | OpenAPI + tests are success criteria | Locked (owner brief) |
| 2026-09-17 | v1 excludes orders, payments, delivery, franchising | Locked |
| 2026-09-17 | HIFL Stages 1–6; no code before Design + Build plan | Locked (process) |
| 2026-09-17 | Owner brief captured in `internal/product-idea.md`; Intent + Spec revised from brief | Locked (process) |
| 2026-09-17 | Former “open ideas awaiting owner input” promoted into Intent/Spec scope | Locked |
| 2026-09-17 | Earlier provisional Spring Boot 3 / Security / JPA / OpenPDF stack | Provisional until Design / Build-plan gates |

## Document map

| Doc | Role |
|-----|------|
| [hifl-playbook.md](hifl-playbook.md) | Process: stages, gates, handoffs |
| [intent.md](intent.md) | Stage 1 Intent (revised from owner brief) |
| [spec.md](spec.md) | Stage 2 Spec / PRD draft |
| [internal/product-idea.md](../internal/product-idea.md) | Owner product brief (source for Intent/Spec revision) |
| This file | Durable decisions and decision log |
| `design.md` | Stage 3 — **do not write until Spec Approve** |
