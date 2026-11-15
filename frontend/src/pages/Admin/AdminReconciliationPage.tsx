import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import {
  AlertTriangle,
  Banknote,
  Building2,
  CalendarClock,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  Copy,
  FileCheck2,
  LoaderCircle,
  RefreshCw,
  Scale,
  Search,
  ShieldAlert,
  Wallet,
  X,
} from "lucide-react";
import { toast } from "react-toastify";
import Swal from "sweetalert2";

import TixAdminTopNav from "../../components/admin/TixAdminTopNav";
import { hasRealmRole } from "../../lib/auth";
import axiosClient from "../../lib/axiosClient";
import {
  adminFinanceService,
  extractPaymentError,
  type EventReconciliationView,
} from "../../services/adminFinanceService";
import { organizerService } from "../../services/organizerService";
import "../Organizer/organizer-dashboard.css";
import "./admin-tix.css";

interface AdminEventListItem {
  eventId: string;
  title: string;
  status: string;
  mode?: string | null;
  startsAt?: string | null;
  endsAt?: string | null;
  rowVersion: string;
}

interface AdminEventDetailMeta extends AdminEventListItem {
  organizerSubject?: string | null;
  feeRate?: string | null;
  approvedAt?: string | null;
}

type QueueStatus = "ONGOING" | "ENDED" | "APPROVED" | "CANCELLED";

const QUEUE_STATUS_TABS: ReadonlyArray<{
  key: QueueStatus;
  apiStatus: "PUBLISHED" | "APPROVED" | "CANCELLED";
  vi: string;
  en: string;
}> = [
  { key: "ONGOING", apiStatus: "PUBLISHED", vi: "Đã mở bán", en: "On Sale" },
  { key: "ENDED", apiStatus: "PUBLISHED", vi: "Đã kết thúc", en: "Ended" },
  { key: "APPROVED", apiStatus: "APPROVED", vi: "Đã duyệt", en: "Approved" },
  { key: "CANCELLED", apiStatus: "CANCELLED", vi: "Đã hủy", en: "Cancelled" },
];

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const LIST_PAGE_SIZE = 5;

function isEventEnded(endsAt: string | null | undefined): boolean {
  if (!endsAt) return false;
  const endsMs = Date.parse(endsAt);
  return Number.isFinite(endsMs) && Date.now() >= endsMs;
}

function formatMoney(
  value: string | number | null | undefined,
  currency = "VND",
  locale = "vi-VN",
): string {
  if (value === null || value === undefined || value === "") return "—";
  const num = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(num)) return `${value} ${currency}`;
  const formatted = new Intl.NumberFormat(locale, { maximumFractionDigits: 0 }).format(num);
  return currency.toUpperCase() === "VND" ? `${formatted} ₫` : `${formatted} ${currency}`;
}

