# Báo cáo hai tuần — Chẩn đoán nguyên nhân gốc sự cố giao dịch trực tuyến bằng đồ thị phụ thuộc

- **Nhóm:** Lê Văn Minh (B22DCCN533) · Phạm Văn Tuyến (B22DCCN773) · Phạm Long Nhật (B22DCCN581)
- **Giảng viên hướng dẫn:** Cô Liên
- **Mốc:** `DH-MOC` — báo cáo sau hai tuần kể từ định hướng ngày 2026-08-22
- **Trạng thái:** `DRAFT`, chưa nộp
- **Đầu vào định hướng:** [`docs/evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`](../evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md) — nguyên văn

> **Bố cục.** Báo cáo đi đúng sáu mục cô liệt kê, theo đúng thứ tự trong thư. Mã `DH-*` ở mỗi tiêu đề trỏ về câu nguyên văn tương ứng.
>
> **Cam kết về cách viết.** Cô nhận xét bản trước *"còn khá mơ hồ, phần tổng hợp chưa thể hiện rõ liên kết logic"* (`DH-PB`). Bản này áp năm quy tắc: mỗi khẳng định có một nguồn hoặc một con số; mỗi mục mở đầu bằng câu nối với mục trước; phân biệt rõ phần nhóm tự làm với phần trích từ bài báo; ưu tiên bảng của chính hệ thống nhóm; không dùng cụm định tính không kiểm chứng được.

---

## Ký hiệu phân biệt nguồn

| Nhãn | Nghĩa |
|---|---|
| **[N]** | Nhóm tự phân tích hoặc tự chạy |
| **[T]** | Trích từ tài liệu đã công bố, có ghi mã nguồn |
| **[?]** | Chưa xác minh, không được đưa vào bản nộp cuối nếu chưa kiểm |

---

# Mục 1 — Mô hình đồ thị phụ thuộc của hệ thống giao dịch trực tuyến

> `DH-MT1`: *"Xây dựng mô hình đồ thị phụ thuộc cho hệ thống giao dịch trực tuyến (các dịch vụ đặt vé, thanh toán, xác thực, cơ sở dữ liệu, API đối tác)."*

## 1.1 Hệ thống được mô hình hóa

Nhóm đã hoàn tất phân tích nghiệp vụ cho hệ đặt vé sự kiện trực tuyến của mình trước khi nhận định hướng: từ điển miền, quy trình nghiệp vụ, bản đồ sự kiện miền, bản đồ bounded context, mô hình aggregate và bất biến, đặc tả use case và bảng yêu cầu. Bảy tài liệu này được duyệt lần đầu ngày 2026-08-22. **[N]**

Sau khi nhận định hướng, cả chuỗi được **rà lại và duyệt lại đúng thứ tự** ngày 2026-08-27 để gỡ phần thiết kế cũ không còn thuộc phạm vi, rồi bổ sung tiếp **bộ kịch bản chất lượng** và **bảng ưu tiên kèm danh sách yêu cầu có ý nghĩa kiến trúc**, duyệt ngày 2026-08-28. **Nội dung nghiệp vụ bán vé không đổi một chữ qua cả hai vòng.** **[N]**

Năm nhóm dịch vụ cô liệt kê ánh xạ vào bản đồ đó như sau:

| Cô nêu | Bounded context tương ứng trong phân tích của nhóm |
|---|---|
| dịch vụ **đặt vé** | `BC-CAND-01` Vòng đời sự kiện và cấu hình bán · `BC-CAND-02` Mua vé và cam kết nguồn cung · `BC-CAND-04` Quyền tham dự và kiểm soát vào cửa · `BC-CAND-06` Giao nhận thông tin vé |
| **thanh toán** | `BC-CAND-03` Thanh toán và hoàn tiền · `BC-CAND-05` Đối soát và chi trả |
| **xác thực** | `BC-CAND-07` Hồ sơ tài khoản và quyền nghiệp vụ |
| **cơ sở dữ liệu** | Chưa có trong bản đồ context — bổ sung ở §1.4 |
| **API đối tác** | Chưa có trong bản đồ context — bổ sung ở §1.4 |

**[N]** Ánh xạ này không phải sự trùng hợp: bản đồ context được suy ra từ dòng sự kiện nghiệp vụ của chính hệ đặt vé, nên nó phủ đúng các nhóm dịch vụ mà một hệ giao dịch trực tuyến phải có.

## 1.2 Vì sao không dùng thẳng bản đồ context làm đồ thị phụ thuộc

Đây là điểm phải làm rõ trước, vì bỏ qua nó thì toàn bộ mục 1 sai nền.

Bản đồ bounded context của nhóm ghi một giới hạn tường minh: *"Mũi tên không phải API, topic, sự kiện tích hợp, quyền sở hữu dữ liệu hay hướng gọi đồng bộ"*, và *"Chiều mũi tên chỉ nói ai cần biết gì, không nói ai gọi ai."* **[N]**

Nghĩa là bản đồ đó là **đồ thị ngữ nghĩa**, còn chẩn đoán nguyên nhân gốc cần **đồ thị phụ thuộc vận hành**. Hai đồ thị khác nhau ở ba điểm:

| | Đồ thị ngữ nghĩa (đã có) | Đồ thị phụ thuộc vận hành (cần dựng) |
|---|---|---|
| Một cạnh nghĩa là gì | Hai phần cần thống nhất ý nghĩa về một khái niệm nghiệp vụ | Lúc chạy, A phụ thuộc B: A gọi B, hoặc dữ liệu chảy từ A sang B |
| Có nút hạ tầng không | Không — cố ý không mô hình hóa | **Có** — cơ sở dữ liệu và hệ ngoài, đúng danh sách cô nêu |
| Dùng để làm gì | Lập ranh giới mô hình miền | Lan truyền tín hiệu bất thường và xếp hạng nghi phạm |

Do đó mục 1 **không** chép lại bản đồ cũ. Nó thực hiện một phép chuyển đổi có quy tắc, trình bày ở §1.3.

## 1.3 Quy tắc chuyển đổi và bằng chứng cho từng cạnh

Bản đồ context của nhóm có **12 cạnh ngữ nghĩa**, và mỗi cạnh đã được truy về một dòng cụ thể trong bản đồ sự kiện miền. **[N]** Bảng dưới áp ba quy tắc lên từng cạnh:

- **Quy tắc 1** — Một cạnh ngữ nghĩa cho biết **một tương tác có thật tồn tại**. Nó là điều kiện cần của một cạnh vận hành.
- **Quy tắc 2** — Nó **không** cho biết cạnh vận hành đi theo chiều nào, vì "cần biết" và "gọi" là hai chiều có thể ngược nhau.
- **Quy tắc 3** — Nó **không** cho biết tương tác được hiện thực bằng lời gọi đồng bộ, sự kiện bất đồng bộ hay dữ liệu tham chiếu đã sao chép. Ba cách hiện thực cho ba dạng cạnh vận hành khác nhau, và có trường hợp **không sinh ra cạnh vận hành nào**.

