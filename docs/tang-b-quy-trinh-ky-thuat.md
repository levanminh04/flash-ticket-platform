# TẦNG B — KHUNG QUY TRÌNH KỸ THUẬT
## ĐATN FlashTicket · PTIT · Nộp 14/12/2026

---

## 0. Tài liệu này là gì

**Trả lời:** từ chỗ chưa biết gì về nghiệp vụ, đi qua những bước nào, bằng phương pháp nào, để ra được bản thiết kế đủ để bắt tay code.

**Bốn phần:** Giải phẫu ba framework → Chắt lọc → Ghép chuỗi và bổ sung phần thiếu → Khung rỗng.

**Không chứa:** nội dung nghiệp vụ cụ thể (có bao nhiêu service, tên là gì, bảng nào). Đó là thứ bạn điền vào Phần 4 khi thực sự làm.

**Ràng buộc kế thừa từ Tầng A** — Tầng B không được mâu thuẫn với bảy điều ở Phần 5 Tầng A. Quan trọng nhất: mục tiêu phải định lượng **trước** khi thiết kế kiến trúc, và mọi thay đổi thiết kế phải ghi bằng bản ghi mới chứ không sửa bản cũ.

Ký hiệu: 🔴 bắt buộc · 🟡 nên có · ⚪ cắt được

---

# PHẦN 1 — GIẢI PHẪU BA FRAMEWORK

## 1.1 OOAD + UML

### Tư tưởng chính

Phân tích và thiết kế hệ thống bằng cách ánh xạ các đối tượng của thế giới thực thành đối tượng phần mềm. Quy trình được **dẫn dắt bởi ca sử dụng** (use-case driven): use case là đầu vào cho mọi mô hình phía sau, và cũng là đầu vào cho kiểm thử.

Trình tự chuẩn được dạy ở các trường kỹ thuật Việt Nam:

| Pha | Mô hình xây dựng |
|---|---|
| **Pha phân tích** | Biểu đồ use case → biểu đồ hoạt động → biểu đồ lớp (mức phân tích) → biểu đồ tuần tự / cộng tác → biểu đồ trạng thái |
| **Pha thiết kế** | Biểu đồ lớp (mức thiết kế) → biểu đồ thành phần → biểu đồ triển khai |

### Bộ công cụ đầy đủ

**Biểu đồ cấu trúc:** lớp, đối tượng, thành phần, gói, triển khai, cấu trúc phức hợp, profile.
**Biểu đồ hành vi:** use case, hoạt động, trạng thái, tuần tự, cộng tác/giao tiếp, tổng quan tương tác, thời gian.

**Kỹ thuật kèm theo (không phải biểu đồ):**
- **Đặc tả use case** — bảng mô tả: tác nhân, tiền điều kiện, luồng chính, luồng thay thế, luồng ngoại lệ, hậu điều kiện
- **Phân loại lớp phân tích** theo ba vai trò: lớp biên (boundary), lớp điều khiển (control), lớp thực thể (entity)

### Quy trình đứng sau: RUP

RUP (Rational Unified Process) là quy trình mà UML sinh ra để phục vụ. Ba đặc trưng: **hướng ca sử dụng**, **lấy kiến trúc làm trung tâm**, **lặp và tăng dần**. Bốn pha: Khởi đầu → Chi tiết hóa → Xây dựng → Chuyển giao, mỗi pha có nhiều vòng lặp; chín luồng công việc (disciplines) chạy xuyên các pha.

### ❗ Giới hạn khi dùng OOAD cho đề tài này

OOAD/UML không tự đưa ra **ranh giới microservice** hoặc quyền sở hữu dữ liệu. UML vẫn mô tả được hệ phân tán, nhưng câu hỏi “chia thành mấy service, ranh giới ở đâu” cần thêm phân tích miền, giao dịch và thuộc tính chất lượng; không thể chỉ suy từ danh sách use case.

---

## 1.2 DDD (Domain-Driven Design) + Event Storming

### Tư tưởng chính

Phần mềm phức tạp phải được thiết kế bám theo **mô hình nghiệp vụ**, không bám theo cấu trúc kỹ thuật. Cả nhóm dùng chung một bộ thuật ngữ với chuyên gia nghiệp vụ, và bộ thuật ngữ đó xuất hiện thẳng trong tên lớp, tên bảng, tên API.

DDD có hai nửa tách biệt:

### Nửa chiến lược (Strategic DDD) — chia hệ thống

| Khái niệm | Nghĩa |
|---|---|
| **Ngôn ngữ chung** (Ubiquitous Language) | Một bộ thuật ngữ thống nhất giữa người làm nghiệp vụ và người viết code |
| **Miền và miền con** (Domain / Subdomain) | Miền là toàn bộ lĩnh vực nghiệp vụ; miền con là một mảng của nó |
| **Phân loại miền con** | **Cốt lõi** (tạo lợi thế cạnh tranh) · **Hỗ trợ** (cần nhưng không tạo khác biệt) · **Chung** (ai cũng cần, nên mua sẵn) |
| **Ngữ cảnh giới hạn** (Bounded Context) | Ranh giới mà bên trong đó một mô hình và một bộ thuật ngữ có nghĩa nhất quán. Ngoài ranh giới, cùng một từ có thể mang nghĩa khác |
| **Bản đồ ngữ cảnh** (Context Map) | Sơ đồ các bounded context và quan hệ giữa chúng |
| **Các mẫu tích hợp giữa context** | Shared Kernel, Customer–Supplier, Conformist, **Anti-Corruption Layer**, Open Host Service, Published Language, Separate Ways |

### Nửa chiến thuật (Tactical DDD) — thiết kế bên trong một context

Entity · Value Object · **Aggregate** (và Aggregate Root) · Repository · Domain Service · **Domain Event** · Factory · Specification.

> **Khái niệm quan trọng nhất trong nửa này: Aggregate.**
> Aggregate là một cụm đối tượng được coi như **một đơn vị nhất quán duy nhất** — mọi thay đổi bên trong nó xảy ra trong **một giao dịch**. Ngược lại, thay đổi xuyên nhiều aggregate **không** được nằm trong một giao dịch, mà phải dùng nhất quán cuối cùng.
> Nói cách khác: **ranh giới aggregate chính là ranh giới giao dịch.** Đây là chỗ DDD chạm thẳng vào trục nghiên cứu của bạn.

### Hai mẫu phân rã dịch vụ

- **Phân rã theo năng lực nghiệp vụ** (decompose by business capability) — xác định tổ chức *làm được những gì*, mỗi năng lực thành một dịch vụ
- **Phân rã theo miền con** (decompose by subdomain) — mỗi miền con thành một dịch vụ, ranh giới lấy theo bounded context

Khảo sát thực tiễn cho thấy **kết hợp cả hai** là chiến lược được dùng phổ biến nhất, vì chúng bổ trợ nhau.

### Event Storming — kỹ thuật khai phá

Ba mức, dùng cho ba mục đích khác nhau:

| Mức | Mục đích | Sản phẩm |
|---|---|---|
| **Big Picture** | Nhìn toàn cảnh miền, tìm ranh giới | Dòng thời gian sự kiện + các cụm ứng viên thành bounded context |
| **Process Level** | Đi sâu một quy trình cụ thể | Chuỗi lệnh–sự kiện–chính sách của quy trình đó |
| **Design Level** | Mô hình hóa chi tiết | Aggregate, read model — gần với tactical DDD |

