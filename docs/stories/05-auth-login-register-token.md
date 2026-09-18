# 05 — Auth login, trusted register, token exchange

**Status:** ready  
**Depends on:** `04-auth-jwks-jwt.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As an admin I want username/password login, and as a trusted system I want public register then `/auth/token` after approve, so that catalog sees only JWTs.

## Acceptance criteria

- [ ] Public `POST /auth/login` — admin username+password → JWT; reject non-admin and non-`ACTIVE`
- [ ] Admin JWT `roles` includes `ADMIN`; `scope` = `catalog:read catalog:write menu:read`
- [ ] Public `POST /auth/register` with `displayName` only — `TRUSTED_SYSTEM` + `PENDING`; no API secret
- [ ] Ignore or 400 if client sends `role` on register; never mint `ADMIN` here
- [ ] Public `POST /auth/token` with api_key + secret → JWT only if `ACTIVE` and unrevoked; else 401
- [ ] Trusted JWT `roles` includes `TRUSTED_SYSTEM`; `scope` = `catalog:read menu:read`; `client_id` set
- [ ] Secret hashed at rest; **never** in JWT
- [ ] Standard JSON envelope on these JSON APIs

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: login success/fail; register PENDING; token fails until ACTIVE (approve lands in 05 — here token fails for PENDING); secret not in token

## Notes

Approve/deny/revoke are story **06**.
