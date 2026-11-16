async page => {
  const report = { from: "2026-11-06", to: "2026-11-12", asOf: "2026-11-11T18:00:00Z", currency: "VND" };
  const requests = [];
  let fail = false;
  await page.clock.setFixedTime(new Date(report.asOf));
  await page.addInitScript(() => {
    localStorage.setItem("flashTicket.language", "vi");
    window.__KEYCLOAK_INSTANCE__ = {
      authenticated: true, subject: "owner", tokenParsed: { sub: "owner", iss: "fixture", realm_access: { roles: ["ORGANIZER"] } },
      init() { setTimeout(() => this.onReady?.(true), 0); return Promise.resolve(true); },
      isTokenExpired() { return false; },
    };
  });
  const events = Array.from({ length: 21 }, (_, index) => ({
    eventId: String(index + 1), title: "Event " + (index + 1), status: "PUBLISHED",
    mode: "QUANTITY", startsAt: "2026-12-01T12:00:00Z",
  }));
  const sales = (eventId, soldTicketCount, soldRevenue) => ({
    eventId, eventTitle: "Event " + eventId, soldTicketCount, soldRevenue, soldOrderCount: soldTicketCount,
  });
  await page.route("**/api/**", async route => {
    const url = new URL(route.request().url());
    requests.push(url);
    const { pathname, searchParams } = url;
    if (pathname === "/api/v1/organizer/events") {
      return route.fulfill({ json: searchParams.get("cursor") === "next"
        ? { items: events.slice(20), nextCursor: null }
        : { items: events.slice(0, 20), nextCursor: "next" } });
    }
    if (pathname === "/api/v1/organizers/owner") return route.fulfill({ json: { organizationName: "Organizer fixture" } });
    if (pathname === "/api/v1/me") return route.fulfill({ json: { displayName: "Organizer fixture" } });
    if (fail) return route.fulfill({ status: 503, json: {} });
    if (pathname.endsWith("/sales/summary")) return route.fulfill({ json: { ...report, ...sales("21", 5, "500000") } });
    if (pathname.endsWith("/sales/chart")) return route.fulfill({ json: {
      ...report, items: [{ ...sales("21", 5, "500000"), periodStart: report.to, periodEnd: report.to }],
    } });
    if (pathname.endsWith("/events/sales-statistics")) {
      const pageIndex = Number(searchParams.get("page"));
      return route.fulfill({ json: {
        ...report, page: pageIndex, totalPages: 2,
        items: pageIndex ? [sales("1", 0, "0")] : [sales("21", 5, "500000")],
      } });
    }
    return route.fulfill({ status: 404, json: {} });
  });

  await page.goto("http://127.0.0.1:5177/organizer");
  await page.locator(".tix-kpi-value-lg").first().waitFor();
  await page.waitForLoadState("networkidle");
  if (!requests.some(url => url.searchParams.get("cursor") === "next")) throw Error("Event cursor was ignored");
  if (!requests.some(url => url.searchParams.get("page") === "1")) throw Error("Booking pagination was ignored");
  if (!requests.some(url => url.searchParams.get("sortBy") === "soldTicketCount")) throw Error("Ranking is not sorted by Booking");
  if (requests.filter(url => url.pathname.includes("/sales/") || url.pathname.endsWith("/sales-statistics"))
      .some(url => url.searchParams.get("from") !== report.from || url.searchParams.get("to") !== report.to)) {
    throw Error("Report range did not use Vietnam time");
  }
  await page.locator(".tix-tickets-type-list").getByText("Event 21", { exact: true }).waitFor();
  await page.getByRole("row").filter({ has: page.getByText("Event 1", { exact: true }) }).getByText("0 đ", { exact: true }).waitFor();
  await page.getByRole("row").filter({ has: page.getByText("Event 2", { exact: true }) }).getByText("Chưa có dữ liệu", { exact: true }).first().waitFor();

  fail = true;
  await page.reload();
  await page.getByRole("alert").filter({ hasText: "Không tải được dữ liệu" }).first().waitFor();
  if (await page.locator(".tix-area-chart-container svg").count()) throw Error("Failed chart displayed synthetic data");
  if (await page.locator(".tix-kpi-value-lg").getByText("0 đ", { exact: true }).count()) throw Error("Failed summary displayed fake zero");
  if (!await page.locator(".tix-export-btn").isDisabled()) throw Error("Incomplete sales report can be exported");
  return { passed: ["all event and sales pages", "server ranking", "Vietnam dates", "verified zero vs unavailable", "errors and export gating"] };
}

