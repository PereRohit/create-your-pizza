# create-your-pizza (ThinkWithMe)

Local workspace for the **ThinkWithMe** pizza store product catalog — also known as **create-your-pizza**.

## What this product is

A pizza store catalog with three v1 surfaces:

| Actor | Capability |
|-------|------------|
| **Admin** (`ADMIN`) | CRUD on catalog items |
| **Customer** (`CUSTOMER`) | View catalog as a PDF menu card |
| **External** (`EXTERNAL`) | Machine-to-machine catalog access via API key |

v1 is **catalog + PDF menu + external read API** only — no orders, payments, delivery, or franchising.

## HIFL Agentic SDLC

This project follows a **Human-in-the-Feedback-Loop (HIFL)** process: the agent drafts stage artifacts; the owner gates each stage with **Approve / Revise / Park**. See:

| Doc | Role |
|-----|------|
| [docs/hifl-playbook.md](docs/hifl-playbook.md) | Stages, gates, hard rules |
| [docs/project-context.md](docs/project-context.md) | Locked product decisions |
| [docs/intent.md](docs/intent.md) | Stage 1 Intent |
| [docs/spec.md](docs/spec.md) | Stage 2 Spec / PRD |

**Intent status:** DRAFT — awaiting the Intent gate. Do not treat Intent as accepted until the owner replies **Approve**.

**Spec / PRD:** DRAFT exists in `docs/spec.md` (synced from the owner brief). Still gated — do not treat Spec as accepted until the owner replies **Approve**.

**Hard rule:** no application code until **Design** and **Build plan** are both approved.

## Stack (not scaffolded yet)

Intended for a later Build stage (provisional until Design / Build-plan gates):

- Java Spring Boot 3
- Spring Security
- JPA
- OpenPDF
- REST APIs

This folder currently holds HIFL docs and project metadata only — **Spring Boot is not scaffolded**.

## Open this folder for later work

```bash
cd /Users/perennialsystem/go/src/github.com/PereRohit/create-your-pizza
# or: File → Open Folder in Cursor / your IDE
```

When Intent is approved and later stages clear their gates, application code will land in this same repo root.

## Docs index

See [docs/README.md](docs/README.md) for a one-line index of the HIFL documents.
