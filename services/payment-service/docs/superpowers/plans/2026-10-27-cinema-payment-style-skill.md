# Cinema Payment Style Skill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tạo skill local hướng dẫn viết Java theo phong cách Cinema, có bước duyệt plan trước code và tiêu chí tách logic lặp thành hàm dùng chung.

**Architecture:** Một `SKILL.md` chứa workflow và các quyết định khi code; một tài liệu tham chiếu chứa minh chứng Cinema và ví dụ áp dụng vào payment-service. Một mục ngắn trong `AGENTS.md` đưa người làm việc trong service đến đúng skill. Giữ nguyên kiến trúc và contract của payment-service.

**Tech Stack:** Markdown, YAML frontmatter và Python validator của skill-creator. Các ví dụ Java bám theo Lombok, Jakarta Validation/JPA, Spring và MapStruct có trong dự án.

**Spec:** [2026-10-27-cinema-payment-style-skill-design.md](../specs/2026-10-27-cinema-payment-style-skill-design.md).

**Trạng thái:** Đã triển khai theo plan được duyệt; đã kiểm tra cấu trúc, liên kết, phạm vi, S1–S4 và review độc lập.

## Global Constraints

- Phạm vi: `services/payment-service`; mọi đường dẫn bên dưới tính từ thư mục này.
- Skill: `.agents/skills/cinema-payment-style/SKILL.md`; không cài vào cấu hình toàn máy.
- Hướng dẫn chính bằng tiếng Việt; tên class, package và annotation giữ nguyên theo Java.
- Logic xử lý lặp lại cùng một trách nhiệm hoặc quy tắc nghiệp vụ từ hai nơi trở lên phải được tách thành một hàm dùng chung và các nơi đó gọi lại hàm này.
- Không refactor Java hiện có, đổi dependency, sao chép ApiResponse/base entity, đổi schema/API, hoặc cài skill vào cấu hình toàn máy.
- Giữ package chữ thường, UUID/Instant, schema và precision tiền, response/Problem hiện có, cấu hình mapper, formatter và bảo mật payment-service.
- Dùng cấu hình/version hiện tại của dự án khi thực thi; số phiên bản trong spec là kết quả khảo sát, không phải yêu cầu đóng băng.
- Giữ nguyên phần CodeGraph trong `AGENTS.md`; dùng CodeGraph trước khi hiểu/tìm code ở service có index, không gọi cho Cinema khi không có index.

## Review Focus

1. Cùng logic validation ở hai luồng: tái sử dụng helper hiện có hoặc tách một hàm; các luồng giữ nguyên exception và tác dụng phụ. Kiểm tra bằng tình huống S2.
2. Hai đoạn giống cú pháp nhưng khác nghiệp vụ: chỉ trích xuất phần chung thực sự, không tạo helper với nhiều cờ boolean. Kiểm tra bằng S3.
3. Không truy cập được Cinema hoặc không có superpowers: tài liệu local vẫn đủ để lập plan và chọn annotation. Kiểm tra bằng S4.
4. Entity có factory/constructor bảo vệ invariant: không thêm builder hoặc final field làm mất quy tắc khởi tạo. Kiểm tra bằng S3.
5. Endpoint cần Swagger/phân quyền nhưng dự án chưa bật hỗ trợ tương ứng: kiểm tra điều kiện, giữ security hiện có và không tự thêm dependency. Kiểm tra bằng S4.

## File Structure

| File | Trách nhiệm |
| --- | --- |
| `.agents/skills/cinema-payment-style/SKILL.md` | Khi dùng skill, workflow trước code, bảng annotation, tiêu chí clean code và checklist kiểm tra |
| `.agents/skills/cinema-payment-style/references/cinema-conventions.md` | Minh chứng có đường dẫn từ Cinema, khác biệt đích và một ví dụ Java thích nghi với payment-service |
| `AGENTS.md` | Một mục trỏ tới skill cho công việc Java trong service; không lặp lại toàn bộ hướng dẫn |
| `docs/superpowers/specs/2026-10-27-cinema-payment-style-skill-design.md` | Thiết kế đã duyệt; giữ liên kết với plan |
| `docs/superpowers/plans/2026-10-27-cinema-payment-style-skill.md` | Theo dõi thực hiện và ghi kết quả kiểm tra |

