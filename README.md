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
| Build | **Started** — stories `docs/stories/`; next `01-compose-config.md`; no application code yet |
| Verify | Not started |

Full checklist and locked product highlights: [docs/handoff.md](docs/handoff.md). Decisions log: [docs/project-context.md](docs/project-context.md). Docs index: [docs/README.md](docs/README.md).

**Hard rule:** implement **one** story at a time after Design + Build plan Approve (graph in the build plan).

## Stack (not scaffolded yet)

Locked for later Build: **Java 26**, **Spring Boot 4.1.1**, **Maven**, JAR, `application.properties`. Owner Initializr (Java **25** on the site, then `<java.version>26</java.version>`). Two sibling folders `auth-service` / `catalog-service`. Compose: `auth-db`, `catalog-db`, Redis, both apps. This repo currently holds HIFL docs only.
