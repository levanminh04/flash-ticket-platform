# Task D — Handoff TD-v1.2

Owner: Minh; 26/09/2026. Technical choices vẫn **CANDIDATE**. **REVIEW_READY WITH BLOCKERS — do not start E**. Human approval OPEN; E NOT STARTED / NOT AUTHORIZED. Đây là đặc tả selection/execution, chưa phải cấu hình đã empirical-selected hoặc frozen.

## 1. Đọc trước khi tiếp tục

Đọc AGENTS và governance skill trước, sau đó [TD-v1.2](task-d-method-and-experiment-specification.md), [CURRENT](CURRENT-STATE.md), [C lock](task-c-research-decision-lock.md), [RCA-001–042](RESEARCH-DECISIONS.md), [mission U26](../evidence/project-direction/2026-09-26-rca-task-d-redesign.md), [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md), [Master](MASTER-RESEARCH-PROGRAM.md) và [B digest](task-b-dataset-capability-summary.md).

P đang ở branch `codex/rca-research-program`, HEAD `fa27a9d32873d957818ac389b8d3fc8f4e98b185`; W ở `main`, HEAD `38a0d0362e1e51a56ba3a6334a7f7c13a036f603`. P main cũ thiếu current D; không dùng default branch để thay nguồn hiện tại. README dirty từ trước không thuộc task và đã được bảo tồn.

Evidence chi tiết: [packet A–J](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-review-packet.md), [memos/ring/delta](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-review-memos.md), [parameter/data matrices](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-provenance-and-data.md), [prechange inventory](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-prechange-inventory.json). Old TD-v1.1 reviews, validation, exposure và original decision rows được bảo tồn.

## 2. Những thay đổi chính và lý do

**C1 giữ primary RQ.** Các nhánh dùng chung M+T, candidate universe và input rights. Tám robust local variants không cap được chọn bằng grouped absolute L-MRR; sáu PPR variants được chọn bằng absolute O-MRR. Row-value diffusion là secondary; 256 R chains giữ directed/undirected degrees và component partition với finite-control/MC limits được khai rõ. PPR mass khác value propagation; solver được chuẩn hóa trước khi giải để tránh lỗi số.

**C5 trở thành first-class graph forecasting/residual track.** Prefix 180s gồm first 120s graph/scaler/model fit và 60s held-out residual scale. Lagged caller/callee features tạo G/L/ALL controls cùng nominal budget; active capacity vẫn khác và phải báo. Common λ được chọn bằng joint normal forecast loss; shared weighted-CDF threshold chỉ fit trên development. TV là secondary detector. Scale từ 9–12 errors không được gọi q99/FPR. Triggered diagnosis dùng past 300s reference/60s query; giữ first-trigger failures, không tạo node GT.

**Baselines và public validation có contract cụ thể.** Local/BARO/RCD adapted là executable contextual comparisons. RCD có pinned time-drop-after-split patch, raw 1s metric input và service first-occurrence/worst-tie/failure-seed policies. Fidelity/environment phải kiểm ở E. RE3-TT transfer trên cả 30 ca, khóa RE2-selected config và **numeric RE2 thresholds**, chỉ cho case-local prefix fitting đã khai; headline equal-cell. RE3-OB là cross-application candidate; SS/LEMMA có component roles, không graph giả hoặc pooled GT. Footers không chứng nhận joins/semantics của dữ liệu thực dùng.

**Development selection được mở đúng phạm vi.** RCA-026–030 ghi quyền mới nguyên tử; lịch sử giữ nguyên. Runtime/final labels vẫn bị chặn, registry phải định trước, sensitivity phải chạy trước freeze. Instability dẫn review hoặc giới hạn claim, không retune đến khi gain dương. DT18, hai cửa nối tài liệu và immutable downstream LLM vẫn giữ hiệu lực.