| # | Cạnh ngữ nghĩa | Nội dung trao đổi | Bằng chứng | Cạnh vận hành suy ra |
|---|---|---|---|---|
| 1 | Tài khoản/quyền → 5 context nghiệp vụ | Định danh và điều kiện được phép phát lệnh | B4 §8.1 | **Có** — chiều chờ gate hợp đồng |
| 2 | Vòng đời sự kiện → Mua vé | Điều kiện bán, cấu hình thương mại, khuyến mãi | `A02`, `A07`, `A08` | **Có** |
| 3 | Vòng đời sự kiện → Kiểm soát vào cửa | Cửa sổ check-in; vé phải thuộc đúng sự kiện | `A12`, `A13`, `D01` | **Có thể không** — xem §1.5 |
| 4 | Vòng đời sự kiện → Đối soát | Sự kiện đã kết thúc; tỷ lệ phí nền tảng | `A13`, `C08`, `A04`, `C07` | **Có** |
| 5 | Vòng đời sự kiện → 4 context | Sự kiện bị hủy lan sang đơn, khoản thu, vé, sổ đối soát | `A11`, `B13`, `B14`, `C05`, `C07` | **Có** — dạng cạnh chờ gate hợp đồng |
| 6 | Mua vé → Thanh toán | Nghĩa vụ thanh toán của đơn còn hiệu lực | `B03`, `B08`, `B09`, `B10` | **Có** |
| 7 | Thanh toán → Mua vé | Thu hợp lệ làm tài nguyên đang giữ được chốt | `B05` | **Có** |
| 8 | Thanh toán → Kiểm soát vào cửa | Xác nhận thu cho phép phát hành vé | `B05`, `B06`, `C03` | **Có** |
| 9 | Thanh toán → Đối soát | Khoản thu và kết quả hoàn là đầu vào sổ cái | `C03`, `C07` | **Có** |
| 10 | Kiểm soát vào cửa → Thanh toán | Phát hành vé thất bại sau khi đã thu tiền | `B12` → `C01` | **Có** |
| 11 | Kiểm soát vào cửa → Mua vé | Phát hành thất bại trả lượt khuyến mãi và tồn kho | `B12` | **Có** |
| 12 | Kiểm soát vào cửa → Giao nhận vé | Vé đã phát hành cần gửi tới người mua | `B06` → `B07` | **Có** |
| 13 | Dấu vết nghiệp vụ → cơ chế chẩn đoán | Dấu vết đã khử nhạy cảm làm đầu vào chỉ đọc | B4 §9, dòng *"Dấu vết vận hành đã được tạo"* | **Không** — đây là cạnh quan sát, không phải phụ thuộc giao dịch |

**Kết quả [N]:** 12 cạnh ngữ nghĩa cho **11 cạnh có tương tác vận hành được xác nhận là tồn tại** và **1 cạnh còn phụ thuộc cách hiện thực** (số 3). Không cạnh nào được vẽ từ suy đoán.

**Dòng 13 trong bảng là một cạnh quan sát, không nằm trong 12 cạnh ngữ nghĩa đó [N].** Nó lấy nguồn từ dòng *"Dấu vết vận hành đã được tạo"* ở bản đồ sự kiện, không từ bản đồ ngữ cảnh — và nó **không** chuyển thành phụ thuộc giao dịch. Ghi ra để cho thấy quan hệ này đã được xét chứ không bị bỏ sót.

**Cột "cạnh vận hành suy ra" nói gì và không nói gì [N].** Nó trả lời đúng một câu: *lúc chạy, giữa hai nút này có tương tác không?* Nó **chưa** trả lời hai câu còn lại — **chiều** nào, và **dạng** nào (lời gọi đồng bộ, sự kiện bất đồng bộ, hay dữ liệu tham chiếu đã sao chép). Đó chính là điều Quy tắc 2 và Quy tắc 3 vừa nói, nên viết *"11 cạnh chắc chắn"* sẽ tự mâu thuẫn với hai quy tắc đứng ngay phía trên. Chiều và dạng của cả 11 cạnh được chốt ở gate thiết kế hợp đồng, và §3.2 cho biết vì sao chiều phải đúng: cơ chế lan truyền đi ngược chiều phụ thuộc, nên vẽ sai chiều là đẩy điểm nghi ngờ sang nhầm nút.

## 1.4 Hai lớp nút mà bản đồ ngữ nghĩa không có

Cô nêu **cơ sở dữ liệu** và **API đối tác** như hai nhóm dịch vụ riêng. Bản đồ context không chứa chúng vì nó cố ý không mô hình hóa hạ tầng. Đồ thị vận hành phải bổ sung. **[N]**

Vì sao bắt buộc phải có: **phần lớn sự cố hạ tầng biểu hiện ở đúng những nút này**. Một truy vấn chậm ở nút dữ liệu hoặc một API đối tác hết thời gian chờ sẽ lan lên các nút nghiệp vụ gọi nó. Nếu đồ thị chỉ có nút nghiệp vụ thì nguyên nhân thật **nằm ngoài tập ứng viên**, và không cơ chế xếp hạng nào tìm ra được.

### 1.4.1 Bốn tiêu chí để một thành phần trở thành nút ứng viên

**[N]** Không phải mọi thành phần trong hệ thống đều nên thành nút. Bốn tiêu chí, áp đồng thời:

| # | Tiêu chí | Nếu không đạt thì sao |
|---|---|---|
| 1 | **Quan sát được** — phát ra span riêng, hoặc có chỉ số tài nguyên quy được về riêng nó | Không có tín hiệu để chấm điểm |
| 2 | **Có thể là nguyên nhân** — sự cố của nó gây triệu chứng ở nơi khác | Không bao giờ là đáp án đúng |
| 3 | **Tách được tài nguyên** — tài nguyên của nó không lẫn với thành phần khác | Không phân định được nó với thành phần dùng chung |
| 4 | **Chèn lỗi được** — gây lỗi có kiểm soát lên nó để sinh ca đánh giá | Không sinh được ca lỗi có nhãn |

### 1.4.2 Danh sách nút ứng viên

**[N]** Áp bốn tiêu chí lên các thành phần hạ tầng và hệ ngoài của hệ thống:

| Lớp | Nút ứng viên | Lớp lỗi gắn vào |
|---|---|---|
| **Dữ liệu** | Cơ sở dữ liệu quan hệ | CPU, truy vấn chậm, cạn connection pool, IO đĩa, tranh khóa |
| **Dữ liệu** | Bộ nhớ đệm | Độ trễ, dồn ứ khi hết hạn hàng loạt, cạn kết nối |
| **Trung gian** | Hàng đợi thông điệp | Ứ đọng, tiêu thụ chậm, thông điệp chết, mất kết nối |
| **Trung gian** | Cổng vào API | Cạn thread pool, lỗi định tuyến, chặn theo hạn mức |
| **Hệ ngoài** | Cổng thanh toán | Hết thời gian chờ, phản hồi chậm, thông báo kết quả đến muộn |
| **Hệ ngoài** | Hệ quản lý danh tính | Độ trễ xác thực, mất khả dụng |
| **Máy chủ** | Hai máy triển khai | Tranh chấp CPU và bộ nhớ ở mức máy |
| **Điều phối** | Đăng ký và phát hiện dịch vụ | Phân giải dịch vụ thất bại — **cơ chế lan truyền khác các nút trên**, xem §1.4.3 |

**Cố ý loại khỏi tập ứng viên [N]:**

