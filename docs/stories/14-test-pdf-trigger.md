# 14 — Test-only PDF trigger

**Status:** ready  
**Depends on:** `12-pdf-job-locks.md`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As a developer, I want an unauthenticated test-profile route that starts PDF generation immediately so that I can exercise the same lock/dirty/skip rules without waiting for the interval.

## Acceptance criteria

- [ ] `POST /test/pdf/generate` — no auth
- [ ] Enabled only on test/dev profile — **not** production
- [ ] Same algorithm as the scheduled job (skip, not queue)

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: profile gated; skip-not-queue; same dirty/lock behaviour as job

## Notes

Unblocks **15** (with 06 and 13).
