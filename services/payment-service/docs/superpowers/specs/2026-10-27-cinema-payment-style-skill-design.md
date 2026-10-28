# Thiết kế skill cinema-payment-style

Ngày: 2026-10-27

Trạng thái: Người dùng đã duyệt thiết kế, gồm tiêu chí clean code

Phạm vi: `services/payment-service`

## Mục đích

Tạo skill để khi thêm hoặc sửa Java trong payment-service, Codex viết class và dùng annotation theo phong cách của `C:/Users/WEOHWOEH/Documents/PersionalProject/Cinema-Booking-System`, đồng thời lập kế hoạch trước khi code theo workflow brainstorming.

Người dùng đã chọn hướng 1: áp dụng phong cách Cinema vào cấu trúc hiện có của payment-service, bổ sung yêu cầu clean code và xác nhận duyệt thiết kế bằng phản hồi "oke".

## Các hướng đã cân nhắc

| Hướng | Đánh đổi | Quyết định |
| --- | --- | --- |
| Áp dụng cách viết class, annotation và workflow; giữ kiến trúc payment-service | Phong cách gần Cinema nhưng vẫn có khác biệt do hợp đồng và cấu hình hiện có | Người dùng chọn hướng 1 |
| Đồng bộ cả package, ApiResponse và hệ thống base entity của Cinema | Giống Cinema hơn nhưng cần thay đổi kiến trúc, database và hợp đồng API | Không chọn |

## Căn cứ từ code đã đọc

Các đường dẫn Cinema dưới đây tính từ `src/main/java/org/example/cinemaBooking/`.

| Nguồn Cinema | Quy ước quan sát được | Cách áp dụng cho payment-service |
| --- | --- | --- |
| `Controller/PaymentController.java` | `@RestController`, `@RequiredArgsConstructor`, `@FieldDefaults`, `@Slf4j`, Swagger, `@Valid`, phân quyền | Constructor injection, controller điều phối mỏng; logging, validation và tài liệu API theo nhu cầu thực tế |
| `Service/Payment/PaymentService.java`, `Service/Movie/CategoryService.java` | Service là class cụ thể, Lombok cho dependency, mapper riêng, exception theo mã lỗi, transaction ở thao tác cần thiết | Ưu tiên hình thức này khi viết mới; giữ transaction boundary và các thành phần hiện có |
| `Entity/Payment.java` | JPA, `@Getter`, `@Setter`, constructor, `@Builder`, enum dạng chuỗi, quan hệ lazy | Chọn annotation phù hợp vòng đời entity; bảo toàn mapping và factory đang bảo vệ invariant |
| `DTO/Request/Payment/CreatePaymentRequest.java`, `DTO/Request/Product/CreateProductRequest.java` | DTO dùng `record`, validation trên component, mã validation có ý nghĩa | Giữ DTO immutable và các quy tắc validation của contract hiện tại |
| `Mapper/PaymentMapper.java` | MapStruct Spring bean, `@Mapping` tường minh cho field đổi tên hoặc bỏ qua | Tái sử dụng `@Mapper(config = PaymentMapperConfig.class)` và chính sách mapping hiện có |
| `Repository/PaymentRepository.java` | Spring Data JPA, `@Query`, `@Param`, `Optional` | Dùng repository đúng tầng; query/fetch theo nhu cầu, giữ các ràng buộc giao dịch |

Payment-service hiện dùng Java 21, Spring Boot 4.1.1, MapStruct 1.6.3 và Spotless với Google Java Format. Các phiên bản này là thông tin từ `pom.xml` tại lúc khảo sát, không phải giá trị cố định để skill ép các lần sửa sau.

## Nội dung skill