Không tạo script, template, README hoặc UI metadata khi hai file skill đã đủ cho nhu cầu này.

## Tình huống kiểm tra hành vi

Các tình huống dưới đây dùng để đánh giá hướng dẫn, không phải yêu cầu sửa Java trong service thật. Khi đánh giá có sinh code, chỉ lưu trong thư mục tạm, không đưa vào source dự án.

| ID | Đề bài đưa cho người/agent đánh giá | Điều kiện đạt |
| --- | --- | --- |
| S1 | "Thêm một service tra cứu trạng thái thanh toán theo phong cách Cinema; chưa có thiết kế hoặc plan được duyệt." | Đọc ngữ cảnh, trình bước thiết kế/plan còn thiếu và chờ duyệt; không viết implementation. Khi đề bài cung cấp thiết kế đã duyệt, lập plan có file, annotation, helper và cách kiểm tra. |
| S2 | "Hai phương thức cùng kiểm tra buyerSubject và cùng ném PaymentException với một PaymentErrorCode; hãy refactor theo plan đã duyệt." | Chỉ ra/tạo một helper private cho quy tắc chung, cả hai phương thức gọi helper; tên rõ nghĩa, giữ exception, kết quả và số lần gọi repository/gateway. Trước khi thêm helper phải kiểm tra helper sẵn có. |
| S3 | "Review hai phép tính khác quy tắc nhưng giống hình thức, cùng một entity có static factory, UUID, Instant và @Version." | Giữ riêng quy tắc khác nhau, giữ factory/mapping, không áp makeFinal cho state JPA hoặc thêm builder tùy tiện; chỉ đề xuất thay đổi trong phạm vi. |
| S4 | "Lập plan thêm endpoint có tài liệu và phân quyền theo Cinema; source Cinema và các skill superpowers không truy cập được. Chỉ được dùng tài liệu local và bằng chứng dependency/security của dự án." | Dùng reference local, chỉ ra điều kiện Swagger/method security và tái sử dụng cấu hình security, giữ response/Problem; có plan trước code; không tự cài dependency hoặc thay contract. |

## Task 1: Tạo skill và tài liệu tham chiếu có căn cứ

**Files:** Create hai file `.agents/skills/cinema-payment-style/SKILL.md` và `references/cinema-conventions.md` bên trong thư mục skill.

**Interfaces:** Consumes spec đã duyệt và các source Cinema đã xác định trong spec. Produces entrypoint `cinema-payment-style` với link tương đối `references/cinema-conventions.md` dùng được khi source Cinema không còn ở máy.

- [x] **Step 1:** Dùng `skill-creator` và `superpowers:writing-skills`; trước khi viết skill, chạy baseline S1–S4 ở ngữ cảnh đánh giá không chứa skill mới. Cho agent đánh giá đề bài và raw source tối thiểu, không đưa đáp án trong bảng; giữ bảng điều kiện đạt ở phía người chấm. Ghi hành vi và lỗi thực tế để so sánh sau.
- [x] **Step 2:** Viết reference từ các Controller/Service/Entity/DTO/Mapper/Repository được nêu trong spec. Phân biệt mẫu Cinema với lựa chọn phải thích nghi: `@Mapper(config = PaymentMapperConfig.class)`, constructor JPA, factory, kiểu dữ liệu, tiền, response và security. Không sao chép toàn bộ service hoặc nghiệp vụ Cinema.
- [x] **Step 3:** Thêm một ví dụ chuyển dependency của `PaymentQueryService` sang constructor injection bằng `@RequiredArgsConstructor` và `@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)`. Dùng đúng dependency hiện có; không lặp constructor viết tay, không đưa logic thanh toán giả vào ví dụ. Giải thích private helper cho logic lặp và component đúng trách nhiệm khi dùng ở nhiều class.
- [x] **Step 4:** Viết `SKILL.md` với `name: cinema-payment-style`; description bắt đầu bằng `Use when` và giới hạn ở Java trong payment-service hoặc yêu cầu phong cách Cinema tương ứng. Nội dung gồm workflow trong spec, bảng annotation theo vai trò và điều kiện áp dụng, tiêu chí clean code, checklist review và link reference.
- [x] **Step 5:** Chạy lại S1–S4 bằng ngữ cảnh đánh giá mới có hai file skill, so sánh theo bảng điều kiện đạt và ghi bằng chứng. Nếu baseline đã làm đúng một tiêu chí, ghi là đã đúng sẵn; không tạo lỗi giả. Sửa phần hướng dẫn gây sai quyết định rồi chạy lại tình huống tương ứng.

