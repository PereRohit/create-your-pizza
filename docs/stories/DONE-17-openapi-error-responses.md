# 17 — BUG: static OpenAPI omits 503 and all error responses

**Status:** **DONE** 2026-09-23 on `fix/17-openapi-error-responses` — candid review loop clean on cycle 3 (**Findings: none**) after two fix cycles, and the bug-closure full regression passed (**120/120** live checks, both suites green). Closure evidence below.  
**Type:** bug (found at Stage 6 Verify full-regression run, 2026-09-22)  
**Depends on:** `DONE-15-openapi-agents.md` (reopens one of its acceptance criteria)  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md) · [BUG-02 RCA](../bugs.md#bug-02--static-openapi-omits-503-and-all-error-responses)

## Story

As an integrator importing the published contract into Postman, I want the documented operations to include the error responses they can really return — above all the **503 busy** envelope on catalog writes — so that I can code against concurrency behaviour without discovering it in production.

## Defect

**Symptom:** every one of the 20 operations in `docs/openapi/auth-service.{yaml,json}` and `docs/openapi/catalog-service.{yaml,json}` declares exactly one response, `200`. No `503`, `401`, `403`, or `404` appears anywhere. `GET /api/menu.pdf` declares its 200 content as `*/*` instead of `application/pdf`.

**Breaches:** Design §9 marks the committed static files **Hard (Build delivery)** and enumerates what they must cover — "envelope, pagination, **503**, **PDF binary** + `version`, auth approve/revoke, JWKS". Spec **FR-21** makes them the integration API list, **NFR-4** requires consistency with implemented endpoints, and **NFR-12** locks the 503 shape (omits `data` and `pagination`).

**No runtime impact.** The live regression confirmed the services really do return the 503 envelope with `Retry-After: 60` and really do serve raw `application/pdf`. Only the published contract is incomplete. `Pagination` is present in both files and `?version=` is documented, so the gap is specifically error responses plus the PDF media type — with one detail the RCA's catalog-side baseline did not cover: the `ApiEnvelope` schema exists only in `catalog-service.*`, so auth-service has to gain it before its error responses can reference it.

**Missed because:** [DONE-15](DONE-15-openapi-agents.md) ticks the "document … 503 …" criterion but is marked `N/A` for tests, so nothing machine-checked the exported files against the clause; SpringDoc emits bare `200` / `*/*` unless controllers declare responses. The earlier Verify draft recorded a PASS from a substring spot check rather than a real assertion.

Full RCA: [BUG-02](../bugs.md#bug-02--static-openapi-omits-503-and-all-error-responses).

## Acceptance criteria

- [x] Catalog write operations (`POST`/`PUT`/`DELETE` on `/api/products` and `/api/options`) document **503** referencing the standard envelope, with `data` and `pagination` absent and the `Retry-After` header described
- [x] Operations document the auth failures they can return: **401** unauthenticated, **403** for trusted-vs-admin write attempts and admin-only reads, **404** where applicable (unknown id, unknown `?version=`)
- [x] `GET /api/menu.pdf` documents its 200 content as `application/pdf` with binary format, and keeps the `version` query parameter
- [x] Envelope and `Pagination` schemas keep their existing definitions byte for byte, and nothing is renamed or removed — auth-service additionally *gains* the `ApiEnvelope` definition, which was missing there, because its new error responses reference it
- [x] No live Swagger UI and no live `/v3/api-docs` — the static-files-only rule stays intact
- [x] Regenerated `docs/openapi/*` with `./scripts/generate-openapi.sh`; all four files parse and every `$ref` in them resolves (0 dangling of any kind)
- [x] Existing suites stay green — auth **83**, catalog **138**, 1 skipped each (were 62 / 115; the ticket adds 21 / 23)

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed — `CatalogErrorResponsesCustomizerTest` (11) and `AuthErrorResponsesCustomizerTest` (11) drive every branch of the two customisers against a stand-in for SpringDoc's output. That stand-in hands over components with the unreferenced schemas **already pruned**, as the real exporter does, so the envelope registration is exercised rather than assumed.
- A **machine check on the exported specification** — the guard whose absence let a ticked criterion be false. `StaticOpenApiContractTest` in each service (catalog **12**, auth **10**) reads the **committed** `docs/openapi/*` and fails if 503 is missing from a catalog write, if the menu endpoint stops declaring `application/pdf`, if any operation loses 401/403/404, if either service's creating `POST`s stop publishing **201** (`/auth/register`, `/auth/admins`, `/api/products`, `/api/options`), if the YAML and JSON disagree, or if **any** `$ref` anywhere in either file fails to resolve — schema references included, not only `#/components/responses/…` ones, and the envelope assertions now read through to the schema instead of matching the `$ref` string. Verified red three times, each against the guard as it stood then: on the pre-fix Verify-era files (catalog 8 of 10 failing, auth 6 of 9); on the first regeneration of this branch, where the error responses referenced a pruned `ApiEnvelope` (2 of 11 failing on catalog, 2 of 10 on auth); and on the committed catalog files with both POST 201s flipped back to 200, which failed the new 201 assertion and nothing else (1 of 12).
- Full **behaviour** coverage of the criteria above

## Tasks

- [x] Declare the error responses on the controllers (or via a shared `@ControllerAdvice` / OpenAPI customiser) so the exporter emits them — prefer one shared declaration over repeating annotations on every method
- [x] Declare the menu PDF response as `application/pdf` binary
- [x] Add the exported-spec assertion test
- [x] Regenerate with `./scripts/generate-openapi.sh` (**Docker required**) and commit `docs/openapi/*`
- [x] `mvn -f catalog-service test` and `mvn -f auth-service test` green

## Notes

Branch: `fix/17-openapi-error-responses`. Both services are in scope; catalog carries the 503 and the PDF media type, auth carries 401/403/404/409.

No Spec or Design change — this restores a contract those documents already lock. No product scope, no new endpoints.

**Where the declaration lives.** SpringDoc is a **test-scoped** dependency (Build plan / Design §9: no runtime Swagger), so `io.swagger` annotations cannot go on the controllers without putting SpringDoc on the runtime classpath. The contract is therefore declared where the rest of it already lives — the export configuration — as one `OpenApiCustomizer` per service (`CatalogErrorResponsesCustomizer`, `AuthErrorResponsesCustomizer`). That satisfies "one shared declaration over repeating annotations" and keeps the static-files-only rule intact. The pre-existing `ServiceBusy` component moved from `OpenApiExportConfig` into the catalog customiser so the whole error contract sits in one class.

**Why the customiser also registers `ApiEnvelope`.** Swagger's `removeBrokenReferenceDefinitions` keeps only the component schemas something references at that moment, and SpringDoc runs it **before** the customisers. An envelope declared on the `OpenAPI` bean is therefore pruned — the customiser's references arrive too late to protect it — and every error response ships a dangling `$ref`. The first regeneration on this branch did exactly that, in both services. The schema now lives next to its only referrers, in the customiser, so it is present when the file is written. `Pagination` stays on the `OpenAPI` bean: SpringDoc's generated `ApiEnvelope*` schemas reference it, so it survives the prune with its hand-written descriptions intact.

**Two further contract inaccuracies corrected in the same pass**, both the same SpringDoc-default cause and both covered by the NFR-4 clause this ticket already cites:

1. `POST /auth/register`, `POST /auth/admins`, `POST /api/products`, `POST /api/options` documented **200** while the controllers answer **201**.
2. Every JSON operation documented its body as `*/*` because no controller declares `produces`; they now read `application/json`.

The first of those falls outside the fault BUG-02 was raised for, so it is not covered by this ticket's own scope fence. The owner **authorised** carrying it here on 2026-09-23 rather than raising a separate defect; both are recorded under [Scope extended by the owner](../bugs.md#bug-02--static-openapi-omits-503-and-all-error-responses) in the register entry.

**Observed, deliberately not changed:** `auth-service.yaml|json` declares the `bearerAdminJwt` security scheme but no operation references it (catalog has a global `security` entry). That is an absent declaration rather than a false one, it is outside this ticket's acceptance criteria, and it is left for the owner to schedule.

Handled as a **normal ticket** (own branch, ACs, tasks, tests, candid review loop) **plus** the [bug closure criteria](../bugs.md#how-a-bug-is-closed): full regression — both suites and the entire live residual pack on a fresh stack — including the live 503 and raw-PDF checks that prove the documented behaviour matches reality.

## Candid review loop

| Cycle | Outcome |
|-------|---------|
| 1 | 4 findings. The published files carried **10 dangling `$ref`s** — every error response pointed at `ApiEnvelope`, which Swagger's `removeBrokenReferenceDefinitions` had pruned before the customisers ran, and catalog *lost* a schema it had at `HEAD`. The guard did not notice, because it resolved only `#/components/responses/…`. Fixed by registering the envelope inside each customiser and by making the guard resolve every pointer in the document. |
| 2 | 3 findings. Catalog's published-file guard never asserted the **201** on its two `POST`s (auth guarded its two); a note explaining the envelope move was attached as Javadoc to `paginationSchema()`, which it does not describe; and the BUG-02 register entry still read "no fix attempted / branch not created" beside a note describing work on that branch. All three applied. |
| 3 | **Findings: none.** |

## Closure evidence — 2026-09-23

Per the [bug closure criteria](../bugs.md#how-a-bug-is-closed): both service suites **and** the entire Stage 6 live residual pack, re-run end to end on a fresh stack (`docker compose down -v --remove-orphans` then `up -d --build`) with the Verify TTL overrides `APP_JWT_TTL=1m`, `APP_PDF_INTERVAL=1m`, `APP_CACHE_CATALOG_TTL=30s`. Stack ready in **13s**; first PDF at ~**74s**.

| Closure requirement | Result |
|---------------------|--------|
| Every service's mocked suite | auth **83** tests / 1 skipped, catalog **138** / 1 skipped, both **BUILD SUCCESS** |
| Entire live residual pack on a fresh stack (§3.1–§3.6) | **120 checks, 0 failures** |
| **The evidence that first exposed the defect, now passing** | **503 documented on all six catalog writes**; `ServiceBusy` declares the `Retry-After` header; the 503 example omits `data` and `pagination` (NFR-12); `GET /api/menu.pdf` published as binary `application/pdf`. Re-run as a real assertion, not a substring match |
| Published contract resolves | **58 `$ref`s in auth, 61 in catalog, 0 dangling** of any kind, in both the JSON and the YAML |
| Documented behaviour matches the live system | Injected `create-your-pizza/lock:pdf-generation` → **503** + `Retry-After: 60`, envelope without `data`/`pagination`, **no row written**; release → **201**. `GET /api/menu.pdf` served as raw `application/pdf` beginning `%PDF-` |
| Static-files-only rule intact | `/v3/api-docs`, `/v3/api-docs.yaml`, `/swagger-ui.html`, `/swagger-ui/index.html` all **404** on **both** services |
| No regression in previously green checks | Every §3.1–§3.6 row that passed at BUG-01 closure passed again, including the FR-16c job-skip across a held write lock (v1 frozen, `dirty` preserved, generate on release → v2) |

**Harness note for whoever re-runs this.** The residual pack must mint a **fresh** admin JWT per authenticated call: under `APP_JWT_TTL=1m` a token reused across a multi-minute run silently starts returning 401 and shows up as unrelated failures such as "dirty was not set". Spring's default `JwtTimestampValidator` also allows **60s** of clock skew, so an expiry check must wait more than 60s past `exp` — [verify.md](../verify.md) §3.6 uses ~71s.