- Hướng dẫn chính bằng tiếng Việt; tên class, package và annotation giữ nguyên theo Java.
- Phạm vi kích hoạt: thêm, sửa hoặc review Java trong payment-service, đặc biệt Controller, Service, Entity, DTO, Mapper và Repository.
- Ưu tiên `@RequiredArgsConstructor` và `@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)` cho dependency injection khi class không có yêu cầu constructor đặc biệt. Giải thích cách viết field tương ứng để tránh trùng hoặc sai constructor.
- Chọn annotation theo vai trò class: không thêm `@Slf4j` khi không log; không áp `makeFinal = true` cho state JPA; không thay factory hoặc constructor bảo vệ invariant bằng builder tùy ý.
- Swagger và `@PreAuthorize` có điều kiện: kiểm tra dependency và cơ chế bảo mật thực tế trước khi dùng. `@SecurityRequirement` chỉ mô tả API, không thực thi phân quyền.
- Chỉ ra điểm khác biệt cần giữ: package chữ thường, UUID/Instant, schema và precision tiền, response/Problem hiện có, cấu hình mapper, formatter và bảo mật payment-service.
- Ví dụ từ Cinema được ghi nhận là bằng chứng về phong cách; ví dụ chuyển sang payment-service phải dùng đúng class và contract đích, không sao chép nghiệp vụ Cinema.

## Tiêu chí clean code

Đây là yêu cầu bổ sung trực tiếp của người dùng, áp dụng cùng các quy ước phong cách Cinema.

- Logic xử lý lặp lại cùng một trách nhiệm hoặc quy tắc nghiệp vụ từ hai nơi trở lên phải được tách thành một hàm dùng chung và các nơi đó gọi lại hàm này. Đọc các helper hiện có trước khi viết helper mới.
- Nếu logic lặp trong cùng class, ưu tiên một hàm `private` có tên diễn đạt rõ mục đích. Nếu nhiều class cùng cần một quy tắc, đặt hàm trong component hoặc service đúng trách nhiệm, được inject khi cần; không gom các nghiệp vụ không liên quan vào một lớp util chung.
- Mỗi hàm có một trách nhiệm rõ ràng. Tách các bước validation, tra cứu dữ liệu, tính toán, mapping và cập nhật trạng thái khi chúng tạo thành công việc độc lập có tên có ý nghĩa; hàm điều phối phải đọc được theo trình tự nghiệp vụ.
- Đặt tên class, hàm, biến và tham số theo ý nghĩa nghiệp vụ; dùng guard clause để giảm lồng nhánh và hằng số hoặc enum cho giá trị nghiệp vụ lặp lại. Comment giải thích lý do hoặc ràng buộc khó thấy, thay vì diễn giải lại câu lệnh.
- Khi trích xuất hàm, giữ nguyên kết quả, loại exception, thứ tự và số lần gọi có tác dụng phụ, phạm vi transaction và cách annotation có hiệu lực. Không tự gộp các thao tác lưu dữ liệu, gửi message hoặc gọi gateway chỉ vì chúng có hình thức gần giống nhau.
- Với các đoạn code chỉ giống cú pháp nhưng có quy tắc và lý do thay đổi khác nhau, giữ riêng; chỉ trích xuất phần thực sự chung. Hàm dùng chung cần tên và tham số rõ nghĩa, không dùng nhiều cờ boolean để phục vụ các luồng không liên quan.
- Plan phải nêu helper nào sẽ tái sử dụng hoặc trích xuất, các vị trí gọi và cách kiểm tra hành vi sau khi tách. Review cuối phải rà soát logic lặp trong phần code thay đổi.

## Workflow bắt buộc trước khi code

