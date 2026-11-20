// Browser fixture only: no API, login or payment request leaves this test.
async (page, origin = "http://127.0.0.1:5173", screenshotDir) => {
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
  let reads = 0, opens = 0, failOpen = false, readStatus, nextMessage;
  const api = "/api/v1/assistant";
  const current = `${api}/conversations/current`;
  const organizerApi = `${api}/organizer`;
  const organizerConversation = { ...conversation, conversationId: "00000000-0000-4000-8000-000000000013", messages: [] };
  const adminApi = api + "/admin";
  const adminConversation = { ...conversation, conversationId: "00000000-0000-4000-8000-000000000015", messages: [] };
  let organizerReads = 0, adminReads = 0, adminOpens = 0, adminAborts = 0;
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
      opens++;
      return failOpen ? reply({}, 503) : reply(conversation);
    }
    if (path === current && method === "GET") { reads++; return readStatus ? reply({}, readStatus) : reply(conversation); }
    if (path === `${organizerApi}/conversations/current`) {
      if (method === "GET") organizerReads++;
      return reply(organizerConversation);
    }
    if (path === adminApi + "/conversations/current") {
      if (method === "GET") adminReads++;
      if (method === "POST") adminOpens++;
      return reply(adminConversation);
    }
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
    await target.exposeFunction("beginAssistantFixture", ({ body, authorization, accept, path }) => {
      assert(authorization === "Bearer fixture-token" && accept === "text/event-stream, application/problem+json",
        "SSE POST must use the normal authenticated client");
      chat.push(body);
      assert(nextMessage, "Unscheduled SSE request");
      const fixture = nextMessage;
      nextMessage = null;
      const scopedConversation = path.startsWith(adminApi) ? adminConversation
        : path.startsWith(organizerApi) ? organizerConversation : conversation;
      assert(body.conversationId === scopedConversation.conversationId, "SSE request used the wrong workspace");
      if (!fixture.status) streaming.set(body.requestId, { ...fixture, body, scopedConversation });
      return { status: fixture.status || 200, oversize: fixture.outcome === "oversize" };
    });
    await target.exposeFunction("abortAssistantFixture", path => {
      if (path === adminApi + "/messages/stream") adminAborts++;
    });
    await target.exposeFunction("finishAssistantFixture", async requestId => {
      const { response, ready, outcome, body, scopedConversation } = streaming.get(requestId);
      streaming.delete(requestId);
      await ready;
      if (outcome === "beforeCommit" || outcome === "oversize") return "";
      if (outcome === "turnError") return `event:turn_error\ndata:${JSON.stringify({ requestId, code: "ASSISTANT_CAPACITY_EXCEEDED" })}\n\n`;
      if (outcome === "conflict") {
        scopedConversation.version = "00000000-0000-4000-8000-000000000011";
        scopedConversation.lastRequestId = "00000000-0000-4000-8000-000000000012";
        return `event:turn_error\ndata:${JSON.stringify({ requestId, code: "CONVERSATION_CONFLICT" })}\n\n`;
      }
      scopedConversation.version = requestId;
      scopedConversation.lastRequestId = requestId;
      scopedConversation.messages.push(
        { id: `${requestId}-user`, role: "user", text: body.message, createdAt },
        { id: `${requestId}-assistant`, role: "assistant", createdAt, ...response },
      );
      if (outcome === "afterCommit") return "";
      if (outcome === "malformed") return "event:result\ndata:invalid-json\n\n";
      return `event:result\rdata:${JSON.stringify({ requestId, conversationId: scopedConversation.conversationId, version: scopedConversation.version })}\r\r`;
    });
    await target.addInitScript(({ role, language, streamPaths }) => {
      localStorage.setItem("flashTicket.language", language);
      const fetchOriginal = window.fetch.bind(window);
      window.fetch = async (input, init) => {
        const request = new Request(input, init);
        const path = new URL(request.url).pathname;
        if (!streamPaths.includes(path)) return fetchOriginal(input, init);
        const body = await request.json();
        const { status, oversize } = await window.beginAssistantFixture({ body, path, authorization: request.headers.get("authorization"), accept: request.headers.get("accept") });
        if (status !== 200) return new Response("{}", { status, headers: { "content-type": "application/problem+json" } });
        const encoder = new TextEncoder();
        const first = encoder.encode(`: chờ\r\nevent:processing\r\ndata:{\r\ndata:"requestId":"${body.requestId}"}\r\n\r\n`);
        const stream = new ReadableStream({
          start(controller) {
            request.signal.addEventListener("abort", () => {
              void window.abortAssistantFixture(path).then(() => { window.lastAssistantFixtureAbort = path; });
              controller.error(request.signal.reason);
            }, { once: true });
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
      const roles = Array.isArray(role) ? role : role ? [role] : [];
      const authenticated = roles.length > 0;
      window.__KEYCLOAK_INSTANCE__ = {
        authenticated, token: authenticated ? "fixture-token" : undefined,
        idToken: authenticated ? "fixture-id-token" : undefined,
        tokenParsed: authenticated ? { iss: "fixture", sub: `assistant-fixture-${role}`, realm_access: { roles } } : undefined,
        hasRealmRole(name) { return roles.includes(name); },
        init() { setTimeout(() => this.onReady?.(authenticated), 0); return Promise.resolve(authenticated); },
        isTokenExpired() { return false; }, updateToken() { return Promise.resolve(false); },
        login() { return Promise.resolve(); }, logout() { return Promise.resolve(); },
      };
    }, { role, language, streamPaths: [api, organizerApi, adminApi].map(root => root + "/messages/stream") });
  };
  try {
    for (const [role, text] of [[null, /Vui lòng đăng nhập/], ["ORGANIZER", /chỉ hỗ trợ tài khoản người mua vé/]]) {
      const target = await context.newPage();
      try {
        await prepare(target, role);
        await target.goto(`${origin}/chatbot`);
        await target.locator(".assistant-gate").getByRole("status").filter({ hasText: text }).waitFor();
        assert(await target.locator(".assistant-gate button").count() === (role ? 0 : 1), "Only guest should see sign-in action");
        assert(await target.locator(".floating-chatbot-input").count() === 0, "Unauthorized composer rendered");
      } finally { await target.close(); }
    }
    assert(opens === 0 && chat.length === 0, "Guest/non-BUYER reached Assistant API");
    checks.push("guest and non-BUYER gates");

    await prepare(page, "BUYER");
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.goto(origin);
    const launcher = page.locator(".floating-chatbot-trigger");
    const panel = page.locator(".floating-chatbot-panel");
    await launcher.focus();
    await page.keyboard.press("Enter");
    await page.keyboard.press("Tab");
    assert(await panel.locator(":focus-visible").count() === 1, "Panel must be reachable from launcher by keyboard");
    await page.keyboard.press("Escape");
    await panel.waitFor({ state: "hidden" });
    assert(await launcher.evaluate(node => node === document.activeElement), "Escape did not restore launcher focus");
    await launcher.click();
    const prompt = panel.getByRole("button", { name: "Kiểm tra đơn vé" });
    await prompt.waitFor();
    const capture = async (surface, name, width, height) => {
      await page.setViewportSize({ width, height });
      await surface.scrollIntoViewIfNeeded();
      if (name === "results") await surface.evaluate(node => {
        const nav = document.querySelector(".account-category-nav");
        if (nav) window.scrollBy({ top: node.getBoundingClientRect().top - nav.getBoundingClientRect().bottom - 8, behavior: "instant" });
      });
      await page.evaluate(() => document.fonts.ready);
      const composer = await surface.locator(".floating-chatbot-input input").boundingBox();
      const overflow = await surface.evaluate(node => {
        const transcript = node.querySelector(".assistant-transcript");
        return document.documentElement.scrollWidth > innerWidth + 1 || transcript.scrollWidth > transcript.clientWidth + 1;
      });
      assert(!overflow && composer && composer.y >= 0 && composer.y + composer.height <= height + 1, `Overflow or hidden composer: ${name} ${width}`);
      if (screenshotDir) await surface.screenshot({ path: `${screenshotDir}/assistant-${name}-${width}.png` });
    };
    for (const [width, height] of [[1440, 1000], [390, 844], [320, 568]]) await capture(panel, "welcome", width, height);
    await prompt.click();
    const input = panel.getByRole("textbox", { name: "Tin nhắn cho trợ lý" });
    assert((await input.inputValue()).includes("đơn vé gần đây") && await input.evaluate(node => node === document.activeElement)
      && chat.length === 0, "Suggestion should only fill and focus composer");
    let release;
    nextMessage = { response: { text: "Không tìm thấy đơn.", orders: [] }, ready: new Promise(resolve => { release = resolve; }) };
    try {
      await panel.getByRole("button", { name: "Gửi", exact: true }).click();
      const progress = panel.getByRole("status").filter({ hasText: "Trợ lý đang xử lý" });
      await progress.waitFor({ timeout: 7000 });
      assert(await input.isDisabled() && await progress.locator("svg").evaluate(node => getComputedStyle(node).animationName) === "none",
        "Busy state must disable composer and respect reduced motion");
      assert(reads === 0 && await panel.getByText("Không tìm thấy đơn.").count() === 0,
        "Processing must appear before the turn is committed or any transcript GET");
    } finally { release(); }
    await panel.locator('.assistant-order-card a[href="/my-orders"]').waitFor();
    assert(chat.length === 1 && chat[0].conversationId === conversationId && chat[0].version === initialVersion && chat[0].requestId
      && reads === 1, "Send must reconcile one SSE turn from GET");
    checks.push("keyboard, suggestions, streamed progress/reduced motion and empty orders");

    failOpen = true;
    await panel.getByRole("button", { name: "Đọc lại hội thoại" }).click();
    const failed = panel.getByRole("alert").filter({ hasText: "Không mở được hội thoại" });
    await failed.waitFor();
    failOpen = false;
    await panel.getByRole("button", { name: "Đọc lại hội thoại" }).click();
    await failed.waitFor({ state: "hidden" });
    nextMessage = { response: { text: "Đây là sự kiện, đơn và nháp vé của bạn.", events, orders, draft } };
    await input.fill("Xem sự kiện, đơn và nháp vé");
    await input.press("Enter");
    await panel.locator(`a[href="/event/${eventId}"]`).waitFor();
    for (const [width, height] of [[1440, 1000], [390, 844], [320, 568]]) await capture(panel, "floating-results", width, height);
    const shell = page.locator(".chatbot-shell");
    await panel.getByRole("link", { name: "Mở rộng" }).click();
    await page.waitForURL(`${origin}/chatbot`);
    await launcher.waitFor({ state: "detached" });
    const order = shell.locator(`.assistant-order-card:has(a[href="/my-orders?orderId=${existingOrderId}"])`);
    const form = shell.locator("form.assistant-purchase-form");
    await form.waitFor();
    assert(await order.getByText(existingOrderId, { exact: false }).count() === 1
      && /300[.\s]000/.test(await order.locator(".assistant-ticket-total").innerText())
      && await order.getByText(/Giữ vé đến/).count() === 1
      && await shell.getByText("Invalid fixture order").count() === 0, "Wrong order details or invalid order ID exposure");
    assert(/300[.\s]000/.test(await form.locator(".assistant-ticket-total").innerText())
      && await form.getByText(/Nháp có hiệu lực đến/).count() === 1
      && await form.getByText(/Nháp chưa giữ vé hoặc giá/).count() === 1
      && bookings.length === 0, "Draft needs an estimate/deadline and must not book by itself");
    for (const [width, height] of [[1440, 1000], [390, 844], [320, 568]]) await capture(shell, "results", width, height);
    await form.getByRole("textbox").fill(customerEmail);
    await form.evaluate(node => {
      node.requestSubmit();
      node.requestSubmit();
    });
    await shell.locator(`a[href="/checkout?orderId=${orderId}"]`).waitFor();
    assert(bookings.length === 1 && bookings[0].key === draftId && bookings[0].body.eventId === eventId
      && bookings[0].body.customerEmail === customerEmail && bookings[0].body.selection.mode === "QUANTITY"
      && JSON.stringify(bookings[0].body.selection.items) === JSON.stringify(items), "Expected one Booking request with draft idempotency key");
    checks.push("reload recovery, event link, valid order, draft and one mock Booking with Checkout CTA");

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
      await english.getByRole("heading", { name: "FlashTicket Assistant" }).waitFor();
      await english.locator('.floating-chatbot-input input:not(:disabled)').waitFor();
      assert(await english.getByRole("textbox", { name: "Message to the assistant" }).count() === 1,
        "Direct navigation must open the conversation and enable the English composer");
      const [sentBeforeLogout, readsBeforeLogout] = [chat.length, reads];
      const englishInput = english.getByRole("textbox", { name: "Message to the assistant" });
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
      await english.locator(".assistant-gate").waitFor({ timeout: 7000 });
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
        await english.locator(".assistant-gate").waitFor({ timeout: 7000 });
      } finally { releaseLogout(); }
      assert(chat.length === sentBeforeLogout + 1 && reads === readsBeforeLogout, "Logout while reading must prevent stale reconciliation");
      checks.push("logout during stream blocks stale reconciliation");
    } finally { await english.close(); }
    const buyerOnly = await context.newPage();
    try {
      await prepare(buyerOnly, "BUYER");
      await buyerOnly.goto(`${origin}/organizer/chatbot`);
      await buyerOnly.getByRole("heading", { name: /chưa được cấp quyền Nhà tổ chức/i }).waitFor({ timeout: 7000 });
      assert(await buyerOnly.locator(".chatbot-shell").count() === 0, "Buyer-only session must not open Organizer chat");
    } finally { await buyerOnly.close(); }

    const organizer = await context.newPage();
    try {
      await prepare(organizer, "ORGANIZER");
      await organizer.goto(`${origin}/organizer/chatbot`);
      const shell = organizer.locator(".chatbot-shell");
      await shell.locator(".floating-chatbot-input input:not(:disabled)").waitFor({ timeout: 7000 });
      assert(await shell.getByRole("link", { name: "Chat mua vé", exact: true }).count() === 0,
        "Organizer-only session must not advertise a Buyer action");
    } finally { await organizer.close(); }

    const dual = await context.newPage();
    try {
      await prepare(dual, ["BUYER", "ORGANIZER"]);
      await dual.goto(`${origin}/organizer`);
      await dual.getByRole("link", { name: "Chat thống kê", exact: true }).click({ timeout: 7000 });
      await dual.waitForURL(`${origin}/organizer/chatbot`);
      const shell = dual.locator('.chatbot-shell[aria-label="Trợ lý nhà tổ chức"]');
      const buyerShell = dual.locator('.chatbot-shell[aria-label="Trợ lý FlashTicket"]');
      const input = shell.locator(".floating-chatbot-input input");
      await shell.locator(".floating-chatbot-input input:not(:disabled)").waitFor();
      assert(await dual.locator(".floating-chatbot-trigger").count() === 0, "Organizer chat should have a single composer");
      const buyerReadsBefore = reads;
      const bookingsBefore = bookings.length;
      let releaseOrganizer;
      nextMessage = { response: { text: "Hãy bấm Chat mua vé để tìm vé." }, ready: new Promise(resolve => { releaseOrganizer = resolve; }) };
      try {
        await input.fill("Tôi muốn mua vé");
        await input.press("Enter");
        await shell.getByRole("status").filter({ hasText: "Trợ lý đang xử lý" }).waitFor();
        assert(organizerReads === 0 && reads === buyerReadsBefore, "Organizer progress must arrive before its GET");
      } finally { releaseOrganizer(); }
      await shell.getByText("Hãy bấm Chat mua vé để tìm vé.", { exact: true }).waitFor();
      assert(organizerReads === 1 && reads === buyerReadsBefore && bookings.length === bookingsBefore,
        "Organizer must reconcile its own transcript without buying tickets");
      await shell.getByRole("link", { name: "Chat mua vé", exact: true }).click();
      await dual.waitForURL(`${origin}/chatbot`);
      await buyerShell.locator(".floating-chatbot-input input:not(:disabled)").waitFor();
      assert(await buyerShell.getByText("Hãy bấm Chat mua vé để tìm vé.", { exact: true }).count() === 0,
        "Buyer must not display the Organizer transcript");
      const buyerDraft = { ...draft, draftId: "00000000-0000-4000-8000-000000000014" };
      nextMessage = { response: { text: "Đây là nháp vé từ chat Buyer.", draft: buyerDraft } };
      const buyerInput = buyerShell.locator(".floating-chatbot-input input");
      await buyerInput.fill("Tạo nháp mua hai vé");
      await buyerInput.press("Enter");
      const buyerReply = buyerShell.locator("article").filter({ hasText: "Đây là nháp vé từ chat Buyer." });
      const form = buyerReply.locator("form.assistant-purchase-form");
      await form.waitFor();
      assert(reads === buyerReadsBefore + 1 && organizerReads === 1 && bookings.length === bookingsBefore,
        "Buyer draft must use its own GET and wait for confirmation");
      await form.getByRole("textbox").fill(customerEmail);
      await form.evaluate(node => node.requestSubmit());
      await buyerReply.locator(`a[href="/checkout?orderId=${orderId}"]`).waitFor();
      assert(bookings.length === bookingsBefore + 1 && bookings.at(-1).key === buyerDraft.draftId,
        "Dual-role user must confirm a single order through the Buyer form");
      await buyerShell.getByRole("link", { name: "Chat thống kê", exact: true }).click();
      await dual.waitForURL(`${origin}/organizer/chatbot`);
      await shell.getByText("Hãy bấm Chat mua vé để tìm vé.", { exact: true }).waitFor();
      assert(await shell.getByText("Đây là nháp vé từ chat Buyer.", { exact: true }).count() === 0,
        "Organizer must not display the Buyer transcript");
      for (const width of [390, 320]) {
        await dual.setViewportSize({ width, height: 844 });
        const box = await shell.boundingBox();
        assert(box && box.width >= width - 36, `Organizer chat should use the mobile viewport at ${width}px`);
        assert(await shell.getByRole("link", { name: "Chat mua vé", exact: true }).isVisible(),
          "Chat mua vé should be reachable on mobile");
      }
      checks.push("organizer role gate, SSE workspace isolation and dual-role purchase confirmation");
    } finally { await dual.close(); }

    for (const role of ["BUYER", "ORGANIZER", "ADMIN"]) {
      const target = await context.newPage();
      try {
        await prepare(target, role);
        const beforeOpen = adminOpens;
        await target.goto(origin + (role === "ADMIN" ? "/admin/sales" : "/admin/chatbot"));
        const shell = target.locator(".chatbot-shell");
        if (role === "ADMIN") {
          await target.getByRole("navigation", { name: "Admin Navigation" })
            .getByRole("link", { name: "Chat Admin", exact: true }).click();
          await target.waitForURL(origin + "/admin/chatbot");
          await shell.getByRole("heading", { name: "Trợ lý quản trị" }).waitFor();
          await shell.locator(".floating-chatbot-input input:not(:disabled)").waitFor();
          assert(adminOpens === beforeOpen + 1, "Admin-only session must open the Admin workspace");
          assert(await shell.getByRole("link", { name: /Chat mua vé|Chat thống kê/ }).count() === 0,
            "Admin-only session must not advertise Buyer or Organizer actions");
          await shell.getByRole("link", { name: "Sự kiện chờ duyệt" }).waitFor();
          await target.goto(origin + "/chatbot");
          await shell.getByText(/chỉ (?:dành cho tài khoản BUYER|hỗ trợ tài khoản người mua vé)/i).waitFor();
        } else {
          await target.getByText("Trợ lý chỉ hỗ trợ tài khoản quản trị.", { exact: true }).waitFor();
          assert(adminOpens === beforeOpen && await shell.count() === 0,
            role + " session must not open Admin chat");
        }
      } finally { await target.close(); }
    }

    const multiRole = await context.newPage();
    try {
      await prepare(multiRole, ["ADMIN", "BUYER", "ORGANIZER"]);
      await multiRole.goto(origin + "/admin/chatbot");
      const adminShell = multiRole.locator('.chatbot-shell[aria-label="Trợ lý quản trị"]');
      const adminInput = adminShell.locator(".floating-chatbot-input input");
      await adminShell.locator(".floating-chatbot-input input:not(:disabled)").waitFor();
      const [buyerReadsBefore, organizerReadsBefore, bookingsBefore] = [reads, organizerReads, bookings.length];
      nextMessage = { response: { text: "Doanh thu hệ thống là 300.000 VND.", draft } };
      await adminInput.fill("Doanh thu bán vé toàn hệ thống?");
      await adminInput.press("Enter");
      await adminShell.getByText("Doanh thu hệ thống là 300.000 VND.", { exact: true }).waitFor();
      assert(adminReads === 1 && reads === buyerReadsBefore && organizerReads === organizerReadsBefore,
        "Admin SSE must reconcile only through the Admin GET");
      assert(await adminShell.locator("form.assistant-purchase-form").count() === 0,
        "Admin must never render a purchase form");
      await adminShell.getByRole("link", { name: "Chat thống kê", exact: true }).click();
      await multiRole.waitForURL(origin + "/organizer/chatbot");
      await multiRole.locator(".chatbot-shell").getByRole("link", { name: "Chat Admin", exact: true }).click();
      await multiRole.waitForURL(origin + "/admin/chatbot");

      let releaseAdmin;
      nextMessage = { outcome: "beforeCommit", ready: new Promise(resolve => { releaseAdmin = resolve; }) };
      try {
        await adminInput.fill("Sự kiện nào đang chờ duyệt?");
        await adminInput.press("Enter");
        await adminShell.getByRole("status").filter({ hasText: "Trợ lý đang xử lý" }).waitFor();
        for (const width of [390, 320]) {
          await multiRole.setViewportSize({ width, height: 844 });
          const box = await adminShell.boundingBox();
          assert(box && box.x >= 0 && box.x + box.width <= width, "Admin chat must fit at " + width + "px");
        }
        await adminShell.getByRole("link", { name: "Chat mua vé", exact: true }).click();
        await multiRole.waitForURL(origin + "/chatbot");
        await multiRole.waitForFunction(path => window.lastAssistantFixtureAbort === path, adminApi + "/messages/stream");
        assert(adminAborts === 1 && adminReads === 1 && reads === buyerReadsBefore,
          "Leaving Admin must abort its active stream without reconciling either workspace");
      } finally { releaseAdmin(); }

      const buyerShell = multiRole.getByRole("region", { name: /^(FlashTicket Assistant|Trợ lý FlashTicket)$/ });
      assert(await buyerShell.getByText("Doanh thu hệ thống là 300.000 VND.", { exact: true }).count() === 0,
        "Buyer must not display the Admin transcript");
      const buyerDraft = { ...draft, draftId: "00000000-0000-4000-8000-000000000016" };
      nextMessage = { response: { text: "Nháp vé sau khi chuyển từ Admin.", draft: buyerDraft } };
      const buyerInput = buyerShell.locator(".floating-chatbot-input input");
      await buyerInput.fill("Tạo nháp mua hai vé");
      await buyerInput.press("Enter");
      const buyerReply = buyerShell.locator("article").filter({ hasText: "Nháp vé sau khi chuyển từ Admin." });
      const form = buyerReply.locator("form.assistant-purchase-form");
      await form.getByRole("textbox").fill(customerEmail);
      assert(bookings.length === bookingsBefore, "Creating a draft must not book tickets");
      await form.evaluate(node => node.requestSubmit());
      await buyerReply.locator('a[href="/checkout?orderId=' + orderId + '"]').waitFor();
      assert(bookings.length === bookingsBefore + 1 && bookings.at(-1).key === buyerDraft.draftId,
        "Admin-to-Buyer switch must let the user confirm one order in Buyer");
      checks.push("admin role gate, scoped SSE and GET, abort on role switch and Buyer purchase confirmation");
    } finally { await multiRole.close(); }

    assert(unexpected.length === 0 && errors.length === 0, `Unexpected API/page errors: ${[...unexpected, ...errors].join("; ")}`);
    return { checks: [...checks, "VI/EN labels, 1440/390/320 layouts and no unexpected writes"] };
  } finally {
    // Keep requests mocked until the runner closes this isolated context.
    await page.close();
  }
};
