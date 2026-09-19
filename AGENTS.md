# Flash Ticket Platform — Project Constitution

## BẮT BUỘC ĐỌC TRƯỚC — ưu tiên đồ án khi ra quyết định

**Luồng giao dịch chính chạy đúng → dữ liệu quan sát dùng được → thử tải/mô phỏng lỗi và RCA. Không tối ưu nghiệp vụ phụ bằng cách làm chậm các mục tiêu này.** Đây là rule người dùng đã chốt, không phải lời khuyên tùy chọn (sổ quyết định `PRJ-016`–`PRJ-021`, ngày 2026-09-13).

- Với phần chưa chốt, ưu tiên phương án đơn giản và tận dụng chức năng cũ phù hợp. Không viết lại renderer, gộp/tách dữ liệu hoặc mở rộng nghiệp vụ chỉ để mô hình đẹp/đầy đủ hơn. Việc tái sử dụng vẫn phải đúng gate và ranh giới đích; **không** nới lỏng Legacy implementation quarantine.
- Không cắt tính đúng đắn của tiền, giữ chỗ, phát vé, xử lý lặp hoặc các yêu cầu bảo mật đã duyệt để gọi là “ưu tiên RCA”. Không thêm service/Saga/nghiệp vụ chỉ để tạo nhiều tình huống nghiên cứu.
- Agent được chọn chi tiết triển khai đơn giản trong phạm vi đã chốt và công việc được giao. **Phải hỏi Minh trước khi bỏ/hoãn chức năng đã duyệt hoặc mở rộng phạm vi**; không đánh đồng “ít ưu tiên” với “được xóa”. Các gate và kiểm soát thay đổi bên dưới vẫn áp dụng.
- Điều kiểm được an toàn từ nguồn sẵn có thì **kiểm ngay trong lượt và đưa ra kết quả cùng đề xuất**. Không dùng “để kiểm”, “sau này cần kiểm” để thay công việc hiện làm được; không hỏi người dùng điều có thể xác minh từ code. Nếu thật sự không thể kiểm, nói rõ thiếu đầu vào/quyền gì và giới hạn kết luận, tuyệt đối không bịa kết quả.
- Khi đưa ra lựa chọn, nói trực diện **dùng lại gì / sửa gì / bỏ gì / thêm gì**, lý do liên quan luồng chính và công sức triển khai. Không mở lại lựa chọn đã chốt; chỉ trình những quyết định còn ảnh hưởng hành vi, phạm vi hoặc chi phí mà người dùng cần chọn.

Rule này không thay đề tài chính thức, quyền quyết định giữa hai bộ tài liệu, lịch ưu tiên “luồng chính trước, công cụ tải/chèn lỗi về sau” (`PRJ-013/014`), hoặc quyền phê duyệt của con người. Đọc tiếp toàn bộ hiến pháp và nguồn `DT18-*` và `docs/project/roles.md` trước khi diễn giải mục tiêu hoặc phân công.

## Scope and required workflow

- Treat `D:/Project/flash-ticket-platform` as the only canonical workspace for the graduation project.
- Treat `D:/Project/flash-ticket-system` and any earlier repository as implementation references only. Do not mutate them unless the user explicitly names them as targets.
- If a Flash Ticket task starts from another workspace, remain read-only and report the workspace mismatch before project mutation.
- Use the repository skill `$govern-capstone-work` for every task involving project planning, research, surveys, requirements, business analysis, B3–B14, bounded contexts, microservices, ADRs, Sagas, database/schema ownership, AI-assistant design, report writing, legacy-source comparison, or multi-file changes under `docs/`.

## Java formatting and quality gate

- The authoritative Java formatter is Spotless with Google Java Format, as configured in each application's `pom.xml`. Generate and edit Java in that format; do not add a competing formatter.
- Before handing off Java changes, run `./mvnw -B -ntp spotless:check` in every affected application (`.\mvnw.cmd -B -ntp spotless:check` on Windows). `./mvnw -B -ntp verify` already runs this check.
- Do not run `spotless:apply` reflexively or across every application. Finish the Java edits first; if the check fails, run `spotless:apply` once in only the affected application, inspect the formatting diff, then rerun the check or `verify`.
- Do not leave formatting failures for CI to discover. The PR workflow runs `./mvnw -B -ntp verify` and blocks the aggregate check on failure.

## Đề tài và ranh giới hai bộ tài liệu

Đọc mục này trước khi kết luận bất kỳ điều gì về phạm vi, mục tiêu, hay vai trò của hệ thống đặt vé.

