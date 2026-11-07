// Playwright MCP: chạy file trong tab mới với Vite ở 127.0.0.1:5173.
async (page) => {
  let scenario = "populated";
  const pendingLoading = [];
  const legacyOrder = ["trust", "categories", "features", "upcoming", "venues", "how", "pricing", "past", "faq", "blog"];
  await page.addInitScript(({ legacyOrder }) => {
    if (!sessionStorage.getItem("fe2-seeded")) {
      localStorage.setItem("flashTicket.homeContentConfig", JSON.stringify({
        heroTitle: "Dễ dàng mua và\nbán vé sự kiện\ncủa bạn", sectionOrder: legacyOrder,
        titleColors: {}, categoryOverrides: {},
      }));
      sessionStorage.setItem("fe2-seeded", "1");
    }
    const admin = sessionStorage.getItem("fe2-admin") === "1";
    window.__KEYCLOAK_INSTANCE__ = {
      authenticated: admin,
      tokenParsed: admin ? { name: "Layout reviewer", realm_access: { roles: ["ADMIN"] } } : undefined,
      init() { setTimeout(() => this.onReady?.(admin), 0); return Promise.resolve(admin); },
      isTokenExpired() { return false; }, updateToken() { return Promise.resolve(false); },
      login() { return Promise.resolve(); }, logout() { return Promise.resolve(); },
    };
  }, { legacyOrder });
  const categories = [{ id: "music", slug: "music", name: "Âm nhạc" }, { id: "arts", slug: "arts", name: "Nghệ thuật" }];
  const events = Array.from({ length: 6 }, (_, i) => ({
    id: `layout-demo-${i}`, slug: `layout-demo-${i}`,
    title: ["Đêm nhạc mùa thu — Live Session", "Những thanh âm thành phố — Đêm diễn nghệ thuật với tên dài để kiểm tra bố cục", "Jazz & Friends"][i % 3],
    startDatetime: "2027-12-12T19:00:00", minPrice: 250000 + i * 50000,
    venueName: "Nhà hát Thành phố", city: "Hồ Chí Minh", category: categories[i % 2],
    bannerUrl: `https://images.unsplash.com/${i % 2 ? "photo-1516280440614-37939bbacd81" : "photo-1493225457124-a3eb161ffa5f"}?w=1000&auto=format&fit=crop`,
    images: [{ type: "POSTER", url: "https://images.unsplash.com/photo-1516280440614-37939bbacd81?w=600&auto=format&fit=crop" }, { type: "BANNER", url: "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=1000&auto=format&fit=crop" }],
  }));
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    let data = [];
    if (url.pathname.endsWith("/categories")) data = categories;
    if (url.pathname.endsWith("/events")) {
      if (scenario === "loading") await new Promise(resolve => { pendingLoading.push(resolve); });
      if (scenario === "error") return route.fulfill({ status: 503, contentType: "application/json", body: "{}" });
      const content = scenario === "empty" ? []
        : url.searchParams.has("categoryId") ? events.filter(e => e.category.id === url.searchParams.get("categoryId")) : events;
      data = { items: content.map(event => ({
        eventId: event.id, title: event.title, description: "Fixture minh họa",
        startsAt: event.startDatetime, endsAt: "2027-12-12T22:00:00", venueName: event.venueName,
        mode: "QUANTITY", seatMap: null,
        images: [{ imageId: event.id, objectReference: event.bannerUrl, displayOrder: 0 }],
        ticketTypes: [{ ticketTypeId: event.id + "-type", name: "Vé fixture", unitPrice: String(event.minPrice), currency: "VND", sectorId: null, configuredQuantity: "100", colorCode: "#cc9900" }],
      })), nextCursor: null };
    }
    if (url.pathname.endsWith("/me")) data = { displayName: "Layout reviewer", roles: ["ADMIN"] };
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(data) });
  });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("http://127.0.0.1:5173");
  await page.locator(".language-switcher-nav summary").click();
  await page.getByRole("menuitemradio", { name: "Vietnamese", exact: true }).click();
  await page.locator(".hero-event-card").waitFor();
  await page.screenshot({ path: "output/playwright/fe2-home-desktop.png" });
  const order = () => page.locator("main > [data-home-section]").evaluateAll(nodes => nodes.map(n => n.dataset.homeSection));
  const actualOrder = await order();
  if (actualOrder[0] !== "upcoming" || actualOrder[1] !== "categories") throw new Error("Event sections must lead in DOM order, including old saved default layout");
  const checks = ["legacy layout migration + DOM order"];
  const contrast = await page.locator(".home-upcoming-title").evaluate(element => {
    const foreground = getComputedStyle(element).color.match(/\d+/g).slice(0, 3).map(Number);
    const luminance = rgb => rgb.map(value => {
      value /= 255;
      return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
    }).reduce((sum, value, i) => sum + value * [0.2126, 0.7152, 0.0722][i], 0);
    // #282828 là đầu sáng nhất của nền gradient trang chủ hiện tại.
    const a = luminance(foreground), b = luminance([40, 40, 40]);
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
  });
  if (contrast < 3) throw new Error(`Default section heading contrast below 3:1: ${contrast}`);
  checks.push("default heading contrast");
  for (const width of [320, 390, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 1000 });
    const metrics = await page.locator(".home-hero").evaluate(el => ({ height: el.getBoundingClientRect().height, width: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth }));
    if (metrics.height > 600) throw new Error(`Hero is too tall at ${width}px: ${metrics.height}`);
    if (metrics.scroll > metrics.width + 1) throw new Error(`Homepage overflows at ${width}px`);
    const sections = await page.locator("main > [data-home-section]").evaluateAll(nodes => nodes.map(n => n.getBoundingClientRect().top));
    if (sections.some((y, i) => i > 0 && y < sections[i - 1])) throw new Error("Visual order differs from DOM");
    checks.push(`layout ${width}px`);
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: "output/playwright/fe2-home-mobile.png" });
  if (await page.locator('.hero-event-card a[href*="undefined"]').count()) throw new Error("Invalid hero event URL");

  scenario = "empty";
  await page.reload();
  await page.getByText("Chưa có sự kiện sắp diễn ra.", { exact: true }).first().waitFor();
  if (await page.locator('main a[href^="/event/"]').count()) throw new Error("Empty state must not invent event links");
  checks.push("empty without sample events");
  scenario = "error";
  await page.reload();
  await page.locator(".home-event-state[role=alert]").first().waitFor();
  scenario = "populated";
  await page.locator(".home-event-state").getByRole("button", { name: "Thử lại", exact: true }).first().click();
  await page.locator(".hero-event-card").waitFor();
  checks.push("API error + retry");

  scenario = "loading";
  await page.reload();
  await page.getByText("Đang tải sự kiện…", { exact: true }).first().waitFor();
  if (await page.locator(".hero-event-card").count()) throw new Error("Loading state showed an invented event");
  scenario = "populated";
  pendingLoading.splice(0).forEach(resolve => resolve());
  await page.locator(".hero-event-card").waitFor();
  checks.push("loading without sample event");

  await page.evaluate(() => {
    const key = "flashTicket.homeContentConfig";
    const config = JSON.parse(localStorage.getItem(key));
    config.layoutVersion = 2;
    config.heroTitle = "Sự kiện của bạn";
    config.sectionOrder = ["categories", "upcoming", "venues", "trust", "features", "how", "pricing", "past", "faq", "blog"];
    localStorage.setItem(key, JSON.stringify(config));
    sessionStorage.setItem("fe2-admin", "1");
  });
  await page.goto("http://127.0.0.1:5173/?editHome=1");
  await page.locator(".home-editor-panel").waitFor();
  await page.locator(".home-editor-close").click();
  if ((await order())[0] !== "categories") throw new Error("Custom layout order was lost");
  if ((await page.locator(".home-hero h1").innerText()) !== "Sự kiện của bạn") throw new Error("Custom hero title was lost");
  checks.push("custom configuration + editor preserved");
  return { passed: checks.length, checks };
}
