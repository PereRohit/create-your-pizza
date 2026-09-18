# User stories (`docs/stories/`)

Created **after** Design Approve **and** Build-plan Approve — **before** application code. See [hifl-playbook.md](../hifl-playbook.md) and the dependency graph in [build-plan.md](../build-plan.md) §6.

## Filename convention

- `{priority}-{short-slug}.md` — lowercase, hyphenated; numeric prefix = order (dependency then priority).
- When complete: rename to `DONE-{priority}-{short-slug}.md`.
- Do not implement a `DONE-` file again unless the owner reopens it.

## Files

| File | Status |
|------|--------|
| [01-compose-config.md](01-compose-config.md) | ready — **next** |
| [02-auth-schema-bootstrap.md](02-auth-schema-bootstrap.md) | ready (needs Initializr auth) |
| [03-auth-jwks-jwt.md](03-auth-jwks-jwt.md) | ready |
| [04-auth-login-register-token.md](04-auth-login-register-token.md) | ready |
| [05-auth-admin-users.md](05-auth-admin-users.md) | ready |
| [06-catalog-schema-seed.md](06-catalog-schema-seed.md) | ready (needs Initializr catalog) |
| [07-catalog-jwt-jwks.md](07-catalog-jwt-jwks.md) | ready |
| [08-catalog-writes.md](08-catalog-writes.md) | ready |
| [09-catalog-queries.md](09-catalog-queries.md) | ready |
| [10-catalog-redis-cache.md](10-catalog-redis-cache.md) | ready |
| [11-pdf-job-locks.md](11-pdf-job-locks.md) | ready |
| [12-public-pdf.md](12-public-pdf.md) | ready |
| [13-test-pdf-trigger.md](13-test-pdf-trigger.md) | ready |
| [14-openapi-agents.md](14-openapi-agents.md) | ready |

## Template

```markdown
# {priority} — {title}

**Status:** ready | in progress | done
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md)

## Story

As a [type of user], I want [some goal] so that [some reason].

## Acceptance criteria

- [ ] …

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage of the criteria above

## Notes

Environment setup / Docker / config properties as needed.
```
