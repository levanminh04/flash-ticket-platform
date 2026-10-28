# Payment Cinema Style Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Áp dụng skill Cinema vào code payment-service: đồng nhất khai báo dependency/class phù hợp và tách logic validation đang lặp, giữ nguyên hành vi.

**Architecture:** Dùng Lombok cho constructor injection đơn giản; giữ constructor có validation hoặc khởi tạo đặc biệt. Tách phép chọn đường dẫn vi phạm Bean Validation đầu tiên thành helper thuần, dùng lại ở ba boundary hiện tại; từng boundary vẫn quyết định null handling, prefix field và loại lỗi của mình.

**Tech Stack:** Java 21, Spring Boot, Lombok, Jakarta Validation, Jackson 3, MapStruct, JUnit/AssertJ, Maven Wrapper, Spotless và SpotBugs đang có trong dự án.

**Spec:** Yêu cầu người dùng "áp dụng sửa lại code trong payment", [cinema-payment-style](../../../.agents/skills/cinema-payment-style/SKILL.md) và [quy ước Cinema](../../../.agents/skills/cinema-payment-style/references/cinema-conventions.md). Đây là refactor giữ hành vi theo phong cách đã chọn; không thiết kế chức năng mới.

**Trạng thái:** Đã triển khai, kiểm tra đầy đủ suite mặc định và review độc lập.

## Global Constraints

- Toàn bộ đường dẫn Java dưới đây tính từ `src/main/java/com/flashticket/payment_service/`; đường dẫn test tính từ `src/test/java/com/flashticket/payment_service/`.
- Chỉ sửa payment-service; giữ nguyên các file skill/tài liệu đã tạo và mọi thay đổi sẵn có của người dùng.
- Không đổi API, DTO/message, mã lỗi, null handling, canonical bytes/hash, schema, kiểu tiền, query, transaction/isolation, quyền truy cập hay profile/conditional bean.
- Không thêm dependency, Swagger hoặc cơ chế method security chỉ để giống Cinema. Không sửa cấu hình build để bỏ quality gate.
- Giữ factory, constructor protected, mapping và quyền setter của JPA entity; DTO record và MapStruct config hiện có tiếp tục được dùng.
- Chỉ trích xuất phần thực sự chung; không gộp logic khác nghiệp vụ hoặc gom vào CommonUtils.
- Giữ tên, kiểu, thứ tự dependency và chữ ký constructor được test/caller dùng. Constructor có requireNonNull, cấu hình mapper hoặc tạo collaborator phải giữ nguyên logic và semantics.
- Dùng JDK 21 đã có tại `C:/Users/WEOHWOEH/.jdks/ms-21.0.12.1`; Java mặc định trên PATH hiện là 17 nên phải chọn đúng JDK cho tiến trình build. Dùng UTC theo hướng dẫn dự án.

## Review Focus

1. Constructor Lombok phải giữ đúng dependencies và cách Spring tạo bean; smoke/controller/mapper/config tests phải tiếp tục chạy.
2. Có nhiều constraint sai cùng lúc: vẫn chọn field theo thứ tự từ điển; helper không phụ thuộc thứ tự Set.
3. Prefix khác nhau giữa boundary: codec/serializer giữ đường dẫn tương đối, purchase validator giữ `$.`; null giữ nguyên mã hiện tại.
4. Serialization/hashing: golden canonical bytes và hash fixtures không thay đổi; helper không dùng mapper của ứng dụng để canonicalize.
5. Callback và simulator: giữ điều kiện bật bean/profile, ACK và tác dụng phụ; integration test bị loại khỏi suite mặc định không được báo là đã chạy.

## Những gì đã đối chiếu

