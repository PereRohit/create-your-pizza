# 11 — Catalog Redis cache

**Status:** ready  
**Depends on:** `10-catalog-queries.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As a catalog reader, I want list/detail JSON served Redis-first with a 3-minute TTL so that reads stay cheap without invalidation on write.

## Acceptance criteria

- [ ] Keys `create-your-pizza/catalog:*`
- [ ] Redis-first; on miss load DB then write Redis
- [ ] TTL from `app.cache.catalog-ttl` default 3 minutes
- [ ] Admin writes do **not** delete these keys
- [ ] Fake/map adapter in tests; real Redis only via Compose

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: hit, miss+fill, TTL from config, no invalidation on write

## Notes

Catalog-service only.
