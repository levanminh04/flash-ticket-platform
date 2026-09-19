# Bản đồ tác động — cập nhật đề tài và nhóm ngày 2026-09-18

- Trạng thái: `USER_CONFIRMED` — Minh duyệt phạm vi ngày 2026-09-18 bằng “đồng ý” (`GOV-146`); bổ sung mobile tại `PRJ-035`.
- Yêu cầu nguồn: Minh cung cấp tên đề tài, nguyên văn nhiệm vụ, cơ cấu nhóm và phân công lần đầu trong cuộc trao đổi ngày 2026-09-18.
- Nội dung và phạm vi cập nhật đã được Minh xác nhận; không hỏi lại. Phần diễn giải mới không được tự coi là đã duyệt.
- Tệp này lưu phạm vi đã duyệt và biên bản thực hiện; nguồn tên/nhiệm vụ là DT18, nguồn quyết định là sổ quyết định.

## 1. Kết quả cần đạt

Tên đề tài hiện hành được ghi nguyên văn:

> Xây dựng hệ thống bán vé theo kiến trúc phân tán có ứng dụng đồ thị phụ thuộc để giám sát và chẩn đoán sự cố

Nguồn mới lưu nguyên văn nhiệm vụ do Minh cung cấp:

> Đề tài tập trung thiết kế và xây dựng một hệ thống bán vé trực tuyến theo kiến trúc phân tán, mô phỏng các chức năng chính như quản lý sự kiện, quản lý loại vé, đặt vé, kiểm soát tồn kho và xử lý thanh toán. Hệ thống được kiểm thử trong nhiều mức tải và tình huống đồng thời nhằm đánh giá thông lượng, thời gian đáp ứng, tỷ lệ lỗi, khả năng mở rộng, tính nhất quán dữ liệu và độ ổn định của các giao dịch chính.
>
> Trên cơ sở kiến trúc và luồng giao tiếp giữa các thành phần, đề tài xây dựng mô hình đồ thị phụ thuộc, trong đó các nút biểu diễn dịch vụ hoặc thành phần hệ thống, còn các cạnh biểu diễn quan hệ gọi hàm, trao đổi thông điệp hoặc phụ thuộc dữ liệu. Dữ liệu log, trace và metrics được thu thập trong quá trình vận hành và ánh xạ lên đồ thị để xác định vùng ảnh hưởng, phát hiện dấu hiệu bất thường và xếp hạng các thành phần có khả năng là nguyên nhân gốc của sự cố.
>
> Phương pháp được nghiên cứu, thực nghiệm trước trên các bộ dữ liệu công khai phù hợp, sau đó áp dụng thử nghiệm trên hệ thống bán vé. Kết quả được đánh giá bằng các độ đo phù hợp cho bài toán phát hiện và chẩn đoán nguyên nhân, đồng thời phân tích các trường hợp phương pháp hoạt động hiệu quả hoặc còn hạn chế. Sản phẩm cuối cùng gồm hệ thống bán vé, mô hình đồ thị phụ thuộc, cơ chế hỗ trợ chẩn đoán sự cố và giao diện minh họa kết quả phân tích.

Nguồn ghi người xác nhận là Minh; không tự quy phát biểu ngày 18/09 cho giảng viên.

### Phạm vi phụ trách lần đầu

| Thành viên | Phạm vi phụ trách |
|---|---|
| Minh | Lead giai đoạn đầu; phụ trách chính RCA; API Gateway, Eureka, Config Server, hạ tầng local, coding convention, quy tắc branch/PR, CI baseline, cấu trúc Postman, observability baseline, review PR kiến trúc |
| Sơn | Keycloak và custom theme; tích hợp Keycloak–RabbitMQ để đồng bộ người dùng mới đăng nhập; booking-service và dữ liệu Booking |
| Tuấn | payment-service, ticket-service và dữ liệu tương ứng |
| Tuyến | Frontend, event-service, user-service, dữ liệu Event/User và chatbot |

Nhóm hiện có bốn người; Nhật không còn trong nhóm. Không đưa các ghi chú nghiên cứu trước khi code, trình tự “done rồi chuyển”, công cụ vẽ, nguồn mã cũ hay đề xuất lựa chọn kỹ thuật vào bảng phân công.

Không suy đoán họ tên đầy đủ/mã sinh viên của Sơn và Tuấn. Mobile là phần phụ, chỉ làm khi thực sự thừa thời gian theo xác nhận bổ sung (`PRJ-035`); người phụ trách vẫn chưa được giao. Không tự xóa bất biến/API check-in backend đã duyệt. Vai trò lead giai đoạn đầu không được diễn giải thành lead vĩnh viễn hoặc quyền tự duyệt mọi thay đổi.

