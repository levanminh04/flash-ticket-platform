# RCA — Sổ quyết định nghiên cứu

Chủ sở hữu: **Lê Văn Minh**. Ngày ghi: **2026-09-22**. Loại: `CANONICAL_DECISION`. Chỉ chứa quyết định con người đã xác nhận; lý giải phương pháp và số đo thuộc tạo tác sở hữu tương ứng. Không phải danh sách giả thuyết hay nhật ký reviewer.

## Nguồn và quyền quyết định

Nguồn `U22`: yêu cầu **“MASTER EXECUTION PROMPT — Finish Task C Phase 2 + Establish RCA Master Research Program”** do Minh cung cấp ngày 22/09/2026. Bản gốc: [tệp yêu cầu](<C:/Users/84583/.codex/attachments/20a87370-11e4-425f-850d-6aef6955edcc/Pasted text.txt>); SHA-256 `58359743020A8BC46BE58028AEF8758F1CE3C2552C9EBDCFA8BFF0D1E1F4C9DC`. Các quyết định được lưu ngay tại đây để không phụ thuộc việc attachment còn tồn tại. U22 §1 nói rõ coi các ý này là USER-CONFIRMED; đây là xác nhận của Minh, **không phải bằng chứng giảng viên đã duyệt**.

Sổ này là phần chuyên biệt RCA được dẫn từ [sổ quyết định dự án](../project/decision-register.md). Nội dung nguyên tử bên dưới được duy trì ở một nơi; các tài liệu khác dẫn ID và diễn giải áp dụng, không tạo quyết định cạnh tranh. [Task C Phase 2](task-c-research-decision-lock.md) sở hữu hợp đồng RQ/hypothesis/evaluation; [master program](MASTER-RESEARCH-PROGRAM.md) sở hữu trình tự và cổng thực hiện.

## Quyết định hiện hành

Mọi dòng có ngày **2026-09-22**, người chốt **Lê Văn Minh**, trạng thái **USER_CONFIRMED**, gate **Task C Phase 2**, trừ khi được ghi khác sau này. Cột lý do chỉ tóm tắt lý do trong U22, không thêm điều kiện AI suy ra vào xác nhận.

| ID | Quyết định | Lý do tóm tắt | Nguồn / trích ngắn | Thay thế / được thay bởi |
|---|---|---|---|---|
| RCA-001 | Chọn **C1 làm Primary RQ**: giá trị quan hệ service suy từ trace cho known-window root-service ranking trên RE2-TT với điều kiện so sánh công bằng. | Chọn câu hỏi có thể kiểm bằng dữ liệu đã audit. | U22 §1.2: “PRIMARY RQ = C1.” | Đóng lựa chọn C1/C2 tại Phase 1 §13.1; chưa bị thay |
| RCA-002 | Chấp nhận đóng góp thực nghiệm có kiểm soát, kỹ thuật/hệ thống, khả năng tái lập và quyết định dựa trên bằng chứng; **không yêu cầu novelty phương pháp**. | Ưu tiên pipeline dùng được, kết quả có thể bảo vệ. | U22 §1.1, §14.C | Đóng lựa chọn giá trị ở Phase 1 §13.2; tương thích định vị RES-054, không duyệt MyRCA cũ |
| RCA-003 | Kết quả thực nghiệm âm được chấp nhận nếu phép thử hợp lệ. | Không ép kết quả phải thắng đối chứng. | U22 §1.1: “A negative experimental result is acceptable if the experiment is valid.” | Chưa bị thay |
| RCA-004 | C1 phải phân biệt lợi ích relation khỏi capacity, degree, smoothing, volume, candidate universe, feature và supervision differences. | Graph ON/OFF đơn giản không đủ. | U22 §1.2 | Chưa bị thay; thiết kế exact controls vẫn OPEN |
| RCA-005 | C2 là mở rộng tùy chọn, chỉ xét sau C1 hoàn chỉnh/hợp lệ/ổn định và còn hữu ích; thành công luận văn không phụ thuộc C2. | Giữ một câu hỏi chính. | U22 §1.3 | Thay vai C2 là lựa chọn primary trong shortlist; chưa bị thay |
| RCA-006 | Giữ C3 hiện diện như chủ đề thiết kế/biểu diễn operation và nghiên cứu hỗ trợ; triển khai/ablation có điều kiện theo Task D và giá trị khoa học. | Không bỏ operation evidence chỉ vì không là primary gap. | U22 §1.4, §12 | Làm rõ C3 secondary của Phase 1; chưa bị thay |
| RCA-007 | Chỉ đánh giá operation localization định lượng ở nơi có ground truth phù hợp; FlashTicket có thể cung cấp qua chèn lỗi có kiểm soát sau này. | Không biến operation evidence thành nhãn. | U22 §1.4 | Chưa bị thay; không xác nhận đã có nhãn FlashTicket |
| RCA-008 | Giữ C4 hiện diện như câu hỏi vị trí/cơ chế graph và ablation/comparator hỗ trợ; Task D quyết cơ chế cụ thể. | Hiểu đóng góp của từng công đoạn. | U22 §1.5, §12 | Làm rõ C4 secondary của Phase 1; chưa bị thay |
| RCA-009 | C5 — anomaly detection → RCA — là năng lực hệ thống bắt buộc; mức đánh giá phụ thuộc nhãn của từng môi trường. | Hạn chế benchmark không xóa năng lực phát hiện khỏi hệ cuối. | U22 §1.6, §12 | Làm rõ C5 hoãn primary RQ, không phải bỏ năng lực; chưa bị thay |
| RCA-010 | Lớp LLM/AI giải thích sau ranked structured evidence là năng lực đầu ra dự kiến bắt buộc. | Diễn giải kết quả và gợi ý kiểm tra. | U22 §1.7, §12 | Tiếp tục RES-034; chưa bị thay |
| RCA-011 | LLM không tạo ground truth, tự xếp hạng lại, sửa kết quả RCA thất bại hoặc làm bằng chứng chẩn đoán đúng. | Giữ phép đánh giá RCA độc lập với văn bản giải thích. | U22 §1.7 | Chưa bị thay |
| RCA-012 | RE2-TT là môi trường kiểm chứng công khai cho pipeline hoàn chỉnh; không bắt mọi thành phần có cùng mức đánh giá định lượng. | Ground truth giới hạn điều được tuyên bố. | U22 §1.8 | Mở rõ phạm vi sản phẩm so với một notebook C1; không đổi sự thật Task B |
| RCA-013 | FlashTicket là hệ đích để chuyển giao/kiểm chứng có kiểm soát sau thực nghiệm công khai. | Thực hiện DT18-NV3. | U22 §1.9 | Tiếp tục nhiệm vụ DT18; chưa bị thay |
| RCA-014 | Tasks D–G của nghiên cứu công khai không bị chặn bởi tiến độ FlashTicket; nghiên cứu có thể chạy song song với triển khai của các thành viên. | Giữ độc lập tiến độ và ranh giới adapter. | U22 §0, §1.9 | Không dùng giả định Minh thiếu thời gian để cắt scope; chưa bị thay |
| RCA-015 | Kết quả FlashTicket không hợp thức hóa hồi tố một claim RE2-TT không được dữ liệu hỗ trợ. | Mỗi môi trường có bằng chứng riêng. | U22 §1.9 | Chưa bị thay |
| RCA-016 | Không để quyết định quan trọng chỉ tồn tại ngoài repository; không chép dataset lớn vào repository chỉ để thống nhất hình thức. | Giữ tri thức bền vững và phục hồi ngữ cảnh giữa các phiên. | U22 §6, §14.H–I | Chưa bị thay; cách bố trí hai root do chương trình đánh giá, không gán thành lựa chọn cụ thể Minh đã chốt |
| RCA-017 | Phiên này chỉ hoàn tất C Phase 2 và hồ sơ điều phối; Task D phải chờ lệnh bắt đầu riêng. | Đúng ranh giới ủy quyền. | U22 phần đầu, §15: “Do not start Task D automatically.” | Chưa bị thay |

