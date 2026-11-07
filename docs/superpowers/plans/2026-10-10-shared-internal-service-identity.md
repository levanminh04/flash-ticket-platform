# Shared Internal Service Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Chuyển bốn identity S2S sang `internal-service`, đồng bộ Keycloak + Postman và kiểm chứng các luồng user/admin hiện có.

**Architecture:** Keycloak cấp machine token có realm role `INTERNAL_SERVICE` và audience duy nhất `flash-ticket-api`. User/admin tiếp tục login bằng `frontend`; `user-management` tiếp tục phục vụ Keycloak Admin REST. Triển khai theo thứ tự thêm identity mới → kiểm chứng → chuyển Postman/seed → bỏ identity cũ; Spring chỉ được mô tả để bàn giao.

**Tech Stack:** Keycloak 26.8.0, Docker Compose, Postman Collection v2.1 + environments, Postman MCP/CLI, Playwright OAuth2 PKCE, Python stdlib, Node.js 22 `node:test`/`vm`.

**Spec:** [2026-10-10-shared-internal-service-identity-design.md](D:/DATN/flash-ticket-platform/docs/superpowers/specs/2026-10-10-shared-internal-service-identity-design.md)

## Global Constraints

- Realm `flash-ticket`; client chung `internal-service`; realm role `INTERNAL_SERVICE`; audience S2S chính xác `{flash-ticket-api}` sau khi chuẩn hóa string/array.
- Không kiểm tra `azp` trong phân quyền hoặc script lưu/kiểm tra token S2S; không yêu cầu audience `internal-service`.
- Bỏ các request, biến, guard, assertion và seed case introspect S2S cũ.
- Giữ `frontend`, `user-management`, built-in clients, human users, role nghiệp vụ, OAuth2 PKCE, Account API, SMTP, theme và password policy hiện có.
- Không sửa Java, Spring YAML, broker ACL; không reset PostgreSQL, xóa realm hoặc đổi mật khẩu user thật.
- Secret mới của Internal chỉ ở file local được ignore; realm import dùng `${INTERNAL_SERVICE_CLIENT_SECRET}`. Example env có secret/token rỗng; log/report không chứa credential hoặc JWT. Không rotate secret của client ngoài phạm vi migration.
- Chỉ gọi API ghi trên fixture seed được nhận diện bằng ID + prefix; giữ fixture sau test. Email thật không tự động gửi trong migration này.
- S2S business endpoint chưa triển khai phải ghi `notRun`; skip cục bộ không phải HTTP response hoặc E2E pass.
- Sau khi toàn bộ kiểm chứng bắt buộc và review cuối hoàn tất, xóa các file test/seed runner, fixture credential local và báo cáo JSON test trong phạm vi Keycloak/Postman theo allowlist Task 6. Chuyển kết quả quan trọng sang Markdown trước khi xóa; không để bộ file test trong bản bàn giao cuối.
- Giữ các JSON cấu hình/import/export dùng thực tế: realm import, collection chính, User/Admin/Internal environments; giữ dotenv/local env cần để vận hành. Giữ tài liệu Markdown quan trọng: spec, plan, README, SEED-README, INTERNAL-SERVICE-HANDOFF và MIGRATION-RESULTS. Không xóa test của phần khác trong dự án hoặc seed users trong Keycloak.
- Mọi shell command dùng tiền tố `rtk`; chạy từ `D:/DATN/flash-ticket-platform`. Đọc RTK.md, AGENTS.md và spec trước khi thực thi.
- Working tree có thay đổi từ trước. Không reset/stash toàn bộ, không `git add .`, không ghi đè thay đổi ngoài phạm vi. `/docs/superpowers/` đang bị ignore; không force-add hoặc thay `.gitignore` chỉ để commit plan.
- Đã triển khai inline ngày 11/10/2026; kiểm thử/review/cleanup hoàn tất. Commit được giữ local chưa tạo theo ruling vì working tree có thay đổi và credential baseline.

## Review Focus

1. Mapper `oidc-audience-resolve-mapper` thêm audience ngoài ý muốn: token S2S phải có đúng một audience, user/admin giữ audiences cần cho Keycloak APIs — Task 1, `KC-04`/`KC-06`.
2. Service account thừa hưởng default role chứa `BUYER`/Account permissions: kiểm tra cả effective assignments và token, không chỉ role gán trực tiếp — Task 1, `KC-05`.
3. Token request fail hoặc chọn nhầm env còn giữ token cũ: xóa token Internal trước attempt hợp lệ, không ghi/xóa dữ liệu User/Admin khi profile sai — Task 2, tests về stale token và env isolation.
4. Đồng bộ Postman làm mất auth/body/scripts: đọc lại từ MCP, so sánh request projection; thiếu field phải sửa trước cutover — Task 4, `PM-03`.
5. Migration chạy lại hoặc dừng giữa chừng làm mất client/user: checkpoint theo `clientId`, backup có role/scope mappings, chỉ retire sau đủ bằng chứng và có đường rollback — Tasks 1/4, `KC-01`/`KC-08`.

---

## File map và ranh giới

Tất cả đường dẫn dưới đây tương đối với `D:/DATN/flash-ticket-platform`.

