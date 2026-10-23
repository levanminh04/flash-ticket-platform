# Payment P1 — Contract Validation and Hashing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Hoàn thành validation nghiệp vụ snapshot, canonical serialization/hash và kiểm tính nhất quán StartPayment để các gói HTTP/messaging dùng lại.

**Architecture:** Giữ DTO/MapStruct hiện tại; thêm validator và hash service thuần, có thể inject bằng constructor. ContractCodec tiếp tục phụ trách hình dạng JSON, validator kiểm quan hệ nhiều trường, serializer/hash không query DB hoặc gọi mạng. Chưa tạo controller/repository/Rabbit/Saga trong P1; các phần đó thuộc P2–P8 của spec đã duyệt.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Jackson 3 (`tools.jackson`), Jakarta Validation, JUnit 5/AssertJ và Maven Wrapper hiện có; không thêm thư viện canonical JSON.

**Spec:** `docs/superpowers/specs/2026-10-23-payment-service-runtime-design.md`, trọng tâm mục 3, 5, 8–9, 13 và gói P1 tại mục 14.

## Global Constraints

- “Giữ Java 21, Spring Boot/Cloud và dependency trong pom hiện tại; không nâng stack trong đợt này.”
- “Money VND nguyên đồng, Java BigDecimal chính xác, không float/double; wire money/version vẫn là String theo B13.”
- “Không trim/đổi case/Unicode-normalize subject hay nội dung hiển thị.”
- “Items sắp theo orderItemId và seats theo seatId; không tùy tiện sắp mọi array.”
- “SHA-256 dạng lowercase hex.”
- “Controller/consumer/worker gọi service; service dùng repository, mapper, validator và client/provider.”
- “Không tự chuyển thành APPROVED hoặc coi đối tác đã hiện thực.”
- Mọi thay đổi nằm ở worktree `C:/Users/WEOHWOEH/.codex/worktrees/payment-runtime/flash-ticket-platform`, nhánh `codex/payment-runtime`; không sửa checkout gốc hoặc copy secret vào worktree.
- Chỉ dùng pure unit/smoke tests cho P1; không tạo DB/broker, không thay schema, không đổi wire field hoặc tên package đã có.
- Từng task theo vòng test đỏ → triển khai tối thiểu → test xanh → commit; không giảm Spotless/SpotBugs/enforcer.

## Review Focus

1. JSON trùng key ở cấp lồng nhau phải bị từ chối trước readTree, kể cả hai giá trị giống nhau; Task 1.
2. Phép nhân/cộng nhiều dòng vượt miền tiền hoặc tổng quantity vượt int32 phải bị từ chối trước ép kiểu; Task 2.
3. UUID/time chuẩn hóa được nhưng Unicode/subject giữ nguyên byte có nghĩa; Task 3.
4. Array items/seats đổi thứ tự vẫn cho cùng hash, nhưng serializer không được sửa List đầu vào hoặc sắp array khác; Task 3.
5. Đổi metadata envelope dưới cùng messageId làm đổi inbox hash, trong khi request hash chỉ dùng actor/orderId/provider; Task 4.

## Baseline và quyết định triển khai