Bộ phần tử (theo quy ước màu giấy dán):

| Phần tử | Màu | Nghĩa |
|---|---|---|
| Sự kiện miền (Domain Event) | Cam | Việc đã xảy ra, viết ở thì quá khứ |
| Lệnh (Command) | Xanh dương | Ý định gây ra sự kiện, viết ở thể mệnh lệnh |
| Tác nhân (Actor) | Vàng nhỏ | Ai phát lệnh |
| Aggregate | Vàng nhạt | Nơi tiếp nhận lệnh và sinh sự kiện |
| Chính sách (Policy) | Tím | Quy tắc "khi X xảy ra thì làm Y" |
| Read model | Xanh lá | Dữ liệu người dùng cần thấy để ra quyết định |
| Hệ thống ngoài | Hồng | Bên thứ ba |
| Điểm nóng (Hotspot) | Đỏ | Chỗ chưa thống nhất, cần giải quyết |

### ❗ Điều DDD không có

Không có cách lấy yêu cầu chức năng theo dạng hội đồng quen (use case). Không có cơ chế biến mục tiêu chất lượng thành số đo. Và Event Storming vốn là **kỹ thuật hội thảo cần chuyên gia nghiệp vụ tham gia** — điều kiện nhóm bạn không có.

---

## 1.3 Kịch bản thuộc tính chất lượng và cách xếp ưu tiên

### Tư tưởng chính

Kiến trúc không sinh ra từ chức năng mà sinh ra từ **thuộc tính chất lượng**. Nhưng thuộc tính chất lượng viết dạng khẩu hiệu ("hệ thống phải nhanh", "phải an toàn") thì không dùng được. Phải biến thành **kịch bản cụ thể có số đo**.

### Kịch bản thuộc tính chất lượng — cấu trúc sáu phần

| Thành phần | Nội dung |
|---|---|
| Nguồn kích thích (Source) | Ai/cái gì tạo ra kích thích |
| Kích thích (Stimulus) | Điều kiện cần hệ thống phản ứng |
| Tạo tác (Artifact) | Bộ phận nào của hệ thống bị tác động |
| Môi trường (Environment) | Trạng thái hệ thống lúc đó |
| Phản ứng (Response) | Hệ thống phải làm gì |
| **Độ đo phản ứng (Response Measure)** | **Đo bằng con số nào** |

### Cây tiện ích/ATAM — tài liệu tham khảo, không phải quy trình áp dụng

ATAM dùng cây tiện ích và một quy trình đánh giá nhiều bên. Điều kiện đó không có trong đồ án này, nên **không dựng cây tiện ích và không tuyên bố thực hiện ATAM**. Nhóm chỉ giữ việc xếp ưu tiên bằng một bảng đơn giản: kịch bản, mức quan trọng, lý do, cách kiểm chứng. Khi phân tích quyết định có thể dùng các từ thông thường như rủi ro, điểm nhạy cảm và đánh đổi.

### Các loại nhận xét có thể giữ

| Loại | Nghĩa |
|---|---|
| **Rủi ro** (Risk) | Quyết định kiến trúc có thể gây hậu quả xấu |
| **Không rủi ro** (Non-risk) | Quyết định đã được kiểm chứng là an toàn trong ngữ cảnh này |
| **Điểm nhạy cảm** (Sensitivity point) | Tham số mà thay đổi nó ảnh hưởng mạnh tới một thuộc tính chất lượng |
| **Điểm đánh đổi** (Tradeoff point) | Nơi cải thiện thuộc tính này làm xấu thuộc tính kia |

### ❗ Điều framework này không có

Không mô hình hóa nghiệp vụ, không lấy yêu cầu chức năng, không chia service. Nó chỉ lo phần chất lượng.

---

# PHẦN 2 — CHẮT LỌC

## 2.1 OOAD + UML

| Thành phần | Quyết định | Lý do |
|---|---|---|
| **Biểu đồ use case + đặc tả use case** | 🔴 LẤY nguyên | Hội đồng PTIT chắc chắn đọc kỹ mục này. Cũng là neo để truy vết sang test case |
| **Biểu đồ hoạt động (có swimlane)** | 🔴 LẤY | Mô hình hóa quy trình nghiệp vụ. Chọn cái này thay BPMN vì hội đồng quen UML hơn, và mình không cần sức mạnh mô hình hóa liên tổ chức của BPMN |
| **Biểu đồ tuần tự** | 🔴 LẤY, **bắt buộc gồm cả luồng thất bại** | Đây là công cụ chính để bảo vệ luận điểm về tính nhất quán. Chỉ vẽ luồng thành công là bỏ mất phần có giá trị nhất |
| **Biểu đồ lớp** | 🔴 LẤY nhưng **SỬA cách dùng** | Không vẽ một biểu đồ lớp tổng cho toàn hệ thống — làm vậy là ngầm khẳng định mọi thực thể chung một mô hình dữ liệu, tức đã thiết kế monolith. Thay bằng: **một biểu đồ mức phân tích cho mỗi bounded context**, và một biểu đồ mức thiết kế cho 2–3 service cốt lõi |
| **Biểu đồ triển khai** | 🔴 LẤY | Cần để thể hiện đúng ràng buộc 2 EC2 |
| **Biểu đồ trạng thái** | 🟡 LẤY, chỉ 1–2 cái | Vòng đời vé và vòng đời đơn hàng đúng là máy trạng thái thật. Vẽ cho hai đối tượng này rất đáng; vẽ cho đối tượng khác thì thừa |
| **Đặc tả use case** | 🔴 LẤY, giới hạn 8–12 ca | Đặc tả đầy đủ cho ca cốt lõi; ca CRUD phụ trợ chỉ liệt kê trong biểu đồ, không đặc tả |
| **Biểu đồ thành phần (UML)** | ⚪ BỎ | Trùng vai trò với C4 Container/Component. Giữ cả hai chỉ gây lẫn ký hiệu |
| **Biểu đồ cộng tác/giao tiếp** | ⚪ BỎ | Trùng thông tin với biểu đồ tuần tự |
| **Biểu đồ đối tượng, cấu trúc phức hợp, thời gian, tổng quan tương tác, profile** | ⚪ BỎ | Không tạo ra thông tin nào cần cho đề tài này |
| **Biểu đồ gói** | 🟡 GIỮ đúng một mục đích | Chỉ dùng làm phương tiện vẽ Bản đồ Bounded Context để đưa vào báo cáo |
| **Phân loại lớp biên/điều khiển/thực thể** | ⚪ BỎ | Có giá trị sư phạm nhưng thêm một lớp mô hình trung gian không dùng tới |
| **RUP — bốn pha, chín luồng công việc** | ⚪ BỎ bộ máy | Quá nặng cho 19 tuần |
| **RUP — hai tư tưởng cốt lõi** | 🔴 LẤY | Giữ đúng hai ý: **hướng ca sử dụng** và **lặp, tăng dần**. Hai ý này đủ, phần còn lại là thủ tục |

## 2.2 DDD + Event Storming

