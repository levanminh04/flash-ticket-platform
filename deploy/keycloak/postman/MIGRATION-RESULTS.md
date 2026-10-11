# Kết quả migration — 11/10/2026

Thời điểm chốt bằng chứng: 2026-10-11T06:19:02.248743+00:00 (UTC; Asia/Saigon +07:00). Phạm vi Keycloak + Postman; Spring chỉ bàn giao thiết kế. Đã chuyển runtime/import và Postman từ bốn OAuth clients riêng sang **internal-service**, realm role **INTERNAL_SERVICE**, audience duy nhất **flash-ticket-api**. Không check azp, không introspect S2S.

## Kiểm chứng thực tế

- Keycloak Docker **26.8.0**, Node.js **22**, Python stdlib, Postman CLI **1.73.0**, Postman MCP và Playwright MCP.
- Node contract/script + self-profile: **29/29 pass**, gồm RED→GREEN trước implementation; suite cuối chạy trước cleanup.
- Postman seed regression trước và sau cutover/restart: mỗi lượt **50 requests, 75 assertions, 0 failures**, exit 0. Sau restart: 62 pre-request scripts và 50 test scripts, không failures.
- Main Internal token request qua Postman CLI: HTTP 200, assertion pass.
- Playwright: 5 fresh login PKCE S256 + state/callback check, 5 UserInfo HTTP 200; seed human admin List Users HTTP 200.
- Isolated fresh import: Docker loopback port động, ephemeral H2, mount import read-only; không dùng volume/PostgreSQL runtime. New Internal token HTTP 200, đúng claims; effective service-account role chỉ INTERNAL_SERVICE. Container thử đã dừng/xóa.
- Postman MCP: main 34 + seed 50 = **84 request** đọc lại, **0 semantic mismatch**. URL/auth/body/headers/disabled fields/scripts đều đối chiếu. Không dựa riêng vào thông báo update success.

Commands đã dùng trong giai đoạn kiểm chứng (file test/runner nay là artifact tạm cần dọn, không phải hướng dẫn chạy bản bàn giao):

```text
rtk proxy node --test deploy/keycloak/postman/internal-service.test.cjs deploy/keycloak/postman/self-profile.test.cjs
rtk proxy python -X utf8 deploy/keycloak/postman/run-seed-tests.py --report deploy/keycloak/postman/internal-service-migration-verification.json --manifest deploy/keycloak/postman/internal-service-seed-manifest.json
rtk proxy postman collection run deploy/keycloak/postman/FlashTicket.postman_collection.json -e deploy/keycloak/postman/environments/Internal.local.postman_environment.json -i 9c203108-d033-46b1-bcb3-9f355a0fa245 --no-report-events --timeout-request 15000 --timeout-script 10000
rtk proxy docker compose --env-file deploy/.env.dev -f deploy/docker-compose.keycloak.yml config --quiet
```

## Acceptance

Các trạng thái dưới được kiểm chứng trong phiên này; Spring/receiver business và rollback rehearsal ghi riêng notRun.

| ID | Status | Evidence |
|---|---|---|
| KC-01 | pass | Runtime/import chỉ còn internal-service cho S2S; bốn client/service accounts cũ đã bỏ; 11 human IDs baseline giữ nguyên; built-ins/frontend/user-management giữ. |
| KC-02 | pass | Token mới HTTP 200 trước/sau restart; issuer localhost:8080/realms/flash-ticket, exp tương lai; main Postman token request lưu env đúng. |
| KC-03 | pass | Sai secret HTTP 401 unauthorized_client; tests và seed chứng minh token cũ bị xóa. |
| KC-04 | pass | Live aud flash-ticket-api singleton; tests chấp nhận string/array singleton, từ chối missing/extra aud. |
| KC-05 | pass | Runtime và isolated fresh-import effective role chỉ INTERNAL_SERVICE; token không client roles/human roles. |
| KC-06 | pass | 5 fresh PKCE S256 logins qua Playwright, 5 UserInfo HTTP 200; human có flash-ticket-api, không INTERNAL_SERVICE; token refresh sau restart thành công. |
| KC-07 | pass | Post-cutover seed 50 requests/75 assertions pass; self Account read/update, admin read/mutation/assign roles và negative 401/403 trên fixture. |
| KC-08 | pass | Compose config parse; new secret placeholder + env injection; isolated H2 fresh import HTTP 200 và role isolation pass. Backup mappings đầy đủ; rollback rehearsal notRun. |
| PM-01 | pass | Main 34 requests: một Internal token request có 3 form fields; 8 templates; seed 50 requests; không introspect/old machine token keys. |
| PM-02 | pass | 29 Node tests pass: missing fields, stale tokens, wrong env isolation, expiry/issuer/aud/role/client roles/malformed response, templates blocked. |
| PM-03 | pass | 84 cloud request readbacks khớp URL/auth/body/headers/disabled state/events; Internal env profile/keys đúng, shared secret rỗng, old 4 env absent. |
| PM-04 | pass | 8 templates mặc định disabled; report notRun vì endpoint business chưa triển khai. |