| Loại ra | Trượt tiêu chí | Lý do |
|---|---|---|
| Ngăn xếp thu thập và hiển thị dữ liệu quan sát | 2 | Đây là **nguồn sinh dữ liệu**, không phải nút được chẩn đoán. Đưa vào là lỗi khái niệm |
| Bộ sinh tải | 2 | Là **kích thích** của thí nghiệm, không phải thành phần của hệ |
| Client web và di động | 2 | Nơi **quan sát triệu chứng**, gần như không là nguyên nhân trong phạm vi này |
| Máy chủ cấu hình tập trung | 2 | Phụ thuộc lúc khởi động, hầu như không sinh sự cố lúc chạy |
| Lớp AI giải thích | 2 | **Tiêu thụ** đầu ra của cơ chế (`DH-MT4`), không nằm trên đường phụ thuộc giao dịch — cùng lý do cạnh số 13 ở §1.3 |
| Công cụ kiểm thử và triển khai | 1, 2 | Không thuộc đường chạy thật |

### 1.4.3 Hai điểm cần lưu ý khi dựng

**[N]**

**Hàng đợi vừa là nút, vừa là chỗ đồ thị hay bị thủng.** Ranh giới hàng đợi là đúng nơi ngữ cảnh dấu vết bị đứt nếu không cố ý giữ, vì bên tiêu thụ chạy sau và ở ngữ cảnh khác bên phát. Nếu để đứt thì **mọi cạnh bất đồng bộ biến mất khỏi đồ thị**. Đây là lý do ràng buộc về ngữ cảnh dấu vết trong phong bì thông điệp ở §2.5 được xếp ưu tiên cao nhất.

**Nút phát hiện dịch vụ lan truyền theo cơ chế khác.** Client thường lưu đệm kết quả phân giải, nên sự cố ở nút này biểu hiện **trễ và gián tiếp**, không đi qua span của từng request như các nút còn lại. Giữ nó trong đồ thị nhưng phải ghi rõ khác biệt này, nếu không cơ chế lan truyền sẽ đọc sai quan hệ nhân quả.

### 1.4.4 Một câu hỏi mở quyết định số nút dữ liệu

**[N]** Ràng buộc kiến trúc là mỗi năng lực sở hữu schema riêng. Nhưng nếu nhiều schema nằm chung **một** thực thể cơ sở dữ liệu thì chỉ số tài nguyên **không quy được về từng schema** — về mặt tài nguyên đó là một nút, dù về mặt logic là nhiều.

| Phương án | Số nút dữ liệu | Đánh đổi |
|---|---|---|
| Dùng chung một thực thể | 1 | Tiết kiệm bộ nhớ, nhưng mất khả năng phân định nguyên nhân giữa các schema |
| Mỗi năng lực một thực thể | tới 7 | Phân định tốt, nhưng tốn bộ nhớ trên hạ tầng đã công bố |

Đây là cùng một đánh đổi với việc gộp hay tách đơn vị triển khai, và phải được quyết **có ý thức** ở gate kiến trúc, không phải là hệ quả tình cờ của việc tiết kiệm tài nguyên.

## 1.5 Một ví dụ cho thấy vì sao không được chép thẳng

Cạnh số 3 — *Vòng đời sự kiện → Kiểm soát vào cửa* — minh họa đúng chỗ hai đồ thị tách nhau. **[N]**

Về mặt ngữ nghĩa, lúc quét mã vào cửa hệ thống **cần biết** cửa sổ check-in đã mở chưa và vé có thuộc đúng sự kiện đang quét không. Cạnh ngữ nghĩa tồn tại, có bằng chứng.

Về mặt vận hành có hai cách hiện thực:

| Cách hiện thực | Cạnh vận hành | Hệ quả cho chẩn đoán |
|---|---|---|
| Lúc check-in gọi sang context sự kiện hỏi cửa sổ thời gian | **Có cạnh** | Sự kiện chậm thì check-in chậm theo; cạnh này phải có trên đồ thị |
| Cửa sổ thời gian và định danh sự kiện được ghi vào vé ngay lúc phát hành | **Không có cạnh** | Check-in không phụ thuộc context sự kiện lúc chạy; vẽ cạnh này là vẽ sai |

Quyết định giữa hai cách thuộc gate thiết kế hợp đồng. **Trước khi có quyết định đó, cạnh số 3 phải được ghi là `OPEN`, không được vẽ chắc.** Đây là lý do §1.3 tách riêng cột "cạnh vận hành suy ra".

## 1.6 Đồ thị ở mức nào, và vì sao chưa vẽ ở mức service vật lý

Đồ thị được dựng ở **mức năng lực nghiệp vụ cộng hạ tầng**, không ở mức service vật lý. Ba lý do: **[N]**

1. Việc gộp hay tách các năng lực thành service triển khai chưa được quyết; quyết định đó thuộc gate kiến trúc và phải được hình thành độc lập từ yêu cầu chất lượng.
2. Danh sách cô nêu — *"đặt vé, thanh toán, xác thực, cơ sở dữ liệu, API đối tác"* — cũng ở mức năng lực, không ở mức triển khai. Nên vẽ ở mức này là bám đúng đề bài.
3. Ánh xạ từ nút năng lực sang nút service là một phép ánh xạ thẳng khi kiến trúc được chốt, không phải làm lại từ đầu.

## 1.7 Hệ quả lên phép đánh giá — số ứng viên

Số nút quyết định mức sàn của mọi độ đo xếp hạng, nên phải ước lượng ngay từ mục này. **[N]**

Với xếp hạng ngẫu nhiên trên `N` ứng viên và một nguyên nhân thật, kỳ vọng là `AC@1 = 1/N` và `Avg@5 = 3/N` **[T]**.

| Mức đánh giá | Ứng viên là gì | Số ước lượng | `AC@1` ngẫu nhiên | `Avg@5` ngẫu nhiên |
|---|---|---|---|---|
| Thô | Chỉ nút năng lực nghiệp vụ | 7 | 14,3 % | 42,9 % |
| Thô mở rộng | Nút năng lực + tám nút ở §1.4.2 (hai máy chủ tính hai nút) | ~16 | 6,3 % | 18,8 % |
| Mịn | Chỉ số của từng nút, khoảng 5–10 chỉ số mỗi nút | ~80–160 | 0,6–1,3 % | 1,9–3,8 % |

**Kết luận [N]:** ở mức thô, đoán bừa đã đạt `Avg@5` từ 18,8 % tới 42,9 %. Một phương pháp đạt 60 % ở mức đó **nói được rất ít nếu không kèm phép kiểm định** — chênh lệch so với sàn có thể nằm trong dao động ngẫu nhiên. Muốn kết luận, phải báo cáo số ca chạy và độ dao động, không chỉ so với kỳ vọng. Vì vậy mọi bảng kết quả trong báo cáo này bắt buộc có **cột đối chứng ngẫu nhiên**, và phép đánh giá chính đặt ở **mức chỉ số**.

**Hệ quả ngược lại, cũng phải nói rõ [N]:** vì mức sàn là `3/N`, thêm nút vào tập ứng viên làm sàn tụt và **điểm số trông đẹp hơn mà phương pháp không giỏi hơn**. Đây là lý do §1.4.2 loại một số thành phần khỏi tập ứng viên thay vì gom hết cho `N` lớn: tập ứng viên phải bằng đúng tập những thứ có thể là đáp án và chèn lỗi được.

---

# Mục 2 — Ánh xạ log giao dịch và trace lên đồ thị

> `DH-MT2`: *"Ánh xạ log giao dịch và dấu vết vận hành (trace) lên đồ thị để phát hiện bất thường."*

Mục 1 cho bộ khung tĩnh: nút và cạnh. Mục này gắn dữ liệu chạy thật lên khung đó.

## 2.1 Ba loại dữ liệu và vai của từng loại

