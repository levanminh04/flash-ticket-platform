# Kịch bản chất lượng

Thư mục này giữ `B9` (bộ kịch bản chất lượng) và, khi tới gate của nó, `B10` (bảng ưu tiên và danh sách ASR). Đường dẫn canonical của cả hai được khai sẵn dưới đây. `B10` **đã được tạo** ngày 2026-08-27 ở mức `DRAFT`, sau khi ba điều kiện của nó đủ: tiêu chí xếp hạng (`GOV-034`), nghĩa của mức Thấp (`GOV-035`), tập kịch bản đầu vào (`GOV-036`). Bản hiện hành là **`B10-v0.7`**, trạng thái **`APPROVED`** ngày 2026-08-29 (`GOV-050`), duyệt sau `B9-v0.7` cùng ngày. Trước đó `GOV-047` từng trả cả hai về chờ duyệt lại vì bị sửa nội dung sau khi duyệt; vòng duyệt ngày 2026-08-29 chữa đúng chỗ đó. **Cùng lúc `B10` được duyệt, `GOV-037` đóng và 18 mức ưu tiên thôi là `CANDIDATE`** — bảng ưu tiên nay là đầu vào đã chốt cho bước kiến trúc. *(Dòng này trước 2026-08-29 còn ghi "`B10-v0.5`, vẫn `DRAFT`", tự mâu thuẫn với khối ngay dưới nó.)* `v0.4` sửa kết quả một vòng rà toàn văn: trả `GOV-035` về đúng phạm vi *mức Thấp*, khai lại đầu vào cho đủ, sửa ba ASR lệch nghĩa nguồn, mở sổ `OPEN` từ 6 lên 13 dòng. `v0.5` áp tám quyết định của Lê Văn Minh: ba dòng đổi mức (**13 Cao / 2 Trung bình / 3 Thấp**), thêm `ASR-14` và `ASR-15` (tổng **15 ASR**), chốt mã tương quan là ràng buộc bắt buộc, đóng năm dòng `OPEN`.

> ✅ **Chuỗi hiện hành được duyệt ngày 2026-08-29, đúng thứ tự `B8-v0.12` → `B9-v0.7` → `B10-v0.7`** (`GOV-050`). **Lịch sử:** lượt duyệt ngày 2026-08-28 theo thứ tự `B8-v0.12` → `B9-v0.6` → `B10-v0.5` (`GOV-043`) **đã bị `GOV-047` rút** vì `B9` và `B10` bị sửa sau khi duyệt; vòng 2026-08-29 chữa đúng chỗ đó, **không kịch bản và không ASR nào đổi**. Trong ngày, `RES-042` sửa `NFR-06` nên `B8` từng quay lại `REVIEW_READY` và kéo `B9` theo; `GOV-042` sửa thêm ô *Nguồn kích thích* và ô *Kiểm chứng* của `QS-04`, `QS-05`. Cả ba được duyệt lại đúng chiều, không duyệt ngược.
>
> **Bàn giao đã thi hành:** `B10` đã điền ô `Mức ưu tiên` vào cả 18 bản ghi `C2` ở `B9-v0.7` — **13 Cao · 2 Trung bình · 3 Thấp** — đúng như §1 dưới đây chốt. Ô `ADR liên quan` vẫn để trống; `B11` mới điền.

| Mã | Đường dẫn canonical |
|---|---|
| B9 | `B9-quality-scenarios.md` |
| B10 | `B10-quality-priorities-and-asrs.md` |

---

## 1. Mẫu nào có thẩm quyền

`B9` có **hai** mẫu trong bộ tài liệu, và chúng không giống nhau. Mục này chốt cách dùng để không lặp lại lỗi đã xảy ra ở B6 — nơi 12 bản đặc tả bị viết sai mẫu vì chỉ đọc phiếu Tầng B mà không mở mẫu Tầng C.

| Nguồn | Vai trò |
|---|---|
| **Mẫu `C2`** tại `docs/tang-c-quy-uoc-trinh-bay.md` Phần 4 | **Mẫu bản ghi có thẩm quyền.** Tầng C sở hữu mẫu trình bày; đây là bộ trường mà một kịch bản phải có |
| **Phiếu B9** tại `docs/tang-b-quy-trinh-ky-thuat.md` Nhóm 3 | Mô tả **phương pháp** và phép thử: viết kịch bản thế nào, khi nào dùng số, khi nào không |

Bộ trường đầy đủ theo `C2`, gồm bốn trường mà phiếu Tầng B không liệt kê:

```
QS-__ | Tên: ______________________________________
Thuộc tính chất lượng: ______________________________
──────────────────────────────────────────────────────
Nguồn kích thích : ___________________________________
Kích thích       : ___________________________________
Tạo tác          : ___________________________________
Môi trường       : ___________________________________
Phản ứng         : ___________________________________
Độ đo phản ứng   : (1) bất biến  : ____________________
                   (2) ngưỡng    : ____________________
──────────────────────────────────────────────────────
Mức ưu tiên      : ___________  Lý do: __________________
ADR liên quan    : ___________  Kiểm chứng/Test/EXP: ____
```

