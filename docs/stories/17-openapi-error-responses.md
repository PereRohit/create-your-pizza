# 17 — BUG: static OpenAPI omits 503 and all error responses

**Status:** ready — analysed and handed over, **not started**  
**Type:** bug (found at Stage 6 Verify full-regression run, 2026-09-22)  
**Depends on:** `DONE-15-openapi-agents.md` (reopens one of its acceptance criteria)  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md) · [BUG-02 RCA](../bugs.md#bug-02--static-openapi-omits-503-and-all-error-responses)

## Story

As an integrator importing the published contract into Postman, I want the documented operations to include the error responses they can really return — above all the **503 busy** envelope on catalog writes — so that I can code against concurrency behaviour without discovering it in production.

## Defect

**Symptom:** every one of the 20 operations in `docs/openapi/auth-service.{yaml,json}` and `docs/openapi/catalog-service.{yaml,json}` declares exactly one response, `200`. No `503`, `401`, `403`, or `404` appears anywhere. `GET /api/menu.pdf` declares its 200 content as `*/*` instead of `application/pdf`.

**Breaches:** Design §9 marks the committed static files **Hard (Build delivery)** and enumerates what they must cover — "envelope, pagination, **503**, **PDF binary** + `version`, auth approve/revoke, JWKS". Spec **FR-21** makes them the integration API list, **NFR-4** requires consistency with implemented endpoints, and **NFR-12** locks the 503 shape (omits `data` and `pagination`).

**No runtime impact.** The live regression confirmed the services really do return the 503 envelope with `Retry-After: 60` and really do serve raw `application/pdf`. Only the published contract is incomplete. Envelope and `Pagination` schemas are present and `?version=` is documented, so the gap is specifically error responses plus the PDF media type.

**Missed because:** [DONE-15](DONE-15-openapi-agents.md) ticks the "document … 503 …" criterion but is marked `N/A` for tests, so nothing machine-checked the exported files against the clause; SpringDoc emits bare `200` / `*/*` unless controllers declare responses. The earlier Verify draft recorded a PASS from a substring spot check rather than a real assertion.

Full RCA: [BUG-02](../bugs.md#bug-02--static-openapi-omits-503-and-all-error-responses).

## Acceptance criteria

- [ ] Catalog write operations (`POST`/`PUT`/`DELETE` on `/api/products` and `/api/options`) document **503** referencing the standard envelope, with `data` and `pagination` absent and the `Retry-After` header described
- [ ] Operations document the auth failures they can return: **401** unauthenticated, **403** for trusted-vs-admin write attempts and admin-only reads, **404** where applicable (unknown id, unknown `?version=`)
- [ ] `GET /api/menu.pdf` documents its 200 content as `application/pdf` with binary format, and keeps the `version` query parameter
- [ ] Envelope and `Pagination` schemas remain as they are (already correct) and no endpoint, path, parameter, or schema name changes
- [ ] No live Swagger UI and no live `/v3/api-docs` — the static-files-only rule stays intact
- [ ] Regenerated `docs/openapi/*` committed via `./scripts/generate-openapi.sh`, and all four files still parse and import
- [ ] Existing suites stay green (auth **62**, catalog **115**, 1 skipped each)

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed
- A **machine check on the exported specification** — the guard whose absence let a ticked criterion be false. It must fail if 503 is missing from a catalog write operation, or if the menu endpoint stops declaring `application/pdf`.
- Full **behaviour** coverage of the criteria above

## Tasks

- [ ] Declare the error responses on the controllers (or via a shared `@ControllerAdvice` / OpenAPI customiser) so the exporter emits them — prefer one shared declaration over repeating annotations on every method
- [ ] Declare the menu PDF response as `application/pdf` binary
- [ ] Add the exported-spec assertion test
- [ ] Regenerate with `./scripts/generate-openapi.sh` (**Docker required**) and commit `docs/openapi/*`
- [ ] `mvn -f catalog-service test` and `mvn -f auth-service test` green

## Notes

Branch: `fix/17-openapi-error-responses` (not created yet). Both services are in scope; catalog carries the 503 and the PDF media type, auth carries 401/403/404.

No Spec or Design change — this restores a contract those documents already lock. No product scope, no new endpoints.

Handled as a **normal ticket** (own branch, ACs, tasks, tests, candid review loop) **plus** the [bug closure criteria](../bugs.md#how-a-bug-is-closed): full regression — both suites and the entire live residual pack on a fresh stack — including the live 503 and raw-PDF checks that prove the documented behaviour matches reality.
