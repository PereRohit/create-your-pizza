# 06 — Auth admin user APIs

**Status:** done  
**Depends on:** `DONE-05-auth-login-register-token.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As an admin, I want to create other admins, list users, and approve/deny/revoke trusted systems so that partners get credentials once and I cannot delete myself.

## Acceptance criteria

- [x] `POST /auth/admins` Admin JWT — create `ADMIN` / `ACTIVE` from username+password
- [x] `GET /auth/users` Admin JWT — paginated (`page`, `size` default 10 max 100 clamp); filters `role`, `status`; envelope + `pagination` sibling
- [x] `POST /auth/users/{id}/approve` — PENDING → ACTIVE; generate api_key + secret; secret **once** in `data`
- [x] `POST /auth/users/{id}/deny` — PENDING → DENIED; no credentials
- [x] `POST /auth/users/{id}/revoke` — ACTIVE → REVOKED; further `/auth/token` fails; existing JWTs valid until `exp`
- [x] `DELETE /auth/users/{id}` — 403 if `{id}` equals JWT `sub`; otherwise delete other users
- [x] Get-by-id style responses have no `pagination`

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: approve secret once; deny; revoke blocks token; cannot DELETE self; list pagination `next=-1`; create admin

## Tasks

- [x] Admin user APIs per acceptance criteria
- [x] After ACs + candid review of code, before `DONE-` rename: run [Build plan §5.1](../build-plan.md) **auth service-ready** Compose smoke; record result in Notes / handoff. If smoke fails, do **not** rename to `DONE-`.

## Notes

Unblocks **15** (with 13 and 14). **Auth service-ready smoke** (Build plan §5.1) is the last task before `DONE-`; **08** must not start until this story is `DONE-` (smoke folded in).

Landed on `feat/06-auth-admin-users`: Nimbus admin JWT filter (not Resource Server); create/list/approve/deny/revoke/delete; pagination envelope; `auth-service/Dockerfile` + Compose build on port 8080; smoke script `scripts/auth-service-ready-smoke.sh`.

Candid review: 2 review→fix cycles (unit coverage for `listUsers` + filter rejection branches). Third review agent launch timed out after coverage fix; ACs + 61 tests green.

**§5.1 Auth service-ready smoke: PASS** (2026-09-21) — bootstrap admin → login → register PENDING → approve (apiKey+apiSecret once) → token → JWKS verifies both JWTs (`iss`/`aud`/`roles`/`scope`; trusted `client_id`; secret not in JWT).
