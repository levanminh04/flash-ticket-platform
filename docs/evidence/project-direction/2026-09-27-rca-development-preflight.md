# Task E development preflight — nguồn ủy quyền 27/09/2026

Trạng thái: USER_CONFIRMED — Lê Văn Minh. Nguồn mission đính kèm có SHA256 `7d2fbf1660d8edc81d0145b918c9c84352ee0a88fccb0ce6f2b7d7c529b9e627`.

## Xác nhận trực tiếp kèm mission

> Tôi cho phép chuẩn bị và chạy thử development giới hạn theo TD-v1.2 để kiểm tra thiết kế. Cho phép thực hiện bước này trước khi đủ 5 agent phản biện, nhưng vẫn giữ yêu cầu phản biện cho đánh giá chính thức. Hãy kiểm tra các điểm chặn thực chất, trình phạm vi và danh sách file cần sửa; không chạy tập đánh giá cuối hoặc tự thay đổi phạm vi đồ án.

## Xác nhận impact map

Minh trả lời “Duyệt gói file và tiếp tục theo các gate đã nêu” cho [impact map §4–5](D:/Project/flash-ticket-rca-research/results/task-e/preflight-plan.md), gồm quyền ghi nhận E, hiện thực và chạy đúng30 development cases nếu các kiểm tra đạt. Không cấp quyền commit/push, F/G, thay phương pháp hay final freeze.

Technical interpretation: TD-v1.2 là baseline CANDIDATE được phép kiểm bằng development, không được tự đổi toàn bộ thành APPROVED. Yêu cầu năm reviewer độc lập được hoãn cho bước development này, không xóa khỏi đánh giá chính thức. Nguồn này supersede đúng lệnh cấm E trong snapshot ngày26/09; không sửa lịch sử hoặc scope DT18.

## Nguyên văn mission đính kèm

---

# MISSION — Deep Development Preflight / Task E Execution

Hai workspace local:

- `D:\Project\flash-ticket-platform`
- `D:\Project\flash-ticket-rca-research`

Mục tiêu của phiên này KHÔNG phải tạo một demo đẹp hoặc lấy vài score khả quan.

Mục tiêu là **cố gắng phá vỡ các assumption của TD-v1.2 bằng code và development data càng sớm càng tốt**, trước khi nhóm đầu tư sâu vào F/G.

Nếu protocol có vấn đề, tôi muốn phát hiện ngay.

Nếu protocol đứng vững, tôi muốn có executable evidence đủ mạnh để tin rằng việc chuyển sang core pipeline là hợp lý.

Không sử dụng final-evaluation outcomes để sửa method.

---

# 0. AUTHORITY

Trước khi chạy:

1. đọc root `AGENTS.md`;
2. đọc governance skill liên quan;
3. xác minh branch/HEAD của cả hai repository;
4. đọc:
   - `CURRENT-STATE.md`
   - `RESEARCH-DECISIONS.md`
   - `MASTER-RESEARCH-PROGRAM.md`
   - `task-c-research-decision-lock.md`
   - canonical `task-d-method-and-experiment-specification.md`
   - `task-d-handoff.md`
   - Task B capability summary;
5. đọc evidence chi tiết ở research workspace khi một check cần nó.

Không dùng historical TD-v1.1 assumption thay TD-v1.2.

Không bắt đầu nếu Task D chưa được human-authorized cho E trong state hiện hành.

---

# 1. EXECUTION PHILOSOPHY

Đây là **falsification-first development preflight**.

Không tối ưu cho:

- số liệu đẹp;
- graph thắng;
- detector có F1 cao;
- hoàn thành nhanh.

Tối ưu cho:

- phát hiện sai assumptions;
- phát hiện leakage;
- phát hiện incompatibility;
- phát hiện degenerate score/graph;
- reproducibility;
- failure attribution;
- biết chính xác khi nào phải quay về D.

Một kết quả xấu nhưng đúng là thành công của Task E.

Một kết quả đẹp nhưng không audit được là thất bại.

---

# 2. USE ASTRA AS A LONG-HORIZON MULTI-AGENT SYSTEM