function formatDateTime(value: string | null | undefined, locale = "vi-VN"): string {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString(locale, {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatFeePercent(feeRate: string | null | undefined): string {
  if (!feeRate) return "—";
  const num = Number(feeRate);
  if (!Number.isFinite(num)) return feeRate;
  return `${(num * 100).toFixed(2).replace(/\.?0+$/, "")}%`;
}

function shortId(value: string | null | undefined): string {
  if (!value) return "—";
  return value.length > 16 ? `${value.slice(0, 8)}…${value.slice(-4)}` : value;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function getReconciliationBlockers(
  report: EventReconciliationView,
  isVi: boolean,
): string[] {
  const blockers: string[] = [];
  if (report.paidAt) {
    blockers.push(
      isVi ? "Sự kiện đã được đánh dấu chi trả hoàn tất." : "Payout has already been marked paid.",
    );
    return blockers;
  }
  if (report.cancelled) {
    blockers.push(
      isVi ? "Sự kiện đang ở trạng thái Đã hủy." : "Event is cancelled.",
    );
  }
  const refNow = Date.parse(report.asOf || new Date().toISOString());
  const endsAtMs = Date.parse(report.eventEndsAt);
  if (Number.isFinite(endsAtMs) && Number.isFinite(refNow) && refNow < endsAtMs) {
    blockers.push(isVi ? "Sự kiện chưa kết thúc" : "Event has not ended yet");
  }
  if (report.paymentPendingCount > 0) {
    blockers.push(
      isVi
        ? `Còn ${report.paymentPendingCount} giao dịch thanh toán đang chờ hoàn tất.`
        : `${report.paymentPendingCount} payment attempt(s) still pending.`,
    );
  }
  if (report.refundPendingCount > 0) {
    blockers.push(
      isVi
        ? `Còn ${report.refundPendingCount} khoản hoàn tiền chưa kết thúc.`
        : `${report.refundPendingCount} refund(s) still pending.`,
    );
  }
  return blockers;
}

export function AdminReconciliationPage() {
  const { t, i18n } = useTranslation();
  const { keycloak, initialized } = useKeycloak();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const { eventId: routeEventId } = useParams<{ eventId?: string }>();
  const [searchParams, setSearchParams] = useSearchParams();

  const isVi = !i18n.resolvedLanguage?.startsWith("en");
  const locale = isVi ? "vi-VN" : "en-US";
  const copyText = (vi: string, en: string) => (isVi ? vi : en);

  const initialEventId = useMemo(() => {
    const candidate = routeEventId || searchParams.get("eventId") || "";
    return UUID_REGEX.test(candidate.trim()) ? candidate.trim() : null;
  }, [routeEventId, searchParams]);

  const [queueStatus, setQueueStatus] = useState<QueueStatus>("ONGOING");
  const activeQueueTab =
    QUEUE_STATUS_TABS.find((tab) => tab.key === queueStatus) ?? QUEUE_STATUS_TABS[0];
  const [searchTerm, setSearchTerm] = useState("");
  const [listPage, setListPage] = useState(0);
  const [directLookupInput, setDirectLookupInput] = useState("");
  const [selectedEventId, setSelectedEventId] = useState<string | null>(initialEventId);
  const [evidence, setEvidence] = useState("");
  const [staleWarning, setStaleWarning] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  useEffect(() => {
    if (initialEventId && initialEventId !== selectedEventId) {
      setSelectedEventId(initialEventId);
    }
  }, [initialEventId]);

  useEffect(() => {
    if (!copiedId) return;
    const timer = window.setTimeout(() => setCopiedId(null), 1500);
    return () => window.clearTimeout(timer);
  }, [copiedId]);

  const isAuthenticated = Boolean(keycloak.authenticated);
  const isAdmin = hasRealmRole(keycloak.tokenParsed, "ADMIN");
  const canQuery = initialized && isAuthenticated && isAdmin;

  const eventsListQuery = useInfiniteQuery({
    queryKey: ["admin-reconciliation-events", activeQueueTab.apiStatus],
    enabled: canQuery,
    initialPageParam: undefined as string | undefined,
    queryFn: async ({ pageParam, signal }) =>
      (
        await axiosClient.get<{
          items: AdminEventListItem[];
          nextCursor: string | null;
        }>("/api/v1/admin/events", {
          params: {
            status: activeQueueTab.apiStatus,
            size: activeQueueTab.apiStatus === "PUBLISHED" ? 50 : 20,
            cursor: pageParam,
          },
          signal,
        })
      ).data,
    getNextPageParam: (page, _, __, cursors) =>
      page.nextCursor && !cursors.includes(page.nextCursor) ? page.nextCursor : undefined,
  });

  const allQueueItems = useMemo(
    () => [
      ...new Map(
        (eventsListQuery.data?.pages.flatMap((p) => p.items) ?? []).map((item) => [
          item.eventId,
          item,
        ]),
      ).values(),
    ],
    [eventsListQuery.data],
  );

  const statusFilteredItems = useMemo(() => {
    if (queueStatus === "ONGOING") {
      return allQueueItems.filter((item) => !isEventEnded(item.endsAt));
    }
    if (queueStatus === "ENDED") {
      return allQueueItems.filter((item) => isEventEnded(item.endsAt));
    }
    return allQueueItems;
  }, [allQueueItems, queueStatus]);

  const normalizedSearch = searchTerm.trim().toLowerCase();
  const filteredQueueItems = useMemo(() => {
    if (!normalizedSearch) return statusFilteredItems;
    return statusFilteredItems.filter(
      (item) =>
        item.title.toLowerCase().includes(normalizedSearch) ||
        item.eventId.toLowerCase().includes(normalizedSearch),
    );
  }, [statusFilteredItems, normalizedSearch]);

  useEffect(() => {
    setListPage(0);
  }, [queueStatus, normalizedSearch]);

  const totalListPages = Math.max(1, Math.ceil(filteredQueueItems.length / LIST_PAGE_SIZE));
  const safeListPage = Math.min(listPage, totalListPages - 1);
  const paginatedQueueItems = useMemo(
    () =>
      filteredQueueItems.slice(
        safeListPage * LIST_PAGE_SIZE,
        (safeListPage + 1) * LIST_PAGE_SIZE,
      ),
    [filteredQueueItems, safeListPage],
  );

  const selectEvent = (id: string) => {
    setSelectedEventId(id);
    setStaleWarning(null);
    setActionError(null);
    setEvidence("");
    if (routeEventId && routeEventId !== id) {
      navigate(`/admin/reconciliation?eventId=${encodeURIComponent(id)}`, { replace: true });
    } else if (searchParams.get("eventId") !== id) {
      const nextParams = new URLSearchParams(searchParams);
      nextParams.set("eventId", id);
      setSearchParams(nextParams, { replace: true });
    }
  };

  const handleQueueStatusChange = (nextStatus: QueueStatus) => {
    setQueueStatus(nextStatus);
    setSelectedEventId(null);
    setStaleWarning(null);
    setActionError(null);
    setEvidence("");
    if (routeEventId) {
      navigate("/admin/reconciliation", { replace: true });
    } else if (searchParams.has("eventId")) {
      const nextParams = new URLSearchParams(searchParams);
      nextParams.delete("eventId");
      setSearchParams(nextParams, { replace: true });
    }
  };

  const eventDetailQuery = useQuery({
    queryKey: ["admin-reconciliation-event-meta", selectedEventId],
    enabled: canQuery && Boolean(selectedEventId),
    retry: false,
    queryFn: async ({ signal }) =>
      (
        await axiosClient.get<AdminEventDetailMeta>(`/api/v1/admin/events/${selectedEventId}`, {
          signal,
        })
      ).data,
  });

  const reconciliationQuery = useQuery({
    queryKey: ["admin-event-reconciliation", selectedEventId],
    enabled: canQuery && Boolean(selectedEventId),
    retry: false,
    queryFn: ({ signal }) => adminFinanceService.getReconciliation(selectedEventId!, signal),
  });

  const organizerSubject =
    reconciliationQuery.data?.organizerSubject || eventDetailQuery.data?.organizerSubject;

  const organizerQuery = useQuery({
    queryKey: ["admin-reconciliation-organizer", organizerSubject],
    enabled: canQuery && Boolean(organizerSubject),
    retry: false,
    staleTime: 5 * 60 * 1000,
    queryFn: ({ signal }) => organizerService.getPublicOrganizerBySlug(organizerSubject!, signal),
  });

  useEffect(() => {
    if (reconciliationQuery.data?.reconciliationEvidence && !evidence) {
      setEvidence(reconciliationQuery.data.reconciliationEvidence);
    }
  }, [reconciliationQuery.data?.eventId, reconciliationQuery.data?.reconciliationEvidence]);

  const confirmMutation = useMutation({
    mutationFn: ({ eventId, note }: { eventId: string; note: string }) =>
      adminFinanceService.confirmReconciliation(eventId, note),
    onSuccess: (updated) => {
      setStaleWarning(null);
      setActionError(null);
      qc.setQueryData(["admin-event-reconciliation", updated.eventId], updated);
      toast.success(
        copyText(
          "Đã xác nhận đối soát sự kiện thành công.",
          "Event reconciliation confirmed.",
        ),
      );
    },
    onError: (err) => {
      const parsed = extractPaymentError(err, isVi);
      setActionError(parsed.message);
      toast.error(parsed.message);
      if (selectedEventId) {
        void qc.invalidateQueries({
          queryKey: ["admin-event-reconciliation", selectedEventId],
        });
      }
    },
  });

  const payoutMutation = useMutation({
    mutationFn: (eventId: string) => adminFinanceService.markEventPaid(eventId),
    onSuccess: (updated) => {
      setStaleWarning(null);
      setActionError(null);
      qc.setQueryData(["admin-event-reconciliation", updated.eventId], updated);
      toast.success(
        copyText(
          "Đã đánh dấu sự kiện hoàn tất chi trả.",
          "Event payout marked as paid.",
        ),
      );
    },
    onError: (err) => {
      const parsed = extractPaymentError(err, isVi);
      if (parsed.code === "RECONCILIATION_STALE") {
        setStaleWarning(parsed.message);
      } else {
        setActionError(parsed.message);
      }
      toast.error(parsed.message);
      if (selectedEventId) {
        void qc.invalidateQueries({
          queryKey: ["admin-event-reconciliation", selectedEventId],
        });
      }
    },
  });

  const handleCopy = async (value: string, label: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopiedId(value);
      toast.success(copyText(`Đã sao chép ${label}`, `Copied ${label}`));
    } catch {
      toast.error(copyText("Không thể sao chép vào clipboard.", "Failed to copy to clipboard."));
    }
  };

  const handleDirectLookupSubmit = (e: FormEvent) => {
    e.preventDefault();
    const candidate = directLookupInput.trim();
    if (!UUID_REGEX.test(candidate)) {
      toast.warning(
        copyText(
          "Vui lòng nhập đúng định dạng Event ID.",
          "Please enter a valid Event ID.",
        ),
      );
      return;
    }
    selectEvent(candidate);
  };

  const handleConfirmSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!selectedEventId) return;
    const trimmed = evidence.trim();
    if (!trimmed) {
      toast.warning(
        copyText(
          "Vui lòng nhập ghi chú / bằng chứng đối soát trước khi xác nhận.",
          "Please enter reconciliation evidence before confirming.",
        ),
      );
      return;
    }
    if (trimmed.length > 1000) {
      toast.warning(
        copyText(
          "Bằng chứng đối soát tối đa 1000 ký tự.",
          "Evidence must be at most 1000 characters.",
        ),
      );
      return;
    }
    setActionError(null);
    confirmMutation.mutate({ eventId: selectedEventId, note: trimmed });
  };

  const handleMarkPaid = async (report: EventReconciliationView) => {
    const displayTitle = eventDetailQuery.data?.title || report.eventId;
    const formattedNet = formatMoney(report.netAmount, report.currency, locale);

    const res = await Swal.fire({
      title: copyText("Xác nhận đã chi trả cho Ban tổ chức?", "Confirm Event Payout?"),
      html: `
        <div style="text-align: left; font-size: 14px; line-height: 1.55;">
          <p style="margin: 0 0 8px;">${copyText(
            "Chỉ đánh dấu đã chi trả sau khi đã hoàn tất chuyển tiền cho Ban tổ chức bên ngoài hệ thống. Thao tác này không thể hoàn tác.",
            "Only mark as paid after completing the external transfer to the organizer. This action cannot be undone.",
          )}</p>
          <div style="padding: 10px 12px; border-radius: 10px; background: #f8fafc; border: 1px solid #e2e6ed;">
            <div><strong>${copyText("Sự kiện:", "Event:")}</strong> ${escapeHtml(displayTitle)}</div>
            <div><strong>${copyText("Thực trả BTC:", "Net payout:")}</strong> <span style="color: #065f46; font-weight: 700;">${escapeHtml(formattedNet)}</span></div>
            <div><strong>${copyText("Mã BTC:", "Organizer:")}</strong> <code>${escapeHtml(report.organizerSubject)}</code></div>
          </div>
        </div>
      `,
      icon: "question",
      showCancelButton: true,
      confirmButtonText: copyText("Xác nhận đã chi trả", "Mark Paid"),
      cancelButtonText: copyText("Hủy", "Cancel"),
      confirmButtonColor: "#15171a",
      reverseButtons: true,
    });

    if (!res.isConfirmed) return;
    setActionError(null);
    payoutMutation.mutate(report.eventId);
  };

  if (!initialized) {
    return (
      <div className="tix-dashboard-page tix-admin-page">
        <div className="tix-dashboard-container">
          <TixAdminTopNav activeTab="reconciliation" />
          <div className="tix-orders-card tix-admin-state-box" role="status">
            <LoaderCircle size={26} className="tix-spin" aria-hidden="true" />
            <span>{t("eventsV1.loading", "Đang tải...")}</span>
          </div>
        </div>
      </div>
    );
  }

  if (!isAuthenticated || !isAdmin) {
    return (
      <div className="tix-dashboard-page tix-admin-page">
        <div className="tix-dashboard-container">
          <TixAdminTopNav activeTab="reconciliation" />
          <div className="tix-orders-card tix-admin-state-box" role="alert">
            <ShieldAlert size={32} color="#dc2626" aria-hidden="true" />
            <strong style={{ color: "var(--text-strong, #15171a)" }}>
              {copyText("Truy cập bị từ chối", "Access Denied")}
            </strong>
            <span>
              {copyText(
                "Bạn cần đăng nhập với tài khoản Quản trị viên (ADMIN) để thực hiện đối soát và chi trả sự kiện.",
                "You need an ADMIN account to reconcile and pay out events.",
              )}
            </span>
            {!isAuthenticated && (
              <button
                type="button"
                className="tix-admin-btn tix-admin-btn-primary"
                onClick={() => void keycloak.login()}
              >
                {t("nav.login", "Đăng nhập")}
              </button>
            )}
          </div>
        </div>
      </div>
    );
  }

  const report = reconciliationQuery.data;
  const eventMeta = eventDetailQuery.data;
  const blockers = report ? getReconciliationBlockers(report, isVi) : [];
  const trimmedEvidence = evidence.trim();
  const isBusy = confirmMutation.isPending || payoutMutation.isPending;

  const reconciliationLoadError = reconciliationQuery.isError
    ? extractPaymentError(reconciliationQuery.error, isVi)
    : null;

  return (
    <div className="tix-dashboard-page tix-admin-page">
      <div className="tix-dashboard-container">
        <TixAdminTopNav activeTab="reconciliation" />

        {/* Overview Header */}
        <div className="tix-overview-header">
          <div className="tix-overview-header-left">
            <div className="tix-overview-title-block">
              <h1 className="tix-overview-main-title">
                {copyText("Đối soát và chi trả sự kiện", "Event Reconciliation & Payout")}
              </h1>
              <p className="tix-overview-subtitle">
                {copyText(
                  "Xem báo cáo tài chính từ Payment Service, xác nhận đối soát sau khi sự kiện kết thúc và đánh dấu đã chi trả cho Ban tổ chức.",
                  "Inspect Payment Service financial ledgers, confirm reconciliation after event completion, and mark payouts.",
                )}
              </p>
            </div>
          </div>
        </div>

        {/* Subtabs & Direct Event ID lookup */}
        <div className="tix-subtabs-row">
          <div className="tix-subtabs-group" role="group" aria-label={copyText("Lọc trạng thái sự kiện", "Filter event status")}>
            {QUEUE_STATUS_TABS.map((tab) => (
              <button
                key={tab.key}
                type="button"
                className={`tix-subtab-btn ${queueStatus === tab.key ? "active" : ""}`}
                onClick={() => handleQueueStatusChange(tab.key)}
              >
                <span>{isVi ? tab.vi : tab.en}</span>
              </button>
            ))}
          </div>

          <form className="tix-admin-lookup-form" onSubmit={handleDirectLookupSubmit}>
            <div className="tix-search-input-wrap">
              <Search size={15} aria-hidden="true" />
              <input
                type="text"
                className="tix-search-input"
                value={directLookupInput}
                onChange={(e) => {
                  const value = e.target.value;
                  setDirectLookupInput(value);
                  const trimmed = value.trim();
                  if (UUID_REGEX.test(trimmed) && trimmed !== selectedEventId) {
                    selectEvent(trimmed);
                  }
                }}
                placeholder={copyText(
                  "Nhập Event ID để mở đối soát nhanh...",
                  "Enter Event ID to inspect...",
                )}
              />
              {directLookupInput && (
                <button
                  type="button"
                  className="tix-search-clear-btn"
                  onClick={() => setDirectLookupInput("")}
                  aria-label={copyText("Xóa", "Clear")}
                >
                  <X size={13} />
                </button>
              )}
            </div>
          </form>
        </div>

        {/* Main Split Layout */}
        <div className="tix-admin-split-grid">
          {/* Left: Event List */}
          <section className="tix-orders-card" aria-busy={eventsListQuery.isFetching}>
            <div className="tix-card-header">
              <div>
                <h2 className="tix-card-title">
                  {copyText("Danh sách sự kiện", "Events")}
                </h2>
                <p className="tix-card-subtitle">
                  {copyText(
                    `${filteredQueueItems.length} sự kiện ${activeQueueTab.vi.toLowerCase()}`,
                    `${filteredQueueItems.length} ${activeQueueTab.en} event(s)`,
                  )}
                </p>
              </div>
            </div>

            <div className="tix-search-input-wrap" style={{ width: "100%", marginTop: 12 }}>
              <Search size={15} aria-hidden="true" />
              <input
                type="search"
                className="tix-search-input"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder={copyText("Lọc trong danh sách theo tên / ID...", "Filter loaded events...")}
              />
              {searchTerm && (
                <button
                  type="button"
                  className="tix-search-clear-btn"
                  onClick={() => setSearchTerm("")}
                  aria-label={copyText("Xóa từ khóa", "Clear filter")}
                >
                  <X size={13} />
                </button>
              )}
            </div>

            {eventsListQuery.isPending ? (
              <div className="tix-admin-state-box" role="status">
                <LoaderCircle size={24} className="tix-spin" aria-hidden="true" />
                <span>{t("eventsV1.loading", "Đang tải...")}</span>
              </div>
            ) : eventsListQuery.isError ? (
              <div className="tix-admin-error-box" role="alert" style={{ marginTop: 14 }}>
                <span>{copyText("Không thể tải danh sách sự kiện.", "Failed to load events.")}</span>
                <button
                  type="button"
                  className="tix-admin-btn tix-admin-btn-secondary"
                  onClick={() => void eventsListQuery.refetch()}
                >
                  {t("profile.retry", "Thử lại")}
                </button>
              </div>
            ) : filteredQueueItems.length === 0 ? (
              <div className="tix-admin-state-box">
                <CalendarClock size={28} color="var(--text-muted, #666b74)" aria-hidden="true" />
                <span>
                  {copyText(
                    "Không có sự kiện nào khớp với bộ lọc hiện tại.",
                    "No events match the current filter.",
                  )}
                </span>
              </div>
            ) : (
              <div className="tix-admin-app-list">
                {paginatedQueueItems.map((item) => {
                  const isSelected = selectedEventId === item.eventId;
                  const hasEnded = isEventEnded(item.endsAt);

                  return (
                    <button
                      key={item.eventId}
                      type="button"
                      className={`tix-admin-app-item ${isSelected ? "is-selected" : ""}`}
                      aria-pressed={isSelected}
                      onClick={() => selectEvent(item.eventId)}
                    >
                      <div className="tix-admin-app-main">
                        <span className="tix-admin-app-icon" aria-hidden="true">
                          <Scale size={17} />
                        </span>
                        <div className="tix-admin-app-copy">
                          <strong>{item.title}</strong>
                          <span className="tix-admin-mono">{shortId(item.eventId)}</span>
                          {item.endsAt && (
                            <span>
                              {copyText("Kết thúc:", "Ends:")}{" "}
                              {formatDateTime(item.endsAt, locale)}
                            </span>
                          )}
                        </div>
                      </div>
                      <div className="tix-admin-app-right">
                        <span
                          className={`tix-status-badge ${
                            item.status === "CANCELLED"
                              ? "is-rejected"
                              : hasEnded
                              ? "is-succeeded"
                              : "is-pending"
                          }`}
                        >
                          {item.status === "CANCELLED"
                            ? copyText("Đã hủy", "Cancelled")
                            : hasEnded
                            ? copyText("Đã kết thúc", "Ended")
                            : copyText("Chưa kết thúc", "Ongoing")}
                        </span>
                        <ChevronRight size={16} color="var(--text-muted, #666b74)" aria-hidden="true" />
                      </div>
                    </button>
                  );
                })}
              </div>
            )}

            {totalListPages > 1 && (
              <div className="tix-pagination-wrapper" style={{ marginTop: 14 }}>
                <div className="tix-pagination-box">
                  <button
                    type="button"
                    className="tix-page-arrow-btn"
                    disabled={safeListPage === 0}
                    onClick={() => setListPage((p) => Math.max(0, p - 1))}
                    aria-label={copyText("Trang trước", "Previous page")}
                  >
                    <ChevronLeft size={15} />
                  </button>

                  <span className="tix-page-indicator">
                    {String(safeListPage + 1).padStart(2, "0")}/
                    {String(totalListPages).padStart(2, "0")}
                  </span>

                  <button
                    type="button"
                    className="tix-page-arrow-btn"
                    disabled={safeListPage >= totalListPages - 1}
                    onClick={() => setListPage((p) => Math.min(totalListPages - 1, p + 1))}
                    aria-label={copyText("Trang sau", "Next page")}
                  >
                    <ChevronRight size={15} />
                  </button>
                </div>
              </div>
            )}

            {eventsListQuery.hasNextPage && !eventsListQuery.isFetchNextPageError && (
              <div style={{ marginTop: 10, display: "flex", justifyContent: "center" }}>
                <button
                  type="button"
                  className="tix-admin-btn tix-admin-btn-secondary"
                  disabled={eventsListQuery.isFetchingNextPage}
                  onClick={() => void eventsListQuery.fetchNextPage()}
                >
                  {eventsListQuery.isFetchingNextPage && (
                    <LoaderCircle size={15} className="tix-spin" aria-hidden="true" />
                  )}
                  <span>
                    {eventsListQuery.isFetchingNextPage
                      ? t("eventsV1.loading", "Đang tải...")
                      : t("eventsV1.loadMore", "Tải thêm")}
                  </span>
                </button>
              </div>
            )}
          </section>

          {/* Right: Reconciliation Report & Actions */}
          <section className="tix-orders-card" aria-label={copyText("Chi tiết đối soát", "Reconciliation details")}>
            {!selectedEventId ? (
              <div className="tix-admin-state-box">
                <Scale size={32} color="var(--text-muted, #666b74)" aria-hidden="true" />
                <span>
                  {copyText(
                    "Chọn một sự kiện ở danh sách bên trái hoặc nhập Event ID để xem báo cáo đối soát và chi trả.",
                    "Select an event on the left or enter an Event ID to inspect reconciliation and payout.",
                  )}
                </span>
              </div>
            ) : reconciliationQuery.isPending ? (
              <div className="tix-admin-state-box" role="status">
                <LoaderCircle size={26} className="tix-spin" aria-hidden="true" />
                <span>
                  {copyText("Đang tải báo cáo đối soát từ Payment Service...", "Loading reconciliation report...")}
                </span>
              </div>
            ) : reconciliationLoadError ? (
              <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
                <div className="tix-admin-review-group-header">
                  <div>
                    <h2 className="tix-card-title">
                      {eventMeta?.title || shortId(selectedEventId)}
                    </h2>
                    <span className="tix-admin-mono" style={{ fontSize: 12, color: "var(--text-subtle)" }}>
                      {selectedEventId}
                    </span>
                  </div>
                  <button
                    type="button"
                    className="tix-admin-btn tix-admin-btn-secondary"
                    onClick={() => void reconciliationQuery.refetch()}
                  >
                    <RefreshCw size={14} aria-hidden="true" />
                    <span>{copyText("Thử lại", "Retry")}</span>
                  </button>
                </div>

                <div className="tix-admin-error-box" role="alert">
                  <span>{reconciliationLoadError.message}</span>
                  {reconciliationLoadError.code && (
                    <span className="tix-admin-mono" style={{ fontSize: 12 }}>
                      {reconciliationLoadError.code}
                    </span>
                  )}
                </div>
              </div>
            ) : report ? (
              <div className="tix-admin-recon-detail">
                {/* Header Summary */}
                <div className="tix-admin-recon-header">
                  <div style={{ minWidth: 0 }}>
                    <div className="tix-admin-recon-title-row">
                      <h2 className="tix-card-title" style={{ margin: 0 }}>
                        {eventMeta?.title || copyText("Báo cáo đối soát sự kiện", "Event Reconciliation")}
                      </h2>
                      <span
                        className={`tix-status-badge ${
                          report.paidAt
                            ? "is-succeeded"
                            : report.reconciledAt
                            ? "is-processing"
                            : report.eligibleForReconciliation
                            ? "is-active"
                            : report.cancelled
                            ? "is-rejected"
                            : "is-pending"
                        }`}
                      >
                        {report.paidAt
                          ? copyText("Đã chi trả", "Paid")
                          : report.reconciledAt
                          ? copyText("Đã đối soát · Chờ chi trả", "Reconciled · Awaiting Payout")
                          : report.eligibleForReconciliation
                          ? copyText("Sẵn sàng đối soát", "Eligible for Reconciliation")
                          : report.cancelled
                          ? copyText("Sự kiện đã hủy", "Cancelled")
                          : copyText("Chưa đủ điều kiện", "Not Eligible Yet")}
                      </span>
                    </div>

                    <div className="tix-admin-recon-submeta">
                      <span className="tix-admin-inline-id">
                        <span>Event ID:</span>
                        <span className="tix-admin-mono">{shortId(report.eventId)}</span>
                        <button
                          type="button"
                          className="tix-admin-icon-copy"
                          onClick={() => void handleCopy(report.eventId, "Event ID")}
                          aria-label={copyText("Sao chép Event ID", "Copy Event ID")}
                          title={report.eventId}
                        >
                          {copiedId === report.eventId ? (
                            <Check size={12} color="#16a34a" />
                          ) : (
                            <Copy size={12} />
                          )}
                        </button>
                      </span>
                      <span className="tix-admin-inline-id">
                        {organizerQuery.data?.logoUrl ? (
                          <img
                            src={organizerQuery.data.logoUrl}
                            alt=""
                            className="tix-admin-organizer-avatar"
                            onError={(e) => {
                              e.currentTarget.style.display = "none";
                            }}
                          />
                        ) : (
                          <Building2 size={13} aria-hidden="true" />
                        )}
                        <span style={{ fontWeight: 600, color: "var(--text-strong, #15171a)" }}>
                          {organizerQuery.data?.name || shortId(report.organizerSubject)}
                        </span>
                        <button
                          type="button"
                          className="tix-admin-icon-copy"
                          onClick={() =>
                            void handleCopy(report.organizerSubject, "Organizer Subject")
                          }
                          aria-label={copyText("Sao chép mã Ban tổ chức", "Copy Organizer Subject")}
                          title={report.organizerSubject}
                        >
                          {copiedId === report.organizerSubject ? (
                            <Check size={12} color="#16a34a" />
                          ) : (
                            <Copy size={12} />
                          )}
                        </button>
                      </span>
                    </div>
                  </div>

                  <button
                    type="button"
                    className="tix-admin-btn tix-admin-btn-secondary"
                    disabled={reconciliationQuery.isFetching || isBusy}
                    onClick={() => void reconciliationQuery.refetch()}
                  >
                    <RefreshCw
                      size={14}
                      className={reconciliationQuery.isFetching ? "tix-spin" : undefined}
                      aria-hidden="true"
                    />
                    <span>{copyText("Làm mới số liệu", "Refresh Ledger")}</span>
                  </button>
                </div>

                {/* 3-Step Pipeline */}
                <div className="tix-admin-recon-steps">
                  <div
                    className={`tix-admin-recon-step ${
                      report.eligibleForReconciliation || report.reconciledAt || report.paidAt
                        ? "is-done"
                        : "is-waiting"
                    }`}
                  >
                    <span className="tix-step-dot">1</span>
                    <div>
                      <strong>{copyText("Đủ điều kiện đối soát", "Eligible")}</strong>
                      <small>
                        {report.eligibleForReconciliation
                          ? copyText("Đã kết thúc & hết giao dịch chờ", "Ended & no pending txns")
                          : blockers[0] || copyText("Kiểm tra điều kiện", "Check blockers")}
                      </small>
                    </div>
                  </div>

                  <div
                    className={`tix-admin-recon-step ${
                      report.reconciledAt ? "is-done" : "is-waiting"
                    }`}
                  >
                    <span className="tix-step-dot">2</span>
                    <div>
                      <strong>{copyText("Xác nhận đối soát", "Reconciled")}</strong>
                      <small>
                        {report.reconciledAt
                          ? formatDateTime(report.reconciledAt, locale)
                          : copyText("Chưa xác nhận", "Pending confirmation")}
                      </small>
                    </div>
                  </div>

                  <div
                    className={`tix-admin-recon-step ${report.paidAt ? "is-done" : "is-waiting"}`}
                  >
                    <span className="tix-step-dot">3</span>
                    <div>
                      <strong>{copyText("Hoàn tất chi trả", "Paid Out")}</strong>
                      <small>
                        {report.paidAt
                          ? formatDateTime(report.paidAt, locale)
                          : copyText("Chờ xác nhận chi trả", "Awaiting payout")}
                      </small>
                    </div>
                  </div>
                </div>

                {/* Warnings / Stale Alerts */}
                {staleWarning && (
                  <div className="tix-admin-warn-box" role="alert">
                    <AlertTriangle size={16} aria-hidden="true" />
                    <div>
                      <strong>
                        {copyText(
                          "Số liệu đã thay đổi sau khi đối soát",
                          "Ledger changed since reconciliation",
                        )}
                      </strong>
                      <p style={{ margin: "4px 0 0" }}>{staleWarning}</p>
                    </div>
                  </div>
                )}

                {actionError && (
                  <div className="tix-admin-error-box" role="alert">
                    <span>{actionError}</span>
                  </div>
                )}

                {/* Structured Detail Groups (matching AdminEventReviewPage / AdminOrganizerReviewPage) */}
                <div className="tix-admin-review-groups">
                  {/* Group 1: Financial Summary */}
                  <section className="tix-admin-review-group">
                    <span className="tix-admin-review-group-icon" aria-hidden="true">
                      <Banknote size={18} />
                    </span>
                    <div>
                      <div className="tix-admin-review-group-header">
                        <h3>{copyText("Số liệu tài chính", "Financial Summary")}</h3>
                        <span className="tix-admin-decided-at">
                          <span className="tix-admin-decided-label">
                            {copyText("Tính đến:", "As of:")}
                          </span>
                          <time dateTime={report.asOf}>{formatDateTime(report.asOf, locale)}</time>
                        </span>
                      </div>

                      <div className="tix-admin-finance-grid">
                        <div className="tix-admin-finance-card">
                          <span className="tix-kpi-label">
                            {copyText("Tổng thu hợp lệ", "Valid Gross")}
                          </span>
                          <strong className="tix-admin-finance-val">
                            {formatMoney(report.validGross, report.currency, locale)}
                          </strong>
                          <small className="tix-kpi-note">
                            {copyText("Tổng thanh toán đã ghi nhận", "Settled payments in ledger")}
                          </small>
                        </div>

                        <div className="tix-admin-finance-card">
                          <span className="tix-kpi-label">
                            {copyText("Tổng tiền đã hoàn", "Valid Refunds")}
                          </span>
                          <strong className="tix-admin-finance-val is-refund">
                            {formatMoney(report.validRefunds, report.currency, locale)}
                          </strong>
                          <small className="tix-kpi-note">
                            {copyText("Tổng các khoản hoàn hợp lệ", "Settled refunds in ledger")}
                          </small>
                        </div>

                        <div className="tix-admin-finance-card">
                          <span className="tix-kpi-label">
                            {copyText(
                              `Phí nền tảng (${formatFeePercent(report.feeRate)})`,
                              `Platform Fee (${formatFeePercent(report.feeRate)})`,
                            )}
                          </span>
                          <strong className="tix-admin-finance-val">
                            {formatMoney(report.feeAmount, report.currency, locale)}
                          </strong>
                          <small className="tix-kpi-note">
                            {copyText(
                              `(Tổng thu − Tổng hoàn) × ${formatFeePercent(report.feeRate)}`,
                              `(Gross − Refunds) × ${formatFeePercent(report.feeRate)}`,
                            )}
                          </small>
                        </div>

                        <div className="tix-admin-finance-card is-highlight">
                          <span className="tix-kpi-label">
                            {copyText("Thực trả Ban tổ chức", "Net Organizer Payout")}
                          </span>
                          <strong className="tix-admin-finance-val is-net">
                            {formatMoney(report.netAmount, report.currency, locale)}
                          </strong>
                          <small className="tix-kpi-note">
                            {copyText(
                              "Tổng thu − Tổng hoàn − Phí nền tảng",
                              "Gross − Refunds − Platform fee",
                            )}
                          </small>
                        </div>
                      </div>
                    </div>
                  </section>

                  {/* Group 2: Eligibility & Operational Details */}
                  <section className="tix-admin-review-group">
                    <span className="tix-admin-review-group-icon" aria-hidden="true">
                      <Clock size={18} />
                    </span>
                    <div>
                      <div className="tix-admin-review-group-header">
                        <h3>
                          {copyText("Điều kiện đối soát & Trạng thái", "Eligibility & Status")}
                        </h3>
                      </div>

                      <dl className="tix-admin-review-dl">
                        <div>
                          <dt>{copyText("Điều kiện thao tác", "Eligibility")}</dt>
                          <dd>
                            {report.eligibleForReconciliation ? (
                              <span style={{ color: "#065f46" }}>
                                {copyText(
                                  "Đủ điều kiện xác nhận đối soát",
                                  "Eligible for reconciliation",
                                )}
                              </span>
                            ) : (
                              <span style={{ color: "#b45309" }}>
                                {blockers.join(" · ") ||
                                  copyText("Chưa đủ điều kiện", "Not eligible")}
                              </span>
                            )}
                          </dd>
                        </div>
                        <div>
                          <dt>{copyText("Kết thúc sự kiện", "Event Ends At")}</dt>
                          <dd>{formatDateTime(report.eventEndsAt, locale)}</dd>
                        </div>
                        <div>
                          <dt>{copyText("Thanh toán đang chờ", "Pending Payments")}</dt>
                          <dd>{report.paymentPendingCount}</dd>
                        </div>
                        <div>
                          <dt>{copyText("Hoàn tiền đang chờ", "Pending Refunds")}</dt>
                          <dd>
                            <span>{report.refundPendingCount}</span>
                            {report.refundPendingCount > 0 && (
                              <>
                                {" · "}
                                <Link to="/admin/refunds">
                                  {copyText("Mở quản lý hoàn tiền", "Open Refunds")}
                                </Link>
                              </>
                            )}
                          </dd>
                        </div>
                        <div>
                          <dt>{copyText("Trạng thái hủy", "Cancellation")}</dt>
                          <dd>
                            {report.cancelled
                              ? copyText("Đã hủy (không đối soát chi trả)", "Cancelled")
                              : copyText("Bình thường", "Active / Normal")}
                          </dd>
                        </div>
                      </dl>
                    </div>
                  </section>

                  {/* Group 3: Reconciliation & Payout Audit History */}
                  {(report.reconciledAt || report.paidAt) && (
                    <section className="tix-admin-review-group">
                      <span className="tix-admin-review-group-icon" aria-hidden="true">
                        <FileCheck2 size={18} />
                      </span>
                      <div>
                        <div className="tix-admin-review-group-header">
                          <h3>
                            {copyText(
                              "Lịch sử đối soát & chi trả",
                              "Reconciliation & Payout History",
                            )}
                          </h3>
                        </div>

                        <dl className="tix-admin-review-dl">
                          {report.reconciledAt && (
                            <>
                              <div>
                                <dt>{copyText("Thời gian đối soát", "Reconciled At")}</dt>
                                <dd>{formatDateTime(report.reconciledAt, locale)}</dd>
                              </div>
                              <div>
                                <dt>{copyText("Admin đối soát", "Reconciled By")}</dt>
                                <dd className="tix-admin-mono">{report.reconciledBy || "—"}</dd>
                              </div>
                              <div>
                                <dt>{copyText("Bằng chứng / Ghi chú", "Evidence")}</dt>
                                <dd>{report.reconciliationEvidence || "—"}</dd>
                              </div>
                            </>
                          )}

                          {report.paidAt && (
                            <>
                              <div>
                                <dt>{copyText("Thời gian chi trả", "Paid At")}</dt>
                                <dd>{formatDateTime(report.paidAt, locale)}</dd>
                              </div>
                              <div>
                                <dt>{copyText("Admin chi trả", "Paid By")}</dt>
                                <dd className="tix-admin-mono">{report.paidBy || "—"}</dd>
                              </div>
                            </>
                          )}
                        </dl>
                      </div>
                    </section>
                  )}
                </div>

                {/* Reconciliation & Payout Actions */}
                {report.paidAt ? (
                  <div className="tix-admin-success-box" role="status">
                    <CheckCircle2 size={18} aria-hidden="true" />
                    <span>
                      {copyText(
                        `Sự kiện đã hoàn tất đối soát và được đánh dấu chi trả lúc ${formatDateTime(report.paidAt, locale)}.`,
                        `This event was reconciled and marked paid at ${formatDateTime(report.paidAt, locale)}.`,
                      )}
                    </span>
                  </div>
                ) : (
                  <form onSubmit={handleConfirmSubmit} className="tix-admin-recon-form">
                    <div className="tix-admin-review-group" style={{ borderBottom: "none", paddingBottom: 0 }}>
                      <span className="tix-admin-review-group-icon" aria-hidden="true">
                        <Wallet size={18} />
                      </span>
                      <div>
                        <div className="tix-admin-review-group-header">
                          <label htmlFor="recon-evidence-input" style={{ cursor: "pointer" }}>
                            <h3 style={{ margin: 0 }}>
                              {report.reconciledAt
                                ? copyText(
                                    "Cập nhật xác nhận đối soát",
                                    "Re-confirm Reconciliation",
                                  )
                                : copyText(
                                    "Ghi chú xác nhận đối soát",
                                    "Reconciliation Note",
                                  )}
                            </h3>
                          </label>
                          <span className="tix-admin-promo-meta">{trimmedEvidence.length}/1000</span>
                        </div>

                        <textarea
                          id="recon-evidence-input"
                          className="tix-admin-textarea"
                          rows={3}
                          maxLength={1000}
                          disabled={!report.eligibleForReconciliation || isBusy}
                          value={evidence}
                          onChange={(e) => setEvidence(e.target.value)}
                          placeholder={copyText(
                            "Hãy nhập ghi chú đối soát",
                            "Enter reconciliation note",
                          )}
                        />
                      </div>
                    </div>

                    <div className="tix-admin-actions-bar">
                      <small style={{ color: "var(--text-muted, #666b74)", fontSize: 12 }}>
                        {report.eligibleForReconciliation
                          ? copyText(
                              "Xác nhận đối soát lưu biên bản kiểm tra trước khi chuyển tiền cho Ban tổ chức.",
                              "Confirmation snapshots the ledger check prior to external payout.",
                            )
                          : blockers.join(" ")}
                      </small>

                      <div className="tix-admin-actions-btns">
                        <button
                          type="submit"
                          className="tix-admin-btn tix-admin-btn-secondary"
                          disabled={
                            !report.eligibleForReconciliation || !trimmedEvidence || isBusy
                          }
                        >
                          {confirmMutation.isPending && (
                            <LoaderCircle size={14} className="tix-spin" aria-hidden="true" />
                          )}
                          <span>
                            {report.reconciledAt
                              ? copyText("Cập nhật xác nhận đối soát", "Re-confirm Reconciliation")
                              : copyText("Xác nhận đối soát", "Confirm Reconciliation")}
                          </span>
                        </button>

                        <button
                          type="button"
                          className="tix-admin-btn tix-admin-btn-primary"
                          disabled={
                            !report.reconciledAt || !report.eligibleForReconciliation || isBusy
                          }
                          onClick={() => void handleMarkPaid(report)}
                        >
                          {payoutMutation.isPending && (
                            <LoaderCircle size={14} className="tix-spin" aria-hidden="true" />
                          )}
                          <span>{copyText("Đánh dấu đã chi trả", "Mark Event Paid")}</span>
                        </button>
                      </div>
                    </div>
                  </form>
                )}
              </div>
            ) : null}
          </section>
        </div>
      </div>
    </div>
  );
}

export default AdminReconciliationPage;