- `PaymentQueryService`, `StartPaymentProcessor`, `StartPaymentStore`, `VnpayCallbackService` và các controller/handler/mapper liên quan đã được khảo sát qua CodeGraph.
- `ContractCodec.validate`, `CanonicalContractSerializer.validatedTree`, `PurchaseSnapshotValidator.validate` đang cùng dùng `stream().min(Comparator.comparing(propertyPath))` để chọn vi phạm đầu tiên. Prefix/null/error tại caller có khác nhau nên chỉ chia sẻ phép chọn path.
- Các helper `unavailable`, `record`, `bound`, `fail`, JSON codec và `VnpayWireFormat` đã có mục đích riêng; không tạo thêm wrapper trùng chức năng của chúng.
- Các constructor của `ContractHashService`, `CanonicalContractSerializer`, `PurchaseSnapshotValidator`, `MessageInvariantValidator` có requireNonNull. `ContractCodec` còn dựng mapper strict riêng. Giữ constructor này, chỉ đồng nhất field defaults.
- `VnpayRefundClient` có thứ tự tham số khác thứ tự field; các gateway collaborator còn có khởi tạo đặc biệt. Không đổi constructor của nhóm này hàng loạt.

## Task 1: Ghi baseline trước refactor

**Files:** Chỉ đọc source/test/config; log build trong `target/` đã ignore.

**Interfaces:** Consumes code hiện tại và test suite của dự án. Produces kết quả baseline để phân biệt lỗi có sẵn với regression.

- [x] **Step 1:** Kiểm tra `git status`, hướng dẫn theo thư mục và source mới nhất bằng CodeGraph; bảo toàn các thay đổi người dùng.
- [x] **Step 2:** Chọn JDK 21 và UTC cho tiến trình build; chạy `.\mvnw.cmd verify -B -ntp` từ service, lưu output vào `target/cinema-refactor-baseline.log`.
- [x] **Step 3:** Đọc kết quả thực tế: số test, failures/errors, Spotless và SpotBugs. Nếu baseline lỗi, tìm nguyên nhân và ghi rõ trước khi quy lỗi cho refactor; không tắt gate hoặc tự sửa phần ngoài phạm vi.

## Task 2: Đồng nhất class và constructor injection

**Files — thêm FieldDefaults và dùng RequiredArgsConstructor hiện có hoặc thay constructor gán field đơn giản:**

- `controller/PaymentQueryController.java`
- `controller/VnpayCallbackController.java`
- `service/payment/PaymentQueryService.java`
- `service/start/StartPaymentProcessor.java`
- `service/start/StartPaymentStore.java`
- `service/vnpay/VnpayCallbackService.java`
- `exception/PaymentExceptionHandler.java`
- `mapper/MessageJsonMapper.java`
- `mapper/SnapshotJsonMapper.java`
- `security/PaymentSecurityProblemHandler.java`

**Files — thêm FieldDefaults nhưng giữ constructor có validation:**

- `hashing/ContractHashService.java`
- `hashing/CanonicalContractSerializer.java`
- `validation/PurchaseSnapshotValidator.java`
- `validation/MessageInvariantValidator.java`

**Interfaces:** Preserves tên class, Spring stereotype, dependency types/order, constructor visibility, public methods, conditional/profile annotations và transaction. Không tạo service hoặc interface mới.

- [x] **Step 1:** Với 10 class nhóm đầu, dùng `@RequiredArgsConstructor` và `@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)`; chuyển field dependency từ private final tường minh sang khai báo theo mẫu Cinema. Chỉ bỏ constructor viết tay ở hai callback class khi đã xác nhận chỉ gán các dependency tương ứng, không có annotation ở tham số hay logic khác.
- [x] **Step 2:** Với 4 class nhóm sau, thêm FieldDefaults phù hợp nhưng giữ requireNonNull và toàn bộ semantics constructor. Giữ static constant/mapper initializer, logger có dùng và các qualifier nếu có; không thêm logger không dùng.
- [x] **Step 3:** Compile và chạy các test sẵn có về tạo bean, config, mapper, query/controller, security và callback bằng Maven. Không thêm test chỉ đếm annotation; kết quả tạo bean và hành vi public mới là điều cần bảo toàn.