## HTTP cases đã chạy

UserInfo/Account GET 200; update profile hợp lệ 204 và giữ email; email invalid 400; anonymous/malformed token 401. BUYER/ORGANIZER Admin REST 403. Admin list/count/search/detail/sessions/roles 200; existing seed create/duplicate 409 expected; update, enable/disable, reset password, add BUYER/ORGANIZER, logout 204. Password 5 ký tự bị policy trả 400. Add ORGANIZER giữ BUYER. user-management/Internal lấy token 200; Internal Account API bị 401; frontend password grant 400; thiếu grant_type 400; sai Internal secret 401.

Mutation chỉ chạy trên fixture prefix/ID được allowlist. 7 fixtures giữ nguyên trong Keycloak, xem [SEED-README.md](SEED-README.md). Không xóa user thật hoặc reset PostgreSQL. SMTP/theme/password policy/realm defaults được đối chiếu giữ nguyên so baseline. Import không chứa dữ liệu human runtime do migration tự export thêm.

## Giới hạn notRun

| Hạng mục | Status | Lý do |
|---|---|---|
| 8 S2S business templates | notRun | Endpoint business chưa triển khai; template skip cục bộ không phải HTTP pass |
| Spring/Gateway 401/403, ownership, outbound reuse | notRun | Ngoài scope thực thi; xem [bàn giao](INTERNAL-SERVICE-HANDOFF.md) |
| DELETE user | notRun | Giữ seed theo yêu cầu |
| Gửi Gmail thật trong migration | notRun | Không gửi email/mutate SMTP trong scope này |
| Rollback rehearsal | notRun | Backup cấu hình/mappings đã parse; không rollback runtime đang thành công |

## IDs để tra cứu

- Workspace FlashTicket: e7cda7e3-269b-437d-a162-633b9c590157.
- Main collection: 37889324-4eb8b8df-1467-41b0-99bb-76eb27d8f27b.
- Seed collection giữ trên cloud: 37889324-44e0974d-943d-4fa1-b00a-a7c98d059f23.
- Internal env: 5c4cd6f7-f14b-4266-a118-309d1708eff0.
- User env: a047a3c1-8ec1-462b-819a-3a60ebe59097; Admin env: 42016c76-5d93-4ac8-b106-640bb6eb82d4.
- Seed env cloud: 6b4a613b-674f-48d3-ac46-99a397d7aedf.
- Legacy env FlashTicket Local Keycloak giữ ngoài bốn env được retire; các luồng mới chọn đúng User/Admin/Internal.

Shared Internal secret cố ý rỗng vì MCP chỉ ghi shared values. Import local Internal env hoặc nhập local clientSecret từ dotenv để dùng Postman desktop. CLI kiểm chứng sử dụng local secret, không chứng minh cloud tự có secret. Desktop Send chạy script lưu token vào env đang chọn; CLI run không tự ghi token ra local env file.

## Lịch sử còn liên quan trước migration

Các report cũ đã được đọc/tóm tắt trước cleanup, không cộng vào kết quả migration:
- Seed trước migration: 59 requests/94 assertions, 0 failures; business templates/introspection cũ không áp dụng contract mới.
- Self-profile: 3 requests/8 assertions, 5 guard checks; fixture tạm đã xóa ở phiên cũ.
- Human admin OAuth: 3 requests/8 assertions, 0 failures; không đổi password admin thật.
- Stability: user OAuth 4 requests/10 assertions; admin lifecycle/Gmail 20/30; bốn token/introspection suites cũ mỗi suite 2/5. Đây là bằng chứng lịch sử, không claim Gmail được test lại hôm nay.
- Cloud sync trước migration: 50 requests, 0 semantic differences; guards report passed. Readback hiện tại là 84 như ở trên.

