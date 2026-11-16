// Contract của Event dev/tuyen; fixture chỉ nằm trong tab kiểm tra.
async (page) => {
  await page.addInitScript(() => {
    window.__KEYCLOAK_INSTANCE__ = {
      authenticated: false,
      init() { setTimeout(() => this.onReady?.(false), 0); return Promise.resolve(false); },
      isTokenExpired() { return false; }, updateToken() { return Promise.resolve(false); },
    };
  });
  const requests = [];
  const origin = "http://127.0.0.1:5173";
  const eventId = "00000000-0000-4000-8000-000000000001";
  const categoryId = "00000000-0000-4000-8000-000000000002";
  const otherEventId = "00000000-0000-4000-8000-000000000006";
  const ticketTypeId = "00000000-0000-4000-8000-000000000003";
  const event = {
    eventId, title: "Sự kiện API hiện tại", description: "Thông tin từ Event Service",
    startsAt: "2027-12-12T12:00:00Z", endsAt: "2027-12-12T15:00:00Z", venueName: "Nhà hát", mode: "QUANTITY",
    images: [{ imageId: eventId, objectReference: "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=800", displayOrder: 0, role: "BANNER" }],
    ticketTypes: [{ ticketTypeId, name: "Vé thường", unitPrice: "250000", currency: "VND", configuredQuantity: "100", sectorId: null, colorCode: "#cc9900" }], seatMap: null,
  };
  const availability = {
    eventId, sourceConfigVersion: "1", asOf: event.startsAt,
    ticketTypes: [{ ticketTypeId, available: "85" }], sectors: [], seats: [],
  };
  await page.route("**/api/**", async route => {
    const url = new URL(route.request().url());
    requests.push(url.pathname + url.search);
    let data;
    if (url.pathname === "/api/v1/events") data = { items: [event], nextCursor: url.searchParams.get("cursor") ? null : "next-page" };
    else if (url.pathname === `/api/v1/events/${eventId}`) data = event;
    else if (url.pathname === `/api/v1/events/${eventId}/availability`) data = availability;
    else if (url.pathname === "/api/v1/events/" + otherEventId) data = { ...event, eventId: otherEventId, title: "Sự kiện chưa có tồn vé", ticketTypes: [], mode: "QUANTITY", seatMap: null };
    else if (url.pathname === "/api/v1/categories") data = [{ id: categoryId, name: "Âm nhạc", address: null }];
    else if (url.pathname === "/api/v1/venues") data = [{ id: eventId, name: "Nhà hát", address: "Địa chỉ thật trong API" }];
    else return route.fulfill({ status: 404, contentType: "application/json", body: "{}" });
    return route.fulfill({ contentType: "application/json", body: JSON.stringify(data) });
  });
  await page.goto(origin);
  const result = await page.evaluate(async ({ eventId, categoryId }) => {
    const { eventService } = await import("/src/services/eventService.ts");
    const { categoryService } = await import("/src/services/categoryService.ts");
    const { venueService } = await import("/src/services/venueService.ts");
    const events = await eventService.getEvents({ search: "âm nhạc", category: categoryId, size: 12 });
    const detail = await eventService.getEventDetails(eventId);
    const map = await eventService.getSeatMap(eventId);
    return { events, detail, map, categories: await categoryService.getCategories(), venues: await venueService.getVenues() };
  }, { eventId, categoryId });
  if (result.events.content[0]?.id !== eventId || result.detail.minPrice !== 250000) throw new Error("Event wire DTO was not mapped to UI model");
  if (result.detail.ticketTypes[0].id !== ticketTypeId || result.detail.startDatetime !== event.startsAt) throw new Error("Ticket IDs or dates lost in mapping");
  if (result.events.nextCursor !== "next-page" || result.map !== null) throw new Error("Cursor or optional seatmap changed");
  const query = new URLSearchParams(requests.find(r => r.startsWith("/api/v1/events?") && r.includes("q="))?.split("?")[1]);
  if (query.get("q") !== "âm nhạc" || query.get("categoryId") !== categoryId || query.has("page") || query.has("sort")) throw new Error("Query uses unsupported legacy parameters");
  if (requests.some(r => /\/api\/(events|categories|venues)/.test(r) || r.includes("/seat-map"))) throw new Error("Public client called a legacy or organizer-only endpoint");
  if (result.categories[0].slug !== categoryId || result.venues[0].address !== "Địa chỉ thật trong API") throw new Error("Lookup mapping lost IDs or addresses");
  const security = await page.evaluate(async () => {
    const { hasRealmRole } = await import("/src/lib/auth.ts");
    const { default: keycloak } = await import("/src/lib/keycloak.ts");
    const { default: client } = await import("/src/lib/axiosClient.ts");
    let sent = false;
    client.defaults.adapter = async config => { sent = true; return { data: {}, status: 200, statusText: "OK", headers: {}, config }; };
    keycloak.authenticated = true;
    keycloak.token = "expired-fixture-token";
    keycloak.isTokenExpired = () => true;
    keycloak.updateToken = () => Promise.reject(new Error("fixture refresh failure"));
    keycloak.clearToken = () => { keycloak.token = undefined; keycloak.authenticated = false; };
    let rejected = false;
    try { await client.get("/api/v1/me"); } catch { rejected = true; }
    return { rejected, sent, cleared: !keycloak.token, realm: hasRealmRole({ realm_access: { roles: ["BUYER"] } }, "BUYER"), resourceOnly: hasRealmRole({ resource_access: { client: { roles: ["ADMIN"] } } }, "ADMIN") };
  });
  if (!security.rejected || security.sent || !security.cleared) throw new Error("Refresh failure leaked a stale token/request");
  if (!security.realm || security.resourceOnly) throw new Error("UI role check must match backend realm roles");
  await page.goto(origin + "/search");
  await page.locator(".explore-event").first().waitFor();
  const nextPage = page.waitForRequest(request => new URL(request.url()).searchParams.get("cursor") === "next-page");
  await page.getByRole("button", { name: /Xem thêm sự kiện|Load more events/ }).click();
  await nextPage;

  event.description = '<img src="x" onerror="window.__eventDescriptionExecuted = true">Mô tả từ organizer';
  await page.goto(`http://127.0.0.1:5173/event/${eventId}`);
  await page.locator(".event-detail-description").waitFor();
  if (await page.locator(".event-detail-description img").count() || await page.evaluate(() => window.__eventDescriptionExecuted)) throw new Error("Event description must not execute organizer HTML");
  await page.locator(".event-ticket-badge").getByText(/85/).waitFor();
  if (await page.getByText(/Đã bán|tickets sold/i).count()) throw new Error("Unavailable tickets were presented as purchased");
  if (!(await page.locator(".event-detail-description").textContent()).trim().startsWith("Mô tả từ organizer")) throw new Error("Event description exposed raw HTML");
  if (await page.getByText("Địa chỉ thật trong API").count()) throw new Error("Event detail inferred an address from a venue name");

  const sectorId = "00000000-0000-4000-8000-000000000004";
  const secondTypeId = "00000000-0000-4000-8000-000000000005";
  event.mode = "SEAT_MAP";
  event.ticketTypes[0].configuredQuantity = null;
  event.ticketTypes[0].sectorId = sectorId;
  event.ticketTypes[0].unitPrice = "0";
  event.ticketTypes.push({ ...event.ticketTypes[0], ticketTypeId: secondTypeId, name: "Vé khu đứng khác" });
  event.seatMap = {
    layoutId: eventId, schemaVersion: 1, name: "Sơ đồ khu đứng", canvasWidth: 100, canvasHeight: 100,
    backgroundImageReference: null, layoutData: { schemaVersion: 1, decorations: [] },
    sectors: [{ sectorId, name: "Khu đứng", code: "ST", kind: "STANDING", configuredCapacity: "50",
      displayOrder: 0, colorCode: "#cc9900", geometry: { schemaVersion: 1, shape: {}, seatLayout: {} }, seats: [] }],
  };
  availability.ticketTypes = [{ ticketTypeId, available: "30" }, { ticketTypeId: secondTypeId, available: "30" }];
  availability.sectors = [{ sectorId, kind: "STANDING", available: "30" }];
  await page.reload();
  await page.getByRole("status").getByText(/30/).waitFor();
  await page.locator(".event-ticket-item-meta").getByText(/Vé khu đứng|Standing ticket/).first().waitFor();
  await page.locator(".event-hero-price-value").getByText(/Miễn phí|Free/).waitFor();

  await page.locator(".event-hero-meta-secondary").getByText(/19:00/).waitFor();

  availability.ticketTypes.forEach(type => { type.available = "0"; });
  availability.sectors[0].available = "0";
  await page.reload();
  const buyButton = page.locator(".event-hero-cta-button");
  await buyButton.getByText(/Hết vé|Sold out/).waitFor();
  if (!await buyButton.isDisabled()) throw new Error("Sold-out event left its purchase button enabled");
  await page.evaluate(id => {
    window.history.pushState({}, "", "/event/" + id);
    window.dispatchEvent(new PopStateEvent("popstate"));
  }, otherEventId);
  await page.getByRole("heading", { name: "Sự kiện chưa có tồn vé" }).waitFor();
  await page.getByRole("status").getByText(/Chưa xác định số vé còn lại|Ticket availability unknown/).waitFor();
  if (!await buyButton.isEnabled()) throw new Error("Previous event inventory disabled the next event purchase button");
  await page.locator(".event-hero-price-value").getByText(/Đang cập nhật|Updating/).waitFor();

  const beforeInvalidUrl = requests.length;
  await page.goto("http://127.0.0.1:5173/event/API");
  await page.locator(".event-detail-page h2").first().waitFor();
  if (await page.locator(".event-hero-title").count() || requests.slice(beforeInvalidUrl).some(path => path.startsWith("/api/v1/events?"))) {
    throw new Error("An invalid event ID triggered title search or rendered a different event");
  }
  return { passed: 19, checks: ["v1 endpoints", "event/ticket IDs + time + price mapping", "cursor", "supported q/categoryId query", "embedded optional seatmap", "lookup IDs/address", "no request/stale token after failed refresh", "realm-role parity", "search cursor navigation", "description rendered as text", "available versus sold", "venue identity", "shared standing inventory", "standing label", "zero price", "invalid ID does not search", "Vietnam event time", "inventory isolated across routes", "missing price"] };
}