| Thành phần | Quyết định | Lý do |
|---|---|---|
| **Ngôn ngữ chung** | 🔴 LẤY | Chi phí gần bằng 0, quyết định tên lớp/bảng/API về sau. Làm sai từ đầu thì phải đổi tên hàng loạt vào tháng 10 |
| **Bounded Context + Context Map** | 🔴 LẤY | Đây là lý do chính dùng DDD: nó trả lời "chia service thế nào và vì sao" |
| **Phân loại miền con (cốt lõi / hỗ trợ / chung)** | 🟡 LẤY | Rẻ và có tác dụng thật: nó chỉ ra chỗ nào đáng đầu tư công sức, chỗ nào chỉ cần chạy được. Khớp trực tiếp với ba vòng phạm vi ở Tầng A |
| **Aggregate** | 🔴 LẤY, **coi là khái niệm trung tâm của Tầng B** | Ranh giới aggregate = ranh giới giao dịch. Xác định aggregate đúng thì biết được chỗ nào dùng ACID, chỗ nào buộc phải dùng Saga. Đây là mắt xích nối miền nghiệp vụ với trục nghiên cứu |
| **Domain Event** | 🔴 LẤY | Là đầu vào của Event Storming và là đơn vị giao tiếp bất đồng bộ giữa service |
| **Entity, Value Object** | 🟡 LẤY nhẹ | Dùng để phân biệt cái gì có định danh, cái gì không. Không cần đào sâu |
| **Anti-Corruption Layer** | 🔴 LẤY | Đúng hai chỗ cần: (a) tích hợp VNPay — không để mô hình của cổng thanh toán rò vào mô hình nghiệp vụ; (b) **tính năng AI — đây chính là cơ chế kỹ thuật để thực thi ràng buộc "AI không được sửa logic nghiệp vụ"** |
| **Open Host Service / Published Language** | 🟡 LẤY | Là cách gọi có tên cho việc chốt hợp đồng API/event giữa các service |
| **Shared Kernel, Customer–Supplier, Conformist, Separate Ways** | ⚪ BỎ | Bốn mẫu này giải quyết vấn đề phối hợp giữa nhiều đội độc lập — nhóm 3 người dưới một người quyết định thiết kế không có vấn đề đó |
| **Repository, Factory, Domain Service, Specification** | ⚪ BỎ khỏi tài liệu thiết kế | Là chi tiết cài đặt, xuất hiện tự nhiên trong code Spring. Không cần mô hình hóa riêng |
| **Dòng thời gian sự kiện miền (tham khảo Event Storming)** | 🔴 LẤY gọn | Dùng nội bộ để nhìn luồng và tìm ranh giới; không cần tổ chức đầy đủ nghi thức workshop hay đưa toàn bộ bảng màu vào báo cáo |
| **Event Storming mức Process Level** | 🟡 LẤY cho 2–3 quy trình then chốt | Chỉ làm cho luồng mua vé và luồng xử lý thất bại |
| **Event Storming mức Design Level** | ⚪ BỎ | Trùng với tactical DDD, và tốn thời gian không tương xứng ở quy mô này |
| **Bản chất "hội thảo có chuyên gia nghiệp vụ"** | 🔴 **SỬA, và khai báo trung thực** | Không có chuyên gia để mời. Áp dụng nội bộ nhóm, nguồn tri thức là khảo sát đối sánh + tài liệu + kinh nghiệm thành viên. **Trong báo cáo ghi đúng như vậy**, không viết là đã hội thảo với các bên liên quan |
| **Nguyên tắc "1 bounded context = 1 service"** | 🔴 **KHÔNG áp dụng máy móc** | Bounded context là ranh giới mô hình, không bắt buộc luôn là một tiến trình. Đồ án đã chốt **≤ 8 service nghiệp vụ** và **≤ 3 luồng Saga**; ghi ADR khi một quyết định gộp/tách ảnh hưởng rõ đến dữ liệu, giao dịch hoặc triển khai |

## 2.3 Kịch bản chất lượng + bảng ưu tiên

| Thành phần | Quyết định | Lý do |
|---|---|---|
| **Kịch bản sáu phần** | 🟡 DÙNG GỌN | Giữ đủ bối cảnh, kích thích, phản ứng và độ đo; có thể gộp trường nếu bảng báo cáo quá dài |
| **Bảng xếp ưu tiên** | 🔴 LẤY | Một bảng phẳng đủ để chọn kịch bản cốt lõi; không cần cây tiện ích hoặc cặp nhãn hình thức |
| **Rủi ro / điểm nhạy cảm / đánh đổi** | 🟡 LẤY khi có ích | Dùng để giải thích quyết định thật, không cần phân loại mọi phát hiện |
| **Quy trình ATAM và cây tiện ích** | ⚪ BỎ | Quá nặng cho phạm vi ĐATN đại học và không có nhóm đánh giá độc lập |
| **Cách gọi tên trong báo cáo** | 🔴 SỬA | Viết “phân tích các kịch bản chất lượng và đánh đổi kiến trúc”; không viết “nhóm đã thực hiện ATAM” |

---

# PHẦN 3 — GHÉP CHUỖI VÀ BỔ SUNG PHẦN THIẾU

## 3.1 Ba framework phân vai thế nào

Điểm mấu chốt: **ba framework không chồng lấn, mỗi cái trả lời một câu hỏi khác nhau.** Nếu dùng nhầm vai, sẽ ra thiết kế sai.

| Câu hỏi | Framework trả lời | Framework KHÔNG được dùng để trả lời |
|---|---|---|
| Nghiệp vụ vận hành thế nào? | Event Storming (Big Picture) + Biểu đồ hoạt động | — |
| Hệ thống phải làm những chức năng gì? | Use Case + đặc tả | — |
| **Chia thành mấy service, ranh giới ở đâu?** | **Bounded Context (DDD)** | ❌ **Không phải Use Case** |
| Chỗ nào dùng được giao dịch ACID, chỗ nào buộc phải nhất quán cuối cùng? | **Aggregate (DDD chiến thuật)** | — |
| Kiến trúc phải ưu tiên điều gì? | **Kịch bản chất lượng + bảng ưu tiên** | ❌ Không phải chỉ từ Use Case |
| Thiết kế có hợp lý không, đánh đổi ở đâu? | So sánh phương án trong các ADR quan trọng + kết quả kiểm chứng | — |

> ⚠️ **Lỗi phổ biến nhất cần tránh:** nhìn biểu đồ use case rồi chia service theo nhóm use case — ví dụ gom mọi use case của Admin thành "Admin Service". Đó là chia theo **vai trò người dùng**, không phải theo **miền nghiệp vụ**, và là một sai lầm kiến trúc. Ranh giới service đến từ Event Storming, không đến từ use case.

## 3.2 Chuỗi bàn giao

Đây là quan hệ đầu vào–đầu ra chính, không phải dây chuyền cứng. Các nhánh có thể lặp và cập nhật khi xuất hiện bằng chứng mới:

```
Khảo sát đối sánh
   └→ Từ điển miền ────────────────────┐
   └→ Biểu đồ hoạt động                │ (nuôi tên gọi cho mọi
        └→ Event Storming Big Picture  │  mô hình phía sau)
             └→ Bounded Context ───────┴→ RANH GIỚI SERVICE
                  └→ Aggregate ──────────→ RANH GIỚI GIAO DỊCH
                                              └→ Cơ chế nhất quán (Saga/Outbox…)
   └→ Use Case + đặc tả ────────────────→ CHỨC NĂNG + neo cho test
   └→ Yêu cầu phi chức năng
        └→ Kịch bản chất lượng
             └→ Bảng ưu tiên ───────────→ YÊU CẦU CÓ Ý NGHĨA KIẾN TRÚC
                                              └→ Quyết định kiến trúc (ADR)
```