## Xác nhận làm rõ nguồn và phạm vi — 23/09/2026

Nguồn U23: [nguyên văn xác nhận Minh §3](../evidence/advisor-direction/2026-09-23-huong-dan-do-minh-cung-cap.md). Mọi dòng dưới: người chốt **Lê Văn Minh**, ngày **2026-09-23**, trạng thái **USER_CONFIRMED**, gate **TD-v1.1 reconciliation / làm rõ phạm vi DT18**. Không thay RCA-001–017, không duyệt phương pháp hay execution.

| ID | Xác nhận nguyên tử | Nguồn / trích ngắn | Phạm vi ảnh hưởng |
|---|---|---|---|
| RCA-018 | Tên đề tài hiện hành đúng là “Xây dựng hệ thống bán vé theo kiến trúc phân tán có ứng dụng đồ thị phụ thuộc để giám sát và chẩn đoán sự cố”. | U23: tên đề tài “chuẩn” | Tái xác nhận DT18/PRJ-025; không thay tên hoặc nhiệm vụ |
| RCA-019 | Đoạn hướng dẫn giảng viên được cung cấp ở U23 thuộc giai đoạn đề tài trước, khi RCA là đối tượng nghiên cứu chính. | U23: “đoạn hướng dẫn trên là khi đề tài vẫn còn lấy RCA là đối tượng nghiên cứu chính” | Provenance tương đối; không suy ngày/kênh gửi gốc |
| RCA-020 | Hướng dẫn cũ vẫn có giá trị cho phần RCA; RCA không bị bỏ khi đồ án thêm đối tượng nghiên cứu hệ thống. | U23: “hướng dẫn cũ này của cô vẫn còn giá trị … RCA không bỏ” | Cách đọc nguồn phương pháp trong D/Master/A; không xác nhận từng ví dụ là bắt buộc |
| RCA-021 | Không áp dụng nguyên xi hướng dẫn cũ theo cách giảm nhẹ hoặc coi nhẹ việc xây dựng FlashTicket. | U23: “không nên tuân thủ y hệt … giảm nhẹ hay coi nhẹ việc xây dựng hệ thống flash ticket” | D/H/J và diễn giải DT18 giữ nghĩa vụ hệ thống; không thêm API/kiến trúc |

## Quy tắc cập nhật

Thay đổi ý định đã khóa phải có xác nhận mới của Minh, ID mới, nguồn/ngày và liên kết thay thế; giữ dòng cũ. Không thêm đề xuất reviewer hoặc thiết kế Task D chưa được duyệt vào bảng USER_CONFIRMED. Một quyết định triển khai được giao cho agent không tự là lời Minh xác nhận đúng phương án agent chọn. Không dùng sổ này thay các gate kiến trúc/hợp đồng của bộ hệ thống.

## Redesign trước approval — 26/09/2026

Nguồn U26: [nguyên văn mission](../evidence/project-direction/2026-09-26-rca-task-d-redesign.md). Mỗi dòng dưới: người chốt **Lê Văn Minh**, ngày **2026-09-26**, trạng thái **USER_CONFIRMED**, gate **Task D pre-approval redesign**. Đây là ý định/phạm vi và quyền làm việc; không duyệt các công thức do agent đề xuất. RCA-001–021 được giữ nguyên phía trên.

| ID | Xác nhận nguyên tử | Nguồn | Thay thế / phạm vi |
|---|---|---|---|
| RCA-022 | Giữ C1 là Primary Research Question về giá trị observed service relations trong known-window service ranking công bằng. | U26 §3.1 | Tái xác nhận RCA-001 |
| RCA-023 | C1 không phải toàn bộ graph method của đề tài. | U26 §3.1 | Làm rõ phạm vi C1 |
| RCA-024 | C5 không thay C1 làm primary; C5 là FIRST-CLASS GRAPH-BASED ANOMALY-DETECTION METHOD TRACK. | U26 §3.2 | Nâng vai trò RCA-009; nghĩa vụ capability cũ còn nguyên |
| RCA-025 | Thiết kế theo prior-work-first, custom-second, phân loại từng thành phần và giải trình study-specific choices. | U26 §3.3 | Chính sách phương pháp D |
| RCA-026 | Cho phép dùng root/fault labels trên development để chọn method variant. | U26 §3.4 | Supersede prohibition development selection tại C v1 §4 / TD-v1.1 §§2–3 |
| RCA-027 | Cho phép dùng root/fault labels trên development để chọn hyperparameter. | U26 §3.4 | Cùng phạm vi supersession RCA-026 |
| RCA-028 | Cho phép calibration có giám sát trên development nếu phương pháp yêu cầu và được khai báo. | U26 §3.4 | Không xác nhận một thuật toán calibration cụ thể |
| RCA-029 | Cho phép model selection bằng root/fault development labels. | U26 §3.4 | Không tự duyệt full supervised network hoặc runtime labels |
| RCA-030 | Cho phép sensitivity-informed selection trên development. | U26 §3.4 | Cùng phạm vi supersession RCA-026 |
| RCA-031 | Labels không được trở thành runtime input. | U26 §3.4 | Giữ firewall model/evaluator |
| RCA-032 | Final evaluation data không được dùng để tune. | U26 §3.4 | Giữ final outcomes tách development |
| RCA-033 | Mọi search space phải định nghĩa trước final evaluation. | U26 §3.4 | Registry và freeze receipts |
| RCA-034 | Phương pháp phải freeze trước final evaluation. | U26 §3.4 | Không phải đã freeze tại D |
| RCA-035 | Sensitivity analysis là bắt buộc trước final freeze cho lựa chọn ảnh hưởng materially. | U26 §3.5 | E/F phải thực hiện khi được giao |
| RCA-036 | Có small pre-registered set backup/robustness mechanisms trước final evaluation. | U26 §3.6 | Không chọn winner sau final outcomes |
| RCA-037 | Giữ ý tưởng L/O/R nếu independent review thấy hợp lệ; exact R có thể redesign. | U26 §3.7 | Không duyệt exact control algorithm |
| RCA-038 | RE2-TT tiếp tục primary environment nếu evidence ủng hộ. | U26 §3.8 | Làm rõ điều kiện RCA-012 |
| RCA-039 | Bắt buộc serious compatibility analysis RE3-TT và xét SS/LEMMA theo task thực tế, không graph giả. | U26 §3.8 | Mở lại public-validation design; không tự cho retrieval campaign |
| RCA-040 | Một configuration thất bại không đồng nghĩa graph thất bại; phải có failure attribution. | U26 §3.9 | Làm rõ RCA-003; không ép effect dương |
| RCA-041 | Không bắt đầu Task E trong phiên redesign này; human approval vẫn OPEN. | U26 §3.10 | Quyền execution không sinh từ REVIEW_READY |
| RCA-042 | Cho phép material revision tài liệu RCA trong scope sau review/reconciliation; lập impact map trước hơn ba tệp, không cần hỏi lại. | U26 §15 | Không gồm application/API/schema, commit/push hoặc destructive Git |