- Worktree bắt đầu từ `fc92eb7`. Đã chạy `mvnw.cmd verify -B -ntp` bằng Microsoft JDK 21.0.12.1/UTC ngày 23/10/2026: BUILD SUCCESS, 43 tests, 0 failure/error/skip; Spotless và SpotBugs PASS. Đây không phải kiểm mapping PostgreSQL hoặc RabbitMQ.
- Dùng native execution trong chat hiện tại theo yêu cầu “code trên nhánh worktree”; chỉ chuyển sang thực thi sau người dùng review plan. Không yêu cầu người dùng chọn lại cách tạo worktree.
- Constructor/annotation `@Component` dùng constructor injection; không cần tách interface/Impl cho validator, serializer, hash service.
- `ContractValidationException(field,code)` hiện có là lỗi chung. Validator lỗi nhiều trường chọn thứ tự kiểm xác định; field mới dùng đường dẫn bắt đầu `$`, lỗi thuộc list có index theo input gốc. Không đổi mã lỗi codec cũ ngoài lỗi JSON trùng key.
- PurchaseSnapshot hiện chỉ có mode QUANTITY/SEAT_MAP, không có sector kind SEATED/STANDING hoặc seat→ticketType mapping. P1 kiểm được tính nhất quán nội bộ của snapshot, không thể chứng minh selection khớp Event. Booking vẫn sở hữu kiểm cấu hình; ghi rõ giới hạn này trong README, không thêm kind vào wire contract.
- Với SEAT_MAP: sectorId bắt buộc, mọi item cùng sector; toàn bộ items đều có seats đủ quantity (seated) hoặc toàn bộ không có seats (standing). Cấm trộn hai dạng trong cùng snapshot. QUANTITY: sectorId cấp đơn và item đều null, seats rỗng.
- Tổng quantity phải nằm 1..Integer.MAX_VALUE vì ticketCount wire là int32. Event startsAt phải nhỏ hơn endsAt; frozenAt<expiresAt. Không kiểm expiresAt so với thời gian hiện tại ở validator bất biến: command cũ vẫn cần decode để xử lý retry/late payment.

## File map

Đường dẫn viết tắt `main` = `services/payment-service/src/main/java/com/flashticket/payment_service`; `test` = `services/payment-service/src/test/java/com/flashticket/payment_service`; `fixtures` = `services/payment-service/src/test/resources/contracts/canonical-v1`.

| File | Trách nhiệm |
| --- | --- |
| Modify `main/dto/common/ContractCodec.java` | Từ chối duplicate JSON key trên mapper/factory riêng |
| Create `main/validation/PurchaseSnapshotValidator.java` | Kiểm invariant snapshot và expectedTicketCount |
| Create `main/hashing/CanonicalContractSerializer.java` | Canonical bytes UTF-8 của purchase, request và envelope |
| Create `main/hashing/ContractHashService.java` | SHA-256 và public hash API |
| Create `main/validation/MessageInvariantValidator.java` | Liên kết envelope và StartPayment/requestHash |
| Modify `test/dto/ContractValidationTest.java` | Hồi quy duplicate key |
| Create `test/validation/PurchaseSnapshotValidatorTest.java` | Arithmetic/mode/seat/time validation |
| Create `test/hashing/CanonicalContractSerializerTest.java` | Golden bytes, normalization, non-mutation |
| Create `test/hashing/ContractHashServiceTest.java` | Golden hashes và miền dữ liệu hash |
| Create `test/validation/MessageInvariantValidatorTest.java` | Request/envelope consistency |
| Create `test/validation/PaymentContractFixtures.java` | Fixture hợp lệ và copy helpers cho test; không chứa rule tính hash |
| Create `test/config/PaymentContractComponentsTest.java` | Spring inject các bean mới trong smoke profile |
| Create files trong `fixtures` tại Task 3 | Golden input/bytes/hash và nguồn reference độc lập |
| Modify `services/payment-service/README.md` | Cách dùng boundary mới và giới hạn kiểm liên service |

## Task 1: Reject duplicate JSON keys at the contract boundary

**Files:** Modify ContractCodec.java và ContractValidationTest.java trong file map.

**Interfaces:** Giữ nguyên constructor `ContractCodec(JsonMapper, Validator)` và toàn bộ read/fromTree/write/toTree signatures. `read` nhận JSON trùng key phải ném ContractValidationException với field `$`, code `INVALID_JSON`. fromTree không thể phát hiện duplicate đã bị parser khác loại mất; đây là precondition của caller, không hứa ngược lại.