| Loại | Trả lời câu gì | Gắn vào đâu trên đồ thị |
|---|---|---|
| **Trace** | Một giao dịch đã đi qua những nút nào, theo thứ tự nào, mất bao lâu ở mỗi chặng | **Cạnh** — mỗi quan hệ cha–con giữa hai span là một lần cạnh được đi qua |
| **Metric** | Từng nút đang tiêu thụ tài nguyên và phục vụ ở mức nào | **Nút** — một chuỗi thời gian cho mỗi cặp nút × loại tài nguyên |
| **Log giao dịch** | Chuyện gì đã xảy ra bên trong một nút, ở mức ngữ nghĩa nghiệp vụ | **Nút**, và qua mã tương quan thì gắn được vào **một giao dịch cụ thể** |

**Điểm mấu chốt [N]:** trace cho biết **cấu trúc**, metric cho biết **mức độ bất thường**, log cho biết **nội dung bất thường**.

Thiếu metric thì có đồ thị nhưng không biết nút nào bất thường. Thiếu log thì biết nút nào bất thường nhưng không giải thích được vì sao — và đó chính là đầu vào mà mục 4 cần.

**Thiếu trace thì mất gì [N].** Không phải mất đồ thị: mục 1 dựng đồ thị từ bằng chứng nghiệp vụ của chính nhóm mà không cần một span nào, và §3.1 cho thấy cả một họ phương pháp suy đồ thị ra từ thống kê trên metric. Thứ mất đi là **đồ thị được xác nhận bằng đường đi thật, cộng khả năng gắn một giao dịch cụ thể vào một đường đi cụ thể**. Hệ quả là mất họ phương pháp lan truyền trên đồ thị gọi thật, mất phần lớn giá trị của nhánh đa nguồn — và mất luôn cách kiểm chứng xem đồ thị dựng ở mục 1 có khớp thực tế chạy không.

## 2.2 Trường bắt buộc của một dòng log giao dịch

Để một dòng log gắn được vào đồ thị, nó phải mang đủ ba nhóm trường. **[N]**

| Nhóm | Trường | Vì sao bắt buộc |
|---|---|---|
| Định vị trên đồ thị | Định danh nút phát sinh log | Không có thì không biết log thuộc nút nào |
| Nối vào một giao dịch | Mã tương quan của giao dịch; định danh span đang thực thi | Không có thì không nối được log với trace, và mọi phân tích đa nguồn sụp |
| Nội dung nghiệp vụ | Loại nghiệp vụ; kết quả; mã lỗi nếu có | Đây là phần phân biệt log giao dịch với log kỹ thuật thuần |

Cộng thêm hai ràng buộc về dạng: dấu thời gian theo múi giờ chuẩn có phần mili giây, và **một định danh nút duy nhất dùng nguyên văn ở cả ba nguồn** — trong thuộc tính của trace, trong trường của log, và trong nhãn của metric. Nếu ba nguồn gọi cùng một nút bằng ba tên khác nhau thì không ghép được, và toàn bộ nhánh đa nguồn mất nền.

## 2.3 Quy tắc ánh xạ

**[N]** Ba quy tắc, viết ở dạng có thể cài đặt được:

1. **Span → cạnh.** Một span có span cha thuộc nút khác sinh ra một lần đi qua cạnh *(nút cha → nút con)*. Trọng số cạnh là số lần đi qua trong cửa sổ quan sát; độ trễ của cạnh là phân phối thời gian của các span con đó.
2. **Metric → nút.** Mỗi chỉ số có tên dạng *(định danh nút, loại tài nguyên)* và trở thành một chuỗi thời gian gắn vào nút. Đây cũng là dạng nhãn mà các bộ dữ liệu chuẩn dùng **[T]**.
3. **Log → nút và giao dịch.** Một dòng log gắn vào nút phát sinh nó; qua mã tương quan nó gắn tiếp vào giao dịch cụ thể, tức gắn được vào **một đường đi trên đồ thị** chứ không chỉ một nút.

## 2.4 Phát hiện bất thường trên khung đó

**[N]** Ba loại tín hiệu bất thường, tương ứng ba loại dữ liệu:

| Tín hiệu | Nguồn | Cách phát hiện |
|---|---|---|
| Nút bất thường | Metric | Phát hiện điểm đổi trên chuỗi thời gian của nút |
| Cạnh bất thường | Trace | Độ trễ hoặc tỷ lệ lỗi của cạnh lệch khỏi phân phối nền |
| Bằng chứng nội dung | Log | Mật độ dòng lỗi hoặc mẫu log hiếm xuất hiện trong cửa sổ sự cố |

Ba tín hiệu này là đầu vào của cơ chế xếp hạng ở mục 3.

## 2.5 Điều kiện để phần này chạy được trên hệ thống của nhóm

**[N]** Mục 2 hiện ở mức đặc tả. Để chạy trên hệ thống thật, nhóm đã ghi thành ràng buộc bắt buộc lên các gate thiết kế còn lại — quan trọng nhất là ba điều:

1. **Ngữ cảnh dấu vết phải là trường bắt buộc trong phong bì thông điệp**, cho cả lời gọi đồng bộ lẫn sự kiện bất đồng bộ. Một chặng làm mất ngữ cảnh là một lỗ trên đồ thị.
2. **Nếu kiến trúc có bước chuyển xử lý bất đồng bộ, thông tin nối dấu vết phải đi qua được bước đó.** Đây là câu điều kiện có chủ đích: nó không giả định mẫu kiến trúc nào đã được chọn, vì việc chọn thuộc gate kiến trúc. Bước chuyển bất đồng bộ chạy sau và ở ngữ cảnh khác, nên nếu không mang theo thông tin nối thì dấu vết đứt đúng chỗ quan trọng nhất.
3. **Một định danh nút duy nhất** dùng chung ba nguồn, như §2.2.

Danh sách đầy đủ mười hai ràng buộc và gate chịu trách nhiệm ghi tại hồ sơ nội bộ của nhóm. Đây là bước phòng ngừa có chủ đích: các ràng buộc này rẻ khi thiết kế đúng từ đầu và rất đắt khi phải sửa sau.

---

# Mục 3 — Cơ chế lan truyền và xếp hạng nguyên nhân gốc

> `DH-MT3`: *"Đề xuất cơ chế lan truyền và xếp hạng nguyên nhân gốc dựa trên bằng chứng log/trace."*

Mục 2 cho các tín hiệu bất thường đã gắn lên nút và cạnh. Mục này biến chúng thành một danh sách nghi phạm có thứ tự.

## 3.1 Bốn họ phương pháp hiện có, và họ nào thật sự dùng đồ thị

Nhóm đã khảo sát các phương pháp được dùng làm đối chứng trong tài liệu chuẩn. **[T]** Phân họ theo **cơ chế lõi** và **nguồn dữ liệu**, không theo năm công bố:

| Họ | Dùng đồ thị? | Cơ chế lõi | Đầu vào |
|---|---|---|---|
| Thống kê thuần | **Không** | Phát hiện điểm đổi trên chuỗi thời gian, rồi kiểm định phi tham số để xếp hạng | metric |
| Suy luận nhân quả | Có, nhưng đồ thị **suy ra từ thống kê**, không lấy từ trace | Kiểm định độc lập có điều kiện để tìm cấu trúc nhân quả | metric |
| **Đồ thị phụ thuộc từ trace** | **Có — lấy trực tiếp từ trace** | Lan truyền trên đồ thị gọi thật, kết hợp phân tích phổ | trace |
| Trace nhưng không lan truyền | Dùng đường đi, không lan truyền | Đếm: nút nào nhiều giao dịch lỗi và ít giao dịch bình thường đi qua thì khả nghi hơn | trace |

