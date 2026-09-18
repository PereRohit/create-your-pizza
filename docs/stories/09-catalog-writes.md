# 09 — Catalog admin writes

**Status:** ready  
**Depends on:** `08-catalog-jwt-jwks.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As an admin, I want CRUD for products and pizza option entities so that the menu stays current, writes wait on PDF generation, and PDF version does not bump on save.

## Acceptance criteria

- [ ] `POST/PUT/DELETE /api/products` and `/api/options` require `catalog:write` **and** `ADMIN`
- [ ] Pizza body includes `optionsEnabled`; combo membership; combo price admin-set
- [ ] If Redis PDF-generation lock held → **503** envelope (`please try after sometime` / `system busy`), omit `data` and `pagination`, header `Retry-After: 60`
- [ ] Successful mutation: take write lock (`NX EX` `app.lock.write-ttl` default 30s), persist, `catalog_meta.dirty = true`, `DEL` lock in `finally`
- [ ] If write lock not acquired → 503
- [ ] Do **not** delete Redis catalog cache keys; do **not** insert `menu_pdf` or change `last_pdf_version`
- [ ] Trusted JWT cannot write (403)

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: CRUD; dirty on success; 503 when PDF lock; 503 when write lock busy; version unchanged; trusted 403; fake locks

## Notes

Unblocks **10** and **12**.
