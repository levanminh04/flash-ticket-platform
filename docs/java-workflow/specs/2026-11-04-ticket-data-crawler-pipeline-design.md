# Spec — Pipeline Crawl Dữ Liệu Bán Vé, Chuẩn Hóa Schema & Nạp Vào Database Server 119

- **Ngày:** 2026-11-04
- **Trạng thái:** DRAFT — Chờ review hoàn tất trước khi lập kế hoạch triển khai
- **Mã nhánh / Module:** `tools/ticket-data-pipeline`
- **Mục tiêu chính:** Xây dựng công cụ Python CLI độc lập để crawl dữ liệu sự kiện thực tế (Ticketbox, Ticketgo...), chuẩn hóa theo schema phân tán của FlashTicket, sinh dữ liệu đơn hàng giả lập và nạp an toàn vào các PostgreSQL database trên server 119 (`3.106.109.119`).

---

## 1. Bối cảnh & Mục tiêu

### 1.1. Bối cảnh dự án
Hệ thống FlashTicket là nền tảng bán vé theo kiến trúc microservices phân tán phục vụ đề tài tốt nghiệp: *"Xây dựng hệ thống bán vé theo kiến trúc phân tán có ứng dụng đồ thị phụ thuộc để giám sát và chẩn đoán sự cố (RCA)"*.
Để phục vụ việc thử nghiệm tải cao, mô phỏng hành vi giao dịch và chẩn đoán sự cố, hệ thống cần một tập dữ liệu lớn, chân thực về:
1. **Danh mục sự kiện & Hạng vé:** Có hình ảnh, tiêu đề, địa điểm, thời gian tổ chức và nhiều mức giá vé thực tế tại Việt Nam.
2. **Dữ liệu đơn hàng & Vé đã đặt:** Mô phỏng giao dịch đặt vé của người dùng, liên kết xuyên suốt qua các cơ sở dữ liệu `event_db`, `booking_db`, `payment_db`, `ticket_db`.

### 1.2. Mục tiêu kỹ thuật
- Xây dựng pipeline dữ liệu 3 giai đoạn độc lập: **Crawl $\rightarrow$ Format & Generate $\rightarrow$ Import**.
- Tạo công cụ CLI bằng Python, đặt tại thư mục `tools/ticket-data-pipeline/`, cô lập hoàn toàn khỏi runtime của các microservices Java và React frontend.
- Tự động sinh file script SQL chuẩn hóa (tương tự như `docs/java-workflow/reports/2026-11-02-booking-flow-seed119.sql`) tuân thủ nghiêm ngặt các domain constraint và cấu trúc phân quyền trên server 119.
- Hỗ trợ nạp tự động qua SSH Host `ft119` hoặc xuất file để review trước khi nạp thủ công.

---

## 2. Hiện trạng Hạ tầng & Ràng buộc Kỹ thuật

### 2.1. Cấu hình Server 119
- **Host SSH:** `ft119` (IP: `3.106.109.119`, User: `ubuntu`, Port: `22`, IdentityFile: `~/.ssh/flashticket_key` theo `~/.ssh/config`).
- **PostgreSQL Server:** Chạy local trên port 5432 của server 119, quản trị bởi user `postgres`.

### 2.2. Quy ước Database & Phân quyền trên Server 119
Hệ thống sử dụng quy ước đặt tên bảng có tiền tố `tbl_` (theo `services/99-table-prefix.sql`) và tách riêng từng Database / Schema:

| Database | Schema | Role Owner | Các bảng chính liên quan |
| :--- | :--- | :--- | :--- |
| `event_db` | `event_schema` | `event_owner` | `tbl_categories`, `tbl_venues`, `tbl_events`, `tbl_ticket_types`, `tbl_event_images` |
| `booking_db` | `booking_schema` | `booking_owner` | `tbl_event_sales_snapshots`, `tbl_ticket_type_inventory`, `tbl_orders`, `tbl_order_items` |
| `payment_db` | `payment_schema` | `payment_owner` | `tbl_event_finance_snapshots`, `tbl_payment_attempts` |
| `ticket_db` | `ticket_schema` | `ticket_owner` | `tbl_event_access_snapshots`, `tbl_tickets` |

### 2.3. Ràng buộc Nghiệp vụ cốt lõi (Domain Invariants)
1. **Đồng bộ Snapshot:** Một sự kiện trong `event_db` chỉ có thể mở bán khi có đầy đủ snapshot tương ứng trong `booking_db` (`tbl_event_sales_snapshots`), `payment_db` (`tbl_event_finance_snapshots`), và `ticket_db` (`tbl_event_access_snapshots`).
2. **Ràng buộc Thời gian:**
   - `sale_starts_at < sale_ends_at <= ends_at`
   - `checkin_starts_at = starts_at` và `checkin_ends_at = ends_at`
