# 15 — OpenAPI and AGENTS.md

**Status:** done  
**Depends on:** `06-auth-admin-users.md`, `13-public-pdf.md`, `14-test-pdf-trigger.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As an integrator or agent, I want Swagger/OpenAPI on both services and an `AGENTS.md` so that I can import APIs in Postman and set up the repo.

## Acceptance criteria

- [x] Postman-importable OpenAPI via **static** YAML/JSON under `docs/openapi/` (no live Swagger UI / `/v3/api-docs`)
- [x] Document envelope, pagination, 503, PDF binary + `version` query, auth approve/revoke, JWKS (in static specs)
- [x] Repo-root `AGENTS.md`: compose, first-admin in **auth-service logs**, static OpenAPI paths, JWKS URL, Maven `-f` per sibling, Java 26 / Boot 4.1.1

## Tests (if coding)

- N/A for delivery. Regenerate specs with `./scripts/generate-openapi.sh` (Docker-only).

## Tasks

- [x] Static OpenAPI YAML/JSON under `docs/openapi/` for auth and catalog
- [x] Document envelope, pagination, 503, PDF binary + `version`, approve/revoke, JWKS
- [x] Repo-root `AGENTS.md`
- [x] No runtime SpringDoc / Swagger UI serving
- [x] `scripts/generate-openapi.sh` (Docker-only temporary JDK container)
- [x] Candid review before `DONE-`

## Notes

Last story in the graph. After all `DONE-`, draft [verify.md](../verify.md). Branch: `feat/15-openapi-agents`.

**Hard:** static `docs/openapi/*` for Postman. After API changes: `./scripts/generate-openapi.sh` then commit.
