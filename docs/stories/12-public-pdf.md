# 12 — Public PDF GET

**Status:** ready  
**Depends on:** `11-pdf-job-locks.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As a public menu consumer, I want unauthenticated raw PDF download of the latest or a historic version so that I can open the menu card without a JWT.

## Acceptance criteria

- [ ] `GET /api/menu.pdf` — no auth; success = raw `application/pdf` (not JSON envelope)
- [ ] No query: latest — Redis-first, DB `max(version)` fallback, backfill Redis
- [ ] `?version=N` (integer ≥ 1): if latest, Redis-first allowed; if historical, **DB only**
- [ ] Missing version or no rows → JSON **404** envelope
- [ ] Redis stores **latest only**

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: latest Redis; historical DB; unknown 404; content-type

## Notes

Unblocks **14** (with 05 and 13).
