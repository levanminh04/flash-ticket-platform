# RCA — Current state

Chủ sở hữu Minh. Cập nhật **2026-09-26**. Đây là live checkpoint duy nhất, không thay human approval hoặc kết quả thực nghiệm.

| State | Nội dung |
|---|---|
| DONE | A evidence map; B CLOSED; C Phase1+2; TD-v1.0/v1.1 history giữ; **TD-v1.2 source-first redesign, ring reconciliation, delta closure và document/math validation** |
| CURRENT | [TD-v1.2](task-d-method-and-experiment-specification.md) technical **CANDIDATE**, **REVIEW_READY WITH BLOCKERS — do not start E** |
| NEXT | Agent review/nhận định/phản biện những thay đổi TD-v1.2 đã push theo một prompt riêng; assurance năm independent reviewers và human acceptance/E scope vẫn OPEN |
| AUTHORIZATION | U26 cho D review/redesign và bounded compatibility/math/document checks. **Task E NOT STARTED / NOT AUTHORIZED**; chưa training, baseline runs, full-corpus retrieval, empirical selection/sensitivity hoặc final freeze |
| LATER | E/F development selection+sensitivity+fidelity+actual-use audits khi được giao; G locked evaluation; H FlashTicket; I LLM |

[Human decisions RCA-022–042](RESEARCH-DECISIONS.md) append đúng nguồn [U26](../evidence/project-direction/2026-09-26-rca-task-d-redesign.md); RCA-001–021 giữ nguyên. Scoped dev-label permission supersedes prohibition development selection/calibration, không runtime/final tuning. C5 first-class không thay C1 primary. Exact PPR/ridge/TV/RCD/folds/threshold choices là CANDIDATE.

[Handoff](task-d-handoff.md) → [A–J review packet](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-review-packet.md) → [memos](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-review-memos.md) → [provenance/data](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-provenance-and-data.md). Ba distinct agents thực hiện năm vai trò và đủ các cạnh phản biện; chưa thỏa năm independent agents do native thread limit. B/C/D đã đọc TD-v1.2 và đóng các finding delta họ xác định trong scope review; không thay blocker hoặc human approval.

[Validation receipt](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-validation.json): **126/126 PASS** cho document/math/scope. [Governance](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-governance-validation.txt): PASS với một impact-map warning; impact map và U26 authorization đã có. [Metadata receipt](D:/Project/flash-ticket-rca-research/task-d/td-v1.2-public-metadata.json): 90 RE3-TT HTTP206 footers, 6,026,998 bytes, schema/count checks đạt; không lấy remote telemetry rows. Một existing raw sample được kiểm, không chứng nhận joins của cả 30 ca. E1 TT90 outcome exposure vẫn hiệu lực; không gọi là clean test.

**Publication — 26/09/2026:** Minh yêu cầu push và một prompt review những thay đổi vừa push. Payload P đã push lên [codex/rca-research-program](https://github.com/levanminh04/flash-ticket-platform/tree/codex/rca-research-program), commit 735695b9cc10bff03e4c7d738a0b59764de694dd; W lên [main](https://github.com/levanminh04/flash-ticket-rca-research/tree/main), commit 592184c6fd7b4da90f501a6339d01701a4776b62. Local/remote branch refs đã xác minh khớp. [Publication receipt](https://github.com/levanminh04/flash-ticket-rca-research/blob/main/task-d/td-v1.2-publication-receipt.md) ghi exact inventory/hashes; [một review prompt](https://github.com/levanminh04/flash-ticket-rca-research/blob/main/task-d/td-v1.2-independent-review-prompt.md) có hai repo/branches, hai root tương đối và yêu cầu đọc sâu/phản biện bằng subagents. Handoff commits chỉ cập nhật publication/navigation, không tự đổi phương pháp.

**Historical snapshot:** P fa27a9d32873d957818ac389b8d3fc8f4e98b185 / W 38a0d0362e1e51a56ba3a6334a7f7c13a036f603 là HEAD trước redesign. Các câu local/uncommitted/chưa push trong packet/handoff/receipt cũ mô tả thời điểm tạo, không là publication state hiện hành. Validation giữ nguyên bằng chứng prepublication; không rerun mù các historical HEAD/path assertions trên branch mới. P README dirty từ trước vẫn chưa commit/push. Không đổi application/API/schema/raw/legacy; publication không cấp approval hoặc Task E authorization.

**OPEN:** assurance năm independent agents; human acceptance/E scope; future empirical compatibility/selection/sensitivity/fidelity/resources; exact authorized extension campaign/license; G effects; H integration/system evaluation; I explanation; advisor original send date/channel. Chưa có báo cáo hiệu quả đo được mới.
