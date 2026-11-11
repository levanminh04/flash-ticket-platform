import { FormEvent, useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import OrganizerLayout from "../../components/organizer/OrganizerLayout";
import axiosClient from "../../lib/axiosClient";
import { organizerDashboardService } from "../../services/organizerDashboardService";
import OrganizerDraftMedia, { EventWithImages } from "./OrganizerDraftMedia";
import { useOrganizerGate } from "./useOrganizerGate";

type SectorKind = "SEATED" | "STANDING";
type Seat = { seatId: string; seatLabel: string; rowName: string; seatNumber: string; coordX: number; coordY: number; ticketTypeId: string | null; isHidden: boolean };
type Shape = {
  shapeType: string;
  bounds: { x: number; y: number; width: number; height: number };
  points?: number[];
  cx?: number;
  cy?: number;
};
type Sector = {
  sectorId: string; name: string; code: string; kind: SectorKind; configuredCapacity: string;
  displayOrder: number; colorCode: string | null; geometry: { shape: Shape; editorVisible: boolean; editorLocked: boolean }; seats: Seat[];
};
type SeatMap = {
  schemaVersion: number; layoutId: string; name: string; canvasWidth: number; canvasHeight: number;
  backgroundImageReference: string | null; layoutData: { schemaVersion: number; decorations: object[] }; sectors: Sector[];
};
type SeatTicket = { ticketTypeId: string | null; sectorId: string; name: string; unitPrice: string; currency: string; colorCode: string | null };
type Owner = EventWithImages & { seatMap: SeatMap | null; ticketTypes: SeatTicket[] };

function SeatingForm({ eventId, subject, issuer }: { eventId: string; subject: string; issuer?: string }) {
  const client = useQueryClient();
  const navigate = useNavigate();
  const path = `/api/v1/organizer/events/${encodeURIComponent(eventId)}`;
  const key = ["organizer-seating", issuer, subject, eventId];
  const read = async (signal?: AbortSignal) => (await axiosClient.get<Owner>(path, { signal })).data;
  const query = useQuery({ queryKey: key, queryFn: ({ signal }) => read(signal), retry: false });
  const event = query.data;
  const map = event?.seatMap;
  const [params] = useSearchParams();
  const [step, setStep] = useState<"media" | "map" | "pricing" | "review">(
    params.get("step") === "pricing" ? "pricing" : params.get("step") === "review" ? "review" : params.get("step") === "media" ? "media" : "map",
  );
  const [mediaBusy, setMediaBusy] = useState(false);

  function update(saved: Owner) {
    client.setQueryData(key, saved);
    void client.invalidateQueries({ queryKey: ["organizer-event-list"] });
    void client.invalidateQueries({ queryKey: ["organizer-dashboard-events"] });
    void client.invalidateQueries({ queryKey: ["admin-event-review"] });
  }

  const pricing = useMutation({
    mutationFn: async (ticketTypes: SeatTicket[]) => {
      const { data } = await axiosClient.put<Owner>(`${path}/ticket-types`, { rowVersion: event!.rowVersion, ticketTypes });
      return data;
    },
    onSuccess: update,
    onError: () => void query.refetch(),
  });
  const submit = useMutation({
    mutationFn: () => organizerDashboardService.transition(eventId, "submit", event!.rowVersion),
    onSuccess: saved => {
      update({ ...event!, ...saved });
      navigate("/organizer/events", { replace: true });
    },
    onError: () => void query.refetch(),
  });
  const busy = mediaBusy || pricing.isPending || submit.isPending || query.isFetching;

  function savePricing(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (busy || !map || !event) return;
    const values = new FormData(e.currentTarget);
    const primary = map.sectors.map(sector => ({
      ticketTypeId: event.ticketTypes.find(type => type.sectorId === sector.sectorId)?.ticketTypeId ?? null,
      sectorId: sector.sectorId, name: String(values.get(`ticket-${sector.sectorId}`)).trim(),
      unitPrice: String(values.get(`price-${sector.sectorId}`)), currency: "VND", colorCode: sector.colorCode,
    }));
    const primaryIds = new Set(primary.map(ticket => ticket.ticketTypeId));
    const additional = event.ticketTypes.filter(ticket => !primaryIds.has(ticket.ticketTypeId))
      .map(({ ticketTypeId, sectorId, name, unitPrice, currency, colorCode }) =>
        ({ ticketTypeId, sectorId, name, unitPrice, currency, colorCode }));
    pricing.mutate([...primary, ...additional]);
  }

  const configured = Boolean(map?.sectors.length && map.sectors.every(sector => {
    const types = event?.ticketTypes.filter(ticket => ticket.sectorId === sector.sectorId) ?? [];
    return types.length > 0 && sector.seats.every(seat => seat.isHidden || types.some(ticket => ticket.ticketTypeId === seat.ticketTypeId));
  }));

  return <section className="organizer-panel organizer-dashboard-card">
    <Link to="/organizer/events">Về danh sách sự kiện</Link>
    {query.isPending && <p role="status">Đang tải bản nháp…</p>}
    {query.isError && <><p role="alert">Không tải được sự kiện.</p><button onClick={() => void query.refetch()}>Thử lại</button></>}
    {event && <h2>{event.title}</h2>}
    {event?.status === "PENDING_APPROVAL" && <p role="status">Đã gửi duyệt. Sự kiện đang chờ Admin phê duyệt.</p>}
    {event && event.status !== "PENDING_APPROVAL" && (event.mode !== "SEAT_MAP" || event.status !== "DRAFT") && <p>Chỉ cấu hình sơ đồ cho bản nháp có sơ đồ ghế.</p>}
    {event?.mode === "SEAT_MAP" && event.status === "DRAFT" && <>
      <nav className="seating-steps" aria-label="Các bước cấu hình">
        <button type="button" className={step === "media" ? "btn btn-primary" : "btn btn-secondary"} aria-current={step === "media" ? "step" : undefined} disabled={busy} onClick={() => setStep("media")}>Ảnh sự kiện</button>
        <button type="button" disabled={busy} className={step === "map" ? "btn btn-primary" : "btn btn-secondary"} aria-current={step === "map" ? "step" : undefined} onClick={() => setStep("map")}>Sơ đồ</button>
        <button type="button" className={step === "pricing" ? "btn btn-primary" : "btn btn-secondary"} aria-current={step === "pricing" ? "step" : undefined} disabled={!map || busy} onClick={() => setStep("pricing")}>Loại vé</button>
        <button type="button" className={step === "review" ? "btn btn-primary" : "btn btn-secondary"} aria-current={step === "review" ? "step" : undefined} disabled={!map || busy} onClick={() => setStep("review")}>Kiểm tra và gửi duyệt</button>
      </nav>
      {step === "media" && <OrganizerDraftMedia event={event} onSaved={update} onBusy={setMediaBusy}
        disabled={query.isFetching} onRefresh={() => void query.refetch()} />}
      {step === "map" && <section>
        <h3>Sơ đồ khu vực</h3>
        <p>{map?.sectors.length ? `Đã lưu ${map.sectors.length} khu.` : "Bản nháp chưa có khu nào."}</p>
        <div className="organizer-form-actions">
          <Link className="btn btn-primary" to={`/organizer/events/${eventId}/seat-map?phase=sectors`}>Mở trình vẽ khu vực</Link>
          {map && event.ticketTypes.length > 0 && <Link className="btn btn-secondary" to={`/organizer/events/${eventId}/seat-map`}>Chỉnh ghế và gán loại vé</Link>}
        </div>
      </section>}
      {map && <form hidden={step !== "pricing"} onSubmit={savePricing}>
        <h3>Đặt giá vé theo khu</h3>
        {map.sectors.map(sector => {
          const ticket = event.ticketTypes.find(type => type.sectorId === sector.sectorId);
          return <fieldset key={sector.sectorId} className="events-v1-controls" disabled={busy}>
            <legend>{sector.name} · {sector.kind === "SEATED" ? "Khu ngồi" : "Khu đứng"} · {sector.configuredCapacity} chỗ</legend>
            <label>Tên loại vé<input name={`ticket-${sector.sectorId}`} defaultValue={ticket?.name ?? sector.name} required pattern={".*\\S.*"} /></label>
            <label>Giá vé (VND)<input name={`price-${sector.sectorId}`} defaultValue={ticket?.unitPrice} inputMode="numeric" pattern="0|[1-9][0-9]*" required /></label>
          </fieldset>;
        })}
        <div className="organizer-form-actions">
          <button type="submit" className="btn btn-primary" disabled={busy || !map.sectors.length}>Lưu loại vé</button>
          {!busy && event.ticketTypes.length > 0 && <Link className="btn btn-secondary" to={`/organizer/events/${eventId}/seat-map`}>Chỉnh ghế và gán loại vé</Link>}
        </div>
      </form>}
      {map && step === "review" && <section aria-label="Kiểm tra trước khi gửi duyệt">
        <p role="status">{configured ? "Đã lưu sơ đồ và vé theo khu." : "Hãy lưu loại vé theo khu và gán vé cho các ghế hiển thị trước khi gửi duyệt."}</p>
        <button type="button" className="btn btn-primary" disabled={busy || !configured} onClick={() => submit.mutate()}>Gửi duyệt</button>
      </section>}
      {busy && <p role="status">Đang xử lý…</p>}
      {(pricing.isError || submit.isError) && <p role="alert">Chưa hoàn tất thao tác. Kiểm tra dữ liệu đã lưu rồi thử lại.</p>}
    </>}
  </section>;
}

export default function OrganizerEventSeatingPage() {
  const { eventId } = useParams();
  const { ready } = useOrganizerGate();
  const { keycloak } = useKeycloak();
  const subject = keycloak.tokenParsed?.sub;
  const issuer = keycloak.tokenParsed?.iss;
  return <OrganizerLayout title="Cấu hình sơ đồ và vé" description="Lưu sơ đồ, đặt giá vé theo khu rồi gửi Admin duyệt." className="organizer-hub-page organizer-seating-page" hideTopBar>
    {ready && eventId && subject && <SeatingForm key={`${issuer}:${subject}:${eventId}`} eventId={eventId} subject={subject} issuer={issuer} />}
  </OrganizerLayout>;
}
