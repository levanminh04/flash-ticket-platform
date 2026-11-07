// Chạy bằng một Playwright Page có sẵn; fixture chỉ dùng trong tab test.
async (page) => {
  await page.addInitScript(() => {
    const signedIn = sessionStorage.getItem("header-test-signed-in") === "1";
    window.__KEYCLOAK_INSTANCE__ = {
      authenticated: signedIn,
      tokenParsed: signedIn ? { name: "Nguyễn Người Mua Có Tên Rất Dài Để Kiểm Tra Header", realm_access: { roles: ["BUYER"] } } : undefined,
      init() { setTimeout(() => this.onReady?.(signedIn), 0); return Promise.resolve(signedIn); },
      isTokenExpired() { return false; },
      updateToken() { return Promise.resolve(false); },
      login() { window.__headerLoginCalls = (window.__headerLoginCalls || 0) + 1; return Promise.resolve(); },
      register() { return Promise.resolve(); },
      logout() { return Promise.resolve(); },
    };
  });
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    const data = path.endsWith("/events")
      ? { items: [], nextCursor: null }
      : path.endsWith("/me")
        ? { displayName: "Nguyễn Người Mua Có Tên Rất Dài Để Kiểm Tra Header", roles: ["BUYER"] }
        : [];
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(data) });
  });
  await page.goto("http://127.0.0.1:5173");
  await page.locator(".nav-auth-trigger").waitFor();

  const checks = [];
  for (const width of [320, 390, 768, 900, 1024, 1440]) {
    await page.setViewportSize({ width, height: 844 });
    const search = page.locator(".navbar input[name=search]");
    if (!(await search.isVisible())) throw new Error(`Search must remain visible at ${width}px`);
    const bounds = await page.locator(".navbar").evaluate((nav) => ({
      viewport: document.documentElement.clientWidth,
      scrollWidth: nav.scrollWidth,
      right: nav.getBoundingClientRect().right,
      controls: [...nav.querySelectorAll(".nav-quick-link, .language-switcher-trigger, .search-submit")]
        .map((element) => { const r = element.getBoundingClientRect(); return { x: r.x, right: r.right, width: r.width, height: r.height }; }),
    }));
    if (bounds.scrollWidth > bounds.viewport + 1) throw new Error(`Header overflow at ${width}px: ${bounds.scrollWidth}`);
    for (const control of bounds.controls) {
      if (control.x < 0 || control.right > bounds.viewport + 1) throw new Error(`Header control clipped at ${width}px`);
      if (width <= 900 && (control.width < 44 || control.height < 44)) throw new Error(`Touch target smaller than 44px at ${width}px`);
    }
    checks.push(`layout ${width}px`);
  }

  await page.setViewportSize({ width: 390, height: 844 });
  await page.locator(".navbar input[name=search]").fill("nhạc sống");
  await page.locator(".navbar input[name=search]").press("Enter");
  await page.waitForURL("**/search?search=**");
  if (new URL(page.url()).searchParams.get("search") !== "nhạc sống") throw new Error("Search keyword was lost");
  await page.locator('.nav-quick-link[href="/my-tickets"]').click();
  if (await page.evaluate(() => window.__headerLoginCalls) !== 1) throw new Error("Guest tickets must still require login");
  checks.push("mobile search submit + guest ticket login");

  const language = page.locator(".language-switcher-nav");
  await language.locator("summary, .language-switcher-trigger").first().click();
  await language.getByRole("menuitemradio", { name: "English" }).click();
  if (await page.locator(".navbar input[name=search]").getAttribute("placeholder") !== "Search...") throw new Error("Language did not change");
  await language.locator("summary, .language-switcher-trigger").first().click();
  await page.keyboard.press("Escape");
  if (await language.locator('[role="menu"]').isVisible()) throw new Error("Escape must close language menu");
  checks.push("touch language switch + Escape");

  await page.evaluate(() => sessionStorage.setItem("header-test-signed-in", "1"));
  await page.reload();
  const account = page.locator(".account-trigger");
  await account.waitFor();
  await account.focus();
  await page.keyboard.press("Enter");
  if (!(await page.locator('.account-dropdown a[href="/profile"]').isVisible())) throw new Error("Keyboard must open account menu");
  await page.keyboard.press("Escape");
  if (await page.locator('.account-dropdown a[href="/profile"]').isVisible()) throw new Error("Escape must close account menu");
  await account.click();
  await page.locator(".navbar").click({ position: { x: 4, y: 4 } });
  if (await page.locator('.account-dropdown a[href="/profile"]').isVisible()) throw new Error("Outside click must close account menu");
  checks.push("long account name + keyboard + outside dismissal");
  return { passed: checks.length, checks };
}