| File | Thao tác / trách nhiệm |
|---|---|
| `deploy/keycloak/import/flash-ticket-realm.json` | Sửa client/realm role/client scope/service account và mappings; giữ cấu hình ngoài S2S |
| `deploy/docker-compose.keycloak.yml` | Truyền `INTERNAL_SERVICE_CLIENT_SECRET` vào Keycloak |
| `deploy/.env.example` | Thêm key secret rỗng, hướng dẫn điền local |
| `deploy/.env` hoặc `deploy/.env.dev` | Cập nhật file local thực sự dùng khi chạy Compose; không đưa credential vào Git |
| `deploy/keycloak/postman/FlashTicket.postman_collection.json` | Một folder Internal, token request, guards và templates theo service nhận |
| `deploy/keycloak/postman/environments/Internal.example.postman_environment.json` | Env portable, không credential |
| `deploy/keycloak/postman/environments/Internal.local.postman_environment.json` | Env Internal local để test |
| `deploy/keycloak/postman/environments/{Booking,Payment,Event,Ticket}.example.postman_environment.json` | Xóa sau cutover thành công; bản local cũ đưa vào backup ngoài repo |
| `deploy/keycloak/postman/FlashTicket.Seed.postman_collection.json` | Dùng tạm để regression; xóa file local ở Task 6, không tự xóa collection cloud |
| `deploy/keycloak/postman/environments/Seed.{example,local}.postman_environment.json` | Thay machine keys để test; xóa hai file local ở Task 6, không tự xóa env cloud |
| `deploy/keycloak/postman/seed-credentials.json` | Dùng tạm credential/refresh tokens cho seed; phải được ignore, xóa ở Task 6 |
| `deploy/keycloak/postman/run-seed-tests.py` | Runner tạm, output mới không ghi đè lịch sử trong lúc test; xóa ở Task 6 |
| `deploy/keycloak/postman/internal-service.test.cjs` | Test tạm contract/script, không thêm dependency; xóa sau kiểm chứng ở Task 6 |
| `deploy/keycloak/postman/self-profile.test.cjs` | Chạy regression hiện có; xóa file test này ở Task 6 |
| `deploy/keycloak/postman/internal-service-migration-verification.json` | Báo cáo trung gian; chuyển kết quả sang MIGRATION-RESULTS.md rồi xóa ở Task 6 |
| `deploy/keycloak/postman/internal-service-seed-manifest.json` | Snapshot trung gian; giữ summary fixture trong Markdown rồi xóa ở Task 6 |
| `deploy/keycloak/postman/README.md`, `SEED-README.md` | Cách chọn env/login/token, migration/rollback, giới hạn kiểm thử |
| `deploy/keycloak/postman/INTERNAL-SERVICE-HANDOFF.md` | Contract và mã giả bàn giao Spring; không là implementation đã chạy |
| `deploy/keycloak/postman/MIGRATION-RESULTS.md` | Kết quả kiểm chứng cuối, acceptance statuses, fixture summary, notRun và giới hạn; không chứa secret/token |

Backup credentialed đặt ngoài repo tại `%TEMP%/flash-ticket-internal-service-<UTC timestamp>/`. Không tạo framework provisioning hoặc migration service mới; dùng Admin REST/Console và MCP đã có.

## Task 1: Thêm contract Keycloak mới và chứng minh token đúng trước cutover

**Files:** Modify realm JSON, Compose, `deploy/.env.example` và dotenv local; Create `internal-service.test.cjs`. Các file Postman chưa chuyển identity ở task này.

**Interfaces:**
- Consumes: realm runtime `flash-ticket`, client `user-management` có quyền Admin REST, cấu hình local hiện có.
- Produces: client `internal-service`, realm role `INTERNAL_SERVICE`, OIDC client scope `flash-ticket-api`, secret local `INTERNAL_SERVICE_CLIENT_SECRET`.
- Produces: backup `migration-state.local.json` gồm `phase`, `realm`, `clientIds`, `postmanIds`, `baselineUserIds`, `baselineConfigHashes`, `checks`; các export credentialed chỉ ở cùng thư mục temp.
- Phase values dùng xuyên suốt: `captured`, `keycloak-ready`, `postman-ready`, `retired`, `verified`. `checks` chỉ chứa tên kiểm tra và pass/fail/notRun, không JWT/secret.

- [x] **1. Capture baseline và backup có thể đọc lại.** Kiểm tra Git diff hiện tại, container/Compose thực sự đang chạy, live client scopes/mappings khác file import ở đâu, IDs collection/env từ MCP. Trước lần dùng Postman MCP đọc toàn bộ instructions resource của nó. Backup file local, main/seed collection, environments, bốn client cũ kèm service-account direct/effective role mappings, scope mappings và protocol mappers. Lưu human user IDs và cấu hình realm ngoài phạm vi để so sánh sau; giải quyết mọi chênh lệch bằng patch có chọn lọc, không export đè cả realm một cách mù quáng.
- [x] **2. Thêm test contract đang fail vào `internal-service.test.cjs`.** Dùng `node:test`/`assert/strict`, đọc JSON bằng `fs`; test `internal client is confidential and machine-only` assert `publicClient=false`, `clientAuthenticatorType=client-secret`, `serviceAccountsEnabled=true`, `standardFlowEnabled=false`, `directAccessGrantsEnabled=false`, `implicitFlowEnabled=false`, `fullScopeAllowed=false`. Test `internal audience mapper and role scope are explicit` assert mapper/config ở step 4 và assignment/scope mapping cho `INTERNAL_SERVICE`.

  Các assertions chính sau khi test resolve client/scope từ realm JSON:

  ```js
  assert.equal(client.clientId, 'internal-service');
  assert.equal(client.fullScopeAllowed, false);
  assert.equal(client.secret, '${INTERNAL_SERVICE_CLIENT_SECRET}');
  assert.equal(mapper.protocolMapper, 'oidc-audience-mapper');
  assert.equal(mapper.config['included.custom.audience'], 'flash-ticket-api');
  assert.equal(mapper.config['access.token.claim'], 'true');
  assert.equal(mapper.config['id.token.claim'], 'false');
  assert.deepEqual(serviceAccount.realmRoles, ['INTERNAL_SERVICE']);
  ```
