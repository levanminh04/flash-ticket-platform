// Chạy sau home-layout.browser.js trong cùng tab để dùng lại fixture API/Keycloak.
async (page) => {
  await page.evaluate(() => {
    localStorage.removeItem("flashTicket.homeContentConfig");
    sessionStorage.removeItem("fe2-admin");
  });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("http://127.0.0.1:5173");
  await page.locator(".hero-event-card").waitFor();
  const palette = await page.evaluate(() => {
    const root = getComputedStyle(document.documentElement);
    return { primary: root.getPropertyValue("--primary").trim(), success: root.getPropertyValue("--success").trim(), error: root.getPropertyValue("--error").trim() };
  });
  if (palette.primary !== "#cc9900") throw new Error(`Primary must be FlashTicket gold, got ${palette.primary}`);
  if (palette.success !== "#10b981" || palette.error !== "#ef4444") throw new Error("Semantic status colors changed");
  const checks = ["gold brand + independent status colors"];

  const contrast = async (selector) => page.locator(selector).first().evaluate(element => {
    const parse = value => value.match(/[\d.]+/g).map(Number);
    const luminance = rgb => rgb.slice(0, 3).map(v => {
      v /= 255;
      return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
    }).reduce((sum, v, i) => sum + v * [0.2126, 0.7152, 0.0722][i], 0);
    const layers = [];
    for (let node = element; node; node = node.parentElement) layers.push(parse(getComputedStyle(node).backgroundColor));
    let background = [255, 255, 255];
    for (const rgba of layers.reverse()) {
      const alpha = rgba[3] ?? 1;
      background = background.map((v, i) => rgba[i] * alpha + v * (1 - alpha));
    }
    const a = luminance(parse(getComputedStyle(element).color)), b = luminance(background);
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
  });
  for (const selector of [".hero-event-cta", ".nav-quick-link strong", ".home-category-event-body h3 a", ".home-category-event-price", ".upcoming-card-title a", ".upcoming-card-price", ".upcoming-card-date", ".upcoming-card-location"]) {
    const ratio = await contrast(selector);
    if (ratio < 4.5) throw new Error(`Text contrast below 4.5:1 (${ratio.toFixed(2)}): ${selector}`);
  }
  checks.push("action / navigation / card text contrast");

  const cards = await page.locator(".home-category-event-card, .home-page .upcoming-event-card").evaluateAll(nodes => nodes.map(node => {
    const style = getComputedStyle(node), title = node.querySelector("h3"), image = node.querySelector("img");
    const bounds = image.getBoundingClientRect();
    return { radius: style.borderRadius, fontSize: getComputedStyle(title).fontSize, ratio: bounds.width / bounds.height };
  }));
  if (new Set(cards.map(c => c.radius)).size !== 1 || new Set(cards.map(c => c.fontSize)).size !== 1) throw new Error("Event card styles differ");
  if (cards.some(c => Math.abs(c.ratio - 16 / 9) > 0.05)) throw new Error("Event card image ratios differ");
  checks.push("shared event card radius, typography and images");

  const action = page.locator(".hero-event-cta").first();
  const normal = await action.evaluate(e => getComputedStyle(e).backgroundColor);
  await action.hover();
  await page.waitForTimeout(250);
  if (await action.evaluate(e => getComputedStyle(e).backgroundColor) === normal) throw new Error("Primary action lacks hover state");
  if (await contrast(".hero-event-cta") < 4.5) throw new Error("Hover contrast failed");
  await action.focus();
  await page.keyboard.press("Tab");
  await page.keyboard.press("Shift+Tab");
  // Browser/Windows có thể snap outline 2px thành 1.6px; không so bằng pixel tuyệt đối.
  const focus = await action.evaluate(e => ({ visible: e.matches(":focus-visible"), style: getComputedStyle(e).outlineStyle, width: parseFloat(getComputedStyle(e).outlineWidth) }));
  if (!focus.visible || focus.style === "none" || focus.width < 1.5) throw new Error("Keyboard focus is not visible");
  checks.push("primary hover + keyboard focus");

  for (const width of [320, 390, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 844 });
    if (await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1)) throw new Error(`Color/card styling overflow at ${width}px`);
  }
  checks.push("responsive cards 320–1440px");

  await page.locator(".nav-auth-trigger").click();
  if (await contrast(".auth-popup-button-primary") < 4.5) throw new Error("Sign-in action contrast failed");
  const signIn = await page.locator(".auth-popup-button-primary").evaluate(e => getComputedStyle(e).backgroundColor);
  if (signIn !== normal) throw new Error("Sign-in action does not use the primary brand color");
  await page.locator(".auth-popup-close").click();
  checks.push("sign-in dialog brand and contrast");

  await page.evaluate(() => sessionStorage.setItem("fe2-admin", "1"));
  await page.goto("http://127.0.0.1:5173/?editHome=1");
  await page.locator(".home-editor-panel").waitFor();
  await page.locator(".home-editor-tabs").getByRole("button", { name: "Bố cục", exact: true }).click();
  const disabled = page.locator(".home-editor-panel button:disabled").first();
  const state = await disabled.evaluate(e => ({ background: getComputedStyle(e).backgroundColor, color: getComputedStyle(e).color, cursor: getComputedStyle(e).cursor }));
  if (state.cursor !== "not-allowed" || state.background === normal) throw new Error("Disabled action must have a neutral, distinct state");
  checks.push("native disabled state");
  await page.locator(".home-editor-close").click();
  return { passed: checks.length, checks };
}