## Task 3: Tách phép chọn vi phạm validation đầu tiên

**Files:**

- Create `dto/common/ContractViolations.java`.
- Modify `dto/common/ContractCodec.java`, `hashing/CanonicalContractSerializer.java`, `validation/PurchaseSnapshotValidator.java`.
- Create test `dto/common/ContractViolationsTest.java`.
- Extend test `dto/ContractCodecTest.java`, `hashing/CanonicalContractSerializerTest.java`, `validation/PurchaseSnapshotValidatorTest.java`.

**Interfaces:** `ContractViolations` là final utility có constructor private và phương thức `public static Optional<String> firstPath(Set<? extends ConstraintViolation<?>> violations)`. Empty set trả Optional.empty; nonempty trả path nhỏ nhất theo String lexical order, không mutate set. Các caller tự xử lý mã lỗi/prefix/null như hiện tại. Helper không là Spring bean và không có side effect.

- [x] **Step 1:** Bổ sung characterization bằng Validator thật: object có nhiều field vi phạm phải chọn cùng path qua codec, canonical serializer và purchase validator. Kết quả mong đợi lần lượt là `buyerSubject`, `buyerSubject`, `$.buyerSubject`, đều mã `CONSTRAINT_VIOLATION`. Dùng snapshot fixture hiện có và đặt buyerSubject cùng một field đứng sau nó theo từ điển thành giá trị vi phạm; tạo object trực tiếp để không bị codec chặn trước boundary đang kiểm tra.
- [x] **Step 2:** Chạy characterization trên code chưa trích xuất và xác nhận chúng đạt. Giữ kiểm tra null của codec/serializer là `$` + `REQUIRED`, purchase validator là `$` + `CONSTRAINT_VIOLATION`; kiểm tra lại golden bytes hiện có.
- [x] **Step 3:** Viết test cho `ContractViolations.firstPath`: set rỗng, một lỗi, nhiều lỗi với thứ tự chèn đảo ngược và input không bị mutate; dùng Jakarta Validator thật với record test có constraint. Chạy để xác nhận helper chưa tồn tại, rồi mới thêm helper tối thiểu đáp ứng API và hành vi nêu trên.
- [x] **Step 4:** Thay ba block chọn min bằng gọi `firstPath`; giữ xử lý null trước validate và giữ cách caller tạo exception/prefix. Dọn import Comparator không còn dùng cho phép chọn lỗi; không xóa Comparator còn dùng để sort canonical JSON.
- [x] **Step 5:** Chạy helper test, ba characterization suite, validation/message/hash tests và mapper tests liên quan. Điều kiện đạt: path/mã lỗi/null semantics, wire representation, golden bytes và hashes đều giữ nguyên.

## Task 4: Kiểm tra toàn bộ và review

**Files:** Chỉ các file trong Task 2–3; cập nhật tiến độ/kết quả vào plan.

**Interfaces:** Produces bản refactor cùng bằng chứng build/test và danh sách phần chưa kiểm tra nếu thiếu hạ tầng.

- [x] **Step 1:** Chạy `.\mvnw.cmd spotless:apply -B -ntp`, rồi `.\mvnw.cmd verify -B -ntp` bằng JDK 21/UTC; đọc kết quả của cả compile, test, Spotless, SpotBugs. Không coi targeted tests thay thế full suite mặc định.
- [x] **Step 2:** Rà soát diff: chỉ thay annotation/dependency declaration và helper validation đã duyệt; không thay public endpoint, SQL, DTO, hash fixtures, profile, security matcher hoặc transaction.
- [x] **Step 3:** Review độc lập các constructor, caller validation và năm Review Focus; xử lý regression có bằng chứng và chạy lại kiểm tra liên quan.
- [x] **Step 4:** Báo file/nhóm thay đổi, test count và lỗi nếu có. Suite mặc định loại `EntityMappingTest`, `PaymentReadRepositoriesTest`, `StartPaymentRabbitIntegrationTest`; báo đúng giới hạn đó. Không chạy/tác động database hoặc broker dùng chung chỉ để hoàn tất refactor giữ hành vi.