Hai nhánh trái (miền nghiệp vụ) và phải (chất lượng) chạy song song, và **gặp nhau ở bước ra quyết định kiến trúc**: ranh giới service đến từ nhánh trái, còn cơ chế bên trong ranh giới đó đến từ nhánh phải.

## 3.3 ❗ Bốn thứ không framework nào phủ — phải bổ sung

Đây là chỗ dễ hụt nhất, vì cả ba framework đều im lặng:

### (a) 🔴 Thiết kế dữ liệu trong hệ phân tán

OOAD cho ra biểu đồ lớp → ERD, nhưng theo giả định một CSDL duy nhất. DDD cho ra aggregate nhưng dừng ở mức mô hình, không nói về thiết kế vật lý. Không framework nào nói **dữ liệu được sở hữu bởi ai khi có nhiều CSDL**.

**Bổ sung — ba phần thay cho một ERD tổng:**
1. **Bản đồ sở hữu dữ liệu** — bảng: mỗi thực thể nghiệp vụ × service nào được ghi × service nào chỉ đọc bản sao
2. **ERD riêng cho từng schema/CSDL của từng service** — gồm cả bảng hiện tại được giữ/sửa và bảng mới phát sinh
3. **Chiến lược dữ liệu xuyên service** — sao chép qua sự kiện hay gọi API; chấp nhận độ trễ bao lâu; xử lý dữ liệu mồ côi

**Quy tắc kiểm tra:** mỗi service dùng credential chỉ có quyền trên schema/CSDL của mình; không có khóa ngoại, `JOIN`, repository hoặc truy vấn trực tiếp xuyên ranh giới. Liên kết ngoài miền là ID mềm; dữ liệu cần dùng cục bộ lấy qua API hoặc bản sao/read model đồng bộ bằng sự kiện.

### (b) 🔴 Hợp đồng giao tiếp (API và Event)

Không framework nào trong ba cái nói về việc chốt hợp đồng. Nhưng đây là **ranh giới giữa ba thành viên**: chưa chốt hợp đồng thì không ai tích hợp được với ai.

**Bổ sung:** đặc tả API đồng bộ + lược đồ sự kiện bất đồng bộ, chốt **trước** khi chia việc code.

### (c) 🔴 Thứ tự xây dựng

Cả ba đều là framework thiết kế, không nói xây theo thứ tự nào.

**Bổ sung hai quy tắc:**
- **Bộ khung xuyên suốt trước tiên** — việc code đầu tiên không phải là service hoàn chỉnh nhất, mà là một lát cắt mỏng chạy xuyên toàn hệ thống và **triển khai được lên EC2 thật**. Đây là cách phát hiện sớm 2 EC2 có đủ hay không, khi còn thời gian xoay xở
- **Xây theo lát dọc, không theo tầng ngang** — hoàn thiện từng luồng nghiệp vụ trọn vẹn, không làm kiểu "tuần này cả nhóm làm CSDL, tuần sau cả nhóm làm API"

### (d) 🔴 Chỗ đứng của hệ thống con AI

Không framework nào cho biết đặt một thành phần AI vào đâu trong kiến trúc.

**Tính năng AI đã chốt: trợ lý hỗ trợ chẩn đoán lỗi** theo hướng `structured logging → Drain → tạo context → LLM API`. Ba quy tắc bố trí:
1. Hệ thống con AI là **một bounded context riêng**, không nhét vào context nghiệp vụ có sẵn
2. Nó chỉ đọc log/dấu vết/sự kiện cần thiết qua adapter/ACL và credential riêng; **không có đường ghi vào nghiệp vụ, không truy cập trực tiếp schema nghiệp vụ**. ACL ở đây làm nhiệm vụ dịch và cách ly mô hình, còn quyền truy cập phải được thực thi bằng phân quyền thật. Ràng buộc chỉ đọc không áp cho chatbot mua vé dùng API công khai. Xem B5.5 mục 2.4
3. Hai vai trò (chatbot mua vé / trợ lý sửa lỗi) là **hai bounded context**, kể cả khi triển khai chung một tiến trình — tách bạch bằng module, tool và vai trò phân quyền riêng. Ghi một ADR nêu lý do triển khai chung

---

# PHẦN 4 — KHUNG RỖNG

Mười chín phiếu gợi ý, chia năm nhóm. Không phải phiếu nào cũng trở thành một mục độc lập trong báo cáo: dùng chúng để tạo đầu ra cần thiết, rồi gộp trình bày theo mẫu ĐATN chính thức.

**Ai sở hữu:** các phiếu B5, B10, B11, B12, B13 là quyết định thiết kế — thuộc quyền bạn với tư cách người quyết định kiến trúc. Các phiếu còn lại có thể giao.

---

## NHÓM 1 — KHAI PHÁ MIỀN *(chủ đạo: Event Storming + DDD chiến lược)*

### 🔴 B1 — Báo cáo khảo sát đối sánh
**Đầu vào:** không có gì (đây là bước đầu tiên)
**Phương pháp:** chọn một số hệ thống có luồng công khai và đủ bằng chứng. Không bắt buộc đúng ba hệ thống hoặc phải có đại diện mọi nhóm. Chỉ quan sát những gì sinh viên truy cập hợp pháp/miễn phí; không đặt yêu cầu khảo sát màn hình admin/organizer trả phí hoặc không có tài khoản demo, và không suy đoán cấu trúc nội bộ từ giao diện.

**Bảng công việc khảo sát tối thiểu:**

| # | Công việc/luồng cần khảo sát | Bằng chứng chấp nhận được | Có thể bỏ khi |
|---|---|---|---|
| 1 | Tìm kiếm/danh sách sự kiện | Ảnh chụp, URL, ghi chú ngày quan sát | Hệ thống không cho truy cập công khai |
| 2 | Chi tiết sự kiện và loại vé | Luồng công khai hoặc tài liệu trợ giúp | Không có sự kiện mẫu |
| 3 | Chọn vé/chỗ ngồi | Các bước và phản hồi nhìn thấy | Yêu cầu trả phí/tài khoản đặc biệt |
| 4 | Giữ chỗ/đếm ngược | Thời hạn hiển thị nếu quan sát được | Không công khai; ghi “không quan sát được” |
| 5 | Hết vé hoặc thay đổi khả dụng | Thông báo công khai trong giao diện | Không tạo được tình huống hợp lệ |
| 6 | Khởi tạo thanh toán, lỗi hoặc thử lại | Chỉ đi đến bước an toàn, không bắt buộc mua thật | Phát sinh chi phí hoặc rủi ro giao dịch |
| 7 | Cách nhận vé/QR và hướng dẫn check-in | Sự kiện miễn phí, demo hoặc tài liệu công khai | Không có nguồn công khai |

**Mẫu ghi nhận:**

```text
Hệ thống | Luồng công khai | Điều quan sát được | Bằng chứng | Giới hạn/không quan sát được
```

**Phép thử:** phân biệt rõ **quan sát** và **suy luận**. Ví dụ, đếm ngược cho thấy có khái niệm giữ chỗ ở giao diện, nhưng không chứng minh hệ thống dùng Redis, khóa phân tán hay một kiến trúc cụ thể.
```
Các hệ thống có thể khảo sát: ___________________________
Các luồng công khai đã quan sát: ________________________
Những gì không thể quan sát/không được suy luận: ________
```
**Đi vào:** phần Tổng quan/Khảo sát hệ thống tương tự theo bố cục được duyệt

