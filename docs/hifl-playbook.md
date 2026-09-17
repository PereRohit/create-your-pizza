# HIFL Playbook — Agentic SDLC

Human-in-the-Feedback-Loop (**HIFL**) process for **any** Agentic SDLC project. This file is Stage 0 (process only). Product decisions, live stage status, and owner preferences belong in `project-context.md` and `handoff.md` — not here.

## Purpose

HIFL keeps a human owner in control of product direction while the agent drafts artifacts and, later, implementation. Every stage produces a durable document under `docs/`. Work does not advance until the owner accepts the current stage’s artifact. This reduces rework, keeps scope honest, and makes decisions searchable in project context.

## How to adopt

1. Keep (or copy) this playbook as the process source of truth for the repo.
2. Maintain **`docs/project-context.md`** for durable product decisions, decision log, and **project-specific owner preferences** (e.g. preferred git branch when committing).
3. Maintain living **`docs/handoff.md`** for resume memory (compressed past stages + next-agent checklist).
4. Do **not** put product status, APPROVED badges, or repo-local git branch names in this playbook.

Default stage artifact paths below are conventions. A project may document alternate paths in `project-context.md`; if unspecified, use these defaults.

## Roles

| Role | Who | Responsibility |
|------|-----|----------------|
| **Owner (human)** | Project owner | Sets goals, answers open questions, reviews each gate, chooses Approve / Revise / Park |
| **Agent** | Cursor agent(s) (or equivalent) | Drafts stage artifacts, incorporates feedback, implements only after Design + Build plan are approved |

The agent does not invent locked product decisions past what the owner has stated. Open ideas stay in Intent until the owner promotes them.

## Stages (1–6)

| Stage | Name | Agent drafts | Artifact path (default) | Human gate |
|-------|------|--------------|-------------------------|------------|
| 1 | Intent | Problem, actors, outcomes, scope, constraints, open ideas | `docs/intent.md` | Approve / Revise / Park |
| 2 | Spec | Requirements, interfaces, acceptance criteria | `docs/spec.md` | Approve / Revise / Park |
| 3 | Design | Architecture, data model, security, interfaces | `docs/design.md` | Approve / Revise / Park |
| 4 | Build plan | Tasks, order, test plan, stack confirmation | `docs/build-plan.md` | Approve / Revise / Park |
| 5 | Build | Application code per approved plan | repo (after gate) | Checkpoint reviews as agreed |
| 6 | Verify | Test evidence, demo notes, residual risks | `docs/verify.md` | Approve / Revise / Park |

### Stage rules (each stage)

