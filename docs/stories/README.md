# User stories (`docs/stories/`)

Created **after** Design Approve **and** Build-plan Approve — **before** application code. See [hifl-playbook.md](../hifl-playbook.md) (stories vs tasks vs Definition of Ready) and the graph in [build-plan.md](../build-plan.md) §6.

## How work is tracked (Agile)

| Kind | What it is | Where | Done when |
|------|------------|--------|-----------|
| **Story** | User/operator increment **or** enabler that lands in git and unblocks others | `{id}-{slug}.md` or `{id}-OWNER-{slug}.md`; own branch | Rename to `DONE-…` |
| **Task** | Step inside a story (pom dep, one migration) | `## Tasks` on that story | Parent story is `DONE-` |
| **Definition of Ready** | Laptop/IDE so a story can start | Checklist below | Boxes ticked; not a story |
| **HIFL gate / commit confirm** | Process | Chat + [handoff.md](../handoff.md) | Owner says Approve / yes commit |

Do **not** invent a story for Lombok-in-IDE, installing Docker, or “please confirm commit”. Do **not** invent a story for Nimbus/OpenPDF — those are **tasks** on **04** and **12**.

**Enabler stories in this backlog:** **01** Compose (agent), **02** Maven Initializr (**owner**). No other owner-only enabler is required for v1 Build.

## Definition of Ready (not stories)

- [ ] JDK **26** and Maven available
- [ ] Docker Desktop (or equivalent) for Compose
- [ ] Lombok annotation processing enabled in the IDE

## Filename convention

- `{priority}-{short-slug}.md` — numeric prefix = order.
- If the story cannot be `DONE-` without the owner: `{priority}-OWNER-{short-slug}.md`.
- When complete: `DONE-{priority}-{short-slug}.md` or `DONE-{priority}-OWNER-{short-slug}.md`.
- Do not implement a `DONE-` file again unless the owner reopens it.
- Do **not** put `OWNER` on a story only because of commit-ask, HIFL gates, or Definition of Ready.

## Git branch (required)

Each **story** is implemented on its **own** branch. The branch holds **only** that story’s changes.

- Format: `feat/<story-id>-<max-5-word-summary>`
- `<story-id>` is the **number only** (`02` from `02-OWNER-maven-initializr.md`)
- Example for `01-compose-config.md`: `feat/01-compose-and-config`
- Example for `02-OWNER-maven-initializr.md`: `feat/02-maven-initializr`
- Still **ask** the owner before any commit.

## Files

| File | Status |
|------|--------|
| [DONE-01-compose-config.md](DONE-01-compose-config.md) | done |
| [02-OWNER-maven-initializr.md](02-OWNER-maven-initializr.md) | ready — **owner** enabler (parallel with 01) |
| [03-auth-schema-bootstrap.md](03-auth-schema-bootstrap.md) | ready |
| [04-auth-jwks-jwt.md](04-auth-jwks-jwt.md) | ready |
| [05-auth-login-register-token.md](05-auth-login-register-token.md) | ready |
| [06-auth-admin-users.md](06-auth-admin-users.md) | ready |
| [07-catalog-schema-seed.md](07-catalog-schema-seed.md) | ready |
| [08-catalog-jwt-jwks.md](08-catalog-jwt-jwks.md) | ready |
| [09-catalog-writes.md](09-catalog-writes.md) | ready |
| [10-catalog-queries.md](10-catalog-queries.md) | ready |
| [11-catalog-redis-cache.md](11-catalog-redis-cache.md) | ready |
| [12-pdf-job-locks.md](12-pdf-job-locks.md) | ready |
| [13-public-pdf.md](13-public-pdf.md) | ready |
| [14-test-pdf-trigger.md](14-test-pdf-trigger.md) | ready |
| [15-openapi-agents.md](15-openapi-agents.md) | ready |

## Template

```markdown
# {priority} — {title}

**Status:** ready | in progress | done
**Type:** story | enabler (optional)
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md)

## Story

As a [type of user], I want [some goal] so that [some reason].

## Acceptance criteria

- [ ] …

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage of the criteria above

## Tasks

- [ ] Implementation steps for this story only (not separate stories)
- [ ] **Owner:** … (only if this story cannot finish without the owner; then the filename must include `OWNER`)

## Notes

Environment setup / Docker / config properties as needed.
```
