# User stories (`docs/stories/`)

Created **after** Design Approve **and** Build-plan Approve — **before** application code. See [hifl-playbook.md](../hifl-playbook.md) (Build-stage user stories).

## Filename convention

- `{priority}-{short-slug}.md` — lowercase, hyphenated; numeric prefix = order (dependency then priority).
- When complete: rename to `DONE-{priority}-{short-slug}.md`.
- Do not implement a `DONE-` file again unless the owner reopens it.

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

No story files yet — ordered list is in [build-plan.md](../build-plan.md) §4. Create those files only after Build-plan **Approve**.