- [x] **Step 1 — Viết test đỏ:** thêm `rejectsDuplicateJsonKeysBeforeBuildingTree` với các input duplicate buyerSubject cấp root, quantity trong item, seatId trong seat và key trùng nhưng cùng giá trị; assert exception field/code. Thêm `doesNotChangeApplicationJsonMapper` chứng minh cấu hình strict chỉ thuộc codec; giữ input hợp lệ round-trip.
- [x] **Step 2 — Kiểm đỏ:** chạy từ thư mục Payment: `.\mvnw.cmd "-Dtest=ContractValidationTest,ContractCodecTest" test -B -ntp`; case duplicate phải fail vì hiện codec nhận last-value. Không coi lỗi môi trường/compiler khác là test đỏ đúng.
- [x] **Step 3 — Triển khai:** tạo factory/parser riêng bật strict duplicate detection trước `readTree`; dùng API Jackson 3 đã có trong dependency, không sửa global JsonMapper hoặc thêm parser thứ hai. Giữ lỗi timestamp/enum hiện có và unknown-field paths.
- [x] **Step 4 — Kiểm xanh:** chạy lại cùng lệnh; mọi case mới/cũ PASS.
- [x] **Step 5 — Commit:** chỉ stage hai file của task; message `fix(payment): reject duplicate JSON contract fields`.

## Task 2: Validate purchase arithmetic, identity, selection and time

**Files:** Create PurchaseSnapshotValidator.java, PurchaseSnapshotValidatorTest.java và PaymentContractFixtures.java.

**Interfaces:** `PurchaseSnapshotValidator(Validator validator)`; `public void validate(PurchaseSnapshot purchase)`; `public int expectedTicketCount(PurchaseSnapshot purchase)`. expectedTicketCount gọi validate trước trả tổng. Bean Validation được áp trước để object được tạo trực tiếp cũng bị kiểm, không chỉ object qua codec. Không tự truy vấn Event hoặc chặn snapshot quá hạn theo clock hiện tại.

- [x] **Step 1 — Viết test đỏ:** fixture hợp lệ QUANTITY có items 2×100 + 1×50, subtotal=250, promotion discount=25, total=225 và ticketCount=3; tạo thêm SEAT_MAP seated và standing hợp lệ. Assert không throw và expectedTicketCount đúng.
- [x] **Step 2 — Viết assertions lỗi:** parameterized cases cho lineSubtotal lệch, subtotal lệch, total lệch/0, discount>subtotal, promotion null nhưng discount>0, promotion discount lệch, item/seat UUID trùng toàn snapshot, SEAT_MAP sector thiếu/khác, số seat khác quantity, trộn seated/standing, QUANTITY có sector/seat, frozenAt=expiresAt, startsAt>=endsAt và object null. Kiểm code/field ổn định.
- [x] **Step 3 — Bổ sung biên:** giữ case tiền tối đa `9999999999999999999` hợp lệ ở fixture cũ; multiplication/sum vượt 19 chữ số bị lỗi trước lưu/cast; tổng quantity >2147483647 bị lỗi (dùng 2 items, không cấp phát hàng tỷ seat). Snapshot có các mốc hợp lệ trong năm 2000 vẫn được validate thành công; validator không dùng clock để chặn snapshot cũ.
- [x] **Step 4 — Kiểm đỏ:** `.\mvnw.cmd "-Dtest=PurchaseSnapshotValidatorTest" test -B -ntp`; lỗi thiếu validator được chấp nhận ở lần đầu. Sau khi có skeleton, chạy lại để thấy assertion nghiệp vụ đỏ trước viết rule.
- [x] **Step 5 — Triển khai:** Bean Validation, rồi arithmetic bằng BigDecimal/BigInteger, bound money mỗi kết quả, sets UUID, kiểm mode/sector và time theo thứ tự tài liệu. Mã lỗi mới: `LINE_TOTAL_MISMATCH`, `SUBTOTAL_MISMATCH`, `TOTAL_MISMATCH`, `DISCOUNT_MISMATCH`, `DUPLICATE_ID`, `INVALID_SELECTION`, `INVALID_TIME_RANGE`, `AMOUNT_OUT_OF_RANGE`, `TICKET_COUNT_OUT_OF_RANGE`; dữ liệu hình dạng không hợp lệ dùng `CONSTRAINT_VIOLATION`. Lỗi money cụ thể ưu tiên trước sai tổng phụ thuộc nó.
- [x] **Step 6 — Kiểm xanh:** chạy lại test mới và `.\mvnw.cmd "-Dtest=ContractCodecTest,ContractValidationTest,PurchaseSnapshotValidatorTest" test -B -ntp`.
- [x] **Step 7 — Commit:** stage đúng ba file task; message `feat(payment): validate purchase snapshot invariants`.