**Supersession theo phạm vi:** chính sách evaluation-only cũ ở C v1 §4, TD-v1.1 §§2–3 và bản digest B ngày22/09 giữ giá trị lịch sử. RCA-026–030 thay đúng lệnh cấm development selection/calibration đã khai báo; giữ nguyên cấm runtime labels và final-test tuning. Dùng supervision name **label-guided development / hyperparameter selection**. Việc dùng development injection-regime labels để chọn threshold/forecast loss trong TD-v1.2 là **CANDIDATE technical interpretation của RCA-028**, không thêm một xác nhận injection-time policy vào lời Minh.


## Development preflight — 27/09/2026

Nguồn U27: [mission nguyên văn và hai xác nhận trực tiếp](../evidence/project-direction/2026-09-27-rca-development-preflight.md). Người chốt Lê Văn Minh, ngày2026-09-27, trạng thái USER_CONFIRMED, gate Task E development preflight. Giữ RCA-001–042 và nguồn lịch sử; không phê duyệt công thức hoặc final freeze.

| ID | Xác nhận nguyên tử | Nguồn | Thay thế / phạm vi |
|---|---|---|---|
| RCA-043 | Cho phép chuẩn bị và chạy development giới hạn theo TD-v1.2 để kiểm tra thiết kế. | U27 xác nhận trực tiếp | Supersede lệnh cấm bắt đầu E trong snapshot26/09, chỉ phạm vi development |
| RCA-044 | Cho phép bước development này diễn ra trước khi đủ năm agent phản biện. | U27 xác nhận trực tiếp | Hoãn assurance blocker cho scoped E, không xóa yêu cầu |
| RCA-045 | Vẫn giữ yêu cầu phản biện cho đánh giá chính thức. | U27 xác nhận trực tiếp | Formal assurance OPEN; không tính vai trò thành identities |
| RCA-046 | Không chạy tập đánh giá cuối trong phiên development này. | U27 xác nhận trực tiếp | Giữ final outcome firewall; không G |
| RCA-047 | Không tự thay đổi phạm vi đồ án. | U27 xác nhận trực tiếp | DT18 và C lock giữ nguyên |
| RCA-048 | Duyệt gói file/phạm vi preflight-plan.md §4–5 và tiếp tục theo các gate đã nêu. | U27 trả lời câu hỏi impact map | Đúng30 development cases khi checks đạt; source/code/artifacts theo map; không commit/push |

Cách triển khai và các công thức tiếp tục là CANDIDATE/engineering choices trong contract. Kết quả xấu hợp lệ có giá trị; ambiguity/fidelity/leakage/coverage/control failure dẫn stop/review theo U27, không tự sửa method để cứu score. CURRENT-STATE sở hữu checkpoint thực thi.

## C5 clarification và tiếp tục E — 27/09/2026

Nguồn [U27R](../evidence/project-direction/2026-09-27-rca-c5-amendment-and-e-resume.md), Lê Văn Minh, USER_CONFIRMED, gate scoped D amendment → E development. Giữ RCA-001–048. Policy kỹ thuật được giao cho agent phân tích/chọn không tự là USER_CONFIRMED.

| ID | Xác nhận nguyên tử | Nguồn | Phạm vi |
|---|---|---|---|
| RCA-049 | Cho phép xử lý hai finding C5 đang OPEN. | U27R §1 | D-E27-01 và D-E27-02 |
| RCA-050 | Cho phép sửa Task D nếu technical review kết luận cần sửa. | U27R §1 | Revision/amendment có rationale, review và fixtures |
| RCA-051 | Cho phép cập nhật canonical/derived research documents cần thiết. | U27R §1 | Đúng D/E, không hồ sơ hệ thống ngoài registration |
| RCA-052 | Cho phép sửa hơn ba file và coi mission là explicit approval cho impact map hợp lý trong phạm vi nhiệm vụ. | U27R §1 | [Continuation impact map](D:/Project/flash-ticket-rca-research/results/task-e/continuation-plan.md) |
| RCA-053 | Sau D clarification/review đạt, tự động resume E theo development scope đã duyệt, không xin confirm lại cùng scope. | U27R §1/12 | Exact automatic gate §12; không mở F/G/H/I |
| RCA-054 | Cho phép tải development telemetry còn thiếu trong allowlist đã định. | U27R §1 | Chỉ RE2-TT development30 |
| RCA-055 | Cho phép tạo isolated environment cần cho RCD/BARO qualification. | U27R §1 | Pin/license/fidelity; không upgrade môi trường cũ để ép chạy |
| RCA-056 | Cho phép synthetic fixtures, loader audit, smoke và toàn authorized development30. | U27R §1 | Giữ gates và predetermined smoke |
| RCA-057 | Cho phép mandatory development sensitivity đã đăng ký. | U27R §1 | OFAT, không Cartesian/winner-only |
| RCA-058 | Cho phép sửa implementation bug nếu không đổi scientific method. | U27R §1 | Giữ failed runs và root-cause attribution |
| RCA-059 | Không mở 60 final evaluation cases. | U27R §1 | Các giới hạn khác vẫn hiệu lực trực tiếp theo nguồn U27R |
| RCA-060 | Không dùng final outcomes để tune. | U27R §1 | Giữ final outcome firewall |
| RCA-061 | Không xóa/ghi đè failed runs. | U27R §1/16 | Giữ mọi attempt và negative outputs |

RCA-049/050 thay đúng hạn chế không sửa method ở checkpoint U27 trước. Chưa có exact C5 policy được human-confirmed; candidate được review và dùng cho development theo delegated scope. Yêu cầu phản biện cho đánh giá chính thức ở RCA-045 giữ nguyên.

## Human freeze TD-v1.3 và authorization Task F — 28/09/2026

Nguồn [U28F](../evidence/project-direction/2026-09-28-rca-td13-freeze-and-task-f-authorization.md), Lê Văn Minh, `USER_CONFIRMED`, gate `D/E closure → Task F`. Giữ RCA-001–061 và mọi limitation/evidence lịch sử; canonical TD-v1.3 không được sửa byte.

| ID | Xác nhận nguyên tử | Nguồn | Phạm vi |
|---|---|---|---|
| RCA-062 | Human approve/freeze đúng executed TD-v1.3 có SHA256 `34fd73f6a84dc6b45834bd7fd4cc7b86e19e54f1011de99735631f027ee18971`, giữ toàn bộ documented limitations. | U28F — quyết định nguyên văn | Supersede đúng trạng thái human acceptance/freeze `OPEN`; không xác nhận graph superiority, final efficacy hoặc five-reviewer PASS |
| RCA-063 | Cho phép handoff, bắt đầu và thực hiện Task F theo contract hiện hành trong `MASTER-RESEARCH-PROGRAM.md`. | U28F — quyết định nguyên văn và scope authorization | Task F implementation/validation trong impact map đã cấp; không mở final60, G/H/I, method amendment, FlashTicket application changes hoặc commit/push |

