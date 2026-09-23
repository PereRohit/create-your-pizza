# Docs

[hifl-playbook.md](hifl-playbook.md) is the stack-agnostic Agentic SDLC process. The other docs in this folder are this product’s filled artifacts.

**Resume mid-process:** read [hifl-playbook.md](hifl-playbook.md) (including the candid review loop), then the living [handoff.md](handoff.md) (compressed past stages + next checklist), then open APPROVED artifacts only as needed. After each stage **Approve**, compress that stage into handoff past memory and refresh next-stage items — do not blank-rewrite. Before every gate and before a story is `DONE-`, run the playbook’s candid review loop.

| Doc | Role |
|-----|------|
| [handoff.md](handoff.md) | **Living resume** — compressed past stages + next-agent handoff |
| [hifl-playbook.md](hifl-playbook.md) | Stack-agnostic Agentic SDLC process: HIFL stages, gates, candid review loop, hard rules, stage-end handoff |
| [project-context.md](project-context.md) | Durable product decisions and decision log |
| [intent.md](intent.md) | Stage 1 Intent — **APPROVED** |
| [spec.md](spec.md) | Stage 2 Spec / PRD — **APPROVED** (aligned 2026-09-18) |
| [design.md](design.md) | Stage 3 Design / TRD — **APPROVED** 2026-09-18 |
| [build-plan.md](build-plan.md) | Stage 4 — **APPROVED** 2026-09-18 |
| [verify.md](verify.md) | Stage 6 — **APPROVED** 2026-09-23; evidence only, defects link out to `bugs.md` |
| [bugs.md](bugs.md) | **Defect register** — RCA per bug, link to its ticket, closure criteria (full regression). BUG-01 and BUG-02 both closed; no open rows |
| [technical-audit-audience.md](technical-audit-audience.md) | Java/Maven audit triage (2026-09-23) — FIX / DEFER / IGNORE plus CVE pass. Integration branch: `maintenance/java-maven-audit`; code fixes land via `fix/` branches into that branch, then one PR to `main` after regression |
| [stories/README.md](stories/README.md) | Build-stage user stories **01–15**, plus bug tickets **16** and **17** — all done |
| [openapi/](openapi/) | **Static** OpenAPI YAML/JSON; regenerate via [`scripts/generate-openapi.sh`](../scripts/generate-openapi.sh) (Docker-only) |

**Git:** do not commit unless the owner confirms.
