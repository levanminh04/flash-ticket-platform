import { useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { Clock } from "lucide-react";
import OrganizerLayout from "../../components/organizer/OrganizerLayout";
import { organizerDashboardService, organizerEventStatuses, OrganizerSummary } from "../../services/organizerDashboardService";
import { useOrganizerGate } from "./useOrganizerGate";
import { confirmDestructiveAction } from "../../lib/swal";
import { toast } from "react-toastify";
import Swal from "sweetalert2";
import { cancellationService } from "../../services/cancellationService";

type Action = "submit" | "publish" | "cancel";

function EventList({ subject, issuer }: { subject: string; issuer?: string }) {
  const { t, i18n } = useTranslation();
  const { keycloak } = useKeycloak();
  const client = useQueryClient();
  const [status, setStatus] = useState<OrganizerSummary["status"] | "">("");
  const [search, setSearch] = useState("");
  const [message, setMessage] = useState<"success" | "error" | null>(null);
  const scope = ["organizer-event-list", issuer, subject];
  const events = useInfiniteQuery({
    queryKey: [...scope, status],
    initialPageParam: null as string | null,
    queryFn: ({ pageParam, signal }) => organizerDashboardService.events(pageParam, signal, status || undefined),
    getNextPageParam: page => page.nextCursor ?? undefined,
    retry: false,
  });
  const mutation = useMutation({
    mutationFn: ({ event, action }: { event: OrganizerSummary; action: Action }) =>
      organizerDashboardService.transition(event.eventId, action, event.rowVersion),
    onSuccess: async () => {
      setMessage("success");
      await Promise.all([
        client.invalidateQueries({ queryKey: scope }),
        client.invalidateQueries({ queryKey: ["organizer-dashboard-events", issuer, subject] }),
      ]);
    },
    onError: () => {
      setMessage("error");
      void client.invalidateQueries({ queryKey: scope });
    },
  });

  async function handleRequestCancellation(event: OrganizerSummary) {
    const result = await Swal.fire({
      title: t("organizerEvents.cancelRequestTitle", "Yêu cầu hủy sự kiện"),
      html: `<p style="color: #64748b; font-size: 14px; margin-bottom: 12px;">Sự kiện: <strong>${event.title}</strong><br/>Vì sự kiện đã mở bán vé, yêu cầu hủy cần được Quản trị viên phê duyệt trước khi tiến hành hoàn tiền cho khán giả.</p>`,
      input: "textarea",
      inputLabel: t("organizerEvents.cancelRequestReasonLabel", "Lý do hủy sự kiện (bắt buộc):"),
      inputPlaceholder: t("organizerEvents.cancelRequestPlaceholder", "Nhập lý do chi tiết (VD: Điều kiện thời tiết bất khả kháng, nghệ sĩ gặp sự cố sức khỏe...)..."),
      showCancelButton: true,
      confirmButtonText: t("organizerEvents.cancelRequestConfirm", "Gửi yêu cầu hủy"),
      cancelButtonText: t("adminOrganizer.rejectCancel", "Hủy bỏ"),
      confirmButtonColor: "#dc2626",
      inputValidator: (val) => {
        if (!val || !val.trim()) {
          return t("organizerEvents.cancelRequestReasonRequired", "Vui lòng nhập lý do hủy sự kiện!");
        }
        return null;
      },
    });

    if (result.isConfirmed && result.value) {
      const reason = result.value.trim();
      try {
        await cancellationService.requestCancellation(event.eventId, event.rowVersion, reason);
        toast.success(t("organizerEvents.cancelRequestSuccess", "Đã gửi yêu cầu hủy sự kiện tới Quản trị viên."));
        void client.invalidateQueries({ queryKey: ["organizer-cancellation-requests-pending"] });
        void client.invalidateQueries({ queryKey: scope });
      } catch (err: unknown) {
        const errCode = (err as { response?: { data?: { code?: string } } })?.response?.data?.code;
        if (errCode === "CANCELLATION_REQUEST_PENDING") {
          toast.info(t("organizerEvents.alreadyPending", "Sự kiện này đã có yêu cầu hủy đang chờ Quản trị viên phê duyệt."));
          void client.invalidateQueries({ queryKey: ["organizer-cancellation-requests-pending"] });
          return;
        }
        if (errCode === "DIRECT_CANCELLATION_AVAILABLE") {
          const directConfirm = await Swal.fire({
            title: t("organizerEvents.directCancelTitle", "Sự kiện chưa có người mua vé"),
            text: t("organizerEvents.directCancelText", "Sự kiện này chưa có khán giả nào mua vé nên không cần gửi Quản trị viên duyệt hoàn tiền. Bạn có muốn hủy trực tiếp sự kiện ngay bây giờ không?"),
            icon: "question",
            showCancelButton: true,
            confirmButtonText: t("eventsV1.cancel", "Hủy sự kiện ngay"),
            cancelButtonText: t("adminOrganizer.rejectCancel", "Đóng"),
            confirmButtonColor: "#dc2626",
          });
          if (directConfirm.isConfirmed) {
            mutation.mutate({ event, action: "cancel" });
          }
          return;
        }
        const msg =
          (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
          t("organizerEvents.cancelRequestFailed", "Không thể gửi yêu cầu hủy. Vui lòng thử lại.");
        toast.error(msg);
      }
    }
  }
  const rows = events.data?.pages.flatMap(page => page.items) ?? [];
  const visible = rows.filter(event => event.title.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()));

  const pendingRequestsQuery = useQuery({
    queryKey: ["organizer-cancellation-requests-pending", rows.map((e) => e.eventId).join(",")],
    queryFn: async () => {
      const publishedEvents = rows.filter((e) => e.status === "PUBLISHED");
      const results = await Promise.allSettled(
        publishedEvents.map((e) => cancellationService.getOrganizerCancellationHistory(e.eventId, 5)),
      );
      const pendingSet = new Set<string>();
      results.forEach((res, index) => {
        if (res.status === "fulfilled") {
          const hasPending = res.value.items?.some((req) => req.status === "PENDING");
          if (hasPending) {
            pendingSet.add(publishedEvents[index].eventId);
          }
        }
      });
      return pendingSet;
    },
    enabled: rows.some((e) => e.status === "PUBLISHED"),
    staleTime: 10000,
  });

  const pendingCancellationEventIds = pendingRequestsQuery.data ?? new Set<string>();

  async function act(event: OrganizerSummary, action: Action) {
    if (mutation.isPending) return;
    if (action === "cancel") {
      if (Date.parse(event.startsAt) <= Date.now()) return;
      const confirmed = await confirmDestructiveAction({
        title: t("eventsV1.cancel"),
        text: t("eventsV1.cancelConfirm", { title: event.title }),
        confirmButtonText: t("eventsV1.cancel"),
        cancelButtonText: t("adminOrganizer.rejectCancel"),
      });
      if (!confirmed) return;
    }
    if (!keycloak.authenticated || keycloak.tokenParsed?.sub !== subject) return;
    setMessage(null);
    mutation.mutate({ event, action });
  }

  return (
    <section className="organizer-panel organizer-dashboard-card">
      <div className="events-v1-controls">
        <label>
          <span>{t("adminOrganizer.status")}</span>
          <select value={status} disabled={mutation.isPending} onChange={event => {
            setStatus(event.target.value as typeof status);
            setMessage(null);
          }}>
            <option value="">{t("eventsV1.all")}</option>
            {organizerEventStatuses.map(value => <option key={value} value={value}>{t(`dashboardV1.status.${value}`)}</option>)}
          </select>
        </label>
        <label>
          <span>{t("eventsV1.search")}</span>
          <input value={search} onChange={event => setSearch(event.target.value)} type="search" />
        </label>
        <button type="button" className="btn btn-secondary" disabled={events.isFetching || mutation.isPending} onClick={() => void events.refetch()}>{t("paymentV1.refresh")}</button>
      </div>
      {message && <p role={message === "error" ? "alert" : "status"}>{t(`eventsV1.${message}`)}</p>}
      {events.isPending && <p role="status">{t("organizerEvents.listLoading")}</p>}
      {events.isError && <><p role="alert">{t("organizerEvents.loadFailed")}</p><button type="button" className="btn btn-secondary" disabled={events.isFetching} onClick={() => void (events.isFetchNextPageError ? events.fetchNextPage() : events.refetch())}>{t("profile.retry")}</button></>}
      {events.data && <>
        <p>{t(events.hasNextPage ? "dashboardV1.loadedCount" : "dashboardV1.totalCount", { count: rows.length })}</p>
        {visible.length === 0 ? <p>{t("eventsV1.empty")}</p> : <div className="dashboard-table-wrap"><table className="dashboard-events-table">
          <thead><tr>
            <th scope="col">{t("dashboardV1.event")}</th>
            <th scope="col">{t("adminOrganizer.status")}</th>
            <th scope="col">{t("eventsV1.schedule")}</th>
            <th scope="col">{t("eventsV1.mode")}</th>
            <th scope="col">{t("adminOrganizer.tableActions")}</th>
          </tr></thead>
          <tbody>{visible.map(event => <tr key={event.eventId}>
            <td>{event.title}</td>
            <td>{t(`dashboardV1.status.${event.status}`)}</td>
            <td><time dateTime={event.startsAt}>{new Date(event.startsAt).toLocaleString(i18n.resolvedLanguage)}</time><br /><time dateTime={event.endsAt}>{new Date(event.endsAt).toLocaleString(i18n.resolvedLanguage)}</time></td>
            <td>{t(`eventsV1.${event.mode}`)}</td>
            <td><div className="events-v1-actions">
              {event.status === "PUBLISHED" && (
                <>
                  <Link className="btn btn-secondary" to={`/event/${event.eventId}`}>{t("organizerEvents.viewPublic")}</Link>
                  {pendingCancellationEventIds.has(event.eventId) ? (
                    <span
                      style={{
                        display: "inline-flex",
                        alignItems: "center",
                        gap: 6,
                        padding: "6px 12px",
                        borderRadius: 6,
                        fontSize: 13,
                        fontWeight: 600,
                        background: "#fef3c7",
                        color: "#92400e",
                        border: "1px solid #fde68a",
                      }}
                    >
                      <Clock size={14} />
                      {t("organizerEvents.cancellationPending", "Đang chờ Admin duyệt hủy")}
                    </span>
                  ) : Date.parse(event.startsAt) > Date.now() ? (
                    <button
                      type="button"
                      className="btn btn-secondary"
                      style={{ color: "#dc2626", borderColor: "#fca5a5" }}
                      disabled={mutation.isPending || events.isFetching}
                      onClick={() => void handleRequestCancellation(event)}
                    >
                      {t("organizerEvents.requestCancel", "Yêu cầu hủy sự kiện")}
                    </button>
                  ) : null}
                </>
              )}
              {event.status === "DRAFT" && event.mode === "SEAT_MAP" && <Link className="btn btn-primary" to={`/organizer/events/${event.eventId}/configure-seating`}>Cấu hình sơ đồ</Link>}
              {event.status === "DRAFT" && event.mode === "QUANTITY" && <button className="btn btn-primary" disabled={mutation.isPending || events.isFetching} onClick={() => void act(event, "submit")}>{t("eventsV1.submit")}</button>}
              {event.status === "APPROVED" && <button className="btn btn-primary" disabled={mutation.isPending || events.isFetching} onClick={() => void act(event, "publish")}>{t("eventsV1.publish")}</button>}
              {["DRAFT", "PENDING_APPROVAL", "APPROVED"].includes(event.status) && Date.parse(event.startsAt) > Date.now() && <button className="btn btn-secondary" disabled={mutation.isPending || events.isFetching} onClick={() => void act(event, "cancel")}>{t("eventsV1.cancel")}</button>}
            </div></td>
          </tr>)}</tbody>
        </table></div>}
        {events.hasNextPage && !events.isFetchNextPageError && <button type="button" className="btn btn-secondary" disabled={events.isFetching || mutation.isPending} onClick={() => void events.fetchNextPage()}>{t("adminOrganizer.loadMore")}</button>}
      </>}
    </section>
  );
}

export default function OrganizerEventsPage() {
  const { t } = useTranslation();
  const { ready } = useOrganizerGate();
  const { keycloak } = useKeycloak();
  const subject = keycloak.tokenParsed?.sub;
  return (
    <OrganizerLayout title={t("organizer.eventManagement")} description={t("eventsV1.description")} className="organizer-hub-page organizer-events-page" hideTopBar
      actions={<Link className="btn btn-primary" to="/organizer/events/new">{t("organizerHub.createEvent")}</Link>}>
      {ready && subject && <EventList key={`${keycloak.tokenParsed?.iss}:${subject}`} subject={subject} issuer={keycloak.tokenParsed?.iss} />}
    </OrganizerLayout>
  );
}
