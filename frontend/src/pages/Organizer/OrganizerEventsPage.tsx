import { useEffect, useMemo, useRef, useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router-dom";
import {
  AlertCircle,
  Armchair,
  Calendar,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Clock,
  Copy,
  ExternalLink,
  FileEdit,
  MoreHorizontal,
  PlusCircle,
  QrCode,
  RefreshCw,
  Search,
  Ticket,
  X,
} from "lucide-react";
import { toast } from "react-toastify";
import Swal from "sweetalert2";
import OrganizerLayout from "../../components/organizer/OrganizerLayout";
import TixOrganizerTopNav from "../../components/organizer/TixOrganizerTopNav";
import {
  organizerDashboardService,
  organizerEventStatuses,
  OrganizerSummary,
} from "../../services/organizerDashboardService";
import { cancellationService } from "../../services/cancellationService";
import { useOrganizerGate } from "./useOrganizerGate";
import { confirmDestructiveAction } from "../../lib/swal";
import "./organizer-dashboard.css";
import "./organizer-events.css";

type Action = "submit" | "publish" | "cancel";
type CancellationCheckState = "loading" | "pending" | "none" | "error";

const TABLE_PAGE_SIZE = 10;
const salesHistoryStart = "1970-01-01";

function formatSchedule(isoString: string, locale: string) {
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return isoString;
  return date.toLocaleString(locale, {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function getStatusBadgeClass(status: OrganizerSummary["status"]) {
  switch (status) {
    case "PUBLISHED":
      return "is-published";
    case "APPROVED":
      return "is-approved";
    case "PENDING_APPROVAL":
      return "is-pending";
    case "CANCELLED":
      return "is-cancelled";
    default:
      return "is-draft";
  }
}

function EventManagementWorkspace({ subject, issuer }: { subject: string; issuer?: string }) {
  const { t, i18n } = useTranslation();
  const { keycloak } = useKeycloak();
  const navigate = useNavigate();
  const client = useQueryClient();
  const locale = i18n.resolvedLanguage === "en" ? "en-US" : "vi-VN";

  const [statusFilter, setStatusFilter] = useState<OrganizerSummary["status"] | "ALL">("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [tablePage, setTablePage] = useState(0);

  const [isStatusDropdownOpen, setIsStatusDropdownOpen] = useState(false);
  const [openActionMenuId, setOpenActionMenuId] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const statusDropdownRef = useRef<HTMLDivElement>(null);
  const actionMenuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      if (statusDropdownRef.current && !statusDropdownRef.current.contains(target)) {
        setIsStatusDropdownOpen(false);
      }
      if (actionMenuRef.current && !actionMenuRef.current.contains(target)) {
        setOpenActionMenuId(null);
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setIsStatusDropdownOpen(false);
        setOpenActionMenuId(null);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

  const scope = [issuer, subject];

  // 1. Lấy toàn bộ danh sách sự kiện thật của organizer (chia cursor)
  const eventsQuery = useQuery({
    queryKey: ["organizer-events-list-complete", ...scope],
    queryFn: async ({ signal }) => {
      const events = new Map<string, OrganizerSummary>();
      const seen = new Set<string>();
      let cursor: string | null = null;
      do {
        const page = await organizerDashboardService.events(cursor, signal);
        page.items.forEach((event) => events.set(event.eventId, event));
        cursor = page.nextCursor;
        if (cursor) {
          if (seen.has(cursor)) throw new Error("Repeated organizer event cursor");
          seen.add(cursor);
        }
      } while (cursor);
      return [...events.values()];
    },
    retry: false,
  });

  // 2. Lấy thống kê vé bán & doanh thu thật theo từng sự kiện (toàn thời gian từ 1970-01-01)
  const eventSalesQuery = useQuery({
    queryKey: ["organizer-event-sales-all-time", ...scope, salesHistoryStart],
    queryFn: async ({ signal }) => {
      const first = await organizerDashboardService.eventSales(
        subject,
        0,
        salesHistoryStart,
        undefined,
        signal,
      );
      const sales = new Map(first.items.map((item) => [item.eventId, item]));
      for (let page = 1; page < first.totalPages; page += 1) {
        const next = await organizerDashboardService.eventSales(
          subject,
          page,
          salesHistoryStart,
          undefined,
          signal,
        );
        next.items.forEach((item) => sales.set(item.eventId, item));
      }
      return { ...first, items: [...sales.values()] };
    },
    retry: false,
  });

  const eventsList = useMemo(
    () => (eventsQuery.isSuccess ? eventsQuery.data : []),
    [eventsQuery.isSuccess, eventsQuery.data]
  );

  const filteredEvents = useMemo(() => {
    const q = searchQuery.trim().toLocaleLowerCase();
    return eventsList.filter((evt) => {
      const matchStatus = statusFilter === "ALL" || evt.status === statusFilter;
      const matchQuery =
        !q ||
        evt.title.toLocaleLowerCase().includes(q) ||
        evt.eventId.toLocaleLowerCase().includes(q);
      return matchStatus && matchQuery;
    });
  }, [eventsList, searchQuery, statusFilter]);

  const totalPages = Math.max(1, Math.ceil(filteredEvents.length / TABLE_PAGE_SIZE));
  const safePage = Math.min(tablePage, totalPages - 1);
  const paginatedEvents = useMemo(() => {
    const start = safePage * TABLE_PAGE_SIZE;
    return filteredEvents.slice(start, start + TABLE_PAGE_SIZE);
  }, [filteredEvents, safePage]);

  // 3. Chỉ kiểm tra lịch sử yêu cầu hủy cho các sự kiện PUBLISHED đang hiển thị trên trang hiện tại
  const visiblePublishedEvents = useMemo(
    () => paginatedEvents.filter((evt) => evt.status === "PUBLISHED"),
    [paginatedEvents]
  );

  const cancellationHistoryQueries = useQueries({
    queries: visiblePublishedEvents.map((evt) => ({
      queryKey: ["organizer-cancellation-history", ...scope, evt.eventId],
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        cancellationService.getOrganizerCancellationHistory(evt.eventId, 20, undefined, signal),
      staleTime: 10000,
      retry: false,
    })),
  });

  const cancellationStatusMap = useMemo(() => {
    const map = new Map<string, { status: CancellationCheckState; refetch: () => void }>();
    visiblePublishedEvents.forEach((evt, idx) => {
      const q = cancellationHistoryQueries[idx];
      let status: CancellationCheckState = "loading";
      if (!q || q.isPending) {
        status = "loading";
      } else if (q.isError) {
        status = "error";
      } else if (q.data?.items?.some((req) => req.status === "PENDING")) {
        status = "pending";
      } else {
        status = "none";
      }
      map.set(evt.eventId, {
        status,
        refetch: () => void q?.refetch(),
      });
    });
    return map;
  }, [visiblePublishedEvents, cancellationHistoryQueries]);

  const eventSalesMap = useMemo(() => {
    const map = new Map<string, { soldRevenue: string; soldTicketCount: number; soldOrderCount: number }>();
    eventSalesQuery.data?.items?.forEach((item) => {
      map.set(item.eventId, {
        soldRevenue: item.soldRevenue,
        soldTicketCount: item.soldTicketCount,
        soldOrderCount: item.soldOrderCount,
      });
    });
    return map;
  }, [eventSalesQuery.data]);

  const formatMoney = (value: string | number, currency = "VND") => {
    try {
      const num = typeof value === "string" ? BigInt(value) : BigInt(Math.round(value));
      return `${num.toLocaleString(locale)} ${currency === "VND" ? "đ" : currency}`;
    } catch {
      return `${value} đ`;
    }
  };

  // Thống kê thật từ API
  const statusCounts = useMemo(() => {
    let draft = 0;
    let pending = 0;
    let approved = 0;
    let published = 0;
    let cancelled = 0;
    for (const evt of eventsList) {
      if (evt.status === "DRAFT") draft += 1;
      else if (evt.status === "PENDING_APPROVAL") pending += 1;
      else if (evt.status === "APPROVED") approved += 1;
      else if (evt.status === "PUBLISHED") published += 1;
      else if (evt.status === "CANCELLED") cancelled += 1;
    }
    return { draft, pending, approved, published, cancelled };
  }, [eventsList]);

  const salesTotals = useMemo(() => {
    let totalTickets = 0;
    let totalRevenue = 0n;
    eventSalesQuery.data?.items?.forEach((item) => {
      totalTickets += item.soldTicketCount;
      try {
        totalRevenue += BigInt(item.soldRevenue || "0");
      } catch {
        // Ignore malformed numeric string
      }
    });
    return {
      totalTickets,
      totalRevenue: formatMoney(totalRevenue.toString(), eventSalesQuery.data?.currency || "VND"),
    };
  }, [eventSalesQuery.data, locale]);

  // Mutation chuyển trạng thái sự kiện (submit / publish / cancel)
  const mutation = useMutation({
    mutationFn: ({ event, action }: { event: OrganizerSummary; action: Action }) =>
      organizerDashboardService.transition(event.eventId, action, event.rowVersion),
    onSuccess: async () => {
      toast.success(t("eventsV1.success"));
      await Promise.all([
        client.invalidateQueries({ queryKey: ["organizer-events-list-complete", ...scope] }),
        client.invalidateQueries({ queryKey: ["organizer-event-list", ...scope] }),
      ]);
    },
    onError: () => {
      toast.error(t("eventsV1.error"));
      void client.invalidateQueries({ queryKey: ["organizer-events-list-complete", ...scope] });
    },
  });

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
    mutation.mutate({ event, action });
  }

  async function handleRequestCancellation(event: OrganizerSummary) {
    const result = await Swal.fire({
      title: t("organizerEvents.cancelRequestTitle", "Yêu cầu hủy sự kiện"),
      text: t("organizerEvents.cancelRequestExplanation", {
        defaultValue: "{{title}} — Yêu cầu hủy sự kiện đã công bố sẽ được quản trị viên xem xét.",
        title: event.title,
        interpolation: { escapeValue: false },
      }),
      input: "textarea",
      inputLabel: t("organizerEvents.cancelRequestReasonLabel", "Lý do hủy sự kiện (bắt buộc):"),
      inputPlaceholder: t(
        "organizerEvents.cancelRequestPlaceholder",
        "Nhập lý do chi tiết (VD: Điều kiện thời tiết bất khả kháng, nghệ sĩ gặp sự cố sức khỏe...)..."
      ),
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
        toast.success(
          t("organizerEvents.cancelRequestSuccess", "Đã gửi yêu cầu hủy sự kiện tới Quản trị viên.")
        );
        void client.invalidateQueries({ queryKey: ["organizer-cancellation-history", ...scope] });
        void client.invalidateQueries({ queryKey: ["organizer-events-list-complete", ...scope] });
      } catch (err: unknown) {
        const errCode = (err as { response?: { data?: { code?: string } } })?.response?.data?.code;
        if (errCode === "CANCELLATION_REQUEST_PENDING") {
          toast.info(
            t(
              "organizerEvents.alreadyPending",
              "Sự kiện này đã có yêu cầu hủy đang chờ Quản trị viên phê duyệt."
            )
          );
          void client.invalidateQueries({ queryKey: ["organizer-cancellation-history", ...scope] });
          return;
        }
        if (errCode === "DIRECT_CANCELLATION_AVAILABLE") {
          const directConfirm = await Swal.fire({
            title: t("organizerEvents.directCancelTitle", "Sự kiện chưa có người mua vé"),
            text: t(
              "organizerEvents.directCancelText",
              "Sự kiện này chưa có khán giả nào mua vé nên không cần gửi Quản trị viên duyệt hoàn tiền. Bạn có muốn hủy trực tiếp sự kiện ngay bây giờ không?"
            ),
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

  const handleCopyId = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    void navigator.clipboard.writeText(id);
    setCopiedId(id);
    setTimeout(() => {
      setCopiedId((prev) => (prev === id ? null : prev));
    }, 1500);
  };

  const getStatusLabel = (key: OrganizerSummary["status"] | "ALL") => {
    if (key === "ALL") return t("organizerHub.statusAll", { defaultValue: "Tất cả trạng thái" });
    return t(`dashboardV1.status.${key}`);
  };

  const isRefreshing =
    eventsQuery.isFetching ||
    eventSalesQuery.isFetching ||
    cancellationHistoryQueries.some((q) => q.isFetching);
  const handleRefresh = () => {
    void Promise.all([
      eventsQuery.refetch(),
      eventSalesQuery.refetch(),
      ...cancellationHistoryQueries.map((q) => q.refetch()),
    ]);
  };

  const renderPrimaryQuickAction = (event: OrganizerSummary) => {
    if (event.status === "DRAFT") {
      return (
        <Link
          className="tix-inline-action-btn is-primary"
          to={`/organizer/events/${event.eventId}/edit`}
        >
          <FileEdit size={13} aria-hidden="true" />
          <span>{t("organizerEvents.continueEditing", "Tiếp tục chỉnh sửa")}</span>
        </Link>
      );
    }
    if (event.status === "APPROVED") {
      return (
        <button
          type="button"
          className="tix-inline-action-btn is-primary"
          disabled={mutation.isPending || eventsQuery.isFetching}
          onClick={() => void act(event, "publish")}
        >
          <CheckCircle2 size={13} aria-hidden="true" />
          <span>{t("eventsV1.publish", "Xuất bản")}</span>
        </button>
      );
    }
    if (event.status === "PUBLISHED") {
      return (
        <Link
          className="tix-inline-action-btn"
          to={`/event/${event.eventId}`}
        >
          <ExternalLink size={13} aria-hidden="true" />
          <span>{t("organizerEvents.viewPublic", "Xem công khai")}</span>
        </Link>
      );
    }
    return null;
  };

  return (
    <div className="tix-dashboard-page tix-events-page">
      <div className="tix-dashboard-container">
        {/* 1. Topbar đồng bộ với /organizer và /organizer/events/new */}
        <TixOrganizerTopNav activeTab="events" />

        {/* 2. Overview Header */}
        <div className="tix-overview-header">
          <div className="tix-overview-header-left">
            <div className="tix-overview-title-block">
              <h1 className="tix-overview-main-title">
                {t("organizer.eventManagement", "Quản lý sự kiện")}
              </h1>
              <p className="tix-overview-subtitle">
                {t(
                  "eventsV1.description",
                  "Theo dõi và thực hiện gửi duyệt, xuất bản, hủy sự kiện của bạn."
                )}
              </p>
            </div>

            <div className="tix-overview-badges-row">
              <span className="tix-overview-badge">
                <Calendar size={13} aria-hidden="true" />
                <span>
                  {eventsQuery.isSuccess
                    ? t("organizerHub.eventsCount", {
                        count: eventsList.length,
                        defaultValue: `${eventsList.length} sự kiện`,
                      })
                    : t("organizerHub.dataLoading")}
                </span>
              </span>
              {eventsQuery.isSuccess && (
                <span className="tix-overview-badge">
                  <Ticket size={13} aria-hidden="true" />
                  <span>
                    {t("organizerEvents.publishedCountBadge", {
                      count: statusCounts.published,
                      defaultValue: `${statusCounts.published} đã công bố`,
                    })}
                  </span>
                </span>
              )}
              {eventsQuery.isSuccess && statusCounts.pending > 0 && (
                <span className="tix-overview-badge">
                  <Clock size={13} aria-hidden="true" />
                  <span>
                    {t("organizerEvents.pendingCountBadge", {
                      count: statusCounts.pending,
                      defaultValue: `${statusCounts.pending} chờ duyệt`,
                    })}
                  </span>
                </span>
              )}
            </div>
          </div>

          <div className="tix-overview-header-right">
            <button
              type="button"
              className="tix-filter-select-btn"
              disabled={isRefreshing || mutation.isPending}
              onClick={handleRefresh}
            >
              <RefreshCw size={14} className={isRefreshing ? "tix-spin" : ""} aria-hidden="true" />
              <span>{t("paymentV1.refresh", "Làm mới")}</span>
            </button>

            <Link className="tix-primary-cta-btn" to="/organizer/events/new">
              <PlusCircle size={15} aria-hidden="true" />
              <span>{t("organizerHub.createEvent", "Tạo sự kiện")}</span>
            </Link>
          </div>
        </div>

        {/* 3. KPI Summary Strip (100% số liệu thật từ API) */}
        <div className="tix-kpi-grid">
          <div className="tix-kpi-card">
            <div className="tix-kpi-top">
              <span className="tix-kpi-label">
                {t("organizerEvents.kpiTotalEvents", "Tổng sự kiện")}
              </span>
              <span className="tix-kpi-icon-circle" aria-hidden="true">
                <Calendar size={13} />
              </span>
            </div>
            <div className="tix-kpi-value-lg">
              {eventsQuery.isSuccess ? eventsList.length.toLocaleString(locale) : "—"}
            </div>
            <div className="tix-kpi-note">
              {eventsQuery.isSuccess
                ? t("organizerEvents.kpiTotalBreakdown", {
                    draft: statusCounts.draft,
                    cancelled: statusCounts.cancelled,
                    defaultValue: `${statusCounts.draft} bản nháp · ${statusCounts.cancelled} đã hủy`,
                  })
                : t("organizerHub.dataLoading")}
            </div>
          </div>

          <div className="tix-kpi-card">
            <div className="tix-kpi-top">
              <span className="tix-kpi-label">
                {t("organizerEvents.kpiPublishedEvents", "Đã công bố")}
              </span>
              <span className="tix-kpi-icon-circle" aria-hidden="true">
                <CheckCircle2 size={13} />
              </span>
            </div>
            <div className="tix-kpi-value-lg">
              {eventsQuery.isSuccess ? statusCounts.published.toLocaleString(locale) : "—"}
            </div>
            <div className="tix-kpi-note">
              {eventsQuery.isSuccess
                ? t("organizerEvents.kpiApprovedNote", {
                    count: statusCounts.approved,
                    defaultValue: `${statusCounts.approved} đã duyệt sẵn sàng xuất bản`,
                  })
                : t("organizerHub.dataLoading")}
            </div>
          </div>

          <div className="tix-kpi-card">
            <div className="tix-kpi-top">
              <span className="tix-kpi-label">
                {t("organizerEvents.kpiPendingEvents", "Chờ phê duyệt")}
              </span>
              <span className="tix-kpi-icon-circle" aria-hidden="true">
                <Clock size={13} />
              </span>
            </div>
            <div className="tix-kpi-value-lg">
              {eventsQuery.isSuccess ? statusCounts.pending.toLocaleString(locale) : "—"}
            </div>
            <div className="tix-kpi-note">
              {t("organizerEvents.kpiPendingNote", "Đang chờ Ban quản trị kiểm duyệt")}
            </div>
          </div>

          <div className="tix-kpi-card">
            <div className="tix-kpi-top">
              <span className="tix-kpi-label">
                {t("organizerEvents.kpiAllTimeSales", "Tổng vé đã bán")}
              </span>
              <span className="tix-kpi-icon-circle" aria-hidden="true">
                <Ticket size={13} />
              </span>
            </div>
            <div className="tix-kpi-value-lg">
              {eventSalesQuery.isSuccess ? (
                <>
                  {salesTotals.totalTickets.toLocaleString(locale)}
                  <span className="tix-kpi-unit">{t("organizerHub.unitTickets", "vé")}</span>
                </>
              ) : (
                "—"
              )}
            </div>
            <div className="tix-kpi-note">
              {eventSalesQuery.isSuccess
                ? t("organizerEvents.kpiAllTimeRevenueNote", {
                    revenue: salesTotals.totalRevenue,
                    defaultValue: `Doanh thu: ${salesTotals.totalRevenue}`,
                  })
                : eventSalesQuery.isError
                ? t("organizerHub.dataLoadFailed")
                : t("organizerHub.dataLoading")}
            </div>
          </div>
        </div>

        {/* 4. Main Events Table Card */}
        <div className="tix-orders-card">
          <div className="tix-orders-toolbar">
            <div>
              <h3 className="tix-card-title">
                {t("organizerEvents.listTitle", "Danh sách sự kiện")}
              </h3>
              <p className="tix-card-subtitle">
                {eventsQuery.isSuccess
                  ? t("organizerEvents.tableSubtitle", {
                      shown: filteredEvents.length,
                      total: eventsList.length,
                      defaultValue: `Hiển thị ${filteredEvents.length} / ${eventsList.length} sự kiện với số liệu bán vé và trạng thái thực tế`,
                    })
                  : t(
                      "organizerEvents.description",
                      "Danh sách sự kiện, trạng thái thiết lập và thao tác vận hành."
                    )}
              </p>
            </div>

            <div className="tix-orders-actions">
              <div className="tix-search-input-wrap">
                <Search size={15} aria-hidden="true" />
                <input
                  type="search"
                  aria-label={t("organizerEvents.searchAria", "Tìm kiếm sự kiện")}
                  placeholder={t("organizerHub.searchPlaceholder", "Tìm theo tên sự kiện...")}
                  value={searchQuery}
                  onChange={(e) => {
                    setSearchQuery(e.target.value);
                    setTablePage(0);
                  }}
                  className="tix-search-input"
                />
                {searchQuery && (
                  <button
                    type="button"
                    className="tix-search-clear-btn"
                    onClick={() => {
                      setSearchQuery("");
                      setTablePage(0);
                    }}
                    title={t("organizerHub.clearSearch", "Xóa từ khóa tìm kiếm")}
                    aria-label={t("organizerHub.clearSearch", "Xóa từ khóa tìm kiếm")}
                  >
                    <X size={13} />
                  </button>
                )}
              </div>

              {/* Status Filter Dropdown */}
              <div
                className={`tix-dropdown-wrap ${isStatusDropdownOpen ? "is-open" : ""}`}
                ref={statusDropdownRef}
              >
                <button
                  type="button"
                  className="tix-filter-select-btn"
                  disabled={mutation.isPending}
                  onClick={() => {
                    setIsStatusDropdownOpen((prev) => !prev);
                  }}
                  aria-expanded={isStatusDropdownOpen}
                  aria-haspopup="listbox"
                >
                  <span>{getStatusLabel(statusFilter)}</span>
                  <ChevronDown
                    size={14}
                    className={`tix-chevron-icon ${isStatusDropdownOpen ? "rotated" : ""}`}
                  />
                </button>

                {isStatusDropdownOpen && (
                  <div className="tix-dropdown-menu tix-dropdown-menu--status" role="listbox">
                    {(["ALL", ...organizerEventStatuses] as const).map((opt) => (
                      <button
                        key={opt}
                        type="button"
                        role="option"
                        aria-selected={statusFilter === opt}
                        className={`tix-dropdown-item ${statusFilter === opt ? "is-active" : ""}`}
                        onClick={() => {
                          setStatusFilter(opt);
                          setTablePage(0);
                          setIsStatusDropdownOpen(false);
                        }}
                      >
                        <span>{getStatusLabel(opt)}</span>
                        {statusFilter === opt && <Check size={14} className="tix-check-icon" />}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>

          {eventsQuery.isError && (
            <div className="tix-events-error-box" role="alert">
              <span>{t("organizerEvents.loadFailed")}</span>
              <button
                type="button"
                className="tix-inline-action-btn"
                disabled={eventsQuery.isFetching}
                onClick={() => void eventsQuery.refetch()}
              >
                {t("profile.retry", "Thử lại")}
              </button>
            </div>
          )}

          <div className="tix-table-wrapper">
            <table className="tix-orders-table">
              <thead>
                <tr>
                  <th scope="col">{t("organizerHub.colEventName", "Tên sự kiện")}</th>
                  <th scope="col">{t("organizerHub.colStatus", "Trạng thái")}</th>
                  <th scope="col">{t("organizerHub.colMode", "Hình thức")}</th>
                  <th scope="col">{t("organizerHub.colTime", "Thời gian")}</th>
                  <th scope="col">{t("organizerHub.colSoldTickets", "Vé đã bán")}</th>
                  <th scope="col">{t("organizerHub.colRevenue", "Doanh thu")}</th>
                  <th scope="col" style={{ textAlign: "right" }}>
                    {t("organizerHub.colActions", "Thao tác")}
                  </th>
                </tr>
              </thead>
              <tbody>
                {eventsQuery.isPending ? (
                  <tr>
                    <td
                      colSpan={7}
                      style={{
                        textAlign: "center",
                        padding: "48px 16px",
                        color: "var(--text-subtle, #565e69)",
                        fontSize: "14px",
                      }}
                    >
                      {t("organizerEvents.listLoading", "Đang tải danh sách sự kiện...")}
                    </td>
                  </tr>
                ) : paginatedEvents.length === 0 ? (
                  <tr>
                    <td
                      colSpan={7}
                      style={{
                        textAlign: "center",
                        padding: "48px 16px",
                        color: "var(--text-subtle, #565e69)",
                        fontSize: "14px",
                      }}
                    >
                      {eventsList.length === 0 ? (
                        <div
                          style={{
                            display: "flex",
                            flexDirection: "column",
                            alignItems: "center",
                            gap: "8px",
                          }}
                        >
                          <Calendar size={32} color="var(--text-muted, #666b74)" />
                          <span>
                            {t(
                              "organizerHub.emptyEvents",
                              "Bạn chưa tạo sự kiện nào. Bắt đầu ngay bằng cách tạo sự kiện đầu tiên!"
                            )}
                          </span>
                          <Link
                            to="/organizer/events/new"
                            className="tix-primary-cta-btn"
                            style={{ marginTop: "8px" }}
                          >
                            <PlusCircle size={15} />
                            <span>{t("organizerHub.createEventNow", "Tạo sự kiện ngay")}</span>
                          </Link>
                        </div>
                      ) : (
                        t(
                          "organizerHub.noMatchingEvents",
                          "Không tìm thấy sự kiện nào phù hợp với bộ lọc."
                        )
                      )}
                    </td>
                  </tr>
                ) : (
                  paginatedEvents.map((event, index) => {
                    const sales = eventSalesMap.get(event.eventId);
                    const isFuture = Date.parse(event.startsAt) > Date.now();
                    const cancellationState = cancellationStatusMap.get(event.eventId);
                    const cancellationStatus = cancellationState?.status;

                    return (
                      <tr key={event.eventId}>
                        <td>
                          <div className="tix-event-cell">
                            <span className="tix-event-title-main" title={event.title}>
                              {event.title}
                            </span>
                            <span className="tix-event-id-sub">
                              ID: {event.eventId.slice(0, 8)}...
                            </span>
                          </div>
                        </td>

                        <td>
                          <div className="tix-status-stack">
                            <span
                              className={`tix-status-badge ${getStatusBadgeClass(event.status)}`}
                              data-status={event.status}
                            >
                              {getStatusLabel(event.status)}
                            </span>
                            {event.status === "PUBLISHED" && cancellationStatus === "pending" && (
                              <span className="tix-cancel-pending-pill">
                                <Clock size={11} aria-hidden="true" />
                                {t("organizerEvents.cancellationPending", "Đang chờ Admin duyệt hủy")}
                              </span>
                            )}
                            {event.status === "PUBLISHED" && cancellationStatus === "loading" && (
                              <span className="tix-cancel-checking-pill">
                                <Clock size={11} aria-hidden="true" />
                                {t("organizerEvents.cancellationStatusChecking", "Đang kiểm tra trạng thái hủy...")}
                              </span>
                            )}
                            {event.status === "PUBLISHED" && cancellationStatus === "error" && (
                              <button
                                type="button"
                                className="tix-cancel-error-pill"
                                onClick={() => cancellationState?.refetch()}
                                title={t("organizerEvents.cancellationStatusRetry", "Kiểm tra lại trạng thái yêu cầu hủy")}
                              >
                                <AlertCircle size={11} aria-hidden="true" />
                                <span>
                                  {t("organizerEvents.cancellationStatusError", "Không tải được trạng thái hủy · Thử lại")}
                                </span>
                              </button>
                            )}
                          </div>
                        </td>

                        <td>
                          <span className="tix-mode-pill">
                            {event.mode === "SEAT_MAP"
                              ? t("organizerHub.modeSeatMap", "Sơ đồ ghế")
                              : t("organizerHub.modeQuantity", "Vé số lượng")}
                          </span>
                        </td>

                        <td>
                          <div className="tix-schedule-range">
                            <time dateTime={event.startsAt}>
                              {formatSchedule(event.startsAt, locale)}
                            </time>
                            <time dateTime={event.endsAt} className="tix-schedule-end">
                              {formatSchedule(event.endsAt, locale)}
                            </time>
                          </div>
                        </td>

                        <td>
                          <span className="tix-sold-count">
                            {eventSalesQuery.isPending
                              ? "—"
                              : (sales?.soldTicketCount ?? 0).toLocaleString(locale)}
                          </span>
                        </td>

                        <td>
                          <strong className="tix-revenue-text">
                            {eventSalesQuery.isPending
                              ? "—"
                              : formatMoney(
                                  sales?.soldRevenue ?? "0",
                                  eventSalesQuery.data?.currency || "VND"
                                )}
                          </strong>
                        </td>

                        <td style={{ textAlign: "right" }}>
                          <div className="tix-row-actions-group">
                            {renderPrimaryQuickAction(event)}

                            <div
                              className="tix-row-action-wrap"
                              ref={openActionMenuId === event.eventId ? actionMenuRef : null}
                            >
                              <button
                                type="button"
                                className={`tix-row-action-trigger ${
                                  openActionMenuId === event.eventId ? "is-open" : ""
                                }`}
                                onClick={() =>
                                  setOpenActionMenuId((prev) =>
                                    prev === event.eventId ? null : event.eventId
                                  )
                                }
                                title={t("organizerHub.actionOptions", "Tùy chọn thao tác")}
                                aria-label={t("organizerHub.actionOptions", "Tùy chọn thao tác")}
                                aria-haspopup="true"
                                aria-expanded={openActionMenuId === event.eventId}
                              >
                                <MoreHorizontal size={16} />
                              </button>

                              {openActionMenuId === event.eventId && (
                                <div
                                  className={`tix-row-action-menu ${
                                    index >= paginatedEvents.length - 2 &&
                                    paginatedEvents.length >= 3
                                      ? "open-up"
                                      : ""
                                  }`}
                                  role="menu"
                                >
                                  {event.status === "DRAFT" && event.mode === "SEAT_MAP" && (
                                    <>
                                      <button
                                        type="button"
                                        role="menuitem"
                                        className="tix-action-menu-item"
                                        onClick={() => {
                                          setOpenActionMenuId(null);
                                          navigate(
                                            `/organizer/events/${event.eventId}/configure-seating`
                                          );
                                        }}
                                      >
                                        <Armchair size={14} />
                                        <span>
                                          {t("organizerEvents.configureSeating", "Cấu hình sơ đồ")}
                                        </span>
                                      </button>

                                      <button
                                        type="button"
                                        role="menuitem"
                                        className="tix-action-menu-item"
                                        onClick={() => {
                                          setOpenActionMenuId(null);
                                          navigate(`/organizer/events/${event.eventId}/seat-map`);
                                        }}
                                      >
                                        <Armchair size={14} />
                                        <span>
                                          {t("organizerHub.actionManageSeatMap", "Quản lý sơ đồ ghế")}
                                        </span>
                                      </button>
                                    </>
                                  )}

                                  {event.status === "PUBLISHED" && (
                                    <button
                                      type="button"
                                      role="menuitem"
                                      className="tix-action-menu-item"
                                      onClick={() => {
                                        setOpenActionMenuId(null);
                                        navigate(`/organizer/check-in?eventId=${event.eventId}`);
                                      }}
                                    >
                                      <QrCode size={14} />
                                      <span>
                                        {t("organizerEvents.checkInTickets", "Check-in vé")}
                                      </span>
                                    </button>
                                  )}

                                  <button
                                    type="button"
                                    role="menuitem"
                                    className="tix-action-menu-item"
                                    onClick={(e) => handleCopyId(event.eventId, e)}
                                  >
                                    {copiedId === event.eventId ? (
                                      <>
                                        <Check size={14} color="#10b981" />
                                        <span style={{ color: "#10b981", fontWeight: 600 }}>
                                          {t("organizerHub.copiedId", "Đã sao chép mã!")}
                                        </span>
                                      </>
                                    ) : (
                                      <>
                                        <Copy size={14} />
                                        <span>
                                          {t("organizerHub.actionCopyId", "Sao chép ID sự kiện")}
                                        </span>
                                      </>
                                    )}
                                  </button>

                                  {["DRAFT", "PENDING_APPROVAL", "APPROVED"].includes(
                                    event.status
                                  ) &&
                                    isFuture && (
                                      <>
                                        <div className="tix-action-menu-separator" />
                                        <button
                                          type="button"
                                          role="menuitem"
                                          className="tix-action-menu-item is-danger"
                                          disabled={mutation.isPending || eventsQuery.isFetching}
                                          onClick={() => {
                                            setOpenActionMenuId(null);
                                            void act(event, "cancel");
                                          }}
                                        >
                                          <X size={14} />
                                          <span>{t("eventsV1.cancel", "Hủy sự kiện")}</span>
                                        </button>
                                      </>
                                    )}

                                  {event.status === "PUBLISHED" &&
                                    cancellationStatus === "none" &&
                                    isFuture && (
                                      <>
                                        <div className="tix-action-menu-separator" />
                                        <button
                                          type="button"
                                          role="menuitem"
                                          className="tix-action-menu-item is-danger"
                                          disabled={mutation.isPending || eventsQuery.isFetching}
                                          onClick={() => {
                                            setOpenActionMenuId(null);
                                            void handleRequestCancellation(event);
                                          }}
                                        >
                                          <X size={14} />
                                          <span>
                                            {t("organizerEvents.requestCancel", "Yêu cầu hủy sự kiện")}
                                          </span>
                                        </button>
                                      </>
                                    )}

                                  {event.status === "PUBLISHED" &&
                                    cancellationStatus === "error" && (
                                      <>
                                        <div className="tix-action-menu-separator" />
                                        <button
                                          type="button"
                                          role="menuitem"
                                          className="tix-action-menu-item"
                                          onClick={() => {
                                            setOpenActionMenuId(null);
                                            cancellationState?.refetch();
                                          }}
                                        >
                                          <RefreshCw size={14} />
                                          <span>
                                            {t(
                                              "organizerEvents.cancellationStatusRetry",
                                              "Kiểm tra lại trạng thái yêu cầu hủy"
                                            )}
                                          </span>
                                        </button>
                                      </>
                                    )}
                                </div>
                              )}
                            </div>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="tix-pagination-wrapper">
              <div className="tix-pagination-box">
                <button
                  type="button"
                  className="tix-page-arrow-btn"
                  disabled={safePage === 0}
                  onClick={() => setTablePage((p) => Math.max(0, p - 1))}
                  aria-label={t("organizerHub.prevPage", "Trang trước")}
                >
                  <ChevronLeft size={15} />
                </button>

                <span className="tix-page-indicator">
                  {String(safePage + 1).padStart(2, "0")}/{String(totalPages).padStart(2, "0")}
                </span>

                <button
                  type="button"
                  className="tix-page-arrow-btn"
                  disabled={safePage >= totalPages - 1}
                  onClick={() => setTablePage((p) => Math.min(totalPages - 1, p + 1))}
                  aria-label={t("organizerHub.nextPage", "Trang sau")}
                >
                  <ChevronRight size={15} />
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default function OrganizerEventsPage() {
  const { ready } = useOrganizerGate();
  const { keycloak } = useKeycloak();
  const subject = keycloak.tokenParsed?.sub;

  return (
    <OrganizerLayout
      hideSidebar
      hideHeader
      requireOrganizer
      className="organizer-hub-page"
    >
      {ready && subject && (
        <EventManagementWorkspace
          key={`${keycloak.tokenParsed?.iss}:${subject}`}
          subject={subject}
          issuer={keycloak.tokenParsed?.iss}
        />
      )}
    </OrganizerLayout>
  );
}
