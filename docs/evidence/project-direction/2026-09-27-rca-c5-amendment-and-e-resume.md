# C5 amendment và resume Task E — nguồn ủy quyền 27/09/2026

State: USER_CONFIRMED — Lê Văn Minh. Nguồn: mission do Minh gửi trong task; SHA256 `0109696102a7945375433833b3c6051d593714139b9e9627c8ce2cb3950c8adb`. Scope authorization không phải xác nhận exact algorithm do agent chọn. U27R mở sửa D/review/resume E theo gate; giữ final60 và F/G/H/I ngoài phạm vi.

## Nguyên văn mission

# MISSION — Resolve C5 Ambiguities, Amend Task D if Needed, Then Resume and Complete Task E Development Preflight

Hai workspace local:

- `D:\Project\flash-ticket-platform`
- `D:\Project\flash-ticket-rca-research`

Bạn đang tiếp tục đúng chuỗi công việc đã thực hiện trước đó.

Không bắt đầu lại từ đầu và không bỏ qua các evidence/run receipts đã có.

Mục tiêu của phiên này là:

1. **độc lập phân tích và chốt hai ambiguity C5 đã làm Task E dừng;**
2. sửa Task D theo đúng mức cần thiết;
3. review amendment;
4. nếu không còn blocker phương pháp thực chất, **tự động resume Task E**;
5. tiếp tục development preflight cho tới khi có một kết luận cuối cùng đáng tin cậy:
   - `PASS — READY FOR NEXT AUTHORIZED STAGE`
   - `PASS WITH LIMITATIONS`
   - `RETURN TO TASK D`
   - hoặc `INVALID RUN — REPEAT AFTER EXECUTION FIX`

Không được dừng giữa chừng chỉ để xin lại quyền mà prompt này đã cấp.

---

# 1. USER AUTHORIZATION FOR THIS RUN

Tôi xác nhận:

- được phép xử lý hai finding C5 hiện đang OPEN;
- được phép sửa Task D nếu technical review kết luận cần sửa;
- được phép cập nhật các canonical/derived research documents cần thiết;
- được phép tạo/sửa nhiều hơn ba file trong đúng phạm vi D/E này;
- prompt này được xem là explicit approval cho impact map phát sinh hợp lý trong phạm vi nhiệm vụ này;
- sau khi D được chốt lại và review đạt, được phép **resume ngay Task E** theo development scope đã được duyệt trước đó;
- không cần dừng lại để xin tôi confirm lại cùng một scope;
- được phép tải các development telemetry objects còn thiếu trong allowlist đã định;
- được phép tạo isolated environment cần thiết cho RCD/BARO qualification;
- được phép chạy synthetic fixtures, loader audits, smoke và toàn bộ authorized development30;
- được phép chạy mandatory development sensitivity đã đăng ký;
- được phép sửa implementation bugs nếu không làm thay đổi scientific method;
- được phép tạo amendment/revision mới của Task D nếu cần.

Không được:

- mở 60 final evaluation cases;
- chạy final benchmark/campaign;
- mở F/G/H/I;
- sửa application code, API, schema, Saga hoặc behavior của FlashTicket;
- thay Primary RQ;
- đổi dataset/scope đồ án;
- dùng final outcomes để tune;
- tự tạo feature/operator mới chỉ để cứu kết quả sau khi thấy development score xấu;
- xóa/ghi đè failed runs.

Nếu một thay đổi vượt các ranh giới trên thì mới cần hỏi tôi.

---

# 2. CURRENT STATE — DO NOT RE-DISCOVER FROM SCRATCH

State hiện tại đã có executable evidence.

Đọc đầy đủ trước:

## Platform

- `AGENTS.md`
- governance skill liên quan
- `docs/research-rca/CURRENT-STATE.md`
- `docs/research-rca/RESEARCH-DECISIONS.md`
- `docs/research-rca/MASTER-RESEARCH-PROGRAM.md`
- `docs/research-rca/task-c-research-decision-lock.md`
- `docs/research-rca/task-d-method-and-experiment-specification.md`
- `docs/research-rca/task-d-handoff.md`
- `docs/research-rca/task-e-handoff.md`
- `docs/research-rca/ARTIFACT-MAP.md`
- `docs/evidence/project-direction/2026-09-27-rca-development-preflight.md`

## Research workspace

Đọc đầy đủ:

- `results/task-e/preflight-plan.md`
- `results/task-e/e27-001-synthetic/adjudication.md`
- `results/task-e/e27-001-synthetic/file-inventory.json`
- `results/task-e/e27-001-synthetic/failures.jsonl`
- `results/task-e/e27-001-synthetic/review.json`
- các receipts `e27-001` tới `e27-006`
- `configs/task-e-td12-development.json`
- current Task E source/tests
- Task B capability/GT/trace/multimodal audits liên quan.

Không mặc định README/handoff lịch sử là live state nếu CURRENT-STATE hoặc decision register đã supersede.

Xác minh Git branch/HEAD và working tree của cả hai repo trước mutation.

Bảo tồn unrelated user changes.

---

# 3. WHAT HAS ALREADY BEEN ESTABLISHED

Không lặp lại những gì đã có nếu không cần để kiểm amendment.

Hiện đã có:

- current math fixtures: `21/21 PASS`;
- current bounded input-boundary fixtures: `13/13 PASS`;
- failed attempts được giữ;
- two actual development samples đã được inspect;
- mỗi sample reference graph quan sát có 27 services và 55 directed edges;
- TD-v1.2 chưa bị thay đổi trong lần preflight;
- chưa chạy actual development predictions;
- chưa có MRR/F1 thực tế;
- chưa chạy full30;
- chưa qualify RCD runtime;
- chưa hoàn thiện C5 detector/event implementation;
- còn thiếu development telemetry objects;
- final evaluation chưa được mở.

Các PASS hiện tại chỉ chứng minh phạm vi fixture tương ứng, không phải empirical RCA success.

---

# 4. TWO OPEN FINDINGS THAT BLOCKED E

## D-E27-01 — C5 applicable-neighbor membership

TD-v1.2 hiện chưa định nghĩa duy nhất:

- khi nào một neighbor/channel thuộc frozen applicable set;
- membership có yêu cầu chỉ cần scaler hợp lệ từ prefix hay phải đủ điều kiện train own forecasting model;
- own-model eligibility khác neighbor-feature eligibility như thế nào;
- per-bin availability khác frozen membership như thế nào;
- denominator coverage được freeze theo predicate nào.

Executable counterexample đã chứng minh hai interpretation hợp lý tạo input khác nhau:

- mean / coverage = `10 / 0.5`
- hoặc `0 / 0`

Không được tự coi bất kỳ cách hiểu nào hiện là đáp án.

---

## D-E27-02 — encountered trace conflict scope

TD-v1.2 nói encountered conflict làm bin invalid nhưng chưa định nghĩa đủ:

- service nào bị ảnh hưởng;
- channel nào;
- bin nào;
- graph edge/node nào;
- prefix graph đang xây bị ảnh hưởng thế nào;
- graph sau khi freeze bị ảnh hưởng thế nào;
- conflict có persistence không;
- recovery khi nào;
- có retroactive invalidation hay không;
- audit trail phải ghi gì.

Finding này hiện là specification ambiguity; chưa có evidence rằng conflict phổ biến trong dataset.

---

# 5. IMPORTANT — DO NOT ANCHOR ON PREVIOUS PROPOSALS

Các proposal trước đây, bao gồm bất kỳ đề xuất nào kiểu:

- “neighbor chỉ cần scaler hợp lệ”;
- “neighbor phải đủ own-model eligibility”;
- “chỉ mask trace channel”;
- “không retroactive graph”;

chỉ là **candidate hypotheses**.

Không được chọn chúng vì người dùng hoặc reviewer trước đã nêu.

Bạn phải đánh giá độc lập dựa trên:

1. semantics thực của TD-v1.2;
2. Task B evidence;
3. data availability/missingness;
4. graph semantics;
5. causal/dataflow consistency;
6. prevention of future leakage;
7. reproducibility;
8. fairness giữa G/L/ALL;
9. compatibility với MT/MTL;
10. prior literature/official implementations nếu thực sự liên quan.

Nếu kết luận hướng khác tốt hơn, hãy chọn hướng khác.

---

# 6. REQUIRED INDEPENDENT REVIEW FOR THE TWO POLICIES

Nếu native subagents khả dụng, dùng ít nhất ba independent reviewers trước khi coordinator chọn policy.

## Reviewer A — C5 semantics / dataflow

Tập trung:

- membership;
- scaler;
- model eligibility;
- per-bin availability;
- graph freeze;
- chronology;
- masks.

Đề xuất ít nhất 2 viable policies cho mỗi ambiguity.

---

## Reviewer B — Statistical / anomaly-detection consequences

Phân tích mỗi policy về:

- bias do missingness;
- denominator stability;
- graph context distortion;
- capacity fairness G/L/ALL;
- forecast semantics;
- risk làm anomaly score thay đổi vì availability chứ không phải signal;
- future information leakage.