## Task 3: Produce deterministic canonical UTF-8 bytes

**Files:** Create CanonicalContractSerializer.java, CanonicalContractSerializerTest.java; mở rộng PaymentContractFixtures.java; create trong `fixtures`: `request.canonical.json`, `request.sha256`, `purchase-seated.input.json`, `purchase-seated.canonical.json`, `purchase-seated.sha256`, `start-envelope.input.json`, `start-envelope.canonical.json`, `start-envelope.sha256`, `README.md`.

**Interfaces:** `CanonicalContractSerializer(ContractCodec codec)`; `public byte[] purchaseBytes(PurchaseSnapshot purchase)`; `public byte[] requestBytes(UUID orderId, PaymentProvider provider, String actor)`; `public byte[] envelopeBytes(Envelope<?> envelope)`. Đầu vào đã qua invariant validator; codec vẫn kiểm Bean Validation. Serializer không gọi hash service, không thay đổi collection đầu vào.

- [x] **Step 1 — Khóa golden request:** canonical bytes chính xác, không BOM/newline: `{"actor":"buyer-1","orderId":"10000000-0000-0000-0000-000000000001","provider":"VNPAY"}`. Expected SHA-256: `da056e90a7f9c56e2f743714e9f31aa41fad549341cc5588282b6debba79a05e` (đã tính độc lập bằng .NET SHA256 khi lập plan).
- [x] **Step 2 — Chuẩn bị golden purchase/envelope:** fixture có hai items và ít nhất hai seats để kiểm sorting, null, tiền/version lớn, Unicode, quotes/backslash/control-character. Canonical keys theo thứ tự từ điển; string UTF-8 không escape ký tự non-ASCII, dùng JSON escape chuẩn cho quote/backslash và control character, không escape `/`. Tạo expected bytes bằng reference độc lập ngoài Java implementation, SHA-256 bằng .NET hoặc Python hashlib; README ghi rule/reference và checksum, không gọi production serializer để sinh expected files.
- [x] **Step 3 — Viết test đỏ:** assert `purchaseBytes` bằng golden byte array; request bằng literal ở Step 1; envelope bằng golden. Đảo items/seats vẫn bằng golden và input Lists giữ nguyên thứ tự. UUID viết hoa sau decode ra bytes lowercase; Instant UTC có `.000Z` ra dạng ISO_INSTANT chuẩn; khác subject case/Unicode composition phải ra bytes khác. Unknown arbitrary array trong hợp đồng được hỗ trợ không được tự sắp; bảo đảm không có recursive sort mọi array.
- [x] **Step 4 — Kiểm đỏ:** `.\mvnw.cmd "-Dtest=CanonicalContractSerializerTest" test -B -ntp`.
- [x] **Step 5 — Triển khai:** tạo tree từ DTO typed bằng codec; chuẩn hóa UUID/Instant từ giá trị typed; sort object keys đệ quy và chỉ sort các list purchase có ngữ nghĩa đã định. Envelope chứa StartPayment hoặc IssueTickets chuẩn hóa purchase đúng path; các DTO khác giữ array order. Giữ null, wire strings và scalar type; không tự serialize entity/POJO tùy ý như contract.
- [x] **Step 6 — Kiểm xanh:** chạy lại test và các test DTO; xác nhận byte cuối không có newline/BOM. Golden file có thể kết thúc newline khi lưu chỉ nếu test đọc loại chính xác delimiter file đã tài liệu hóa; ưu tiên lưu expected bytes không newline để tránh trim làm mất byte có nghĩa.
- [x] **Step 7 — Commit:** stage serializer/tests/fixture files của task; message `feat(payment): canonicalize payment contract bytes`.

