// Chạy adapter thật của FE; chỉ giả lập endpoint OIDC trong context riêng.
// Bắt lỗi adapter cũ xóa token khi Keycloak chỉ trả nonce trong ID token.
async (page) => {
  const context = await page.context().browser().newContext();
  const browserPage = await context.newPage();
  const origin = "http://127.0.0.1:5173";
  const fixtureUrl = `${origin}/auth-nonce-fixture`;
  let tokens;
  const results = [];
  try {
    await browserPage.route("**/auth-nonce-fixture*", route => route.fulfill({
      contentType: "text/html",
      body: `<html><body><script type="module">
        import keycloak from '/src/lib/keycloak.ts';
        window.fixtureKeycloak = keycloak;
        window.fixtureReady = keycloak.init({checkLoginIframe: false, pkceMethod: 'S256'})
          .then(() => ({authenticated: keycloak.authenticated, hasToken: !!keycloak.token}))
          .catch(error => ({authenticated: keycloak.authenticated, hasToken: !!keycloak.token, error: error?.message || String(error)}));
      </script></body></html>`,
    }));
    await browserPage.route("**/realms/flash-ticket/protocol/openid-connect/token", route => route.fulfill({json: tokens}));

    for (const validNonce of [true, false]) {
      await browserPage.goto(fixtureUrl);
      await browserPage.waitForFunction(() => !!window.fixtureReady);
      await browserPage.evaluate(() => window.fixtureReady);
      const loginUrl = new URL(await browserPage.evaluate(
        url => window.fixtureKeycloak.createLoginUrl({redirectUri: url}), fixtureUrl,
      ));
      if (!loginUrl.searchParams.get("nonce") || loginUrl.searchParams.get("code_challenge_method") !== "S256") {
        throw new Error("Login must retain nonce and PKCE S256");
      }
      tokens = await browserPage.evaluate(({nonce, validNonce}) => {
        const encode = value => btoa(JSON.stringify(value)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
        const jwt = claims => `${encode({alg: "RS256", typ: "JWT"})}.${encode(claims)}.fixture-signature`;
        const now = Math.floor(Date.now() / 1000);
        const claims = {
          sub: "00000000-0000-4000-8000-000000000001", iat: now, exp: now + 300,
          iss: "http://3.106.109.119:8080/realms/flash-ticket",
          aud: "frontend", sid: "fixture-session", session_state: "fixture-session",
        };
        return {
          access_token: jwt({...claims, realm_access: {roles: ["BUYER"]}}),
          refresh_token: jwt({...claims, exp: now + 1800}),
          id_token: jwt({...claims, nonce: validNonce ? nonce : "wrong-nonce"}),
          token_type: "Bearer", expires_in: 300, refresh_expires_in: 1800,
          session_state: "fixture-session", scope: "openid",
        };
      }, {nonce: loginUrl.searchParams.get("nonce"), validNonce});
      // Callback phải mở document mới; đổi riêng hash không khởi tạo lại adapter.
      await browserPage.goto("about:blank");
      await browserPage.goto(`${fixtureUrl}#code=fixture-code&state=${loginUrl.searchParams.get("state")}`);
      await browserPage.waitForFunction(() => !!window.fixtureReady);
      const state = await browserPage.evaluate(() => window.fixtureReady);
      if (validNonce && (!state.authenticated || !state.hasToken)) {
        throw new Error(`Valid ID-token nonce must authenticate when access/refresh tokens omit nonce: ${JSON.stringify(state)}`);
      }
      if (!validNonce && (state.authenticated || state.hasToken)) {
        throw new Error("Mismatched ID-token nonce must reject login and clear tokens");
      }
      results.push(validNonce ? "valid ID nonce accepted without access/refresh nonce" : "wrong ID nonce rejected");
      await browserPage.evaluate(() => localStorage.clear());
    }
    return {passed: results.length, checks: results};
  } finally {
    await context.close();
  }
}