Factual status của yêu cầu five-distinct-independent-reviewer vẫn `OPEN / NOT FACTUALLY CERTIFIED`. Current authorization không rewrite historical reviews và không biến publication-only provenance change thành scientific drift.

## PRE-G authorization — 29/09/2026

Nguồn [U29PG](../evidence/project-direction/2026-09-29-rca-pre-g-authorization.md), Lê Văn Minh, `USER_CONFIRMED`, gate `Task F corrective closure → PRE-G`. Các dòng này ghi quyền và giới hạn do Minh xác nhận, **không** tự tuyên bố PRE-G PASS hoặc mở Task G.

| ID | Xác nhận nguyên tử | Nguồn | Phạm vi |
|---|---|---|---|
| RCA-064 | Cho phép triển khai PRE-G theo đúng bản đồ tác động đã trình và được duyệt. | U29PG — “Duyệt đúng phạm vi” | Chỉ các tệp và kiểm tra trong receipt; không tự mở G |
| RCA-065 | PRE-G chỉ kiểm metadata/source-ingestion contract và adapter compatibility, không đọc row-level final60. | U29PG — nguyên văn | Không chứng nhận raw final-scope validation từ metadata |
| RCA-066 | Cho phép hoàn thiện và kiểm thử orchestration ba seed bằng synthetic/development evidence. | U29PG — nguyên văn | Không chạy final campaign hoặc chọn seed tốt nhất |
| RCA-067 | Không mở raw final60, labels hoặc outcomes; nếu chỉ đạt metadata-level qualification thì ghi đúng giới hạn đó, không gọi raw final-scope validation. | U29PG — nguyên văn | Giữ firewall và claim boundary; actual-use raw audit còn OPEN |

## PRE-G corrective work và Task F re-review — 30/09/2026

Nguồn [U30PG — phần corrective authorization của U29PG](../evidence/project-direction/2026-09-29-rca-pre-g-authorization.md), Lê Văn Minh, `USER_CONFIRMED`, gate `PRE-G corrective → Task F re-review → readiness assessment`. Không đổi RCA-064–067 hoặc ghi quyền chạy G từ câu hỏi readiness.

| ID | Xác nhận nguyên tử | Nguồn | Phạm vi |
|---|---|---|---|
| RCA-068 | Đồng ý sửa hai lỗi PRE-G theo đề xuất review đã trình. | U30PG — “đồng ý với đề xuất” | Synthetic qualification forgery và endpoint-source forgery; giữ impact map 12 tệp |
| RCA-069 | Sau khi sửa xong, rà soát lại Task F một lần nữa. | U30PG — nguyên văn | Re-review/read-only checks của frozen release, không sửa hoặc rerun campaign Task E/F |
| RCA-070 | Chốt đánh giá đã sẵn sàng mở G được chưa. | U30PG — nguyên văn | Yêu cầu kết luận readiness; không tự là quyền mở raw final60, labels/outcomes hoặc chạy G |

Owner của quyền/gate vẫn Lê Văn Minh. Technical qualification correction là công việc đã được giao; scientific method/config/split/evaluator, raw actual-use admission và formal five-distinct-reviewer status giữ nguyên. Kết quả thực kiểm được ghi ở PRE-G receipt/readiness sau review.

## Task G entry validation và campaign preparation — 30/09/2026

Nguồn trực tiếp: Lê Văn Minh trong cùng cuộc trao đổi, nguyên văn: “h mở Task G ở bước kiểm tra đầu vào và chuẩn bị campaign”. Gate `Task F/PRE-G → G entry/preparation`, owner Minh.

| ID | Xác nhận nguyên tử | State | Nguồn / tạo tác ảnh hưởng |
|---|---|---|---|
| RCA-071 | Mở Task G ở bước kiểm tra đầu vào và chuẩn bị campaign. | USER_CONFIRMED | Câu hiện hành của Minh; `task-g-handoff.md`, `CURRENT-STATE.md` |

Phạm vi thực hiện và các suy luận được tách khỏi câu xác nhận: handoff G ghi kiểm tra đã làm là `FACT`, phương án controller/persistence chưa thực hiện là `CANDIDATE`, quyền truy cập raw final60/nhãn và final campaign chưa được nêu rõ là `OPEN`. RCA-071 supersede câu “G chưa được mở” đúng bước entry/preparation; không sửa TD, F, E hoặc biến metadata PASS thành raw admission. Quyền scientific/five-reviewer approval không được suy từ việc mở bước này.

## Task G preparation scope và telemetry admission — 30/09/2026

Nguồn trực tiếp, Lê Văn Minh: “Duyệt phạm vi chuẩn bị G trên; cho phép đọc telemetry final60, vẫn đóng nhãn và campaign”. Phạm vi đã trình tại `task-g-handoff.md`: exact map 13 text/code files, tối đa 180 pinned telemetry objects và host-local trust key ngoài Git/output bundle. Owner Minh, gate G entry/preparation.

| ID | Xác nhận nguyên tử | State | Nguồn / tạo tác ảnh hưởng |
|---|---|---|---|
| RCA-072 | Duyệt phạm vi chuẩn bị G đã trình. | USER_CONFIRMED | Câu hiện hành; exact map trong `task-g-handoff.md`, G source/tests/entry receipts |
| RCA-073 | Cho phép đọc telemetry final60. | USER_CONFIRMED | Câu hiện hành; G telemetry acquisition/admission theo map đã trình |
| RCA-074 | Nhãn và campaign tiếp tục đóng. | USER_CONFIRMED | Câu hiện hành; G permission contract/controller/label boundary |

Technical HMAC/loader/controller là lựa chọn triển khai được giao trong approved preparation scope, không là scientific algorithm do Minh xác nhận. Final predictions/tau/answers/outcomes vẫn bị khóa theo preparation plan; metadata case IDs giữ controller-only. Raw admission là FACT chỉ sau actual checks/review, không tự nâng scope F hoặc cấp quyền campaign. TD/F/E và formal five-reviewer limitations giữ nguyên.

## Yêu cầu tiếp tục đến readiness final — 30/09/2026

Nguồn trực tiếp, Lê Văn Minh: “tiếp tục giúp tôi , sử dụng subagent nếu cần thiết để rà soát, kiểm tra tính đúng đắn, tôi cần mọi thứ sẵn sàng để chạy final, không cần tiết kiệm”.

| ID | Xác nhận nguyên tử | State | Nguồn / tạo tác ảnh hưởng |
|---|---|---|---|
| RCA-075 | Tiếp tục công việc đến mức sẵn sàng chạy final. | USER_CONFIRMED | Câu hiện hành; G readiness assessment và kế hoạch phần còn thiếu tại `task-g-handoff.md` |
| RCA-076 | Cho phép dùng subagent khi cần để rà soát tính đúng đắn. | USER_CONFIRMED | Câu hiện hành; các technical review G |

