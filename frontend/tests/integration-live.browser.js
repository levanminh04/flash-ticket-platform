// Server thật, context riêng; credentials do runner đọc từ seed local, không lưu vào kết quả.
async (page) => {
  const { origin, username, password, outputDir } = page.__task14Config;
  delete page.__task14Config;
  const context = await page.context().browser().newContext({ viewport: { width: 1440, height: 1000 } });
  const livePage = await context.newPage();
  const checks = [];
  let authenticated = false;
  const record = (name, status, detail) => checks.push({ name, status, detail });
  const probe = async (path, withAuth = false) => livePage.evaluate(async ({path, withAuth}) => {
    try {
      const { default: client } = await import("/src/lib/axiosClient.ts");
      const response = withAuth
        ? await client.get(path, { timeout: 10000, validateStatus: () => true })
        : await fetch(path, { signal: AbortSignal.timeout(10000) });
      return { status: response.status, data: withAuth ? response.data : await response.json().catch(() => null) };
    } catch { return { status: 0, data: null }; }
  }, {path, withAuth});
  const capture = async label => {
    for (const [width, height] of [[1440, 1000], [390, 844], [320, 568]]) {
      await livePage.setViewportSize({width, height});
      await livePage.evaluate(() => document.fonts.ready);
      const fits = await livePage.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1);
      record(`${label}: layout ${width}px`, fits ? "PASS" : "FAIL", fits ? "Không tràn ngang" : "Có tràn ngang");
      await livePage.screenshot({path: `${outputDir}/task14-${label}-${width}.png`, fullPage: false});
    }
    await livePage.setViewportSize({width: 1440, height: 1000});
  };
  try {
    const home = await livePage.goto(origin);
    record("Frontend document", home?.ok() ? "PASS" : "FAIL", `HTTP ${home?.status() || 0}`);
    await livePage.getByRole("button", {name: "Đăng nhập", exact: true}).waitFor({timeout: 15000});
    let catalog;
    for (const path of ["/api/v1/events?size=2", "/api/v1/categories", "/api/v1/venues"]) {
      const result = await probe(path);
      const valid = path.includes("events")
        ? Array.isArray(result.data?.items) && "nextCursor" in result.data
        : Array.isArray(result.data);
      record(`Guest GET ${path}`, result.status === 200 && valid ? "PASS" : "FAIL", `HTTP ${result.status}; contract=${valid}`);
      if (path.includes("events")) catalog = result;
    }
    await capture("guest");

    await livePage.goto(`${origin}/login`);
    await livePage.getByRole("textbox", {name: "Email", exact: true}).waitFor({timeout: 20000});
    const authUrl = new URL(livePage.url());
    const secured = authUrl.searchParams.get("code_challenge_method") === "S256" && !!authUrl.searchParams.get("nonce");
    record("Login request PKCE + nonce", secured ? "PASS" : "FAIL", `client=${authUrl.searchParams.get("client_id")}; PKCE=${authUrl.searchParams.get("code_challenge_method")}`);
    if (!secured) throw new Error("Login request thiếu PKCE S256/nonce");
    await livePage.getByRole("textbox", {name: "Email", exact: true}).fill(username);
    await livePage.getByRole("textbox", {name: "Password", exact: true}).fill(password);
    const exchange = livePage.waitForResponse(response => new URL(response.url()).pathname.endsWith("/token"), {timeout: 20000});
    await livePage.getByRole("button", {name: "Sign In", exact: true}).click();
    const tokenResponse = await exchange;
    await livePage.waitForURL(`${origin}/`, {timeout: 20000});
    await livePage.waitForFunction(() => window.__KEYCLOAK_INSTANCE__?.authenticated, {}, {timeout: 15000});
    authenticated = true;
    const buyer = await livePage.evaluate(() => window.__KEYCLOAK_INSTANCE__.hasRealmRole("BUYER"));
    record("Buyer login thật", tokenResponse.status() === 200 && buyer ? "PASS" : "FAIL", `Token HTTP ${tokenResponse.status()}; role BUYER=${buyer}`);

    const profile = await probe("/api/v1/me", true);
    record("Buyer GET /api/v1/me", profile.status === 200 ? "PASS" : "FAIL", `HTTP ${profile.status}; request qua axios interceptor thật`);
    const firstEvent = catalog?.data?.items?.[0];
    if (catalog?.status === 200 && firstEvent?.eventId) {
      const detail = await probe(`/api/v1/events/${firstEvent.eventId}`);
      record("Event detail thật", detail.status === 200 && detail.data?.eventId === firstEvent.eventId ? "PASS" : "FAIL", `HTTP ${detail.status}`);
      const availability = await probe(`/api/v1/events/${firstEvent.eventId}/availability`);
      record("Availability thật", availability.status === 200 ? "PASS" : "FAIL", `HTTP ${availability.status}`);
      await livePage.goto(`${origin}/event/${firstEvent.eventId}`);
      await livePage.locator("main, .event-detail-page").first().waitFor({timeout: 15000});
      await capture("event");
    } else {
      record("Event detail/availability/seatmap thật", "BLOCKED", "Catalog không có event đọc được; không dùng ID bịa hoặc fixture để thay backend thật");
    }

    await livePage.goto(`${origin}/`);
    await livePage.getByLabel("Tài khoản của tôi", {exact: true}).waitFor({timeout: 20000});
    await capture("buyer");
    await livePage.reload();
    await livePage.waitForFunction(() => window.__KEYCLOAK_INSTANCE__?.authenticated, {}, {timeout: 20000});
    record("Reload giữ phiên", "PASS", "Adapter khôi phục SSO; không inject token");
    const refreshed = await livePage.evaluate(() => window.__KEYCLOAK_INSTANCE__.updateToken(-1));
    record("Refresh token thật", refreshed ? "PASS" : "FAIL", `updateToken(-1)=${refreshed}`);

    record("Tạo đơn/voucher thật", "BLOCKED", "Cần catalog/availability thật và test event đã xác định trước khi tạo hold");
    record("VNPay → Payment → Ticket → Booking COMPLETED", "BLOCKED", "Task1.3 chưa nối checkout Payment v1; không gọi IPN từ browser hoặc giả giao dịch thành công");
    record("Đăng ký và User projection", "BLOCKED", "Chưa có User Service hoạt động để xác minh hồ sơ; không tạo user mới không kiểm được projection");
  } catch (error) {
    record("Luồng thật bị ngắt", "FAIL", error.message.split("\n")[0]);
  } finally {
    if (authenticated) {
      try {
        await livePage.getByLabel("Tài khoản của tôi", {exact: true}).click();
        await livePage.getByRole("button", {name: "Đăng xuất", exact: true}).click();
        await livePage.getByRole("dialog").getByRole("button", {name: "Đăng xuất", exact: true}).click();
        await livePage.getByRole("button", {name: "Đăng nhập", exact: true}).waitFor({timeout: 20000});
        const cleared = await livePage.evaluate(() => !window.__KEYCLOAK_INSTANCE__?.token && !window.__KEYCLOAK_INSTANCE__?.authenticated && !sessionStorage.getItem("flashTicket:keycloakSession"));
        record("Logout thật", cleared ? "PASS" : "FAIL", `token và cờ phiên được xóa=${cleared}`);
      } catch { record("Logout thật", "FAIL", "Không hoàn tất logout qua giao diện"); }
    }
    await context.close();
  }
  return {
    status: checks.some(check => check.status !== "PASS") ? "INCOMPLETE" : "PASS",
    passed: checks.filter(check => check.status === "PASS").length,
    failed: checks.filter(check => check.status === "FAIL").length,
    blocked: checks.filter(check => check.status === "BLOCKED").length,
    checks,
  };
}