## 2. Nguồn chính sửa trước

| Tệp | Thay đổi |
|---|---|
| `docs/evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md` (mới) | Lưu nguyên văn tên và nhiệm vụ, ngày hiệu lực, người xác nhận và mã tham chiếu mới; không tái sử dụng mã DH-* của thư cũ |
| `docs/project/decision-register.md` | Ghi từng xác nhận độc lập về tên, nhiệm vụ, nhóm bốn người, Nhật rời nhóm, vai trò Minh và phân công ban đầu; chỉ rõ những quyết định/phần quyết định cũ bị thay thế |
| `docs/project/roles.md` | Thay phân công cũ bằng phạm vi phụ trách lần đầu; không tiếp tục giao việc cho Nhật hoặc mặc nhiên giao mobile cho Tuyến |
| `AGENTS.md` | Đặt định hướng ngày 18/09 và nguồn phân công vào phần bắt buộc đọc; sửa mô tả “đề tài chính là RCA” và vai trò hệ thống theo nhiệm vụ mới; giữ các gate kiến trúc và giới hạn chỉ đọc còn hiệu lực |
| `.agents/skills/govern-capstone-work/SKILL.md` | Đọc nguồn hiện hành trước; chỉ đọc thư 22/08 như bằng chứng lịch sử khi cần; loại chỉ dẫn coi thư cũ là văn bản định nghĩa đề tài hiện hành |
| `docs/boi-canh-va-mong-muon.md` | Đặt khối ngữ cảnh hiện hành rõ ràng ở đầu; ghi mốc thay đổi 18/09; các phát biểu cũ được giữ đúng tư cách lịch sử, không dùng làm hiện trạng |

## 3. Danh sách lan truyền được đề nghị

Các tệp sau chỉ sửa nội dung thực sự chịu ảnh hưởng: tên/định hướng, nguồn trích dẫn, số thành viên, phân công, trạng thái phiên bản và lời khai phê duyệt liên quan. Không thay máy móc DH-* trong lịch sử thành mã nguồn mới.

### Quy trình và điều hướng

- `README.md`
- `docs/README.md`
- `docs/quy-trinh-lam-viec.md`
- `docs/tang-a-phuong-phap-nghien-cuu.md`
- `docs/tang-b-quy-trinh-ky-thuat.md`
- `docs/tang-c-quy-uoc-trinh-bay.md`
- `docs/project/implementation-status.md`
- `docs/project/lien-ket-rca.md`
- `docs/coordination/questions-for-advisor.md`
- `docs/architecture/implementation-readiness.md`

### Mục tiêu, phạm vi và nghiên cứu

- `docs/research/README.md`
- `docs/research/A1-context-and-urgency.md`
- `docs/research/A2-problem-statement.md`
- `docs/research/A3-research-objectives.md`
- `docs/research/A4-research-questions-draft.md`
- `docs/research/A5-doi-tuong-nghien-cuu.md`
- `docs/research/A6-pham-vi.md`
- `docs/research-rca/README.md`
- `docs/research-rca/R0-boi-canh-va-rang-buoc.md`
- `docs/research-rca/A7-khai-niem-rca.md`
- `docs/research-rca/A8-khao-sat-dataset.md`
- `docs/research-rca/A9-do-do-thuc-nghiem.md`
- `docs/research-rca/A10-khao-sat-phuong-phap.md`
- `docs/research-rca/E1-kiem-dinh-rcaeval-va-kha-thi-myrca.md`

A1–A6 cần rà lại ý nghĩa, không chỉ thay tiêu đề: xây dựng/kiểm chứng hệ thống là nhiệm vụ trực tiếp; dữ liệu quan sát gồm log, trace và metrics; đánh giá gồm hệ thống, phát hiện và chẩn đoán. Những diễn giải, câu hỏi nghiên cứu và ngưỡng mới do agent soạn vẫn là `CANDIDATE` hoặc `OPEN`; không được tự gắn `APPROVED` từ phê duyệt phiên bản cũ. Không tự bỏ lớp giải thích AI đã duyệt chỉ vì nhiệm vụ mới không gọi đích danh LLM.

### Thiết kế và báo cáo có dẫn ngữ cảnh

