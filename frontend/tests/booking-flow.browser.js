// User-flow fixture: Booking v1, không ghi dữ liệu lên server119.
async page => {
  const ids = Array.from({ length: 14 }, (_, i) => `00000000-0000-4000-8000-${String(i + 1).padStart(12, "0")}`);
  const [eventId, typeA, typeB, standingId, seatedId, otherSector, seatA, heldSeat, seatB, orderId] = ids;
  let mode = "QUANTITY", failCreate = false, expired = false, failAvailability = false, rejectInventory = false, largeAmount = false;
  const creates = [], promotions = [], requests = [];
  const emptyMap = { schemaVersion: 1, layoutId: ids[10], name: "Hall", canvasWidth: 800, canvasHeight: 600, backgroundImageReference: null, layoutData: { schemaVersion: 1, decorations: [] } };
  const geometry = { schemaVersion: 1, shape: { shapeType: "rectangle", bounds: { x: 100, y: 180, width: 260, height: 140 } }, seatLayout: { seatRadius: 10 } };
  const getEvent = () => ({
    eventId, title: "Sự kiện đặt vé fixture", description: "Dữ liệu kiểm thử Booking", startsAt: "2027-12-12T12:00:00Z", endsAt: "2027-12-12T15:00:00Z", venueName: "Nhà hát fixture", mode: mode === "QUANTITY" ? "QUANTITY" : "SEAT_MAP", images: [],
    ticketTypes: [typeA, typeB].map((ticketTypeId, i) => ({ ticketTypeId, name: i ? "Loại B" : "Loại A", unitPrice: i ? "25000" : "15000", currency: "VND", sectorId: mode === "QUANTITY" ? null : mode === "STANDING" ? standingId : i ? otherSector : seatedId, configuredQuantity: "100", colorCode: "#805c00" })),
    seatMap: mode === "QUANTITY" ? null : { ...emptyMap, sectors: mode === "STANDING"
      ? [{ sectorId: standingId, name: "Khu đứng", code: "S", kind: "STANDING", configuredCapacity: "100", displayOrder: 0, colorCode: "#805c00", geometry, seats: [] }]
      : [seatedId, otherSector].map((sectorId, i) => ({ sectorId, name: i ? "Khu B" : "Khu A", code: i ? "B" : "A", kind: "SEATED", configuredCapacity: "2", displayOrder: i, colorCode: "#805c00", geometry: { ...geometry, shape: { ...geometry.shape, bounds: { ...geometry.shape.bounds, x: i ? 420 : 100 } } }, seats: (i ? [seatB] : [seatA, heldSeat]).map((seatId, j) => ({ seatId, seatLabel: i ? "B1" : j ? "A2" : "A1", rowName: i ? "B" : "A", seatNumber: String(j + 1), coordX: i ? 440 : 120 + j * 30, coordY: 210, ticketTypeId: i ? typeB : typeA, isHidden: false })) })) },
  });
  const getAvailability = () => ({ eventId, sourceConfigVersion: "1", asOf: new Date().toISOString(), ticketTypes: [{ ticketTypeId: typeA, available: mode === "STANDING" ? "3" : "4" }, { ticketTypeId: typeB, available: mode === "STANDING" ? "3" : "4" }], sectors: mode === "QUANTITY" ? [] : mode === "STANDING" ? [{ sectorId: standingId, kind: "STANDING", available: "3" }] : [{ sectorId: seatedId, kind: "SEATED", available: "1" }, { sectorId: otherSector, kind: "SEATED", available: "1" }], seats: mode === "SEATED" ? [{ seatId: seatA, state: "AVAILABLE" }, { seatId: heldSeat, state: "HELD" }, { seatId: seatB, state: "AVAILABLE" }] : [] });
  let order;
  await page.addInitScript(() => {
    localStorage.setItem("flashTicket.language", "vi");
    window.__KEYCLOAK_INSTANCE__ = { authenticated: true, tokenParsed: { sub: "booking-fixture-buyer", iss: "fixture", email: "buyer@example.invalid", name: "Buyer fixture", realm_access: { roles: ["BUYER"] } },
      init() { setTimeout(() => this.onReady?.(true), 0); return Promise.resolve(true); },
      isTokenExpired() { return false; }, updateToken() { return Promise.resolve(false); }, login() { return Promise.resolve(); }, logout() { return Promise.resolve(); },
    };
  });
  await page.route("**/api/**", async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname;
    requests.push({ path, method: req.method() });
    let data = [];
    if (path === `/api/v1/events/${eventId}`) data = getEvent();
    else if (path.endsWith("/availability")) {
      if (failAvailability) return route.fulfill({ status: 503, contentType: "application/json", body: JSON.stringify({ code: "CONFIG_NOT_READY", message: "Chưa tải được vé." }) });
      data = getAvailability();
    } else if (path === "/api/v1/orders" && req.method() === "POST") {
      const body = req.postDataJSON(), key = req.headers()["idempotency-key"];
      creates.push({ body, key });
      if (failCreate) { failCreate = false; return route.abort("failed"); }
      if (rejectInventory) { rejectInventory = false; return route.fulfill({ status: 409, contentType: "application/json", body: JSON.stringify({ code: "INSUFFICIENT_QUANTITY", message: "Vé vừa hết. Chọn lại vé." }) }); }
      const seats = body.selection.seatIds || [];
      const items = body.selection.items || [{ ticketTypeId: typeA, quantity: seats.length }];
      order = { orderId, eventId, state: "PENDING_PAYMENT", customerEmail: body.customerEmail, currency: "VND", subtotal: "40000", discount: "0", total: "40000", expiresAt: new Date(Date.now() + (expired ? -1000 : 300000)).toISOString(), paymentFrozenAt: null, sourceConfigVersion: "1", items: items.map((item, i) => ({ orderItemId: ids[11 + i], ticketTypeId: item.ticketTypeId, ticketTypeName: item.ticketTypeId === typeA ? "Loại A" : "Loại B", quantity: item.quantity, unitPrice: "20000", lineSubtotal: "40000", sectorId: body.selection.sectorId || null, seats: seats.map(seatId => ({ seatId, seatLabel: "A1", rowName: "A", seatNumber: "1" })) })) };
      if (largeAmount) { order.total = order.subtotal = "9007199254740993"; order.items[0].unitPrice = order.items[0].lineSubtotal = order.total; }
      data = order;
    } else if (path.endsWith("/promotion")) {
      const body = req.postDataJSON(); promotions.push(body);
      if (body.code === "BAD") return route.fulfill({ status: 422, contentType: "application/json", body: JSON.stringify({ code: "PROMOTION_NOT_FOUND", message: "Mã không hợp lệ." }) });
      if (body.code === "FROZEN") return route.fulfill({ status: 409, contentType: "application/json", body: JSON.stringify({ code: "PRICE_FROZEN", message: "Đơn đã bắt đầu thanh toán." }) });
      order = { ...order, discount: "4000", total: "36000" }; data = order;
    } else if (path.endsWith("/cancel")) { order = { ...order, state: "CANCELLED" }; data = order; }
    else if (path === "/api/v1/me") data = { displayName: "Buyer fixture" };
    else return route.fulfill({ status: 404, contentType: "application/json", body: "{}" });
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(data) });
  });
  const openSelection = async () => { await page.goto(`http://127.0.0.1:5173/events/${eventId}/book`); await page.locator(".booking-sidebar").waitFor(); };
  const confirm = async () => { await page.locator(".btn-continue").click(); await page.getByLabel("Email", { exact: true }).fill("buyer@example.invalid"); await page.getByRole("button", { name: "Xác nhận & Giữ vé", exact: true }).click(); };
  const checks = [];
  const capture = async (name, width, height = 1000) => {
    await page.setViewportSize({ width, height });
    await page.evaluate(() => document.fonts.ready);
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1)) throw new Error(`Layout overflow: ${name} ${width}`);
    await page.screenshot({ path: `D:/DATN/flash-ticket-platform/output/playwright/fe-booking-${name}-${width}.png`, fullPage: true });
  };
  await page.setViewportSize({ width: 1440, height: 1000 });
  await openSelection();
  const rowA = page.locator(".quantity-ticket-row").filter({ hasText: "Loại A" });
  await rowA.getByRole("button", { name: "+", exact: true }).click();
  await rowA.getByRole("button", { name: "+", exact: true }).click();
  await confirm(); await page.waitForURL("**/checkout?orderId=**");
  const first = creates.at(-1);
  if (!first.key || first.body.selection.mode !== "QUANTITY" || first.body.selection.items[0].quantity !== 2 || Object.keys(first.body).sort().join() !== "customerEmail,eventId,selection") throw new Error("Create order contract/key incorrect");
  if (!await page.locator(".booking-order-total").innerText().then(t => t.includes("40.000"))) throw new Error("Checkout must use server total, not client estimate");
  checks.push("QUANTITY + exact v1 body/key + authoritative server total");
  await page.getByLabel("Mã giảm giá", { exact: true }).fill("BAD");
  await page.getByRole("button", { name: "Áp dụng mã", exact: true }).click();
  await page.getByRole("alert").filter({ hasText: "Mã không hợp lệ" }).waitFor();
  await page.getByLabel("Mã giảm giá", { exact: true }).fill("TEST10");
  await page.getByRole("button", { name: "Áp dụng mã", exact: true }).click();
  await page.locator(".booking-order-total").filter({ hasText: "36.000" }).waitFor();
  if (creates.length !== 1 || promotions.length !== 2 || Object.keys(promotions[1]).join() !== "code") throw new Error("Voucher must be separate from create and never recreate order");
  await page.reload(); await page.locator(".booking-order-total").filter({ hasText: "36.000" }).waitFor();
  checks.push("voucher error/retry + server prices + reload receipt without duplicate hold");
  await capture("checkout", 1440); await capture("checkout", 390, 844); await capture("checkout", 320, 568);
  const deadline = order.expiresAt;
  await page.getByLabel("Mã giảm giá", { exact: true }).fill("FROZEN"); await page.getByRole("button", { name: "Áp dụng mã", exact: true }).click();
  await page.getByRole("alert").filter({ hasText: "Đơn đã bắt đầu" }).waitFor();
  if (!await page.getByRole("button", { name: "Áp dụng mã", exact: true }).isDisabled() || deadline !== order.expiresAt) throw new Error("Frozen price must stop voucher attempts without extending the hold");
  checks.push("frozen payment stops voucher edits; deadline unchanged");
  await page.getByRole("button", { name: "Hủy đơn", exact: true }).click();
  await page.getByRole("status").filter({ hasText: "Đã hủy đơn" }).waitFor();
  if (!await page.getByRole("button", { name: "Áp dụng mã", exact: true }).isDisabled()) throw new Error("Cancelled order must not accept vouchers");
  checks.push("explicit cancel uses Booking response");

  mode = "STANDING"; await openSelection();
  await page.getByRole("button", { name: "Khu đứng", exact: true }).click();
  const standing = page.locator(".standing-sector-modal");
  for (const type of ["Loại A", "Loại A", "Loại B", "Loại B"]) await standing.locator(".quantity-ticket-row").filter({ hasText: type }).getByRole("button", { name: "+", exact: true }).click();
  await capture("standing", 390, 844);
  await page.keyboard.press("Escape"); await confirm(); await page.waitForURL("**/checkout?orderId=**");
  const standingRequest = creates.at(-1).body.selection;
  if (standingRequest.mode !== "SEAT_MAP" || standingRequest.kind !== "STANDING" || standingRequest.sectorId !== standingId || standingRequest.items.reduce((n, item) => n + item.quantity, 0) !== 3) throw new Error("Standing selection oversold a shared pool or used wrong contract");
  checks.push("STANDING shared sector pool across ticket types");

  mode = "SEATED"; await openSelection();
  await page.getByText("Chọn ghế bằng danh sách", { exact: true }).click();
  if (!await page.getByRole("button", { name: "Khu A — A2", exact: true }).isDisabled()) throw new Error("Held seat must be disabled");
  await page.getByRole("button", { name: "Khu A — A1", exact: true }).click();
  await page.getByRole("button", { name: "Khu B — B1", exact: true }).click();
  await capture("seats", 1440); await capture("seats", 390, 844); await capture("seats", 320, 568);
  await confirm(); await page.waitForURL("**/checkout?orderId=**");
  const seated = creates.at(-1).body.selection;
  if (seated.kind !== "SEATED" || seated.sectorId !== seatedId || seated.seatIds.join() !== seatA || "items" in seated) throw new Error("SEATED must send only seatIds from one sector");
  checks.push("SEATED IDs + held seat disabled + one sector");

  mode = "QUANTITY"; failCreate = true; await openSelection();
  await rowA.getByRole("button", { name: "+", exact: true }).click(); await confirm();
  await page.locator(".booking-submit-error").waitFor();
  const failedKey = creates.at(-1).key;
  await page.reload(); await page.locator(".booking-sidebar").waitFor();
  await rowA.getByRole("button", { name: "+", exact: true }).click(); await confirm();
  await page.waitForURL("**/checkout?orderId=**");
  if (creates.at(-1).key !== failedKey) throw new Error("Unknown-outcome retry after reload changed Idempotency-Key");
  checks.push("network failure/reload retry reuses Idempotency-Key");

  // Unknown A, successful B, then retry A: retain A's key rather than overwriting it.
  const directInput = { eventId, customerEmail: "retry-a@example.invalid", selection: { mode: "QUANTITY", items: [{ ticketTypeId: typeA, quantity: 1 }] } };
  failCreate = true;
  await page.evaluate(async input => { const { bookingService: s } = await import("/src/services/bookingService.ts"); await s.createOrder(input).catch(() => {}); }, directInput);
  const keyA = creates.at(-1).key;
  await page.evaluate(async input => { const { bookingService: s } = await import("/src/services/bookingService.ts"); await s.createOrder({ ...input, customerEmail: "retry-b@example.invalid" }); await s.createOrder(input); }, directInput);
  if (creates.at(-1).key !== keyA || creates.at(-2).key === keyA) throw new Error("A/B/A retry lost pending A's key");
  checks.push("pending keys survive switching request A/B/A");

  failAvailability = true; await page.goto(`http://127.0.0.1:5173/events/${eventId}/book`);
  await page.getByRole("alert").waitFor();
  failAvailability = false; await page.getByRole("button", { name: "Thử lại", exact: true }).click(); await page.locator(".booking-sidebar").waitFor();
  rejectInventory = true; await rowA.getByRole("button", { name: "+", exact: true }).click(); await confirm();
  await page.locator(".booking-submit-error").filter({ hasText: "Vé vừa hết" }).waitFor();
  if (await page.locator(".btn-continue").count()) throw new Error("Stale inventory selection must be cleared after refresh");
  checks.push("availability failure/retry and inventory conflict refresh");
  await capture("quantity", 1440); await capture("quantity", 390, 844); await capture("quantity", 320, 568);

  mode = "STANDING";
  await page.evaluate(({ eventId, typeA, typeB, standingId }) => {
    sessionStorage.setItem("transient_slug", eventId);
    sessionStorage.setItem("transient_quantities", JSON.stringify({ [typeA]: 3, [typeB]: 3 }));
    sessionStorage.setItem("transient_selectedStandingSectorId", standingId);
  }, { eventId, typeA, typeB, standingId });
  await openSelection(); await confirm(); await page.waitForURL("**/checkout?orderId=**");
  if (creates.at(-1).body.selection.items.reduce((n, item) => n + item.quantity, 0) !== 3) throw new Error("Login draft ignored current standing pool availability");
  checks.push("login draft is revalidated against current shared-pool inventory");
  mode = "QUANTITY";

  expired = true; await openSelection(); await rowA.getByRole("button", { name: "+", exact: true }).click(); await confirm(); await page.waitForURL("**/checkout?orderId=**");
  if (!await page.getByRole("button", { name: "Áp dụng mã", exact: true }).isDisabled()) throw new Error("Expired order must not apply promotions");
  if (requests.some(r => r.method === "GET" && /\/api\/.*orders/.test(r.path))) throw new Error("Frontend invented a GET order endpoint");
  checks.push("server deadline + no invented read endpoint");
  if (requests.filter(r => r.path.endsWith("/cancel")).length !== 1) throw new Error("Page reload or local deadline must never auto-cancel");
  checks.push("deadline/reload never auto-cancel a hold");
  expired = false; largeAmount = true;
  await openSelection(); await rowA.getByRole("button", { name: "+", exact: true }).click();
  await page.locator(".btn-continue").click();
  if (!await page.locator("dialog[open]").evaluate(dialog => dialog.contains(document.activeElement))) throw new Error("Recipient dialog did not receive focus");
  await page.keyboard.press("Escape");
  if (!await page.locator(".btn-continue").evaluate(button => button === document.activeElement)) throw new Error("Recipient dialog did not return focus");
  await confirm(); await page.waitForURL("**/checkout?orderId=**");
  await page.locator(".booking-order-total").filter({ hasText: "9.007.199.254.740.993" }).waitFor();
  await page.evaluate(async () => { const { default: i18n } = await import("/src/i18n/index.ts"); await i18n.changeLanguage("en"); });
  await page.getByLabel("Voucher code", { exact: true }).waitFor();
  await page.locator(".booking-order-total").filter({ hasText: "9,007,199,254,740,993" }).waitFor();
  checks.push("native recipient dialog focus/Escape + VI/EN + exact large monetary values");
  await page.evaluate(async () => { const { default: kc } = await import("/src/lib/keycloak.ts"); kc.tokenParsed.sub = "another-buyer"; });
  const cached = await page.evaluate(async orderId => { const { bookingService } = await import("/src/services/bookingService.ts"); return bookingService.getCachedOrder(orderId); }, orderId);
  if (cached) throw new Error("Order receipt leaked to another subject");
  checks.push("receipt isolated by buyer/session");
  await page.goto(`http://127.0.0.1:5173/checkout?orderId=${ids[13]}`);
  await page.getByRole("alert").filter({ hasText: "Không tìm thấy thông tin đơn" }).waitFor();
  if (requests.some(r => r.method === "GET" && /\/api\/.*orders/.test(r.path))) throw new Error("Missing receipt triggered an invented read API");
  checks.push("missing receipt stays empty without a fabricated read API");
  return { passed: checks.length, checks };
}
