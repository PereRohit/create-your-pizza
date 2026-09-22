# 16 — BUG: catalog Redis beans never wired

**Status:** done — closed 2026-09-22 on `fix/16-redis-bean-wiring` after full regression  
**Type:** bug (found at Stage 6 Verify, 2026-09-22)  
**Depends on:** `DONE-11-catalog-redis-cache.md`, `DONE-12-pdf-job-locks.md`, `DONE-13-public-pdf.md` (reopens their runtime behaviour, not their contracts)  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md) · [BUG-01 RCA](../bugs.md#bug-01--catalog-redis-beans-never-wired) · [Verify](../verify.md)

## Story

As the store operator, I want catalog-service to actually use Redis for its cache, latest menu, and PDF/write locks so that reads are shared across instances, the latest menu is served Redis-first, and write-vs-generate exclusion holds across the fleet instead of only inside one JVM.

## Defect

**Symptom:** on the live Compose stack, Redis `DBSIZE` stays **0** across a full read / write / PDF cycle. No `create-your-pizza/catalog:*`, no `create-your-pizza/menu`. Injecting `create-your-pizza/lock:pdf-generation` into Redis does not make an admin write return 503.

**Root cause:** the Redis-vs-in-memory choice uses `@ConditionalOnBean(StringRedisTemplate.class)` on `@Bean` methods inside application `@Configuration` classes. `@ConditionalOnBean` only sees definitions registered before it is evaluated, and Spring Boot processes user `@Configuration` **before** auto-configuration, so `StringRedisTemplate` (from `DataRedisAutoConfiguration`) does not exist yet. The Redis bean is skipped and the sibling `@ConditionalOnMissingBean` selects the in-memory bean. Those annotations are only safe on auto-configuration classes.

**Affected:** `CatalogCacheConfiguration`, `CatalogLockConfiguration`, `CatalogPdfConfiguration` — so `CatalogCacheStore`, `LatestMenuStore`, and `CatalogLockStore` all run from in-memory fallbacks. Spec **NFR-1 / FR-26**, **FR-16b–16d / FR-16f**, and Design §6 (`SET NX EX` + TTL self-heal) are unmet on the delivery stack. Auth-service is unaffected.

**Missed because:** `catalog-service/src/test/resources/application.properties` globally excludes `DataRedisAutoConfiguration`, so `StringRedisTemplate` exists in no test context and the in-memory branch is the only branch a context test can select. The Redis adapters are unit-tested in isolation against a mocked template, so their logic is sound but unreached.

## Acceptance criteria

- [x] When a `StringRedisTemplate` bean is present, catalog resolves `CatalogCacheStore` → `RedisCatalogCacheStore`, `LatestMenuStore` → `RedisLatestMenuStore`, `CatalogLockStore` → `RedisCatalogLockStore`, **independent of `@Configuration` processing order**
- [x] When no `StringRedisTemplate` is present (tests excluding `DataRedisAutoConfiguration`), all three still fall back to their in-memory implementations
- [x] `Clock` and `MenuPdfRenderer` selection in `CatalogPdfConfiguration` no longer depends on bean-registration order
- [x] No behaviour, key, or TTL change: `create-your-pizza/catalog:*` with TTL `app.cache.catalog-ttl` and **no** invalidation on write; `create-your-pizza/menu` with no TTL; `create-your-pizza/lock:pdf-generation` 120s and `create-your-pizza/lock:catalog-write` 30s from `app.lock.*`
- [x] Live Compose: an authenticated list GET creates `create-your-pizza/catalog:*`; a successful generate writes `create-your-pizza/menu`; an externally injected PDF-generation lock key makes an admin write return **503** + `Retry-After: 60`
- [x] Existing suites stay green (auth **62** tests, catalog **106** tests, 1 skipped each)

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: Redis implementation selected when a `StringRedisTemplate` bean exists; in-memory selected when it does not
- The selection regression test **must not** exclude `DataRedisAutoConfiguration` — that exclusion is what hid this defect. Lettuce connects lazily, so asserting bean types needs no live Redis.

## Tasks

- [x] Replace the `@ConditionalOnBean` / `@ConditionalOnMissingBean` pair in each of `CatalogCacheConfiguration`, `CatalogLockConfiguration`, `CatalogPdfConfiguration` with one order-independent bean per port (`ObjectProvider<StringRedisTemplate>` resolved at bean-creation time)
- [x] Harden the latent `Clock` / `MenuPdfRenderer` selections in `CatalogPdfConfiguration`
- [x] Add the bean-selection regression test with Redis auto-configuration enabled
- [x] `mvn -f catalog-service test` and `mvn -f auth-service test` green

## Notes

Branch: `fix/16-redis-bean-wiring`. Catalog-service only; no Spec or Design change — this restores behaviour those documents already lock.

Prefer framework wiring over hand-rolled selection (stories README coding preference): defer the lookup to bean-creation time rather than reimplementing a condition.

Live proof belongs to the Stage 6 Verify re-run, not to this story's `mvn test`.

Implementation note: every previously existing test still passes; catalog now reports **115** tests (1 skipped) because this fix adds 9: `CatalogStoreWiringTest` (4), `CatalogRedisWiringApplicationTest` (1), `RedisCatalogLockStoreTest` (4). Auth is untouched at **62** (1 skipped).

## Closure evidence (full regression, 2026-09-22)

Candid review loop: 1 pass, **Findings: none**. The reviewer independently confirmed the new guards fail against the pre-fix configuration and pass after it, so they are not vacuous.

Mocked suites: auth `Tests run: 62, Failures: 0, Errors: 0, Skipped: 1`; catalog `Tests run: 115, Failures: 0, Errors: 0, Skipped: 1` — both **BUILD SUCCESS**.

Live Compose on a fresh stack (`down -v` + `up --build`, TTL overrides `APP_JWT_TTL=1m`, `APP_PDF_INTERVAL=1m`, `APP_CACHE_CATALOG_TTL=30s`):

| Closure check | Result |
|---|---|
| Redis `DBSIZE` at boot | `0` |
| `create-your-pizza/catalog:*` after an authenticated list GET | **8 keys** (e.g. `create-your-pizza/catalog:list:\|\|\|1\|100`) |
| Cache key TTL vs `app.cache.catalog-ttl=30s` | **29s** |
| `create-your-pizza/menu` after a generate | **present**, no TTL, `version` matches DB `max(version)` |
| Delete menu key → `GET /api/menu.pdf` | **200** from DB, key **backfilled** (FR-16d / FR-16f) |
| Injected `create-your-pizza/lock:pdf-generation` → admin write | **503** + `Retry-After: 60`, envelope omits `data`/`pagination`, **no row written** (FR-16b) |
| Release injected lock → admin write | **201** |
| Injected `create-your-pizza/lock:catalog-write` held across a job interval | job **skipped**: version frozen, `dirty` preserved (FR-16c — live for the first time) |
| Release write lock → next tick | generated, version advanced, `dirty=false`, Redis menu version follows |
| No invalidation on write; TTL refresh after 30s | both hold, now from Redis (Design §7) |

The injected-lock check is the one that failed before this fix and is the direct proof the lock store is now Redis-backed rather than process-local.
