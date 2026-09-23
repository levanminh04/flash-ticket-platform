# Task D — Handoff

- Ngày: **2026-09-23**. Chủ sở hữu: **Minh**. Protocol hiện hành: **TD-v1.1**.
- **STATUS: REVIEW_READY — hoàn tất revision/specification và independent review; human approval OPEN. Task E: NOT STARTED / NOT AUTHORIZED.** Bằng chứng kiểm tra cuối ở receipt phía dưới; review-ready không là kết quả thực nghiệm hoặc phê duyệt của con người.
- PURPOSE: giữ nguyên C1 và sửa khoảng thiếu graph-conditioned detection/multimodal specification, provenance, metric/program ownership. Các lựa chọn kỹ thuật vẫn **CANDIDATE**.

## Quyền nguồn và lịch sử

[Protocol TD-v1.1](task-d-method-and-experiment-specification.md) là đặc tả duy nhất. [Reconciliation evidence](D:/Project/flash-ticket-rca-research/task-d/td-v1.1-reconciliation-evidence.md) chứa conflict matrix28 hàng, modality audit, pipeline, component roles, GT/metric matrix, new findings và **exact15-file inventory**. [Advisor source23/09](../evidence/advisor-direction/2026-09-23-huong-dan-do-minh-cung-cap.md) giữ nguyên văn hướng dẫn và câu Minh làm rõ.

**Quyết định mới thực sự của con người: RCA-018–021**, ghi nguyên tử trong [phân sổ](RESEARCH-DECISIONS.md) và đăng ký tại sổ dự án: giữ tên DT18; hướng dẫn thuộc giai đoạn RCA-primary trước đây; còn giá trị cho phương pháp RCA; không dùng để giảm nhẹ FlashTicket. Không phải giảng viên duyệt TD. Ngày/kênh gửi gốc vẫn NOT VERIFIED. RCA-001–017/C lock/root-label policy giữ nguyên.

TD-v1.0 được bảo tồn ở P commit `6bd04e625926e923301cc6bb1433682f01fd87fc`, protocol SHA256 `9822a92081fb5d958fde35b68d274906bd36ee66288017645a7ec6840a2c1c4d`; W commit `c7e47fdcc69c53876f7bd3f1a5e71ef89c59ccf6` giữ evidence/review/validation cũ. TD-v1.1 đã commit/push lên P branch `codex/rca-research-program` (commit nội dung `a4a2fa3`) và W branch `main` (commit nội dung `9eca376`). README edit sẵn vẫn chỉ ở local, chưa commit/push.

## Thay đổi có ý nghĩa và phạm vi không đổi

- **C1 giữ nguyên:** known-window M+T; cùng V/windows/features L/O/R; solver/controls/seeds; split30/60; MRR/δ=.05/inference/verdict/failure rules và baselines. NDCG@5 chỉ phụ, tie/miss/failure rõ.
- **C5 candidate:** reference trace graph tác động score trước threshold; bốn configs G-M/G-MT/G-MTL/L-MTL. M/T/L là detector node evidence; mọi config vẫn có trace-derived candidates, G có trace topology. Nested comparisons có điều kiện, không full factorial/new primary RQ.
- **Logs:** LOGCOUNT-v1 theo service/time, không templates/Drain/GNN bắt buộc, không gán log vào edge. Count scoring có giới hạn; thiếu logs0+mask, không loại ca, không hứa equal alerts giữa configs.
- **Triggered integrated diagnosis:** cùng MTL profile cho cả bốn detector configs; tách hoàn toàn primary C1. C3 operation support, C4 late-ranking disposition và C2 optional giữ nguyên.
- **Public extension:** D§15/MRP-v1.1 có owner/gate. RE2-SS không trace; LEMMA chưa pinned/mapped. Chưa chọn dataset thứ hai hoặc claim multi-public validation đã hoàn thành.
- Packetv1.1 tách detector/profile/quality khỏi evaluator GT. Source-file-hash log samples có thể thay khi rehash suffix; future invariance chỉ numerical decisions/ranks, không toàn packet/LLM. H owns actual availability semantics.

## Independent review và kiểm tra

[Source-first review](D:/Project/flash-ticket-rca-research/task-d/td-v1.1-source-first-review.md) lưu trước candidate fixes; reviewer đó sau đó challenge proposal. [Fresh delta review](D:/Project/flash-ticket-rca-research/task-d/td-v1.1-delta-review.md) do **reviewer khác**, thử20 attacks và open-ended pass: 0 CRITICAL/MAJOR trong revised specification;3 MINOR về evaluation bin, future-hash sampling và crop streak đã sửa/kiểm lại. Không dùng số reviewer như approval.

[Validation receipt](D:/Project/flash-ticket-rca-research/task-d/td-v1.1-validation.json): actual checks/hashes/preservation, source quote, exact scope/links/UTF8, synthetic graph-before-threshold/convexity/NDCG/calibration/state fixtures. [Governance audit](D:/Project/flash-ticket-rca-research/task-d/td-v1.1-governance-validation.txt) là kết quả kiểm quản trị. Không nhận fixtures là benchmark, full-log compatibility, runtime hoặc experimental success.

[Exposure ledger v1.0](D:/Project/flash-ticket-rca-research/task-d/task-d-exposure-ledger.md) vẫn có hiệu lực: E1 đã xem TT outcomes; không untouched test. Đọc revision evidence cho thêm source/metadata checks; không new outcome exposure, không raw67M re-audit.

## OPEN, owner và đúng điểm dừng

| OPEN | Owner / timing | Tác động |
|---|---|---|
| Chấp nhận TD-v1.1 và giao execution E riêng | Minh trước E | **E chưa được phép**; không tự chạy từ review-ready |
| Exact second-public task/release/scope | Minh + D/E, trước campaign mới và trước final multi-public claim J/K | Không điều kiện validity C1; không silently waive hoặc giả graph của SS |
| Source pins/licenses/env, BARO adaptation fidelity | E trước freeze | Không evidence đã chạy |
| Full used-input loader/log joins, C5 calibration reachability/resources | E/F trước G | Giữ missing/error cases; method revision phải review |
| C1 reference graph mobility/coverage/flat evidence | E/F label-free validation | Giữ nguyên numeric gates; không dùng GT chọn phương pháp |
| Effects/robustness/negative outcomes | G | Đo sau freeze; không ép graph/MTL thắng |
| Target timing/healthy controls/integration và DT18 system evaluation | H + system team | Không giảm phạm vi FlashTicket; public không thay target |
| LLM model/prompt/rubric/faithfulness/usefulness | I | Không rerank/repair/GT creation; chưa chạy |
| Original advisor date/channel | Minh khi có nguồn | Relative historical context đã xác nhận; không chặn nghiên cứu phương pháp |

**NEXT EXACT ACTION:** Minh đọc/duyệt TD-v1.1 và giao scope Task E nếu chấp nhận. Không execute E/F/G/H/I hoặc tự thêm dataset campaign trong phiên D này.

Minimum pack sau AGENTS/skill/DT18: CURRENT-STATE → handoff này → toàn TD-v1.1 → B digest → exposure ledger → MRP§2–3/TaskE; mở revision evidence/delta review đúng vấn đề. Không restart survey/raw audit hay reviewer đã xong.

**Formal-report material:** rationale/limits của mechanism; primary versus C5/integrated profiles; source chronology/DT18; multimodal nested comparisons/missingness; one-root NDCG; bounded GT; historical exposure và finite-scenario inference; public-extension/target/LLM evidence riêng. Chưa có số hiệu năng mới để đưa vào báo cáo.
