# 13 — Public PDF GET

**Status:** done  
**Depends on:** `12-pdf-job-locks.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As a public menu consumer, I want unauthenticated raw PDF download of the latest or a historic version so that I can open the menu card without a JWT.

## Acceptance criteria

- [x] `GET /api/menu.pdf` — no auth; success = raw `application/pdf` (not JSON envelope)
- [x] No query: latest — Redis-first, DB `max(version)` fallback, backfill Redis
- [x] `?version=N` (integer ≥ 1): if latest, Redis-first allowed; if historical, **DB only**
- [x] Missing version or no rows → JSON **404** envelope
- [x] Redis stores **latest only**

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: latest Redis; historical DB; unknown 404; content-type

## Tasks

- [x] `GET /api/menu.pdf` unauthenticated; success raw `application/pdf`
- [x] Latest: Redis-first, DB `max(version)` fallback, backfill Redis
- [x] `?version=N`: latest Redis-first; historical DB only
- [x] Missing version / no rows → JSON 404 envelope

## Notes

Unblocks **15** (with 06 and 14). Branch: `feat/13-public-pdf`.

Candid review: 2 passes (1 finding fixed: JSON 404 envelope when `Accept: application/pdf`); second review Findings: none.
