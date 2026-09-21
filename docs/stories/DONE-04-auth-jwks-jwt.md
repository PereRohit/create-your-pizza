# 04 — Auth JWKS and JWT issue

**Status:** done  
**Depends on:** `03-auth-schema-bootstrap.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As catalog-service (and any client), I want auth to publish JWKS and issue RS256 JWTs so that tokens can be verified locally without `/validate`.

## Acceptance criteria

- [x] Auth holds private RS256 key in process/config only
- [x] On boot/rotation, public JWK upserted into `verification_keys`
- [x] Public `GET /auth/.well-known/jwks.json` returns active keys array
- [x] Issued JWT: header `alg`, `kid`, `typ=JWT`; claims `sub`, `iss`, `aud`, `exp`, `iat`, `scope`, `roles`; `client_id` when trusted; optional `jti`
- [x] `iss` = `create-your-pizza-auth`; `aud` = `create-your-pizza-catalog`
- [x] TTL from `app.jwt.ttl` default 30 minutes
- [x] No `POST /auth/validate`

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: JWKS shape; token signature + claims; TTL; private key not in JWKS JSON

## Tasks

- [x] Add Nimbus JOSE JWT to `auth-service/pom.xml`
- [x] RS256 key in process; upsert public JWK; JWKS endpoint; issue JWT with locked claims

## Notes

Third-party Nimbus is a **task** on this story, not its own story.
