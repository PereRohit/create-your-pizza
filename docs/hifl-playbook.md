# HIFL Playbook — ThinkWithMe

Human-in-the-Feedback-Loop (HIFL) process for the ThinkWithMe pizza store catalog.

## Purpose

HIFL keeps a human owner in control of product direction while the agent drafts artifacts and, later, implementation. Every stage produces a durable document under `docs/`. Work does not advance until the owner accepts the current stage’s artifact. This reduces rework, keeps scope honest, and makes decisions searchable in Context.

This playbook is Stage 0 (process setup). Product content starts at Stage 1 (Intent).

## Roles

| Role | Who | Responsibility |
|------|-----|----------------|
| **Owner (human)** | Project owner | Sets goals, answers open questions, reviews each gate, chooses Approve / Revise / Park |
| **Agent** | Cursor agent(s) | Drafts stage artifacts, incorporates feedback, implements only after Design + Build plan are approved |

The agent does not invent locked product decisions past what the owner has stated. Open ideas stay in Intent until the owner promotes them.

## Stages (1–6)

| Stage | Name | Agent drafts | Artifact path | Human gate |
|-------|------|--------------|---------------|------------|
| 1 | Intent | Problem, actors, outcomes, scope, constraints, open ideas | `docs/intent.md` | Approve / Revise / Park |
| 2 | Spec | Requirements, API/auth behavior, acceptance criteria | `docs/spec.md` | Approve / Revise / Park |
| 3 | Design | Architecture, data model, security, PDF/API design | `docs/design.md` | Approve / Revise / Park |
| 4 | Build plan | Tasks, order, test plan, stack confirmation | `docs/build-plan.md` | Approve / Revise / Park |
| 5 | Build | Application code per approved plan | repo (after gate) | Checkpoint reviews as agreed |
| 6 | Verify | Test evidence, demo notes, residual risks | `docs/verify.md` | Approve / Revise / Park |

### Stage rules (each stage)

1. **Agent drafts** the artifact at the path above (or updates it after Revise).
2. **Human reviews** using the gate checklist below.
3. **Human responds** with gate language: **Approve**, **Revise: …**, or **Park**.
4. On **Approve**, status in the artifact becomes accepted; the next stage may start.
5. On **Revise**, feedback returns to the **owning stage**; agent updates that artifact only (not the next stage).
6. On **Park**, work on that stage pauses; no later stage starts.

## Gate language

Use exactly one of:

- **Approve** — artifact is accepted; next stage may begin.
- **Revise: \<specific feedback\>** — agent revises the current stage’s artifact; do not start the next stage.
- **Park** — pause this stage; leave a short reason if useful.

Ambiguous replies (“looks ok but…”) should be clarified by the agent as Approve vs Revise before advancing.

## Hard rules

1. **No stage starts until the previous stage’s artifact is accepted** (Approve).
2. **Feedback returns to the owning stage** — Spec feedback does not rewrite Intent unless the owner says the Intent itself is wrong; then reopen Intent.
3. **No application code before Design and Build plan are both approved.**
4. **Build (Stage 5) starts only after the Build-plan gate** is Approve.
5. Stack choices (e.g. Spring Boot 3) stay provisional until Design (and Build plan) lock them.
6. Artifacts live in **Context `docs/`**; process status lives in **`notes.md`** (coordinator). Do not scatter decisions only in chat.

## Gate review checklist (for the human)

Before Approve / Revise / Park, check:

- [ ] Problem and actors match what you meant
- [ ] In-scope / out-of-scope match v1 ambition (catalog + PDF + external API)
- [ ] Auth levels and external API-key access are correct
- [ ] Success criteria are testable enough for later Spec
- [ ] Open ideas are listed as pending (not silently implemented)
- [ ] Risks / unknowns you care about are called out
- [ ] Nothing in this artifact commits to work you want deferred
- [ ] Links to related docs (`project-context`, playbook, prior stage) are accurate

For Spec/Design/Build-plan gates, also check consistency with the accepted prior artifact.

## Handoffs in this Project

| What | Where |
|------|--------|
| Process + product docs | Context store `docs/` (this playbook, `project-context.md`, `intent.md`, later `spec.md`, etc.) |
| Stage status / checklist | Context `notes.md` (coordinator-owned) |
| Internal agent working notes | Context `internal/` (not user-facing product truth) |
| Application code | Workspace repo — **only after** Design + Build plan Approve |

### Typical handoff flow

1. Agent writes or updates a stage artifact under `docs/`.
2. Agent reports paths and a short summary; gate ask is clear (Approve / Revise / Park).
3. Owner replies with gate language.
4. On Approve: agent (or coordinator) marks the stage accepted and starts the next draft.
5. On Revise: agent edits the same artifact and re-asks the gate.
6. On Park: stop; leave status visible in `notes.md`.

## Related docs

- [Project context](project-context.md) — durable product decisions
- [Intent (Stage 1)](intent.md) — current Intent draft
