# Flash Ticket Platform — Project Constitution

## Scope and required workflow

- Treat `D:/Project/flash-ticket-platform` as the only canonical workspace for the graduation project.
- Treat `D:/Project/flash-ticket-system` and any earlier repository as implementation references only. Do not mutate them unless the user explicitly names them as targets.
- If a Flash Ticket task starts from another workspace, remain read-only and report the workspace mismatch before project mutation.
- Use the repository skill `$govern-capstone-work` for every task involving project planning, research, surveys, requirements, business analysis, B3–B14, bounded contexts, microservices, ADRs, Sagas, database/schema ownership, AI-assistant design, report writing, legacy-source comparison, or multi-file changes under `docs/`.

## Đề tài và ranh giới hai bộ tài liệu

Đọc mục này trước khi kết luận bất kỳ điều gì về phạm vi, mục tiêu, hay vai trò của hệ thống đặt vé.

1. **Đề tài chính thức do giảng viên hướng dẫn đặt ngày 2026-08-22:** *"Chẩn đoán nguyên nhân gốc sự cố giao dịch trực tuyến bằng đồ thị phụ thuộc"*. Nguyên văn thư và bảng mã `DH-TEN`, `DH-MT1`–`DH-MT4`, `DH-DATA`, `DH-DO`, `DH-MOC`, `DH-PB` nằm tại `docs/evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`. **Mọi trích dẫn định hướng phải trỏ về tệp đó**, không trỏ về trí nhớ hay một bản tóm tắt trung gian. `DH-MT1` gọi đích danh *"các dịch vụ đặt vé, thanh toán, xác thực, cơ sở dữ liệu, API đối tác"* — hệ thống của nhóm nằm **trong** mục tiêu đầu tiên của đề tài, không phải một đề tài tách rời.

2. **Hai bộ tài liệu, một đề tài.** Bộ hệ thống ở `docs/` và bộ nghiên cứu ở `docs/research-rca/` **độc lập về nhịp làm việc và cổng kiểm soát**. Chúng **không độc lập về đề tài**. Đề tài chính là RCA; bộ hệ thống cung cấp mô hình hệ giao dịch cho `DH-MT1`, bộ nghiên cứu cung cấp phương pháp, dữ liệu, độ đo và ràng buộc. Quyển báo cáo cuối được tổng hợp có chọn lọc từ cả hai nguồn.

   FlashTicket là **sản phẩm đích** và là nơi cơ chế chẩn đoán được tích hợp, triển khai và chạy trực tiếp (`RES-023`). Bộ dữ liệu công khai chỉ dùng để thử nghiệm có đáp án và so sánh kết quả.

   **Không được viết** rằng hai bộ là "hai đề tài", "hai trục", hay bộ này "không phụ thuộc" bộ kia. **Không được hiểu** việc tách bộ là hủy hoặc hạ vai trò bộ tài liệu hệ thống.

3. **Quyền quyết định, không phải quyền đọc** (`RES-028`). Mỗi bộ **không được dùng tài liệu của bộ kia để tự quyết định phần thuộc trách nhiệm riêng của mình**.

   - Bộ nghiên cứu **được nhận** mô hình hệ giao dịch — `B4`, `B5` §5.1, `B7` — qua cửa *Hệ thống → Nghiên cứu*, giữ nguyên nguồn và phiên bản khi trích. Đồ thị suy ra từ chúng là `CANDIDATE` cho tới khi được duyệt. Nó **không được** sửa một bất biến, ranh giới hay yêu cầu của bộ hệ thống.
   - Bộ hệ thống **được nhận** yêu cầu quan sát qua cửa *Nghiên cứu → Hệ thống*. Nó **không được** để yêu cầu đó quyết định số service, cách gộp tiến trình hay mẫu kiến trúc — những thứ đó thuộc `B11`–`B14`.

   > **Phạm vi của mục 3.** Nó chỉ áp cho ranh giới giữa **hai bộ tài liệu**. Nó **không nới lỏng bất cứ điều gì** trong mục *Legacy implementation quarantine* bên dưới: lệnh cấm đọc `B5.5` và repository cũ trong luồng `FORMATION` ở `B2`–`B10` và `B11-A` giữ nguyên hiệu lực.

