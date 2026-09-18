# 06 — Auth admin user APIs

**Status:** ready  
**Depends on:** `05-auth-login-register-token.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As an admin, I want to create other admins, list users, and approve/deny/revoke trusted systems so that partners get credentials once and I cannot delete myself.

## Acceptance criteria

- [ ] `POST /auth/admins` Admin JWT — create `ADMIN` / `ACTIVE` from username+password
- [ ] `GET /auth/users` Admin JWT — paginated (`page`, `size` default 10 max 100 clamp); filters `role`, `status`; envelope + `pagination` sibling
- [ ] `POST /auth/users/{id}/approve` — PENDING → ACTIVE; generate api_key + secret; secret **once** in `data`
- [ ] `POST /auth/users/{id}/deny` — PENDING → DENIED; no credentials
- [ ] `POST /auth/users/{id}/revoke` — ACTIVE → REVOKED; further `/auth/token` fails; existing JWTs valid until `exp`
- [ ] `DELETE /auth/users/{id}` — 403 if `{id}` equals JWT `sub`; otherwise delete other users
- [ ] Get-by-id style responses have no `pagination`

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: approve secret once; deny; revoke blocks token; cannot DELETE self; list pagination `next=-1`; create admin

## Notes

Unblocks **15** (with 13 and 14).