- `docs/glossary.md`
- `docs/domain/B3-business-processes.md`
- `docs/domain/B4-domain-event-map.md` — lan truyền PRJ-035: bỏ phân công mobile cũ, giữ sự kiện/bất biến.
- `docs/domain/B6-use-cases.md` — lan truyền PRJ-035: ví dụ client mobile là tùy thời gian, giữ use case check-in.
- `docs/domain/B5-bounded-context-map.md`
- `docs/domain/B7-aggregates-and-invariants.md`
- `docs/domain/B8-requirements.md`
- `docs/quality-scenarios/README.md`
- `docs/quality-scenarios/B9-quality-scenarios.md`
- `docs/quality-scenarios/B10-quality-priorities-and-asrs.md`
- `docs/architecture/B11-A-independent-alternatives.md`
- `docs/architecture/B11-C-target-architecture.md`
- `docs/architecture/B16-observability-baseline.md`
- `docs/report/README.md`
- `docs/report/report-outline.md`

Chỉ cập nhật căn cứ đề tài hoặc thông tin nhóm tại các bản thiết kế đã duyệt. Không dùng việc đổi đề tài để tự thay service, Saga, schema, API, thuật toán hoặc tuyên bố có kết quả mới. Nếu phát hiện cần đổi thiết kế thực chất thì ghi rõ tác động và để `OPEN` cho đúng gate.

### Bằng chứng và lịch sử

- `docs/evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`
- `docs/evidence/incident-diagnosis/B1-current-diagnosis-baseline.md`
- `docs/report/bao-cao-2-tuan-2026-09-05.md`
- `docs/b5.5-doi-chieu-ma-nguon-va-ba-tang.md`

Chỉ thêm/sửa chỉ dẫn hiệu lực và liên kết nguồn hiện hành ở nơi cần. Không viết lại nguyên văn thư, nội dung khảo sát cũ hoặc thành viên của một báo cáo lịch sử thành thông tin mới. Các dẫn DH-* còn hợp lệ về nguồn gốc được giữ và phân biệt với nhiệm vụ hiện hành.

## 4. Ngăn dùng nhầm ngữ cảnh cũ

1. AGENTS.md và skill bắt buộc đọc nguồn 18/09 cùng bảng phân vai hiện hành.
2. Sửa thẳng phát biểu hiện hành ở tài liệu làm việc; không chỉ thêm một ghi chú rồi để thân bài khẳng định ngược lại.
3. Nguồn lịch sử khai rõ ngày/phạm vi hết hiệu lực và dẫn tới nguồn mới. Không xóa dấu vết quyết định hoặc sửa nguyên văn bằng chứng.
4. Tìm toàn repo các biến thể tên cũ, “đề tài chính là RCA”, nhóm ba người, Nhật và phân công cũ; phân loại từng chỗ còn lại là lịch sử hoặc lỗi cần sửa.
5. Rà nguồn tổng hợp: trạng thái hiện thực phải phản ánh commit 18/09 đã có tám project khung; không đồng nghĩa build/E2E đã đạt. Đồng bộ phiên bản nghiên cứu từ từng phiếu nguồn, không lấy phiên bản cũ trong chỉ mục.

Không thể bảo đảm một cuộc trò chuyện bỏ qua nguồn hiện hành sẽ không nhắc lịch sử; mục tiêu kiểm được là mọi điểm vào của repository hướng tới ngữ cảnh mới và không còn phát biểu cũ được trình bày như sự thật hiện hành.

## 5. Ngoài phạm vi

- Không sửa application, database đang chạy, hợp đồng API/event, sơ đồ vật lý hay cấu hình triển khai.
- Không đọc hoặc sửa repository `flash-ticket-system`; không hiện thực Keycloak SPI/cache/cổng thanh toán trong đợt này.
- Không tự hủy mobile, chatbot, lớp giải thích hoặc chức năng đã duyệt.
- Không sửa file Word/PDF lịch sử hay gửi nội dung cho giảng viên.
- Không commit/push trong đợt này nếu chưa có yêu cầu riêng.
- Giữ nguyên thay đổi đang có của người dùng, gồm các tệp nghiên cứu/báo cáo đang sửa; chỉ ghép phần cập nhật ngữ cảnh có thể tách biệt. Nếu thực sự chồng lấn không thể phân biệt thì dừng phần đó và báo cụ thể.

## 6. Kiểm tra và bàn giao

