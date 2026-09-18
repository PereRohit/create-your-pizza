# 08 — Catalog JWT verify via JWKS

**Status:** ready  
**Depends on:** `04-auth-jwks-jwt.md`, `07-catalog-schema-seed.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As catalog-service, I want OAuth2 Resource Server local JWT verify from auth JWKS so that I never open `auth-db` or call `/validate`.

## Acceptance criteria

- [ ] Catalog is a resource server; JWKS URI from properties (`AUTH_JWKS_URL` / `jwk-set-uri`)
- [ ] Load JWKS at startup; unknown `kid` → refetch JWKS; no scheduled poll
- [ ] Verify signature, `exp`, `iss`, `aud` in catalog
- [ ] Missing/invalid JWT → 401; wrong scope → 403
- [ ] Catalog does **not** query `auth-db`; no `/auth/validate` client
- [ ] Redis is not used for keys or tokens

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: valid token; bad signature; wrong iss/aud; unknown kid refetch (mock HTTP); 401/403

## Notes

Do not start **09** until this is `DONE-`.
