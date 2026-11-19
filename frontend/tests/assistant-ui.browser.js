// Browser fixture only: no API, login or payment request leaves this test.
async (page, origin = "http://127.0.0.1:5173") => {
  if (!["http://127.0.0.1:5173", "http://localhost:5173"].includes(origin)) throw new Error("Assistant fixture requires local Vite");
  const context = page.context();
  const [conversationId, eventId, draftId, ticketTypeId, orderId, existingOrderId] = Array.from(
    { length: 6 }, (_, index) => `00000000-0000-4000-8000-00000000000${index + 1}`,
  );
  const eventTitle = "Đêm nhạc FlashTicket — Chuyến lưu diễn mùa hè tại Nhà hát thành phố";
  const createdAt = new Date().toISOString();
  const expiresAt = new Date(Date.now() + 600_000).toISOString();
  const items = [{ ticketTypeId, quantity: 2 }];
  const ticketTypes = [{ ticketTypeId, name: "Vé tiêu chuẩn", unitPrice: "150000", currency: "VND" }];
  const draft = { draftId, eventId, eventTitle, estimatedTotal: "300000", currency: "VND", items, ticketTypes, availabilityAsOf: createdAt, expiresAt };
  const initialVersion = "00000000-0000-4000-8000-000000000010";
  const conversation = { conversationId, version: initialVersion, expiresAt, messages: [] };
  const events = [{ eventId, title: eventTitle, ticketTypes, mode: "QUANTITY" }];
  const orders = [
    { orderId: existingOrderId, eventTitle, state: "PENDING_PAYMENT", total: "300000", currency: "VND", expiresAt },
    { orderId: "invalid-id", eventTitle: "Invalid fixture order" },
  ];
  const customerEmail = "buyer@example.invalid";
  const chat = [], bookings = [], unexpected = [], errors = [], checks = [];
  const streaming = new Map();
  let reads = 0, readStatus, nextMessage;
  const api = "/api/v1/assistant";
  const current = `${api}/conversations/current`;
  const assert = (ok, message) => { if (!ok) throw new Error(message); };

  await context.route("**/*", async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    const headers = {
      "access-control-allow-origin": origin,
      "access-control-allow-headers": "authorization, content-type, idempotency-key",
      "access-control-allow-methods": "GET, POST, OPTIONS",
    };
    const reply = (data, status = 200) => route.fulfill({ status, json: data, headers });
    if (!path.startsWith("/api/")) {
      return url.origin === origin && ["GET", "HEAD"].includes(method) ? route.continue() : route.abort();
    }
    if (method === "OPTIONS") return route.fulfill({ status: 204, headers });
    if (path === current && method === "POST") {
      return reply(conversation);
    }
    if (path === current && method === "GET") { reads++; return readStatus ? reply({}, readStatus) : reply(conversation); }
    if (path === "/api/v1/orders" && method === "POST") {
      const body = request.postDataJSON();
      bookings.push({ body, key: request.headers()["idempotency-key"] });
      return reply({ orderId, eventId, state: "PENDING_PAYMENT", customerEmail, currency: "VND", total: "300000", expiresAt,
        items: [{ ticketTypeId, quantity: 2, unitPrice: "150000" }] }, 201);
    }
    if (method !== "GET" || path.startsWith(api) || /\/payments?(?:\/|$)/.test(path)) unexpected.push(`${method} ${path}`);
    if (path === "/api/v1/me") return reply({ displayName: "Fixture buyer" });
    return reply({}, 404);
  });

  const prepare = async (target, role, language = "vi") => {
    target.on("pageerror", error => errors.push(error.message));
    await target.exposeFunction("beginAssistantFixture", ({ body, authorization, accept }) => {
      assert(authorization === "Bearer fixture-token" && accept === "text/event-stream, application/problem+json",
        "SSE POST must use the normal authenticated client");
      chat.push(body);
      assert(nextMessage, "Unscheduled SSE request");
      const fixture = nextMessage;
      nextMessage = null;
      if (!fixture.status) streaming.set(body.requestId, { ...fixture, body });
      return { status: fixture.status || 200, oversize: fixture.outcome === "oversize" };
    });
    await target.exposeFunction("finishAssistantFixture", async requestId => {
      const { response, ready, outcome, body } = streaming.get(requestId);
      streaming.delete(requestId);
      await ready;
      if (outcome === "beforeCommit" || outcome === "oversize") return "";
      if (outcome === "turnError") return `event:turn_error\ndata:${JSON.stringify({ requestId, code: "ASSISTANT_CAPACITY_EXCEEDED" })}\n\n`;
      if (outcome === "conflict") {
        conversation.version = "00000000-0000-4000-8000-000000000011";
        conversation.lastRequestId = "00000000-0000-4000-8000-000000000012";
        return `event:turn_error\ndata:${JSON.stringify({ requestId, code: "CONVERSATION_CONFLICT" })}\n\n`;
      }
      conversation.version = requestId;
      conversation.lastRequestId = requestId;
      conversation.messages.push(
        { id: `${requestId}-user`, role: "user", text: body.message, createdAt },
        { id: `${requestId}-assistant`, role: "assistant", createdAt, ...response },
      );
      if (outcome === "afterCommit") return "";
      if (outcome === "malformed") return "event:result\ndata:invalid-json\n\n";
      return `event:result\rdata:${JSON.stringify({ requestId, conversationId, version: conversation.version })}\r\r`;
    });
    await target.addInitScript(({ role, language, streamPath }) => {
      localStorage.setItem("flashTicket.language", language);
      const fetchOriginal = window.fetch.bind(window);
      window.fetch = async (input, init) => {
        const request = new Request(input, init);
        if (new URL(request.url).pathname !== streamPath) return fetchOriginal(input, init);
        const body = await request.json();
        const { status, oversize } = await window.beginAssistantFixture({ body, authorization: request.headers.get("authorization"), accept: request.headers.get("accept") });
        if (status !== 200) return new Response("{}", { status, headers: { "content-type": "application/problem+json" } });
        const encoder = new TextEncoder();
        const first = encoder.encode(`: chờ\r\nevent:processing\r\ndata:{\r\ndata:"requestId":"${body.requestId}"}\r\n\r\n`);
        const stream = new ReadableStream({
          start(controller) {
            request.signal.addEventListener("abort", () => controller.error(request.signal.reason), { once: true });
            for (const [start, end] of [[0, 5], [5, first.length - 1], [first.length - 1, first.length]]) {
              controller.enqueue(first.slice(start, end));
            }
            if (oversize) controller.enqueue(encoder.encode(`:${"x".repeat(8192)}\n\n`));
            window.finishAssistantFixture(body.requestId).then(terminal => {
              if (terminal) controller.enqueue(encoder.encode(terminal));
              controller.close();
            }).catch(error => controller.error(error));
          },
        });
        return new Response(stream, { headers: { "content-type": "text/event-stream;charset=UTF-8" } });
      };
      const authenticated = Boolean(role);
      window.__KEYCLOAK_INSTANCE__ = {
        authenticated, token: authenticated ? "fixture-token" : undefined,
        idToken: authenticated ? "fixture-id-token" : undefined,
        tokenParsed: authenticated ? { iss: "fixture", sub: `assistant-fixture-${role}`, realm_access: { roles: [role] } } : undefined,
        hasRealmRole(name) { return name === role; },
        init() { setTimeout(() => this.onReady?.(authenticated), 0); return Promise.resolve(authenticated); },
        isTokenExpired() { return false; }, updateToken() { return Promise.resolve(false); },
        login() { return Promise.resolve(); }, logout() { return Promise.resolve(); },
      };
    }, { role, language, streamPath: `${api}/messages/stream` });
  };
  try {
    await prepare(page, "BUYER");
    await page.goto(origin);
    const launcher = page.locator(".floating-chatbot-trigger");
    const panel = page.locator(".floating-chatbot-panel");
    await launcher.click();
    const input = panel.locator(".floating-chatbot-input input");
    await panel.locator(".floating-chatbot-input input:not(:disabled)").waitFor();
    await input.fill("Kiểm tra đơn vé gần đây");
    let release;
    nextMessage = { response: { text: "Không tìm thấy đơn.", orders: [] }, ready: new Promise(resolve => { release = resolve; }) };
    try {
      await input.press("Enter");
      const progress = panel.getByRole("status").filter({ hasText: "Trợ lý đang xử lý" });
      await progress.waitFor({ timeout: 7000 });
      assert(await input.isDisabled(), "The composer must stay disabled while streaming");
      assert(reads === 0 && await panel.getByText("Không tìm thấy đơn.").count() === 0,
        "Processing must appear before the turn is committed or any transcript GET");
    } finally { release(); }
    await panel.locator('.assistant-order-card a[href="/my-orders"]').waitFor();
    assert(chat.length === 1 && chat[0].conversationId === conversationId && chat[0].version === initialVersion && chat[0].requestId
      && reads === 1, "Send must reconcile one SSE turn from GET");
    checks.push("widget progress before terminal, single GET and empty orders");

    nextMessage = { response: { text: "Đây là sự kiện, đơn và nháp vé của bạn.", events, orders, draft } };
    await input.fill("Xem sự kiện, đơn và nháp vé");
    await input.press("Enter");
    await panel.locator(`a[href="/event/${eventId}"]`).waitFor();
    await panel.getByRole("link", { name: "Mở rộng" }).click();
    await page.waitForURL(`${origin}/chatbot`);
    await launcher.waitFor({ state: "detached" });
    const shell = page.locator(".chatbot-shell");
    await shell.locator("form.assistant-purchase-form").waitFor();
    assert(await shell.locator("form.assistant-purchase-form").count() === 1
      && await shell.locator(`a[href="/my-orders?orderId=${existingOrderId}"]`).count() === 1
      && bookings.length === 0, "GET renders one draft and order without creating a booking");
    checks.push("GET transcript with one event, order and purchase draft");

    const fullInput = shell.getByRole("textbox", { name: "Tin nhắn cho trợ lý" });
    const fullError = shell.getByRole("alert");
    const fullPageTurn = async (message, fixture) => {
      const [sentBefore, readsBefore] = [chat.length, reads];
      nextMessage = fixture;
      await fullInput.fill(message);
      await fullInput.press("Enter");
      await page.waitForFunction(() => !document.querySelector(".chatbot-shell .assistant-progress")?.textContent?.trim(), undefined, { timeout: 7000 });
      assert(chat.length === sentBefore + 1 && reads === readsBefore + 1, "Each SSE send must reconcile with exactly one GET");
      return chat.at(-1);
    };
    const uncertain = await fullPageTurn("Kiểm tra lại đơn", { outcome: "beforeCommit" });
    await fullError.filter({ hasText: "Chưa xác nhận được kết quả" }).waitFor();
    assert(await fullInput.inputValue() === uncertain.message, "Retry must preserve the original text");
    const recovered = await fullPageTurn(uncertain.message, { outcome: "afterCommit", response: { text: "Đã khôi phục lượt qua GET." } });
    assert(JSON.stringify(recovered) === JSON.stringify(uncertain)
      && await shell.getByText("Đã khôi phục lượt qua GET.", { exact: true }).count() === 1,
      "Disconnect after commit must reconcile once and retry must reuse the exact request");
    await fullPageTurn("Khung bị lỗi", { outcome: "malformed", response: { text: "Đã đọc lại sau khung hỏng." } });
    assert(await shell.getByText("Đã đọc lại sau khung hỏng.", { exact: true }).count() === 1, "Malformed terminal must reconcile from GET");
    let releaseOversize;
    try {
      await fullPageTurn("Thử giới hạn", { outcome: "oversize", ready: new Promise(resolve => { releaseOversize = resolve; }) });
    } finally { releaseOversize(); }
    await fullError.filter({ hasText: "Chưa xác nhận được kết quả" }).waitFor();
    for (const [status, errorText] of [[401, "Phiên đăng nhập đã hết hạn"], [403, "không có quyền"],
      [429, "đạt giới hạn yêu cầu"], [503, "Trợ lý đang bận"]]) {
      await fullPageTurn("Thử giới hạn", { status });
      await fullError.filter({ hasText: errorText }).waitFor();
    }
    await fullPageTurn("Thử giới hạn", { outcome: "turnError" });
    await fullError.filter({ hasText: "Trợ lý đang bận" }).waitFor();
    await fullPageTurn("Thử giới hạn", { outcome: "conflict" });
    await shell.getByText("Hội thoại đã thay đổi ở nơi khác.", { exact: false }).waitFor();

    await page.clock.install();
    let releaseTimeout;
    const readsBeforeTimeout = reads;
    nextMessage = { outcome: "beforeCommit", ready: new Promise(resolve => { releaseTimeout = resolve; }) };
    try {
      await fullInput.fill("Thử hết thời gian");
      await fullInput.press("Enter");
      await shell.getByRole("status").filter({ hasText: "Trợ lý đang xử lý" }).waitFor();
      assert(reads === readsBeforeTimeout, "GET must wait until timeout when the stream has no terminal event");
      await page.clock.fastForward(60_000);
      await fullError.filter({ hasText: "Chưa xác nhận được kết quả" }).waitFor({ timeout: 7000 });
      assert(reads === readsBeforeTimeout + 1, "Stream timeout must allow one GET with the lifecycle signal");
    } finally { releaseTimeout(); }
    readStatus = 410;
    await fullPageTurn("Thử hết thời gian", { outcome: "beforeCommit" });
    await shell.getByText("Hội thoại đã hết hạn.", { exact: false }).waitFor();
    assert(await fullInput.isDisabled(), "Expired transcript must disable sending until the conversation is reopened");
    readStatus = undefined;
    checks.push("full-page SSE, disconnect/retry, malformed/oversized frames, HTTP/turn errors, conflict, 60s timeout and GET 410");

    const english = await context.newPage();
    try {
      await prepare(english, "BUYER", "en");
      await english.goto(`${origin}/chatbot`);
      await english.locator(".floating-chatbot-input input:not(:disabled)").waitFor();
      const [sentBeforeLogout, readsBeforeLogout] = [chat.length, reads];
      const englishInput = english.locator(".floating-chatbot-input input");
      await english.evaluate(() => {
        const auth = window.__KEYCLOAK_INSTANCE__;
        auth.isTokenExpired = () => true;
        auth.updateToken = () => new Promise(resolve => { window.finishFixtureRefresh = resolve; });
      });
      await englishInput.fill("Pending refresh");
      await englishInput.press("Enter");
      await english.waitForFunction(() => Boolean(window.finishFixtureRefresh));
      await english.evaluate(() => {
        const auth = window.__KEYCLOAK_INSTANCE__;
        auth.authenticated = false; auth.token = undefined; auth.idToken = undefined; auth.tokenParsed = undefined;
        auth.onAuthLogout?.();
        window.finishFixtureRefresh(false);
      });
      await englishInput.waitFor({ state: "detached", timeout: 7000 });
      assert(chat.length === sentBeforeLogout && reads === readsBeforeLogout, "Logout during refresh must prevent the old POST and GET");
      checks.push("logout during JWT refresh blocks stale POST/GET");

      await english.evaluate(() => {
        const auth = window.__KEYCLOAK_INSTANCE__;
        auth.authenticated = true; auth.token = "fixture-token"; auth.idToken = "fixture-id-token";
        auth.tokenParsed = { iss: "fixture", sub: "assistant-fixture-BUYER", realm_access: { roles: ["BUYER"] } };
        auth.isTokenExpired = () => false;
        auth.onAuthSuccess?.();
      });
      let releaseLogout;
      nextMessage = { outcome: "beforeCommit", ready: new Promise(resolve => { releaseLogout = resolve; }) };
      try {
        await englishInput.fill("Turn open during logout");
        await englishInput.press("Enter");
        await english.getByRole("status").filter({ hasText: "Assistant is processing" }).waitFor({ timeout: 7000 });
        await english.evaluate(() => {
          const auth = window.__KEYCLOAK_INSTANCE__;
          auth.authenticated = false; auth.token = undefined; auth.idToken = undefined; auth.tokenParsed = undefined;
          auth.onAuthLogout?.();
        });
        await englishInput.waitFor({ state: "detached", timeout: 7000 });
      } finally { releaseLogout(); }
      assert(chat.length === sentBeforeLogout + 1 && reads === readsBeforeLogout, "Logout while reading must prevent stale reconciliation");
      checks.push("logout during stream blocks stale reconciliation");
    } finally { await english.close(); }
    assert(unexpected.length === 0 && errors.length === 0, `Unexpected API/page errors: ${[...unexpected, ...errors].join("; ")}`);
    return { checks: [...checks, "VI/EN progress and no unexpected writes"] };
  } finally {
    // Keep requests mocked until the runner closes this isolated context.
    await page.close();
  }
};