1. Đọc yêu cầu, hướng dẫn dự án và phần code liên quan; dùng CodeGraph trước khi tìm/đọc code ở dự án có index.
2. Làm rõ điểm thiếu ảnh hưởng đến hành vi; tái sử dụng các quyết định người dùng đã chốt.
3. Với chức năng mới hoặc thay đổi hành vi, đề xuất các hướng phù hợp, chốt hướng và trình thiết kế theo quy mô công việc để người dùng duyệt.
4. Viết plan cụ thể gồm file/class tác động, annotation dự định dùng và lý do, helper sẽ tái sử dụng hoặc trích xuất để loại bỏ logic lặp, thay đổi contract/database nếu có, cùng cách kiểm tra. Nếu dùng superpowers có sẵn, chuyển sang `superpowers:writing-plans` sau khi thiết kế được duyệt; nếu thiếu, skill vẫn phải thực hiện được bằng hướng dẫn nội tại.
5. Chờ duyệt plan trước khi code, trừ khi người dùng đã duyệt đúng plan đó hoặc yêu cầu rõ bỏ bước hỏi. Yêu cầu chung như "làm nhanh" không tự coi là duyệt một plan chưa trình bày.
6. Thực hiện trong phạm vi đã duyệt; nếu thay đổi phạm vi đáng kể thì cập nhật plan và xin chốt phần thay đổi.
7. Chạy kiểm tra phù hợp, gồm formatter/build hoặc test liên quan khi sửa Java; báo kết quả thực tế và các giới hạn còn lại.

## File dự kiến tạo hoặc sửa

- `.agents/skills/cinema-payment-style/SKILL.md`: hướng dẫn chính, workflow và bảng chọn annotation.
- `.agents/skills/cinema-payment-style/references/cinema-conventions.md`: nguồn minh chứng và ví dụ chuyển phong cách sang payment-service, đủ dùng khi không mở được dự án Cinema.
- `AGENTS.md`: thêm một mục ngắn trỏ tới skill và nêu khi phải đọc, giữ nguyên phần CodeGraph. Đây là đề xuất để các phiên làm việc trong service tìm được hướng dẫn local.
- Plan triển khai skill sẽ nằm trong `docs/superpowers/plans/` sau khi thiết kế được duyệt.

Vị trí, cách chia hướng dẫn/tài liệu tham chiếu và mục liên kết trong AGENTS.md là các lựa chọn triển khai do Codex đề xuất, chưa phải yêu cầu riêng của người dùng.

## Ngoài phạm vi

Lần này chỉ tạo skill, tài liệu đi kèm và liên kết hướng dẫn. Không refactor Java hiện có, đổi dependency, sao chép ApiResponse/base entity, đổi schema/API, hoặc cài skill vào cấu hình toàn máy.

## Tiêu chí kiểm tra

- Frontmatter, tên skill và link tham chiếu hợp lệ; không còn placeholder.
- Quy ước annotation có căn cứ từ source Cinema và điều kiện tương thích với payment-service.
- Thử tình huống thêm service, sửa entity và thêm endpoint để kiểm tra skill yêu cầu plan trước code, chọn annotation đúng vai trò và giữ contract đích.
- Thử tình huống hai luồng có cùng logic validation hoặc tính toán: plan phải chỉ ra một hàm dùng chung và các vị trí gọi; kết quả áp dụng phải loại bỏ logic trùng, giữ hành vi và tác dụng phụ của từng luồng.
- Thử tình huống hai đoạn giống cú pháp nhưng khác quy tắc nghiệp vụ để bảo đảm skill không ép gộp thành helper thiếu rõ ràng.
- Review ví dụ theo trách nhiệm của hàm, cách đặt tên, độ lồng nhánh và việc tái sử dụng helper hiện có.
- Kiểm tra một tình huống không có sẵn source Cinema để bảo đảm tài liệu tham chiếu đủ dùng.
- Kiểm tra diff để xác nhận chỉ các file hướng dẫn nằm trong phạm vi được thay đổi; không cần chạy toàn bộ test Java khi chưa sửa code hoặc build config.

## Bước tiếp theo

Thiết kế và plan đã được người dùng duyệt; skill đã được tạo và kiểm tra. Chi tiết các bước và kết quả nằm trong [plan triển khai](../plans/2026-10-27-cinema-payment-style-skill.md).
