# Bugs — CreateYourPizza

Defect register. A bug lands here when it is found **after** the story that owns the behaviour is already `DONE-` — whatever stage surfaces it. Each entry holds the full RCA and links to its own ticket under [stories/](stories/README.md), which is closed exactly like any other ticket.

**Links:** [HIFL playbook](hifl-playbook.md) · [Stories](stories/README.md) · [Verify](verify.md) · [Handoff](handoff.md)

## Index

| ID | Title | Found | Severity | Ticket | Branch | Status | Resolved |
|----|-------|-------|----------|--------|--------|--------|----------|
| [BUG-01](#bug-01--catalog-redis-beans-never-wired) | Catalog Redis beans never wired | Stage 6 Verify · 2026-09-22 | **Blocking** | [DONE-16-fix-redis-wiring.md](stories/DONE-16-fix-redis-wiring.md) | `fix/16-redis-bean-wiring` | **closed** | 2026-09-22 |
| [BUG-02](#bug-02--static-openapi-omits-503-and-all-error-responses) | Static OpenAPI omits 503 and all error responses | Stage 6 Verify regression · 2026-09-22 | Docs contract; **no runtime impact** | [DONE-17-openapi-error-responses.md](stories/DONE-17-openapi-error-responses.md) | `fix/17-openapi-error-responses` | **closed** | 2026-09-23 |

This index mirrors the defect table in [verify.md](verify.md) §8. Both carry branch and resolution date; neither drops a closed row. Verify was approved on 2026-09-23 with **no open rows** in either.

## How a bug is closed

1. **RCA here** — fault, root cause, evidence, blast radius, why the tests missed it, recommended fix. The register entry is written before the ticket.
2. **Ticket under `stories/`** using the normal story template, numbered in sequence, on its own branch **`fix/<id>-<max-5-word-summary>`** — same as a story but with the `fix/` prefix instead of `feat/`. A bug ticket is not a special case otherwise: same template, same one-branch rule, same `DONE-` rename.
3. **Implement** the ticket's acceptance criteria and tasks, with unit tests at **>80% LoC** of changed code and full behaviour coverage.
4. **Candid review loop** per the [playbook](hifl-playbook.md#candid-review-loop) — fresh reviewer, then fresh fix agent, cap of 3 cycles — before the `DONE-` rename.
5. **Full regression**, not just the touched paths: both service suites (`mvn -f auth-service test`, `mvn -f catalog-service test`) **and** the Stage 6 live residual pack re-run end to end on a fresh Compose stack.
6. **Close:** rename the ticket to `DONE-…`, flip the row above to **closed** with the closing evidence, record the **fix branch** and **resolution date** in both this index and the [verify.md](verify.md) §8 defect table, then refresh the Verify evidence rows the regression re-ran and [handoff.md](handoff.md). Verify stays open while any row is open; closed rows are never deleted.

A bug is **not** closed by a passing unit test alone. The live evidence that first exposed it must itself pass — for BUG-01 that means real Redis keys and an externally injected lock producing a 503.

---

## BUG-01 — Catalog Redis beans never wired

| | |
|---|---|
| **Found** | Stage 6 Verify live residual pack, 2026-09-22 |
| **Severity** | Blocking — Spec acceptance unmet on the delivery stack |
| **Ticket** | [DONE-16-fix-redis-wiring.md](stories/DONE-16-fix-redis-wiring.md) |
| **Branch** | `fix/16-redis-bean-wiring` |
| **Status** | **closed 2026-09-22** — candid review clean, full regression passed (closure evidence table on the ticket) |
| **Regressed stories** | [DONE-11](stories/DONE-11-catalog-redis-cache.md), [DONE-12](stories/DONE-12-pdf-job-locks.md), [DONE-13](stories/DONE-13-public-pdf.md) — runtime behaviour, not their contracts |
| **Spec / Design clauses** | NFR-1, FR-26, FR-16b, FR-16c, FR-16d, FR-16f; Design §6 (`SET NX EX` + TTL self-heal), §7 (Redis keys) |

**Fault:** every Redis-backed port in catalog-service silently resolves to its in-memory fallback at startup. Redis `DBSIZE` stays **0** across a full read / write / PDF cycle — no `create-your-pizza/catalog:*`, no `create-your-pizza/menu` — and injecting `create-your-pizza/lock:pdf-generation` into Redis does not make an admin write return 503.

**Root cause:** the Redis-vs-in-memory choice is made with `@ConditionalOnBean(StringRedisTemplate.class)` on `@Bean` methods inside ordinary application `@Configuration` classes (`CatalogCacheConfiguration`, `CatalogLockConfiguration`, `CatalogPdfConfiguration`). `@ConditionalOnBean` only sees bean definitions registered **before** it is evaluated, and Spring Boot processes user `@Configuration` **before** auto-configuration. `StringRedisTemplate` is contributed later by `DataRedisAutoConfiguration`, so the condition correctly reports "no such bean", the Redis bean is skipped, and the sibling `@ConditionalOnMissingBean` then selects the in-memory bean. These annotations are only safe on auto-configuration classes; here they sit on application configuration.

**Evidence** — Spring Boot condition evaluation report from the running container (`DEBUG=true`), showing both halves of the ordering race:

```
DataRedisAutoConfiguration#stringRedisTemplate matched:
   - @ConditionalOnSingleCandidate ... found a single bean 'redisConnectionFactory'

CatalogLockConfiguration#redisCatalogLockStore:
   Did not match:
      - @ConditionalOnBean (types: ...StringRedisTemplate) did not find any beans of type ...StringRedisTemplate
```

Identical negative matches for `redisCatalogCacheStore` and `redisLatestMenuStore`; `inMemoryCatalogLockStore`, `inMemoryCatalogCacheStore`, and `inMemoryLatestMenuStore` all matched. Confirmed behaviourally: an identical repeated list query served a stale page for 30s while Redis `DBSIZE` stayed `0`.

**Blast radius**

| Port | Bean actually wired | Consequence |
|------|--------------------|-------------|
| Catalog cache | `InMemoryCatalogCacheStore` | Per-instance heap cache; lost on restart; not shared across replicas |
| Latest menu | `InMemoryLatestMenuStore` | No `create-your-pizza/menu`; latest PDF always falls through to `menu_pdf` |
| PDF / write locks | `InMemoryCatalogLockStore` | Mutual exclusion only inside one JVM; no Redis TTL self-heal after a crash |

Auth-service is unaffected (Design §3: auth does not use Redis). Cache TTL semantics themselves are **correct** — stale read on an identical query, refresh after TTL, no invalidation on write, all per Design §7. Only the backing store is wrong.

**Why the test suite missed it:** `catalog-service/src/test/resources/application.properties` globally excludes `DataRedisAutoConfiguration`, so `StringRedisTemplate` exists in **no** test context and the in-memory branch is the only branch a Spring context test can select — the defect is invisible by construction. The Redis adapters are unit-tested in isolation against a mocked template (`RedisCatalogCacheStoreTest`, `RedisLatestMenuStoreTest`), which is why their logic is sound but unreached. No test asserts **which** implementation is wired when Redis is present; `RedisCatalogLockStore` has no direct unit test.

**Recommended fix:** defer resolution to bean-creation time, after all definitions are registered, with `ObjectProvider` instead of a registration-order condition. One bean per port; contexts without Redis still get the in-memory implementation:

```java
@Bean
CatalogLockStore catalogLockStore(ObjectProvider<StringRedisTemplate> redis) {
    return redis.stream().findFirst()
        .<CatalogLockStore>map(RedisCatalogLockStore::new)
        .orElseGet(InMemoryCatalogLockStore::new);
}
```

Scope agreed with the owner for the fix ticket:

1. All three broken selections — lock, cache, latest-menu.
2. The latent `@ConditionalOnMissingBean` uses in `CatalogPdfConfiguration` (`Clock`, `MenuPdfRenderer`), which resolve correctly today but carry the same ordering fragility if anything ever overrides them.
3. Regression guard: a context test that does **not** exclude `DataRedisAutoConfiguration` and asserts the resolved bean types are the Redis ones, plus restoring the injected-lock-key check in [verify.md](verify.md) §3.5 as a live assertion.

**Fix as delivered (2026-09-22):** one `ObjectProvider<StringRedisTemplate>` bean per port in all three configuration classes; `Clock` and `MenuPdfRenderer` moved from `@ConditionalOnMissingBean` to `@Fallback`, which is order-independent and still overridable. Nine tests added — `CatalogStoreWiringTest` (4, `ApplicationContextRunner` with Redis auto-configuration supplied explicitly), `CatalogRedisWiringApplicationTest` (1, full context with the exclusion overridden per-test), `RedisCatalogLockStoreTest` (4, the adapter that previously had no direct test). Closure evidence: see the ticket. The live injected-lock check that first exposed the defect now returns **503** + `Retry-After: 60`, and FR-16c job-skip was demonstrated on a live stack for the first time.

---

## BUG-02 — Static OpenAPI omits 503 and all error responses

**Closed 2026-09-23** on `fix/17-openapi-error-responses`, after the candid review loop returned **Findings: none** on its third cycle and the full regression passed — both suites plus the entire Stage 6 live residual pack on a fresh stack. Closing evidence at the end of this entry.

| | |
|---|---|
| **Found** | Stage 6 Verify full-regression run for BUG-01, 2026-09-22 |
| **Severity** | Breached a locked delivery requirement, but **no runtime impact** — the services behaved correctly, only the published contract was incomplete. Fixed rather than carried as a residual. |
| **Ticket** | [DONE-17-openapi-error-responses.md](stories/DONE-17-openapi-error-responses.md) |
| **Branch** | `fix/17-openapi-error-responses` |
| **Status** | **closed 2026-09-23** — candid review clean on cycle 3, full regression passed (closing evidence below; per-cycle detail on the ticket) |
| **Regressed story** | [DONE-15](stories/DONE-15-openapi-agents.md) — an acceptance criterion was ticked but not satisfied; now satisfied and machine-checked |
| **Spec / Design clauses** | Design §9 (static YAML/JSON is the import contract and must cover **503** and **PDF binary**); Spec FR-21, NFR-4, NFR-12 |

**Fault:** the committed integrator contract under `docs/openapi/` documents **only** HTTP 200. Across both services, every one of the 20 operations declares a single `200` response — there is no `503`, `401`, `403`, or `404` anywhere. Separately, `GET /api/menu.pdf` declares its 200 content as `*/*` rather than `application/pdf`, so the raw-binary nature of the response is not expressed.

**Evidence** — collecting every response code across `docs/openapi/*.json`:

```
503 documented on: NONE
all response codes seen: ['200']

GET    /api/menu.pdf   responses=['200'] content=['200:*/*'] params=[('version','query')]
POST   /api/products   responses=['200'] content=['200:*/*'] params=[]
```

**Why it matters:** Design §9 states the committed static files are the Postman/import contract and enumerates what they must cover — "envelope, pagination, **503**, **PDF binary** + `version`, auth approve/revoke, JWKS" — and marks it **Hard (Build delivery)**. Spec NFR-4 requires the OpenAPI to stay consistent with the implemented endpoints, and NFR-12 locks the 503 busy shape (omits `data` and `pagination`). An integrator importing these files gets no signal that catalog writes can return 503 with `Retry-After: 60`, which is precisely the concurrency behaviour v1 is built around. Envelope and `Pagination` schemas **are** present, and the `version` query parameter **is** documented, so the gap is specifically error responses plus the PDF media type.

**Blast radius:** documentation only. No service behaviour is wrong — the live regression confirmed the real 503 envelope, `Retry-After: 60`, and raw `application/pdf` responses all work. The risk is integrators coding against an incomplete contract.

**Why it was missed:** [DONE-15](stories/DONE-15-openapi-agents.md) ticks "Document envelope, pagination, 503, PDF binary + `version` query, auth approve/revoke, JWKS (in static specs)" as an acceptance criterion, but that story is marked `N/A` for tests — nothing machine-checked the generated files against the clause, and the exporter emits SpringDoc's defaults (bare `200`, `*/*`) unless controllers carry explicit response annotations. The earlier Verify draft then recorded "503 busy documented on catalog — **PASS**" from a spot check that matched the string `503` somewhere in the file rather than asserting it as a documented response on an operation. The BUG-01 regression re-ran that check as a real assertion and it failed.

**Recommended fix** — written at RCA time; ticket 17 carries all four:

1. Annotate the controllers (or a shared `@ControllerAdvice`/OpenAPI customiser) so the exporter emits the error responses each operation can actually return: `503` + `Retry-After` on catalog writes, plus `401`/`403`/`404` where they apply, all referencing the existing envelope schema with `data`/`pagination` omitted for 503.
2. Declare `GET /api/menu.pdf` 200 content as `application/pdf` with `format: binary`.
3. Regenerate with `./scripts/generate-openapi.sh` (Docker) and commit `docs/openapi/*`.
4. Add a **machine check** so this cannot silently regress — a test asserting the exported spec documents 503 on catalog writes and `application/pdf` on the menu endpoint. This is the missing guard that let a ticked AC be false.

**Scope extended by the owner — 2026-09-23.** Implementing ticket 17 surfaced two further inaccuracies in the same published files, with the same root cause as the fault above: SpringDoc's defaults were never overridden, so the contract states things the services do not do. The owner authorised repairing both on the ticket-17 branch rather than raising a separate defect, so they are closed against this entry:

1. `POST /auth/register`, `POST /auth/admins`, `POST /api/products`, and `POST /api/options` documented success as **200** while the controllers answer **201**.
2. Every JSON operation documented its response body as `*/*`, because no controller declares `produces`; they now read `application/json`.

Both breach the same Spec **NFR-4** clause already cited above — the OpenAPI must stay consistent with the implemented endpoints.

Ticket 17 is handled as a normal ticket (own `fix/` branch, ACs, tasks, tests, candid review loop) **plus** the [bug closure criteria](#how-a-bug-is-closed) above.

**Fix as delivered (2026-09-23).** SpringDoc is test-scoped, so `io.swagger` annotations cannot go on the controllers without putting it on the runtime classpath and breaking the static-files-only rule. The contract is therefore declared as one `OpenApiCustomizer` per service beside the existing export configuration — `CatalogErrorResponsesCustomizer` and `AuthErrorResponsesCustomizer` — each contributing the shared error responses as reusable `components.responses` plus the binary `application/pdf` body on the menu endpoint. Twenty-one tests were added to auth (62 → 83) and 23 to catalog (115 → 138).

One trap is worth recording, because the first regeneration on the branch shipped broken and the first version of the new guard did not notice. Swagger's `removeBrokenReferenceDefinitions` keeps only the component schemas something references **at the moment it runs**, and SpringDoc runs it **before** the customisers. An `ApiEnvelope` declared on the `OpenAPI` bean is pruned, and the customiser's references then dangle — catalog silently *lost* a schema it had at `HEAD`, and auth, which never had one, gained five dangling refs. The envelope is now registered inside each customiser, next to its only referrers. `Pagination` deliberately stays on the bean: SpringDoc's generated `ApiEnvelope*` schemas reference it, so it survives the prune with its hand-written descriptions intact.

**Closing evidence** — both suites plus the entire Stage 6 residual pack, re-run end to end on a fresh stack (`down -v --remove-orphans`, `up -d --build`) with `APP_JWT_TTL=1m`, `APP_PDF_INTERVAL=1m`, `APP_CACHE_CATALOG_TTL=30s`. Ready in 13s; first PDF ~74s.

| Requirement | Result |
|-------------|--------|
| Every service's mocked suite | auth **83** / 1 skipped, catalog **138** / 1 skipped, both BUILD SUCCESS |
| Entire live residual pack, fresh stack | **120 checks, 0 failures** |
| **The evidence that first exposed the defect** | The §3.4 check, re-run as a real assertion: **503 documented on all six catalog writes**, `ServiceBusy` declaring `Retry-After`, the 503 example omitting `data`/`pagination` per NFR-12, and `GET /api/menu.pdf` published as binary `application/pdf` |
| Contract resolves | 58 `$ref`s in auth and 61 in catalog, **0 dangling**, in both the JSON and the YAML |
| Live behaviour matches the document | Injected PDF lock → **503** + `Retry-After: 60`, envelope without `data`/`pagination`, no row written; menu served as raw `%PDF-` bytes; no live `/v3/api-docs` or Swagger UI on either service |
| No regression | Every row green at BUG-01 closure passed again, including the FR-16c job skip across a held write lock (version frozen at v1, `dirty` preserved, generate on release → v2) |
