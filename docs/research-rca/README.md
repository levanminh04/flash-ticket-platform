# Bộ tài liệu nghiên cứu — Chẩn đoán nguyên nhân gốc

Thư mục này là **bộ tài liệu thứ hai** của đồ án, mở ngày 2026-08-24 (`RES-010`, nay đọc theo `RES-028` và `RES-033`).

> **Độc lập cái gì, và không độc lập cái gì.** Bộ này độc lập với bộ hệ thống ở `docs/` về **nhịp làm việc và cổng kiểm soát**. Nó **không** độc lập về đề tài, và **không** bị cấm đọc bộ kia: `RES-028` viết ranh giới theo **quyền quyết định** chứ không theo quyền đọc — bộ này được nhận `B4`, `B5` §5.1 và `B7` qua cửa *Hệ thống → Nghiên cứu* tại `docs/project/lien-ket-rca.md`, giữ nguyên nguồn và phiên bản khi trích, nhưng **không được** sửa một bất biến, ranh giới hay yêu cầu của bộ hệ thống. Đề tài chính là *"Chẩn đoán nguyên nhân gốc sự cố giao dịch trực tuyến bằng đồ thị phụ thuộc"*, và `DH-MT1` đặt hệ thống đặt vé vào **mục tiêu đầu tiên** của chính đề tài đó. Việc tách bộ **không** hạ vai trò hay hủy bộ tài liệu hệ thống.

## Vì sao tách riêng

Lý do tách là **nhịp làm việc và cổng kiểm soát**, không phải nội dung. Ép mọi thứ vào cùng một chuỗi phiếu A1–A9 và cùng chuỗi gate Tầng B làm cả hai bị méo: phiếu khảo sát phương pháp phải chờ gate phân tích miền, còn phiếu nghiệp vụ phải mang nội dung khảo sát thuật toán.

Tách ra thì mỗi bộ chạy theo nhịp của nó. Hai bộ vẫn phục vụ **một** đề tài và nối với nhau qua hai cửa ở §Quy tắc. Quyển báo cáo cuối được tổng hợp có chọn lọc từ cả hai nguồn (`RES-024`).

**Phân vai theo trách nhiệm** (`RES-033`): bộ hệ thống chịu trách nhiệm **dữ liệu quan sát, điểm tích hợp, quyền chỉ đọc và việc kết quả đến được người dùng**; bộ này chịu trách nhiệm **phương pháp chẩn đoán và lớp giải thích**.

### Lớp giải thích thuộc bộ này

> **"Trợ lý RCA"** là tên gọi tắt của **lớp giải thích** ở bước cuối chuỗi RCA — đúng thứ `DH-MT4` gọi là *"trợ lý"*. Nó **không phải** nhánh `T`, **không phải** trợ lý cũ, và **không** chạy đường ống `log → Drain → context → LLM API`.

Chuỗi đầy đủ: **đồ thị phụ thuộc → ánh xạ log và trace → phát hiện bất thường → lan truyền và xếp hạng → giải thích**. Bốn bước đầu là `DH-MT1`–`DH-MT3`; bước cuối là `DH-MT4`. Cả chuỗi được thiết kế ở bộ này và **chạy trong FlashTicket** (`RES-023` mức 2, gate `B11`).

Ba giới hạn còn nguyên hiệu lực cho lớp giải thích, chép từ `PRJ-002` sang `RES-034`: không cam kết loại bỏ việc tái hiện lỗi · không tự kết luận nguyên nhân cuối cùng · không tự sửa hệ thống. Cách đo ghi tại `A9` §6.

## Danh sách tài liệu

| Phiếu | Tệp | Nội dung | Trạng thái |
|---|---|---|---|
| R0 | `R0-boi-canh-va-rang-buoc.md` | Ngữ cảnh chốt ngày 2026-08-24; **mười hai ràng buộc gửi tới `B9`–`B16`** để thiết kế hệ thống không chặn nhánh chẩn đoán | `DRAFT` |
| A7 | `A7-khai-niem-rca.md` | Từ vựng nền: bốn nhóm tên riêng không được lẫn; bốn họ phương pháp; công thức độ đo; mức sàn ngẫu nhiên | `DRAFT` |
| A8 | `A8-khao-sat-dataset.md` | Khảo sát bộ dữ liệu đã công bố; hai nguồn giảng viên gửi; điều kiện chạy đối chiếu máy thật | `DRAFT` |
| A9 | `A9-do-do-thuc-nghiem.md` | Độ đo xếp hạng theo quy chuẩn và cách đọc | `DRAFT` |
| A10 | `A10-khao-sat-phuong-phap.md` | Bốn họ phương pháp; tiêu chí chọn và tập phương pháp đối chứng; lý do loại từng phương pháp | `DRAFT` |