3. **Tiền tệ:** Chỉ chấp nhận currency `'VND'`.
4. **Sales Mode:** Mặc định `'QUANTITY'` cho dữ liệu crawl thông thường.
5. **Cấu hình phiên bản:** `source_config_version = 1`, `row_version = 0`.
6. **Keycloak Subjects:** `organizer_subject` và `buyer_subject` phải là UUID hợp lệ (tương thích Keycloak realm `flash-ticket`).

---

## 3. Kiến trúc Pipeline & Cấu trúc Thư mục

### 3.1. Sơ đồ luồng dữ liệu (Data Flow)

```
[ Nguồn Vé: Ticketbox / Ticketgo ]
                │  (httpx / Rate Limit / User-Agent)
                ▼
      [ 1. Raw Extractor ]
                │  Lưu cache
                ▼
   storage/raw/<source>/<id>.json
                │
                ▼
     [ 2. Data Normalizer ]  <───  Cấu hình Organizer / Default Dates
                │
     Pydantic Domain Models
                │
                ├───> [ 3. Snapshot Builder ]   (Tạo JSONB cho Booking/Payment/Ticket)
                │
                └───> [ 4. Order Generator ]    (Sinh Orders, Items, Tickets, Payments)
                                │
                                ▼
                      [ 5. SQL Exporter ]
                                │
                                ▼
                 storage/sql/import-events-<ts>.sql
                                │
                                ▼
                     [ 6. Server 119 Importer ]
                                │  (SCP + SSH execution via ft119)
                                ▼
         [ PostgreSQL 119: event_db, booking_db, payment_db, ticket_db ]
                                │
                                ▼
                    [ 7. Post-Import Verification ]
                                │
                                ▼
                 storage/reports/<ts>-summary.json
```

### 3.2. Cấu trúc thư mục nguồn

```
tools/ticket-data-pipeline/
├── crawlers/
│   ├── __init__.py
│   ├── base.py                   # Interface BaseScraper
│   ├── ticketbox.py              # Extractor cho Ticketbox (v2 API / Web)
│   └── ticketgo.py               # Extractor cho Ticketgo
├── models/
│   ├── __init__.py
│   ├── raw_models.py             # Dữ liệu thô từ nguồn
│   ├── flashticket_models.py     # Domain models chuẩn FlashTicket (Category, Venue, Event, TicketType)
│   └── simulated_models.py       # Dữ liệu giao dịch mô phỏng (Order, OrderItem, Ticket, Payment)
├── transformers/
│   ├── __init__.py
│   ├── normalizer.py             # Map và làm sạch dữ liệu, tịnh tiến thời gian (--shift-future)
│   └── snapshot_builder.py       # Tạo JSONB schemaVersion: 1 cho các snapshot
├── generators/
│   ├── __init__.py
│   └── order_generator.py        # Giả lập đơn hàng, người mua, phân bổ trạng thái và trừ tồn kho
├── exporters/
│   ├── __init__.py
│   └── sql_builder.py            # Sinh tệp SQL chuẩn tuân thủ ROLE, search_path và ON CONFLICT
├── importers/
│   ├── __init__.py
│   ├── ssh_runner.py             # Kết nối SSH Host ft119, upload tệp và chạy psql
│   └── verifier.py               # Truy vấn đối chiếu số lượng bản ghi sau import
├── storage/                      # Thư mục lưu dữ liệu trung gian (bỏ qua Git)
│   ├── raw/                      # Cache JSON thô
│   ├── processed/                # JSON chuẩn hóa
│   ├── sql/                      # Tệp .sql sinh ra
│   └── reports/                  # Báo cáo sau import
├── config.py                     # Quản lý cấu hình (Keycloak subjects mặc định, fees, timeouts)
├── cli.py                        # Điểm vào dòng lệnh (Click / Argparse)
├── requirements.txt              # httpx, pydantic, psycopg2-binary, click
└── README.md                     # Hướng dẫn chi tiết sử dụng
```

---

## 4. Đặc tả Chi tiết từng Module

### 4.1. Module Crawlers (`crawlers/`)
- **`BaseScraper` interface:**
  ```python
  class BaseScraper(ABC):
      @abstractmethod
      def list_events(self, limit: int = 10) -> list[RawEventSummary]: ...
      @abstractmethod
      def get_event_detail(self, event_id: str) -> RawEventDetail: ...
  ```