## Task 2: Liên kết skill với hướng dẫn của service

**Files:** Modify `AGENTS.md`.

**Interfaces:** Consumes entrypoint từ Task 1. Produces một mục `Cinema Java Style` dẫn đến `.agents/skills/cinema-payment-style/SKILL.md`.

- [x] **Step 1:** Đọc lại `AGENTS.md` trước sửa để giữ các thay đổi mới của người dùng và toàn bộ block `CODEGRAPH_START`/`CODEGRAPH_END`.
- [x] **Step 2:** Thêm mục ngắn yêu cầu đọc skill khi thêm, sửa hoặc review Java trong payment-service; nhắc plan trước code và logic lặp phải tách hàm dùng chung. Quy tắc và ví dụ chi tiết chỉ đặt trong skill.
- [x] **Step 3:** Mở link theo đúng đường dẫn từ `AGENTS.md`, rồi link reference từ `SKILL.md`; xác nhận đều trỏ tới file tồn tại và phạm vi kích hoạt không bao trùm các service khác.

## Task 3: Kiểm tra và bàn giao

**Files:** Review hai file skill, `AGENTS.md`; update checkbox/kết quả trong plan này. Không thêm file Java hoặc test Java.

**Interfaces:** Consumes các deliverable Task 1–2. Produces skill có cấu trúc hợp lệ và báo cáo kiểm tra thực tế.

- [x] **Step 1:** Chạy validator bên dưới từ thư mục service. Dùng UTF-8 vì hướng dẫn tiếng Việt. Điều kiện đạt: exit code 0 và `Skill is valid!`. Nếu thiếu Python/PyYAML, dùng runtime sẵn có phù hợp hoặc báo giới hạn; không tự cài dependency cho ứng dụng Java.

```powershell
python -X utf8 'C:\Users\WEOHWOEH\.codex\skills\.system\skill-creator\scripts\quick_validate.py' '.agents/skills/cinema-payment-style'
```

- [x] **Step 2:** Review S1–S4 cùng toàn bộ mục clean code trong spec; xác nhận không còn phần yêu cầu chưa được đưa vào skill/reference. Không coi validator frontmatter là bằng chứng rằng workflow hoạt động đúng.
- [x] **Step 3:** Chạy `git diff --check` và `git status --short --untracked-files=all`; đọc diff của `AGENTS.md` và nội dung các file mới. Điều kiện đạt: không có lỗi whitespace, không có thay đổi Java/build/database ngoài phạm vi.
- [x] **Step 4:** Báo đường dẫn skill, cách yêu cầu áp dụng, các quy ước chính, kiểm tra đã chạy và giới hạn thực tế. Chỉ đánh dấu hoàn tất sau khi có kết quả kiểm tra; không chạy toàn bộ test Java khi chỉ thay tài liệu.

## Kết quả kiểm tra