- [x] **3. Chạy test để ghi nhận thiếu contract mới.** Run: `rtk proxy node --test deploy/keycloak/postman/internal-service.test.cjs`. Expected: các test contract mới FAIL vì chưa có client/role/scope mới; không chấp nhận lỗi syntax/missing file làm bằng chứng RED.
- [x] **4. Patch cấu hình durable theo contract.** Tạo client và role ở Interfaces; tạo scope OIDC `flash-ticket-api` với mapper `oidc-audience-mapper`, `included.custom.audience=flash-ticket-api`, `access.token.claim=true`, `id.token.claim=false`. Attach default vào `frontend` + `internal-service`. Internal dùng `basic`, `roles`, `flash-ticket-api`; không attach scopes làm sinh own-client audience. Gán scope mapping realm role `INTERNAL_SERVICE` cho Internal. JSON service account có `serviceAccountClientId=internal-service`, `realmRoles=[INTERNAL_SERVICE]`, không human default composite hoặc client roles Account/Admin. Giữ bốn clients cũ tạm thời; bỏ chúng ở Task 4.
- [x] **5. Đồng bộ secret local và Compose.** Sinh secret bằng nguồn ngẫu nhiên an toàn; ghi cùng giá trị vào dotenv active và env Internal local khi Task 2 tạo env. Realm JSON chỉ có placeholder `${INTERNAL_SERVICE_CLIENT_SECRET}`; Compose truyền biến đó qua environment. Không in resolved Compose config; kiểm tra syntax bằng `rtk proxy docker compose --env-file deploy/.env -f deploy/docker-compose.keycloak.yml config --quiet` (dùng `deploy/.env.dev` nếu đó là file active đã xác định).
- [x] **6. Upsert runtime theo `clientId`/role/scope name.** Admin REST dùng IDs resolve từ GET, không hardcode UUID cũ. Chỉ patch client/scope/mappings liên quan; bỏ human default/composite assignments trên đúng service account Internal, không sửa default roles realm-wide. Không restart/import để cập nhật realm tồn tại: `--import-realm` sẽ không ghi đè nó. Giữ nguyên global `roles` scope và audience-resolve mapper của human clients; nếu S2S vẫn thừa audience, sửa Internal effective assignments/scope attachments thay vì xóa mapper toàn realm.
- [x] **7. Kiểm chứng live token và assignment — KC-02/03/04/05.** POST `/realms/flash-ticket/protocol/openid-connect/token` bằng form client_credentials của Internal: HTTP 200, Bearer token, issuer đúng base URL + realm, `exp` tương lai, aud chuẩn hóa đúng `{flash-ticket-api}`, `realm_access.roles` chỉ role app `INTERNAL_SERVICE`; không Account/realm-management roles/aud. GET service-account effective realm/client roles để chứng minh không thừa hưởng `BUYER`, `ORGANIZER`, `ADMIN`, Account/Admin. Với secret sai yêu cầu client authentication error (Keycloak trả HTTP 401 `invalid_client`), không đưa token vào report. Không assert `azp`.
- [x] **8. Kiểm chứng login human — KC-06.** Dùng Playwright/Postman OAuth2 Authorization Code + PKCE S256, `prompt=login`, cho seed buyer và realm admin hiện có. Lấy fresh token, kiểm tra có `flash-ticket-api`, không `INTERNAL_SERVICE`; UserInfo/Account và admin List Users vẫn hoạt động. Cho phép audiences Keycloak khác trên human tokens. Nếu refresh token fixture hết hạn, login PKCE lại; không bật password grant cho `frontend`.
- [x] **9. Chạy test GREEN và ghi checkpoint `keycloak-ready`.** Run lại command step 3, expected PASS; chạy lại upsert với cùng state không tạo duplicate client/role/scope. Lưu bằng chứng KC-02–06 vào `checks`. Nếu fail: giữ bốn client cũ, không cho Task 4 retire.
- [x] **10. Lưu task diff riêng.** Review `rtk proxy git diff --check` và diff chỉ các file task. Commit `feat(keycloak): add shared internal service identity` nếu có thể stage riêng đúng task hunks; không gom thay đổi có từ trước, secret hoặc ignored docs. Nếu chưa tách được diff an toàn, giữ local và ghi rõ trạng thái chưa commit.

## Task 2: Postman Internal env, body và token lifecycle không dùng introspection

