# 10 — Catalog queries

**Status:** ready  
**Depends on:** `09-catalog-writes.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As an admin or trusted system, I want the same list and get APIs with filters and pagination so that I can read sellable products and pizza-spec options as a flat array.

## Acceptance criteria

- [ ] `GET /api/products` requires `catalog:read` (Admin or Trusted)
- [ ] Query: `category` veg|non-veg; `type` simple|combo|pizza-base|pizza-spec; `maxPrice` (price **<** X on products and options); `page`/`size` default 10 max 100 clamp still 200
- [ ] Untyped list is a flat union including pizza-spec; `type=pizza-spec` is all options
- [ ] pizza-spec wire: `productId`, `productName`, `productType=pizza-spec`, `productPrice` = option price, `optionKind`, `isBase`; no empty `productCategory`
- [ ] pizza-base includes `optionsEnabled`; `customisationNotes` when non-empty
- [ ] Default sort `created_at` ascending; filter takes precedence
- [ ] `category` excludes pizza-spec unless `type=pizza-spec` (then ignore category)
- [ ] Envelope + `pagination` (`current`, `next` or **-1**, `total` matching items)
- [ ] `GET /api/products/{id}` sellable only; `GET /api/options/{id}` one option; no `pagination` on get-by-id
- [ ] Unauthenticated 401

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: filters, clamp, pizza-spec, admin and trusted same reads, pagination next=-1

## Notes

Unblocks **11**.
