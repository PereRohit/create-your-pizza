# 11 — Catalog Redis cache

**Status:** done  
**Depends on:** `10-catalog-queries.md` (`DONE-` includes §5.1 catalog read-path smoke)  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As a catalog reader, I want list/detail JSON served Redis-first with a 3-minute TTL so that reads stay cheap without invalidation on write.

## Acceptance criteria

- [x] Keys `create-your-pizza/catalog:*`
- [x] Redis-first; on miss load DB then write Redis
- [x] TTL from `app.cache.catalog-ttl` default 3 minutes
- [x] Admin writes do **not** delete these keys
- [x] Fake/map adapter in tests; real Redis only via Compose

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: hit, miss+fill, TTL from config, no invalidation on write

## Tasks

- [x] Catalog cache port + Redis / in-memory adapters
- [x] Redis-first list/get; no write-path invalidation
- [x] Behaviour tests (hit, miss+fill, TTL, no invalidation)

## Notes

Catalog-service only. Branch: `feat/11-catalog-redis-cache`. No §5.1 Compose smoke on this story (that gate passed on **10**).

Candid review: 1 pass, Findings: none.
