# Project authority and phase gates

## Contents

1. Authority map
2. Evidence states
3. Phase gates
4. Artifact classes and review gate
5. Legacy comparison and B11 sub-gates

## 1. Authority map

| Priority | Source | Role |
|---:|---|---|
| 1 | Explicit current user decision | Resolves project intent and may supersede an earlier project decision when impact is acknowledged |
| 2 | Root `AGENTS.md` | Project constitution and governance invariants |
| 3 | `docs/quy-trinh-lam-viec.md` | Master workflow and stage dependencies |
| 4 | Tầng A/B/C | Research method, technical workflow, and presentation conventions |
| 5 | Specialized B/A artifacts and accepted ADRs | Detailed domain, requirements, and architecture evidence |
| 6 | Status, indexes, summaries, and report outline | Derived navigation and progress state |
| 7 | Legacy code/database | Reuse, migration, coupling, and feasibility evidence only |

Apply the active system/developer/user instruction hierarchy before this project-local ordering. When equally authoritative sources conflict, preserve the conflict rather than selecting the easiest rule.

## 2. Evidence states

| State | Meaning | Promotion rule |
|---|---|---|
| `FACT` | Reproducible observation from an identified source | Cite the source and scope |
| `USER_CONFIRMED` | Explicit choice by the project owner | Record it durably before relying on it across tasks |
| `CANDIDATE` | Hypothesis or option under evaluation | Compare alternatives; do not propagate as final |
| `DECIDED` | Choice approved at its authorized gate | Record drivers, consequences, and verification |
| `OPEN` | Missing decision or input | Record owner/input; never auto-fill to complete a checklist |

Keep `USER_CONFIRMED` statements atomic and semantically faithful to the user's wording. Do not merge an implication into the confirmed statement. Record implications separately as `CANDIDATE` or `OPEN` and require their own evidence or decision.

## 3. Phase gates

| Phase | Authorized outputs | Not yet authorized |
|---:|---|---|
| 0 | Repository, roles, templates, governance foundation | Domain or architecture conclusions |
| 1 | Context, problem statement, research questions, public survey baseline | Target service decomposition |
| 2 | B2 baseline; business processes, domain events, candidate bounded contexts, candidate aggregates, invariants, open hotspots | Final services, accepted architecture ADRs, final Sagas, target schemas/contracts |
| 3 | Functional requirements, quality scenarios, priorities, B10 ASRs | Architecture decision without the required drivers |
| 4 | Approved B11-A independent alternatives, B11-B legacy feasibility check, B11-C architecture/ADRs with A1–A6 impact check, B12 data ownership and target schemas, B13 contracts, B14 sequences | Implementation claims without evidence |
| 5 | Implementation and controlled reuse/migration | Evaluation conclusions not measured |
| 6 | Tests, experiments, AI evaluation, operational evidence | Report claims exceeding evidence |
| 7 | Final synthesis and report packaging | New silent design decisions |

Interpretation rules:

- A bounded context is a model boundary, not automatically a process or deployable service.
- A cross-context business flow is not automatically a Saga.
- Candidate aggregate ownership is not automatically a physical schema.
- A cap of eight business services and three Saga flows is a constraint, not a target count.
- B10 provides architectural drivers; B11 decides architecture; B12–B14 elaborate the approved target.

## 4. Artifact classes and review gate

Classify artifacts before choosing sources:

| Class | Includes | Rule |
|---|---|---|
| `FORMATION` | A1–A7, B2–B10, B11-A, B11-C, B12–B14, target ADRs, target-design report arguments | Derive conclusions from requirements, domain evidence, invariants, ASRs, and approved constraints; do not load legacy sources in B2–B10 or B11-A |
| `COMPARISON` | B5.5, legacy baseline, B11-B, reuse/migration mapping, implementation feasibility evidence | Describe current assets and evaluate approved target alternatives; never generate, add, rank, or modify target boundaries or alternatives |

Default an unclassified new artifact to `FORMATION`.

Use the review chain `B2 baseline -> B3 -> B4 -> B5 -> B7`. Downstream drafts are allowed for exploration, but a downstream artifact cannot become `APPROVED` or drive a `DECIDED` conclusion until its required inputs are `APPROVED`. Each B2–B14 artifact records `DRAFT | REVIEW_READY | APPROVED`, a human reviewer, review date, and input versions. AI never approves its own output. If an approved input changes materially, return dependent artifacts to `REVIEW_READY`.

Use these canonical paths: B2 `docs/glossary.md`; B3 `docs/domain/B3-business-processes.md`; B4 `docs/domain/B4-domain-event-map.md`; B5 `docs/domain/B5-bounded-context-map.md`; B7 `docs/domain/B7-aggregates-and-invariants.md`; B11-A `docs/architecture/B11-A-independent-alternatives.md`; B11-B `docs/architecture/B11-B-legacy-feasibility.md`. Do not invent alternate primary files or a separate hotspot register; B4/B5/B7 own their open hotspots.

A fixed option set supplied in a prompt is not evidence. At B5, do not choose, rank, recommend, or relabel a placement/decomposition option, even as a `CANDIDATE`, unless it is independently derived from approved B4 evidence and later checked through B7/B10. Otherwise record only an `OPEN` hotspot with no placement preference. The instruction “choose for me” delegates analysis; it does not make the agent's selection `USER_CONFIRMED`.

## 5. Legacy comparison and B11 sub-gates

Use this direction only:

`requirements and evidence -> domain analysis -> quality drivers -> B11-A alternatives -> B11-B legacy comparison -> B11-C decision`

Do not use this reversed direction:

`legacy packages/tables/imports -> target boundaries -> retrospective justification`

If the user requests the reversed direction before a human-approved B11-A option set exists, stop before inspecting legacy assets. Explain the valid sequence and continue only with business/domain/quality evidence. Do not soften the violation by labeling a legacy-derived service list as `CANDIDATE`; its derivation is still invalid.

At B11-A, use B5, B7, B10, and approved constraints only; record the option set at the canonical path and require human `APPROVED` status before B11-B. At B11-B, record the B11-A input version and allow legacy evidence only to identify reusable assets, migration work, coupling, operational constraints, and feasibility risks for those approved alternatives. Do not create, add, rank, or modify an option. Return genuine feasibility risks to the appropriate design gate only as generalized constraints; close B5.5 before revising B11-A, invalidate the old B11-B result after a material B11-A change, and do not carry a package/table-derived option back into formation. At B11-C, require B11-A and B11-B to be `APPROVED`, select from the independently formed alternatives, and record the feasibility result. Before accepting a target ADR, record its impact on A1–A6 and re-review affected research artifacts and dependent inputs. Never let legacy silently become the original domain rationale.
