async (page, origin = "http://127.0.0.1:5177") => {
  const root = "/api/v1/organizer/events";
  const id = "497e2026-c37c-4bee-92e4-8af9f0878d1f";
  const seated = "497e2026-c37c-4bee-92e4-8af9f0878d2f";
  const venueId = "venue-test";
  const ticketId = "ticket-test";
  const image = { imageId: "image-test", objectReference: origin + "/event-placeholder.svg?banner", displayOrder: 0, role: "BANNER" };
  const promotion = { promotionId: "promotion-test", code: "SALE", startsAt: "2027-01-01T03:00:00Z", endsAt: "2027-01-20T02:00:00Z", maximumUses: "5", discount: { kind: "FIXED", amount: "1000" } };
  let draft, savedBody, submitBody, reads = 0, created = 0, priceWrites = 0;
  const requests = [];
  await page.addInitScript(() => {
    window.__KEYCLOAK_INSTANCE__ = {
      authenticated: true, subject: "owner", tokenParsed: { sub: "owner", iss: "fixture", realm_access: { roles: ["ORGANIZER"] } },
      init() { setTimeout(() => this.onReady?.(true), 0); return Promise.resolve(true); },
      isTokenExpired() { return false; },
    };
  });
  await page.route("**/api/**", async route => {
    const req = route.request();
    const { pathname: path } = new URL(req.url());
    const method = req.method();
    requests.push(path);
    if (path === "/api/v1/categories") return route.fulfill({ json: [] });
    if (path === "/api/v1/venues") return route.fulfill({ json: [{ id: venueId, name: "Địa điểm fixture" }] });
    if (path === root && method === "POST") {
      created += 1;
      const body = req.postDataJSON();
      draft = { ...body, eventId: id, status: "DRAFT", rowVersion: "1",
        ticketTypes: body.ticketTypes.map(ticket => ({ ...ticket, ticketTypeId: ticketId })),
      };
      return route.fulfill({ status: 201, json: draft });
    }
    if (path === root + "/" + id && method === "GET") {
      reads += 1;
      return route.fulfill({ json: draft });
    }
    if (path === root + "/" + id + "/images" && method === "POST") {
      draft = { ...draft, rowVersion: "2", images: [image], promotions: [promotion] };
      return route.fulfill({ json: draft });
    }
    if (path === root + "/" + id && method === "PUT") {
      savedBody = req.postDataJSON();
      draft = { ...draft, ...savedBody.draft, images: [image], rowVersion: "3" };
      return route.fulfill({ json: draft });
    }
    if (path === root + "/" + id + "/submit" && method === "POST") {
      submitBody = req.postDataJSON();
      return route.fulfill({ json: { ...draft, status: "PENDING_APPROVAL", rowVersion: "4" } });
    }
    if (path === root + "/" + seated && method === "GET") return route.fulfill({
      json: { ...draft, eventId: seated, mode: "SEAT_MAP", status: "DRAFT", ticketTypes: [],
        seatMap: { sectors: [{ sectorId: "sector-test", name: "Khu đứng", kind: "STANDING", configuredCapacity: "10", seats: [] }] },
      },
    });
    if (path === root + "/" + seated + "/ticket-types" && method === "PUT") priceWrites += 1;
    return route.fulfill({ status: 404, json: {} });
  });

  await page.goto(origin + "/organizer/events/new");
  await page.locator('input[name="title"]').fill("Bản nháp fixture");
  await page.locator('select[name="venueId"]').selectOption(venueId);
  await page.locator('[name=startsAt]').fill("2027-01-20T10:00");
  await page.locator('[name=endsAt]').fill("2027-01-20T12:00");
  await page.locator('[name=saleStartsAt]').fill("2027-01-01T10:00");
  await page.locator('[name=saleEndsAt]').fill("2027-01-20T09:00");
  await page.getByLabel("Giới hạn vé mỗi người").fill("2");
  await page.getByLabel("Tên loại vé").fill("Vé thường");
  await page.getByLabel("Số lượng", { exact: true }).fill("10");
  const price = page.getByLabel("Giá vé (VND)");
  await price.fill("0");
  await page.getByRole("button", { name: "Lưu bản nháp" }).click();
  if (created || await price.evaluate(el => el.checkValidity())) throw Error("Zero price accepted by quantity form");

  const readDraft = page.waitForResponse(response => response.request().method() === "GET" && response.url().endsWith(root + "/" + id));
  await price.fill("250000");
  await page.getByRole("button", { name: "Lưu bản nháp" }).click();
  await page.waitForURL("**/organizer/events/" + id + "/edit");
  await readDraft;
  const mediaUpload = page.waitForResponse(response => response.request().method() === "POST" && response.url().endsWith(root + "/" + id + "/images"));
  const banner = await page.evaluate(() => {
    const canvas = document.createElement("canvas");
    canvas.width = 1280;
    canvas.height = 720;
    return canvas.toDataURL("image/png").split(",")[1];
  });
  await page.getByLabel("Thêm ảnh bìa").setInputFiles({ name: "test.png", mimeType: "image/png", buffer: Buffer.from(banner, "base64") });
  if (!(await mediaUpload).ok()) throw Error("Fixture image upload did not succeed");
  await page.locator('.tix-media-cover-preview img[src$="/event-placeholder.svg?banner"]').waitFor({ timeout: 7000 });
  await page.reload();
  await page.locator('.tix-media-cover-preview img[src$="/event-placeholder.svg?banner"]').waitFor();
  await page.locator('input[name="title"]').fill("Bản nháp đã sửa");
  if (!await page.getByRole("button", { name: "Gửi duyệt" }).isDisabled()) throw Error("Can submit unsaved draft changes");
  const put = page.waitForResponse(response => response.request().method() === "PUT" && response.url().endsWith(root + "/" + id));
  await page.getByRole("button", { name: "Lưu bản nháp" }).click();
  await put;
  if (reads < 2 || created !== 1 || savedBody.rowVersion !== "2" || savedBody.draft.ticketTypes[0].ticketTypeId !== ticketId ||
      savedBody.draft.images[0].imageId !== image.imageId || "role" in savedBody.draft.images[0] || savedBody.draft.promotions[0].promotionId !== promotion.promotionId) {
    throw Error("Draft did not reload or update with existing children and version");
  }
  await page.getByRole("button", { name: "Gửi duyệt" }).click();
  await page.getByText(/Đã gửi duyệt/).waitFor();
  if (submitBody.rowVersion !== "3") throw Error("Submit used stale rowVersion");

  await page.goto(origin + "/organizer/events/" + seated + "/configure-seating?step=pricing");
  const seatedPrice = page.getByLabel("Giá vé (VND)");
  await seatedPrice.fill("0");
  await page.getByRole("button", { name: "Lưu loại vé" }).click();
  if (priceWrites || await seatedPrice.evaluate(el => el.checkValidity())) throw Error("Zero price accepted by seating form");
  if (requests.some(path => path.startsWith("/api/") && !path.startsWith("/api/v1/"))) throw Error("Draft used legacy endpoints");
  return { passed: ["quantity price validation", "create, reopen and upload", "versioned edit preserves ticket IDs, media and promotions", "submit uses saved version", "seat price validation and v1 routes"] };
}

