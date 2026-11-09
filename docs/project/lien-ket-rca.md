# Liên kết từ hệ thống FlashTicket sang nghiên cứu RCA

- Phiên bản: `LK-v0.7`; cập nhật theo phạm vi Minh duyệt ngày 09/11/2026.
- Thuộc bộ hệ thống tại `D:/Project/flash-ticket-platform`; nguồn nghiên cứu chính là [D:/RCA-flashticket](D:/RCA-flashticket).
- Cửa chiều ngược lại: [R0 §3](../research-rca/R0-boi-canh-va-rang-buoc.md). Không tạo cửa nối thứ ba.
- Đợt dọn chỉ cập nhật tài liệu và nguồn điều hướng; không sửa nghiệp vụ, API, schema, kiến trúc hoặc quyền runtime.

## 1. Nguồn hệ thống cấp cho nghiên cứu

Hai bộ cùng phục vụ [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md). Hệ thống chịu trách nhiệm xây dựng/đánh giá hệ bán vé, sinh dữ liệu quan sát, tích hợp và cung cấp kết quả cho người có quyền. Repo RCA chịu trách nhiệm phương pháp, giao thức và kết quả nghiên cứu.

| Thông tin nghiên cứu được nhận | Nguồn sở hữu phía hệ thống | Giới hạn sử dụng |
|---|---|---|
| Tương tác và sự kiện miền | [B4](../domain/B4-domain-event-map.md), [B5 §5.1](../domain/B5-bounded-context-map.md) | Quan hệ ngữ nghĩa không tự là API, topic, lời gọi đồng bộ hoặc cạnh runtime |
| Aggregate và bất biến | [B7](../domain/B7-aggregates-and-invariants.md) | Nhận làm bằng chứng; nghiên cứu không tự sửa bất biến hoặc ranh giới nghiệp vụ |
| Service, hạ tầng, vị trí tích hợp và quyền | [B11-C](../architecture/B11-C-target-architecture.md), [ADR-003](../adr/ADR-003-rca-rieng-chi-doc-kho-quan-sat.md), [runtime baseline](../architecture/aws-runtime-baseline.md) | Thiết kế/placement không thay bằng chứng deployment hoặc runtime |
| Datastore và hợp đồng giao tiếp | [B12](../architecture/B12-data-ownership-and-schema.md), [B13](../architecture/B13-api-and-event-contracts.md), [B14](../architecture/B14-main-flow-sequences.md) | Giữ revision, namespace, kiểu cạnh và nguồn ánh xạ khi dựng đồ thị |
| Chuẩn dữ liệu quan sát và kế hoạch kiểm | [B16](../architecture/B16-observability-baseline.md), [B15](../architecture/B15-verification-plan.md) | Baseline được duyệt không tự xác nhận coverage, tính đúng adapter hoặc chất lượng RCA |

Đọc trạng thái, revision và bằng chứng tại chính nguồn; không sao chép số node/service hay phiên bản vào đây làm nguồn cạnh tranh. Đồ thị suy ra giữ đúng trạng thái được quyết tại repo RCA; nguồn hệ thống đã duyệt không tự phê duyệt đồ thị.

## 2. Đối chiếu yêu cầu quan sát hiện hành

Đối chiếu [R0 §3](../research-rca/R0-boi-canh-va-rang-buoc.md) và nguồn P9 được dẫn tại đó với phần liên quan của hệ thống. Bộ R-01–R-10 là đầu vào nghiên cứu; yêu cầu bổ sung chưa được hệ thống chấp nhận vẫn là `CANDIDATE`/`OPEN`. Việc dọn tài liệu không tự duyệt thêm trường, instrumentation, API, cấu hình, kịch bản hoặc phương pháp.

Các bảng đối chiếu 12 ràng buộc và kết quả gate tháng 8–9 thuộc [bản lịch sử](https://github.com/levanminh04/flash-ticket-platform/blob/98d5ad5546244e5e7e29b41dbef92d454f50a7ac/docs/project/lien-ket-rca.md). Chúng không chứng nhận đáp ứng bộ yêu cầu P9 mới và không bị dùng để tự mở lại gate hệ thống đã đóng.

### 2.1 Cách dùng

1. Xác định nguồn và revision của thông tin trao đổi qua hai cửa.
2. Ghi kết quả đối chiếu trong chính tạo tác đang làm: đã có bằng chứng, cần bổ sung hoặc còn OPEN, kèm hậu quả.
3. Phần làm thay đổi mã, hợp đồng, schema, giao diện hoặc hành vi phải theo phạm vi Minh duyệt; không tự chuyển ứng viên thành yêu cầu.

### 2.2 Phép thử độc lập — giữ ranh giới hai bộ tài liệu

| # | Câu hỏi | Cách trả lời |
|---|---|---|
| 1 | Tạo tác này thuộc bộ nào? | Bộ hệ thống (`D:/Project/flash-ticket-platform/docs/`) hay bộ nghiên cứu (`D:/RCA-flashticket`) |
| 2 | Nó dẫn những nguồn nào? | Liệt kê **hết**, từ dòng “Đầu vào và phiên bản” và từ mọi trích dẫn trong thân bài |
| 3 | Có nguồn nào thuộc bộ kia không? | Đối chiếu danh sách ở câu 2 với đường dẫn của hai bộ |
| 4 | Nếu có — nó nằm ở đâu? | Nguồn qua cửa nối chỉ dùng đối chiếu ở phụ lục và giữ `CANDIDATE`/`OPEN` trong phần chưa được chấp nhận; không tự sinh hoặc sửa yêu cầu, bất biến, ranh giới hay phương pháp của bên kia |

Ghi kết quả trong phần tự kiểm của tạo tác, theo quy định hiện có. Không tạo thêm gate từ lượt dọn này.

## 3. Các điểm còn mở khi tích hợp

- Chất lượng dữ liệu thực, ánh xạ tới hợp đồng đầu vào RCA và mức hỗ trợ từng nguồn phải được kiểm trong gói tích hợp được duyệt; chưa xác nhận bằng một ảnh dashboard hoặc thiết kế B16.
- Người nhận kết quả và hình thức cung cấp kết quả vẫn theo RES-039; không tự chọn UI/API khi dọn chỉ dẫn.
- ADR-003/GOV-093 giữ quyết định Gemini của bộ hệ thống; MRP P8/DECISIONS của repo RCA ghi lựa chọn model không bắt buộc Gemini. Hai nguồn cần Minh phân xử trước khi chốt tích hợp LLM; lượt này không tự sửa ADR hoặc chọn nhà cung cấp.
- Giao thức và quyền thu/chạy/chấm FlashTicket do nguồn tương ứng và phạm vi Minh duyệt quyết; không lấy hồ sơ Task A–G cũ làm quyền chạy mới.

## 4. Lịch sử và kiểm toàn vẹn

Hồ sơ RCA cũ đã gỡ khỏi bản làm việc; xem [README RCA](../research-rca/README.md) để tra Git và bản patch giữ thay đổi chưa commit. Các quyết định hệ thống/nghiệp vụ cũ giữ giá trị trong phạm vi chưa bị thay thế. Đợt dọn không sửa snapshot, manifest, code hoặc kết quả nghiên cứu tại repo RCA.