- **Xử lý mạng:**
  - Sử dụng thư viện `httpx.Client` với headers giả lập trình duyệt chuẩn.
  - Delay ngẫu nhiên từ 1.0 đến 2.0 giây giữa mỗi request.
  - Tự động lưu kết quả chi tiết vào `storage/raw/<source>/<event_id>.json`.

### 4.2. Module Chuẩn hóa (`transformers/normalizer.py` & `snapshot_builder.py`)
- **Chuyển đổi thời gian (`--shift-future`):**
  - Nếu ngày tổ chức sự kiện gốc đã qua, tịnh tiến mốc `sale_starts_at` về thời điểm hiện tại (`now()`), `sale_ends_at` sau 14 ngày, `starts_at` sau 21 ngày, `ends_at` sau 22 ngày.
  - Đảm bảo bất biến `checkin_starts_at = starts_at` và `checkin_ends_at = ends_at`.
- **Làm sạch chuỗi & Đơn vị tiền tệ:**
  - Chuẩn hóa giá vé về số nguyên dương (`numeric(19,0)`), đơn vị tiền tệ cố định `'VND'`.
  - Loại bỏ các thẻ HTML độc hại hoặc định dạng rác trong trường mô tả.
  - Sinh mã màu ngẫu nhiên nhưng hài hòa (`#1677FF`, `#52C41A`, `#FAAD14`, `#F5222D`...) cho từng hạng vé.
- **Tạo Snapshot JSONB:**
  - `configuration_snapshot` trong `tbl_event_sales_snapshots` phải chứa đầy đủ `schemaVersion: 1`, `eventId`, `organizerSubject`, `ticketTypes`, `sectors: []`, `seats: []`, `promotions: []` đúng chuẩn nghiệp vụ của `booking-service`.

### 4.3. Module Giả lập Đơn hàng (`generators/order_generator.py`)
- **Phân bổ trạng thái:**
  - 80% đơn `COMPLETED`: Có `accepted_charge_id`, `frozen_purchase_snapshot`, trạng thái vé `ISSUED` trong `ticket_db`.
  - 10% đơn `PENDING_PAYMENT`: Đang trong thời hạn thanh toán (chưa có charge và vé).
  - 10% đơn `EXPIRED`: Quá hạn thanh toán để tạo dữ liệu đối soát thực tế.
- **Tính toàn vẹn tồn kho:**
  - Tổng số vé của các đơn hàng thành công và đang giữ chỗ sẽ được trừ tương ứng vào tồn kho khả dụng để đảm bảo tính nhất quán của `tbl_ticket_type_inventory`.

### 4.4. Module Xuất SQL (`exporters/sql_builder.py`)
Tệp SQL sinh ra có cấu trúc phân khối rõ ràng:
```sql
\set ON_ERROR_STOP on

-- Khối 1: Event DB
\connect event_db
BEGIN;
SET LOCAL ROLE event_owner;
SET LOCAL search_path = event_schema, pg_catalog;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

INSERT INTO tbl_categories (id, name) VALUES (...) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_venues (id, name, address) VALUES (...) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_events (...) VALUES (...) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_ticket_types (...) VALUES (...) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_event_images (...) VALUES (...) ON CONFLICT (id) DO NOTHING;
COMMIT;

-- Khối 2: Booking DB
\connect booking_db
BEGIN;
SET LOCAL ROLE booking_owner;
SET LOCAL search_path = booking_schema, pg_catalog;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

INSERT INTO tbl_event_sales_snapshots (...) VALUES (...) ON CONFLICT (event_id) DO NOTHING;
INSERT INTO tbl_ticket_type_inventory (...) VALUES (...) ON CONFLICT (ticket_type_id) DO NOTHING;
INSERT INTO tbl_orders (...) VALUES (...) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_order_items (...) VALUES (...) ON CONFLICT (id) DO NOTHING;
COMMIT;

-- Khối 3: Payment DB
\connect payment_db
BEGIN;
SET LOCAL ROLE payment_owner;
SET LOCAL search_path = payment_schema, pg_catalog;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

INSERT INTO tbl_event_finance_snapshots (...) VALUES (...) ON CONFLICT (event_id) DO NOTHING;
INSERT INTO tbl_payment_attempts (...) VALUES (...) ON CONFLICT (id) DO NOTHING;
COMMIT;

-- Khối 4: Ticket DB
\connect ticket_db
BEGIN;
SET LOCAL ROLE ticket_owner;
SET LOCAL search_path = ticket_schema, pg_catalog;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

INSERT INTO tbl_event_access_snapshots (...) VALUES (...) ON CONFLICT (event_id) DO NOTHING;
INSERT INTO tbl_tickets (...) VALUES (...) ON CONFLICT (id) DO NOTHING;
COMMIT;
```