RCA-075 ghi mục tiêu tiếp tục, không tự tuyên bố đã đạt readiness hoặc mở nhãn/campaign. RCA-074 vẫn hiện hành. Full campaign implementation nằm ngoài entry-only outcome của map RCA-072: bản đồ tiếp theo được trình `CANDIDATE` trước mutation, giữ TD/F/E/ENTRY byte-identical và quyền final riêng. Technical checks và independent opinions là `FACT` theo đúng phạm vi đã thực kiểm, không human gate acceptance.

## Task G full-runner readiness authorization — 30/09/2026

Nguồn trực tiếp, Lê Văn Minh: “duyệt , tiến hành bước tiếp theo giúp tôi”, trả lời đề xuất next readiness scope trong `task-g-handoff.md` và bản tóm tắt bước tiếp theo. Gate `G telemetry entry → campaign implementation readiness`, owner Minh.

| ID | Xác nhận nguyên tử | State | Nguồn / tạo tác ảnh hưởng |
|---|---|---|---|
| RCA-077 | Duyệt phạm vi 14 tệp đã đề xuất cho bước chuẩn bị bộ chạy G tiếp theo. | USER_CONFIRMED | Câu hiện hành; exact map14 tại `task-g-handoff.md`, generated readiness cache/key và timing-only development30 đã trình |
| RCA-078 | Tiến hành bước tiếp theo đã trình. | USER_CONFIRMED | Câu hiện hành; implementation, synthetic/development validation, timing và independent readiness review |

RCA-077/078 là quyền thực hiện readiness plan, không phải tuyên bố readiness PASS hoặc scientific approval. RCA-074 giữ nhãn/campaign đóng; final τ/answers/outcomes/predictions vẫn ngoài scope. Phương pháp/split/evaluator/thresholds và TD/F/E/PRE-G/ENTRY/README/`.idea/`/ứng dụng giữ nguyên. Các chi tiết implementation thuộc delegated scope, không tự là lựa chọn thuật toán mới do Minh xác nhận. Contract đăng ký trước implementation/qualification, giữ mọi failed attempts và review findings.

## G32 final bridge readiness và audit τ-only — 30/09/2026

Nguồn trực tiếp `U30G32`: yêu cầu hiện hành của Lê Văn Minh, “Tiếp tục Task G — hoàn thiện G32 một cách cẩn thận”. Gate `G31 → G32 preparation`, owner Minh. Exact map14 trong yêu cầu khớp đề xuất G32 ở đầu `task-g-handoff.md`; quyền này supersede đúng câu đề xuất G32 chưa duyệt, không thay quyền hoặc kết quả lịch sử G30/G31.

| ID | Xác nhận nguyên tử | State | Nguồn / phạm vi |
|---|---|---|---|
| RCA-079 | Giao thực hiện đúng phạm vi G32 map14 đã trình trong `task-g-handoff.md`. | USER_CONFIRMED | U30G32 §1/4; ba tệp P, bốn module/bốn test và contract/receipt/review G32 ở W |
| RCA-080 | Cho phép generated cache giới hạn theo đề xuất G32. | USER_CONFIRMED | U30G32 §1/4; bounded synthetic/development/conversion evidence, tái dùng telemetry sau hash verification, không tải corpus mới |
| RCA-081 | Cho phép readiness key riêng theo đề xuất G32. | USER_CONFIRMED | U30G32 §1/4; host-local readiness domain, không tự cấp quyền campaign |
| RCA-082 | Cho phép τ-only input audit để kiểm riêng mốc thời gian chèn lỗi và xác minh cửa sổ đầu vào. | USER_CONFIRMED | U30G32 §1; nếu không tách được khỏi fields còn đóng thì giữ OPEN và báo Minh |
| RCA-083 | Root/fault, labels, answers/outcomes, final predictions và campaign tiếp tục đóng. | USER_CONFIRMED | U30G32 §1/6; giữ RCA-074 trong phạm vi này |

Các lựa chọn triển khai được giao — projection timing có provenance, numeric issuance, diagnostic schema, tách chi phí, seal/lazy evaluator và bounded fixtures — không tự là thuật toán được Minh xác nhận. Chúng phải được kiểm bằng code, dữ liệu được phép đọc và independent review; kết quả chỉ là FACT sau thực chạy. `OPEN`: readiness thực tế, nguồn/units/windows τ chưa kiểm, matching timeout nếu workload thay đổi, quyền final campaign và formal five-reviewer certification. Không sửa phương pháp/split/threshold/seed/comparator/evaluator đã khóa, không commit/push/PR/dependency hoặc tự duyệt gate. Handoff và CURRENT-STATE được cập nhật sau nguồn/bằng chứng, giữ historical exposure, F development qualification và FlashTicket validation còn thiếu.

## G32 — kết quả thực kiểm và phạm vi còn OPEN, 01/10/2026

Các mục dưới là quan sát kỹ thuật hoặc đề xuất của agent, **không phải xác nhận mới của Minh**. RCA-079–083 giữ nguyên nghĩa và quyền. Bản quyết định chứa authorization trước implementation đã được lưu nguyên byte trong G32 cache, SHA `53e4174aef90dea61d2e2d7a8696990bcdf4789fff86a4e051638d784a45622e`; cập nhật kết quả không viết lại authorization lịch sử. Owner quyết định/gate vẫn là Lê Văn Minh.

| ID | Nội dung | State | Bằng chứng / gate / ảnh hưởng |
|---|---|---|---|
| RCA-084 | G32 đã thực kiểm đúng readiness scope: actual telemetry conversion 60 ca/180 tệp, isolated τ projection; development timing 30 ca/180 matched arms; ba mẫu numeric synthetic khác nhau, durable seal và lazy artificial evaluation. Không actual final models/truth/campaign. | FACT | W G32 contract `7169093d2248814ef7436728665d5379df72aad0940b99c511287098f147a3c6`, conversion audit `e191fc4ef00e2162577fb1216883415750ec76c9f728fa07b6a0a668b78d857d`, receipt `096101485c64cd7784d4b079da64d0544ea5dc71f5123106b6a17ec4ca438f02`; G32 technical readiness, handoff và CURRENT-STATE |
| RCA-085 | Cách triển khai được giao đã được kiểm: stable literal keys giữ từng candidate universe; numeric-only worker wire; controller-only sanitized log/operation support; full model/masks/quality/graph/control diagnostics, explicit failures/missing costs; readiness domain/key luôn từ chối final campaign. | FACT | Tám source/test hashes trong G32 contract; 72 mục G32 PASS, 309 mục regression PASS; independent technical review và fresh-process verification. Không đổi TD/F/E, method/split/threshold/seeds/comparators/evaluator |
| RCA-086 | Chưa đủ điều kiện chạy campaign final ngay: actual output capacity/whole60 RAM và streaming còn chưa qualified; actual C5/RCD/integrated model runtime chưa đo; actual runner/domain/key, fixed post-seal truth provider, planned final outputs/evaluation/uncertainty/reproduction/independent handoff chưa thực hiện. Một ca thiếu log reference được giữ nguyên, không bỏ ca hoặc đoán dữ liệu. | OPEN | G32 actual ordinal45; full diagnostic output không suy từ input bound hoặc ba synthetic artifacts. Minh quyết định next scope; không tự nâng F development qualification, xóa historical exposure, chứng nhận five-reviewer hoặc suy đã kiểm FlashTicket |
| RCA-087 | Đề xuất G33 riêng trong exact map14 tại handoff: ba module/ba test mới và năm result files ở W cùng ba tài liệu P; tái dùng pinned G32 conversion, bổ sung streaming/sharding, own campaign trust domain, fixed lazy evaluator và reproduction. | CANDIDATE | Phần “Đề xuất G33” của handoff; chưa được authorize hoặc implementation. Generated actual-shard/cache/key scope phải được Minh duyệt rõ; actual predictions và truth/campaign vẫn đóng đến gate được duyệt |

