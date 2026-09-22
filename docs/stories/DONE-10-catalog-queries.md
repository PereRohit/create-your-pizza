# 10 — Catalog queries

**Status:** done  
**Depends on:** `09-catalog-writes.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As an admin or trusted system, I want the same list and get APIs with filters and pagination so that I can read sellable products and pizza-spec options as a flat array.

## Acceptance criteria

- [x] `GET /api/products` requires `catalog:read` (Admin or Trusted)
- [x] Query: `category` veg|non-veg; `type` simple|combo|pizza-base|pizza-spec; `maxPrice` (price **<** X on products and options); `page`/`size` default 10 max 100 clamp still 200
- [x] Untyped list is a flat union including pizza-spec; `type=pizza-spec` is all options
- [x] pizza-spec wire: `productId`, `productName`, `productType=pizza-spec`, `productPrice` = option price, `optionKind`, `isBase`; no empty `productCategory`
- [x] pizza-base includes `optionsEnabled`; `customisationNotes` when non-empty
- [x] Default sort `created_at` ascending; filter takes precedence
- [x] `category` excludes pizza-spec unless `type=pizza-spec` (then ignore category)
- [x] Envelope + `pagination` (`current`, `next` or **-1**, `total` matching items)
- [x] `GET /api/products/{id}` sellable only; `GET /api/options/{id}` one option; no `pagination` on get-by-id
- [x] Unauthenticated 401

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: filters, clamp, pizza-spec, admin and trusted same reads, pagination next=-1

## Tasks

- [x] Catalog query APIs per acceptance criteria
- [x] After ACs + candid review of code, before `DONE-` rename: run [Build plan §5.1](../build-plan.md) **Catalog read-path service-ready** Compose smoke; record result in Notes / handoff. If smoke fails, do **not** rename to `DONE-`.

## Notes

Unblocks **11**. Branch: `feat/10-catalog-queries`. `catalog-service/Dockerfile` + Compose build on port 8081; smoke script `scripts/catalog-read-path-smoke.sh`.

Candid review: 1 pass, Findings: none.

**§5.1 Catalog read-path service-ready smoke: PASS** (2026-09-22) — admin login + trusted token from auth; `GET /api/products` Bearer succeeds (envelope + pagination); missing/invalid JWT → 401; trusted write → 403.
