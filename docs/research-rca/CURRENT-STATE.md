# RCA — Current state

Chủ sở hữu Minh. Cập nhật **2026-09-26**. Đây là live checkpoint duy nhất, không thay human approval hoặc kết quả thực nghiệm.

| State | Nội dung |
|---|---|
| DONE | A evidence map; B CLOSED; C Phase1+2; TD-v1.0/v1.1 history giữ; **TD-v1.2 source-first redesign, ring reconciliation, delta closure và document/math validation** |
| CURRENT | [TD-v1.2](task-d-method-and-experiment-specification.md) technical **CANDIDATE**, **REVIEW_READY WITH BLOCKERS — do not start E** |
| NEXT | Xử lý assurance năm distinct independent reviewers; Minh review/accept protocol và giao E scope riêng |
| AUTHORIZATION | U26 cho D review/redesign và bounded compatibility/math/document checks. **Task E NOT STARTED / NOT AUTHORIZED**; chưa training, baseline runs, full-corpus retrieval, empirical selection/sensitivity hoặc final freeze |
| LATER | E/F development selection+sensitivity+fidelity+actual-use audits khi được giao; G locked evaluation; H FlashTicket; I LLM |

[Human decisions RCA-022–042](RESEARCH-DECISIONS.md) append đúng nguồn [U26](../evidence/project-direction/2026-09-26-rca-task-d-redesign.md); RCA-001–021 giữ nguyên. Scoped dev-label permission supersedes prohibition development selection/calibration, không runtime/final tuning. C5 first-class không thay C1 primary. Exact PPR/ridge/TV/RCD/folds/threshold choices là CANDIDATE.

[Handoff](task-d-handoff.md) → [A–J review packet](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-review-packet.md) → [memos](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-review-memos.md) → [provenance/data](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-provenance-and-data.md). Ba distinct agents thực hiện năm vai trò và đủ các cạnh phản biện; chưa thỏa năm independent agents do native thread limit. B/C/D đã đọc TD-v1.2 và đóng các finding delta họ xác định trong scope review; không thay blocker hoặc human approval.

[Validation receipt](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-validation.json): **126/126 PASS** cho document/math/scope. [Governance](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-governance-validation.txt): PASS với một impact-map warning; impact map và U26 authorization đã có. [Metadata receipt](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-public-metadata.json): 90 RE3-TT HTTP206 footers, 6,026,998 bytes, schema/count checks đạt; không lấy remote telemetry rows. Một existing raw sample được kiểm, không chứng nhận joins của cả 30 ca. E1 TT90 outcome exposure vẫn hiệu lực; không gọi là clean test.

**Git snapshot:** P codex/rca-research-program fa27a9d32873d957818ac389b8d3fc8f4e98b185; W main 38a0d0362e1e51a56ba3a6334a7f7c13a036f603. TD-v1.2 đang local, chưa commit, **chưa push**. P README dirty từ trước được bảo tồn. Không đổi application/API/schema/raw/legacy. Packet có từng tệp, lý do và checks.

**OPEN:** assurance năm independent agents; human acceptance/E scope; future empirical compatibility/selection/sensitivity/fidelity/resources; exact authorized extension campaign/license; G effects; H integration/system evaluation; I explanation; advisor original send date/channel. Chưa có báo cáo hiệu quả đo được mới.