Nếu native multi-agent/subagent support khả dụng, sử dụng nó.

Không giả vờ nhiều reviewer nếu không có.

## Wave 1 — read-only independent reviewers

Ít nhất các workstream:

### Agent A — Data / Loader / Schema

Kiểm:

- exact data revisions;
- actual files được dùng;
- schemas;
- timestamps;
- service mapping;
- missingness;
- prefix/window availability;
- candidate universe;
- graph parent resolution;
- log/metric/trace joins;
- root target presence.

Không sửa code chính.

### Agent B — Mathematics / Evaluator

Tự implement/check các fixtures nhỏ cho:

- robust local deviation;
- available-modality fusion;
- PPR mass semantics;
- value diffusion;
- ties;
- MRR/Hit/NDCG;
- R perturbation invariants;
- bootstrap/denominator logic;
- C5 residual/threshold/event logic.

Cố tạo adversarial synthetic inputs để phá implementation.

### Agent C — Leakage / Supervision Red Team

Truy dataflow từ filesystem tới model.

Tìm mọi đường mà:

- case name;
- root/fault;
- injection time;
- answer artifact;
- final labels;
- future timestamps;

có thể lọt vào model-facing inputs, cache, feature selection hoặc thresholding.

### Agent D — Comparator Fidelity

Kiểm:

- BARO adaptation;
- RCD adaptation;
- exact upstream revision;
- patch;
- environment;
- inputs;
- output mapping;
- failure semantics.

So sánh với primary source/upstream implementation, không chỉ đọc Task D.

### Agent E — Adversarial Scientific Reviewer

Không code trước.

Đọc outputs A–D và hỏi:

> Nếu những check này PASS, còn cách nào kết quả sau này vẫn misleading vì preflight đã bỏ sót một assumption quan trọng?

---

# 3. COORDINATOR RESPONSIBILITY

Coordinator không chỉ ghép summary.

Phải:

1. adjudicate disagreements;
2. kiểm evidence trực tiếp;
3. quyết định finding nào:
   - implementation bug;
   - data incompatibility;
   - D-spec ambiguity;
   - execution risk;
   - acceptable limitation;
4. chỉ sửa code sau khi root cause rõ.

Nếu subagents không thể cùng edit an toàn, để chúng read-only; coordinator hoặc các implementation agents có ownership tách biệt mới sửa.

---

# 4. PHASE A — REPRODUCIBILITY SNAPSHOT

Trước run đầu tiên, tạo run contract gồm ít nhất:

- run ID;
- TD version/hash;
- P/W Git commits;
- dataset revision;
- exact input IDs;
- file hashes/manifests;
- Python/runtime/package versions;
- hardware;
- command/config;
- random seeds;
- output schema;
- exposure scope.

Không run trước rồi mới cố nhớ lại config.

Không tải full dataset chỉ vì tiện nếu task không cần.

---

# 5. PHASE B — SYNTHETIC / UNIT FALSIFICATION

Trước dữ liệu thật, chứng minh implementation đáp ứng mathematics.

Bắt buộc có fixtures cho ít nhất:

## C1

- zero evidence;
- constant evidence;
- one-hot root evidence;
- two-node directed graph;
- chain;
- star;
- isolate;
- ties;
- PPR mass sum;
- damping extremes trong registry;
- observed / L / R separation;
- R degree/component invariants;
- numerical large-value cases.

## Evaluator

- root rank 1;
- root missing;
- full tie;
- partial tie;
- method failure;
- Hit@K;
- NDCG@5;
- planned denominator.

## C5

- perfect forecast;
- isolated spike;
- correlated neighbor shift;
- missing neighbor;
- missing target;
- constant residual scale;
- threshold equality;
- unavailable bins;
- streak reset;
- refractory behavior.

Nếu fixture fail:

STOP downstream run cho component đó.

Không sửa expected output để làm test pass nếu contract nói khác.

---

# 6. PHASE C — ACTUAL-DATA LOADER PRECHECK

Không chọn case vì biết outcome đẹp.