## Review, cleanup và Git

Review độc lập bằng gpt-6-astra: không Critical; một Important về hướng dẫn collection cũ đã sửa. Test regression đã thấy RED rồi GREEN, toàn bộ suite 29/29 pass. Root/folder description đã patch trên cloud và readback khớp; toàn bộ 34 request và root/folder scripts vẫn giữ nguyên. Không có Minor deferred. Reviewer kiểm tra snapshots và chạy Node; live PKCE/CLI/fresh-import là bằng chứng executor, không phải reviewer chạy lại. Branch local **codex/shared-internal-service**; baseline 53f8711d07f08e02d4e2f6662c550b64cfaecfa5. Ở thời điểm kết thúc migration, thay đổi được giữ local chưa commit để bảo toàn baseline. Sau đó người dùng yêu cầu merge vào dev/son: bộ cấu hình Keycloak/Postman có phụ thuộc từ các bước trước được đưa vào commit riêng, loại trừ env local và thay đổi ngoài phạm vi. Kiểm tra trước commit: mọi credential literal trong realm đã tồn tại ở HEAD; Internal mới vẫn là placeholder. Không push remote.

Backup rollback ngoài repo: C:/Users/sondimid/AppData/Local/Temp/flash-ticket-internal-service-20261011T055317Z. Backup có credential, giữ riêng; không đưa vào Git. Files vận hành được giữ: realm import, Compose/dotenv, main collection, User/Admin/Internal envs và Markdown quan trọng. Runtime seed/cloud seed được giữ. Xem [README](README.md) để dùng bản cuối.

## Các quyết định khi triển khai và review

- Ruling: Work in existing checkout on new codex/shared-internal-service branch — user chose inline and required dirty local Postman/config assets are absent from HEAD; preserve all existing changes — cost if wrong: no separate checkout isolation, mitigated by full local backup.
- Ruling: Final fresh-context review runs before Task 6 cleanup — cleanup needs review done and tests must exist for any fixes — cost if wrong: final deletion verified by inventory rather than a second reviewer.
- Ruling: Task commits must not include baseline literals/changes — preserve user dirty changes, commit only isolated safe hunks/files or leave local — cost if wrong: some deliverables remain uncommitted.
- Task 1: Ruling: Keycloak 26.8 bad-secret response is HTTP 401 unauthorized_client, not invalid_client — accept actual client-auth error per spec — cost if wrong: broadens error-name assertion to two valid auth errors, status remains strict.
- Task 1: Ruling: use loopback 127.0.0.1 with Host localhost:8080 for local HTTP — Windows localhost IPv6 fallback caused each request delays and admin-token expiry — cost if wrong: issuer unchanged and asserted, network transport is local only.
- Task 2: Ruling: preserve cloud-edited Admin role-assignment request auth/body/scripts — cloud has newer user edits than local export — cost if wrong: existing manual OAuth/custom role behavior remains outside S2S migration; seed Admin regression uses separate requests.
- Task 4: Ruling: MCP full PUT drops nested request URL/auth/body/events; restore every request via granular update and re-read — API success did not preserve payload — cost if wrong: cutover remains gated on exact semantic comparison. OAuth useBrowser boolean normalized to string true because granular API rejects boolean; behavior unchanged.
- Task 4: Ruling: fresh import uses Docker dynamic loopback port instead of fixed 18080 — Windows excludes the fixed port; isolated ephemeral H2 has no runtime volumes — cost if wrong: transport differs, issuer and claims are asserted.
- Final: Ruling: review-package has empty commit range; use final working files versus pre-migration backup — no task-only safe commits yet, actual data must still receive fresh review — cost if wrong: reviewer must distinguish baseline changes explicitly rather than Git range.
- Final: Ruling: reusable helper backup safety — delete all temporary helpers and prohibit reruns; complete external backup stays intact — cost if wrong: replay could overwrite rollback mappings.
- Final: Ruling: existing Assign BUYER customization — preserve pre-migration user-edited auth/body/script, disclose that the pre-script sends BUYER even if visible body says ORGANIZER — cost if wrong: user may think ORGANIZER was granted; use seed Assign ORGANIZER or review request before sending.
- Final: Ruling: seed fixture guard hardening — actual run used allowlisted fixture IDs; preserve cloud seed behavior and document that manual ID edits require verification — cost if wrong: retargeted cloud mutations can affect non-fixture users.
- Final: Ruling: independent review used saved runtime/cloud evidence — executor live PKCE/token/CLI/readback checks supply integration proof; reviewer independently checked snapshots/tests — cost if wrong: external changes after snapshot would require recheck.
- Final: Ruling: Spring/Gateway and business side effects — leave implementation pending and all receiver expectations notRun under approved scope — cost if wrong: services are not ready to enforce this contract until handoff is implemented.
- Final: Ruling: rollback execution — keep parsed complete mappings and instructions, do not reverse successful runtime just to rehearse — cost if wrong: restoration procedure remains unproven end-to-end.
- Final: Ruling: email/user deletion — do not send email or delete retained seed users in this migration — cost if wrong: these paths lack current regression evidence.
- Final: Ruling: legacy literals/baseline changes — do not rotate unrelated credentials or stage user changes; new Internal secret remains local-only with placeholder import — cost if wrong: legacy credentials still require separate handling.
- Final: Ruling: Task 6 completion — final literal-path inventory and parse/link checks verify cleanup after reviewer finishes — cost if wrong: deletion errors would not receive a second reviewer, so stop on inventory failure.
- Final: Ruling: deployment beyond local — claim only tested local Compose and isolated import, no production/network deployment — cost if wrong: another environment may require hostname/network configuration.