**Files:** Modify main collection; Create Internal example/local env; Extend `internal-service.test.cjs`. User/Admin env chỉ được bổ sung giá trị thiếu cần cho regression, không đổi profile/credential contract.

**Interfaces:**
- Consumes: Task 1 identity/secret, issuer `{{keycloakBaseUrl}}/realms/{{realm}}`, phase `keycloak-ready`.
- Produces: `03 - Internal Service` và `01 - Get Internal Service Token`; env **FlashTicket - Internal**.
- Env keys: `profile=internal`, `clientId=internal-service`, `clientSecret`, `accessToken`, `tokenExpiresAt` (epoch milliseconds string), `lastRequestError`, `keycloakBaseUrl`, `realm`, `enableServiceTemplates=false`; receiver URL/path và `orderId`, `buyerSubject`, `eventId` theo templates còn giữ.
- Scripts chỉ đọc/ghi token bằng `pm.environment`; không dùng collection/global token hoặc `pm.sendRequest` để tự lấy token nền.

- [x] **1. Thêm tests chạy script thật của collection bằng `vm`.** Theo pattern `self-profile.test.cjs`; mock `pm.environment`, `pm.execution.skipRequest`, `pm.test`, response, `Date` và `require('buffer')`. Helper `runInternalScripts({phase, env, response, claims})` trả `{env, skipped, tests, logs}` và chạy root/folder/request events theo thứ tự Postman. Fixture JWT chỉ để test script, không giả lập xác minh chữ ký.
- [x] **2. Pin các assertions âm/dương.** Tests: `failed token fetch clears stale internal token` (HTTP 400/401 không còn accessToken/expiry); `wrong profile leaves User and Admin environments untouched` (skip, báo lý do, token cũ User/Admin không đổi); `missing secret clears stale internal credentials` (Internal skip, token/expiry bị xóa); `valid response saves exp-based expiry only after all checks`; `wrong issuer, expired, missing role or extra audience cannot save`; `aud string and singleton array are accepted`; `changed or missing azp does not affect acceptance`; `templates are disabled and expired tokens show a reason`. Assert `lastRequestError` có lý do nhưng không echo secret/JWT; không tạo HTTP status giả cho local skip.

  Dùng fixture env profile Internal có token cũ, response lỗi và JWT fixture hợp lệ để pin lifecycle:

  ```js
  assert.equal(failed.env.accessToken, undefined);
  assert.equal(failed.env.tokenExpiresAt, undefined);
  assert.match(failed.env.lastRequestError, /HTTP 401/);
  assert.equal(wrongProfile.skipped, true);
  assert.equal(wrongProfile.env.accessToken, previousHumanToken);
  assert.equal(valid.env.accessToken, response.access_token);
  assert.equal(valid.env.tokenExpiresAt, String(claims.exp * 1000));
  assert.equal(extraAudience.env.accessToken, undefined);
  assert.equal(changedAzp.env.accessToken, changedAzpResponse.access_token);
  ```
- [x] **3. Chạy RED.** Run `rtk proxy node --test deploy/keycloak/postman/internal-service.test.cjs`; expected các tests Internal fail vì collection/env chưa có contract. Test self-profile hiện có phải vẫn chạy được bằng `rtk proxy node --test deploy/keycloak/postman/self-profile.test.cjs`.
- [x] **4. Tạo folder/token request/env.** Request NoAuth POST `{{keycloakBaseUrl}}/realms/{{realm}}/protocol/openid-connect/token`; body `urlencoded` có enabled fields `grant_type=client_credentials`, `client_id={{clientId}}`, `client_secret={{clientSecret}}`. Example có clientSecret/accessToken/tokenExpiresAt rỗng; local lấy secret Task 1. Bảo toàn collection UID; giữ request IDs cho các request được di chuyển.
- [x] **5. Viết guards/save script theo tests.** Profile sai: set lỗi + fail test + skip, kết thúc script và không thay token human. Profile Internal: clear token/expiry trước validation clientId/base URL/realm/secret, rồi mới gửi; điều chỉnh root guard cho token request Internal để clear diễn ra trước root base-URL validation, và thêm case missing base URL vào test stale-token. Save chỉ sau HTTP 200, Bearer response, JWT payload hợp lệ và issuer/exp/role/aud đúng toàn bộ. Invalid response phải fail assertion, giữ response/status thật và để token rỗng. Decode JWT là diagnostic; lỗi transport sau clear không được khôi phục token cũ.
- [x] **6. Hợp nhất templates theo service nhận.** Giữ ba contract B13: POST Booking `/internal/v1/orders/{{orderId}}/freeze-payment` với body cũ; GET Ticket `/internal/v1/orders/{{orderId}}/issuance`; GET Payment `/internal/v1/events/{{eventId}}/financial-summary`. Giữ tối đa một custom GET cho mỗi receiver Booking/Payment/Event/Ticket/User, body/path hiện có; nếu templates cùng receiver khác path thì giữ từng biến thể thực sự khác. Đổi label về receiver, không còn bốn caller identities. `enableServiceTemplates=false` là mặc định; khi bật vẫn yêu cầu fresh Internal token và tham số đủ. URL/path conflict giữa env cũ phải được báo, không âm thầm chọn một endpoint.
- [x] **7. Xóa toàn bộ introspection S2S và kiểm tra references.** Bỏ diagnostic folders/requests, `introspectionActive`, own-client audience và mọi guard `azp` trong S2S. Giữ nguyên login User/Admin, Account, Admin service token và error response visibility. Chưa xóa env cũ khỏi cloud/local cho tới Task 4.
- [x] **8. Chạy GREEN và live token lifecycle — PM-01/02/04.** Run `rtk proxy node --test deploy/keycloak/postman/internal-service.test.cjs deploy/keycloak/postman/self-profile.test.cjs`; expected PASS. Chạy riêng Internal token request bằng Postman CLI hoặc Send trong Postman, đúng env phải HTTP 200/save; secret sai HTTP 401/không stale token; missing secret/wrong env/expired token là local blocked với lý do. Không chạy cả collection có Admin write/email ngoài ý muốn.
- [x] **9. Lưu task diff riêng.** Commit task-only public JSON/tests với message `feat(postman): consolidate internal service requests` theo quy tắc Task 1 step 10; env local không stage. Checkpoint giữ `keycloak-ready` cho tới seed và cloud readback xong.