**Nhận định [T]:** trong tập phương pháp đối chứng của tài liệu chuẩn, **9 phương pháp chỉ dùng metric, 2 dùng trace, 4 dùng đa nguồn**. Hướng *"đồ thị phụ thuộc dựng từ trace"* mà cô định hướng là nhóm **mỏng nhất** trong các công trình hiện có.

**Nhận định [T]:** tài liệu khảo sát mà cô gửi kết luận chưa phương pháp nào vượt trội ở mọi tình huống — mỗi phương pháp hoặc thiếu về hiệu quả, hoặc thiếu về hiệu năng, hoặc nhạy cảm với điều kiện cụ thể.

## 3.2 Hướng cơ chế của nhóm

**[N]** Từ hai nhận định trên, hướng nhóm theo đuổi là **kết hợp cấu trúc từ trace với bằng chứng từ metric và log**:

```
Trace  ──→  dựng đồ thị phụ thuộc, xác định đường đi của giao dịch lỗi
              │
Metric ──→  chấm điểm bất thường cho từng nút trên đường đi đó
              │
Log    ──→  cung cấp bằng chứng nội dung, dùng để điều chỉnh thứ hạng
              │
              ↓
        Lan truyền ngược theo chiều phụ thuộc, rồi xếp hạng
```

Nguyên tắc lan truyền: nếu nút `B` bất thường và `A` phụ thuộc `B`, thì triệu chứng quan sát ở `A` **không** làm `A` khả nghi hơn — nó làm `B` khả nghi hơn. Đây là lý do chiều cạnh ở mục 1 phải đúng, và là lý do cạnh số 3 ở §1.5 không được vẽ ẩu.

> **Chưa tuyên bố đây là đóng góp mới.** Việc dùng lại một cơ chế đã công bố không tạo ra tính mới. Hướng cụ thể và mức đóng góp chỉ được chốt **sau khi có kết quả thực nghiệm ở mục 6** cho biết khoảng cách thật giữa nhóm đơn nguồn và nhóm đa nguồn. Đây cũng là câu nhóm muốn xin ý cô: cô kỳ vọng một cơ chế mới, hay một tổ hợp có lập luận từ các cơ chế đã công bố rồi đánh giá trung thực?

## 3.3 Hai lớp lỗi mà cơ chế cần phủ

**[N]** Mục tiêu dài hạn không chỉ là lỗi hạ tầng:

```
              Cơ chế của nhóm
                     │
             chẩn đoán đa nguồn
                     │
         ┌───────────┴───────────┐
         ↓                       ↓
   lỗi tài nguyên/mạng      lỗi mức mã nguồn
   CPU · bộ nhớ · đĩa       lỗi trong logic
   độ trễ · mất gói · socket    ứng dụng
         └───────────┬───────────┘
                     ↓
         Đánh giá chéo hai lớp lỗi
```

Hai lớp này tương ứng hai bộ dữ liệu khác nhau, trình bày ở mục 5. Lớp lỗi mức mã nguồn quan trọng với đề tài vì nó gần với lỗi nghiệp vụ của một hệ giao dịch hơn lỗi hạ tầng.

---

# Mục 4 — Tích hợp AI hỗ trợ giải thích

> `DH-MT4`: *"Tích hợp AI hỗ trợ giải thích để trợ lý có thể diễn giải nguyên nhân và gợi ý bước xử lý"*

Mục 3 cho một danh sách nghi phạm đã xếp hạng. Với người phải xử lý sự cố, một danh sách tên nút chưa đủ để hành động. Mục này biến nó thành lời giải thích và bước kiểm tra tiếp theo.

## 4.1 Vị trí trong đường ống

**[N]**

```
Đồ thị + tín hiệu bất thường
        ↓
Cơ chế xếp hạng  →  danh sách nghi phạm kèm bằng chứng
        ↓
Lớp giải thích   →  diễn giải nguyên nhân + bước kiểm tra gợi ý
```

Lớp giải thích **nhận đầu ra của cơ chế**, không thay thế nó. Nó không tự xếp hạng lại và không tự kết luận.

**Đây là bước cuối của một chuỗi, không phải một thành phần riêng [N].** Toàn chuỗi — đồ thị → ánh xạ log và trace → phát hiện bất thường → xếp hạng → giải thích — được thiết kế như **một** cơ chế và **chạy trực tiếp trong FlashTicket**. Nhóm **không** xây một trợ lý độc lập tự đi thu thập và tự đoán nguyên nhân; việc tìm nguyên nhân là của mục 3, việc của mục này chỉ là làm cho kết quả đó đọc được.

## 4.2 Đầu vào và đầu ra

| | Nội dung |
|---|---|
| **Đầu vào** | Danh sách nghi phạm theo thứ hạng; với mỗi nghi phạm: chỉ số bất thường và mức lệch, các cạnh bất thường liên quan, mẫu log tiêu biểu trong cửa sổ sự cố |
| **Đầu ra** | Diễn giải bằng ngôn ngữ tự nhiên vì sao nghi phạm này đứng đầu; bước kiểm tra tiếp theo để xác nhận hoặc loại trừ |

## 4.3 Ranh giới quyền — chốt trước, không để mở

**[N]** Ba giới hạn đặt ngay từ thiết kế:

1. **Chỉ đọc.** Lớp giải thích đọc dấu vết, log và kết quả xếp hạng; không ghi dữ liệu nghiệp vụ, không thực thi hành động khắc phục.
2. **Không phải bên kết luận cuối cùng.** Đầu ra là giả thuyết kèm bằng chứng để người trực kiểm tra.
3. **Dữ liệu nhạy cảm phải được khử hoặc che trước khi rời phạm vi kiểm soát của hệ thống** — tức trước khi bằng chứng được đưa cho mô hình ngôn ngữ.

## 4.4 Cách đánh giá lớp này

**[N]** Bộ tiêu chí định nghĩa **trước** khi chạy, để tránh chấm điểm theo cảm tính:

| Tiêu chí | Đo thế nào |
|---|---|
| Tính trung thành với đầu vào | Diễn giải chỉ dùng bằng chứng thật sự có trong danh sách nghi phạm nhận được: đúng chỉ số đó, đúng cạnh đó, đúng mẫu log đó |
| Tính hữu ích | Bước kiểm tra được gợi ý có dẫn tới xác nhận hay loại trừ được không |
| Tính trung thực | Số lần đưa ra bằng chứng hoặc hành động **không có thật** trong dữ liệu đầu vào |

**Vì sao không có tiêu chí "danh sách có chứa nguyên nhân thật không" ở đây [N].** Câu hỏi đó chấm **cơ chế xếp hạng ở mục 3**, không chấm lớp giải thích: §4.1 quy định lớp này **nhận** đầu ra của cơ chế và không xếp hạng lại. Nếu cơ chế đưa nhầm nghi phạm lên đầu, một lớp giải thích hoàn hảo vẫn diễn giải nhầm nghi phạm đó — và chấm nó điểm thấp là chấm sai đối tượng. Nguyên nhân thật có lọt top-k hay không đã được `AC@k` và `Avg@5` ở mục 6 đo rồi.

Tiêu chí thứ ba là bắt buộc. Một lớp giải thích bịa ra bằng chứng nghe hợp lý còn nguy hiểm hơn không có lớp giải thích.

---

# Mục 5 — Bộ dữ liệu thực nghiệm đã công bố