4. **Đúng hai cửa nối, không tạo cửa thứ ba.** Chiều nghiên cứu → hệ thống đi qua `docs/research-rca/R0-boi-canh-va-rang-buoc.md` §3; chiều hệ thống → nghiên cứu đi qua `docs/project/lien-ket-rca.md`. Nội dung đi qua cửa chỉ được ghi ở `CANDIDATE` hoặc `OPEN`, và **không được sinh ra hoặc sửa đổi** một kịch bản, yêu cầu, bất biến hay ranh giới của bên kia. Trước khi đóng một gate, chạy **Phép thử độc lập** tại `docs/project/lien-ket-rca.md`.

5. **Không còn bảng quy đổi khi đọc.** `RES-031` thu hồi khung *"chỉ ngừng nhắc ở tài liệu mới"*. Mọi tài liệu đã được sửa thẳng để khớp `DH-TEN`; **đọc tài liệu nào cũng theo đúng nghĩa đen của nó**.

   Hai ngoại lệ có chủ đích, đều tự khai rõ ngay trong tệp:

   - `docs/boi-canh-va-mong-muon.md` là **bản ghi lời chủ đồ án**, nên các dòng cũ được giữ nguyên và §11 của chính tệp đó đính chính phần đổi đề tài.
   - Nhật ký phiên bản và các dòng quyết định đã bị thay thế trong `decision-register.md` giữ nguyên câu chữ theo quy tắc 3 của sổ; cột *Thay thế quyết định* cho biết dòng nào còn hiệu lực.

6. **Trợ lý cũ đã bị gỡ; năng lực giải thích thì không** (`RES-034`, `GOV-030`). Đây là chỗ đã gây hiểu sai nhiều lần, nên đọc kỹ ba dòng sau trước khi kết luận bất cứ điều gì về phần chẩn đoán.

   | Tên | Là gì | Tình trạng |
   |---|---|---|
   | **Trợ lý cũ** | Thành phần độc lập tự thu thập dấu vết và tự đề xuất nguyên nhân, chạy đường ống cố định `log → Drain → context → LLM API`; có bounded context riêng và một "nhánh `T`" đan xen bảy giai đoạn | **Đã gỡ khỏi bộ hệ thống** |
   | **Cơ chế RCA** | Dựng đồ thị phụ thuộc, ánh xạ log và trace, phát hiện bất thường, lan truyền và xếp hạng nguyên nhân (`DH-MT1`–`DH-MT3`) | Phương pháp ở bộ RCA · **chạy trong FlashTicket** |
   | **Lớp giải thích** | Nhận kết quả đã xếp hạng, diễn giải nguyên nhân và gợi ý bước kiểm tra (`DH-MT4`) | Phương pháp ở bộ RCA · **chạy trong FlashTicket** |

   > **"Trợ lý RCA"** là tên gọi tắt của **lớp giải thích** — đúng thứ `DH-MT4` gọi là *"trợ lý"*. Nó **không phải** nhánh `T`, **không phải** trợ lý cũ, và **không** chạy đường ống `log → Drain → context → LLM API`. Thân bài các tài liệu dùng chữ *"cơ chế chẩn đoán"* và *"lớp giải thích"*; câu này tồn tại để nối chữ *"trợ lý"* trong thư của giảng viên với thiết kế mới.

   **Ba giới hạn còn nguyên hiệu lực** cho lớp giải thích, chép từ `PRJ-002` sang `RES-034`: không cam kết loại bỏ việc tái hiện lỗi · không tự kết luận nguyên nhân cuối cùng · không tự sửa hệ thống.

7. **Ranh giới hai bộ viết theo trách nhiệm, không theo "thứ này thuộc bộ nào"** (`RES-033`).

   | Bộ hệ thống (`docs/`) chịu trách nhiệm | Bộ RCA (`docs/research-rca/`) chịu trách nhiệm |
   |---|---|
   | FlashTicket phải sinh **dữ liệu quan sát** nào | Cách xử lý log và trace |
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