## Quy tắc của bộ tài liệu này

1. **Quyền quyết định, không phải quyền đọc** (`RES-028`). Bộ này **được nhận** `B4`, `B5` §5.1 và `B7` qua cửa *Hệ thống → Nghiên cứu* để dựng đồ thị cho `DH-MT1` — giữ nguyên nguồn và phiên bản khi trích, và đồ thị suy ra là `CANDIDATE` cho tới khi được duyệt. Nó **không được** sửa một bất biến, ranh giới hay yêu cầu của bộ hệ thống. Ngược lại, bộ hệ thống **không được** để yêu cầu quan sát từ đây quyết định số service hay mẫu kiến trúc.

   > **Cách viết cũ đã bị thu hồi.** Quy tắc này từng ghi *"bộ này không đọc `B2`–`B8` để hình thành nội dung"*. Câu đó cấm đúng thứ mà `DH-MT1` bắt buộc — dựng đồ thị từ mô hình hệ giao dịch. `RES-028` thay bằng ranh giới quyền quyết định.

   Hai bộ nối qua **đúng hai cửa**: `R0` §3 cho chiều nghiên cứu → hệ thống, và `docs/project/lien-ket-rca.md` cho chiều hệ thống → nghiên cứu. Nội dung đi qua cửa chỉ được ghi ở `CANDIDATE`/`OPEN`. Không tạo cửa thứ ba.
2. **Không chặn và không bị chặn.** Bộ hệ thống tiếp tục `B9` không cần chờ đây. Thực nghiệm ở đây không cần chờ backend.
3. **Nguồn dùng chung.** Sổ nguồn duy nhất cho cả hai bộ vẫn là `docs/research/source-register.md`. Không tách thành hai danh mục tài liệu tham khảo, vì quyển báo cáo cuối chỉ có một.
4. **Không tuyên bố tính mới** khi chưa có kết quả thực nghiệm chống lưng. Dùng lại một cơ chế đã công bố không tạo ra tính mới.
5. **Một bảng chỉ chứa kết quả của một bộ dữ liệu.** Không trộn số giữa hai bộ, không trộn hai bộ độ đo.
6. **AI không tự duyệt tạo tác của chính nó.** Quy tắc `GOV-011` áp dụng cho bộ này như mọi tạo tác khác.

## Đọc theo thứ tự nào

Người mới vào: `A7` trước — nó phân biệt bốn nhóm tên riêng mà mọi phần sau đứng trên. Rồi `A10` để biết có những họ phương pháp nào. `A8` và `A9` là tra cứu khi cần.

Người sắp chốt một gate của Tầng B (`B9` trở đi): đọc **`R0` §3**, rồi chạy **Phép thử độc lập** tại `docs/project/lien-ket-rca.md` §2.2.

## Điểm cần biết ngay

- **Số so sánh kỳ này lấy từ bộ dữ liệu đã công bố** — đó là đúng điều `DH-DATA` yêu cầu, vì chỉ bộ công bố mới có nhãn nguyên nhân thật. Điều này **không** có nghĩa FlashTicket đứng ngoài: lộ trình ba mức ở `R0` §2.4 và `RES-023` đưa cơ chế lên chạy thật trên hệ nhà ở mức 2. Bộ chính là **RE2** (`RES-022`).
- **Mã phiếu còn nợ một lần sửa.** Bốn phiếu vẫn dùng `A7`–`A10`, trùng nghĩa với `A7`/`A8`/`A9` mà Tầng A định nghĩa là phương pháp nghiên cứu, ý nghĩa/đóng góp và bố cục/phân công. Ghi tại `RES-009` và `R0-OPEN-05`; đổi sang `R1`–`R4` kéo theo khoảng 59 tham chiếu mã nên chưa làm.

  > 🔴 **Quy tắc bắt buộc trong khi chưa đổi mã** (`GOV-046`, 2026-08-29). Trích bốn phiếu này **từ bên ngoài thư mục `docs/research-rca/`** thì phải viết **đường dẫn đầy đủ**: `docs/research-rca/A9-do-do-thuc-nghiem.md` §6, **không** viết trần `A9` §6. Bên trong thư mục này viết trần được.
  >
  > **Vì sao không để tới lúc đổi mã:** tiêu chí đo `MT-5` nằm ở `A9` §6 của bộ này, trong khi `A9` của Tầng A là *bố cục đồ án và phân công*. Người mở nhầm sẽ không thấy gì và kết luận tài liệu thiếu. Vòng rà 2026-08-28 đã bắt được một chỗ viết trần ở `A6` §1 mục 4.