> `DH-DATA`: *"Tìm những bộ dữ liệu đã được công bố để thực nghiệm giải pháp. Ví dụ có dataset công khai (Train Ticket, Online Boutique, Sock Shop) mô phỏng hệ thống giao dịch trực tuyến, có thể dùng làm dữ liệu thử nghiệm."*

Mục 2 và mục 3 yêu cầu log và trace. Mục này chọn bộ dữ liệu đáp ứng được yêu cầu đó, và giải thích vì sao một số bộ không dùng được.

## 5.1 Bốn vai không được lẫn

**[N]** Trước khi vào bảng, cần phân biệt bốn thứ mà tên riêng dễ làm lẫn:

| Vai | Là gì | Ví dụ | Nhóm có phải chạy không |
|---|---|---|---|
| **Hệ thống thử nghiệm** | Phần mềm bị chèn lỗi để thu dữ liệu | Train Ticket, Online Boutique, Sock Shop | **Không** — dữ liệu đã thu sẵn |
| **Bộ dữ liệu** | Dữ liệu đã thu, kèm nhãn nguyên nhân thật | Các bộ ở §5.2 | Không — đây là tệp |
| **Thuật toán** | Chương trình nhận dữ liệu, trả về xếp hạng | Các phương pháp ở mục 3 | **Có** |
| **Độ đo** | Con số chấm điểm kết quả của thuật toán | `AC@1`, `AC@3`, `Avg@5` ở mục 6 | Không — đây là kết quả tính ra |

Cách nhớ: **nơi ra đề — đề thi — thí sinh — ba-rem chấm.**

Ba hệ thống cô nêu thuộc vai thứ nhất. Nhóm **không cần cài đặt** chúng, vì dữ liệu đã được công bố sẵn.

Vai thứ tư có mặt ở đây vì nó quyết định cách đọc mọi con số ở mục 6, và vì nó phụ thuộc vai thứ nhất: **số ứng viên của hệ thống thử nghiệm quyết định mức sàn ngẫu nhiên** (§1.7). Bỏ nó khỏi danh sách là bỏ đúng chỗ hai vai nối vào nhau.

## 5.2 Bảng đối chiếu các bộ đã khảo sát

**[T]**

| Bộ | Số ca lỗi | Metric | Log | Trace | Nhãn |
|---|---|---|---|---|---|
| Artifact bài khảo sát nhân quả — **cô gửi** | ~375 hệ thật | ✔ | ✘ | ✘ | Nút nguyên nhân |
| Bộ đa phương thức đa lĩnh vực — **cô gửi** | 51 | ✔ | ✔ | ✔ | Nguyên nhân theo ca |
| **RE1** | 375 | ✔ | ✘ | ✘ | Nút + chỉ số |
| **RE2** | 270 (3 hệ) | ✔ | ✔ | ✔ | Nút + chỉ số |
| **RE3** | 90 (3 hệ) | ✔ | ✔ | ✔ | Nút + chỉ số |

Ba bộ RE cùng dùng ba hệ thống mà cô nêu — Online Boutique, Sock Shop, Train Ticket — và do **cùng nhóm tác giả** với artifact cô gửi phát triển.

## 5.3 Ràng buộc loại trừ

**[N]** `DH-MT2` yêu cầu ánh xạ **log và trace**; `DH-MT3` yêu cầu xếp hạng **dựa trên bằng chứng log/trace**. Một bộ chỉ có metric **không kiểm được hai mục tiêu này** — không phải vì thiếu phương pháp, mà vì thiếu dữ liệu đầu vào.

| Bộ | Kiểm được `DH-MT2` và `DH-MT3`? | Kết luận |
|---|---|---|
| Artifact cô gửi | **Không** — chỉ có metric | Dùng cho phần đối chiếu metric và cho việc so với kết quả bài gốc |
| RE1 | **Không** — chỉ có metric | Không dùng làm bộ chính |
| Bộ đa phương thức cô gửi | Có, nhưng 31/51 ca thuộc hệ xử lý nước | Ngoài bối cảnh giao dịch trực tuyến |
| **RE2** | **Có** | **Bộ chính** |
| **RE3** | **Có**, lỗi mức mã nguồn | Trục đánh giá thứ hai |

> **Điểm cần cô xác nhận.** Artifact cô gửi **chỉ chứa metric**, không có log và trace. Nhóm đề xuất dùng bộ **RE2** của cùng nhóm tác giả — vốn có đủ ba loại dữ liệu — làm bộ chính, và vẫn giữ artifact cô gửi cho phần đối chiếu metric. Đây là **bổ sung theo đúng yêu cầu của cô**, không phải thay thế nguồn cô gửi. Cô cho phép không ạ?

## 5.4 Bộ chính: RE2 trên Online Boutique

**[N] Đề xuất, chưa chốt.** Trong ba hệ của RE2, nhóm **đề xuất** Online Boutique cho vòng thực nghiệm đầu vì nó có 12 nút — nhỏ nhất, nên rủi ro bộ nhớ khi nạp log và trace thấp nhất. Lựa chọn này **chưa có dòng quyết định**: `RES-022` chỉ chốt bộ `RE2`, chưa chốt hệ nào trong bộ (`A8-OPEN-06`). Nó quyết định số ứng viên nên quyết định cả cách đọc mọi điểm số. Đây là lý do kỹ thuật, không phải chọn cho dễ: bộ này vẫn có đủ metric, log, trace và sáu loại lỗi.

**Loại lỗi trong RE2 [T]:** CPU, bộ nhớ, đĩa, độ trễ mạng, mất gói, socket.

**Cần xác minh trước khi đưa vào bản nộp [?]:** số ca lỗi của RE2 tính riêng trên Online Boutique. Tài liệu nhóm hiện chỉ ghi 270 ca trên cả ba hệ.

## 5.5 Điều kiện chạy đã đối chiếu máy thật

**[N]**

| Hạng mục | Yêu cầu | Máy của nhóm |
|---|---|---|
| CPU | 8 nhân | 12 nhân / 16 luồng ✔ |
| RAM | 16 GB | 15,7 GB ⚠ sát ngưỡng |
| Đĩa | ~50 GB | Ổ D còn 73,3 GB ✔ |
| Hệ điều hành | Ubuntu | Cần thêm distro trên WSL2 ⚠ |
| Python | 3.12 | Hiện 3.9.13 ⚠ |

**Rủi ro đã nhận diện [N]:** RE2 có log hàng triệu dòng và trace hàng chục triệu span. Thư viện chuẩn yêu cầu dữ liệu ở dạng bảng nạp vào bộ nhớ. Nạp toàn bộ vào 15,7 GB RAM là rủi ro thật. **Việc đầu tiên của vòng thực nghiệm là đo dung lượng và thời gian nạp thực tế**; nếu vượt, xử lý bằng cách nạp theo từng ca lỗi thay vì nạp cả bộ.

---

# Mục 6 — Độ đo và kết quả thực nghiệm so sánh

> `DH-DO`: *"Khảo sát các độ đo thực nghiệm theo qui chuẩn để thực nghiệm so sánh giải pháp đề xuất so với các giải pháp đã có cơ sở. Chạy thực nghiệm so sánh các giải pháp tư vấn khác nhau khi chạy trên cùng bộ dataset thì kết quả khác nhau ntn."*

Mục 5 chốt bộ dữ liệu. Mục này chốt cách chấm điểm và trình bày kết quả chạy.

## 6.1 Bài toán được chấm thế nào

**[N]** Mọi độ đo ở đây chấm cùng một loại đầu ra: **một danh sách xếp hạng**.

