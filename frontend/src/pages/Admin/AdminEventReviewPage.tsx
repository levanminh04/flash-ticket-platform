import { useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { toast } from "react-toastify";
import Swal from "sweetalert2";
import axiosClient from "../../lib/axiosClient";
import { hasRealmRole } from "../../lib/auth";
import { OrganizerSummary } from "../../services/organizerDashboardService";
import { cancellationService, CancellationRequest } from "../../services/cancellationService";

interface EventDetail extends OrganizerSummary {
  description: string;
  ticketTypes: { ticketTypeId: string; name: string; unitPrice: string; currency: string }[];
}

const path = "/api/v1/admin/events";

function ReviewQueue({ subject, issuer }: { subject: string; issuer?: string }) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const [selected, setSelected] = useState("");
  const [fee, setFee] = useState("");
  const scope = ["admin-event-review", issuer, subject];
  const queue = useInfiniteQuery({
    queryKey: [...scope, "queue"],
    initialPageParam: null as string | null,
    queryFn: async ({ pageParam, signal }) =>
      (
        await axiosClient.get<{ items: OrganizerSummary[]; nextCursor: string | null }>(path, {
          params: { status: "PENDING_APPROVAL", size: 20, cursor: pageParam ?? undefined },
          signal,
        })
      ).data,
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    retry: false,
  });
  const detail = useQuery({
    queryKey: [...scope, "detail", selected],
    enabled: Boolean(selected),
    queryFn: async ({ signal }) =>
      (await axiosClient.get<EventDetail>(`${path}/${selected}`, { signal })).data,
    retry: false,
  });
  const decision = useMutation({
    mutationFn: ({
      event,
      action,
    }: {
      event: EventDetail;
      action: "approve" | "return-to-draft";
    }) =>
      axiosClient.post(`${path}/${event.eventId}/${action}`, {
        rowVersion: event.rowVersion,
        ...(action === "approve" ? { feeRate: fee.trim() } : {}),
      }),
    onSuccess: async () => {
      setSelected("");
      setFee("");
      await client.invalidateQueries({ queryKey: scope });
    },
    onError: () => client.invalidateQueries({ queryKey: scope }),
  });
  const event = detail.data;
  const validFee = /^(?:0(?:\.[0-9]{1,8})?|1(?:\.0{1,8})?)$/.test(fee.trim());

  return (
    <div>
      {decision.isSuccess && <p role="status">{t("eventsV1.success")}</p>}
      {decision.isError && <p role="alert">{t("eventsV1.error")}</p>}
      {queue.isPending && <p role="status">{t("organizerEvents.listLoading")}</p>}
      {queue.isError && (
        <>
          <p role="alert">{t("organizerEvents.loadFailed")}</p>
          <button
            className="btn btn-secondary"
            onClick={() =>
              void (queue.isFetchNextPageError ? queue.fetchNextPage() : queue.refetch())
            }
          >
            {t("profile.retry")}
          </button>
        </>
      )}
      {queue.data && (
        <div className="admin-organizer-table-wrap">
          <table className="admin-organizer-table">
            <thead>
              <tr>
                <th scope="col">{t("dashboardV1.event")}</th>
                <th scope="col">{t("dashboardV1.startsAt")}</th>
                <th scope="col">{t("adminOrganizer.tableActions")}</th>
              </tr>
            </thead>
            <tbody>
              {queue.data.pages
                .flatMap((page) => page.items)
                .map((item) => (
                  <tr key={item.eventId}>
                    <td>{item.title}</td>
                    <td>{new Date(item.startsAt).toLocaleString()}</td>
                    <td>
                      <button
                        className="btn btn-secondary"
                        disabled={decision.isPending}
                        onClick={() => {
                          decision.reset();
                          setFee("");
                          setSelected(item.eventId);
                        }}
                      >
                        {t("adminEventReview.details")}
                      </button>
                    </td>
                  </tr>
                ))}
              {queue.data.pages.every((page) => page.items.length === 0) && (
                <tr>
                  <td colSpan={3}>{t("dashboardV1.empty")}</td>
                </tr>
              )}
            </tbody>
          </table>
          {queue.hasNextPage && !queue.isFetchNextPageError && (
            <button
              className="btn btn-secondary"
              disabled={queue.isFetching}
              onClick={() => void queue.fetchNextPage()}
            >
              {t("adminOrganizer.loadMore")}
            </button>
          )}
        </div>
      )}
      {selected && detail.isPending && <p role="status">{t("organizerEvents.listLoading")}</p>}
      {detail.isError && (
        <>
          <p role="alert">{t("organizerEvents.loadFailed")}</p>
          <button className="btn btn-secondary" onClick={() => void detail.refetch()}>
            {t("profile.retry")}
          </button>
        </>
      )}
      {event && (
        <section className="organizer-panel organizer-dashboard-card" style={{ marginTop: 24 }}>
          <h2>{event.title}</h2>
          <p>{t(`dashboardV1.status.${event.status}`)}</p>
          <p>{event.description}</p>
          <ul>
            {event.ticketTypes.map((ticket) => (
              <li key={ticket.ticketTypeId}>
                {ticket.name} · {ticket.unitPrice} {ticket.currency}
              </li>
            ))}
          </ul>
          {event.status === "PENDING_APPROVAL" && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (validFee && !decision.isPending && !detail.isFetching)
                  decision.mutate({ event, action: "approve" });
              }}
            >
              <label>
                {t("adminEventReview.fee")}{" "}
                <input
                  required
                  inputMode="decimal"
                  value={fee}
                  onChange={(e) => setFee(e.target.value)}
                  disabled={decision.isPending}
                />
              </label>
              <div className="admin-organizer-actions">
                <button
                  className="btn btn-primary"
                  type="submit"
                  disabled={!validFee || decision.isPending || detail.isFetching}
                >
                  {t("adminOrganizer.approve")}
                </button>
                <button
                  className="btn btn-secondary"
                  type="button"
                  disabled={decision.isPending || detail.isFetching}
                  onClick={() => decision.mutate({ event, action: "return-to-draft" })}
                >
                  {t("adminEventReview.returnToDraft")}
                </button>
              </div>
            </form>
          )}
        </section>
      )}
    </div>
  );
}

