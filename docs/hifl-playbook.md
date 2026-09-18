# HIFL Playbook — CreateYourPizza

Human-in-the-Feedback-Loop (HIFL) process for the CreateYourPizza pizza store catalog.

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
4. On **Approve**, status in the artifact becomes accepted; the agent **updates** [`docs/handoff.md`](handoff.md) for the **next** stage (see [Stage-end handoff](#stage-end-handoff--required)); then the next stage may start (subject to any owner hold, e.g. Design).
5. On **Revise**, feedback returns to the **owning stage**; agent updates that artifact only (not the next stage).
6. On **Park**, work on that stage pauses; no later stage starts; leave current `handoff.md` accurate for resume.

## Gate language

Use exactly one of:

- **Approve** — artifact is accepted; next stage may begin.
- **Revise: \<specific feedback\>** — agent revises the current stage’s artifact; do not start the next stage.
- **Park** — pause this stage; leave a short reason if useful.

Ambiguous replies (“looks ok but…”) should be clarified by the agent as Approve vs Revise before advancing.

## Build-stage user stories (required before coding)

After Design **and** Build-plan **Approve**, do **not** start implementation by opening a random class. First write **user stories** under [`docs/stories/`](stories/README.md).

**Rules:**

1. One markdown file per story. Sort filenames so **dependencies and priority** are obvious (e.g. `01-env-compose.md`, `02-auth-bootstrap.md`).
2. Format: **As a [type of user], I want [goal] so that [reason].** Include **acceptance criteria**, and excerpts/links to Intent / Spec / Design (and ADRs if any).
3. Every story that involves **coding** MUST include **unit tests**: **>80% LoC coverage** and **full behaviour coverage** of that story. Environment/setup work may be its own story.
4. [`docs/handoff.md`](handoff.md) records **which story file(s) are in progress**. When a story is done, **rename** the file with a `DONE-` prefix (e.g. `DONE-01-env-compose.md`) so later agents skip it.
5. Pick the next non-`DONE-` story in sort order unless the owner says otherwise.

This applies to any agentic SDLC using this playbook, not only CreateYourPizza.

## Hard rules

1. **No stage starts until the previous stage’s artifact is accepted** (Approve).
2. **Feedback returns to the owning stage** — Spec feedback does not rewrite Intent unless the owner says the Intent itself is wrong; then reopen Intent.
3. **No application code before Design and Build plan are both approved**, and not before **user stories** exist under `docs/stories/` (see above).
4. **Build (Stage 5) starts only after the Build-plan gate** is Approve.
5. Stack choices (e.g. Spring Boot 3) stay provisional until Design (and Build plan) lock them.
6. Artifacts live in **`docs/`**; stage status is tracked with the project coordinator. Do not scatter decisions only in chat.
7. **Stage-end handoff required:** after every stage **Approve**, update [`docs/handoff.md`](handoff.md) before drafting the next stage: **compress** completed stages into a short past-memory section, then refresh next-stage checklist/steps. Do **not** wipe and fully rewrite from scratch. That file is the durable resume memory for the next agent — not chat, not Cursor rules.
8. **No git commit unless the owner confirms.** After doc or code changes, the agent **asks** whether to commit (and on which branch/message). Preferred branch when committing docs: `cursor/sync-spec-prd-revise-efa1` — do not create new branches for doc syncs. Never assume commit.

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
| Process + product docs | `docs/` (this playbook, `project-context.md`, `intent.md`, `spec.md`, `handoff.md`, etc.) |
| **Resume memory for next agent** | [`docs/handoff.md`](handoff.md) — living file; **compress + update** after every stage Approve |
| Stage status / checklist | Also mirrored in `handoff.md` status table; coordinator may track separately |
| Application code | Workspace repo — **only after** Design + Build plan Approve |

### Stage-end handoff (required)

**When:** immediately after owner **Approve** for Intent, Spec, Design, Build plan, Build (ready for Verify), or Verify.

**What:** update the living [`docs/handoff.md`](handoff.md) (single path — do not invent `handoff-design.md` variants unless the owner asks). **Do not fully rewrite from a blank slate.**

**How (compress then update):**

1. If `handoff.md` already exists, **summarize** prior completed-stage material into a compact **Past stages (compressed)** section (bullets / short paragraphs — not a dump of Intent/Spec).
2. Fold the just-approved stage into that compressed memory (what was decided + pointer to the APPROVED artifact).
3. Refresh **current state**, **owner preferences**, **next-agent checklist**, **next-stage steps**, and **What NOT to do** for the upcoming stage only.
4. Keep a short **locked highlights** list (or fold into past memory) so product locks stay visible without re-reading full Spec.

**Intent:** the handoff carries **compressed memory of past stages** plus **actionable handoff for the next stage** — history stays, verbosity drops.

**Minimum structure:**

- Audience, as-of date, repo root
- Stage status table (Intent → Verify) with links
- **Past stages (compressed)** — accumulated summaries of APPROVED stages
- Owner preferences still in force (incl. ask-before-commit; Design hold if any)
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

**Agent resume flow (typical):** read this playbook → read current [`handoff.md`](handoff.md) (past memory + next checklist) → open linked APPROVED artifacts only as needed → continue. Repo docs are the memory; do not rely on Cursor rules for HIFL handoff.

### Typical gate + handoff flow

1. Agent writes or updates a stage artifact under `docs/`.
2. Agent reports paths and a short summary; gate ask is clear (Approve / Revise / Park). **Ask before any git commit.**
3. Owner replies with gate language.
4. On Approve: agent marks the stage accepted; **compresses past stages + updates `handoff.md` for the next stage**; asks about commit; then starts the next draft (unless owner hold applies).
5. On Revise: agent edits the same artifact and re-asks the gate.
6. On Park: stop; leave `handoff.md` accurate for resume.

## Related docs

- [Project context](project-context.md) — durable product decisions
- [Intent (Stage 1)](intent.md) — APPROVED
- [Spec (Stage 2)](spec.md) — APPROVED
- [Handoff](handoff.md) — living resume: compressed past + next-stage handoff
- [Stories](stories/README.md) — Build-stage user stories (after Build-plan Approve)
- [Docs index](README.md) — how to pick up mid-process