**`Mức ưu tiên` do B9 tạo ô, B10 điền.** Tầng B giao việc xếp ưu tiên cho `B10`; `C2` chỉ yêu cầu trường tồn tại trong bản ghi. Ô `ADR liên quan` cũng để trống ở B9 vì ADR chỉ có ở B11.

**Độ đo tách hai dòng.** Dòng *(1) bất biến* là ràng buộc đúng/sai, không phụ thuộc đo đạc. Dòng *(2) ngưỡng* là con số, chỉ chốt sau một vòng đo thử. Không trộn hai loại vào một dòng.

**Không ép mọi tiêu chí thành số.** Đây là phép thử của phiếu B9: *"Không ép mọi tiêu chí thành số nếu số đó không có ý nghĩa."* Chỗ nào con số chưa có nghĩa thì ghi `OPEN` kèm lý do và điều kiện để điền được.

Mã kịch bản theo Tầng C §3.3: `QS-<2 chữ số>`. Mã thí nghiệm: `EXP-<2 chữ số>`.

---

## 2. Đầu vào của B9

Phiếu Tầng B khai đầu vào là `B8` và `A3`. **Danh sách đó thiếu `B7`.** Ba nguồn cho thấy `B7` là đầu vào thật:

- `B4` §10 — bảng bất biến và hotspot ghi cột *"Gate xử lý tiếp"* trỏ đích danh về `B9` cho bảy bất biến và cả bốn hotspot
- `B7` §4.4 — yêu cầu `B9`/`B10` **tách hai phép thử** tranh chấp check-in
- `B7` §5 — tám trên mười một bất biến có cột *"Phần còn vượt aggregate"* trỏ về `B9`–`B11`

Đầu vào đúng của B9: **`B7` + `B8` + `A3`**, cộng `B2` làm ràng buộc từ vựng.

---

## 3. Phạm vi của B9 lấy từ `B4` §10

Bảng `B4` §10 là **thẩm quyền** về việc bất biến nào đi tới B9. Không tự mở rộng và không tự thu hẹp.

| Vào B9 | Không vào B9 |
|---|---|
| `INV-01`, `INV-04`, `INV-05`, `INV-06`, `INV-07`, `INV-08`, `INV-09` | `INV-02`, `INV-03` — chỉ `B7` |
| `HOT-01`, `HOT-02`, `HOT-03`, `HOT-04` | `INV-10`, `INV-11` — `B7`, rồi `B12` |

---

## 4. Ranh giới với bộ tài liệu nghiên cứu

`B9` là tạo tác `FORMATION` trong dải `B2`–`B10`. Theo Tầng B §3.3, nó chỉ được hình thành kết luận từ *"yêu cầu, khảo sát công khai có giới hạn, thuật ngữ, quy trình, sự kiện, bất biến, aggregate, ASR và ràng buộc **đã được xác nhận**"*.

**Nguồn sinh nội dung của B9 là một danh sách đóng:** `B7`, `B8`, `A3`, `B2`, và các quyết định `USER_CONFIRMED` trong sổ. **Không** sinh kịch bản từ `docs/research-rca/` — kể cả từ `R0`.

`RES-015` yêu cầu mọi gate `B9`–`B16` **đối chiếu** `R0` §3. Việc đó được thực hiện bằng **một mục phụ lục** ở cuối `B9`, mọi dòng ở `CANDIDATE`/`OPEN`, và không ràng buộc nào của `R0` được sinh ra hoặc sửa đổi một kịch bản. Phép thử bốn câu trước khi đóng gate nằm tại `docs/project/lien-ket-rca.md` §2.2.

**Từ vựng.** Các kịch bản về dấu vết vận hành viết theo mục từ còn lại của `B2` §6 — **Dấu vết vận hành**, **Log có cấu trúc**, **Mã tương quan**, **Sự cố** và bốn lớp lỗi — những khái niệm tồn tại độc lập với thiết kế của bất kỳ thành phần nào tiêu thụ chúng. Mục từ **Context chẩn đoán** đã bị gỡ ngày 2026-08-27 (`RES-034`) vì nó là từ vựng của thiết kế trợ lý cũ; `QS-14` nay nói thẳng thứ bị lọc là *dấu vết đã chọn và liên kết*. `B9` **không** mô tả phương pháp chẩn đoán — không đồ thị, không xếp hạng, không lớp giải thích.

---

## 5. Ngưỡng

Ngưỡng là **đầu ra** của `B9`/`B10`, không phải điều kiện để bắt đầu. `A3-OPEN-01` và `B8-OPEN-01` chính là phần này.
