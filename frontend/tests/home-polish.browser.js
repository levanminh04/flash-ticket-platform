// Chạy sau home-layout.browser.js trong cùng tab; không gọi API thật.
async (page) => {
  await page.evaluate(() => {
    localStorage.removeItem("flashTicket.homeContentConfig");
    sessionStorage.removeItem("fe2-admin");
  });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("http://127.0.0.1:5173");
  await page.locator(".hero-event-card").waitFor();
  const checks = [];

  await page.locator(".nav-auth-trigger").click();
  const dialog = page.getByRole("dialog");
  if (!await dialog.evaluate(el => el.contains(document.activeElement))) throw new Error("Sign-in dialog must receive focus");
  await page.locator(".auth-popup-button-secondary").focus();
  await page.keyboard.press("Tab");
  // Chromium cho phép Tab qua browser chrome trước khi quay lại dialog.
  if (await page.evaluate(() => document.activeElement === document.body)) await page.keyboard.press("Tab");
  if (!await dialog.evaluate(el => el.contains(document.activeElement))) throw new Error("Tab reached background content");
  await page.locator(".navbar .logo").evaluate(el => el.focus());
  if (!await dialog.evaluate(el => el.contains(document.activeElement))) throw new Error("Modal background is not inert");
  await page.keyboard.press("Escape");
  await dialog.waitFor({ state: "hidden" });
  if (!await page.locator(".nav-auth-trigger").evaluate(el => el === document.activeElement)) throw new Error("Dialog must return focus to its trigger");
  checks.push("native modal focus / Tab / Escape / return focus");

  const faq = page.locator(".faq-item button").nth(1);
  await faq.focus();
  await page.keyboard.press("Enter");
  if (await faq.getAttribute("aria-expanded") !== "true") throw new Error("FAQ must announce expanded state");
  const answerId = await faq.getAttribute("aria-controls");
  if (!answerId || !await page.locator(`#${answerId}`).isVisible()) throw new Error("FAQ controls must identify its visible answer");
  await page.keyboard.press("Enter");
  if (await faq.getAttribute("aria-expanded") !== "false" || await page.locator(`#${answerId}`).isVisible()) throw new Error("Closed FAQ answer must be hidden");
  checks.push("FAQ keyboard + expanded state + hidden answers");

  const search = page.locator(".search-input");
  await search.fill("Đêm nhạc");
  const suggestions = page.locator(".search-suggestion-dropdown");
  await suggestions.waitFor();
  await page.locator(".search-suggestion-main").first().focus();
  await page.waitForTimeout(220);
  if (!await suggestions.isVisible()) throw new Error("Search suggestions disappeared during keyboard navigation");
  await page.keyboard.press("Escape");
  if (await suggestions.isVisible() || !await search.evaluate(el => el === document.activeElement)) throw new Error("Escape must dismiss suggestions and return to search");
  await search.fill("");
  checks.push("search suggestions stay usable by keyboard");

  const prompt = page.locator(".nav-prompt-trigger");
  await prompt.focus();
  await page.keyboard.press("Enter");
  await page.locator(".nav-prompt-item").first().focus();
  await page.keyboard.press("Escape");
  if (await page.locator(".nav-prompt-dropdown").getAttribute("open") !== null) throw new Error("Prompt menu must close on Escape");
  checks.push("prompt menu keyboard open / close");

  for (const selector of [".logo", ".search-submit", ".nav-prompt-trigger", ".faq-item button"]) {
    const control = page.locator(selector).first();
    await control.focus();
    await page.keyboard.press("Tab");
    await page.keyboard.press("Shift+Tab");
    const focus = await control.evaluate(el => ({ visible: el.matches(":focus-visible"), style: getComputedStyle(el).outlineStyle, width: parseFloat(getComputedStyle(el).outlineWidth) }));
    if (!focus.visible || focus.style === "none" || focus.width < 1.5) throw new Error(`Missing keyboard focus: ${selector}`);
  }
  checks.push("visible keyboard focus on header + FAQ");

  for (const language of ["English", "Vietnamese"]) {
    await page.locator(".language-switcher-nav summary").click();
    await page.getByRole("menuitemradio", { name: language, exact: true }).click();
    const pricingTitle = await page.locator(".pricing-glass-card").nth(1).locator("h3").innerText();
    if (pricingTitle !== (language === "English" ? "Transaction fee" : "Phí giao dịch")) throw new Error(`Pricing UI not localized: ${language}`);
    for (const width of [320, 390, 768, 900, 1024, 1440]) {
      await page.setViewportSize({ width, height: 844 });
      const metrics = await page.evaluate(() => {
        const card = document.querySelector(".pricing-glass-card").getBoundingClientRect();
        const next = document.querySelectorAll(".pricing-glass-card")[1].getBoundingClientRect();
        return { overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1, cardWidth: card.width, stacked: next.top >= card.bottom - 1 };
      });
      if (metrics.overflow || (width <= 640 && (!metrics.stacked || metrics.cardWidth < width - 110))) throw new Error(`Pricing/layout too narrow: ${language} ${width}px`);
      for (const selector of [".search-submit", ".nav-auth-trigger"]) {
        const rect = await page.locator(selector).boundingBox();
        if (rect.width < 43 || rect.height < 43) throw new Error(`Touch target too small: ${selector}`);
      }
    }
  }
  checks.push("VI/EN + six viewports + pricing reflow + touch targets");

  await page.locator(".language-switcher-nav summary").focus();
  await page.keyboard.press("ArrowDown");
  if (!await page.getByRole("menuitemradio").first().evaluate(el => el === document.activeElement)) throw new Error("Language menu arrow navigation failed");
  await page.keyboard.press("End");
  if (!await page.getByRole("menuitemradio").last().evaluate(el => el === document.activeElement)) throw new Error("Language menu End key failed");
  await page.keyboard.press("Escape");
  checks.push("language menu arrow keys / End / Escape");

  await page.setViewportSize({ width: 320, height: 568 });
  await page.locator(".hero-event-body h2").evaluate(el => { el.textContent = "Sự kiện với tên rất dài ".repeat(8); });
  await page.locator(".hero-event-meta span").last().evaluate(el => { el.textContent = "Địađiểmdàikhôngcódấucách".repeat(8); });
  await page.locator(".faq-item button").first().click();
  const answer = page.locator(".faq-answer").first();
  await answer.locator("p").evaluate(el => { el.textContent = "Nội dung câu trả lời dài để kiểm tra chữ không bị cắt. ".repeat(30); });
  if (!await answer.isVisible()) await page.locator(".faq-item button").first().click();
  const longContent = await page.evaluate(() => {
    const answer = document.querySelector(".faq-answer");
    return { overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1, answerClipped: answer.scrollHeight > answer.clientHeight + 1 };
  });
  if (longContent.overflow || longContent.answerClipped) throw new Error("Long content overflowed or FAQ answer was clipped");
  await page.locator(".nav-auth-trigger").click();
  const shortModal = await page.locator(".auth-popup-card").boundingBox();
  if (shortModal.x < 0 || shortModal.y < 0 || shortModal.x + shortModal.width > 321 || shortModal.y + shortModal.height > 569) throw new Error("Small-screen dialog was clipped");
  await page.keyboard.press("Escape");
  checks.push("long title / unbroken venue / long FAQ / small-screen dialog");

  await page.emulateMedia({ reducedMotion: "reduce" });
  const behavior = await page.locator(".upcoming-cards-container").evaluate(el => getComputedStyle(el).scrollBehavior);
  if (behavior !== "auto") throw new Error("Reduced motion must disable smooth scrolling");
  await page.emulateMedia({ reducedMotion: "no-preference" });
  checks.push("reduced motion preference");
  return { passed: checks.length, checks };
}