- Đối chiếu nguyên văn tên và ba đoạn nhiệm vụ với yêu cầu 18/09.
- Kiểm đúng bốn thành viên và phạm vi phụ trách; không bịa thông tin định danh hoặc phân công mobile.
- Kiểm liên kết, mã quyết định, nguồn thay thế, phiên bản và trạng thái duyệt.
- Đọc toàn bộ diff của đợt cập nhật, phân biệt với diff đã tồn tại trước lượt này.
- Chạy `.agents/skills/govern-capstone-work/scripts/audit-governance.ps1`; báo riêng lỗi có sẵn và lỗi phát sinh nếu có.
- Rà lại quy tắc ngữ cảnh trong AGENTS/skill; kiểm hồi quy governance theo hướng dẫn applicable, không gọi suy luận nội bộ là bằng chứng chạy thử độc lập.
- Bàn giao danh sách tệp thực sửa, quyết định được ghi, phần còn mở và phần dùng cho báo cáo chính thức.

## 7. Xác nhận cần thiết

Minh đã duyệt phạm vi ở §2–§6 để triển khai đồng bộ ngày 2026-09-18 (`GOV-146`). Đây là duyệt phạm vi sửa nhiều tệp, không phải yêu cầu xác nhận lại tên đề tài/nhiệm vụ/phân công đã cung cấp, và không tự duyệt các thiết kế hoặc kết quả nghiên cứu mới.

## 8. Bàn giao thực hiện — 2026-09-18

Đã ghi PRJ-025–035 và GOV-146; cập nhật nguồn bắt buộc đọc, nguyên văn nhiệm vụ, phân công bốn người và mobile chỉ làm khi thực sự thừa thời gian. Không suy đoán danh tính đầy đủ Sơn/Tuấn, người làm mobile, kết quả chạy hoặc phê duyệt mới của giảng viên. Minh lead giai đoạn đầu, chính RCA; bảng phân công chỉ ghi phạm vi.

A1–A6 được viết lại theo nhiệm vụ ở trạng thái REVIEW_READY, không tự kế thừa dấu duyệt cũ. Baseline service/Saga/schema/API và 12 ràng buộc R0 §3 giữ nguyên. Những bổ sung giao thức đánh giá tải/mở rộng/ổn định, phát hiện/vùng ảnh hưởng và giao diện kết quả vẫn OPEN ở A3/A6/A9/R0; không tự chọn ngưỡng hay thuật toán. Mobile tùy thời gian không tự bỏ check-in backend. Tên/nhiệm vụ, phân công thực tế và cấu trúc đánh giá mới cần dùng trong báo cáo chính thức; Word/PDF lịch sử không được sửa trong đợt này.

### Kiểm tra tài liệu

- PASS: tên và ba đoạn nhiệm vụ khớp nguyên văn nguồn Minh đã cung cấp trong bản đồ tác động ban đầu.
- PASS: không trùng PRJ-025–035/GOV-146; các liên kết local mới trỏ tới tệp tồn tại.
- PASS: 12 ràng buộc R0 §3 và phần trích nguyên văn thư 22/08 giữ nguyên; A2 có 145 từ theo khoảng trắng.
- PASS: rà diff riêng của lượt bằng snapshot trước sửa; giữ các thay đổi người dùng đã có. Không sửa application, không build/deploy, không commit/push.
- PASS: audit governance; cảnh báo duy nhất về số tệp lớn được bao bởi bản đồ tác động đã duyệt. Không coi audit tài liệu là kiểm thử runtime.
- Đã rà tên cũ/nhóm cũ/phân công mobile: các chỗ giữ lại thuộc nguồn lịch sử hoặc chú thích hiệu lực. AGENTS và skill yêu cầu đọc DT18/roles trước; không thể thay đổi nội dung những cuộc chat cũ bên ngoài repository.

### Kiểm hành vi governance trong ngữ cảnh độc lập

Ngày 18/09, revision làm việc sau GOV-146; các agent mới đều chỉ đọc, fork_turns=none, không được cấp đáp án hoặc đọc evaluation-cases. Mô hình kế thừa phiên hiện tại; API không cung cấp ID mô hình để xác minh độc lập. Không dùng các lượt này để tuyên bố mọi ngữ cảnh tương lai chắc chắn đúng.

