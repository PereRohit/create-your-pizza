# 14 — OpenAPI and AGENTS.md

**Status:** ready  
**Depends on:** `05-auth-admin-users.md`, `12-public-pdf.md`, `13-test-pdf-trigger.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As an integrator or agent, I want Swagger/OpenAPI on both services and an `AGENTS.md` so that I can import APIs in Postman and set up the repo.

## Acceptance criteria

- [ ] SpringDoc on **auth-service** and **catalog-service**; Postman-importable OpenAPI
- [ ] Document envelope, pagination, 503, PDF binary + `version` query, auth approve/revoke, JWKS
- [ ] Repo-root `AGENTS.md`: compose, first-admin in **auth-service logs**, Swagger URLs, JWKS URL, Maven `-f` per sibling, Java 26 / Boot 4.1.1

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story (if any new Java)
- Full **behaviour** coverage: OpenAPI paths exist for locked routes (smoke against springdoc config)

## Notes

Last story in the graph. After all `DONE-`, draft [verify.md](../verify.md).