Deferred minors: không có.

## Lưu ý Admin request có sẵn

Request chính `06 - Assign BUYER Realm Role` giữ customization người dùng trước migration: body hiện ORGANIZER nhưng pre-request thay thành BUYER. Vì vậy HTTP 204 của request này chỉ chứng minh gán BUYER, không chứng minh gán ORGANIZER. Bộ seed có request Assign ORGANIZER riêng đã test 204 và giữ BUYER. Chưa sửa customization ngoài scope S2S.

## Cleanup hoàn tất

Ngày 11/10/2026: đã xóa đúng 17 files trong allowlist Task 6 (2 Node test files, runner Python, Seed collection/env exports local, fixture credentials/manifest và các report JSON trung gian/lịch sử). Đã xóa output browser log riêng của phiên này; không xóa output phiên trước. Bản bàn giao chỉ còn main JSON, 6 env User/Admin/Internal example/local và 4 Markdown trong thư mục Postman. JSON parse và các link README/SEED-README pass; 21 file Spring/theme/start protected khớp hashes baseline. Seed accounts/cloud seed collection/env và backup ngoài repo giữ nguyên. Workspace tạm của riêng plan đã được xóa sau khi chuyển toàn bộ rulings và ledger hoàn thành sang Markdown; helpers có nguy cơ ghi đè backup không còn trong bản bàn giao.

## Ledger hoàn thành tasks

- Task 1: complete (commits 53f8711..53f8711, tests: node --test deploy/keycloak/postman/internal-service.test.cjs → # duration_ms 84.9086)
- Task 2: complete (commits 53f8711..53f8711, tests: node --test deploy/keycloak/postman/internal-service.test.cjs deploy/keycloak/postman/self-profile.test.cjs → # duration_ms 146.2391)
- Task 3: complete (commits 53f8711..53f8711, tests: node --test deploy/keycloak/postman/internal-service.test.cjs deploy/keycloak/postman/self-profile.test.cjs → # duration_ms 154.1324)
- Task 4: complete (commits 53f8711..53f8711, tests: node --test deploy/keycloak/postman/internal-service.test.cjs deploy/keycloak/postman/self-profile.test.cjs → # duration_ms 153.8612)
- Task 5: complete (commits 53f8711..53f8711, tests: node --test deploy/keycloak/postman/internal-service.test.cjs deploy/keycloak/postman/self-profile.test.cjs → # duration_ms 157.5847)
- Task 6: complete (commits 53f8711..53f8711, tests: python -X utf8 -c 'import json; from pathlib import Path; p=Path('deploy/keycloak/postman'); files=list(p.rglob('*.json')); [json.loads(f.read_text()) for f in files]; assert len(files)==7; assert not list(p.glob('*.cjs')); assert not list(p.glob('*.py')); print('FINAL INVENTORY PASS: 7 operational JSON, no test/runner')' → FINAL INVENTORY PASS: 7 operational JSON, no test/runner)