Common C1 timeout mới là `max(300,10×36.35044049999851)=363.5044049999851 s`, từ workload development C1 đầy đủ diagnostics đã khóa; independent AST/dependency/input/checkpoint verification xác nhận vẫn tương ứng sau sửa controller-only. Timeout G31 khoảng880s giữ **historical-only**. Integrated conversion actual có lượt504.3566413s thuộc chi phí controller/cửa sổ khác, không là C1 model timing; auxiliary900s là infrastructure bound, chưa actual resource qualification. Synthetic seal call được đo ngoài là6.2363052s, gồm lưu artifact/readback/verification/publication; pure receipt-write chưa đo riêng, vẫn null. Không điền chi phí thiếu bằng0.

Finding độc lập sau seal: trong ba SYN receipts, field kế thừa `arm_wall_seconds` của assembler vẫn là tổng các **numeric components**, chưa gồm toàn bộ startup/serialization/controller overhead. DEV timing đã áp full-wall measurement riêng nên căn cứ363.5044s không bị thay. C1 worker wall, input/worker call wall, C5 worker wall và external seal call có phép đo riêng; không cộng các component có thể chồng lấn thành end-to-end. Giữ nguyên sealed evidence và công bố nghĩa giới hạn của alias; không dùng SYN alias làm final resource qualification. G33 phải tách tên/coverage numeric với wall và chứng minh overhead/timing tương ứng trước campaign. Đây là `FACT` về dữ liệu đã lưu và `OPEN` của final cost reporting, không phải lý do chọn lại output.

Source/report giữ mọi failed attempts, lỗi invocation/runtime, gián đoạn usage và lý do retry. Không rerun model chọn kết quả thuận lợi. G31 SYN60 vẫn là một mẫu ba nút được đưa qua60 mục; durable90 là DEV30+SYN60, không90 ca mới. INCONCLUSIVE do control mobility của mẫu giả không chứng minh dataset thật xấu hoặc phương pháp thắng/thua. G32 cũng không phải final efficacy; chỉ ba boundary fixtures và evaluator reproduction từ artifact đã seal.

## G33 preparation — authorization hiện hành, 01/10/2026

Nguồn là yêu cầu trực tiếp của Minh: “ĐỒNG Ý VỚI G33 preparation”, giao thực hiện các mục1–3 đã trình: xác nhận scope, implementation/verification/review độc lập, rồi trình duyệt campaign thật. Các xác nhận dưới giữ riêng ý nghĩa nguyên tử; lựa chọn kỹ thuật được giao và kết quả thực kiểm phải ghi riêng, không tự thành USER_CONFIRMED.

| ID | Xác nhận nguyên tử | State | Nguồn / phạm vi |
|---|---|---|---|
| RCA-088 | Minh đồng ý thực hiện G33 preparation theo map14 đã trình trong task-g-handoff.md. | USER_CONFIRMED | U01G33; ba tài liệu P, ba locked_* modules/ba tests và năm eventual result files W; prediction/evaluation/reproduction final files chỉ tạo tại gate tương ứng |
| RCA-089 | Minh đồng ý generated preparation cache và key riêng theo đề xuất G33. | USER_CONFIRMED | U01G33 chấp thuận đề xuất map14/cache/key ở bước1; bounded synthetic/development/resource/integrity evidence, host-local key riêng; không corpus/dependency mới |
| RCA-090 | Minh giao implementation, synthetic/development verification và reviewer độc lập cho G33 preparation. | USER_CONFIRMED | U01G33 mục2; stream/shard đủ diagnostics, cost/timing và controlled final boundary, giữ frozen method và mọi failed attempts |
| RCA-091 | Minh giao trình duyệt campaign thật sau khi preparation đạt kiểm chứng. | USER_CONFIRMED | U01G33 mục3; việc trình duyệt không tự là cho phép chạy final60 hoặc đọc nhãn thật |

`OPEN`: actual predictions/root/fault/labels/answers/outcomes/campaign vẫn theo RCA-083, chờ quyết định final gate riêng. Chuẩn bị được giao có thể đạt readiness khi đường chạy, completeness, resources/budgets, fidelity, matching timing và independent review được chứng minh; actual model runtime chưa đo phải công bố, không yêu cầu chạy actual model trước khi có quyền. Không đòi phương pháp thắng để đóng G. `CANDIDATE`: chi tiết segmentation/dedup/streaming, separate preparation/final trust domains, fixed after-seal metadata projection và predeclared reproduction sẽ do implementation/review kiểm trong scope đã duyệt. Giữ nguyên G32/G31/G30/TD/F/E/PRE-G/app/README/.idea và mọi thay đổi có trước; không commit/push/PR/install hoặc tự approve gate. Baseline trước G33 lưu toàn6205 nonignored file hashes và Git status hai repo trong ignored G33 cache; source snapshot authorization được giữ trước implementation, handoff cập nhật trước CURRENT-STATE.

### G33 — findings tài nguyên trước qualification

