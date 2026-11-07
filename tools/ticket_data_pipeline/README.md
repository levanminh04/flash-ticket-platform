# FlashTicket Data Pipeline Tool

Công cụ tự động hóa quá trình Crawl dữ liệu sự kiện từ các nền tảng bán vé (Ticketbox, Ticketgo...), chuẩn hóa định dạng theo mô hình phân tán của FlashTicket, tạo dữ liệu đơn hàng giả lập và nạp vào database trên server 119 (`3.106.109.119`).

## 1. Cài đặt

```powershell
cd tools/ticket_data_pipeline
pip install -r requirements.txt
```

## 2. Hướng dẫn Sử dụng CLI

### Chạy toàn bộ luồng (Run All):
```powershell
# Chế độ Dry-Run (chỉ sinh file SQL để kiểm tra trước):
python -m tools.ticket_data_pipeline.cli run-all --sources ticketbox --limit 5 --orders-per-event 5

# Chế độ Apply (nạp thẳng vào database server 119 qua SSH Host ft119):
python -m tools.ticket_data_pipeline.cli run-all --sources ticketbox --limit 5 --orders-per-event 5 --apply
```

### Chạy từng lệnh riêng lẻ:
```powershell
# 1. Crawl dữ liệu thô:
python -m tools.ticket_data_pipeline.cli crawl --source ticketbox --limit 10

# 2. Chuẩn hóa dữ liệu sang domain FlashTicket:
python -m tools.ticket_data_pipeline.cli format --shift-future

# 3. Nạp file SQL sẵn có lên server 119:
python -m tools.ticket_data_pipeline.cli import-119 --file storage/sql/import_seed119_xxxx.sql --apply
```

## 3. Chạy Kiểm thử (Unit Tests)

```powershell
python -m pytest tools/ticket_data_pipeline/tests/ -v
```

