# RCA — Current state

Chủ sở hữu: **Minh**. Cập nhật **2026-09-22**. Lớp `CURRENT_STATE`; đây là checkpoint hiện hành duy nhất của chương trình. Trạng thái công việc không thay quyền của nguồn quyết định hoặc kết quả đo.

| Trạng thái | Nội dung |
|---|---|
| DONE | Task A evidence map hoàn tất (document vẫn DRAFT về phê duyệt); Task B CLOSED; Task C Phase 1 review hoàn tất; **TASK C PHASE 2: COMPLETE** theo lựa chọn Minh tại RCA-001–017 |
| CURRENT | Bàn giao gói decision lock + master program + ngữ cảnh bền vững; dừng đúng phạm vi tài liệu |
| NEXT | **Task D — Method and Experimental Protocol Specification**, chưa bắt đầu |
| BLOCKED | Không có hard external blocker đã biết cho việc đặc tả D. Đang **chờ lệnh bắt đầu D của Minh** theo RCA-017; đây là ranh giới ủy quyền, không phải thiếu dữ liệu audit |
| LATER DEPENDENCY | FlashTicket readiness chỉ chặn H/target validation khi tới bước đó; không chặn D–G. Corpus thực nghiệm chưa được lưu đầy đủ; E sẽ chuẩn bị theo phạm vi được phép |

**Quyết định hiện hành:** C1 primary; empirical + system + reproducibility contribution, không buộc novelty phương pháp; valid negative được chấp nhận. C2 optional; C3/C4 phải còn trong thiết kế/báo cáo, exact experiments conditional; C5 và LLM explanation là năng lực dự kiến bắt buộc. Chi tiết có thẩm quyền tại [sổ quyết định](RESEARCH-DECISIONS.md) và [C Phase 2](task-c-research-decision-lock.md).

**Nguồn tiếp tục:** [SESSION-BOOTSTRAP](SESSION-BOOTSTRAP.md) → [C handoff §6](task-c-research-decision-lock.md) → [master §Task D và hợp đồng chung](MASTER-RESEARCH-PROGRAM.md) → [B capability](task-b-dataset-capability-summary.md). [ARTIFACT-MAP](ARTIFACT-MAP.md) định vị bằng chứng khi thật sự cần. Master là lộ trình duy nhất; README ngoài workspace và checkpoint C cũ chỉ trỏ về đây.

**NEXT EXACT ACTION:** chờ Minh yêu cầu bắt đầu Task D. Khi có yêu cầu: dùng minimum pack trong bootstrap, xác nhận input versions và viết `docs/research-rca/task-d-method-and-experiment-specification.md` cùng handoff; tổ chức phản biện protocol. Không bắt đầu bằng chạy baseline, tải dữ liệu hay cài framework.

**OPEN có chủ sở hữu:** Minh/Task D khóa mechanisms, feature/reference construction, controls, metrics/statistics, split/exposure policy, baseline compatibility, disposition C3/C4 và hợp đồng detector/evidence packet/adapter. Minh xác nhận mẫu khoa/ngày bảo vệ khi cần J/K. Các điểm này không mở lại lựa chọn primary RQ và không cản việc bắt đầu đặc tả D khi được giao.

**Bằng chứng bàn giao:** review trung lập hai bước và main adjudication trong ARTIFACT-MAP; validation tại `W/program-review/program-package-validation.json`, governance log cùng thư mục. Không có thí nghiệm, download, install hoặc sửa application trong phiên này. Phase 1 được giữ nguyên thân bài, chỉ thêm notice liên kết; không rerun A/B/C hoặc reviewers. Manifest phiên này kiểm preservation của các tệp được liệt kê, **không nhận đã rehash lại toàn bộ raw dataset**.

Nếu bị ngắt phiên sau: lưu artifact trước rồi cập nhật CURRENT/NEXT/OPEN tại đây. Không lấy một heading “current” bên trong lịch sử checkpoint cũ làm chỉ dẫn mới.
