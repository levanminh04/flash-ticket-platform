// Fixture trong browser; không gọi Sandbox/IPN và không ghi dữ liệu backend.
async page => {
  const orderId = "00000000-0000-4000-8000-000000000101", attemptId = "00000000-0000-4000-8000-000000000102", ticketId = "00000000-0000-4000-8000-000000000103";
  const requests = [], creates = [], checks = [];
  let state = "PENDING", accepted = false, denied = false, failStart = false, failWrapped = false, issuance = "PENDING", showQr = false;
  const start = () => ({ orderId, attemptId, attemptState: state, amount: "9007199254740993", currency: "VND", paymentUrl: state === "PENDING" ? "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?fixture=1" : null, expiresAt: new Date(Date.now() + 300000).toISOString() });
  const fulfill = (route, data, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(data) });
  await page.addInitScript(() => {
    localStorage.setItem("flashTicket.language", "vi");
    window.__KEYCLOAK_INSTANCE__ = { authenticated: true, token: "fixture-only", tokenParsed: { sub: "payment-buyer", iss: "fixture", email: "buyer@example.invalid", realm_access: { roles: ["BUYER"] } },
      init() { setTimeout(() => this.onReady?.(true), 0); return Promise.resolve(true); }, isTokenExpired() { return false; }, updateToken() { return Promise.resolve(false); }, login() { return Promise.resolve(); }, clearToken() { this.authenticated = false; }, logout() { return Promise.resolve(); } };
  });
  await page.route("**/api/**", async route => {
    const r = route.request(), p = new URL(r.url()).pathname;
    requests.push({ path: p, method: r.method(), accept: r.headers().accept, auth: r.headers().authorization });
    if (p === "/api/v1/me") return fulfill(route, { displayName: "Buyer fixture" });
    if (p === "/api/v1/events") return fulfill(route, { items: [], nextCursor: null });
    if (p === "/api/v1/orders" && r.method() === "POST") return fulfill(route, { orderId, eventId: attemptId, state: "PENDING_PAYMENT", customerEmail: "buyer@example.invalid", currency: "VND", subtotal: "20000", discount: "0", total: "20000", expiresAt: start().expiresAt, paymentFrozenAt: null, sourceConfigVersion: "1", items: [] });
    if (p === "/api/v1/payments" && r.method() === "POST") {
      creates.push({ body: r.postDataJSON(), key: r.headers()["idempotency-key"] });
      if (failStart) { failStart = false; return route.abort(); }
      return fulfill(route, { success: true, data: start(), message: "OK" });
    }
    if (p === `/api/v1/orders/${orderId}/payment`) {
      if (denied) return fulfill(route, { success: false, data: { code: "RESOURCE_NOT_FOUND", message: "Không tìm thấy thanh toán." } }, 404);
      if (failWrapped) return fulfill(route, { success: false, data: { code: "PAYMENT_STATE_UNAVAILABLE", message: "Chưa xác minh được thanh toán." } });
      return fulfill(route, { success: true, data: { orderId, acceptedChargeId: accepted ? ticketId : null, latestAttempt: start(), startRequest: null, refunds: [] } });
    }
    if (p === `/api/v1/orders/${orderId}/tickets`) return fulfill(route, { issuanceState: issuance, tickets: issuance === "COMPLETED" ? [{ ticketId, eventId: attemptId, orderItemId: orderId, ordinal: 1, ticketTypeName: "Vé hạng A", seatLabel: "A1", state: showQr ? "VALID" : "VOID" }] : [] });
    if (p === `/api/v1/tickets/${ticketId}/qr`) return route.fulfill({ contentType: "image/png", body: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+X2ioAAAAASUVORK5CYII=", "base64") });
    return fulfill(route, { code: "UNEXPECTED_ROUTE", message: p }, 404);
  });
  const open = path => page.goto(`http://127.0.0.1:5173${path}`);
  const capture = async (name, width, height = 900) => {
    await page.setViewportSize({ width, height });
    await page.evaluate(() => document.fonts.ready);
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    if (await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1)) throw new Error(`Page overflow: ${name} ${width}`);
    await page.screenshot({ path: `D:/DATN/flash-ticket-platform/output/playwright/fe-payment-${name}-${width}.png`, fullPage: true });
  };
  await open(`/payment-result?orderId=${orderId}&status=SUCCEEDED&responseCode=00`);
  // Baseline must fail here: old client calls /api/payments/initiate and omits the key.
  await page.evaluate(async orderId => { const { paymentService } = await import("/src/services/paymentService.ts"); await paymentService.initiatePayment(orderId); }, orderId);
  if (creates.length !== 1 || Object.keys(creates[0].body).join() !== "orderId" || !creates[0].key) throw new Error("Payment create must use v1 + {orderId} + UUID key");
  const key = creates[0].key;
  failStart = true;
  await page.evaluate(async orderId => { const { paymentService } = await import("/src/services/paymentService.ts"); await paymentService.initiatePayment(orderId).catch(() => {}); }, orderId);
  await page.reload();
  await page.evaluate(async orderId => { const { paymentService } = await import("/src/services/paymentService.ts"); await paymentService.initiatePayment(orderId); }, orderId);
  if (creates.some(c => c.key !== key)) throw new Error("Pending retry/reload rotated payment key");
  checks.push("Payment v1 body/envelope/key and network/reload retry");

  await page.locator('[data-payment-state="PENDING"]').waitFor();
  if (await page.getByRole("heading", { name: "Thanh toán đã được xác nhận", exact: true }).count()) throw new Error("Forged callback was trusted");
  checks.push("callback status/responseCode never proves payment success");
  state = "UNKNOWN"; await page.getByRole("button", { name: "Kiểm tra lại", exact: true }).click();
  await page.locator('[data-payment-state="UNKNOWN"]').waitFor();
  if (await page.getByRole("button", { name: "Thử thanh toán mới", exact: true }).count()) throw new Error("UNKNOWN offered a new charge");
  checks.push("UNKNOWN remains unresolved without new attempt");
  state = "SUCCEEDED"; await page.getByRole("button", { name: "Kiểm tra lại", exact: true }).click();
  await page.locator('[data-payment-state="SUCCEEDED"]').waitFor();
  await page.getByText("Đã ghi nhận khoản thu, đang chờ xác nhận đơn hàng.", { exact: true }).waitFor();
  accepted = true; await page.getByRole("button", { name: "Kiểm tra lại", exact: true }).click();
  await page.getByRole("heading", { name: "Thanh toán đã được xác nhận", exact: true }).waitFor();
  if (!(await page.locator(".payment-v1-page").innerText()).includes("9.007.199.254.740.993")) throw new Error("Money lost precision");
  checks.push("provider charge vs acceptedChargeId + exact wire money");
  await capture("accepted",1440); await capture("accepted",390,844); await capture("accepted",320,568);
  failWrapped = true; await page.getByRole("button", { name: "Kiểm tra lại", exact: true }).click();
  await page.getByRole("alert").filter({ hasText: "Chưa xác minh được" }).waitFor(); failWrapped = false;
  denied = true; await page.getByRole("button", { name: "Kiểm tra lại", exact: true }).click();
  await page.getByRole("alert").waitFor();
  if (await page.locator('[data-payment-state]').count()) throw new Error("Previous buyer/payment leaked after access failure");
  denied = false; checks.push("wrapped success=false and access errors clear verified data");

  accepted = false; state = "FAILED"; await open(`/payment-result?orderId=${orderId}`);
  await page.locator('[data-payment-state="FAILED"]').waitFor();
  const before = creates.length;
  state = "PENDING";
  await page.getByRole("button", { name: "Thử thanh toán mới", exact: true }).click();
  // Server has moved back to PENDING: a stale failed UI must not create a second charge.
  await page.getByRole("alert").waitFor();
  if (creates.length !== before) throw new Error("Stale failed UI created a new pending payment");
  checks.push("retry new attempt rechecks server before rotating key");
  state = "FAILED";
  failStart = true;
  await page.evaluate(async orderId => { const { paymentService } = await import("/src/services/paymentService.ts"); await paymentService.initiatePayment(orderId, true).catch(() => {}); }, orderId);
  const failedRetryKey = creates.at(-1).key;
  await page.evaluate(async orderId => { const { paymentService } = await import("/src/services/paymentService.ts"); await paymentService.initiatePayment(orderId, true); }, orderId);
  if (creates.at(-1).key === key || creates.at(-1).key !== failedRetryKey) throw new Error("Confirmed failed attempt retry lost its new request key");
  checks.push("confirmed FAILED gets a new key; lost-response retry retains that key");

  const urlChecks = await page.evaluate(async () => {
    const { vnPayCheckoutUrl } = await import("/src/services/paymentService.ts");
    const attempt = { attemptState: "PENDING", expiresAt: new Date(Date.now() + 60000).toISOString() };
    return ["javascript:alert(1)", "https://attacker.invalid", "https://sandbox.vnpayment.vn.attacker.invalid", "http://sandbox.vnpayment.vn", "https://user:pw@sandbox.vnpayment.vn"].every(paymentUrl => vnPayCheckoutUrl({ ...attempt, paymentUrl }) === null);
  });
  if (!urlChecks) throw new Error("Unsafe provider redirect was accepted");
  checks.push("provider redirect accepts only HTTPS VNPay checkout hosts");

  state = "PENDING"; failStart = true;
  await page.evaluate(async ({orderId, attemptId}) => { const {bookingService} = await import("/src/services/bookingService.ts"); await bookingService.createOrder({eventId:attemptId,customerEmail:"buyer@example.invalid",selection:{mode:"QUANTITY",items:[{ticketTypeId:attemptId,quantity:1}]}}); }, {orderId,attemptId});
  await open(`/checkout?orderId=${orderId}`);
  await page.getByRole("button", {name:"Thanh toán bằng VNPay",exact:true}).click();
  await page.getByRole("alert").waitFor();
  const checkoutKey=creates.at(-1).key;
  if (!await page.getByLabel("Mã giảm giá",{exact:true}).isDisabled()) throw new Error("Voucher remained editable after payment start");
  await page.reload();
  if (!await page.getByLabel("Mã giảm giá",{exact:true}).isDisabled()) throw new Error("Reload unlocked a frozen payment voucher");
  await page.route("https://sandbox.vnpayment.vn/**", route=>route.fulfill({contentType:"text/html",body:"<h1>VNPay fixture</h1>"}));
  await page.getByRole("button", {name:"Thanh toán bằng VNPay",exact:true}).click();
  await page.waitForURL("https://sandbox.vnpayment.vn/**");
  if (creates.at(-1).key!==checkoutKey) throw new Error("Checkout retry changed the key");
  checks.push("checkout start/retry/frozen voucher and actual VNPay navigation");

  await open(`/my-tickets?orderId=${orderId}`); await page.getByText("Đang chờ phát vé", { exact: true }).waitFor();
  if (await page.getByRole("button", { name: "Xem mã QR", exact: true }).count()) throw new Error("Pending issuance showed QR");
  issuance = "COMPLETED"; showQr = true; await page.getByRole("button", { name: "Kiểm tra lại", exact: true }).click();
  await page.getByText("Vé hạng A", { exact: true }).waitFor();
  await capture("tickets",1440); await capture("tickets",390,844);
  await page.getByRole("button", { name: "Xem mã QR", exact: true }).click();
  const qr = page.getByRole("img", { name: "Mã QR vé A1", exact: true }); await qr.waitFor();
  if (!(await qr.getAttribute("src")).startsWith("blob:")) throw new Error("QR must use a local authenticated blob");
  if (!requests.some(r => r.path.endsWith("/qr") && r.accept === "image/png" && r.auth === "Bearer fixture-only")) throw new Error("QR request missing auth/image Accept");
  const qrUrl = await qr.getAttribute("src"); await page.keyboard.press("Escape");
  if (await page.locator("dialog[open]").count()) throw new Error("QR dialog did not close on Escape");
  const revoked = await page.evaluate(async url => { try { await fetch(url); return false; } catch { return true; } }, qrUrl);
  if (!revoked) throw new Error("Closed QR blob was not revoked");
  checks.push("Ticket v1 pending/completed + authenticated PNG + native dialog/revocation");
  showQr = false; await page.getByRole("button", { name: "Kiểm tra lại", exact: true }).click();
  await page.getByText("Đã vô hiệu", { exact: true }).waitFor();
  if (await page.getByRole("button", { name: "Xem mã QR", exact: true }).count()) throw new Error("Void ticket exposed QR");
  checks.push("VOID tickets never offer a QR");
  await open("/my-orders"); await page.getByLabel("Mã đơn hàng", { exact: true }).fill(orderId);
  await page.getByRole("button", { name: "Tra cứu", exact: true }).click(); await page.locator('[data-payment-state="PENDING"]').waitFor();
  checks.push("orders use per-order v1 lookup without inventing global history");
  await page.evaluate(async () => { const {default:i18n} = await import("/src/i18n/index.ts"); await i18n.changeLanguage("en"); });
  await page.getByRole("heading",{name:"Orders and payments",exact:true}).waitFor();
  await capture("orders-en",320,568);
  await page.evaluate(async () => { const {default:i18n} = await import("/src/i18n/index.ts"); await i18n.changeLanguage("vi"); });
  checks.push("VI/EN and desktop/mobile layout without overflow");
  await open("/payment-result?status=SUCCEEDED&vnp_ResponseCode=00");
  await page.getByRole("alert").filter({ hasText: "mã đơn" }).waitFor();
  if (requests.some(r => r.path.includes("ipn") || /\/api\/(payments|tickets|orders)(\/|$)/.test(r.path))) throw new Error("Legacy/IPN endpoint was called");
  checks.push("missing orderId stays unresolved; no browser IPN/legacy routes");

  await page.clock.install();
  await open(`/payment-result?orderId=${orderId}`); await page.locator('[data-payment-state="PENDING"]').waitFor();
  const countPayments = () => requests.filter(r=>r.path.endsWith("/payment")).length;
  const pollStart = countPayments();
  for (let i=0;i<9;i++) {
    const response = page.waitForResponse(r=>new URL(r.url()).pathname.endsWith("/payment"));
    await page.clock.fastForward(3001); await response;
    await page.getByRole("button",{name:"Kiểm tra lại",exact:true}).waitFor();
  }
  await page.getByText("Chưa có kết quả cuối cùng. Bạn có thể kiểm tra lại sau.",{exact:true}).waitFor();
  if (countPayments() - pollStart !== 9) throw new Error("Expected ten total sequential polls");
  const beforeStop = countPayments(); await page.clock.fastForward(60000);
  if (countPayments() !== beforeStop) throw new Error("Polling did not stop at the bound");
  checks.push("bounded sequential polling stops without declaring failure");
  await page.evaluate(async orderId => {
    const {default:kc} = await import("/src/lib/keycloak.ts");
    const {paymentService} = await import("/src/services/paymentService.ts");
    kc.tokenParsed.sub = "another-buyer";
    if (paymentService.hasPaymentRequest(orderId)) throw new Error("Payment request key crossed buyers");
    kc.authenticated = false;
  },orderId);
  await page.clock.fastForward(1001);
  await page.getByRole("button",{name:"Đăng nhập",exact:true}).waitFor();
  if (await page.locator("[data-payment-state]").count()) throw new Error("Guest sees prior payment data");
  const guestStart=countPayments(); await page.clock.fastForward(60000);
  if (countPayments()!==guestStart) throw new Error("Guest polling did not stop");
  checks.push("buyer-isolated payment keys and guest cleanup");
  return { passed: checks.length, checks };
}
