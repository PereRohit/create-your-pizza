# create-your-pizza (CreateYourPizza)

Local workspace for the **CreateYourPizza** pizza store product catalog.

## Resume (next agent)

1. [docs/hifl-playbook.md](docs/hifl-playbook.md) — stages, gates, **stage-end handoff** hard rules  
2. [docs/handoff.md](docs/handoff.md) — **living resume** (compressed past stages + next-stage handoff; update after each Approve)  
3. Linked APPROVED artifacts from the handoff status table  

Repo docs are the memory — do not rely on chat or Cursor rules for HIFL handoff. **Do not git-commit** unless the owner confirms.

## HIFL status (summary)

| Stage | Status |
|-------|--------|
| Intent | **APPROVED** — [docs/intent.md](docs/intent.md) |
| Spec / PRD | **APPROVED** — [docs/spec.md](docs/spec.md) |
| Design / TRD | **APPROVED** 2026-09-18 — [docs/design.md](docs/design.md) |
| Build plan | **APPROVED** 2026-09-18 — [docs/build-plan.md](docs/build-plan.md) |
| Build | **DONE** — stories **01–15** in `docs/stories/`, all `DONE-` and merged to `main` |
| Verify | **APPROVED** 2026-09-23 — [docs/verify.md](docs/verify.md); defect register clear (BUG-01, BUG-02 both closed) |

**v1 is delivered and verified.** Full checklist and locked product highlights: [docs/handoff.md](docs/handoff.md). Decisions log: [docs/project-context.md](docs/project-context.md). Docs index: [docs/README.md](docs/README.md). Build and run instructions: [AGENTS.md](AGENTS.md).

**Hard rule:** implement **one** story at a time after Design + Build plan Approve (graph in the build plan).

## Stack

**Java 26**, **Spring Boot 4.1.1**, **Maven**, JAR, `application.properties`. Two independent sibling services `auth-service` / `catalog-service` with **no parent POM**. Compose: `auth-db`, `catalog-db`, Redis, both apps.
