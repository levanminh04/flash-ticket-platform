# Flash Ticket Platform — Project Constitution

## Scope and required workflow

- Treat `D:/Project/flash-ticket-platform` as the only canonical workspace for the graduation project.
- Treat `D:/Project/flash-ticket-system` and any earlier repository as implementation references only. Do not mutate them unless the user explicitly names them as targets.
- If a Flash Ticket task starts from another workspace, remain read-only and report the workspace mismatch before project mutation.
- Use the repository skill `$govern-capstone-work` for every task involving project planning, research, surveys, requirements, business analysis, B3–B14, bounded contexts, microservices, ADRs, Sagas, database/schema ownership, AI-assistant design, report writing, legacy-source comparison, or multi-file changes under `docs/`.

## Authority order

Resolve project knowledge in this order, while preserving the higher system/developer/user instruction hierarchy:

1. The user's explicit current decision.
2. This project constitution.
3. `docs/quy-trinh-lam-viec.md` as the master workflow.
4. Tầng A, B, and C methodology/presentation documents.
5. Specialized business, requirements, architecture, and research artifacts.
6. Status files, indexes, outlines, and summaries derived from those artifacts.
7. Legacy source code and database assets as implementation evidence only.

If two applicable sources at any level conflict materially, do not mutate. Cite the exact lines and obtain a resolution or record an approved temporary rule.

## Non-negotiable phase gates

- Use the review chain `B2 baseline -> B3 -> B4 -> B5 -> B7`. Downstream drafts are allowed, but a downstream artifact cannot become `APPROVED` until its required inputs are `APPROVED`. AI must never approve its own artifact.
- Use Giai đoạn 2 to model business processes, domain events, candidate bounded contexts, candidate aggregates, invariants, and open hotspots.
- Do not equate a bounded context with a physical service.
- Do not equate a distributed business transaction with a Saga.
- Do not equate candidate data ownership with a finalized schema or database.
- Do not choose, rank, recommend, or relabel any preframed aggregate-placement or service-decomposition option at B5, even as a `CANDIDATE` and even when the prompt omits legacy names. Trace candidates to approved B4 evidence and B7 invariants; otherwise record only an `OPEN` hotspot for B11-A with no placement preference.
- Treat the caps of eight business services and three Saga flows as constraints evaluated at B10/B11, never as target counts for B5.
- Complete B10 architectural significance and quality priorities before B11 architecture work.
- Use `docs/architecture/B11-A-independent-alternatives.md` to form alternatives from B5, B7, and B10 without legacy input. A human must mark that option set `APPROVED` before B11-B may open B5.5.
- Use `docs/architecture/B11-B-legacy-feasibility.md` only to check the approved B11-A options for reuse, migration, coupling, and feasibility. Record the B11-A input version; never create, add, rank, or modify an architecture option while legacy evidence is open.
- Use B11-C only after B11-A and B11-B are `APPROVED`. Before accepting a target ADR, record its impact on A1–A6 and re-review affected research artifacts and dependent inputs.
- Finalize architecture ADRs, physical service grouping, Sagas, target schemas, and contracts only at their authorized gates in B11–B14.

## Legacy implementation quarantine

- Derive target boundaries from requirements, workflows, domain events, invariants, data ownership, change/load patterns, and quality attributes.
- Classify A1–A7, B2–B10, B11-A, B11-C, B12–B14, target ADRs, and target-design report arguments as `FORMATION`; classify B5.5, the legacy baseline, B11-B, and reuse/migration mapping as `COMPARISON`. Default an unclassified new artifact to `FORMATION`.
- Do not read B5.5 or the legacy repository while producing B2–B10 or B11-A. Let B5.5 enter only at B11-B after a human has approved the independent B11-A option set.
- Let B5.5 estimate reuse, replacement, migration, coupling, and feasibility. Never use existing packages, imports, tables, or service names to generate target boundaries or alternatives.
- If a request asks for service decomposition from legacy packages or tables before B11-A exists, do not inspect the legacy repository and do not output named service candidates or placement preferences. Reframe the task around business/domain/quality evidence first.
- If legacy evidence reveals a feasibility problem, close B5.5 and return only a generalized constraint to the proper design gate. A material B11-A revision invalidates the old B11-B result; do not silently rewrite the domain model or option set around the legacy structure.

## Decision and change control

- Use only these epistemic states: `FACT`, `USER_CONFIRMED`, `CANDIDATE`, `DECIDED`, and `OPEN`.
- Record durable decisions in `docs/project/decision-register.md` with evidence, owner, gate, and affected artifacts.
- Record a user-confirmed decision atomically and preserve its explicit meaning. Do not append inferred policies, conditions, consequences, or scope to the same `USER_CONFIRMED` statement; record each implication separately as `CANDIDATE` or `OPEN`.
- A request that delegates a choice to the agent is not user confirmation of the option the agent selects. Only the option explicitly selected by the user may be `USER_CONFIRMED`.
- When resolving review feedback or an `OPEN` point, stop before adopting a proposal that would add or materially change application code, an API or contract, schema/data, web or mobile UI, or behavior outside the approved scope. Present the concrete implementation and interface impact and obtain Lê Văn Minh's explicit confirmation before recording it as a requirement or decision. A documentation correction that only restores already-approved behavior is not a scope expansion.
- Before changing more than three files or promoting a material decision, present an impact map. Exact approval in the user's current request satisfies this requirement.
- Edit the authoritative artifact first and derived status/index/report artifacts afterward.
- Do not create an ADR merely to satisfy a checklist. An ADR requires its authorized gate, alternatives, decision drivers, consequences, and verification method.
- Accept a target-architecture ADR only at B11-C, with provenance to approved B11-A/B11-B and an explicit `Tác động lên A1–A6` result. `ADR-000` is the methodological exception.

## Handoff requirements

For every completed mutation, report:

- Decisions added, changed, or deliberately left open.
- Assumptions used.
- Files changed.
- Validation and audits run.
- Remaining conflicts or risks.
- Material that should later appear in the graduation report.
