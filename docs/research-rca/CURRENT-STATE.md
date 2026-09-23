# RCA — Current state

Chủ sở hữu: **Minh**. Cập nhật **2026-09-23**. `CURRENT_STATE`: checkpoint hiện hành duy nhất; completion không thay human approval hoặc kết quả đo.

| Trạng thái | Nội dung |
|---|---|
| DONE | A evidence map COMPLETE/document DRAFT; B CLOSED; C Phase1+2 COMPLETE; **Pre-D READY WITH D-OWNED CONDITIONS; Task D specification/validation COMPLETE** |
| CURRENT | [TD-v1.0](task-d-method-and-experiment-specification.md) **REVIEW_READY**, kỹ thuật CANDIDATE; [D handoff](task-d-handoff.md) chứa validation và minimum continuation pack |
| NEXT | **Task E — Baseline Reproduction / Calibration**, chưa bắt đầu |
| AUTHORIZATION | Minh đã cho phép D qua continuation contract22/23-09. **Chờ Minh chấp thuận TD-v1.0 và giao execution E**, đúng request§36/master D/E; không phải thiếu quyền tiếp tục D |
| LATER DEPENDENCY | FlashTicket readiness chỉ chặn target validation H khi tới bước đó; không chặn E–G. Full experimental corpus chưa được lưu; retrieval/env phải thuộc scope E được giao |

**Quyết định con người không đổi:** [RCA-001–017](RESEARCH-DECISIONS.md), [C Phase2](task-c-research-decision-lock.md). C1 primary; empirical/system/reproducibility contribution và valid negative; C2 optional; C3/C4 còn trong design; C5/LLM intended capabilities vẫn mandatory. Những dòng “D chưa bắt đầu/chờ lệnh” trong C/master/bootstrap là snapshot trước lệnh hiện tại; không dùng làm live status.

**D đã đặc tả:** common metric+trace occurrence evidence; identity/observed/rewired graph controls giữ local score/candidates/degree/components theo contract; 30dev/60eval grouped incidents; tie-aware MRR/δ/conditional uncertainty/denominators; C3 operation support, C4 late graph placement, separate C5/LLM/adapter contracts. Chưa claim baseline chạy được hoặc graph hiệu quả.

**Phát hiện quan trọng:** historical E1 đã xem TT baseline outputs/metrics; evaluation sau D không gọi untouched hoặc độc lập hoàn toàn. [Exposure ledger](D:/Project/flash-ticket-rca-research/task-d/task-d-exposure-ledger.md) giữ mức tiếp xúc và split. [Evidence ledger](D:/Project/flash-ticket-rca-research/task-d/task-d-evidence-ledger.md) có bounded primary checks, Pre-D và main adjudication; [independent review](D:/Project/flash-ticket-rca-research/task-d/task-d-independent-review.md) giữ từng pass/closure, không voting.

**NEXT EXACT ACTION:** Minh chấp thuận protocol và giao Task E. Phiên mới đọc D handoff/minimum pack; bắt đầu từ input manifests và synthetic/invariance/evaluator fixtures, pin declared baseline adapter/environment; không mở lại RQ, không root-label tuning, không bulk download cho tiện, không chạy G hoặc đổi FlashTicket.

**OPEN và owner:** Minh — human acceptance/E scope; E/F — source pins/license/env, loader actual inputs/all-used-log joins, reference-window mobility/coverage, C5 unlabeled calibration, resource measurements; G — effects dưới frozen protocol; H/I — target/explanation validation. Faculty format/defense date do Minh khi J/K cần. Không còn major specification objection chưa phân xử trong D handoff; execution facts vẫn chưa verified.

**Bằng chứng bàn giao:** [D validation](D:/Project/flash-ticket-rca-research/task-d/task-d-validation.json), [governance log](D:/Project/flash-ticket-rca-research/task-d/task-d-governance-validation.txt). Hai correction giới hạn: Task A§M provenance notice, B2B scope status. Giữ C/decision register/master/B CLOSED/raw/history và README edit sẵn; không commit/push. D chỉ có document/synthetic checks, chưa baseline/train/benchmark/download/install/application changes.

Khi bị ngắt, kiểm artifacts này và HEAD/working trees; tiếp tục phần thiếu, không restart reviewer hoặc A/B/C vì quota. Roadmap duy nhất [MASTER](MASTER-RESEARCH-PROGRAM.md); định vị bằng [ARTIFACT-MAP](ARTIFACT-MAP.md).
