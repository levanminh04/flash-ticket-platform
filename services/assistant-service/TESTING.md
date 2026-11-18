# Buyer Assistant — kiểm chứng

Cập nhật: **2026-11-18**. Đây là kết quả local/fixture, không phải xác nhận đã deploy hoặc sẵn sàng production. Không commit/push/deploy, không gọi proxy/model thật, không dùng dữ liệu đơn thật hoặc tạo Booking order.

## Kết quả thực tế

| Kiểm tra | Kết quả | Giới hạn |
|---|---|---|
| Assistant offline `verify` với JDK 21.0.13 và issuer fixture | PASS: 15 tests, 0 failures/errors/skips; Boot JAR chứa adapter OpenAI 1.23.0, không chứa adapter Gemini cũ hay `.env` | Proxy HTTP loopback và Booking/Redis mock; không gọi proxy thật. |
| K3s deploy fixture | PASS: secret allowlist của Assistant yêu cầu `ASSISTANT_MODEL_API_KEY`, bỏ biến ngoài allowlist, không fallback Compose | Kubernetes giả lập; không deploy hoặc sửa GitHub secret. |
| Assistant Docker image local (trước đổi adapter) | PASS: UID/GID 10001, Java 21, có `curl`/`jq` và `/app/app.jar`, không có `/app/.env` | Kiểm runtime tools với `--network none`; chưa rebuild image cho adapter mới. |
| Gateway test với JDK 21 | FAIL: 115 tests, 1 failure, 0 errors/skips | `GatewayRouteConfigurationTests.hasExactlySevenRoutes` kỳ vọng 7 route; cấu hình có 8, gồm `assistant`. |
| Frontend build sau thay đổi cuối cùng | PASS, exit 0: TypeScript + Vite, 2.129 modules, 10,39s | Vite cảnh báo JS chunk khoảng 2.240,52 kB sau minify (>500 kB). Không phải browser acceptance. |
| Frontend lint | FAIL, exit 2 | ESLint 8.57.1 không tìm thấy configuration file. Không thêm framework/config ngoài scope để che lỗi. |
| `git diff --check` | PASS ở thời điểm kiểm | Không kiểm hành vi/runtime và không bao phủ file untracked như một tracked diff. |
| Redis integration | NOT RUN | Có `RedisConversationStoreIT`, chưa có test instance/env namespace được xác nhận. |
| Browser / Gateway integration / live Gemini / Booking thật | NOT VERIFIED | Các điều kiện chạy bên dưới chưa đầy đủ. |

### Tái lập kiểm tra offline từ repository root (Git Bash, JDK 21)

```bash
dotenv -f services/assistant-service/src/test/fixtures/mvc.env run -- "$(cygpath -w "$(command -v bash)")" -c 'mvn -o -B -ntp -f services/assistant-service/pom.xml verify'
```

CI nạp cùng fixture qua `GITHUB_ENV`. Fixture chỉ cung cấp issuer giả `https://issuer.test` cho MVC/security; không có cấu hình runtime hoặc secret.

```bash
mvn -o -B -ntp -f infrastructure/api-gateway/pom.xml test
```

```bash
npm --prefix frontend run build
```

```bash
npm --prefix frontend run lint
```

## Phạm vi test đã viết

- `BuyerAssistantToolsTests`: đúng sáu tools; draft QUANTITY từ event/availability fixture, BigDecimal estimate, UUID/expiry; boundary quantity/duplicate/page/state, lease/JWT/deadline và individual call limit.
- `BuyerApiClientTests`: loopback JDK HttpServer; fixed GET/current Bearer, không follow redirect/fallback credential, DTO bỏ field riêng tư, lỗi status/malformed JSON và cap body plain/gzip. **Chưa có ca HTTP timeout thực tế**.
- `AssistantConversationServiceTests`: success-only whole-turn commit, replay, owner separation/redaction, byte budget và tool pairing, late result/reset/deadline với store mock. Mock fencing không chứng minh Redis Lua thật.
- `BuyerAssistantAgentContractTests`: giữ test scripted cho schema, memory riêng và tool-round bound. Test loopback mới dùng `OpenAiChatModel` thật: `POST /v1/chat/completions`, đúng model và Bearer fixture, `max_tokens=2048`, sáu tool schemas, khứ hồi `tool_calls`/`tool_call_id` và trả lời cuối. JWT BUYER chỉ đi tới Booking mock, không xuất hiện trong request model. Không chứng minh proxy thật hỗ trợ alias, giới hạn hoặc tool calling.
- `AssistantControllerTests`, `AssistantSecurityTests`: bốn ca PASS với issuer fixture, gồm validation, safe errors/no-store, buyer role và signed-token fixtures.
- `RedisConversationStoreIT`: hai store instances, TTL/open/read/replay, CAS/reset/lease fencing, quota và corrupt state. Acquire giữa hai instance hiện là tuần tự, **chưa chứng minh contention đồng thời bằng load/barrier**.
- Gateway: các ca buyer matcher/preflight không lỗi; suite còn một assertion đếm route cũ. Chưa kiểm lưu lượng qua Gateway thật.

