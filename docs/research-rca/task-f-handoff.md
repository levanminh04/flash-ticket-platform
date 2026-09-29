# Task F handoff — frozen public-data RCA core

STATUS:

`TASK F CORRECTIVE CLOSURE COMPLETE — READY FOR MINH TASK G AUTHORIZATION`. The published v1 release and its independent assurance remain historical evidence; the verified v2 successor closes the RCD qualified-callable implementation gap. This status does not itself authorize Task G or final60 access.

CORRECTIVE CLOSURE (29/09/2026):

- The directly importable v1 `rcd_run` accepted an arbitrary callable and could label its output as RCD. The f06 caller used pinned qualification correctly, but the core did not enforce it. This was a blocking Task F implementation-boundary defect, not a TD-v1.3 or completed Task E scientific defect.
- W v2 manifest `configs/task-f-td13-frozen-release-v2.json` has SHA256 `c46b870bccdae1198292abc5850a83a3fa7e7f702286ceeaa07258074391801d` and binds the new 12-file implementation plus the exact RCD qualification receipt/contract. The factory verifies pinned source, patch, runtime and registered parameters; the core accepts only its one issued, intact runner. Scientific method, selections, configurations, evaluator and split remain identical to v1.
- Bounded Python 3.9 attempts `f07` and `f08` have PASS runtime receipts but were independently **BLOCKED — IMPLEMENTATION DEFECT** due to forged issuance paths; both remain preserved. Final `f09-qualified-rcd-boundary-single-issuance` is PASS, rejects lambda/tampered/forged handles and reproduces both f06 success result objects exactly. Task F suite: **70/70 PASS**; release verifier: **PASS, 44 path/SHA references**; fresh independent verdict: **PASS — TASK F CORRECTIVE CLOSURE READY FOR MINH TASK G AUTHORIZATION**. See W `results/task-f/task-f-corrective-closure.md` and `task-f-corrective-independent-review.md`.
- TD-v1.3 SHA256 remains `34fd73f6a84dc6b45834bd7fd4cc7b86e19e54f1011de99735631f027ee18971`. No Task E scientific campaign was rerun; no final60 access, Task G/H/I start, commit or push occurred. Five-distinct-independent-reviewer certification remains `OPEN / NOT FACTUALLY CERTIFIED`.

PURPOSE:

Materialize the human-frozen TD-v1.3 method as a reusable, tested public-data RCA core: admitted telemetry → local anomaly evidence / bounded detection → observed graph → ranking or event-triggered integrated diagnosis → sealed structured packet. No scientific reselection or efficacy claim was made in F.

INPUTS ACTUALLY USED:

- executed/frozen TD-v1.3 SHA256 `34fd73f6a84dc6b45834bd7fd4cc7b86e19e54f1011de99735631f027ee18971`;
- human decisions [RCA-062–063](RESEARCH-DECISIONS.md) / [U28F](../evidence/project-direction/2026-09-28-rca-td13-freeze-and-task-f-authorization.md);
- sealed Task E C1 selection, C5 lambda/threshold selection, integrated trigger evidence, RCD qualification/recovery and sensitivity/closure artifacts recorded by the frozen manifest;
- development30 sealed numeric artifacts and one predeclared raw-development smoke identity only;
- exact Task E loader/replay/integrated-adapter source bytes, bound to preserved receipts.

DECISIONS MADE:

- No scientific decision was made in Task F. Implementation organization, typed adapter qualification, controller-only quality projection, packet sealing and cache representation preserve frozen semantics.
- The Task F public boundary rejects arbitrary loaders and derives telemetry hashes from a trusted source audit.
- C5 public triggers retain `<360s` events as `INSUFFICIENT_HISTORY`; eligible triggers run frozen TD12-INTEGRATED-MTL with no R/comparator, as TD-v1.3 requires.

ARTIFACTS CREATED:

