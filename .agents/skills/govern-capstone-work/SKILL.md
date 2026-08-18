---
name: govern-capstone-work
description: Govern evidence, phase gates, document authority, and change propagation for the Flash Ticket graduation project. Use for any task in flash-ticket-platform involving capstone planning, reports, research, surveys, requirements, business analysis, B3-B14, bounded contexts, microservices, ADRs, Sagas, database or schema ownership, AI-assistant design, legacy-source comparison, or multi-file edits under docs.
---

# Govern Capstone Work

Apply this workflow to prevent unsupported conclusions, phase-gate violations, and accidental propagation across project documents.

## Load the minimum governing context

1. Confirm that the canonical repository is `D:/Project/flash-ticket-platform` and inspect `git status`.
2. Read the root `AGENTS.md` completely.
3. Read [project-authority-and-gates.md](references/project-authority-and-gates.md) for any methodology, domain, architecture, database, ADR, Saga, or legacy comparison task.
4. Classify the requested output as `FORMATION` or `COMPARISON` before selecting sources. Treat B11-B as the explicit `COMPARISON` exception inside B2–B14. Do not load B5.5, the legacy baseline, or the legacy repository for a `FORMATION` task in B2–B10 or B11-A.
5. Read only the project documents that directly govern the requested decision, but read each selected document completely.
6. Read [evaluation-cases.md](references/evaluation-cases.md) only when changing or testing this governance system.

Remain read-only and report the mismatch if the active Flash Ticket workspace is not the canonical repository.

## Execute the governance workflow

### 1. Classify intent

Classify the request as:

- `DISCUSS`: explain or explore; do not mutate.
- `REVIEW`: inspect and assess; do not mutate.
- `PLAN`: produce a decision-complete change plan; do not mutate.
- `EXECUTE`: apply an explicitly authorized scope and validate it.

Use read-only behavior when intent is ambiguous.

### 2. Build an evidence table

Classify every material statement used in reasoning:

| Statement | State | Evidence or owner | Authorized use |
|---|---|---|---|
| Verifiable observation | `FACT` | Source and line | Input to analysis |
| Explicit user choice | `USER_CONFIRMED` | User request or decision ID | Fixed project constraint until superseded |
| Design hypothesis | `CANDIDATE` | Analysis and alternatives | May be compared, not presented as final |
| Gate-approved choice | `DECIDED` | Decision register or accepted ADR | May drive downstream design |
| Missing input | `OPEN` | Required owner/input | Preserve; do not fill silently |

Do not promote a statement without evidence and the authorized gate.

Record each explicit user decision as one atomic statement with no added policy or consequence. Place every inferred implication in a separate `CANDIDATE` or `OPEN` row, even when the implication appears commercially or technically reasonable.

### 3. Check authority and phase gates

Follow the authority order and gate map in the reference. Distinguish domain boundaries from deployment boundaries and business coordination from architecture patterns.

For B2–B7 work, preserve the dependency chain `B2 baseline -> B3 -> B4 -> B5 -> B7`. Allow downstream drafts, but never mark a downstream artifact `APPROVED` or promote its decisions when required inputs are not `APPROVED`. Require status, a human reviewer, review date, and input versions; never let AI approve its own artifact.

Use the canonical files defined in the reference: `docs/glossary.md`, then `docs/domain/B3-business-processes.md`, `B4-domain-event-map.md`, `B5-bounded-context-map.md`, and `B7-aggregates-and-invariants.md`. Do not invent alternate primary filenames or a separate hotspot artifact; keep hotspots in B4/B5/B7.

At B5, reject any fixed aggregate-placement or service-decomposition option frame that cannot be traced to approved B4 evidence, even if the prompt hides its legacy origin. Do not choose, rank, recommend, or relabel any supplied option, including as a `CANDIDATE`. Record only the invariant/hotspot as `OPEN` for B7/B10/B11-A, with no directional placement advice. Treat "choose for me" as delegated analysis, not as user confirmation of the agent's eventual choice.

For architecture work, enforce three auditable B11 gates:

1. Write the legacy-independent option set to `docs/architecture/B11-A-independent-alternatives.md` from B5, B7, B10, and approved constraints. Do not open B5.5 until a human marks this option set `APPROVED`.
2. Write comparison results to `docs/architecture/B11-B-legacy-feasibility.md`, record the exact B11-A input version, and evaluate only the approved options. Do not create, add, rank, or modify an option while legacy evidence is open. If a feasibility risk requires new formation work, close B5.5 and return only a generalized constraint to B11-A; a material B11-A revision invalidates the old B11-B result.
3. Decide at B11-C only after B11-A and B11-B are `APPROVED`. Before accepting a target ADR, record `Tác động lên A1–A6`; return affected research artifacts to `REVIEW_READY`, update and re-review them and their dependent inputs. Treat ADR-000 as the methodological exception.

If sources conflict materially:

1. Stop mutation.
2. Quote or cite the exact conflicting lines.
3. Explain the affected decisions and downstream files.
4. Recommend a resolution.
5. Wait for approval unless the user's current request already resolves that exact conflict.

### 4. Create an impact map

Before changing more than three files or promoting a material decision, state:

- Authoritative artifact to change first.
- Derived artifacts that may need propagation.
- Decisions whose state will change.
- Files explicitly out of scope.
- Validation to run.

Treat an exact, already-approved implementation plan as sufficient approval. Do not request duplicate confirmation.

### 5. Apply minimal changes

Edit the source of truth first. Update derived documents only after the authoritative change is valid. Keep candidates, open questions, and decisions visibly distinct.

Never use legacy package, table, import, or service structure to generate, add, rank, or modify a target boundary or architecture alternative. Use legacy evidence only for reuse, migration, coupling, and feasibility at B11-B after the B11-A option set is human-approved.

When a request explicitly asks to derive service boundaries from the legacy repository before B11-A exists, do not inspect that repository. Refuse the reversed dependency, load business/domain/quality evidence instead, and do not emit named service candidates, inventory-placement preferences, or architecture recommendations from legacy observations.

### 6. Audit and hand off

From the repository root, run `.agents/skills/govern-capstone-work/scripts/audit-governance.ps1` after document or governance changes. Do not search for a top-level `scripts/` directory. Inspect the complete diff, not only individual files.

Report:

- Decision-state changes.
- Assumptions.
- Files changed.
- Checks and audit results.
- Remaining conflicts and open inputs.
- Content that belongs in the formal report.

## Stop conditions

Stop mutation when any of these conditions holds:

- The workspace is not canonical.
- A material rule conflict is unresolved.
- An upstream gate or required input is missing for the requested promotion; a downstream `DRAFT` may still be created when clearly labeled.
- The requested action would promote a candidate without authority.
- The actual diff contains unrelated or unowned changes that overlap the task.
