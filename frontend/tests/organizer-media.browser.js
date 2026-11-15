async (page, origin = "http://127.0.0.1:5177") => {
  const id = "3ea2252f-9a6b-4093-8ddd-183e26e4b5cb";
  const owner = "/api/v1/organizer/events/" + id;
  const publicPath = "/api/v1/events";
  const url = name => origin + "/event-placeholder.svg?" + name;
  let event = {
    eventId: id, title: "Media fixture", description: "Desktop layout", mode: "QUANTITY",
    status: "DRAFT", startsAt: "2028-01-01T19:00:00Z", endsAt: "2028-01-01T22:00:00Z",
    venueName: "Nhà hát", rowVersion: "1", ticketTypes: [], promotions: [],
    images: ["a", "b"].map((imageId, displayOrder) => ({
      imageId, objectReference: url(imageId), displayOrder, role: "GALLERY",
    })),
  };
  const writes = [];
  await page.addInitScript(() => {
    localStorage.setItem("flashTicket.language", "vi");
    window.__KEYCLOAK_INSTANCE__ = {
      authenticated: true, subject: "owner",
      tokenParsed: { sub: "owner", iss: "fixture", realm_access: { roles: ["ORGANIZER"] } },
      init() { setTimeout(() => this.onReady?.(true), 0); return Promise.resolve(true); },
      isTokenExpired() { return false; },
    };
  });
  await page.route("**/api/**", async route => {
    const req = route.request(), target = new URL(req.url()), path = target.pathname, method = req.method();
    if (path === "/api/v1/venues" || path === "/api/v1/categories") return route.fulfill({ json: [] });
    if (path === owner && method === "GET") return route.fulfill({ json: event });
    if (path === publicPath) {
      const items = [event, ...[1, 2, 3].map(n => ({ ...event, eventId: "event-" + n, title: "Event " + n }))];
      return route.fulfill({ json: { items, nextCursor: null } });
    }
    if (path === publicPath + "/" + id) return route.fulfill({ json: event });
    if (!path.startsWith(owner + "/images")) return route.fulfill({ json: {} });
    const imageId = path.split("/").at(-1);
    const body = req.postData() || "";
    const field = (name, value) => body.includes('name="' + name + '"\r\n\r\n' + value + '\r\n');
    writes.push(method);
    if (method === "POST") {
      if (!field("rowVersion", event.rowVersion) || !field("role", "BANNER")
          || !body.includes('filename="banner.webp"') || !body.includes("Content-Type: image/webp")) {
        return route.fulfill({ status: 400, json: {} });
      }
      event.images.push({ imageId: "banner", objectReference: url("banner"), displayOrder: 0, role: "BANNER" });
    } else if (method === "PATCH") {
      const order = req.postDataJSON();
      if (imageId !== "b" || order.rowVersion !== event.rowVersion || order.displayOrder !== 0)
        return route.fulfill({ status: 400, json: {} });
      event.images = event.images.map(image => ({
        ...image, displayOrder: image.role === "BANNER" || image.imageId === "b" ? 0 : 1,
      }));
    } else if (method === "DELETE") {
      if (imageId !== "banner" || target.searchParams.get("rowVersion") !== event.rowVersion)
        return route.fulfill({ status: 400, json: {} });
      event.images = event.images.filter(image => image.imageId !== imageId);
    }
    event.rowVersion = String(Number(event.rowVersion) + 1);
    return route.fulfill({ json: event });
  });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto(origin + "/organizer/events/" + id + "/edit?step=media");
  const media = page.locator(".tix-media-manager");
  const cover = media.locator(".tix-media-cover-preview img");
  const add = media.locator(".tix-media-cover-spotlight input");
  await media.locator(".tix-media-card").nth(1).waitFor();
  if (!(await cover.getAttribute("src")).endsWith("/event-placeholder.svg")) throw Error("Gallery is used as banner");
  await add.setInputFiles({
    name: "small.webp", mimeType: "image/webp",
    buffer: Buffer.from("UklGRhwAAABXRUJQVlA4TA8AAAAvA0AAAAcQ/Y/+ByKi/wEA", "base64"),
  });
  await media.getByRole("alert").filter({ hasText: "1280" }).waitFor();
  if (writes.length) throw Error("Invalid banner was sent");
  const data = await page.evaluate(() => {
    const canvas = document.createElement("canvas");
    canvas.width = 1280;
    canvas.height = 720;
    return canvas.toDataURL("image/webp").split(",")[1];
  });
  await add.setInputFiles({ name: "banner.webp", mimeType: "image/webp", buffer: Buffer.from(data, "base64") });
  await media.locator('.tix-media-cover-preview img[src$="?banner"]').waitFor();
  await media.locator(".tix-media-card").nth(1).getByRole("button", { name: "Đưa ảnh lên trước" }).click();
  await media.locator('.tix-media-card:first-child img[src$="?b"]').waitFor();
  await media.locator(".tix-media-cover-actions").getByRole("button", { name: "Xóa ảnh" }).click();
  await page.locator(".swal2-confirm").click();
  await media.locator('.tix-media-cover-preview img[src="/event-placeholder.svg"]').waitFor();
  if (writes.join() !== "POST,PATCH,DELETE") throw Error("Unexpected writes: " + writes.join());
  async function ratio(selector, expected) {
    const frame = page.locator(selector).first();
    await frame.waitFor();
    const box = await frame.boundingBox();
    if (Math.abs(box.width / box.height - expected) > .015) throw Error("Wrong aspect ratio: " + selector + " " + JSON.stringify(box));
  }
  await ratio(".tix-media-cover-preview", 16 / 9);
  await ratio(".tix-media-preview-wrap", 3 / 4);
  await page.goto(origin + "/");
  await ratio(".discovery-feature:first-child .discovery-image", 16 / 9);
  await ratio(".discovery-feature:nth-child(2) .discovery-image", 16 / 9);
  await ratio(".discovery-upcoming .discovery-image", 16 / 9);
  await page.goto(origin + "/search");
  for (const kind of ["lead", "side", "row"]) {
    await ratio(".explore-event--" + kind + " .explore-image", 16 / 9);
  }
  await page.goto(origin + "/event/" + id);
  await ratio("img.event-editorial-poster", 16 / 9);
  const detailBanner = await page.locator("img.event-editorial-poster").getAttribute("src");
  if (!detailBanner.endsWith("/event-placeholder.svg")) throw Error("Gallery used as public banner");
  await page.locator("#tab-photos").click();
  await ratio(".event-editorial-gallery img", 3 / 4);
  return ["reject wrong banner size", "upload WebP banner", "reorder gallery", "delete banner without promotion", "desktop frame ratios"];
}