- Historical W v1 `configs/task-f-td13-frozen-release.json` — file SHA256 `18484bc4bb0c1d19e8f6936f12d8365a8ad69e16fffa2bf6ce5968189f0561e5`; canonical SHA256 `444406473da6e322579902958da5910133d3ad4c996f1aa170fe4dd2cc8d3570`;
- Historical W v1 `src/rca/` — 11-file ordered source-manifest SHA256 `383bf61c076eefd288b410a3f2bcbc4f20a7b78dd2cde3e06968bf6d0a5b3197`; successor v2 pins 12 files with source-manifest SHA256 `129de4d2ef146ed1277e7c87be458d63b40f14d0fdb13341d3b8632c6909c1d3`;
- W `tests/task_f/`;
- W `results/task-f/frozen-selection-extraction.json`, `task-f-impact-map.md`, `task-f-release-audit.md`, `task-f-independent-review.md`;
- W v1 validation receipts `f04-qualified-public-loader-and-integrated-smoke`, `f05-final-development-validation`, and `f06-final-rcd-boundary`; earlier `f01`–`f03` plus the visible failed f05 RCD interpreter attempt are preserved history. Successor corrective receipts are `f07`–`f09`, with f09 the final PASS after independent review.

EVIDENCE / EXPERIMENTS COMPLETED:

- machine extraction and 41-reference hash check: PASS; no sensitivity substituted as primary;
- historical v1 Task F tests: `65/65 PASS`; successor v2 tests: `70/70 PASS`;
- relevant unchanged Task E unit/contract regressions executed in this session: 144 PASS; campaign/coordinator harnesses were not misreported as semantic unit failures;
- raw development smoke: exact C1 L/O, exact C5 predictions/residuals/scores, exact integrated ranking at endpoints 630/1125, valid clock/oracle-safe packets;
- frozen end-to-end development validation: 30/30 cases PASS, zero failures, all eight C5 modes exact on the predeclared smoke, all 256 R draws and 2,713,600 registered proposals exact, raw/cache exact, repeat packet hashes exact;
- RCD bounded qualified runtime: PASS under pinned Python 3.9; no 270-config rerun;
- historical v1 independent verdict: `PASS — TASK F RELEASE READY FOR PRE-G REVIEW`; final corrective v2 independent verdict: `PASS — TASK F CORRECTIVE CLOSURE READY FOR MINH TASK G AUTHORIZATION`.

KNOWN LIMITATIONS:

- All TD-v1.3 limitations remain: C1 observed PPR did not beat local-only on development30; Phase 1 candidate mechanisms are post-hoc only; observed dependencies are not causal.
- The registered C5 `1e-12` fallback is preserved. It can dominate sparse channels but does not alone explain L-MTL collapse; G-vs-L remains context/capacity confounded.
- Qualified raw ingestion is development-only. PRE-G must requalify the final-scope source-ingestion/adapter relationship without opening final outcomes.
- RCD remains a separately qualified numeric boundary; G owns registered three-seed aggregation, owner mapping, worst-tie padding and evaluator-side scoring.
- Packets are tamper-evident serialized dictionaries; every consumer must call `validate_packet`.
- Five-distinct-independent-reviewer status remains `OPEN / NOT FACTUALLY CERTIFIED`; the Task F reviewer does not rewrite that historical/formal item.

OPEN ITEMS:

- Human authorization for PRE-G work and its exact impact map.
- PRE-G final-scope metadata/source-ingestion qualification while preserving the final60 outcome firewall.
- Task G remains unauthorized; no final campaign run exists.

PROHIBITED INTERPRETATIONS:

- Task F PASS is not final efficacy, graph superiority, causal identification, FlashTicket validation, five-reviewer certification or permission to open final60/G/H/I.
- Development outcomes, Phase 1/2 post-hoc findings and sensitivities are not runtime gates or replacement primary configurations.
- The preserved f05 wrong-interpreter RCD failure is not an algorithm failure; the successful f06 receipt does not erase it.

NEXT EXACT ACTION:

Minh reviews this handoff and, only by a separate explicit prompt, may authorize the PRE-G validity/freeze gate and Task G work. PRE-G must verify the final-scope source/adapter relationship and G controller responsibilities before any final campaign, without loading final labels/outcomes at this gate.

FILES REQUIRED BY NEXT SESSION:

- P `AGENTS.md`, governance skill, `CURRENT-STATE.md`, `MASTER-RESEARCH-PROGRAM.md`, canonical TD-v1.3, `RESEARCH-DECISIONS.md`, `ARTIFACT-MAP.md`, and this handoff;
- W historical v1 and successor v2 frozen manifests, `src/rca/`, `tests/task_f/`, frozen selection extraction, release audit, original and corrective independent reviews, corrective closure, and `f04`–`f09` receipts;
- sealed Task E selection/run contracts and final closure evidence by exact path/hash from the manifest; do not reopen post-hoc work as runtime logic.