## Task 4: Hash contracts and validate StartPayment identity

**Files:** Create ContractHashService.java, MessageInvariantValidator.java và hai test tương ứng; mở rộng fixtures helper khi cần.

**Interfaces:** `ContractHashService(CanonicalContractSerializer serializer, PurchaseSnapshotValidator purchaseValidator)`; `String purchaseHash(PurchaseSnapshot purchase)`; `String requestHash(UUID orderId, PaymentProvider provider, String actor)`; `String envelopeHash(Envelope<?> validatedEnvelope)`.

`MessageInvariantValidator(PurchaseSnapshotValidator purchaseValidator, ContractHashService hashes)`; `void validateStartPayment(Envelope<StartPayment> envelope)`. Service này kiểm semantic linkage, không kiểm JWT/broker ACL hoặc Booking payable. Các message khác tiếp tục dùng consistency annotation đã có; semantic state/source/expected ticket count phụ thuộc persisted state thuộc P5/P6.

- [x] **Step 1 — Viết test đỏ hash:** golden request/purchase/envelope digest 64 lowercase hex; cùng dữ liệu canonical có hash giống; đổi amount hợp lệ theo toàn bộ totals làm đổi purchase hash; đổi requestKey/requestId không làm đổi request hash vì không nằm miền hash; đổi actor/order/provider làm đổi hoặc bị từ chối nếu provider ngoài contract. `requestHash` từ chối actor blank, null order/provider; không trim actor hợp lệ có khoảng trắng biên.
- [x] **Step 2 — Viết test đỏ envelope:** messageType phải `payment.start-requested.v1`; messageId=requestId; aggregateId=correlationId=purchase.orderId; requestHash khớp SHA-256 request tuple; purchase phải qua Task 2. Sai từng liên kết có field/code cụ thể: `MESSAGE_TYPE_MISMATCH`, `MESSAGE_ID_MISMATCH`, `AGGREGATE_ID_MISMATCH`, `CORRELATION_ID_MISMATCH`, `REQUEST_HASH_MISMATCH`. Giữ allowed nullable causationId/traceparent; không thêm luật aggregateVersion=sourceOrderVersion khi B13 chưa quy định.
- [x] **Step 3 — Kiểm miền inbox:** đổi occurredAt, aggregateVersion, causationId hoặc traceparent làm đổi envelopeHash; đổi key order JSON hoặc đảo purchase items/seats không đổi. Sai shape/unknown field/duplicate key phải bị codec từ chối trước gọi validator/hash. Test fixture `StartPayment.json` cũ có requestHash=`fixture` chỉ dùng kiểm shape, không được nhận là command semantic hợp lệ.
- [x] **Step 4 — Kiểm đỏ:** `.\mvnw.cmd "-Dtest=ContractHashServiceTest,MessageInvariantValidatorTest" test -B -ntp`.
- [x] **Step 5 — Triển khai:** SHA-256 chuẩn JDK và HexFormat; validate input rõ ràng; compare requestHash với canonical lowercase hex chính xác. Không đưa snapshotHash vào DTO mới, không đổi mapper để tự tính hash, không tự tạo ID/time. envelopeHash có precondition envelope đã semantic-validated; tránh dependency vòng từ hash service sang MessageInvariantValidator.
- [x] **Step 6 — Kiểm xanh:** cùng lệnh cộng tất cả tests Task 1–3; kiểm error field/code và không query DB/HTTP.
- [x] **Step 7 — Commit:** stage đúng các file task; message `feat(payment): hash contracts and validate start command identity`.

## Task 5: Integrate Spring components and document the boundary

**Files:** Create PaymentContractComponentsTest.java; Modify README.md. Chỉ sửa MappingConfiguration.java nếu cần wiring tường minh thay `@Component`, không tạo hai bean cùng kiểu.