### 🔴 B2 — Từ điển miền
**Đầu vào:** B1
**Phương pháp:** liệt kê mọi danh từ nghiệp vụ, định nghĩa 1–2 câu. Quan trọng nhất là **chỉ ra các cặp từ dễ nhầm** và phân biệt dứt khoát.
**Phép thử:** ba thành viên cùng mô tả luồng đặt vé — dùng khác từ cho cùng khái niệm nghĩa là chưa xong.
```
Thuật ngữ | Định nghĩa | Dễ nhầm với | Khác ở chỗ
_________ | __________ | ____________ | ___________
```
**Đi vào:** Phụ lục (Bảng thuật ngữ), dùng nhất quán xuyên suốt

### 🔴 B3 — Mô hình quy trình nghiệp vụ
**Đầu vào:** B1, B2
**Phương pháp:** Biểu đồ hoạt động có swimlane cho 4 quy trình. **Chưa nhắc bất kỳ tên công nghệ nào ở bước này.**
**Phép thử:** mỗi quy trình có ít nhất một nhánh rẽ sang thất bại. Không có thì chưa xong.
```
Quy trình 1: ______  Quy trình 2: ______
Quy trình 3: ______  Quy trình 4: ______
Nhánh thất bại của từng quy trình: _____________________
```
**Đi vào:** phần Phân tích nghiệp vụ

### 🔴 B4 — Bản đồ sự kiện miền
**Đầu vào:** B2, B3
**Phương pháp:** lập dòng thời gian sự kiện miền cho các luồng cốt lõi, tham khảo Event Storming: sự kiện đã xảy ra → lệnh/tác nhân → chính sách → điểm chưa thống nhất. Có thể làm bằng bảng hoặc sơ đồ đơn giản; chỉ đưa vào báo cáo nếu nó giúp giải thích ranh giới tốt hơn biểu đồ hoạt động.
**Phép thử:** mọi điểm nóng đã được giải quyết hoặc chuyển vào danh sách rủi ro.
```
Chuỗi sự kiện theo thời gian: __________________________
Các lệnh tương ứng: ____________________________________
Chính sách (khi X thì Y): ______________________________
Điểm nóng chưa thống nhất: _____________________________
```
**Đi vào:** phần Phân tích miền nghiệp vụ nếu sơ đồ tạo thêm giá trị

### 🔴 B5 — Bản đồ Bounded Context *(bạn quyết định)*
**Đầu vào:** B4
**Phương pháp:** gom cụm các sự kiện gắn kết chặt → mỗi cụm là một context ứng viên. Phân loại mỗi context: cốt lõi / hỗ trợ / chung. Sau đó **gộp** để giữ trong hai trần dưới đây, mỗi lần gộp ghi một lý do.
**Phép thử — hai trần dự án đã chốt:**
- Số **service nghiệp vụ** sau khi gộp **≤ 8**.
- Số **luồng giao dịch xuyên service (Saga) ≤ 3**.
- Context nào được phân loại "cốt lõi" phải nằm trong vòng 1 phạm vi ở Tầng A
> Một bounded context **không nhất thiết phải là một tiến trình triển khai riêng.** Hai context có thể ở chung một service dạng module tách bạch — khi đó tính là 1 service nhưng vẫn là 2 context trong tài liệu. Mỗi lần làm vậy ghi một ADR. Xem B5.5 mục 3.4.
```
Context | Phân loại (cốt lõi/hỗ trợ/chung) | Sự kiện thuộc về
_______ | _________________________________ | ________________
Các lần gộp | Lý do gộp
___________ | _________
```
**Đi vào:** phần Phân tích và làm căn cứ cho phần Thiết kế

---

## NHÓM 2 — ĐẶC TẢ YÊU CẦU *(chủ đạo: OOAD)*

### 🔴 B6 — Use Case + đặc tả
**Đầu vào:** B3, B4
**Phương pháp:** từ biểu đồ hoạt động trích ra tương tác tác nhân–hệ thống. Đặc tả đầy đủ các ca cốt lõi và ca có bất biến/luồng lỗi đáng chú ý; ca CRUD phụ trợ có thể chỉ liệt kê. Mobile không tạo bộ use case nghiệp vụ riêng nếu chỉ là một kênh giao diện; “check-in vé trực tuyến” là cùng một use case với actor/điều kiện thiết bị tương ứng.
**Phép thử:** mọi use case truy vết được về ít nhất một sự kiện miền ở B4. Không truy vết được thì hoặc use case thừa, hoặc B4 còn thiếu sự kiện.
```
Mã UC | Tên | Tác nhân | Sự kiện miền tương ứng | Có đặc tả đầy đủ?
_____ | ___ | ________ | ______________________ | _________________
```
**Đi vào:** phần Phân tích yêu cầu

### 🔴 B7 — Mô hình miền theo context + xác định Aggregate *(bạn quyết định phần Aggregate)*
**Đầu vào:** B2, B5
**Phương pháp:** biểu đồ lớp **mức phân tích** — chỉ tên lớp, thuộc tính chính, quan hệ, bội số. Không kiểu dữ liệu, không phương thức. **Vẽ riêng cho từng context.** Sau đó xác định aggregate và aggregate root trong mỗi context.
**Phép thử:** mọi lớp có tên xuất hiện trong B2. Với mỗi aggregate, trả lời được: *"thay đổi bên trong nó có nằm gọn trong một giao dịch không?"* — nếu không thì ranh giới aggregate đang sai.
```
Context: ______
 Lớp: ______  Aggregate root: ______
 Bất biến phải giữ trong aggregate: ______
 Thay đổi vượt ra ngoài aggregate này: ______ → cần nhất quán cuối cùng
```
**Đi vào:** phần Phân tích/mô hình miền · **đầu vào then chốt cho B11**

### 🔴 B8 — Bảng yêu cầu FR / NFR
**Đầu vào:** B6, B1
**Phương pháp:** FR xuất phát từ use case; NFR xuất phát từ vấn đề/mục tiêu ở Tầng A. Gán mã cho yêu cầu quan trọng để liên kết với thiết kế và test bằng tìm kiếm văn bản; không cần duy trì ma trận truy vết đầy đủ cho mọi CRUD.
**Phép thử:** NFR chưa cần con số ở bước này, nhưng phải nói rõ **sẽ đo bằng gì** — sang B9 mới gắn số.
```
Mã | Loại (FR/NFR) | Nội dung | Nguồn gốc
___ | _____________ | ________ | _________
```
**Đi vào:** phần Yêu cầu

---

## NHÓM 3 — MỤC TIÊU CHẤT LƯỢNG *(chủ đạo: Kịch bản chất lượng)*

### 🔴 B9 — Bộ kịch bản chất lượng ✍️ *(phiếu điền mẫu)*
**Đầu vào:** B8, phiếu A3 của Tầng A
**Phương pháp:** với mỗi NFR quan trọng, viết một kịch bản chất lượng gọn. Kịch bản hiệu năng dùng số đo; bất biến dùng điều kiện đúng/sai; khả năng hỗ trợ chẩn đoán có thể dùng thời gian, tỉ lệ xác định đúng hoặc thang đánh giá được định nghĩa trước.
**Phép thử:** mỗi kịch bản có tiêu chí quan sát được và cách thu bằng chứng. Không ép mọi tiêu chí thành số nếu số đó không có ý nghĩa.

---