| ID | Nội dung nguyên tử | State | Bằng chứng / tác động |
|---|---|---|---|
| RCA-092 | Capacity attempt01 vượt controller budget cũ; worker counter của source snapshot e78e6f còn theo dõi Windows venv launcher thay vì numeric child. | FACT | Controller OS peak1,640,919,040 bytes vượt1,342,177,280; identity probe `ed60119b514fbd62a8f85c208b63a9c0f620f77a815a6fcce6acce63d8b33a24` xác nhận hai PID khác nhau. Giữ attempts01/02 và DEV receipt cũ làm lịch sử; không dùng `passed:true` của capacity02 làm worker resource qualification. Không actual models/truth |
| RCA-093 | Trong quyền triển khai đã giao, agent chọn controller budget2GiB, theo dõi cây tiến trình bằng lineage/creation-time/handles, giữ counters và kết thúc các numeric descendants khi vượt budget/timeout. | CANDIDATE | Đây là infrastructure choice cần thực kiểm và reviewer, không USER_CONFIRMED hoặc thay phương pháp. Overhead monitor đổi nên phải đo DEV30 mới theo workload hiện hành; counter thiếu phải ghi thiếu/fail closed. Auxiliary900s còn là budget, chưa actual model measurement |
| RCA-094 | Source-only RAM audit đầu đi qua40/60 ca rồi dừng tạiordinal39 vì OS lifetime peak2,175,004,672 bytes vượt2GiB; current private tại checkpoint1,338,023,936 bytes. | FACT | Giữ toàn40 checkpoints và `actual60-controller-resource-attempt01/failure.json`; không actual model/truth. Đây là input-controller resource failure, không scientific outcome hoặc thiếu ca trong planned final denominator |
| RCA-095 | Agent chọn controller budget3GiB để có dự phòng so với peak đã đo, rồi kiểm lại toàn60 source-only trong một tiến trình; worker budget512MiB và frozen scientific settings giữ nguyên. | CANDIDATE | Delegated infrastructure choice thay đề xuất2GiB sau measurement, chưa human campaign approval. Lượt source-only retry phải giữ và so numeric input digests; capacity math đã thành công dưới guard2GiB được tái dùng nếu reviewer xác nhận guard tăng không tác động workload, không tính lại để chọn output |
| RCA-096 | Review source4a phát hiện controller counterNone có thể bỏ qua guard; chưa quan sát actual counter failure. | FACT | Reviewer kiểm code/nhánh lỗi, yêu cầu explicit failure và preserve prefix; source4a DEV attempt03 dừng có chủ đích ở ranh giới ca, giữ partial, không chọn theo numeric outcome |
| RCA-097 | Review source4a phát hiện cờ C1-only chưa bắt exactbool/đúng DEV scope nên semantic verifier có thể bỏ kiểm C5/RCD ở inventory ký sai. | FACT | Fixed issuer thông thường tạoFalse cho SYN/final; đây là completeness/tamper finding cần sửa trước truth, không bằng chứng actual labels đã mở |
| RCA-098 | Future reproduction source4a truyền admission0 trong khi production đo/charge admission thật. | FACT | Sửa future-only path để đo/charge cùng coverage và giữ mismatch; chưa chạy actual replay/model. Frozen evaluator/seed/method giữ nguyên |
| RCA-099 | Actual source-only retry hoàn tất60/60 checkpoints, numeric input digests khớp G32 và40/40 prefix cũ; peak2,166,538,240 bytes dưới3GiB. | FACT | Resource report `ca5285eb23b8c20cd7c36590ab7358bd37af1aec3e7ad1a592733c7053bc0dd3`; independent audit `ec32f11e610b5485334ae3364a4b21f565ec8a36bd00ca4305049b9adf0c50fe`. Conversion/controller source accumulation only, không actual models/integrated/evaluation; không universal final-model capacity claim |

| RCA-100 | Đối chiếu G32 phát hiện G33 source715 chưa giữ toàn per-case conversion provenance trong durable packet. | FACT | Source summary chỉ coarse; thiếu clock/units/window coverage, numeric inventory, RCD quality và separated conversion/support costs đã có ở issued source_metadata. Root và reviewer xác nhận trước DEV04/SYN; không actual model/truth. |
| RCA-101 | Agent bổ sung conversion_provenance controller-only, bind issued packet và kiểm presence/scope/no-oracles; loại đúng named conversion runtime khi so scientific replay. | CANDIDATE | Sửa completeness trong map14/full diagnostics đã giao; không truyền metadata/τ cho workers, không đổi frozen method/window/candidate universe. Source/tests phải đăng ký và đo DEV hiện hành sau independent review. |

| RCA-102 | Reviewer phát hiện issued-binding verifier chưa buộc exact field set, nên thiếu key có thể pass vacuously. | FACT | Đóng completeness bằng equality với exact controller subset trong map14; giữ prefix/history, không method hoặc quyền mới. Chưa freshDEV04/SYN hoặc actual models/truth. |

| RCA-103 | Current G33 development timing hoàn tất30/30 ca và180/180 matched arms, max fullwall42.92569470000308s, timeout429.2569470000308s theo frozen formula. | FACT | Sourcee9b4, report489dff6e5635991f68286d699177a2a36d5196f63e17782c4b10293f1b0d319c; timing-only numeric DEV locked, không labels/efficacy. Giữ DEV01failed/02historical/03controlledstop14ca; chưa final campaign. |
| RCA-104 | Current capacity helper N27/K12/R256/támC5×252bins vượt kiểm transport/science/resource dưới registered guard. | FACT | Report88a5ca7a421278718827e999c61ece7002cedbee6aff8d1533fa24e6cc3fe8c9; C1/C5 correct2process peaks97,755,136/102,121,472bytes; controller147,795,968bytes. Synthetic-only helper40sharedrefs/RCD184wire, không40distinctmodels hoặc actual fullcampaign guarantee; independent frame fidelity chờ kiểm. |
| RCA-105 | Các sửa guard/provenance/exactissuedbinding ở sourcee9b4 đạt independent implementation prequalification và44/44 purpose tests. | FACT | Independentv3 snapshot236d639cc2ae353b513e748aa1c00c13f4c190c330cab2619fbff50955a3c287; không human/five-reviewer approval. Current empirical refs được đăng ký trước genuineSYN3/seal/evaluation; readiness conclusion còn chờ các bước đó và audit cuối. |

### G33 — kết quả thực kiểm preparation, không mở campaign thật

| ID | Nội dung nguyên tử | State | Bằng chứng / tác động |
|---|---|---|---|
| RCA-106 | Reviewer đã xác minh đầy đủ current DEV30 và capacity04, so toàn scientific frames của bốn capacity attempts được giữ lại. | FACT | DEV audit688b7625a8a5a40defd1b97e5989a8ef5c1840ae85e1dfec9c9bd9a39486bfa8; capacity audit129a28f4f3ab647f97afe7df2dcd2b6f87c93607471612f67694afe3100c9076. 9.160 lượt đọc frame =2.290×4, không9.160 ca hoặc model runs. Historical measurement contract7c315 được giữ riêng; current10d521 chỉ thêm evidence/timeout/basis, source/science/environment/permissions/limits tương ứng, không đổi hash receipt cũ để vượt kiểm. |
| RCA-107 | Genuine SYN3 G33 hoàn tất một lượt issued computation và durable preparation seal, không final qualification. | FACT | Ba mẫu khác nhau candidate6/5/4;1548 L/O/R outputs,9 RCD seed outputs420/421/422 bins5,24 detector-cases/4896 bins SUCCESS;48 trigger records gồm12 early INSUFFICIENT_HISTORY và36 success, dùng9 distinct integrated computations. Seal7cb21cda5205353ba563528a653c94118822e75cc05217c1d876ba8d9224cf5b;3 inventories/5781 unique shards, domainPREPARATION/final_prediction_qualified=false. Không actual predictions/truth/campaign. |
| RCA-108 | Full scientific fidelity của cả ba SYN3 packets G33 khớp G32 sau đúng named runtime exclusions. | FACT | Report7d4029b1636e6de7ef28e795552b9a7e6837dc2e756f975af7dc92dd56857271; mỗi ca kiểm toàn controller bindings, C1 science, tám C5 state/bin packets và compact scores/controls/comparators/triggers. Giữ clocks/windows/masks/model/quality/candidate mapping/status/graph/seed outputs; không recompute model G32 hoặc bỏ trường khoa học để lấy equality. |
| RCA-109 | Fresh process full verification rồi pure evaluator replay khớp artifact synthetic đã lưu. | FACT | Root reporta8853a6a4054d5f1a5879b7af5a9b5acf208d4947feead483f9c952698592a12; independent own audit0ddc991c1de9956fb0ac20dbdf019d8a74b52c4609c269b32696e38bfa7f05d0. Artificial truth callback1 chỉ sau fullseal; exact JSON-normalized evaluator17405e6a8d969a69b315b76fbc31a8474687318cc3d2d01a29276d71bf96ea87. Independent attempt01 rawdict/int-key assertion failure được giữ; retry02 chỉ canonical verification, không model rerun hoặc actual truth. |
| RCA-110 | Mười negative checks trên isolated synthetic read-view copies đều bị chặn trước artificial truth; full original seal sau đó vẫn hợp lệ. | FACT | Livecopy21501f04f0e4be03b832c55d84b4ae855d8f47659ca2c85f7e09d4d168ddd7a7: tamper/missing shard, missing/duplicate/stale inventory, missing C5 science, wrong DEV flag, source/config/anchor drift; callback0 ở mọi rejection. Posthelper original-fullseal5400e2e5695d154aca4c12a6584231f8247c026f388d058d856da7d9f914242b xác minh5781 original shards. Path.open redirection là test seam, không OS sandbox; env/raw/auth/resource drift dùng unit/code coverage riêng, không gọi là mười live checks đã kiểm tất cả. |
| RCA-111 | Registered cost driver lưu chi phí numeric và publication trước truth giả, rồi lưu sealed evaluation cost cùng exact evaluator artifact. | FACT | Receipt df388b5354467e81c86090009af2a6eb3ab5387a9250a2816689c810e471f1ac: numeric154.0443118s, publication63.7081112s, sealed-evaluation20.6615990s. Missing isolated numeric/write/truth/evaluator/coldIO/selfreceipt durations giữ null; overlapping internal components không cộng thành end-to-end. Ordering phụ thuộc protected driver d8b67f27b2d3f5371f4716c055e0b290b29aa201a5a7452b3647e47faca44007, trusted host, không independent cryptographic attestation hoặc final efficacy. |

