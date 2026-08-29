# Quy trình chẩn đoán sự cố hiện tại — B1

**Baseline đã lập:** [Quy trình hiện tại, kiểm kê logging và INC-01](B1-current-diagnosis-baseline.md).

## Baseline đã biết

Khi có bug do khách hàng báo hoặc bug âm thầm, người xử lý mở Loki hoặc SSH vào máy Linux, cố tái hiện lỗi, đợi log xuất hiện để lấy tên lỗi rồi dò đoạn mã liên quan. Mô tả này là đầu vào ban đầu, chưa phải phép đo hoàn chỉnh.

## Tình trạng tập ca — đọc ở tệp baseline, không đọc ở đây

*Sửa 2026-08-28.* Chỗ này trước đó là một **bảng rỗng** ghi `INC-01 | Chưa ghi nhận`, trong khi [tệp baseline](B1-current-diagnosis-baseline.md) §3 đã ghi `INC-01` **đầy đủ, có nguyên nhân thật**. Hai nơi nói ngược nhau, và bảng rỗng ở đây là nơi người ta mở trước. Bảng đã được gỡ; tình trạng thật:

| Hạng mục | Tình trạng |
|---|---|
| **Ca thật có đáp án gốc** | **Đúng một** — `INC-01`, lỗi khởi động do dependency không duy nhất, có chuỗi ảnh hưởng và nguyên nhân thể hiện trong log. Chi tiết ở baseline §3 |
| **Thời gian chẩn đoán của `INC-01`** | **Không có dữ liệu đáng tin cậy.** Mô tả *"vài chục phút đến vài giờ"* là tự báo cáo, **không được dùng như số đo** |
| **Ca ứng viên** | Bốn — `CAND-01` đến `CAND-04`, lấy từ audit mã nguồn, **chưa chạy lại lần nào** |
| **Còn thiếu** | Tối thiểu **hai** ca thật hoặc tái hiện được (`INC-02`, `INC-03`) |

**Vì sao con số này quan trọng:** `B9` để ngưỡng của kịch bản *dấu vết đủ tín hiệu* ở `OPEN` **chính vì cỡ mẫu bằng 1 không có ý nghĩa thống kê** (`B9-OPEN-02`), và `A3` để ngưỡng của `MT-4`, `MT-5` chờ cùng lý do. Đây là điều kiện của Giai đoạn 1, **không phải** điều kiện để mở `B9`/`B10`.

## Quy tắc

- Ưu tiên 3–5 ca thật hoặc có thể tái hiện thuộc các lớp: lỗi âm thầm, ngoại lệ, CSDL và hạ tầng.
- Ghi thời gian theo từng bước nếu có thể; không ước lượng hồi tưởng rồi trình bày như số đo.
- Không đưa log thô có bí mật/dữ liệu cá nhân vào Git. Chỉ lưu đoạn đã masking hoặc đặc trưng cần thiết.
- Baseline này dùng để phát hiện điểm nghẽn và làm mốc so sánh cho cơ chế chẩn đoán, không dùng để hứa rằng AI sẽ sửa lỗi tự động.