> **BẢN ĐIỀN MẪU — cho thấy hình dạng, con số bạn tự chốt sau thí nghiệm sơ bộ**
>
> **QS-01 — Chống bán vượt khi tranh chấp cao**
>
> | Thành phần | Nội dung |
> |---|---|
> | Nguồn kích thích | Nhiều người dùng cuối truy cập đồng thời |
> | Kích thích | N yêu cầu giữ chỗ cùng nhắm vào lượng vé còn lại nhỏ hơn N, phát sinh trong cùng một khoảng thời gian rất ngắn |
> | Tạo tác | Thành phần chịu trách nhiệm giữ chỗ và kiểm soát tồn kho vé |
> | Môi trường | Thời điểm mở bán, hệ thống ở mức tải cao nhất |
> | Phản ứng | Số yêu cầu được chấp nhận đúng bằng lượng vé còn lại; các yêu cầu còn lại nhận phản hồi từ chối rõ ràng, không bị treo |
> | **Độ đo phản ứng** | (1) Sai lệch giữa *số vé đã phát hành* và *số vé tồn kho* bằng **0**, kiểm tra bằng truy vấn CSDL sau khi chạy<br>(2) Độ trễ phân vị 95 của thao tác giữ chỗ ≤ **___ ms**<br>(3) Tỉ lệ yêu cầu lỗi hệ thống (khác với từ chối hợp lệ) ≤ **___ %** |
>
> **Ghi chú về cách điền:** ô (1) là **bất biến**, luôn bằng 0, không phụ thuộc đo đạc — đây là ràng buộc đúng/sai. Ô (2) và (3) là **ngưỡng hiệu năng**, phải chạy thử một vòng trên EC2 rồi mới chốt được con số hợp lý; đặt con số trước khi thử sẽ hoặc quá dễ hoặc bất khả thi.

---

```
Ô trống cho các kịch bản còn lại:
QS-02 (___): Nguồn ___ | Kích thích ___ | Tạo tác ___
             Môi trường ___ | Phản ứng ___ | Độ đo ___
QS-03 (___): ...
QS-04 (___): ...
```
**Đi vào:** cuối phần Yêu cầu hoặc đầu phần Kiến trúc · **ràng buộc lên phần Đánh giá**

### 🔴 B10 — Bảng ưu tiên kịch bản + danh sách ASR *(bạn quyết định)*
**Đầu vào:** B9
**Phương pháp:** dùng một bảng phẳng, xếp `Cao/Trung bình/Thấp` theo tác động tới trục nghiên cứu và nêu lý do. ASR gồm các kịch bản ưu tiên cao cùng các ràng buộc đã chốt (hai trần phạm vi, quyền sở hữu dữ liệu, quyền chỉ đọc của trợ lý).
**Phép thử:** chỉ giữ mức Cao cho kịch bản thực sự làm thay đổi kiến trúc hoặc cần thực nghiệm riêng; không có hạn mức hình thức 4–5 mục.
```
Kịch bản | Mức ưu tiên | Lý do | Cách kiểm chứng
________ | ___________ | _____ | _______________
Danh sách ASR: _________________________________
```
**Vì sao phiếu này quan trọng:** nó là cơ chế chống "kiến trúc theo mốt". Sau này ai đề xuất thêm công nghệ gì, câu hỏi kiểm tra là *"nó phục vụ ASR nào?"* — không trả lời được thì không thêm.
**Đi vào:** mở đầu phần Kiến trúc

---

## NHÓM 4 — THIẾT KẾ *(tổng hợp + phần bổ sung ở mục 3.3)*

### 🔴 B11 — Tập quyết định kiến trúc (ADR) *(bạn quyết định — phiếu quan trọng nhất)*
**Đầu vào:** B5 (ranh giới), B7 (aggregate), B10 (ASR)
**Phương pháp:** mỗi quyết định kiến trúc có phương án cạnh tranh hoặc hệ quả dài hạn mới cần một bản ghi riêng. Không đặt chỉ tiêu số lượng ADR.
**Phép thử:** mỗi ADR nêu được (a) các phương án thực sự đã cân nhắc, (b) yêu cầu/kịch bản nào nó phục vụ, (c) sẽ kiểm chứng bằng cách nào. Không tạo phương án giả chỉ để đủ biểu mẫu.
```
Nhóm quyết định cần có ADR (điền tên và mã):
 Kiểu kiến trúc tổng thể: ADR-___
 Phân rã service: ADR-___
 Giao thức giao tiếp giữa service: ADR-___
 Cơ chế chống bán vượt: ADR-___
 Quản lý giao dịch xuyên service: ADR-___
 Phát tán sự kiện tin cậy: ADR-___
 Bất biến khi lặp thao tác: ADR-___
 Điểm nối hệ thống con AI: ADR-___
```
**Đi vào:** phần Kiến trúc và giải thích quyết định

### 🔴 B12 — Bản đồ sở hữu dữ liệu + ERD từng service *(bạn quyết định)*
**Đầu vào:** B5, B7, B11 và lược đồ hiện tại
**Phương pháp:** (1) kiểm kê bảng hiện tại; (2) gán service sở hữu; (3) quyết định giữ/sửa/tách/bỏ/viết mới; (4) vẽ ERD riêng cho từng schema/CSDL; (5) xác định dữ liệu xuyên service lấy qua API hay bản sao sự kiện. Các bảng AI chỉ chốt sau khi workflow B16–B18 rõ.
**Phép thử:** mỗi service có schema/CSDL và credential độc lập; không khóa ngoại, `JOIN`, repository hoặc truy vấn trực tiếp xuyên ranh giới. Biểu đồ lớp theo context và ERD của service phải dùng nhất quán cùng thuật ngữ, nhưng không bắt buộc ánh xạ một-một.
```
Thực thể/bảng hiện tại | Service sở hữu | Xử lý (giữ/sửa/tách/bỏ/mới) | Service cần bản sao | API/sự kiện đồng bộ
_____________________ | _______________ | ___________________________ | ___________________ | _________________
ERD từng service: ______________________________________
Độ trễ nhất quán chấp nhận được: _______________________
```
**Đi vào:** phần Thiết kế cơ sở dữ liệu

### 🔴 B13 — Hợp đồng API và Event *(bạn quyết định)*
**Đầu vào:** B5, B11
**Phương pháp:** đặc tả API đồng bộ + lược đồ sự kiện. **Chốt trước khi chia việc code.**
**Phép thử:** không có vòng lặp phụ thuộc đồng bộ (A gọi B, B gọi A). Có thì thiết kế sai, quay lại B5.
```
API đồng bộ: ______  Sự kiện bất đồng bộ: ______
Ma trận giao tiếp service × service: ____________________
Quy ước đặt tên và phiên bản: __________________________
```
**Đi vào:** phần Thiết kế + Phụ lục nếu đặc tả dài

### 🔴 B14 — Biểu đồ tuần tự các kịch bản
**Đầu vào:** B9, B11
**Phương pháp:** vẽ biểu đồ cho các luồng liên service quan trọng, đặc biệt mua vé/thanh toán/phát hành, check-in trực tuyến cạnh tranh và một số luồng thất bại/Saga. Không cần một biểu đồ cho mọi ASR nếu bảng hoặc test mô tả rõ hơn.
**Phép thử:** các Saga đã chốt có đường thành công, lỗi, thử lại/idempotency và bù trừ tương ứng; use case check-in cho thấy backend quyết định nguyên tử để ngăn quét trùng.
```
Kịch bản | Biểu đồ tuần tự | Đã có luồng thất bại?
________ | _______________ | ______________________
```
**Đi vào:** phần Thiết kế, ưu tiên các luồng cốt lõi