`OPEN`: actual final60 model runtime, combined model/integrated/evaluator resource use, efficacy, final uncertainty/reproduction và final independent scientific handoff chưa thực chạy; auxiliary900s là infrastructure policy. Missing log-reference ordinal45, historical TT90 exposure, Task F development qualification, five-reviewer certification và FlashTicket validation giữ nguyên giới hạn. Final key và ba actual prediction/evaluation/reproduction files chưa tạo. G33 preparation authorization RCA-088–091 không tự cấp quyền final campaign hoặc mở root/fault; việc trình quyết định final tiếp theo phải dựa trên handoff cụ thể và audit cuối.

| ID | Nội dung nguyên tử | State | Bằng chứng / tác động |
|---|---|---|---|
| RCA-112 | Governance audit và full baseline integrity audit01 hoàn tất trong scope preparation. | FACT | Governance PASS/zero errors/one impact-map warning đã đối chiếu current exact map và dirty paths có trước. Audit0484af4f8626f4b21864a85e46a5c48918074a1b26ed0e02215ef11d7fcb7392:6202/6202 protected files unchanged,6205 baseline files không mất, Pnew0/Wnew8 đúng map, source6/contract/key/authorization prefix/historical bodies/branch/HEAD exact; actual final three files/key absent. Audit cuối ghi hash sau final handoff/review, không models/evaluation mới. |
| RCA-113 | Independent post-runtime technical review kết luận PASS_BOUNDED_G33_PREPARATION_TECHNICAL. | FACT | Immutable snapshot81de3537c7df9283819816233b49ccdba70479d8204d302c1765cf64a6b4bb99; fullDEV/capacity/SYNscience/cost/seal/fidelity/lazy evaluator/negative tests được phản biện riêng. Có căn cứ trình Minh final-campaign approval sau governance/complete-diff/continuity; không final-qualified efficacy, human gate approval hoặc five-reviewer certification. |
| RCA-114 | Đề xuất tiếp theo là một registered actual final60 campaign theo frozen TD/F, exact protected cost driver FINAL và own FINAL-domain key. | CANDIDATE | Concrete scope tại task-g-handoff.md:60 cases/20cells×3repetitions,256controls/primary-secondary/ba contextual baselines/RCD420–422 bins5/támC5; planned outputs/failures/cost/shards/uncertainty/reproduction. Chỉ sau complete seal/full180 hashes mở fixed7-column root/fault/τ projection cho evaluator; answers/outcomes/newacquisition/install tiếp tục đóng. Chưa human authorization, không tự đổi flags hoặc tạo FINAL key. |
| RCA-115 | Actual final efficacy/uncertainty/reproduction/independent scientific handoff và campaign permission còn cần quyết định/thực chạy tại gate tiếp theo. | OPEN | Owner Lê Văn Minh cho explicit final permission; runner/reviewer thực hiện và báo cả planned failures sau quyền được cấp. Historical exposure/Fdevelopmentqualification/five-reviewer/FlashTicketvalidation và missing log-reference ordinal45 giữ nguyên. G chưa DONE; không yêu cầu method phải thắng để đóng G. |
| RCA-116 | Independent final preparation review hoàn tất technical, document consistency, governance và tự kiểm toàn protected baseline. | FACT | Final memo/snapshot a5799a5660814cb6f4538019090eb80cf18944473b8f8501085acd651fe51d81: PASS_BOUNDED_G33_PREPARATION_TECHNICAL_WITH_DISCLOSED_LIMITATIONS. Own audit5112b5e5d8a51dd95998860a1b843d1250918f22b1c6c68a4fe5aa9ed786c250 rehash6202/6202, Git/untracked/prefix/history PASS; governance02 after final docs PASS one scoped warning. Có đủ căn cứ trình Minh duyệt exact final proposal ở handoff; không actual permission, final efficacy, human/five-reviewer approval hoặc G DONE. Final root audit02 ghi hashes sau thêm reference này; sealed source/contract/SYN evidence giữ nguyên. |

### Xuất bản tiến độ nghiên cứu sau preparation — 01/10/2026

| ID | Nội dung nguyên tử | State | Bằng chứng / tác động |
|---|---|---|---|
| RCA-117 | Minh yêu cầu push tiến độ hiện tại lên cả hai repository P và W. | USER_CONFIRMED | Yêu cầu trực tiếp sau thảo luận RAM/usage limit: “push tiến độ hiện tại lên 2 repo giúp tôi”. |

Phạm vi xuất bản được agent chọn trong công việc được giao: commit/push hồ sơ nghiên cứu PRE-G và G30–G33 cùng mã/tests/contracts/reviews và ignore rules liên quan trên đúng các nhánh hiện hành; giữ README và .idea ngoài commit. Đây là lựa chọn đóng gói, không xác nhận mới của Minh về từng chi tiết triển khai. Telemetry, ignored generated cache và host-local keys tiếp tục ở local; Git checkpoint không phải self-contained replay bundle. Quyền xuất bản này supersede lệnh không commit/push trước đó riêng cho việc xuất bản tiến độ được yêu cầu, không cấp quyền final predictions/campaign/truth hoặc đổi frozen method. Các receipt/HEAD snapshots trước publication giữ đúng thời điểm lịch sử; không sửa hash hoặc sealed evidence để phản ánh commit mới.