1. **Đề tài chính thức hiện hành từ 2026-09-18:** *"Xây dựng hệ thống bán vé theo kiến trúc phân tán có ứng dụng đồ thị phụ thuộc để giám sát và chẩn đoán sự cố"*. Tên và nguyên văn nhiệm vụ do Lê Văn Minh xác nhận nằm tại `docs/evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md` (`DT18-TEN`, `DT18-NV1`–`DT18-NV3`; `PRJ-025/026`). **Bắt buộc đọc nguồn này trước mọi kết luận về đề tài, mục tiêu, phạm vi hay đánh giá.** Thư ngày 22/08 và mã `DH-*` chỉ là bằng chứng lịch sử; không dùng tên cũ hoặc mốc báo cáo hai tuần làm định hướng hiện hành.

2. **Hai bộ tài liệu, một đề tài.** Bộ hệ thống ở `docs/` chịu trách nhiệm thiết kế, xây dựng và đánh giá hệ thống bán vé phân tán; bộ RCA ở `docs/research-rca/` chịu trách nhiệm phương pháp giám sát/chẩn đoán bằng đồ thị và thực nghiệm. Hai bộ độc lập về nhịp làm việc và cổng kiểm soát, cùng phục vụ nhiệm vụ DT18. **Không viết hệ thống chỉ là bối cảnh hoặc chỉ cần chạy được:** phải đo thông lượng, thời gian đáp ứng, tỷ lệ lỗi, khả năng mở rộng, tính nhất quán dữ liệu và độ ổn định giao dịch theo nhiệm vụ mới.

   FlashTicket là sản phẩm đích và nơi áp dụng cơ chế. Phương pháp được thực nghiệm trước trên dữ liệu công khai phù hợp, sau đó thử trên FlashTicket; log, trace **và metrics** được ánh xạ lên đồ thị. Kết quả gồm hệ thống, mô hình đồ thị, cơ chế hỗ trợ chẩn đoán và giao diện minh họa phân tích. Không lấy kết quả dataset thay cho kiểm chứng trên FlashTicket, không tuyên bố tính mới hoặc hiệu quả chưa đo.

   **Nhóm và phân công hiện hành:** bốn người **Minh, Sơn, Tuấn, Tuyến**; Nhật đã rời nhóm. Minh lead giai đoạn đầu, phụ trách chính RCA và nền tảng chung. Đọc `docs/project/roles.md` để lấy đúng phạm vi từng người; không khôi phục phân công cũ. **Mobile là phần phụ, chỉ làm khi thực sự thừa thời gian** (`PRJ-035`); chưa giao người thực hiện. Ưu tiên này không tự bỏ bất biến/API check-in backend đã duyệt.

3. **Quyền quyết định, không phải quyền đọc** (`RES-028`). Mỗi bộ **không được dùng tài liệu của bộ kia để tự quyết định phần thuộc trách nhiệm riêng của mình**.

   - Bộ nghiên cứu **được nhận** mô hình hệ giao dịch — `B4`, `B5` §5.1, `B7` — qua cửa *Hệ thống → Nghiên cứu*, giữ nguyên nguồn và phiên bản khi trích. Đồ thị suy ra từ chúng là `CANDIDATE` cho tới khi được duyệt. Nó **không được** sửa một bất biến, ranh giới hay yêu cầu của bộ hệ thống.
   - Bộ hệ thống **được nhận** yêu cầu quan sát qua cửa *Nghiên cứu → Hệ thống*. Nó **không được** để yêu cầu đó quyết định số service, cách gộp tiến trình hay mẫu kiến trúc — những thứ đó thuộc `B11`–`B14`.

   > **Phạm vi của mục 3.** Nó chỉ áp cho ranh giới giữa **hai bộ tài liệu**. Nó **không nới lỏng bất cứ điều gì** trong mục *Legacy implementation quarantine* bên dưới: lệnh cấm đọc `B5.5` và repository cũ trong luồng `FORMATION` ở `B2`–`B10` và `B11-A` giữ nguyên hiệu lực.

4. **Đúng hai cửa nối, không tạo cửa thứ ba.** Chiều nghiên cứu → hệ thống đi qua `docs/research-rca/R0-boi-canh-va-rang-buoc.md` §3; chiều hệ thống → nghiên cứu đi qua `docs/project/lien-ket-rca.md`. Nội dung đi qua cửa chỉ được ghi ở `CANDIDATE` hoặc `OPEN`, và **không được sinh ra hoặc sửa đổi** một kịch bản, yêu cầu, bất biến hay ranh giới của bên kia. Trước khi đóng một gate, chạy **Phép thử độc lập** tại `docs/project/lien-ket-rca.md`.