| Kiểm tra | Kết quả và bằng chứng |
| --- | --- |
| Baseline không có skill mới | S1 đã trình thiết kế/plan trước implementation; S2 đã đề xuất helper và giữ mã lỗi/số lần gọi repository; S3 đã phân biệt hai phép nhân và giữ factory/mapping. S2 chưa thể hiện rõ bước tìm helper sẵn có. S4 giữ contract/security nhưng không thể xác nhận phong cách Cinema khi thiếu source. Không tuyên bố skill sửa một lỗi hành vi baseline chưa chứng minh. |
| Cấu trúc trước khi tạo skill | Validator trả `SKILL.md not found`; đây là kiểm tra thiếu artifact, không phải lỗi hành vi của agent. |
| S1 có skill | Đạt: tìm thấy service hiện có, làm rõ nguồn trạng thái, đề xuất annotation phù hợp, trình thiết kế rồi plan và chờ duyệt; không viết implementation. |
| S2 có skill | Đạt: xác nhận chưa có helper cùng trách nhiệm, trích `requireBuyerSubject`, cả hai caller của tình huống gọi lại; giữ mã lỗi, thứ tự guard, số lần gọi repository và isolation/transaction. Không hỏi duyệt lại plan đã duyệt. |
| S3 có skill | Đạt: không gộp phép nhân khác nghiệp vụ; giữ `pending(...)`, constructor protected, UUID/Instant, version và mapping; thay đổi quyền setter cần kiểm tra caller và phạm vi riêng. |
| S4 có skill | Đạt: dùng reference local khi thiếu Cinema/superpowers; plan giữ DTO/mapper/ownership và matcher security, không tự thêm dependency hoặc annotation thiếu hỗ trợ; nêu điều kiện method security và kiểm tra cần chạy. |
| Validator sau khi tạo | `python -X utf8 .../quick_validate.py .agents/skills/cinema-payment-style`: exit code 0, `Skill is valid!`. |
| Link và phạm vi | Cả 4 link Markdown local giữa 5 file hướng dẫn đều tồn tại; block CodeGraph trong AGENTS.md giống HEAD; không có thay đổi Java/build/database. |
| Whitespace | `git diff --check` đạt; các file mới cũng được kiểm tra whitespace riêng. |
| Review độc lập | Không phát hiện vấn đề Critical/Important/Minor trong hai file skill và mục liên kết AGENTS.md. Kết quả hành vi được đánh giá riêng như S1–S4 phía trên. |

S1–S4 là bài thử phản hồi chỉ đọc ở ngữ cảnh độc lập, không phải unit/integration test hoặc bằng chứng ứng dụng đã build. Không chạy test Java vì lần này chỉ thay tài liệu. Các quan sát về source Cinema đã được đối chiếu khi khảo sát; không suy ra mọi class Cinema đều đồng nhất tuyệt đối.

## Lựa chọn khi thực hiện

- Giữ đúng thư mục người dùng chỉ định trên branch hiện có `dev/tuan`, không tạo checkout khác cho thay đổi chỉ tài liệu. Đánh đổi: các file hướng dẫn nằm trong working tree hiện tại, không có thay đổi ứng dụng.
- Ghi chú đánh giá tạm trong `target/cinema-skill-validation/` đã được Git ignore, thay cho helper SDD phụ thuộc shell; kết quả lâu dài được ghi trong plan này. Ghi chú tạm có thể mất khi clean build.
- Baseline vốn đã đạt phần lớn tiêu chí; skill được tạo theo yêu cầu để đóng gói quy ước local và giúp tái sử dụng khi thiếu source Cinema. Các bài thử hữu hạn không bảo đảm mọi tình huống tương lai đều đạt.
- Reviewer không đánh giá lại toàn bộ các quan sát lịch sử về Cinema hoặc chấm S1–S4; phần đối chiếu source và đánh giá các phản hồi thực tế do agent triển khai thực hiện.

## Cách thực hiện đề xuất

Đã triển khai trực tiếp trong chat này bằng `superpowers:executing-plans`. Baseline, đánh giá có skill và review cuối dùng các ngữ cảnh độc lập; không giao từng file cho agent khác thực hiện.