## Kết quả thực hiện

| Bước kiểm tra | Kết quả thực tế |
| --- | --- |
| Baseline `verify` trước refactor | BUILD SUCCESS; 125 test, 0 failures/errors/skipped; Spotless sạch; SpotBugs 0 bug/error |
| Annotation và constructor injection | Đổi 14 class theo phạm vi; 30 test liên quan bean/controller/security/callback đạt. Hai callback constructor sinh bởi Lombok giữ public và thứ tự tham số; constructor có requireNonNull giữ nguyên |
| Characterization trước trích xuất | Thêm 6 test bảo vệ field lỗi theo thứ tự từ điển, prefix và null semantics; bộ codec/serializer/purchase/hash chạy 33 test đều đạt |
| Helper RED | Test mới không compile vì `ContractViolations` chưa tồn tại; chưa thêm helper trước khi quan sát thất bại này |
| Helper GREEN và caller | Sau khi thêm helper và thay 3 block lặp, 59 test helper/validation/hash/mapper đạt; có kiểm tra thứ tự input đảo ngược và input không bị mutate bằng Validator thật |
| Formatter | `spotless:apply` thành công; chỉ 14 file đã sửa cần sắp xếp lại import; baseline các file khác đã sạch |
| `verify` cuối bằng JDK 21/UTC | BUILD SUCCESS; **134 test, 0 failures, 0 errors, 0 skipped**. Spotless kiểm tra 176 file Java; SpotBugs 0 bug/error |
| Review độc lập | Không có finding Critical/Important/Minor. Reviewer kiểm diff Java, helper/test mới và bytecode constructor bằng javap; bảo toàn lexical path, mã/prefix lỗi, canonical/hash, callback, profile và transaction |
| Diff/phạm vi | `git diff --check` đạt; đúng 20 file Java trong phạm vi gồm 16 file main và 4 file test. Không sửa entity, DTO model, schema, dependency/build hoặc security config |

Các log lần lượt lưu trong `target/cinema-refactor-baseline.log`, `cinema-refactor-annotations.log`, `cinema-refactor-characterization.log`, `cinema-refactor-helper-red.log`, `cinema-refactor-helper-green.log`, `cinema-refactor-format.log`, `cinema-refactor-final.log`.

### Giới hạn và lựa chọn thực hiện

- Suite mặc định vẫn loại `EntityMappingTest`, `PaymentReadRepositoriesTest`, `StartPaymentRabbitIntegrationTest`; lần này không xác minh PostgreSQL/RabbitMQ thật hoặc thay đổi hạ tầng. Reviewer cũng không đánh giá hành vi tích hợp bên ngoài đó. Không tuyên bố các test bị loại đã chạy.
- Giữ đúng thư mục người dùng yêu cầu trên branch có sẵn `dev/tuan`, bảo toàn các thay đổi skill/tài liệu trước đó. Ghi chú tạm nằm trong `target/cinema-refactor-validation/` đã ignore thay cho helper SDD phụ thuộc shell. Đánh đổi: code nằm trong working tree hiện tại; ghi chú tạm có thể mất khi clean build, nên kết quả quan trọng được ghi lại trong plan này.
- Reviewer để riêng kết quả full verify cho agent triển khai đối chiếu: build cuối đã được chạy và xác nhận độc lập bằng log phía trên. Không còn finding chưa xử lý.

## Cách thực hiện

Đã triển khai trực tiếp theo plan người dùng duyệt; không mở rộng sang các nhóm constructor hoặc logic ngoài danh sách. Thay đổi Java đang ở working tree để người dùng xem diff.