## Task 3: Seed regression dùng chung Internal và không ghi đè lịch sử

**Files:** Modify seed collection, Seed example/local env, `seed-credentials.json` local, runner; Extend `internal-service.test.cjs`; Create manifest/report mới qua runner.

**Interfaces:**
- Consumes: Internal identity/secret và persistent fixture accounts của seed hiện tại.
- Produces: Seed keys `internalClientId=internal-service`, `internalClientSecret`, `internalToken`; giữ `adminServiceClientId`, `adminServiceClientSecret`, `adminServiceToken` của `user-management` và human keys.
- Runner CLI thêm `--report <path>` và `--manifest <path>`, mặc định lần lượt `internal-service-migration-verification.json` và `internal-service-seed-manifest.json`. Report root `{timestamp, realm, acceptance, seedRegression, cloudReadback, notRun}`; Task 4 bổ sung section của mình mà không làm mất seedRegression.
- `seedRegression` giữ exitCode/stats/statuses/fixture count; không lưu response bodies, Authorization headers, token hoặc password. Refresh tokens rotated vẫn được persist vào file private trước khi CLI chạy như hiện tại.

- [x] **1. Thêm seed contract tests RED.** Assert một Internal token request, references dùng ba keys mới, không còn booking/payment/event/ticket client/token keys hoặc introspect cases, adminServiceToken và human token references không đổi. Assert tất cả biến `{{...}}` cần cho seed có nguồn env/builtin/local hợp lệ; negative missing grant_type vẫn tồn tại nhưng dùng credential Internal.
- [x] **2. Chuyển machine folder thành `30 - Machine token`.** Giữ `user-management` token case; thay bốn token + introspection cases bằng Internal token/save và bad-secret/missing-grant cases. Internal token assertions dùng cùng issuer/aud/role/exp contract Task 2, không `azp`. Seed requests ghi user vẫn chỉ nhắm fixture được allowlist; không chuyển sang người thật hoặc tự xóa fixture.
- [x] **3. Đổi seed env/private machine keys.** Example secret/tokens rỗng. Private `machineClients` giữ user-management, thêm internal-service, retire bốn entries cũ sau Task 4; backup giữ giá trị cũ. Không mất human account IDs, browser checks hoặc refresh tokens.
- [x] **4. Sửa runner để chọn output và báo cáo chính xác.** Thêm argparse trong `main() -> int`, entrypoint `raise SystemExit(main())`; output path theo Interfaces. Không ghi `seed-verification.json`/`seed-manifest.json` lịch sử theo mặc định. Tính danh sách templates chưa chạy từ collection hiện tại thay vì hardcode “19”; phân biệt failed assertion với skipped/notRun. Lọc log lỗi để không in token/secret/response body. Giữ cách chạy localhost qua CLI và kiểm tra final fixture roles/states.
- [x] **5. Chạy checks offline.** Run `rtk proxy node --test deploy/keycloak/postman/internal-service.test.cjs deploy/keycloak/postman/self-profile.test.cjs` expected PASS. Run `rtk proxy python deploy/keycloak/postman/run-seed-tests.py --help` expected mô tả hai output flags, không đọc/refresh token hoặc gọi HTTP khi chỉ hỏi help.
- [x] **6. Refresh login và chạy seed regression — KC-07/PM-02.** Chụp hash hai report/manifest lịch sử trước run. Nếu fixture refresh token fail, login PKCE lại qua Playwright với credential private sẵn có; không reset password. Run `rtk proxy python deploy/keycloak/postman/run-seed-tests.py --report deploy/keycloak/postman/internal-service-migration-verification.json --manifest deploy/keycloak/postman/internal-service-seed-manifest.json`. Expected exit 0, assertions đúng cả case negative, Internal token/save đúng và fixture retained; hashes lịch sử không đổi. Kiểm tra admin đọc list + update/assign role trên fixture, user self read/update và từ chối truy cập không được phép. Restore fixture fields đã tạm đổi nếu case yêu cầu, không xóa account.
- [x] **7. Lưu task diff riêng.** Commit `test(postman): migrate seed cases to shared internal identity` với public seed/runner/tests/report đã kiểm tra không secret; không private credentials/local env. Chưa retire client khi cloud readback chưa pass.

