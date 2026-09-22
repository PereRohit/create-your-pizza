# User stories (`docs/stories/`)

Created **after** Design Approve **and** Build-plan Approve — **before** application code. See [hifl-playbook.md](../hifl-playbook.md) (stories vs tasks vs Definition of Ready; candid review loop) and the graph in [build-plan.md](../build-plan.md) §6.

**Coding preference (this project):** Prefer Spring Boot / Security / Data / Hibernate / Lombok / JDK and Build-plan libraries over hand-rolled boilerplate. Reuse framework injection and annotations; do not invent wrappers that duplicate a library or Spring feature.

## How work is tracked (Agile)

| Kind | What it is | Where | Done when |
|------|------------|--------|-----------|
| **Story** | User/operator increment **or** enabler that lands in git and unblocks others | `{id}-{slug}.md` or `{id}-OWNER-{slug}.md`; own branch | Implement ACs (and non-smoke tasks), candid review of code, then §5.1 smoke as last task on **06**/**10**, then rename to `DONE-…` |
| **Task** | Step inside a story (pom dep, one migration) | `## Tasks` on that story | Parent story is `DONE-` |
| **Definition of Ready** | Laptop/IDE so a story can start | Checklist below | Boxes ticked; not a story |
| **Service-ready smoke** | Compose checkpoint: auth after **06**; catalog read-path after **10** | [Build plan §5.1](../build-plan.md); last task on **06** / **10** before `DONE-` | Smoke steps pass; then `DONE-` rename; note in story/handoff |
| **HIFL gate / commit confirm** | Process | Chat + [handoff.md](../handoff.md) | Owner says Approve / yes commit |
| **Candid review loop** | Fresh reviewer, then fresh fix agent, before a gate or `DONE-` | [Playbook](../hifl-playbook.md#candid-review-loop); ephemeral handoff is not a file | In-scope findings fixed or shown to the owner; later stages/stories are not findings |

Do **not** invent a story for Lombok-in-IDE, installing Docker, “please confirm commit”, or the candid review loop. Do **not** invent a story for Nimbus/OpenPDF — those are **tasks** on **04** and **12**. Do **not** invent a separate story for §5.1 smokes — they are **tasks** on closing stories **06** and **10**.

**Enabler stories in this backlog:** **01** Compose (agent), **02** Maven Initializr (**owner**). No other owner-only enabler is required for v1 Build.

## Definition of Ready (not stories)

- [ ] JDK **26** and Maven available
- [ ] Docker Desktop (or equivalent) for Compose
- [ ] Lombok annotation processing enabled in the IDE

## Service-ready smoke (Build checkpoints)

Story `mvn test` stays mocked (no Docker required). At each service-ready checkpoint, run Compose smoke once as the **last task before** `DONE-` (after ACs + non-smoke tasks + candid review of code). If smoke fails, do **not** rename to `DONE-`. Auth is service-ready after **06**; catalog **read-path** is service-ready after **10** (**11–14** still later).

| Checkpoint | Closing story | Smoke |
|------------|---------------|--------|
| auth service-ready | **06** (last task before `DONE-`) | login → register → approve → token → JWKS verify ([build-plan §5.1](../build-plan.md)) |
| catalog read-path service-ready | **10** (last task before `DONE-`) | Bearer `GET /api/products` + 401 without JWT ([build-plan §5.1](../build-plan.md)) |

**07** may run in parallel with auth. **08** needs **06** `DONE-` (includes auth smoke). **11** needs **10** `DONE-` (includes catalog read-path smoke; PDF **12** may follow **09** without waiting on **10**). Stage 6 Verify still owns the full residual pack.

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
| [DONE-02-OWNER-maven-initializr.md](DONE-02-OWNER-maven-initializr.md) | done |
| [DONE-03-auth-schema-bootstrap.md](DONE-03-auth-schema-bootstrap.md) | done |
| [DONE-04-auth-jwks-jwt.md](DONE-04-auth-jwks-jwt.md) | done |
| [DONE-05-auth-login-register-token.md](DONE-05-auth-login-register-token.md) | done |
| [DONE-06-auth-admin-users.md](DONE-06-auth-admin-users.md) | done (§5.1 auth smoke PASS) |
| [DONE-07-catalog-schema-seed.md](DONE-07-catalog-schema-seed.md) | done |
| [DONE-08-catalog-jwt-jwks.md](DONE-08-catalog-jwt-jwks.md) | done |
| [09-catalog-writes.md](09-catalog-writes.md) | ready |
| [10-catalog-queries.md](10-catalog-queries.md) | ready (+ catalog read-path §5.1 smoke task) |
| [11-catalog-redis-cache.md](11-catalog-redis-cache.md) | ready (needs catalog read-path smoke via **10** `DONE-`) |
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