```
Đầu vào:  dữ liệu của một ca lỗi
Đầu ra:   [nghi phạm hạng 1, hạng 2, hạng 3, ...]
Đáp án:   tập nguyên nhân thật của ca đó
Câu hỏi:  nguyên nhân thật nằm ở hạng bao nhiêu?
```

Vì đầu ra là xếp hạng chứ không phải một nhãn, **không dùng được** các độ đo phân loại quen thuộc như độ chính xác hay F1. Phải dùng họ độ đo xếp hạng.

## 6.2 Công thức

**[T]**

```
              1        số nguyên nhân thật lọt vào top-k
AC@k  =  ─────────  ∑  ──────────────────────────────────
          số ca lỗi  ca  min(k, số nguyên nhân thật của ca)

Avg@k =  ( AC@1 + AC@2 + ... + AC@k ) / k
```

## 6.3 Điểm dễ nhầm nhất: `Avg@5` không phải `AC@5`

**[N]** `Avg@5` là trung bình của cả năm giá trị `AC@1` tới `AC@5`, nên nó **thưởng cho việc xếp nguyên nhân lên cao**. Bảng dưới giả định một ca có đúng một nguyên nhân thật:

| Nguyên nhân thật ở hạng | `AC@1` | `AC@3` | `AC@5` | **`Avg@5`** |
|---|---|---|---|---|
| 1 | 1 | 1 | 1 | **1,00** |
| 2 | 0 | 1 | 1 | **0,80** |
| 3 | 0 | 1 | 1 | **0,60** |
| 4 | 0 | 0 | 1 | **0,40** |
| 5 | 0 | 0 | 1 | **0,20** |
| ngoài top 5 | 0 | 0 | 0 | **0,00** |

`AC@5` cho hạng 1 và hạng 5 điểm như nhau; `Avg@5` phân biệt được. Vì vậy báo cáo dùng **`AC@1`, `AC@3` và `Avg@5` trong cùng một bảng**.

## 6.4 Thiết kế thực nghiệm `E1`

**[N]** Cô hỏi *"chạy trên cùng bộ dataset thì kết quả khác nhau ntn"*. Thiết kế dưới đây trả lời trực tiếp câu đó. Tất cả chạy trên **cùng RE2 trên Online Boutique, cùng bộ độ đo**:

| Chạy | Nguồn dữ liệu dùng | Vai trong bảng |
|---|---|---|
| Đối chứng ngẫu nhiên | — | Mức sàn theo §1.7 |
| Phương pháp thống kê thuần | metric | Mốc đơn nguồn |
| Phương pháp suy luận nhân quả | metric | Đại diện dòng mà tài liệu cô gửi khảo sát |
| Phương pháp dựa trace | trace | Đại diện nhóm dùng trace |
| **Phiên bản đa nguồn của cùng phương pháp thống kê** | **metric + log + trace** | **Cặp đối chứng then chốt** |

**Vì sao cặp cuối là dòng quan trọng nhất [N]:** phương pháp thống kê thuần và phiên bản đa nguồn của nó dùng **cùng một lõi thuật toán**, chỉ khác đầu vào. Chênh lệch giữa hai dòng vì vậy **cô lập được đúng tác dụng của việc thêm log và trace** — tức đo trực tiếp giả thuyết nằm sau `DH-MT2` và `DH-MT3`. Mọi so sánh khác đều lẫn hai biến: vừa khác thuật toán vừa khác dữ liệu.

## 6.5 Nguyên tắc bắt buộc khi trình bày

**[N]**

1. **Một bảng chỉ chứa kết quả của một bộ dữ liệu.** Không trộn số giữa hai bộ.
2. **Mọi phương pháp trong cùng bảng phải chạy trên cùng bộ, cùng độ đo.** Đây là nghĩa của yêu cầu "cùng bộ dataset".
3. **Mọi bảng có cột đối chứng ngẫu nhiên.**
4. **Mỗi số ghi rõ do nhóm chạy hay trích từ nguồn nào.**
5. Không trộn hai bộ độ đo khác nhau vào một bảng.

## 6.6 Kết quả

> **Chưa chạy.** Ô kết quả để trống có chủ đích, không điền số ước lượng.

| Phương pháp | Nguồn dữ liệu | `AC@1` | `AC@3` | `Avg@5` |
|---|---|---|---|---|
| Đối chứng ngẫu nhiên | — |  |  |  |
| Thống kê thuần | metric |  |  |  |
| Suy luận nhân quả | metric |  |  |  |
| Dựa trace | trace |  |  |  |
| Đa nguồn | metric + log + trace |  |  |  |

**Số hiệu năng đã công bố [T]:** nhóm **đã mở bảng kết quả toàn văn** của tài liệu chuẩn và đối chiếu. Ba điều rút ra, cả ba đều ảnh hưởng cách đọc bảng §6.6 khi nó được điền:

1. **Số công bố đo trên Train Ticket, không đo trên Online Boutique** — tức không đo trên bộ nhóm sẽ chạy ở §5.4. Train Ticket có 64 nút, Online Boutique có 12, nên mức sàn ngẫu nhiên chênh nhau hơn năm lần (§1.7). **Không được đặt hai bảng cạnh nhau và so ngang.**
2. **Một bản ghi chép trước đó của nhóm gán nhầm điểm giữa hai biến thể cùng tên gốc** — một biến thể đơn nguồn và một biến thể đa nguồn. Sai số này chỉ lộ ra khi mở bảng gốc, đúng loại lỗi mà quy tắc §6.5 mục 4 tồn tại để chặn.
3. **Phương pháp gần định hướng của cô nhất — lan truyền trên đồ thị phụ thuộc — lại đứng gần cuối bảng đó.** Nhóm ghi thẳng điều này thay vì bỏ qua; ba cách đọc còn mở, và chỉ thực nghiệm mới phân định được. Chi tiết ở hồ sơ khảo sát phương pháp của nhóm.

Cách xử lý giữ nguyên: sau khi `E1` chạy xong, **dùng số nhóm tự chạy làm số chính**, chỉ trích số của bài để đối chiếu mức độ tái lập — và chỉ đối chiếu được nếu chạy thêm một vòng trên cùng hệ mà bài đo.

---

# Phần cuối — Việc đã làm, việc tiếp theo, và điểm cần cô cho ý kiến

## 7.1 Việc nhóm đã làm trong kỳ này

**[N]**

