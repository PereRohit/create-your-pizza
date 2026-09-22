# 09 — Catalog admin writes

**Status:** done  
**Depends on:** `08-catalog-jwt-jwks.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As an admin, I want CRUD for products and pizza option entities so that the menu stays current, writes wait on PDF generation, and PDF version does not bump on save.

## Acceptance criteria

- [x] `POST/PUT/DELETE /api/products` and `/api/options` require `catalog:write` **and** `ADMIN`
- [x] Pizza body includes `optionsEnabled`; combo membership; combo price admin-set
- [x] If Redis PDF-generation lock held → **503** envelope (`please try after sometime` / `system busy`), omit `data` and `pagination`, header `Retry-After: 60`
- [x] Successful mutation: take write lock (`NX EX` `app.lock.write-ttl` default 30s), persist, `catalog_meta.dirty = true`, `DEL` lock in `finally`
- [x] If write lock not acquired → 503
- [x] Do **not** delete Redis catalog cache keys; do **not** insert `menu_pdf` or change `last_pdf_version`
- [x] Trusted JWT cannot write (403)

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: CRUD; dirty on success; 503 when PDF lock; 503 when write lock busy; version unchanged; trusted 403; fake locks

## Tasks

- [x] Product/option write controllers + `CatalogWriteService` (lock → TX → dirty)
- [x] Redis/`CatalogLockStore` port + in-memory fake for tests
- [x] Security: writes require `SCOPE_catalog:write` **and** `ROLE_ADMIN` (roles claim mapped)
- [x] Behaviour tests (MockMvc + fake locks)
- [x] Candid review loop (1 fix cycle: lock-vs-commit ordering + combo DELETE coverage; second pass Findings: none)

## Notes

Unblocks **10** and **12**. Branch: `feat/09-catalog-writes`.