## Điều kiện để chạy tiếp

- JDK 21 thực tế; không bỏ enforcer hoặc đổi target để lấy kết quả xanh.
- Nạp cấu hình từ env-file dự án bằng cơ chế IDE/runtime hiện có. Spring/Maven không tự nạp `.env`. Không dùng biến tạm, CLI/JVM/system-property hoặc run configuration riêng để bù biến thiếu.
- Env-file runtime cần đồng bộ ba biến `ASSISTANT_MODEL_*` theo [`.env.example`](.env.example); nhập key mới trực tiếp vào file riêng. Template không chứa secret thật. Lần kiểm này không sửa `.env` local.
- Trước live, kiểm port, Event/Booking origins và Redis namespace của đúng môi trường. Chỉ dùng dữ liệu giả/công khai để kiểm alias `gemini-3.8-flash-high`, tool calling nhiều lượt, giới hạn và latency trên proxy thật. Chưa kiểm log, retention và chính sách dữ liệu của proxy/upstream; không gửi đơn riêng tư hoặc tạo order/reservation thật khi chưa được phép.

### Redis IT — chưa chạy

Chỉ sau khi env-file test đã được xác nhận và nạp, có Redis instance được phép ghi key test:

```bash
mvn -B -f services/assistant-service/pom.xml -Pintegration verify
```

IT đọc `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `ASSISTANT_REDIS_KEY_PREFIX` từ môi trường đã nạp. Prefix test bắt buộc chứa `:test:` và dùng bộ ký tự giới hạn; mỗi test nối UUID riêng. Không dùng prefix runtime/Booking. Cleanup chỉ đúng state/lease/quota test; không FLUSHDB hoặc quét xóa namespace rộng. Thiếu host/port/prefix có thể khiến IT skip — **skip không phải PASS**. Giữ issuer fixture hợp lệ để `verify` chạy cả unit suite.

## Checklist nghiệm thu

- [x] Java 21 compile/tests; bốn MVC/security tests chạy thực sự.
- [ ] Redis Lua trên namespace test, expiry/TTL và race giữa replicas.
- [ ] Gateway route/security/timeouts thực tế, không chỉ đọc YAML.
- [ ] Widget → page → reload giữ chung hội thoại; React StrictMode không kẹt busy.
- [ ] A pending → logout/B login hoặc mất BUYER: không lộ history/draft, không gửi JWT B cho thao tác A.
- [ ] Reset pending/stale tab/expiry không hồi sinh context hoặc tự resend prompt.
- [ ] Chat “đồng ý” không tạo order; draft chưa confirm không reserve tồn.
- [ ] Form double-click/two tabs/reload/uncertain POST giữ đúng draft key và frozen payload/email.
- [ ] Booking trả OrderView thật rồi mới navigate; sold-out/price/state errors hiển thị đúng.
- [ ] Mobile/keyboard/focus/reset/login và Web Locks support thực tế.
- [x] OpenAI-compatible tool round trip với proxy loopback, không gửi JWT BUYER.
- [ ] Proxy thật: alias, tool calling nhiều lượt, giới hạn, latency và chính sách dữ liệu của proxy/upstream.

## Giới hạn cần biết

Pre-confirm GET và Booking POST không cùng transaction với Assistant reset. Reset không thu hồi order đã đến Booking; kiểm tra frontend chỉ giảm stale submits, không phải atomic cross-service fencing. Sau draft expiry form không cho retry, kể cả POST trước đó chưa rõ kết quả; có thể cần tra đơn qua UI/API của đúng owner. Nếu draft đã bị evict khỏi transcript, pre-confirm không còn tìm được draft và chặn submit.

Redaction email/Bearer/JWT nhận diện được không phải DLP toàn diện. TTL Redis không xóa provider retention/backup/log hạ tầng. Cancel Future không chứng minh provider đã ngừng hoặc không tính phí; chưa có global daily spend cap. Không có kết luận chịu tải hoặc production readiness từ các kiểm tra trên.