### 🔴 B15 — Kế hoạch kiểm chứng
**Đầu vào:** B9, B11
**Phương pháp:** với mỗi kịch bản ưu tiên cao, xác định test/thí nghiệm và dữ liệu cần lưu. Chỉ dựng bản đối chứng khi nó giúp trả lời một câu hỏi cụ thể và có thể cài đặt công bằng; không bắt buộc thêm feature flag cho mọi cơ chế.
**Phép thử:** nếu có đối chứng, đó phải là cách làm hợp lý và cùng điều kiện chạy. Nếu không có đối chứng, nêu rõ đây là kiểm chứng bất biến/đo đặc tính chứ không phải chứng minh giải pháp vượt trội.
```
Kịch bản | Thí nghiệm | Bản đối chứng (tắt gì) | Bất biến kiểm tra sau khi chạy
________ | __________ | ______________________ | ______________________________
```
**Đi vào:** phần Kế hoạch/Đánh giá

---

## NHÓM 5 — HỆ THỐNG CON TRỢ LÝ CHẨN ĐOÁN *(nhánh AI — thiết kế sau chuẩn logging)*

> Hướng đã chốt: **log có cấu trúc → Drain gom template → chọn ngữ cảnh liên quan → LLM API đưa ra tư vấn đơn giản**. Trợ lý chỉ đọc và không tự sửa hệ thống. Drain không phải mô hình học máy cần huấn luyện; “hiệu chỉnh” ở đây là chọn masking và một số tham số trên tập log nhỏ.

### 🔴 B16 — Chuẩn logging và thu thập *(bạn quyết định)*
**Đầu vào:** B11 (ranh giới service), B13 (hợp đồng)
**Phương pháp:** định nghĩa log JSON với tối thiểu `timestamp`, `service_name`, `environment`, `level`, `message/event_code`, `trace_id` hoặc `correlation_id`, `span_id` nếu có, `exception_type` và mã nghiệp vụ cần thiết. Xác định cách truyền trace qua REST/message, thu log và che bí mật/PII trước khi lưu hoặc gửi LLM. Không log token, mật khẩu, thông tin thanh toán đầy đủ hay nội dung cá nhân không cần thiết.
**Mốc chặn của nhánh AI:** chốt chuẩn này trước khi tách/viết các service mới và áp dụng trước hết cho những luồng được chọn để đánh giá. Code cũ được di trú dần; không giả định có thể quay ngược về “trước dòng code đầu tiên”.
**Phép thử:** với một trace/correlation ID của luồng thử, ghép lại được đường đi qua các service liên quan và không lộ dữ liệu nhạy cảm.
```
Trường bắt buộc mỗi dòng log: ______________________________
Định dạng: ______________  Nơi sinh mã tương quan: _________
Cách truyền qua REST: ______  Cách truyền qua message: ______
```
**Đi vào:** phần Thiết kế và Hiện thực trợ lý

### 🔴 B17 — Cấu hình và kiểm tra Drain
**Đầu vào:** B16 + hệ thống đã chạy sinh log thật
**Phương pháp:** tách metadata có cấu trúc khỏi trường `message`, mask các biến như UUID, số, URL hoặc ID, rồi đưa phần thông điệp vào Drain. Chọn cấu hình ban đầu từ tài liệu/thư viện; dùng một tập log phát triển nhỏ để chỉnh `depth`, ngưỡng tương đồng và quy tắc masking. Không cần huấn luyện mô hình hoặc quét tham số toàn diện.
**Kiểm tra:** tạo một tập log đại diện được gán nhãn thủ công trước khi xem kết quả cuối. Tập này chỉ đại diện cho các luồng/lỗi đã chọn, không phải toàn bộ template mà hệ thống có thể sinh ra.
```
Nguồn và phạm vi log: _________________________________
Quy tắc masking: ______________________________________
Cấu hình Drain: _______________________________________
Cách đối chiếu nhóm template: __________  Kết quả: _____
```
**Đi vào:** phần Đánh giá trợ lý

### 🔴 B18 — Tạo context và gọi LLM API
**Đầu vào:** B16, B17 và một yêu cầu chẩn đoán/sự kiện lỗi
**Phương pháp:** Drain chỉ làm **log parsing/template mining**, không chịu trách nhiệm quyết định dòng nào là sự cố. Tầng tạo context chọn các bản ghi liên quan theo `trace_id`/`correlation_id`, mã nghiệp vụ đã được phép dùng, service lân cận và cửa sổ thời gian; kèm template, exception/stack trace đã rút gọn và tín hiệu quan sát cần thiết. Sau khi khử dữ liệu nhạy cảm và giới hạn token, gửi context tới LLM API.

Đầu ra yêu cầu có cấu trúc: nguyên nhân khả dĩ, bằng chứng log hỗ trợ, các bước kiểm tra tiếp theo, mức chắc chắn/giới hạn. Không yêu cầu LLM tự sửa code, chạy lệnh hoặc ghi vào hệ thống nghiệp vụ.
```
Khóa gom context: __________________  Cửa sổ thời gian: ______
Quy tắc chọn/rút gọn log: __________________________________
Trường bị loại hoặc che trước LLM: _________________________
Định dạng đầu ra mong muốn: _________________________________
```
**Đi vào:** phần Thiết kế trợ lý · phần Đánh giá

### 🟡 B19 — Đánh giá trợ lý ở mức vừa sức
**Đầu vào:** B17, B18, hệ thống đã chạy
**Phương pháp:** chuẩn bị một số ca lỗi đã biết thuộc các lớp phù hợp với hệ thống (ví dụ exception ứng dụng, lỗi CSDL, timeout/tích hợp). Với mỗi ca, lưu nguyên nhân thật, log đầu vào và đầu ra trợ lý. Đánh giá tối thiểu: context có chứa bằng chứng cần thiết không, nhận định có chỉ đúng vùng/nguyên nhân không, gợi ý kiểm tra có hữu ích và có bịa hành động/bằng chứng không.

Nếu có đủ người và thời gian, đo thêm thời gian chẩn đoán thủ công so với có trợ lý; đây là phép đo cộng thêm, không bắt buộc phải thiết kế mù nghiêm ngặt như thí nghiệm quy mô lớn. Báo cáo rõ cỡ mẫu nhỏ và không suy rộng thống kê.
```
Các ca lỗi và nguyên nhân thật: ______________________________
Tiêu chí chấm câu trả lời: __________________________________
Kết quả từng ca, gồm cả ca thất bại: _________________________
Thời gian có/không trợ lý (nếu đo): __________________________
```
**Đi vào:** phần Đánh giá — đây là bằng chứng cho phạm vi trợ lý AI, không phải tuyên bố chất lượng chung của LLM

---

# PHẦN 5 — PHÂN BỔ THỜI GIAN VÀ KIỂM SOÁT RỦI RO

## 5.1 Phân bổ tham chiếu (điều chỉnh theo lịch thực tế của nhóm)

Bản phân bổ này đã **hiệu chỉnh theo điều kiện nhóm có công cụ AI** (xem B5.5 Phần 3B): tăng phần thiết kế, giảm phần xây dựng, **giữ nguyên phần kiểm chứng** vì nó bị chặn bởi thời gian thực chứ không phải bởi năng suất.