**Interfaces:** Bean injection đủ `PurchaseSnapshotValidator`, `CanonicalContractSerializer`, `ContractHashService`, `MessageInvariantValidator`; dùng ContractCodec bean hiện có. Smoke profile giữ nguyên exclusion DB/Rabbit.

- [x] **Step 1 — Viết test đỏ:** SpringBootTest inject bốn component; decode semantic-valid envelope từ fixtures mới → validateStartPayment → purchaseHash/requestHash/envelopeHash bằng expected golden; fixture sai tổng bị từ chối qua bean thật. Không mock validator/hash service.
- [x] **Step 2 — Kiểm:** `.\mvnw.cmd "-Dtest=PaymentContractComponentsTest" test -B -ntp`; nếu component scan đã khiến test xanh thì không tạo wiring thừa để ép đỏ, test vẫn kiểm integration có ý nghĩa.
- [x] **Step 3 — Tài liệu:** README ghi đường đi `codec.read → invariant validator → hash → application use case`; ba miền hash; duplicate-key strict; giới hạn kind/seat mapping; golden fixtures là candidate chưa có Booking sign-off; P1 chưa tạo controller/repository/Rabbit/Saga. Thay mô tả scaffold validation đã cũ chỉ ở đoạn liên quan, giữ lịch sử verification trước.
- [x] **Step 4 — Verify toàn service:** `.\mvnw.cmd verify -B -ntp` bằng JDK 21/UTC; yêu cầu BUILD SUCCESS, không failure/error/skip mới, Spotless/SpotBugs PASS. Không chạy mapping-tests nếu không có DB test riêng; ghi NOT_RUN cho PostgreSQL/Rabbit/E2E và lý do scope, không dùng smoke test thay thế.
- [x] **Step 5 — Review diff:** kiểm chỉ có file trong P1, không secret/DDL/dependency mới; golden bytes/hash được tạo độc lập; không thêm semantic validation vào codec khiến các mapper/shape fixture cũ vô tình đổi trách nhiệm. Commit `docs(payment): document validated contract and hash boundaries` cùng test/wiring nếu có.

## Verification commands

Từ thư mục `services/payment-service` trong worktree:

```powershell
$env:JAVA_HOME = 'C:\Users\WEOHWOEH\.jdks\ms-21.0.12.1'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
.\mvnw.cmd verify -B -ntp
```

Path JDK trên đã tồn tại khi lập plan; máy khác dùng JDK 21 tương ứng. Không thay JAVA_HOME hệ thống hoặc commit đường dẫn máy vào config sản phẩm.

## Coverage, handoff và giới hạn

- Task 1–4 phủ spec mục 5; Task 4 nối invariant start của mục 9; Task 5 phủ injection, tài liệu và kiểm P1.
- Các yêu cầu controller/repository/security thuộc P2; Rabbit/inbox/outbox P3; guard/provider/recovery P4; persisted Saga P5; refund/cancel P6; VNPay P7; internal summary/E2E P8. Không tuyên bố chúng xong khi P1 xanh.
- Các giá trị canonical encoding/selection/ticketCount được cụ thể hóa trong plan để review. Nếu Booking đang dùng rule khác, lưu bằng chứng và thống nhất contract trước P4/P8; không tự sửa code Booking hoặc gắn nhãn APPROVED cho B13.
- Sau review plan: thực hiện trực tiếp trong chat này theo từng task trên `codex/payment-runtime`, giữ checkpoint P1. Trước sửa code phải có phản hồi duyệt tài liệu plan này theo gate brainstorming/writing-plans. Không yêu cầu chọn lại worktree.

## Kết quả triển khai P1

Đã hoàn thành các task trên nhánh codex/payment-runtime. Constructor thực tế của CanonicalContractSerializer nhận Jakarta Validator thay ContractCodec vì chỉ cần Bean Validation trước encoder độc lập; API byte/hash giữ nguyên. Full verify: 62 test PASS, Spotless PASS, SpotBugs 0. PostgreSQL/RabbitMQ/E2E chưa chạy trong phạm vi P1. Golden fixture còn chờ đối chiếu Booking.