5. **Đọc đúng hiệu lực, không dùng lịch sử làm hiện trạng.** Tài liệu làm việc phải dùng DT18 và phân công 18/09 ngay trong thân bài. Thư giảng viên 22/08, hồ sơ bối cảnh cũ, báo cáo lịch sử và nhật ký quyết định giữ nguyên bằng chứng nhưng phải khai rõ phạm vi lịch sử và dẫn nguồn mới. Không chỉ đổi tiêu đề rồi giữ lập luận “đề tài chính là RCA” hoặc “hệ thống không được đo sâu” ở thân bài. Khi nguồn cũ và xác nhận 18/09 khác nhau, áp xác nhận mới trong đúng phạm vi; các quyết định kỹ thuật không bị đổi thì vẫn giữ hiệu lực.

6. **Trợ lý cũ đã bị gỡ; năng lực giải thích thì không** (`RES-034`, `GOV-030`). Đây là chỗ đã gây hiểu sai nhiều lần, nên đọc kỹ ba dòng sau trước khi kết luận bất cứ điều gì về phần chẩn đoán.

   | Tên | Là gì | Tình trạng |
   |---|---|---|
   | **Trợ lý cũ** | Thành phần độc lập tự thu thập dấu vết và tự đề xuất nguyên nhân, chạy đường ống cố định `log → Drain → context → LLM API`; có bounded context riêng và một "nhánh `T`" đan xen bảy giai đoạn | **Đã gỡ khỏi bộ hệ thống** |
   | **Cơ chế RCA** | Dựng đồ thị phụ thuộc, ánh xạ log, trace và metrics, xác định vùng ảnh hưởng, phát hiện bất thường và xếp hạng nguyên nhân (`DT18-NV2`) | Phương pháp ở bộ RCA · **chạy trong FlashTicket** |
   | **Lớp giải thích** | Nhận kết quả đã xếp hạng, diễn giải nguyên nhân và gợi ý bước kiểm tra (quyết định còn hiệu lực `RES-034`; nguồn lịch sử `DH-MT4`) | Phương pháp ở bộ RCA · **chạy trong FlashTicket** |

   > **"Trợ lý RCA"** là tên gọi tắt của **lớp giải thích** — đúng thứ `DH-MT4` gọi là *"trợ lý"*. Nó **không phải** nhánh `T`, **không phải** trợ lý cũ, và **không** chạy đường ống `log → Drain → context → LLM API`. Thân bài các tài liệu dùng chữ *"cơ chế chẩn đoán"* và *"lớp giải thích"*; câu này tồn tại để nối chữ *"trợ lý"* trong thư của giảng viên với thiết kế mới.

   **Ba giới hạn còn nguyên hiệu lực** cho lớp giải thích, chép từ `PRJ-002` sang `RES-034`: không cam kết loại bỏ việc tái hiện lỗi · không tự kết luận nguyên nhân cuối cùng · không tự sửa hệ thống.

7. **Ranh giới hai bộ viết theo trách nhiệm, không theo "thứ này thuộc bộ nào"** (`RES-033`).

   | Bộ hệ thống (`docs/`) chịu trách nhiệm | Bộ RCA (`docs/research-rca/`) chịu trách nhiệm |
   |---|---|
   | FlashTicket phải sinh **dữ liệu quan sát** nào | Cách xử lý log, trace và metrics |
   | FlashTicket **tích hợp và chạy** cơ chế RCA (`RES-023` mức 2, gate `B11`) | Cách dựng đồ thị và xếp hạng |
   | Kết quả **đến được người có quyền sử dụng** | Có dùng kỹ thuật gom mẫu log hay không (`RES-035`) |
   | Cơ chế **chỉ được đọc**, không tự sửa nghiệp vụ | Cách dùng mô hình ngôn ngữ |
   | **Vị trí tích hợp** — quyết định ở `B11` | Cách đo chất lượng xếp hạng và lời giải thích |

   **Không được** đọc mục 6 thành *"bộ hệ thống hết trách nhiệm với phần chẩn đoán"*. Bộ hệ thống vẫn phải ghi rõ FlashTicket là nơi cơ chế RCA được tích hợp, triển khai và chạy trực tiếp.

   Chatbot hỗ trợ mua vé **thuộc bộ hệ thống** (`RES-036`) — nó là kênh bán vé, không phải công cụ chẩn đoán.

## Authority order

Resolve project knowledge in this order, while preserving the higher system/developer/user instruction hierarchy:

1. The user's explicit current decision.
2. This project constitution.
3. `docs/quy-trinh-lam-viec.md` as the master workflow.
4. Tầng A, B, and C methodology/presentation documents.
5. Specialized business, requirements, architecture, and research artifacts.
6. Status files, indexes, outlines, and summaries derived from those artifacts.
7. Legacy source code and database assets as implementation evidence only.

If two applicable sources at any level conflict materially, do not mutate. Cite the exact lines and obtain a resolution or record an approved temporary rule.

