# TD-v1.3 freeze và Task F authorization — nguồn quyết định 28/09/2026

Trạng thái: `USER_CONFIRMED — Lê Văn Minh`  
Gate: `D/E closure → Task F`  
Executed TD-v1.3 SHA256: `34fd73f6a84dc6b45834bd7fd4cc7b86e19e54f1011de99735631f027ee18971`

## Quyết định nguyên văn

> **“APPROVE/FREEZE TD-v1.3, retaining all documented limitations, and authorize handoff to Task F.”**

Quyết định này có đúng hai hiệu lực nguyên tử:

1. Lê Văn Minh human-approve/freeze đúng identity byte của TD-v1.3 nêu trên, giữ toàn bộ limitation đã được công bố.
2. Lê Văn Minh cho phép handoff, bắt đầu và thực hiện Task F theo contract hiện hành trong `MASTER-RESEARCH-PROGRAM.md`.

## Nguồn và identity của scope authorization

Mission Task F do Minh cung cấp trong task hiện tại:

- attachment `56a4444d-d6f0-4868-aaef-85fc30daadc6/Pasted text.txt`;
- SHA256 `5e3a28c0736046c5f5ed59e5018637145eb4d80a34bf2365c6ae6843067fa224`;
- 1,483 dòng, 34,270 byte tại thời điểm ghi receipt.

Phần bổ sung cùng task:

- attachment `a723d3f1-3969-4717-beff-93217a98102c/Pasted text.txt`;
- SHA256 `db0d619a230cbca2e3e6591b247a8ee62b89d8850a5b1c69b28f88a4885bffa5`;
- 255 dòng, 8,008 byte tại thời điểm ghi receipt.

Hai digest trên khóa nguyên văn mission và phần bổ sung. Những mục dưới đây là bản ghi phạm vi vận hành để repository không phải dựa vào chat history.

## Phạm vi được ủy quyền

- Ghi durable decision và lan truyền trạng thái dẫn xuất sau khi source-of-truth đã được cập nhật.
- Giữ canonical TD-v1.3 byte-identical; freeze identity và semantics đã dùng cho completed development.
- Tạo Task F frozen release manifest bằng machine extraction từ sealed Task E selections, không chọn lại theo outcome.
- Tái sử dụng/extract qualified Task E implementation sang reusable core ở research workspace và chứng minh semantic equivalence.
- Tạo/sửa hợp lý trong impact map Task F: `src/rca/**`, `tests/task_f/**`, Task F configs, `results/task-f/**`, và các RCA decision/state/handoff/navigation documents cần thiết ở canonical project.
- Chạy synthetic/contract fixtures, E/F equivalence trên development smoke được định trước, một frozen-config development validation và bounded comparator qualification cần cho handoff tới pre-G.
- Tạo structured diagnosis packet theo firewall TD, không có oracle/evaluator fields.
- Dùng một fresh independent reviewer sau implementation và validation.

## Bất biến và giới hạn quyền

- Không sửa canonical TD-v1.3, formula, threshold, hyperparameter, candidate set, selection policy, evaluator hoặc scientific method.
- Không thay primary bằng sensitivity winner; không encode Phase 1/2 post-hoc patterns thành runtime logic.
- Không sửa hoặc ghi đè completed/failed/interrupted Task E evidence, Task D assurance/post-hoc evidence, `scripts/task_e/**`, `tests/task_e/**`, qualified upstream baselines hoặc completed configs.
- Không materialize, enumerate, download, load hay evaluate final60; frozen manifest chỉ tham chiếu canonical split contract/version và exposure ledger.
- Không mở Task G/H/I, không sửa FlashTicket application/API/schema/Saga/behavior.
- Không commit hoặc push trong authorization này.
- Mandatory contextual comparators phải giữ riêng: `Local-MAX-MT`, `BARO-RANK-adapted-TD12`, `RCD-RCAEval-adapted-TD12`; C1 `L` không phải `Local-MAX-MT`.
- R finite perturbation giữ đúng directed/undirected invariants, 256 chains, primary `200 * max(1, edge_count)` proposal budget, registered seed construction và mọi draw kể cả failure. Mobility không được diễn giải thành mixing/spectrum/path/kernel/centrality guarantee.

## Những điều quyết định này không xác nhận

- Không xác nhận graph superiority, final efficacy hoặc FlashTicket efficacy.
- Không xác nhận five-distinct-independent-reviewer requirement đã PASS; factual status đó vẫn `OPEN / NOT FACTUALLY CERTIFIED`.
- Không cấp quyền final60, Task G/H/I, method amendment hoặc một algorithm/hyperparameter mới.
- Không biến publication commit của prior evidence thành method amendment hay experiment rerun.

## Provenance interpretation đã khóa cho Task F

- Research workspace publication HEAD `7ae411d45726d3cbc3ee10b0e70e9ca7a8c8b20a` thêm đúng 12 prior-stage Task D assurance / Task E post-hoc artifacts vào snapshot `664d7180f0af3fadc8f11d7747b9d75833aeca34`; khác HEAD này tự nó không phải scientific drift.
- Historical receipts giữ HEAD/hash tại thời điểm chúng được tạo; không rewrite receipt cũ để khớp publication state mới.
- Execution-time source hashes phải được adjudicate qua run contracts, source manifests và final verification. Line-ending/packaging-only difference đã adjudicate không tự là blocker; semantic or unknown material drift mới là blocker.

## Registration

Nội dung quyết định nguyên tử được đăng ký tại `RCA-062` và `RCA-063` trong `docs/research-rca/RESEARCH-DECISIONS.md`. File này là authority receipt; implementation/result artifacts không được tự mở rộng quyền trên.