Không nhìn development outcome để chọn.

---

## Reviewer C — adversarial falsification

Cố phá mỗi proposal bằng synthetic scenarios:

- neighbor có scaler nhưng không own model;
- own model fit được nhưng bin hiện tại unavailable;
- graph isolate;
- partial modality;
- conflict trong prefix;
- conflict đúng lúc graph freeze;
- conflict sau freeze;
- repeated conflict;
- conflict ở một service nhiều channels;
- missing versus duplicate versus contradictory trace identity.

Reviewer phải nói rõ policy nào dẫn tới hành vi khó bảo vệ.

---

# 7. POLICY SELECTION CRITERIA

Coordinator không vote theo đa số.

Chọn policy tốt nhất theo thứ tự ưu tiên:

1. runtime causality / không dùng future information;
2. deterministic implementation;
3. giữ graph identity và data availability là hai khái niệm riêng khi hợp lý;
4. missingness không âm thầm thay đổi scientific estimand;
5. G/L/ALL có interpretation rõ;
6. behavior có thể test được;
7. không tạo thêm hyperparameter tùy ý nếu không cần;
8. tương thích với existing D registry;
9. ít làm thay đổi method nhất nhưng vẫn đúng;
10. claim sau này dễ bảo vệ.

Nếu evidence cho thấy cả hai finding bắt nguồn từ một abstraction sai sâu hơn của C5, được phép redesign phần C5 liên quan.

Nhưng không mở lại C1 hoặc toàn architecture nếu không có evidence bắt buộc.

---

# 8. REQUIRED RATIONALE ARTIFACT

Sau khi chốt policy, bắt buộc tạo một file riêng trong W, ví dụ:

`task-d/td-v1.2-c5-clarification-rationale.md`

hoặc tên phù hợp hơn theo repository conventions.

File này phải ghi:

## For each finding

- problem;
- competing interpretations;
- evidence;
- advantages/disadvantages;
- selected policy;
- rejected alternatives;
- exact reason for rejection;
- assumptions;
- expected failure modes;
- effect on G/L/ALL;
- effect on MT/MTL;
- leakage analysis;
- backward compatibility;
- new tests required.

Đặc biệt ghi rõ:

> policy được chọn trước actual development outcome và không được chọn vì làm F1/MRR đẹp hơn.

Nếu chọn khác với recommendation của reviewer/user trước, ghi thẳng lý do.

---

# 9. AMEND TASK D

Sau policy selection:

- sửa canonical Task D trước;
- tăng revision/version hợp lý theo conventions repo;
- append decision/change record;
- không rewrite lịch sử;
- cập nhật CURRENT/HANDOFF/MAP chỉ sau canonical change;
- giữ previous TD hash/provenance;
- không gọi technical choice USER_CONFIRMED trừ đúng phần prompt này trao quyền scope; exact algorithm policy do agent chọn vẫn phải được phân loại theo governance phù hợp.

Amendment phải đủ cụ thể để hai engineer độc lập implement giống nhau.

Không chỉ thêm prose kiểu:

> “handle missingness consistently.”

Phải cho deterministic rule.

---

# 10. TEST THE AMENDMENT BEFORE RESUMING E

Bắt buộc thêm synthetic fixtures tương ứng.

## Membership fixtures

Ít nhất:

1. neighbor có scaler + own model;
2. scaler có nhưng own model không fit;
3. own model fit nhưng current bin missing;
4. neighbor entirely absent;
5. applicable set empty;
6. partial availability;
7. G/L/ALL consistency;
8. MT/MTL consistency;
9. denominator freeze test;
10. permutation invariance.

## Conflict fixtures

Ít nhất:

1. conflict trước graph freeze;
2. conflict tại boundary freeze;
3. conflict sau freeze;
4. conflict một channel;
5. conflict nhiều services;
6. repeated conflict;
7. recovery;
8. no-retroactive invariant;
9. cache/replay equivalence;
10. unrelated metrics/logs not accidentally invalidated unless selected policy explicitly requires it.

Expected results phải được xác định từ amended D trước khi test chạy.

---

# 11. TARGETED INDEPENDENT REVIEW

Sau amendment + fixtures:

Dùng fresh reviewer/subagent nếu capacity có thể.

Reviewer phải kiểm:

- amendment có giải quyết ambiguity thật không;
- test có chỉ encode implementation hiện tại thay vì spec không;
- có future leakage không;
- có hidden parameter mới không;
- có accidental scope expansion không;
- G/L/ALL còn interpretable không.

