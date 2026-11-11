import { FormEvent, useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import OrganizerLayout from "../../components/organizer/OrganizerLayout";
import axiosClient from "../../lib/axiosClient";
import { categoryService } from "../../services/categoryService";
import { venueService } from "../../services/venueService";
import { organizerDashboardService, OrganizerSummary } from "../../services/organizerDashboardService";
import OrganizerDraftMedia, { EventWithImages } from "./OrganizerDraftMedia";
import { useOrganizerGate } from "./useOrganizerGate";

const dateFields = [
  ["startsAt", "Bắt đầu sự kiện"], ["endsAt", "Kết thúc sự kiện"],
  ["saleStartsAt", "Bắt đầu bán vé"], ["saleEndsAt", "Kết thúc bán vé"],
] as const;

function EventDraftForm() {
  const client = useQueryClient();
  const navigate = useNavigate();
  const [mode, setMode] = useState<OrganizerSummary["mode"]>("QUANTITY");
  const [ticketIds, setTicketIds] = useState(() => [crypto.randomUUID()]);
  const [event, setEvent] = useState<EventWithImages>();
  const [mediaBusy, setMediaBusy] = useState(false);
  const [error, setError] = useState("");
  const options = useQuery({
    queryKey: ["event-draft-references"],
    queryFn: async () => {
      const [categories, venues] = await Promise.all([categoryService.getCategories(), venueService.getVenues()]);
      return { categories, venues };
    },
    retry: false,
  });

  function update(saved: EventWithImages) {
    setEvent(saved);
    void client.invalidateQueries({ queryKey: ["organizer-event-list"] });
    void client.invalidateQueries({ queryKey: ["organizer-dashboard-events"] });
    void client.invalidateQueries({ queryKey: ["admin-event-review"] });
  }

  const create = useMutation({
    mutationFn: async (draft: object) => (await axiosClient.post<EventWithImages>("/api/v1/organizer/events", draft)).data,
    onSuccess: saved => {
      update(saved);
      if (saved.mode === "SEAT_MAP") navigate(`/organizer/events/${saved.eventId}/configure-seating?step=media`, { replace: true });
    },
    onError: () => setError("Không lưu được bản nháp. Hãy kiểm tra dữ liệu và thử lại."),
  });
  const submit = useMutation({
    mutationFn: (draft: EventWithImages) => organizerDashboardService.transition(draft.eventId, "submit", draft.rowVersion),
    onSuccess: (saved, draft) => update({ ...draft, ...saved }),
    onError: () => setError("Chưa gửi duyệt được. Bản nháp đã lưu. Nếu phiên bản đã thay đổi, hãy mở lại danh sách sự kiện."),
  });

  function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (create.isPending || event) return;
    const values = new FormData(e.currentTarget);
    const read = (key: string) => String(values.get(key) ?? "").trim();
    const [start, end, saleStart, saleEnd] = dateFields.map(([key]) => new Date(read(key)));
    if (!(start < end && saleStart < saleEnd && saleEnd <= end)) {
      setError("Giờ bắt đầu phải trước giờ kết thúc. Giờ kết thúc bán vé không được sau khi sự kiện kết thúc.");
      return;
    }
    const ticketTypes = (mode === "QUANTITY" ? ticketIds : []).map(id => ({
      ticketTypeId: null, name: read(`name-${id}`), unitPrice: read(`price-${id}`),
      configuredQuantity: read(`quantity-${id}`), currency: "VND", colorCode: read(`color-${id}`),
    }));
    if (!read("title") || ticketTypes.some(ticket => !ticket.name)) {
      setError("Tên sự kiện và tên loại vé không được để trống.");
      return;
    }
    setError("");
    create.mutate({
      title: read("title"), description: read("description"),
      categoryId: read("categoryId") || null, venueId: read("venueId"),
      mode, purchaseLimit: Number(read("purchaseLimit")),
      startsAt: start.toISOString(), endsAt: end.toISOString(),
      saleStartsAt: saleStart.toISOString(), saleEndsAt: saleEnd.toISOString(),
      ticketTypes, images: [], promotions: [],
    });
  }

  const refresh = useMutation({
    mutationFn: async (id: string) => (await axiosClient.get<EventWithImages>(`/api/v1/organizer/events/${id}`)).data,
    onSuccess: update,
    onError: () => setError("Không tải lại được bản nháp."),
  });

  if (event) return (
    <section className="organizer-panel organizer-dashboard-card">
      <h2>{event.title}</h2>
      <p role="status">{event.status === "PENDING_APPROVAL" ? "Đã gửi duyệt. Sự kiện đang chờ Admin phê duyệt." : "Đã lưu bản nháp."}</p>
      {error && <p role="alert">{error}</p>}
      {event.status === "DRAFT" && <OrganizerDraftMedia event={event} onSaved={update} onBusy={setMediaBusy}
        disabled={submit.isPending || refresh.isPending} onRefresh={() => refresh.mutate(event.eventId)} />}
      <div className="organizer-form-actions">
        <Link className="btn btn-secondary" to="/organizer/events">Về danh sách sự kiện</Link>
        {event.status === "DRAFT" && <button type="button" className="btn btn-primary" disabled={submit.isPending || mediaBusy || refresh.isPending} onClick={() => {
          setError("");
          submit.mutate(event);
        }}>{submit.isPending ? "Đang gửi…" : "Gửi duyệt"}</button>}
      </div>
    </section>
  );

  return (
    <section className="organizer-panel organizer-dashboard-card">
      {options.isPending && <p role="status">Đang tải danh mục và địa điểm…</p>}
      {options.isError && <><p role="alert">Không tải được danh mục hoặc địa điểm.</p><button className="btn btn-secondary" onClick={() => void options.refetch()}>Thử lại</button></>}
      {options.data && <form className="organizer-event-form" onSubmit={save}>
        <fieldset disabled={create.isPending} className="organizer-form-grid organizer-create-fields">
          <legend>Thông tin sự kiện</legend>
          <label className="organizer-field">Chế độ bán vé<select className="organizer-input" value={mode} onChange={event => setMode(event.target.value as OrganizerSummary["mode"])}>
            <option value="QUANTITY">Theo số lượng</option>
            <option value="SEAT_MAP">Theo sơ đồ</option>
          </select></label>
          <label className="organizer-field organizer-form-span-2">Tên sự kiện<input className="organizer-input" name="title" required /></label>
          <label className="organizer-field organizer-form-span-2">Mô tả<textarea className="organizer-input organizer-textarea" name="description" rows={3} /></label>
          <label className="organizer-field">Danh mục<select className="organizer-input" name="categoryId"><option value="">Chọn danh mục</option>{options.data.categories.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label className="organizer-field">Địa điểm<select className="organizer-input" name="venueId" required defaultValue=""><option value="" disabled>Chọn địa điểm</option>{options.data.venues.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        </fieldset>
        <fieldset disabled={create.isPending} className="organizer-form-grid organizer-create-fields">
          <legend>Thời gian và bán vé</legend>
          {dateFields.map(([key, label]) => <label className="organizer-field" key={key}>{label}<input className="organizer-input" name={key} type="datetime-local" required /></label>)}
          <label className="organizer-field">Giới hạn vé mỗi người<input className="organizer-input" name="purchaseLimit" type="number" min={1} max={2147483647} step={1} required /></label>
        </fieldset>
        <p>Thời gian nhập theo múi giờ của thiết bị.</p>
        {mode === "QUANTITY" && <><h2>Loại vé</h2>
        {ticketIds.map((id, index) => (
          <fieldset key={id} disabled={create.isPending} className="events-v1-controls">
            <legend>Loại vé {index + 1}</legend>
            <label>Tên loại vé<input name={`name-${id}`} required /></label>
            <label>Giá vé (VND)<input name={`price-${id}`} inputMode="numeric" pattern="0|[1-9][0-9]*" required /></label>
            <label>Số lượng<input name={`quantity-${id}`} inputMode="numeric" pattern="[1-9][0-9]*" required /></label>
            <label>Màu vé<input name={`color-${id}`} type="color" defaultValue="#2563eb" /></label>
            {ticketIds.length > 1 && <button type="button" className="btn btn-secondary" onClick={() => setTicketIds(ids => ids.filter(value => value !== id))}>Bỏ loại vé</button>}
          </fieldset>
        ))}</>}
        {error && <p role="alert">{error}</p>}
        <div className="organizer-form-actions">
          {mode === "QUANTITY" && <button type="button" className="btn btn-secondary" disabled={create.isPending} onClick={() => setTicketIds(ids => [...ids, crypto.randomUUID()])}>Thêm loại vé</button>}
          <button type="submit" className="btn btn-primary" disabled={create.isPending || options.data.venues.length === 0}>{create.isPending ? "Đang lưu…" : "Lưu bản nháp"}</button>
        </div>
      </form>}
    </section>
  );
}

export default function OrganizerEventCreatePage() {
  const { ready } = useOrganizerGate();
  const { keycloak } = useKeycloak();
  const subject = keycloak.tokenParsed?.sub;
  return (
    <OrganizerLayout title="Tạo sự kiện" description="Lưu bản nháp trước khi gửi Admin duyệt." className="organizer-hub-page" hideTopBar>
      {ready && subject && <EventDraftForm key={`${keycloak.tokenParsed?.iss}:${subject}`} />}
    </OrganizerLayout>
  );
}
