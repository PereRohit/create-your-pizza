# 08 — Catalog JWT verify via JWKS

**Status:** done  
**Depends on:** `04-auth-jwks-jwt.md`, `07-catalog-schema-seed.md`, `06-auth-admin-users.md` (`DONE-` includes §5.1 auth smoke)  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As catalog-service, I want OAuth2 Resource Server local JWT verify from auth JWKS so that I never open `auth-db` or call `/validate`.

## Acceptance criteria

- [x] Catalog is a resource server; JWKS URI from properties (`AUTH_JWKS_URL` / `jwk-set-uri`)
- [x] Load JWKS at startup; unknown `kid` → refetch JWKS; no scheduled poll
- [x] Verify signature, `exp`, `iss`, `aud` in catalog
- [x] Missing/invalid JWT → 401; wrong scope → 403
- [x] Catalog does **not** query `auth-db`; no `/auth/validate` client
- [x] Redis is not used for keys or tokens

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: valid token; bad signature; wrong iss/aud; unknown kid refetch (mock HTTP); 401/403

## Tasks

- [x] SecurityFilterChain + OAuth2 Resource Server JWT
- [x] JwtDecoder with iss/aud validators; JWKS URI from properties
- [x] JWKS startup load into in-memory JWKSource (`app.jwt.jwks-warmup`); unknown kid → HTTP refetch; no scheduled poll
- [x] Scope rules: GET `/api/**` → `catalog:read`; writes → `catalog:write`; public `/api/menu.pdf`
- [x] Behaviour tests (MockMvc + local JWKS HTTP)
- [x] Candid review loop (1 fix cycle; second pass Findings: none)

## Notes

Unblocks **09**. Branch: `feat/08-catalog-jwt-jwks`.