function CancellationRequestsQueue({ subject, issuer }: { subject: string; issuer?: string }) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const scope = ["admin-cancellation-requests", issuer, subject];

  const requestsQuery = useQuery({
    queryKey: scope,
    queryFn: ({ signal }) =>
      cancellationService.getAdminPendingCancellationRequests("PENDING", 50, undefined, signal),
  });

  const approveMutation = useMutation({
    mutationFn: ({ requestId, rowVersion }: { requestId: string; rowVersion: string }) =>
      cancellationService.approveCancellationRequest(requestId, rowVersion),
    onSuccess: () => {
      toast.success(
        t(
          "adminEventReview.approveCancelSuccess",
          "Đã duyệt hủy sự kiện thành công. Tiến trình hoàn tiền tự động đã được khởi tạo.",
        ),
      );
      return client.invalidateQueries({ queryKey: scope });
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        t("adminEventReview.approveCancelFailed", "Duyệt hủy sự kiện thất bại.");
      toast.error(msg);
    },
  });

  const rejectMutation = useMutation({
    mutationFn: ({
      requestId,
      rowVersion,
      reason,
    }: {
      requestId: string;
      rowVersion: string;
      reason?: string;
    }) => cancellationService.rejectCancellationRequest(requestId, rowVersion, reason),
    onSuccess: () => {
      toast.success(
        t("adminEventReview.rejectCancelSuccess", "Đã từ chối yêu cầu hủy sự kiện."),
      );
      return client.invalidateQueries({ queryKey: scope });
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        t("adminEventReview.rejectCancelFailed", "Từ chối yêu cầu hủy thất bại.");
      toast.error(msg);
    },
  });

  async function handleApprove(req: CancellationRequest) {
    const result = await Swal.fire({
      title: t("adminEventReview.approveCancelConfirmTitle", "Xác nhận duyệt hủy sự kiện?"),
      html: `
        <div style="color: #475569; font-size: 14px; text-align: left; line-height: 1.6;">
          <p><strong>Mã sự kiện:</strong> <code>${req.eventId}</code></p>
          <p><strong>Lý do BTC xin hủy:</strong> <em>"${req.reason}"</em></p>
          <p style="color: #dc2626; font-weight: 600; margin-top: 10px;">
            ⚠️ Khi duyệt hủy, sự kiện sẽ dừng hoạt động và hệ thống sẽ tự động quét, tạo lệnh hoàn tiền cho toàn bộ khán giả đã mua vé!
          </p>
        </div>
      `,
      showCancelButton: true,
      confirmButtonText: t("adminEventReview.confirmApproveCancel", "Duyệt hủy sự kiện"),
      cancelButtonText: t("adminOrganizer.rejectCancel", "Hủy bỏ"),
      confirmButtonColor: "#dc2626",
    });

    if (result.isConfirmed) {
      approveMutation.mutate({
        requestId: req.requestId,
        rowVersion: req.eventRowVersion,
      });
    }
  }

  async function handleReject(req: CancellationRequest) {
    const result = await Swal.fire({
      title: t("adminEventReview.rejectCancelConfirmTitle", "Từ chối yêu cầu hủy sự kiện"),
      input: "textarea",
      inputLabel: t("adminEventReview.rejectReasonLabel", "Lý do từ chối (bắt buộc):"),
      inputPlaceholder: "Nhập lý do từ chối gửi tới Ban tổ chức...",
      showCancelButton: true,
      confirmButtonText: t("adminEventReview.confirmRejectCancel", "Từ chối yêu cầu"),
      cancelButtonText: t("adminOrganizer.rejectCancel", "Hủy bỏ"),
      confirmButtonColor: "#64748b",
      inputValidator: (val) => {
        if (!val || !val.trim()) {
          return t("adminEventReview.rejectReasonRequired", "Vui lòng nhập lý do từ chối!");
        }
        return null;
      },
    });

    if (result.isConfirmed && result.value) {
      rejectMutation.mutate({
        requestId: req.requestId,
        rowVersion: req.eventRowVersion,
        reason: result.value.trim(),
      });
    }
  }

  const items = requestsQuery.data?.items ?? [];

  return (
    <div style={{ marginTop: 20 }}>
      {requestsQuery.isPending && <p role="status">{t("organizerEvents.listLoading")}</p>}
      {requestsQuery.isError && (
        <>
          <p role="alert">{t("organizerEvents.loadFailed")}</p>
          <button className="btn btn-secondary" onClick={() => void requestsQuery.refetch()}>
            {t("profile.retry")}
          </button>
        </>
      )}
      {requestsQuery.data && (
        <div className="admin-organizer-table-wrap">
          <table className="admin-organizer-table">
            <thead>
              <tr>
                <th scope="col">Mã sự kiện</th>
                <th scope="col">Người gửi</th>
                <th scope="col">Thời gian gửi</th>
                <th scope="col">Lý do hủy</th>
                <th scope="col">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {items.map((req) => (
                <tr key={req.requestId}>
                  <td>
                    <code style={{ fontSize: 13 }}>{req.eventId}</code>
                  </td>
                  <td>{req.requestedBy.substring(0, 8)}...</td>
                  <td>{new Date(req.requestedAt).toLocaleString()}</td>
                  <td style={{ maxWidth: 300, overflowWrap: "break-word" }}>{req.reason}</td>
                  <td>
                    <div style={{ display: "flex", gap: 8 }}>
                      <button
                        type="button"
                        className="btn btn-primary"
                        style={{ background: "#dc2626", borderColor: "#dc2626" }}
                        disabled={approveMutation.isPending || rejectMutation.isPending}
                        onClick={() => void handleApprove(req)}
                      >
                        Duyệt hủy
                      </button>
                      <button
                        type="button"
                        className="btn btn-secondary"
                        disabled={approveMutation.isPending || rejectMutation.isPending}
                        onClick={() => void handleReject(req)}
                      >
                        Từ chối
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {items.length === 0 && (
                <tr>
                  <td colSpan={5} style={{ textAlign: "center", color: "#64748b", padding: 32 }}>
                    Không có yêu cầu hủy sự kiện nào đang chờ duyệt.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default function AdminEventReviewPage() {
  const { t } = useTranslation();
  const { keycloak, initialized } = useKeycloak();
  const [tab, setTab] = useState<"NEW_EVENTS" | "CANCELLATIONS">("NEW_EVENTS");
  const subject = keycloak.tokenParsed?.sub;
  const issuer = keycloak.tokenParsed?.iss;

  return (
    <main className="admin-organizer-page">
      {initialized &&
      keycloak.authenticated &&
      subject &&
      hasRealmRole(keycloak.tokenParsed, "ADMIN") ? (
        <section className="container admin-organizer-wrap">
          <div className="admin-organizer-header" style={{ marginBottom: 16 }}>
            <div>
              <h1>{t("adminEventReview.title", "Duyệt sự kiện")}</h1>
              <p style={{ color: "#64748b" }}>
                Phê duyệt sự kiện mới mở bán hoặc xử lý yêu cầu hủy sự kiện từ Ban tổ chức.
              </p>
            </div>
          </div>

          {/* Navigation Tabs */}
          <div
            style={{
              display: "flex",
              gap: 12,
              marginBottom: 24,
              borderBottom: "2px solid #e2e8f0",
              paddingBottom: 2,
            }}
          >
            <button
              type="button"
              className={`refund-tab-btn ${tab === "NEW_EVENTS" ? "active" : ""}`}
              onClick={() => setTab("NEW_EVENTS")}
              style={{ padding: "8px 16px", cursor: "pointer", fontWeight: tab === "NEW_EVENTS" ? 700 : 500 }}
            >
              Duyệt sự kiện mới
            </button>
            <button
              type="button"
              className={`refund-tab-btn ${tab === "CANCELLATIONS" ? "active" : ""}`}
              onClick={() => setTab("CANCELLATIONS")}
              style={{ padding: "8px 16px", cursor: "pointer", fontWeight: tab === "CANCELLATIONS" ? 700 : 500 }}
            >
              Yêu cầu hủy sự kiện
            </button>
          </div>

          {tab === "NEW_EVENTS" ? (
            <ReviewQueue key={`${issuer}:${subject}`} subject={subject} issuer={issuer} />
          ) : (
            <CancellationRequestsQueue
              key={`cancel:${issuer}:${subject}`}
              subject={subject}
              issuer={issuer}
            />
          )}
        </section>
      ) : (
        <section className="admin-organizer-state">
          <p role="status">
            {t(
              initialized
                ? "adminOrganizer.noAccessDescription"
                : "organizerEvents.listLoading",
            )}
          </p>
        </section>
      )}
    </main>
  );
}