**C3/C4 disposition được giữ rõ ở D §1.1.** C3 triển khai supporting operation evidence, hoãn scored-operation ablation vì thay feature count/multiplicity. C4 triển khai late graph ranking, hoãn cross-placement comparison vì thay scorer/capacity làm mất isolation. C5 là task riêng, không phải kết quả so graph placements. Mở rộng cần protocol được review riêng; sensitivity không tự mở C2.

## 3. Assurance và validation

Chỉ có **ba distinct native agents**. B/C/D thực hiện review và B/C kiêm E/A; đủ năm vai trò và các cạnh ring nhưng không đủ năm reviewer độc lập. Native allocation trả lỗi `agent thread limit reached`. Điều kiện assurance này vẫn là blocker.

B/C/D đã đọc lại canonical D và đóng các finding về loss mask, solver scale, starvation gates, RCD time input, CDF weights và numeric threshold transport. Không còn must-fix họ xác định trong phạm vi review; đây không phải approval hoặc bảo đảm không còn lỗi.

[Validation receipt](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-validation.json): **126/126 PASS** cho document/provenance/history/scope/links/UTF-8/preservation và synthetic math. [Governance](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-governance-validation.txt): **PASS, 1 warning** về impact map cho diff 12 tệp. Impact map đã công bố và U26 §15 cho phép sửa; số 12 gồm README dirty không thuộc task. Old validator chạy trước sửa đạt 95/97, chỉ fail hai historical HEAD assertions; old receipt không ghi đè. Validator mới có deliberate contract changelog.

[Bounded metadata receipt](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-public-metadata.json): 90/90 RE3-TT footers, không lỗi, không lấy remote telemetry rows hoặc tạo model outputs. Các PASS này không chứng nhận empirical sensitivity, actual-use loaders/joins, runtime firewall, comparator fidelity, model benefit hoặc LLM quality.

## 4. OPEN — owner và thời điểm

| Item | Owner / timing | Ý nghĩa |
|---|---|---|
| Năm distinct independent source-first reviewers | Coordinator trong session có đủ capacity, trước E | Không được tính năm vai trò từ ba agents thành đủ độc lập |
| Human protocol acceptance và E scope | Minh, trước E | REVIEW_READY không tự cấp execution authorization |
| Empirical selection/sensitivity/registry instability | E/F khi được giao, trước G | D quy định cách làm; chưa freeze cấu hình |
| Fidelity/env/license; actual-used maps/joins/prefix/resources/R mobility | E/F trước freeze | Lưu failures, không sửa answer hoặc âm thầm loại ca |
| Public-extension campaign; LEMMA license/GT/time | Minh + D/E, trước campaign | Scientific roles đã đề xuất; measured multi-public evidence chưa hoàn thành |
| Effects, bounded negative hoặc inconclusive | G sau seal/freeze | Không hứa graph thắng hoặc broad graph claim |
| FlashTicket controlled truth/arrivals/integration/system quality | H + system team tại gates riêng | Public datasets không thay kiểm chứng target |
| LLM provider/rubric/faithfulness/usefulness | I | Không đổi ranking hoặc tạo proof of diagnosis |
| Advisor original send date/channel | Minh nếu còn nguồn | Relative historical context đã xác nhận; không phải method blocker |

## 5. Next action và nội dung báo cáo đồ án

Hoàn thành điều kiện reviewer độc lập còn thiếu và human review của packet. Nếu Minh chấp nhận, cần explicit assignment cho phạm vi E. Handoff này không cho phép bắt đầu baseline runs, bulk downloads, framework installs, training hoặc final campaign.

Engineer dùng D §3/8/13 cho selection/sensitivity/freeze và §11 cho dataset admission; không tự thay method sau khi thấy final outcomes. Revision hiện chỉ ở local, chưa commit/push.

Báo cáo đồ án cần trình bày task/GT distinctions, prior/adaptation/custom rationale, historical exposure, conditional inference, valid-negative failure attribution, fault-family/cross-application transfer, C5 capacity/threshold limits, immutable LLM và system evaluation theo DT18. Chưa có số liệu hiệu quả mới.