## Task 4: Đồng bộ cloud, đọc lại, cutover và kiểm chứng khả năng khôi phục

**Files:** Main/seed collection, example envs, realm import, report mới; xóa bốn example env cũ và cất local env cũ vào backup ngoài repo. Runtime/cloud thay đổi phải có checkpoint.

**Interfaces:**
- Consumes: `keycloak-ready`, Task 2/3 tests và seed regression PASS, backup/state Task 1.
- Produces: phase `postman-ready` trước retire; `retired` sau xóa bốn clients; `verified` sau final checks.
- `cloudReadback` report gồm collection/environment IDs, counts, mismatched request paths, kết quả compare auth/body/events/variables; không credential values.

- [x] **1. Cập nhật đúng collections trong workspace FlashTicket qua MCP.** Resolve lại UID bằng tên và backup ID; không tạo collection duplicate. Giữ discovery/user/admin request IDs và auth. Tạo **FlashTicket - Internal**; giữ User/Admin/Seed env. Chỉ đồng bộ secret qua cơ chế local/private của Postman; nếu MCP chỉ ghi shared value thì để field shared rỗng và nhập local trên Postman, không upload credential vào shared collection/example.
- [x] **2. Readback và compare — PM-03.** Lấy lại main/seed collection và env từ MCP; lưu raw snapshot vào temp private. So sánh normalized projection `{id, folderPath, method, url, auth, body, event}` và env keys/profile, chỉ bỏ server metadata/timestamps/order không mang nghĩa. Auth/body/headers/disabled field state/scripts không được bỏ qua. So sánh OAuth accessToken field sau sanitize về rỗng để không lấy token user ra report. Nếu lệch, sửa bằng granular request update rồi readback lại; không coi MCP success là bằng chứng đủ.
- [x] **3. Khóa điều kiện retire và ghi `postman-ready`.** Report/checks phải PASS KC-02–07 và PM-01–04, Internal cloud/local readback match, backup parse được. Mô phỏng một check fail trong bản state temp: đường quyết định retire phải dừng trước mọi DELETE; restore state thật ngay sau kiểm tra. Rerun discovery không tạo client/env duplicate. Không cần chạy lại toàn bộ suite khi không đổi input hoặc implementation.
- [x] **4. Retire bốn clients cũ bằng ID resolve từ đúng clientId.** Chỉ xóa `booking-service`, `payment-service`, `event-service`, `ticket-service` và service accounts thuộc chúng; giữ human users, `frontend`, `user-management`, built-ins. Mỗi DELETE có checkpoint kết quả để retry an toàn: client đã absent coi là hoàn tất, không thay target sang service khác. Xóa các client/service-account entries và mapping/dedicated scope cũ tương ứng trong import JSON; chỉ xóa scope nếu không còn client khác tham chiếu, giữ human/global scopes.
- [x] **5. Retire env/credential references cũ.** Xóa bốn cloud environments sau backup và readback đạt; xóa bốn example env files; chuyển local env cũ vào temp backup. Bỏ bốn private `machineClients` entries. Tìm stale identifiers chỉ trong active import/collections/env/runner/docs đang sửa, loại historical reports khỏi phép kiểm này. Tên host receiver như booking-service trong URL vẫn hợp lệ; không thay mọi chuỗi theo kiểu global replace.
- [x] **6. Verify final — KC-01/08.** Đối chiếu human user IDs với baseline (cho phép fixture được tạo bởi seed), built-in clients và cấu hình SMTP/theme/password policy không đổi. Fresh Internal token vẫn KC-02–05 PASS, fresh buyer/admin vẫn KC-06–07 PASS. Kiểm tra import/client scopes/role mappings tương ứng runtime sau khi bỏ các giá trị runtime-only và secrets. Nếu có Docker capacity, test fresh import bằng project/network/database/port tách riêng, không mount runtime volume, inject cùng secret placeholder; nếu không có, ghi `notRun` fresh-import, không đánh dấu đã thử.
- [x] **7. Chốt rollback instructions đã kiểm tra với backup.** Trước retire: restore chỉ file/collection/env thay đổi và bỏ new attachments/client nếu cần. Sau retire: recreate client từ backup theo `clientId`, resolve UUID mới rồi remap roles/scopes/service-account assignments theo tên, restore env/collections và kiểm tra token cũ theo contract trước migration. Không restore cả DB/realm, không dựa vào UUID cũ còn tồn tại. Không tự chạy rollback trên runtime đang thành công chỉ để thử; bằng chứng là backup đọc đủ mappings + rehearsal trên môi trường isolated nếu có. Nếu final fail sau partial retire, thực hiện rollback có chọn lọc này và report lỗi, không tiếp tục xóa.
- [x] **8. Lưu checkpoint/report `verified` và task diff.** Report không được biến notRun thành PASS; ghi rõ isolated import/rollback rehearsal có chạy hay không. Commit `chore(keycloak): retire per-service OAuth clients after verified cutover` chỉ task diff/public sanitized exports.

## Task 5: Export cuối và bàn giao Spring không gây hiểu nhầm

**Files:** README, SEED-README, new INTERNAL-SERVICE-HANDOFF, report mới; public collection/env/import final.

