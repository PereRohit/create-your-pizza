# 11 — PDF job and Redis locks

**Status:** ready  
**Depends on:** `08-catalog-writes.md`, `06-catalog-schema-seed.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As the store, I want a dirty-driven PDF job with Redis locks so that menus version only on successful generate and writers are not mixed with generation.

## Acceptance criteria

- [ ] Interval from `app.pdf.interval` default 5 minutes
- [ ] If write lock exists → **skip** (not queued); `dirty` unchanged
- [ ] If `dirty=false` → no-op
- [ ] `SET` PDF lock `NX EX app.lock.pdf-ttl` default 120s; fail to acquire → skip
- [ ] Re-check write lock; if present → `DEL` PDF lock, skip
- [ ] PDF: header **Create Your Pizza**; **vN**; sellable name+price; pizza `options_enabled` → note **options available**; pizza-spec in **own space** with option prices
- [ ] Insert `menu_pdf`; Redis latest key `create-your-pizza/menu` JSON object with base64 `pdf`, integer `version`, UTC `updatedAt`; `dirty=false`; `last_pdf_version=version`
- [ ] Version = last+1 or 1; increments **only** on successful insert
- [ ] `DEL` PDF lock in `finally`
- [ ] OpenPDF in `catalog-service/pom.xml`

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: skip on write lock; skip if not dirty; version only on generate; content notes; fake renderer + fake locks

## Notes

Unblocks **12** and **13**.