1. **Agent drafts** the artifact at the path above (or updates it after Revise).
2. **Human reviews** using the gate checklist below.
3. **Human responds** with gate language: **Approve**, **Revise: …**, or **Park**.
4. On **Approve**, status in the artifact becomes accepted; the agent **updates** [`docs/handoff.md`](handoff.md) for the **next** stage (see [Stage-end handoff](#stage-end-handoff--required)); then the next stage may start (subject to any **owner hold** delaying the next stage).
5. On **Revise**, feedback returns to the **owning stage**; agent updates that artifact only (not the next stage).
6. On **Park**, work on that stage pauses; no later stage starts; leave current `handoff.md` accurate for resume.

## Gate language

Use exactly one of:

- **Approve** — artifact is accepted; next stage may begin (after handoff update; subject to owner hold).
- **Revise: \<specific feedback\>** — agent revises the current stage’s artifact; do not start the next stage.
- **Park** — pause this stage; leave a short reason if useful.

Ambiguous replies (“looks ok but…”) should be clarified by the agent as Approve vs Revise before advancing.

## Hard rules

1. **No stage starts until the previous stage’s artifact is accepted** (Approve).
2. **Feedback returns to the owning stage** — Spec feedback does not rewrite Intent unless the owner says the Intent itself is wrong; then reopen Intent.
3. **No application code before Design and Build plan are both approved.**
4. **Build (Stage 5) starts only after the Build-plan gate** is Approve.
5. **Stack / tooling choices** stay provisional until Design (and Build plan) lock them.
6. Artifacts live in **`docs/`**; do not scatter durable decisions only in chat.
7. **Stage-end handoff required:** after every stage **Approve**, update [`docs/handoff.md`](handoff.md) before drafting the next stage: **compress** completed stages into a short past-memory section, then refresh next-stage checklist/steps. Do **not** wipe and fully rewrite from scratch. That file is the durable resume memory for the next agent — not chat, not editor-specific rules.
8. **No git commit unless the owner confirms.** After doc or code changes, the agent **asks** whether to commit (and on which branch/message). Branch naming and other git policy live in **`project-context.md` / `handoff.md`** for that project — never hard-code a branch name in this playbook.

## Gate review checklist (for the human)

### Any stage

Before Approve / Revise / Park, check:

- [ ] Problem and scope match what you meant
- [ ] In-scope / out-of-scope match the intended v1 (or current release) ambition
- [ ] Open ideas are listed as pending (not silently locked as decisions)
- [ ] Risks / unknowns you care about are called out
- [ ] Nothing in this artifact commits to work you want deferred
- [ ] Links to related docs (`project-context`, playbook, prior stage, handoff) are accurate
- [ ] Consistency with the prior **APPROVED** artifact (for Spec onward)

### Intent (Stage 1)

- [ ] Actors and desired outcomes are clear
- [ ] v1 ambition is honest and testable enough to drive Spec

### Spec (Stage 2)

- [ ] Requirements and acceptance criteria are testable
- [ ] Interfaces, auth, and quality bars match *this* product (as applicable)

### Design (Stage 3) / Build plan (Stage 4)

- [ ] Approach is implementable without silent scope growth
- [ ] Stack / tooling is locked only where Design or Build plan intentionally locks it
- [ ] Build plan has a clear task order and test approach (Build plan gate)

## Handoffs

| What | Where |
|------|--------|
| Process (this playbook) | `docs/hifl-playbook.md` |
| Product decisions + project prefs | `docs/project-context.md` |
| Stage artifacts | `docs/intent.md`, `spec.md`, `design.md`, `build-plan.md`, `verify.md` (defaults) |
| **Resume memory for next agent** | [`docs/handoff.md`](handoff.md) — living file; **compress + update** after every stage Approve |
| Application code | Workspace repo — **only after** Design + Build plan Approve |

### Stage-end handoff (required)

**When:** immediately after owner **Approve** for Intent, Spec, Design, Build plan, Build (ready for Verify), or Verify.

**What:** update the living [`docs/handoff.md`](handoff.md) (single path — do not invent stage-specific handoff filenames unless the owner asks). **Do not fully rewrite from a blank slate.**

**How (compress then update):**

1. If `handoff.md` already exists, **summarize** prior completed-stage material into a compact **Past stages (compressed)** section (bullets / short paragraphs — not a dump of the prior stage artifact).
2. Fold the just-approved stage into that compressed memory (what was decided + pointer to the APPROVED artifact).
3. Refresh **current state**, **owner preferences**, **next-agent checklist**, **next-stage steps**, and **What NOT to do** for the upcoming stage only.
4. Keep a short **locked highlights** list (or fold into past memory) so product locks stay visible without re-reading the full Spec/Design.

**Goal:** the handoff carries **compressed memory of past stages** plus **actionable handoff for the next stage** — history stays, verbosity drops.

**Minimum structure:**

- Audience, as-of date, repo root
- Stage status table (Intent → Verify) with links
- **Past stages (compressed)** — accumulated summaries of APPROVED stages
- Owner preferences still in force (incl. ask-before-commit; any owner hold)
- Checklist for the **next** agent only
- Steps for the next stage(s)
- Locked product highlights (short) and/or folded into past memory
- What NOT to do

**Stage → next handoff focus:**

| After Approve of | Compress that stage into past memory; focus next section on |
|------------------|--------------------------------------------------------------|
| Intent | Spec |
| Spec | Design |
| Design | Build plan |
| Build plan | Build |
| Build (ready for Verify) | Verify |
| Verify | Closeout / residual notes |

**Agent resume flow (typical):** read this playbook → read current [`handoff.md`](handoff.md) (past memory + next checklist) → open linked APPROVED artifacts only as needed → continue. Repo docs are the memory; do not rely on editor-specific rules for HIFL handoff.

### Typical gate + handoff flow

1. Agent writes or updates a stage artifact under `docs/`.
2. Agent reports paths and a short summary; gate ask is clear (Approve / Revise / Park). **Ask before any git commit.**
3. Owner replies with gate language.
4. On Approve: agent marks the stage accepted; **compresses past stages + updates `handoff.md` for the next stage**; asks about commit; then starts the next draft (unless an owner hold applies).
5. On Revise: agent edits the same artifact and re-asks the gate.
6. On Park: stop; leave `handoff.md` accurate for resume.

## Related docs (templates)

| Doc | Role |
|-----|------|
| [project-context.md](project-context.md) | Durable product decisions, decision log, project-specific prefs |
| [handoff.md](handoff.md) | Living resume: compressed past + next-stage handoff |
| [intent.md](intent.md) | Stage 1 Intent |
| [spec.md](spec.md) | Stage 2 Spec / PRD |
| [design.md](design.md) | Stage 3 Design / TRD (when started) |
| [build-plan.md](build-plan.md) | Stage 4 Build plan (when started) |
| [verify.md](verify.md) | Stage 6 Verify (when started) |
| [README.md](README.md) | Docs index for this repo |

Live stage status and APPROVED badges belong in **`handoff.md`** / stage artifacts — not in this playbook.
