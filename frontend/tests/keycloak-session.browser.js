// Kiểm thử server thật trong tab đã có SSO buyer; không chứa mật khẩu/JWT.
async (page) => {
  await page.goto("http://127.0.0.1:5173/login");
  await page.waitForURL("http://127.0.0.1:5173/", {timeout: 20000});
  await page.waitForFunction(() => window.__KEYCLOAK_INSTANCE__?.authenticated, {}, {timeout: 20000});
  await page.reload();
  try {
    await page.waitForFunction(() => window.__KEYCLOAK_INSTANCE__?.authenticated, {}, {timeout: 20000});
  } catch {
    throw new Error("Reload lost the authenticated Keycloak session");
  }
  const result = await page.evaluate(async () => {
    const keycloak = window.__KEYCLOAK_INSTANCE__;
    const refreshed = await keycloak.updateToken(-1);
    return {authenticated: keycloak.authenticated, buyerRole: keycloak.hasRealmRole("BUYER"), refreshed};
  });
  if (!result.authenticated || !result.buyerRole || !result.refreshed) {
    throw new Error("Buyer session or forced refresh failed after reload");
  }
  return {passed: 3, checks: ["buyer login", "session restored after reload", "refresh token accepted"], ...result};
}