| Giai đoạn | Phiếu | Thời lượng | Ghi chú kiểm soát |
|---|---|---|---|
| Nhóm 1 — Khai phá miền | B1–B5 | ~3 tuần | Khảo sát công khai ở mức đủ làm căn cứ, không chạy theo độ bao phủ không thể đạt |
| Nhóm 2 — Đặc tả yêu cầu | B6–B8 | ~2 tuần | |
| Nhóm 3 — Mục tiêu chất lượng | B9–B10 | ~1 tuần | Ngắn nhưng không bỏ qua — quyết định phần Đánh giá thu bằng chứng gì |
| Nhóm 4 — Thiết kế | B11–B15 | ~4 tuần | **Chạy chồng lấn với xây dựng từ tuần thứ 2 của nhóm này** |
| Xây dựng | — | ~5 tuần | Ưu tiên một lát cắt đầu-cuối chạy được sớm; mốc cụ thể theo kế hoạch tuần của nhóm |
| Kiểm chứng | — | ~3 tuần | Không dồn vào tuần cuối; bị chặn bởi thời gian thực |
| Hoàn thiện quyển | — | ~1 tuần | Dự phòng |

Các con số trên là tỷ lệ tham khảo, không phải quy trình chấm công. Nhóm cập nhật theo đầu ra hoàn thành và lịch giảng viên; không thay đổi trần service/Saga hoặc bỏ kiểm chứng chỉ để khớp con số tuần.

**Nguyên tắc chống trượt tiến độ quan trọng nhất:** thiết kế và xây dựng **phải chồng lấn**. Cách chồng lấn an toàn là theo từng bounded context — thiết kế xong context nào thì code context đó; context chưa thiết kế xong thì chưa code. **Không phải** code trước rồi vẽ sơ đồ bù sau.

## 5.2 Bảng kiểm soát rủi ro

| # | Rủi ro | Dấu hiệu nhận biết sớm | Biện pháp đã tích hợp vào quy trình |
|---|---|---|---|
| 1 | **Thiết kế không được kiểm tra bằng hiện thực sớm** | Chưa có lát cắt đầu-cuối chạy được khi các hợp đồng chính đã chốt | Dựng một luồng mỏng qua các thành phần chính và triển khai thử; cập nhật thiết kế từ lỗi tích hợp quan sát được |
| 2 | **Vượt phạm vi kiến trúc đã chốt** | > 8 service nghiệp vụ hoặc > 3 Saga | Gộp/rút lại ranh giới trước khi hiện thực; hai trần đều là ràng buộc dự án |
| 3 | **Tài liệu phương pháp lấn át nội dung đồ án** | Báo cáo nói nhiều về framework hơn vấn đề và kết quả | DSRM/DDD/ATAM chỉ dùng ở mức cần thiết; số chương và biểu đồ theo mẫu ĐATN được xác nhận |
| 4 | **Tuyên bố quá mức về phương pháp** | — | Bảng quy ước diễn đạt ở Tầng C mục 3.4 |
| 5 | **Ba người làm lệch nhau, không tích hợp được** | Chưa chốt hợp đồng mà đã code | Phiếu B13 phải xong trước khi chia việc code |
| 6 | **Mất dấu lý do quyết định** | Không giải thích được một ranh giới/cơ chế cốt lõi | Viết ADR ngắn ngay khi chốt quyết định lớn; cập nhật bảng liên kết tối thiểu sau khi chốt kiến trúc và sau đánh giá |
| 7 | **Sinh ra mã không giải thích được** | Không trả lời được câu hỏi về đoạn mã trong phần mình phụ trách | PTIT chấm cá nhân và hỏi theo phần — đây là rủi ro điểm trực tiếp. Nguyên tắc Tầng A mục 8 mở rộng sang cả mã nguồn |
| 8 | **Thiết kế nghe hợp lý nhưng sai tinh vi** | Ranh giới aggregate "chạy được" cho tới khi gặp tranh chấp thật | Trường *"Các phương án đã cân nhắc"* trong ADR phải do chính người quyết định tự viết |
| 9 | **Vẽ sơ đồ trở thành nút thắt** | Ngại đổi thiết kế vì "vẽ lại mệt" | Nháp bằng sơ đồ dạng mã, chỉ dựng bản cuối trên Visual Paradigm (Tầng C mục 3.1) |
| 10 | **CI/CD lấn phạm vi nghiên cứu** | Mục tiêu/ASR tập trung vào pipeline thay vì nghiệp vụ và độ tin cậy | Xem CI/CD là hạ tầng hỗ trợ; thực hiện nếu hữu ích, không dùng làm điều kiện nghiệm thu trục chính |

---

# PHẦN 6 — TẦNG B RÀNG BUỘC GÌ LÊN TẦNG C

| Ràng buộc từ Tầng B | Tầng C phải có |
|---|---|
| Biểu đồ lớp vẽ theo context, không vẽ tổng thể | Quy ước đặt tên file và tiêu đề biểu đồ theo context |
| Mỗi quyết định kiến trúc quan trọng có một bản ghi | Mẫu ADR có trạng thái và "thay thế bởi" khi cần |
| Kịch bản chất lượng sáu phần | Mẫu phiếu kịch bản |
| Liên kết yêu cầu–quyết định–kiểm chứng cho các mục cốt lõi | Bảng Markdown rút gọn, cập nhật ở hai mốc chính |
| Bỏ biểu đồ thành phần UML, dùng C4 | Quy ước phân biệt rõ C4 với UML, tránh trộn ký hiệu |
| Bản đồ Bounded Context vẽ bằng biểu đồ gói | Quy ước ký hiệu cho bản đồ context |
| Hợp đồng API/Event chốt trước khi code | Quy ước đặt tên endpoint, tên sự kiện, đánh phiên bản |
| Thí nghiệm ghi rõ cấu hình; đối chứng chỉ khi có ý nghĩa | Mẫu báo cáo thí nghiệm linh hoạt |
| Diễn đạt không tuyên bố quá mức | Danh sách cụm từ chuẩn: "tham chiếu cấu trúc của…", "áp dụng trong phạm vi nhóm" |

---

## Nhật ký sửa đổi

| Ngày | Sửa gì | Lý do |
|---|---|---|
| 2026-08-07 | Bản đầu | — |
| 2026-08-08 | Giản lược ATAM/Event Storming/truy vết; thêm khảo sát công khai, schema độc lập, mobile trực tuyến và pipeline Drain→context→LLM | Phù hợp mức ĐATN đại học và quyết định mới |

---

## Tài liệu tham chiếu

- Evans, E. (2003). *Domain-Driven Design: Tackling Complexity in the Heart of Software.*
- Richardson, C. *Microservices Patterns* — mẫu phân rã theo năng lực nghiệp vụ và theo miền con.
- Kazman, R., Klein, M., & Clements, P. (2000). *ATAM: Method for Architecture Evaluation.* SEI Technical Report — kịch bản chất lượng, cây tiện ích, phân loại rủi ro/điểm nhạy cảm/điểm đánh đổi.
- Bass, L., Clements, P., & Kazman, R. *Software Architecture in Practice* — thuộc tính chất lượng và kịch bản tổng quát.
- Brandolini, A. *Introducing EventStorming.*
- Giáo trình phân tích thiết kế hệ thống thông tin hướng đối tượng (chuẩn giảng dạy tại các trường kỹ thuật Việt Nam) — trình tự pha phân tích và pha thiết kế.