Dùng development IDs đúng TD-v1.2.

Trước model run, xuất audit cho toàn bộ development scope cần dùng:

- file presence;
- schema;
- timestamp range;
- required window availability;
- service universe;
- root-in-candidate diagnostic;
- graph node/edge counts;
- parent resolution;
- metric channel counts/service;
- trace/log availability;
- duplicates/conflicts;
- all mappings;
- unusable cases với exact reason.

Không silently repair.

Không silently drop.

Một case fail vẫn tồn tại trong planned denominator theo contract.

---

# 7. PHASE D — SMOKE RUN

Chọn smoke subset bằng RULE được ghi trước outcome.

Không hand-pick.

Ví dụ:

- một representative incident từ mỗi development cell/fold cần thiết;
- hoặc deterministic first-ID-by-hash.

Mục tiêu smoke:

- end-to-end process không crash;
- intermediate artifacts đúng shape;
- score finite;
- graph thực sự thay đổi computation;
- cache không future-leak;
- root label không model-facing;
- runtime/resource khả thi.

Smoke result KHÔNG dùng để lựa chọn winner.

---

# 8. PHASE E — FULL AUTHORIZED DEVELOPMENT RUN

Nếu smoke PASS, không dừng ở vài case.

Chạy **toàn bộ development scope mà TD-v1.2 cho phép**.

Không mở final evaluation outcomes.

## C1 development

Chạy đầy đủ finite registry TD-v1.2:

- local variants;
- PPR variants;
- registered secondary diffusion;
- L/O/R;
- required development folds.

Lưu PER CASE:

- raw channel summaries;
- masks;
- selected service universe;
- local vector;
- graph adjacency/hash;
- PPR personalization;
- O/L/R scores;
- ranks;
- ties;
- root rank;
- failures;
- runtimes.

Không chỉ lưu MRR cuối.

## C5 development

Chạy:

- G;
- L;
- ALL;
- registered modality arms;
- λ registry;
- q registry;
- mandatory development sensitivity;
- TV secondary.

Lưu:

- model availability;
- active model count;
- effective dimension/rank;
- residual distributions;
- residual scale;
- threshold;
- score time series;
- coverage;
- trigger events;
- failures.

---

# 9. MANDATORY DIAGNOSTICS

Không được trả lời chỉ bằng bảng tổng score.

Phải kiểm ít nhất:

### Local evidence

- fraction all-zero;
- constant;
- extreme;
- root local-rank distribution;
- channel-count bias;
- missing-modality impact.

### Graph

- graph density;
- isolates;
- direction counts;
- R mobility;
- O/L/R score-distance;
- cases nơi graph thay rank mạnh nhất;
- cases nơi graph làm root rank xấu đi mạnh nhất.

### C5

- scored coverage;
- MODEL_ABSENT rate;
- residual-scale stability;
- active-capacity G/L/ALL;
- normal vs injected score overlap;
- threshold reachability;
- trigger availability;
- false pre-injection triggers theo bounded archival definition.

### Baselines

- source fidelity;
- adapter delta;
- failed seeds/cases;
- metric universe difference;
- runtime/resource.

---

# 10. SENSITIVITY

Chạy đúng mandatory development sensitivity từ TD-v1.2.

Không exhaustive Cartesian search.

Không lấy sensitivity variant tốt nhất rồi tự động thay primary.

Mỗi sensitivity phải trả lời:

- kết luận có đổi dấu không?
- winner có ổn định không?
- margin lớn hay nhỏ?
- case/fault/root nào gây instability?
- instability là parameter issue hay lack-of-signal issue?

Nếu muốn thay registered primary:

STOP.

Tạo amendment proposal về D với evidence.

Không tự đổi rồi tiếp tục.

---

# 11. FALSIFICATION QUESTIONS

Cuối run bắt buộc trả lời bằng evidence:

1. Local evidence có informative hay gần degenerate?
2. Graph có đủ observed structure không?
3. O thực sự khác L không?
4. O khác R có ổn định hay chủ yếu Monte-Carlo noise?
5. Một vài cases có thống trị Δ không?
6. Local-only đã gần ceiling chưa?
7. C5 G có hoạt động vì graph context hay chỉ vì active capacity?
8. Detector availability có đủ để có ý nghĩa không?
9. Prefix/residual-scale có quá unstable không?
10. Missingness có gây systematic bias không?
11. Có dấu hiệu leakage nào không?
12. Baseline adapters có đủ fidelity để giữ làm comparator không?
13. Có assumption nào trong D bị dữ liệu thật bác bỏ?
14. Có finding nào buộc quay lại D trước F/freeze không?

---

# 12. NO CHERRY-PICKING

Bắt buộc lưu:

- mọi run;
- mọi failure;
- config;
- output;
- failed cases;
- negative findings.

Không xóa run xấu rồi chỉ báo run tốt.

Không đổi registry sau khi xem final-like outcomes.

Không gọi development score là final performance.

---

# 13. STOP / RETURN-TO-D RULES

STOP và đề xuất quay lại Task D nếu có ít nhất một trong các nhóm sau:

- protocol không implement unambiguously;
- target/candidate mapping materially sai;
- leakage;
- shared input failure vượt gate;
- graph controls không informative;
- numerical invariants fail;
- comparator fidelity không đạt;
- development registry cho instability nghiêm trọng mà D chưa có policy;
- C5 coverage/calibration không đủ;
- cần thêm feature/operator ngoài registry để “cứu” result;
- cần xem final outcomes để quyết định.

Không tự rescue bằng phương pháp mới.

---

# 14. WHAT COUNTS AS PASS

Task E preflight PASS không có nghĩa graph thắng.

PASS nghĩa là:

- loaders đúng;
- evaluator đúng;
- firewall đúng;
- comparator usable hoặc failure được adjudicate;
- development registry chạy được;
- sensitivity đủ để freeze hoặc giới hạn claim;
- failures được hiểu;
- không còn assumption mới buộc redesign trước bước tiếp theo.

Một valid negative development result vẫn có thể PASS về methodology.

---

# 15. REQUIRED ARTIFACTS

Không chỉ trả lời chat.

Tạo artifact thực theo contract hiện hành trong W, ví dụ dưới:

- source snapshot / source-pin manifest;
- environment lock;
- run manifest;
- exact configs;
- development IDs;
- loader audit;
- evaluator fixture report;
- leakage audit;
- baseline fidelity report;
- per-case intermediate outputs;
- sensitivity matrix;
- failure ledger;
- resource report;
- final preflight adjudication.

Canonical handoff chỉ update sau khi evidence đã tồn tại.

Không tạo 20 markdown files nếu JSON/CSV + một synthesis đủ tốt.

---

# 16. FINAL REVIEW

Sau khi execution hoàn tất, dùng independent read-only reviewer/subagents mới nếu capacity cho phép.

Họ phải review:

- outputs;
- code paths;
- failures;
- leakage;
- sensitivity;
- claimed PASS/FAIL.

Reviewer không được chỉ đọc coordinator summary.

Coordinator reconcile disagreement bằng evidence.

---

# 17. FINAL RESPONSE

Trả cho tôi:

## Executive verdict

Một trong:

- `PASS — READY FOR NEXT AUTHORIZED STAGE`
- `PASS WITH LIMITATIONS`
- `RETURN TO TASK D`
- `INVALID RUN — REPEAT AFTER EXECUTION FIX`

## Evidence table

| Question | Evidence | Verdict | Confidence |

## What broke

Danh sách exact assumptions bị dữ liệu/code bác bỏ.

## What survived

Những assumptions đã có executable support.

## Development-only observations

Bao gồm score nếu có, nhưng ghi rõ không phải final result.

## Required D revisions

Chỉ nếu thực sự cần.

## Next authorized step

Nêu chính xác, không tự mở Task F/G nếu chưa có quyền.

Mục tiêu của phiên này là:

**không để một protocol chỉ “đẹp trên giấy” đi sâu hơn nếu development execution đã cho thấy nó có vấn đề.**