**Interfaces:**
- Consumes: Task 4 final artifacts + report, acceptance IDs bên dưới.
- Produces: bộ import/export portable, hướng dẫn chọn User/Admin/Internal/Seed env, contract Spring còn phải triển khai.

- [x] **1. Cập nhật README/SEED-README.** Liệt kê file import collection/env; local secret injection; OAuth User/Admin `Get New Access Token → Use Token → Send` để save; Internal `Send Get Internal Service Token` để save vào đúng env. Hướng dẫn đọc HTTP lỗi thật vs local skip, token expiry, missing variables, rollback/checkpoints và không dùng introspection làm prerequisite. Không sửa các report lịch sử hoặc claim toàn bộ business API đã chạy.
- [x] **2. Viết INTERNAL-SERVICE-HANDOFF.** Mã giả YAML/Java cho Gateway + receiver verify chữ ký/issuer/exp/nbf + audience `flash-ticket-api`; `issuer-uri` một mình không tự kiểm tra application audience hoặc map Keycloak realm roles. Mô tả audience property chỉ nếu Spring Boot version hỗ trợ, hoặc validator tương đương; realm role converter whitelist `BUYER`, `ORGANIZER`, `ADMIN`, `INTERNAL_SERVICE`. Không check `azp`.
- [x] **3. Pin phân quyền theo loại endpoint trong handoff.** Gateway deny `/internal/v1/**`; receiver internal yêu cầu `INTERNAL_SERVICE`; booking API user yêu cầu `BUYER` + ownership, không yêu cầu Internal role/aud. Outbound bốn services dùng registration `internal` client_credentials/OAuth2AuthorizedClientManager, reuse token khi còn hạn và xin mới khi hết hạn. Giữ user-management cho Keycloak Admin REST; một shared identity không phân biệt caller Booking/Payment, header không thay thế identity; offline JWT không revoke tức thì. Các assertions receiver HTTP 401/403 chỉ là checklist triển khai Spring sau này.
- [x] **4. Kiểm tra export và secret hygiene.** Parse tất cả JSON final; example secrets/tokens rỗng, realm Internal secret placeholder, không token OAuth nhúng vào collection. So sánh final artifact với readback Task 4, chạy lại Node tests chỉ nếu export/scripts đổi sau kiểm tra trước. `rtk proxy git diff --check` phải sạch; review danh sách files đổi để chứng minh không chạm Java/Spring config ngoài baseline user.
- [x] **5. Chốt report theo acceptance matrix trước cleanup.** Mỗi ID có status `pass|fail|notRun`, evidence cụ thể và timestamp; report chỉ summary claims/roles/aud, không token thô. Ghi rõ business S2S endpoint verification và Spring integration pending. Commit `docs(auth): document shared internal service contract and migration results` nếu file policy cho phép; ignored docs giao bằng link local, không force-add. Chuyển sang Task 6 để hoàn tất bản bàn giao gọn.

## Task 6: Xóa file test sau kiểm chứng, chỉ giữ tài liệu quan trọng và cấu hình sử dụng

**Files:** Create `deploy/keycloak/postman/MIGRATION-RESULTS.md`; update README/SEED-README; Delete các file test theo allowlist dưới đây. Không xóa file thực tế trong giai đoạn chỉ viết/review plan.

**Interfaces:**
- Consumes: checkpoint `verified`, report/manifest trung gian, kết quả tests và review cuối. Nếu còn kiểm tra bắt buộc fail hoặc còn cần test để sửa lỗi thì chưa cleanup.
- Produces: bộ cấu hình/export chạy được cùng các Markdown quan trọng; không còn file test local được liệt kê bên dưới. Kết quả test vẫn truy vết được qua MIGRATION-RESULTS.md.
- Cleanup chỉ là file local của Keycloak/Postman; seed users và seed collection/env trên Postman cloud vẫn giữ theo phạm vi trước đó. Backup rollback cần thiết giữ ngoài repo, không nằm trong bộ bàn giao.