## Non-negotiable phase gates

- Use the review chain `B2 baseline -> B3 -> B4 -> B5 -> B7`. Downstream drafts are allowed, but a downstream artifact cannot become `APPROVED` until its required inputs are `APPROVED`. AI must never approve its own artifact.
- Use Giai đoạn 2 to model business processes, domain events, candidate bounded contexts, candidate aggregates, invariants, and open hotspots.
- Do not equate a bounded context with a physical service.
- Do not equate a distributed business transaction with a Saga.
- Do not equate candidate data ownership with a finalized schema or database.
- Do not choose, rank, recommend, or relabel any preframed aggregate-placement or service-decomposition option at B5, even as a `CANDIDATE` and even when the prompt omits legacy names. Trace candidates to approved B4 evidence and B7 invariants; otherwise record only an `OPEN` hotspot for B11-A with no placement preference.
- Treat the caps of eight business services and three Saga flows as constraints evaluated at B10/B11, never as target counts for B5.
- Complete B10 architectural significance and quality priorities before B11 architecture work.
- Use `docs/architecture/B11-A-independent-alternatives.md` to form alternatives from B5, B7, and B10 without legacy input. A human must mark that option set `APPROVED` before B11-B may open B5.5.
- Use `docs/architecture/B11-B-legacy-feasibility.md` only to check the approved B11-A options for reuse, migration, coupling, and feasibility. Record the B11-A input version; never create, add, rank, or modify an architecture option while legacy evidence is open.
- Use B11-C only after B11-A and B11-B are `APPROVED`. Before accepting a target ADR, record its impact on A1–A6 and re-review affected research artifacts and dependent inputs.
- Finalize architecture ADRs, physical service grouping, Sagas, target schemas, and contracts only at their authorized gates in B11–B14.

## Legacy implementation quarantine

- Derive target boundaries from requirements, workflows, domain events, invariants, data ownership, change/load patterns, and quality attributes.
- Classify A1–A7, B2–B10, B11-A, B11-C, B12–B14, target ADRs, and target-design report arguments as `FORMATION`; classify B5.5, the legacy baseline, B11-B, and reuse/migration mapping as `COMPARISON`. Default an unclassified new artifact to `FORMATION`.
- Do not read B5.5 or the legacy repository while producing B2–B10 or B11-A. Let B5.5 enter only at B11-B after a human has approved the independent B11-A option set.
- Let B5.5 estimate reuse, replacement, migration, coupling, and feasibility. Never use existing packages, imports, tables, or service names to generate target boundaries or alternatives.
- If a request asks for service decomposition from legacy packages or tables before B11-A exists, do not inspect the legacy repository and do not output named service candidates or placement preferences. Reframe the task around business/domain/quality evidence first.
- If legacy evidence reveals a feasibility problem, close B5.5 and return only a generalized constraint to the proper design gate. A material B11-A revision invalidates the old B11-B result; do not silently rewrite the domain model or option set around the legacy structure.

## Decision and change control

- Use only these epistemic states: `FACT`, `USER_CONFIRMED`, `CANDIDATE`, `DECIDED`, and `OPEN`.
- Record durable decisions in `docs/project/decision-register.md` with evidence, owner, gate, and affected artifacts.
- Record a user-confirmed decision atomically and preserve its explicit meaning. Do not append inferred policies, conditions, consequences, or scope to the same `USER_CONFIRMED` statement; record each implication separately as `CANDIDATE` or `OPEN`.
- A request that delegates a choice to the agent is not user confirmation of the option the agent selects. Only the option explicitly selected by the user may be `USER_CONFIRMED`.
- When resolving review feedback or an `OPEN` point, stop before adopting a proposal that would add or materially change application code, an API or contract, schema/data, web or mobile UI, or behavior outside the approved scope. Present the concrete implementation and interface impact and obtain Lê Văn Minh's explicit confirmation before recording it as a requirement or decision. A documentation correction that only restores already-approved behavior is not a scope expansion.
- Before changing more than three files or promoting a material decision, present an impact map. Exact approval in the user's current request satisfies this requirement.
- Edit the authoritative artifact first and derived status/index/report artifacts afterward.
- Do not create an ADR merely to satisfy a checklist. An ADR requires its authorized gate, alternatives, decision drivers, consequences, and verification method.
- Accept a target-architecture ADR only at B11-C, with provenance to approved B11-A/B11-B and an explicit `Tác động lên A1–A6` result. `ADR-000` is the methodological exception.

## Handoff requirements

For every completed mutation, report:

- Decisions added, changed, or deliberately left open.
- Assumptions used.
- Files changed.
- Validation and audits run.
- Remaining conflicts or risks.
- Material that should later appear in the graduation report.
