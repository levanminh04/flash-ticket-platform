# RCA — Đọc đầu phiên

Chủ sở hữu: **Minh**. Lớp `CURRENT_STATE` — chỉ dẫn phục hồi, **không sở hữu trạng thái chạy**; trạng thái sống ở [CURRENT-STATE](CURRENT-STATE.md). Binding máy: `PLATFORM_ROOT=D:/Project/flash-ticket-platform`; `RCA_WORKSPACE_ROOT=D:/Project/flash-ticket-rca-research`.

**Mục tiêu:** theo [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md), xây dựng/đánh giá hệ bán vé phân tán và dùng graph để giám sát/chẩn đoán. Nhánh RCA làm public benchmark trước, kiểm chứng FlashTicket sau. **C1** là primary RQ về giá trị observed service relations cho known-window root-service ranking khi so sánh công bằng. C2 optional; C3/C4 phải hiện diện như supporting design, triển khai tùy D; C5 first-class graph AD (RCA-024) và detection→RCA; LLM downstream explanation bắt buộc. Không yêu cầu novelty thuật toán hoặc kết quả dương.

## Minimum pack để tiếp tục Task D

Đọc đúng **sáu tệp nội dung** dưới đây; với master chỉ cần §§1–3, Task D/E, §§5–6. Đọc AGENTS và skill bắt buộc của repository theo môi trường trước tác vụ; chúng là quy tắc vận hành, không thay sáu nguồn nội dung.

1. [SESSION-BOOTSTRAP.md](SESSION-BOOTSTRAP.md) — tệp này.
2. [CURRENT-STATE.md](CURRENT-STATE.md) — DONE/CURRENT/NEXT/quyền thực hiện.
3. [task-c-research-decision-lock.md](task-c-research-decision-lock.md) — RQ, giới hạn, handoff §6.
4. [MASTER-RESEARCH-PROGRAM.md](MASTER-RESEARCH-PROGRAM.md) — contract Task D và đường phụ thuộc.
5. [task-b-dataset-capability-summary.md](task-b-dataset-capability-summary.md) — data/GT/leakage/readiness.
6. [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md) — nhiệm vụ chính thức phải đọc trước diễn giải mục tiêu.

Trạng thái khi lập bootstrap: C Phase 2 hoàn tất; D chưa được khởi động. Nếu CURRENT-STATE mới hơn thì dùng trạng thái ở đó. Khi cần xác nhận ý định hoặc đổi quyết định, mở đúng ID ở [RESEARCH-DECISIONS](RESEARCH-DECISIONS.md); khi thiết kế comparator, mở đúng mục Task A/primary source theo [ARTIFACT-MAP](ARTIFACT-MAP.md). Sáu tệp đủ **phục hồi và bắt đầu**, không thay bằng chứng cần truy xuất để hoàn thành D.

## Quyền nguồn và thao tác tiếp tục

Ý định hiện hành của Minh → DT18/hiến pháp → C Phase 2 trong phạm vi RQ → B CLOSED cho data facts → A primary synthesis cho prior work → C Phase 1/gates → reviewer detail → advisor history. Sổ quyết định sở hữu câu xác nhận; C lock sở hữu contract; master sở hữu lộ trình; CURRENT sở hữu trạng thái. Quyết định con người không sửa sự thật dataset. Có xung đột quan trọng thì dừng thay đổi liên quan, truy nguồn và nêu rõ.

1. Xác nhận hai root, thay đổi đang có, CURRENT và input versions. Không cần raw rehash nếu không có dấu hiệu/nhu cầu integrity cụ thể.
2. Xác nhận yêu cầu mới cho phép bước nào. U26 chỉ cấp D redesign; **E NOT STARTED / NOT AUTHORIZED**. TD-v1.2 design review-ready with assurance blocker, chưa empirical selection/freeze.
3. Đọc handoff và minimum pack, rồi truy xuất hẹp cho câu hỏi cụ thể. D–G không đợi FlashTicket; chỉ H cần runtime/controlled telemetry.
4. Lưu kết quả vào đúng root, kiểm, cập nhật handoff rồi CURRENT. Phân biệt worker kết thúc với artifact đã hoàn tất; quota ngắt không có nghĩa mất dữ liệu hay phải restart.

**Không đọc lại mặc định:** toàn bộ chat; toàn bộ A/B/C; 65 inputs C; blind A–F/gates/Red Team; raw Parquet/JSON/audit scripts; E1/A7–A10 lịch sử. Chỉ mở khi một câu hỏi cụ thể cần chúng. Không rerun audit hoặc tải lại dataset để “chắc chắn”.

**Không suy diễn:** root label ≠ anomaly-node label; operation evidence ≠ operation GT; span relation ≠ causal edge; time alignment ≠ request join; graph gain ≠ novelty; explanation ≠ sửa rank/GT. Không dùng injected-service set/path/root/fault/oracle metadata làm input. Technical registry hiện ở TD-v1.2; dev selection RCA-026–030 được phép, runtime/final tuning cấm. Đọc handoff/protocol trước tiếp tục.

**Hai root:** repository giữ quyết định, contract, synthesis, state và handoff; workspace giữ data/env/code/run/reviewer chi tiết. Không quyết định con người external-only, không dataset lớn trong repo, không roadmap thứ hai. Hai cửa nối hệ thống vẫn là R0 §3 và `docs/project/lien-ket-rca.md`; artifact map không tạo cửa thứ ba.


**Current override 26/09:** handoff dẫn TD-v1.2 packet/provenance/memos/validation. D §3/8/13 sở hữu selection/sensitivity/freeze. Dòng D chưa bắt đầu chỉ giữ snapshot lúc lập bootstrap; live state ở CURRENT. Ba distinct agents thực hiện năm vai trò chưa thỏa năm independent reviewers; human approval OPEN.