- [x] **1. Chuyển bằng chứng cần giữ vào MIGRATION-RESULTS.md.** Ghi thời điểm, phiên bản công cụ, commands đã chạy, số assertions pass/fail thực tế, KC-01–08/PM-01–04 statuses/evidence, client/env IDs cần tra cứu, fixture username/ID/role/state, readback summary và notRun. Tóm tắt các report cũ có thông tin còn liên quan trước khi xóa. Không chép credential, JWT hoặc raw response chứa thông tin nhạy cảm.
- [x] **2. Chốt inventory cleanup bằng allowlist.** Đối chiếu đúng các file sau dưới `deploy/keycloak/postman/`: `internal-service.test.cjs`, `self-profile.test.cjs`, `run-seed-tests.py`, `FlashTicket.Seed.postman_collection.json`, `seed-credentials.json`, `seed-manifest.json`, `internal-service-seed-manifest.json`, `internal-service-migration-verification.json`, `verification.json`, `stability-verification.json`, `self-profile-verification.json`, `seed-verification.json`, `guard-verification.json`, `cloud-sync-verification.json`, `admin-oauth-verification.json`, `environments/Seed.example.postman_environment.json`, `environments/Seed.local.postman_environment.json`. File không tồn tại thì bỏ qua. Script/log/snapshot khác chỉ được bổ sung nếu ghi nhận là file tạm do đợt triển khai này tạo; không dùng wildcard quét/xóa toàn repo.
- [x] **3. Xóa file trong inventory sau khi review kết quả xong.** Dùng thao tác file theo đường dẫn literal đã kiểm tra nằm trong thư mục Postman của workspace. Xóa cache/output tạm đúng file của đợt này; không xóa main collection, realm import, Compose, env User/Admin/Internal, local runtime secrets, Markdown hoặc dữ liệu Keycloak. Không chạy lại runner đã xóa và không tạo lại file test sau cleanup.
- [x] **4. Sửa tài liệu để không trỏ tới file đã xóa.** README trỏ tới main collection + env dùng thực tế; SEED-README chỉ giữ mô tả case/fixture và collection cloud đã giữ, trỏ tới MIGRATION-RESULTS.md. Nêu runner/seed exports local đã được dùng để kiểm chứng rồi dọn; không để command thực thi yêu cầu file không còn. Spec/plan giữ lịch sử các bước test nhưng ghi rõ test files là tạm và Task 6 đã cleanup khi thực hiện.
- [x] **5. Kiểm tra bản bàn giao sau cleanup.** Inventory allowlist không còn file; các JSON sử dụng thực tế parse được; liên kết/commands hoạt động trong README không tham chiếu file đã xóa; Markdown còn đủ acceptance evidence. Run `rtk proxy git diff --check`, expected không có lỗi whitespace từ task. Không cần tạo test mới hoặc lặp lại mutation tests chỉ để kiểm tra thao tác xóa file.
- [x] **6. Bàn giao cuối.** Commit task-only cleanup với message `chore(auth): remove temporary test artifacts after verification` nếu có thể stage an toàn. Báo các paths main JSON/env và Markdown quan trọng, kết quả test trước cleanup, giới hạn notRun và trạng thái commit. Không đánh dấu hoàn tất nếu chưa lưu bằng chứng hoặc còn file test trong allowlist.

## Acceptance coverage và điều kiện hoàn thành

| Acceptance | Task / bằng chứng bắt buộc |
|---|---|
| KC-01 | Task 4: inventory clients/service accounts/human user IDs trước/sau |
| KC-02 | Tasks 1/2/4: token mới, issuer/exp/role/aud, save đúng env |
| KC-03 | Tasks 1/2/3: secret sai, client auth error, không giữ token cũ |
| KC-04 | Tasks 1/2: aud string/array chuẩn hóa thành đúng singleton |
| KC-05 | Task 1 + final Task 4: direct/effective assignments và token không leak quyền |
| KC-06 | Tasks 1/4: fresh buyer/admin PKCE, human có API aud và không Internal role |
| KC-07 | Task 3: UserInfo/Account/Admin read/seed write/assign roles regression |
| KC-08 | Tasks 1/4/5: placeholder/Compose/runtime-export consistency; isolated import status thật |
| PM-01 | Tasks 2/3/4: một Internal identity, body/save đúng, bỏ introspection/old identity references |
| PM-02 | Tasks 2/3: profile/secret/token invalid/expiry có lỗi nhìn thấy và env isolation |
| PM-03 | Tasks 4/5: MCP readback match, portable exports không secret/token |
| PM-04 | Tasks 2/3/5: template mặc định disabled, report ghi notRun đúng |

Hoàn thành phạm vi khi các kiểm tra bắt buộc PASS, exports/readback khớp, đã bàn giao Spring và cleanup Task 6 xong. Bằng chứng cuối lưu ở MIGRATION-RESULTS.md; các file test/report JSON chỉ tồn tại trong giai đoạn kiểm chứng. Không yêu cầu “pass” cho business endpoints chưa tồn tại, gửi email thật hoặc isolated fresh-import/rollback rehearsal chưa có môi trường; Markdown phải giữ các giới hạn đó rõ ràng.

## Self-review của plan

- Spec sections 1–4 → Task 1; section 5 → Tasks 2–4; section 6 → Task 5; migration/rollback section 7 → Tasks 1/4; acceptance/deliverables sections 8–9 → matrix + Task 5.
- Năm Review Focus đều có bước kiểm chứng sở hữu; profile Internal lowercase không thay profile User/Admin hiện có.
- Checkpoint names, Internal env keys, Seed keys và runner output paths thống nhất giữa tasks.
- Không thêm provisioning framework/dependency, không triển khai Spring, không tự reset dữ liệu hoặc gửi email; tests tập trung token/security scripts và regression thực sự.
- Yêu cầu bổ sung ngày 2026-10-11 → Task 6: test/review xong mới dọn; giữ bằng chứng Markdown và cấu hình/import/export dùng thực tế. Quy tắc không ghi đè report lịch sử áp dụng trong khi test; cleanup được phép xóa các report thuộc allowlist sau khi tóm tắt thông tin cần giữ.
- Execution: inline (Native), hoàn tất 11/10/2026; reviewer độc lập gpt-6-astra. File test/runner/report JSON là tạm, Task 6 đã cleanup. Xem deploy/keycloak/postman/MIGRATION-RESULTS.md để tra bằng chứng, notRun và các bước commit để local theo ruling; checkbox commit nghĩa là đã xử lý disposition, không khẳng định có commit mới.