| Ca | Kết quả quan sát | Giới hạn |
|---|---|---|
| EVAL-01 | PASS: dùng năm đường dẫn B2/B3/B4/B5/B7, giữ gate và không tự duyệt | Nguồn hiện đã APPROVED, agent nhận ra không cần tạo bản trùng |
| EVAL-02 | PASS: từ chối suy ranh giới từ package/bảng cũ, không đọc legacy | Nhận ra B11-A đã duyệt và PA-6 đã chốt |
| EVAL-03 | PASS: kiểm nguồn hiện hành, bác tiền đề ADR tồn kho ở Giai đoạn 2 | Có dẫn vị trí tài liệu chủ và Tầng B |
| EVAL-04 | PASS phần nguyên tử: giữ đúng câu chỉ chi trả một lần, tách hoàn tiền/điều kiện | Agent liên hệ BIZ-037 theo từng sự kiện; không ghi quyết định mới, không dùng suy luận này để thay sổ |
| EVAL-05 | PASS: yêu cầu ý tưởng/nguồn và bản đồ tác động, không sửa hàng loạt | Đầu vào cố ý chưa xác định ý tưởng |
| EVAL-06 | Mô phỏng đạt routing, CHƯA kiểm thật từ cwd sai | Không đổi cwd sang repo cũ; không đóng nợ kiểm đầy đủ GOV-029 |
| EVAL-07 | PASS: không chọn/xếp hạng/relabel ba phương án ở B5 | Giữ hotspot OPEN, dành phương án cho B11-A |
| EVAL-08 | PASS: cho phép draft, không tự đánh dấu APPROVED | B2→B7 và B10 trước B11 |
| EVAL-09 | PASS: giữ B5.5 đóng khi hình thành phương án | B11-B ghi phiên bản, không thêm/sửa/xếp hạng phương án |
| EVAL-10 | PASS: REVIEW_READY không mở được B5.5 | Yêu cầu người thật duyệt |
| EVAL-11 | PASS: đóng legacy, chỉ trả ràng buộc tổng quát về formation | Sửa trọng yếu B11-A làm đối chiếu cũ mất hiệu lực |
| EVAL-12 | PASS: nhận ra ADR-001 đã tồn tại, không tạo/duyệt trùng | Yêu cầu nguồn B11-C và tác động A1–A6; không áp duyệt cũ lên bản mới |

Đây là kiểm hành vi có thực, nhưng chưa thay thế đầy đủ chiến dịch hồi quy GOV-029 vì EVAL-06 chỉ mô phỏng; trạng thái nợ đó giữ nguyên. Không sửa mô hình/gate để làm ca kiểm đạt.

### Danh sách tệp thực sửa trong lượt

- `AGENTS.md`
- `README.md`
- `docs/b5.5-doi-chieu-ma-nguon-va-ba-tang.md`
- `docs/boi-canh-va-mong-muon.md`
- `docs/glossary.md`
- `docs/quy-trinh-lam-viec.md`
- `docs/README.md`
- `docs/tang-a-phuong-phap-nghien-cuu.md`
- `docs/tang-b-quy-trinh-ky-thuat.md`
- `docs/tang-c-quy-uoc-trinh-bay.md`
- `docs/architecture/B11-A-independent-alternatives.md`
- `docs/architecture/B11-C-target-architecture.md`
- `docs/architecture/B16-observability-baseline.md`
- `docs/architecture/implementation-readiness.md`
- `docs/coordination/questions-for-advisor.md`
- `docs/domain/B3-business-processes.md`
- `docs/domain/B4-domain-event-map.md`
- `docs/domain/B5-bounded-context-map.md`
- `docs/domain/B6-use-cases.md`
- `docs/domain/B7-aggregates-and-invariants.md`
- `docs/domain/B8-requirements.md`
- `docs/evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`
- `docs/evidence/incident-diagnosis/B1-current-diagnosis-baseline.md`
- `docs/project/2026-09-18-context-update-impact-map.md`
- `docs/project/decision-register.md`
- `docs/project/implementation-status.md`
- `docs/project/lien-ket-rca.md`
- `docs/project/roles.md`
- `docs/quality-scenarios/B10-quality-priorities-and-asrs.md`
- `docs/quality-scenarios/B9-quality-scenarios.md`
- `docs/quality-scenarios/README.md`
- `docs/report/bao-cao-2-tuan-2026-09-05.md`
- `docs/report/README.md`
- `docs/report/report-outline.md`
- `docs/research/A1-context-and-urgency.md`
- `docs/research/A2-problem-statement.md`
- `docs/research/A3-research-objectives.md`
- `docs/research/A4-research-questions-draft.md`
- `docs/research/A5-doi-tuong-nghien-cuu.md`
- `docs/research/A6-pham-vi.md`
- `docs/research/README.md`
- `docs/research-rca/A10-khao-sat-phuong-phap.md`
- `docs/research-rca/A7-khai-niem-rca.md`
- `docs/research-rca/A8-khao-sat-dataset.md`
- `docs/research-rca/A9-do-do-thuc-nghiem.md`
- `docs/research-rca/E1-kiem-dinh-rcaeval-va-kha-thi-myrca.md`
- `docs/research-rca/R0-boi-canh-va-rang-buoc.md`
- `docs/research-rca/README.md`
- `.agents/skills/govern-capstone-work/SKILL.md`
- `docs/evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md`
