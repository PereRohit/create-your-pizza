# 12 — PDF job and Redis locks

**Status:** done  
**Depends on:** `09-catalog-writes.md`, `07-catalog-schema-seed.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As the store, I want a dirty-driven PDF job with Redis locks so that menus version only on successful generate and writers are not mixed with generation.

## Acceptance criteria

- [x] Interval from `app.pdf.interval` default 5 minutes
- [x] If write lock exists → **skip** (not queued); `dirty` unchanged
- [x] If `dirty=false` → no-op
- [x] `SET` PDF lock `NX EX app.lock.pdf-ttl` default 120s; fail to acquire → skip
- [x] Re-check write lock; if present → `DEL` PDF lock, skip
- [x] PDF: header **Create Your Pizza**; **vN**; sellable name+price; pizza `options_enabled` → note **options available**; pizza-spec in **own space** with option prices
- [x] Insert `menu_pdf`; Redis latest key `create-your-pizza/menu` JSON object with base64 `pdf`, integer `version`, UTC `updatedAt`; `dirty=false`; `last_pdf_version=version`
- [x] Version = last+1 or 1; increments **only** on successful insert
- [x] `DEL` PDF lock in `finally`
- [x] OpenPDF in `catalog-service/pom.xml`

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: skip on write lock; skip if not dirty; version only on generate; content notes; fake renderer + fake locks

## Tasks

- [x] Add OpenPDF to `catalog-service/pom.xml`
- [x] Job algorithm + Redis PDF lock per Design §6
- [x] Fake renderer in tests

## Notes

Unblocks **13** and **14**. Branch: `feat/12-pdf-job-locks`. Callable `PdfGenerationService.runOnce()` is the shared job (story **14** can reuse it). No public GET and no test trigger on this story.

Candid review: 1 pass, Findings: none.