| # | Việc | Kết quả |
|---|---|---|
| 1 | Phân tích nghiệp vụ hệ đặt vé | Bảy tài liệu được duyệt: từ điển miền, quy trình, bản đồ sự kiện, bản đồ context, use case, aggregate và bất biến, bảng yêu cầu |
| 2 | Chuyển bản đồ ngữ nghĩa thành đồ thị phụ thuộc vận hành | 12 cạnh ngữ nghĩa → 11 cạnh có tương tác được xác nhận (chiều và dạng chờ gate hợp đồng) và 1 cạnh phụ thuộc cách hiện thực; cộng một cạnh quan sát không chuyển thành phụ thuộc giao dịch; bổ sung hai lớp nút hạ tầng |
| 3 | Đặc tả ánh xạ log và trace lên đồ thị | Trường bắt buộc, ba quy tắc ánh xạ, ba loại tín hiệu bất thường |
| 4 | Khảo sát phương pháp | Bốn họ, phân bố 9/2/4 trong tập đối chứng chuẩn |
| 5 | Khảo sát bộ dữ liệu | Năm bộ; phát hiện artifact cô gửi chỉ có metric |
| 6 | Khảo sát độ đo | Công thức, cách đọc, mức sàn ngẫu nhiên theo số ứng viên |
| 7 | Ghi ràng buộc phòng ngừa lên các gate thiết kế còn lại | Mười hai ràng buộc, để thiết kế hệ thống không chặn tích hợp chẩn đoán |
| 8 | **Dựng bộ kịch bản chất lượng cho hệ thống** | **18 kịch bản** có tiêu chí quan sát được, mỗi kịch bản truy về một bất biến hoặc một điểm nóng đã mô hình hóa. Ba trong số đó đo trực tiếp **dữ liệu quan sát mà cơ chế chẩn đoán cần**: dấu vết có đủ tín hiệu để lần ra nguyên nhân, dựng lại được trình tự một giao dịch xuyên thành phần, và cơ chế chạy được rồi kết quả đến được người có quyền |
| 9 | **Xếp ưu tiên và chốt danh sách yêu cầu có ý nghĩa kiến trúc** | **13 mức Cao / 2 Trung bình / 3 Thấp**, sinh **15 yêu cầu kiến trúc**. Tiêu chí xếp hạng là *"nếu đòi hỏi này đổi thì kiến trúc có phải khác đi không"*, không phải mức quan trọng nghiệp vụ |
| 10 | **Chốt hai điều kiện để cơ chế chẩn đoán chạy được trên hệ nhà** | Một yêu cầu buộc kiến trúc **chừa chỗ chạy cơ chế và chừa đường lấy dữ liệu quan sát**; một ràng buộc phủ định **cấm phương án làm cho việc chèn lỗi có kiểm soát trở nên bất khả thi** — không chèn được lỗi thì không có ca lỗi có đáp án trên hệ nhà. Cộng ràng buộc **chỉ đọc**: cơ chế không có đường ghi vào dữ liệu nghiệp vụ |
| 11 | **Chốt mã tương quan là ràng buộc bắt buộc** | Mọi bản ghi thuộc cùng một giao dịch phải truy được qua **một mã tương quan duy nhất**, đi qua được cả bước bất đồng bộ. Đây chính là điểm nghẽn số một mà khảo sát quy trình chẩn đoán hiện tại của nhóm ghi nhận: *"thiếu mã giao dịch thống nhất để bắt đầu tìm"* |

## 7.2 Việc tiếp theo

**[N]** Dựng môi trường và đo khả năng nạp RE2 · chạy `E1` và điền bảng §6.6 · xác minh số ca lỗi của RE2 trên riêng Online Boutique, con số `[?]` duy nhất còn lại · mở rộng sang lớp lỗi mức mã nguồn · **bắt đầu bước kiến trúc: tạo và so sánh tập phương án phân rã service một cách độc lập, rồi mới đối chiếu với tài sản mã nguồn hiện có** — bước này quyết định cơ chế chẩn đoán sẽ được đặt ở đâu và lấy dữ liệu quan sát bằng đường nào.

## 7.3 Năm điểm nhóm xin ý kiến cô

**Hai câu hỏi** — nhóm chưa có phương án và cần cô quyết:

1. **Bộ dữ liệu.** Artifact cô gửi chỉ chứa metric, không có log và trace, trong khi `DH-MT2` và `DH-MT3` đều cần cả hai. Nhóm đề xuất dùng **RE2** của cùng nhóm tác giả làm bộ chính và giữ artifact cô gửi cho phần đối chiếu metric. Cô cho phép không ạ?

2. **Mức kỳ vọng của `DH-MT3`.** Cụm *"đề xuất cơ chế lan truyền và xếp hạng"* — cô kỳ vọng một cơ chế mới, hay một tổ hợp có lập luận từ các cơ chế đã công bố rồi đánh giá trung thực? Hai mức kỳ vọng khác nhau rất xa về khối lượng và về cách nhóm viết phần đóng góp.

**Ba điểm xin cô xác nhận cách hiểu** — nhóm đã có phương án, trình để cô chỉnh nếu lệch:

3. **Bốn mục tiêu cụ thể của phần nghiên cứu.** Dựng đồ thị và ánh xạ tín hiệu bất thường lên nó; xây cơ chế lan truyền và xếp hạng; đánh giá **cùng điều kiện** với các phương pháp đã công bố **và một mức sàn ngẫu nhiên**; làm rõ việc thêm nguồn dữ liệu có cải thiện kết quả không. Nhóm cố ý **không** phát biểu mục tiêu nào là *"đề xuất phương pháp mới"* khi chưa có kết quả thực nghiệm chống lưng.

4. **Cách đặt đối tượng nghiên cứu.** Đối tượng là **cơ chế chẩn đoán bằng đồ thị phụ thuộc**; hệ thống đặt vé của nhóm là **phương tiện** — hệ được mô hình hóa theo `DH-MT1` — chứ không phải đối tượng nghiên cứu và cũng không phải phần bị hạ vai; bối cảnh áp dụng là lớp hệ giao dịch trực tuyến kiến trúc microservice.

5. **Cách chia phạm vi và lộ trình ba mức.** Phần được đo sâu là sáu mục cô liệt kê; phần sản phẩm chỉ cần chạy được và kiểm thử chức năng. Việc đưa cơ chế lên chạy trên chính hệ của nhóm đi theo ba mức — mô hình hóa, chạy thật, chấm điểm — và nhóm ghi rõ **mức ba cô không yêu cầu**.

---

## Phép tự kiểm trước khi nộp

- [x] Sáu mục đúng thứ tự cô liệt kê; mỗi mục mở đầu bằng trích dẫn `DH-*` tương ứng.
- [x] Mỗi mục có một câu nối với mục trước.
- [x] Mọi khẳng định gắn nhãn **[N]**, **[T]** hoặc **[?]**.
- [x] Cột *"cạnh vận hành suy ra"* ở §1.3 không khẳng định chiều hay dạng của cạnh nào — đúng như Quy tắc 2 và Quy tắc 3 đứng ngay trên bảng.
- [x] Câu về vai của trace ở §2.1 không mâu thuẫn với §3.1 (có họ phương pháp dựng đồ thị không cần trace) và không mâu thuẫn với mục 1 (đồ thị dựng từ bằng chứng nghiệp vụ).
- [x] Tiêu chí ở §4.4 chấm đúng **lớp giải thích**, không chấm hộ cơ chế xếp hạng của mục 3.
- [x] Số vai ở §5.1 bằng số vai ở hồ sơ từ vựng nền của nhóm, và bằng **bốn**.
- [x] Ràng buộc ở §2.5 không giả định mẫu kiến trúc nào — việc chọn mẫu thuộc gate kiến trúc.
- [x] Không có cụm định tính không kiểm chứng được.
- [x] Đồ thị ở mục 1 dựng từ bằng chứng của chính nhóm; mỗi cạnh truy được về một dòng bản đồ sự kiện.
- [x] Nêu rõ vì sao không chép thẳng bản đồ context, kèm ví dụ cụ thể ở §1.5.
- [x] Mức sàn ngẫu nhiên tính trước, để đọc được kết quả sau.
- [x] Không tuyên bố tính mới cho cơ chế ở mục 3.
- [ ] Chạy `E1` và điền bảng §6.6.
- [ ] Xác minh số ca lỗi của RE2 tính riêng trên Online Boutique — số `[?]` duy nhất còn lại ở §5.4.
- [ ] Lê Văn Minh duyệt trước khi gửi.