Không cần review lại toàn bộ Task D từ đầu.

Chỉ mở broader review nếu amendment vô tình thay estimand/core method.

---

# 12. AUTOMATIC RESUME RULE

Nếu targeted review kết luận:

- không còn CRITICAL/MAJOR ambiguity;
- fixtures pass;
- canonical D deterministic;
- không cần user decision ngoài scope này;

thì:

**TỰ ĐỘNG RESUME TASK E.**

Không hỏi tôi lại.

---

# 13. CONTINUE TASK E TO COMPLETION OF THIS DEVELOPMENT PREFLIGHT

Sau resume, tiếp tục từ checkpoint hiện tại.

Không chạy lại mọi thứ từ đầu nếu artifact/hash chứng minh có thể reuse an toàn.

## Required remaining execution

### A. Complete C5 synthetic qualification

Implement/test đầy đủ:

- forecasting;
- G/L/ALL;
- MT/MTL;
- residual scaling;
- thresholding;
- event streak;
- refractory;
- availability;
- model absence;
- graph-TV;
- trigger-to-RCA boundary.

---

### B. Complete worker/cache/firewall qualification

Kiểm:

- controller/model separation;
- labels/τ/path leakage;
- future timestamps;
- cache key provenance;
- cutoff isolation;
- repeated run determinism;
- raw/cache equivalence.

---

### C. Qualify RCD runtime

- use pinned upstream;
- isolated environment if required;
- exact declared patch;
- license/dependency check;
- real execution, not only stub;
- fidelity delta report;
- preserve failure if incompatible.

Do not silently remove RCD.

If method fundamentally cannot run under justified environment, return D/comparator review as contract says.

---

### D. Acquire missing development objects

Only allowlisted RE2-TT development30.

Verify:

- remote revision;
- object path;
- size/hash;
- schema;
- no final cases;
- no unrelated corpus bulk download.

Audit all expected development telemetry objects.

---

### E. Actual-use full development loader audit

Before model outcome run, all development cases:

- window coverage;
- service universe;
- metric channels;
- trace graph;
- parent resolution;
- logs;
- duplicates/conflicts;
- mappings;
- unavailable objects;
- root-in-candidate diagnostic;
- exact failure reason.

No silent exclusion.

---

### F. Reference graph / R-control qualification

Check actual development reference graphs for:

- nodes;
- edges;
- isolates;
- switchability;
- acceptance;
- mobility;
- overlap;
- degeneracy;
- MC precision diagnostics.

Do not infer R validity from two sample graphs.

---

### G. Predetermined smoke

Use exactly the pre-outcome deterministic smoke rule already registered.

Do not choose cases after seeing score.

Smoke must check:

- pipeline runs;
- finite values;
- intermediate shapes;
- graph participation;
- evaluator separation;
- resource feasibility;
- no leakage.

Smoke does not select method winner.

---

### H. Full authorized development30

If gates pass, run the complete authorized development scope.

## C1

Run registered:

- local variants;
- PPR variants;
- diffusion secondary;
- L/O/R;
- all required folds;
- mandatory diagnostics.

## C5

Run:

- G/L/ALL;
- MT/MTL;
- λ registry;
- q registry;
- graph-TV secondary;
- mandatory OFAT sensitivities.

No final cases.

---

# 14. DO NOT STOP JUST BECAUSE DEVELOPMENT RESULTS ARE BAD

Bad performance is not automatically a blocker.

Examples:

- O < L;
- F1 low;
- C5 unavailable in some cases;
- graph hurts some faults;
- sensitivity unstable;

must be analyzed according to D failure-attribution policy.

Only return D if evidence shows:

- spec invalid/ambiguous;
- implementation cannot satisfy it;
- comparator unfair/incompatible;
- controls uninformative;
- coverage gates fail;
- leakage;
- essential method assumption contradicted;
- registry amendment is necessary before freeze.

Do not invent a new algorithm just because graph loses.

---

# 15. SENSITIVITY AND SELECTION

Run exactly the development sensitivity contract.

No Cartesian brute force unless amended D explicitly requires it.

For every dimension record:

- selected value;
- alternatives;
- OOF performance;
- sign stability;
- margin;
- root/fault/cell variation;
- leave-cell stability;
- coverage;
- cost.

Sensitivity result does not automatically replace primary registry.

Any material registry change requires recorded D amendment before proceeding.

---

# 16. KEEP ALL EVIDENCE

Never overwrite a previous attempt.

Every run gets:

- run ID;
- TD hash;
- code hash;
- data manifest;
- config;
- environment;
- stdout/stderr;
- predictions;
- intermediates;
- failures;
- resources;
- reviewer notes.

Preserve negative/failed runs.

Do not delete embarrassing outputs.

---

# 17. FINAL SCIENTIFIC REVIEW

After full authorized development execution, use fresh independent reviewers if available.

At minimum separate:

### Reviewer 1 — numerical/data correctness

### Reviewer 2 — leakage/evaluator

### Reviewer 3 — scientific interpretation

They must inspect actual evidence, not only coordinator summary.

Coordinator reconciles disagreements.

---

# 18. FINAL REQUIRED QUESTIONS

At the end answer, with executable evidence:

1. Local evidence có informative không?
2. Local-only có gần ceiling không?
3. Observed graph có đủ cấu trúc/mobility không?
4. O khác L thế nào?
5. O khác R thế nào?
6. Effect có bị vài cases chi phối không?
7. Result ổn định theo root/fault/cell không?
8. PPR/diffusion có numerical issue không?
9. C5 availability đủ không?
10. G/L/ALL khác nhau do graph/context hay chủ yếu capacity/missingness?
11. Prefix/residual scale có stable không?
12. Threshold/event behavior có usable không?
13. RCD/BARO comparator fidelity đạt đến đâu?
14. Có leakage không?
15. Có finding nào buộc sửa D trước freeze không?
16. Development evidence hiện hỗ trợ claim nào và KHÔNG hỗ trợ claim nào?

---

# 19. WHAT “FINAL RESULT” MEANS IN THIS RUN

Tôi muốn phiên này đi đến **kết quả cuối cùng của development preflight**, không dừng ở “đã chạy vài test”.

Một trong bốn verdict:

### PASS — READY FOR NEXT AUTHORIZED STAGE

- D deterministic;
- E gates pass;
- development run complete;
- sensitivity complete;
- no unresolved methodological blocker.

### PASS WITH LIMITATIONS

- execution valid;
- limitations material nhưng không làm experiment invalid;
- claim boundaries rõ.

### RETURN TO TASK D

- empirical evidence phát hiện method/protocol cần amendment mới.

### INVALID RUN — REPEAT AFTER EXECUTION FIX

- lỗi implementation/environment làm run không interpretable nhưng không đòi đổi method.

Không tự mở Task F/G dù PASS.

---

# 20. FINAL DELIVERABLES

Cuối phiên phải có:

## A. Executive verdict

Một trong bốn verdict ở trên.

## B. C5 clarification summary

- policy đã chọn;
- alternatives;
- lý do;
- amendment version/hash.

## C. Development evidence table

| Question | Evidence | Result | Confidence |

## D. C1 development results

- configuration selection;
- L/O/R;
- MRR/Hit/NDCG;
- coverage;
- ties;
- failures;
- sensitivity.

Không gọi là final evaluation.

## E. C5 development results

- G/L/ALL;
- MT/MTL;
- F1/P/R;
- coverage;
- events;
- calibration;
- sensitivity;
- failures.

Không gọi là production performance.

## F. Comparator fidelity

BARO/RCD exact status.

## G. Failure attribution

Điều gì là:

- method weakness;
- data weakness;
- implementation bug;
- expected limitation;
- unresolved blocker.

## H. Files changed

Exact path + reason + hashes.

## I. Reproducibility

Commands/config/env/data/code IDs.

## J. Remaining OPEN items

Chỉ những việc thật sự phải để stage sau.

---

# 21. WORKING STYLE

Đây là một long-horizon execution task.

- Lập TODO/phase plan ngay đầu.
- Dùng subagents để giảm anchoring khi hữu ích.
- Đừng hỏi tôi những gì có thể tự xác minh.
- Đừng dừng sau khi sửa D nếu E có thể tiếp tục an toàn.
- Đừng dừng sau smoke nếu full development được phép và gates đã pass.
- Đừng dừng sau full development nếu sensitivity/review còn thiếu.
- Đừng cố tạo kết quả đẹp.
- Đừng tự nhận graph thành công.
- Đừng tự nhận graph thất bại chung chỉ vì một configuration âm.
- Chỉ dừng khi đạt final verdict hoặc gặp quyết định thật sự ngoài quyền đã cấp.

Mục tiêu của phiên này là:

**chốt hai ambiguity bằng reasoning độc lập, biến Task D thành deterministic executable specification, rồi dùng development evidence thực để tìm mọi điểm yếu còn lại trước khi nhóm đầu tư sâu hơn.**