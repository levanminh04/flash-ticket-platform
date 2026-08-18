# Phân vai và quyền quyết định

## Thành viên

| Thành viên | Mã sinh viên | Trạng thái phân vai |
|---|---|---|
| Lê Văn Minh | B22DCCN533 | Đã xác nhận: kiến trúc tổng thể, backend, phân tích/thiết kế trợ lý chẩn đoán sự cố |
| Phạm Văn Tuyến | B22DCCN773 | Đã xác nhận: phụ trách trọn gói mobile; có thể hỗ trợ một số chức năng backend sau khi chia workload |
| Phạm Long Nhật | B22DCCN581 | Đã xác nhận: phụ trách trọn gói frontend ReactJS; hỗ trợ trợ lý chẩn đoán và phân tích nghiệp vụ |

## Ma trận phân công baseline

Hai cột `Người quyết định thiết kế` và `Người hiện thực hóa` phải tách biệt. Một người có thể nằm ở cả hai cột, nhưng không được suy ra trách nhiệm thiết kế chỉ từ số dòng code.

| Phạm vi | Người quyết định thiết kế | Người hiện thực hóa | Người rà soát | Trạng thái |
|---|---|---|---|---|
| Kiến trúc tổng thể và ranh giới service | Lê Văn Minh | Lê Văn Minh điều phối tích hợp | Cả nhóm | Minh đã xác nhận; ranh giới cụ thể chờ B11 |
| Backend, giao dịch đặt vé và kiểm thử độ tin cậy | Lê Văn Minh | Lê Văn Minh chính; Phạm Văn Tuyến có thể hỗ trợ phần sẽ chốt sau | Cả nhóm | Minh đã xác nhận; phần hỗ trợ của Tuyến còn `OPEN` |
| Frontend web và trải nghiệm buyer/organizer | Phạm Long Nhật trong phạm vi FE; Lê Văn Minh duyệt hợp đồng/kiến trúc chung | Phạm Long Nhật | Lê Văn Minh và Phạm Văn Tuyến | Đã xác nhận |
| Mobile và check-in trực tuyến | Phạm Văn Tuyến trong phạm vi mobile; Lê Văn Minh duyệt hợp đồng/kiến trúc chung | Phạm Văn Tuyến | Lê Văn Minh và Phạm Long Nhật | Đã xác nhận |
| Trợ lý chẩn đoán sự cố | Lê Văn Minh | Lê Văn Minh chính; Phạm Long Nhật hỗ trợ | Cả nhóm | Đã xác nhận |
| Phân tích nghiệp vụ | Lê Văn Minh | Lê Văn Minh chính; Phạm Long Nhật hỗ trợ | Cả nhóm | Đã xác nhận |
| Tích hợp, thí nghiệm và báo cáo | Cả nhóm | Cả nhóm | Cả nhóm | Đã định hướng |

“Trọn gói” nghĩa là chịu trách nhiệm chính từ phân tích giao diện, hiện thực đến kiểm thử phần client tương ứng, trong giới hạn hợp đồng backend và kiến trúc chung đã duyệt. Không dùng bảng này để tự khai đóng góp. Ba thành viên đã xác nhận baseline phân vai; phạm vi backend cụ thể Tuyến sẽ hỗ trợ vẫn là `OPEN` cho đến khi nhóm chia workload. Mọi thay đổi sau đó phải được nhóm ghi lại.