### 4.5. Module Import Server 119 & Kiểm tra Tính toàn vẹn (`importers/`)
- Sử dụng lệnh SCP để chuyển file script lên `/tmp/ft_import_<timestamp>.sql` trên server 119.
- Chạy lệnh qua SSH: `ssh ft119 "psql -U postgres -v ON_ERROR_STOP=1 -f /tmp/ft_import_<timestamp>.sql"`.
- Dọn dẹp tệp tạm trên server sau khi hoàn tất.
- **Xác thực tự động (Post-Verification):**
  - Chạy script kiểm tra đếm số dòng vừa nạp:
    ```sql
    SELECT count(*) FROM tbl_events WHERE id IN (<event_ids>);
    SELECT count(*) FROM tbl_event_sales_snapshots WHERE event_id IN (<event_ids>);
    SELECT count(*) FROM tbl_orders WHERE event_id IN (<event_ids>);
    SELECT count(*) FROM tbl_tickets WHERE event_id IN (<event_ids>);
    ```
  - Xuất báo cáo tóm tắt ra `storage/reports/<timestamp>-summary.json`.

---

## 5. Đặc tả Giao diện Dòng lệnh (CLI Specification)

Công cụ `cli.py` hỗ trợ các subcommand linh hoạt:

| Lệnh | Ý nghĩa | Các cờ tham số chính |
| :--- | :--- | :--- |
| `crawl` | Thu thập dữ liệu thô từ các website vé | `--source [ticketbox\|ticketgo\|all]`, `--limit <int>` |
| `format` | Chuẩn hóa dữ liệu từ cache raw sang model FlashTicket | `--shift-future`, `--organizer-id <uuid>` |
| `generate-orders` | Giả lập giao dịch và đơn đặt vé cho sự kiện | `--orders-per-event <int>`, `--completed-ratio <float>` |
| `export-sql` | Xuất dữ liệu đã chuẩn hóa và đơn hàng thành file .sql | `--output <filepath>` |
| `import-119` | Nạp file .sql lên server 119 | `--file <filepath>`, `--dry-run`, `--apply` |
| `run-all` | Thực hiện trọn gói toàn bộ pipeline từ A đến Z | `--sources ticketbox,ticketgo`, `--limit 10`, `--orders 5`, `--apply` |

---

## 6. Xử lý Lỗi & Bảo vệ Database

1. **Kháng lỗi Crawl:** Sử dụng khối `try...except` bọc quanh từng sự kiện; sự kiện bị lỗi định dạng từ nguồn sẽ được ghi vào log lỗi và bỏ qua, không làm dừng toàn bộ mẻ crawl.
2. **Ngăn chặn SQL Injection & Cú pháp lỗi:** Mọi chuỗi ký tự đưa vào câu lệnh SQL đều được escape đúng chuẩn PostgreSQL (sử dụng format dollar-quoting `$$...$$` hoặc escape nháy đơn `''`).
3. **Bảo vệ Hệ thống Đang chạy trên 119:**
   - Đặt `lock_timeout = '5s'` để tránh chờ lock quá lâu gây nghẽn các transaction nghiệp vụ khác.
   - Bọc transaction theo từng database độc lập để đảm bảo nếu một DB bị lỗi thì dữ liệu của DB đó tự động rollback nguyên trạng.

---

## 7. Tiêu chí Nghiệm thu (Acceptance Criteria)

- [ ] **AC-1:** Module crawler lấy thành công danh mục sự kiện thực tế từ ít nhất 1 nguồn (Ticketbox/Ticketgo) với đầy đủ thông tin: tên, ảnh, ngày giờ, địa điểm, các hạng vé và giá vé.
- [ ] **AC-2:** Module chuẩn hóa sinh được file SQL tuân thủ 100% các constraint của 4 schema (`event_schema`, `booking_schema`, `payment_schema`, `ticket_schema`).
- [ ] **AC-3:** Module giả lập sinh thành công các đơn hàng `tbl_orders`, `tbl_order_items`, `tbl_tickets` với dữ liệu snapshot và tổng tiền nhất quán.
- [ ] **AC-4:** Script nạp vào server 119 chạy thành công qua SSH, không gặp lỗi permission, role hay transaction lock.
- [ ] **AC-5:** Dữ liệu sau khi nạp hiển thị được trên API `GET /api/events` của FlashTicket và frontend có thể render sự kiện kèm danh sách vé mở bán.
