import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useKeycloak } from "@react-keycloak/web";
import { useQueries, useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  ArrowDownUp,
  BarChart3,
  Building2,
  Calendar,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Clock,
  Copy,
  Download,
  LoaderCircle,
  Scale,
  Search,
  ShieldAlert,
  ShoppingBag,
  Ticket,
  X,
} from "lucide-react";
import { toast } from "react-toastify";

import TixAdminTopNav from "../../components/admin/TixAdminTopNav";
import { hasRealmRole } from "../../lib/auth";
import {
  adminSalesService,
  type AdminEventSalesItem,
  type AdminSalesSortBy,
  type OrderState,
  type SalesGrouping,
  type SortDirection,
} from "../../services/adminSalesService";
import { organizerService, type OrganizerProfile } from "../../services/organizerService";
import "../Organizer/organizer-dashboard.css";
import "./admin-tix.css";

const REPORT_TIME_ZONE = "Asia/Ho_Chi_Minh";
const reportCalendar = new Intl.DateTimeFormat("sv-SE", {
  timeZone: REPORT_TIME_ZONE,
  dateStyle: "short",
});

type DateRangeKey = "today" | "7d" | "30d" | "this_month" | "90d";
type ChartMetric = "tickets" | "revenue";

function computePresetRange(days: number | "month"): { from: string; to: string } {
  const to = reportCalendar.format(new Date());
  if (days === "month") {
    return { from: `${to.slice(0, 7)}-01`, to };
  }
  const first = new Date(`${to}T00:00:00Z`);
  first.setUTCDate(first.getUTCDate() - (days - 1));
  return { from: first.toISOString().slice(0, 10), to };
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

function formatCompactAxisValue(value: number, metric: ChartMetric): string {
  if (metric === "tickets") {
    if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1).replace(/\.0$/, "")}M`;
    if (value >= 1_000) return `${(value / 1_000).toFixed(1).replace(/\.0$/, "")}K`;
    return String(Math.round(value));
  }
  if (value >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(1).replace(/\.0$/, "")}B`;
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1).replace(/\.0$/, "")}M`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(0)}K`;
  return String(Math.round(value));
}

function formatDateTime(iso: string | null | undefined, locale = "vi-VN"): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString(locale, {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatShortPeriod(periodStart: string, periodEnd: string, groupBy: SalesGrouping): string {
  const [, sm, sd] = periodStart.split("-");
  if (!sm || !sd) return periodStart;
  if (groupBy === "MONTH") {
    return `${sm}/${periodStart.slice(0, 4)}`;
  }
  if (groupBy === "WEEK") {
    const [, em, ed] = periodEnd.split("-");
    return em && ed ? `${sd}/${sm}-${ed}/${em}` : `${sd}/${sm}`;
  }
  return `${sd}/${sm}`;
}

function formatFullPeriod(periodStart: string, periodEnd: string): string {
  const formatDay = (s: string) => {
    const [y, m, d] = s.split("-");
    return y && m && d ? `${d}/${m}/${y}` : s;
  };
  if (periodStart === periodEnd) return formatDay(periodStart);
  return `${formatDay(periodStart)} – ${formatDay(periodEnd)}`;
}

function shortId(value: string): string {
  if (!value) return "—";
  return value.length > 16 ? `${value.slice(0, 8)}…${value.slice(-4)}` : value;
}

const PRESET_OPTIONS: { key: DateRangeKey; vi: string; en: string; days: number | "month" }[] = [
  { key: "this_month", vi: "Tháng này", en: "This month", days: "month" },
  { key: "7d", vi: "7 ngày qua", en: "Last 7 days", days: 7 },
  { key: "30d", vi: "30 ngày qua", en: "Last 30 days", days: 30 },
  { key: "today", vi: "Hôm nay", en: "Today", days: 1 },
  { key: "90d", vi: "90 ngày qua", en: "Last 90 days", days: 90 },
];

const GROUP_BY_OPTIONS: { key: SalesGrouping; vi: string; en: string }[] = [
  { key: "DAY", vi: "Theo ngày", en: "Daily" },
  { key: "WEEK", vi: "Theo tuần", en: "Weekly" },
  { key: "MONTH", vi: "Theo tháng", en: "Monthly" },
];

const SORT_OPTIONS: { key: AdminSalesSortBy; vi: string; en: string }[] = [
  { key: "soldRevenue", vi: "Doanh thu", en: "Revenue" },
  { key: "soldTicketCount", vi: "Vé đã bán", en: "Sold tickets" },
  { key: "soldOrderCount", vi: "Đơn hoàn tất", en: "Sold orders" },
];

const TABLE_PAGE_SIZE = 10;

export function AdminSalesPage() {
  const { t, i18n } = useTranslation();
  const { keycloak, initialized } = useKeycloak();

  const isVi = !i18n.resolvedLanguage?.startsWith("en");
  const locale = isVi ? "vi-VN" : "en-US";
  const copyText = (vi: string, en: string) => (isVi ? vi : en);

  const [dateRangeKey, setDateRangeKey] = useState<DateRangeKey>("this_month");

  const [groupBy, setGroupBy] = useState<SalesGrouping>("DAY");
  const [chartMetric, setChartMetric] = useState<ChartMetric>("tickets");
  const [hoveredIdx, setHoveredIdx] = useState<number | null>(null);

  const [sortBy, setSortBy] = useState<AdminSalesSortBy>("soldRevenue");
  const [direction, setDirection] = useState<SortDirection>("DESC");
  const [tablePage, setTablePage] = useState(0);
  const [searchQuery, setSearchQuery] = useState("");
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const [isDateDropdownOpen, setIsDateDropdownOpen] = useState(false);
  const [isSortDropdownOpen, setIsSortDropdownOpen] = useState(false);

  const dateDropdownRef = useRef<HTMLDivElement>(null);
  const sortDropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!copiedId) return;
    const timer = window.setTimeout(() => setCopiedId(null), 1500);
    return () => window.clearTimeout(timer);
  }, [copiedId]);

  useEffect(() => {
    const onDocClick = (e: MouseEvent) => {
      const target = e.target as Node;
      if (dateDropdownRef.current && !dateDropdownRef.current.contains(target)) {
        setIsDateDropdownOpen(false);
      }
      if (sortDropdownRef.current && !sortDropdownRef.current.contains(target)) {
        setIsSortDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, []);

  const activeRange = useMemo(() => {
    const preset = PRESET_OPTIONS.find((o) => o.key === dateRangeKey) ?? PRESET_OPTIONS[0];
    return computePresetRange(preset.days);
  }, [dateRangeKey]);

  const isAuthenticated = Boolean(keycloak.authenticated);
  const isAdmin = hasRealmRole(keycloak.tokenParsed, "ADMIN");
  const canQuery = initialized && isAuthenticated && isAdmin;

  const summaryQuery = useQuery({
    queryKey: ["admin-sales-summary", activeRange.from, activeRange.to],
    enabled: canQuery,
    queryFn: ({ signal }) => adminSalesService.getSalesSummary(activeRange, signal),
  });

  const chartQuery = useQuery({
    queryKey: ["admin-sales-chart", activeRange.from, activeRange.to, groupBy],
    enabled: canQuery,
    queryFn: ({ signal }) =>
      adminSalesService.getSalesChart({ ...activeRange, groupBy }, signal),
  });

  const topEventsQuery = useQuery({
    queryKey: ["admin-sales-top-events", activeRange.from, activeRange.to],
    enabled: canQuery,
    queryFn: ({ signal }) =>
      adminSalesService.getEventSalesStatistics(
        {
          ...activeRange,
          page: 0,
          size: 4,
          sortBy: "soldRevenue",
          direction: "DESC",
        },
        signal,
      ),
  });

  const tableQuery = useQuery({
    queryKey: [
      "admin-sales-events-table",
      activeRange.from,
      activeRange.to,
      tablePage,
      TABLE_PAGE_SIZE,
      sortBy,
      direction,
    ],
    enabled: canQuery,
    queryFn: ({ signal }) =>
      adminSalesService.getEventSalesStatistics(
        {
          ...activeRange,
          page: tablePage,
          size: TABLE_PAGE_SIZE,
          sortBy,
          direction,
        },
        signal,
      ),
  });

  const stateCounts = useMemo(() => {
    const map = new Map<OrderState, number>();
    for (const row of summaryQuery.data?.byState ?? []) {
      map.set(row.state, row.orderCount);
    }
    return {
      completed: map.get("COMPLETED") ?? 0,
      issuing: map.get("ISSUING") ?? 0,
      pendingPayment: map.get("PENDING_PAYMENT") ?? 0,
      cancelledOrFailed:
        (map.get("CANCELLED") ?? 0) +
        (map.get("EXPIRED") ?? 0) +
        (map.get("ISSUANCE_FAILED") ?? 0),
    };
  }, [summaryQuery.data]);

  const topEvents = useMemo(
    () =>
      (topEventsQuery.data?.items ?? []).filter(
        (evt) =>
          evt.soldTicketCount > 0 || evt.soldOrderCount > 0 || Number(evt.soldRevenue) > 0,
      ),
    [topEventsQuery.data],
  );

  const uniqueOrganizerSubjects = useMemo(
    () =>
      Array.from(
        new Set(
          (tableQuery.data?.items ?? [])
            .map((evt) => evt.organizerSubject)
            .filter(Boolean),
        ),
      ),
    [tableQuery.data],
  );

  const organizerQueries = useQueries({
    queries: uniqueOrganizerSubjects.map((slug) => ({
      queryKey: ["admin-sales-organizer", slug],
      enabled: canQuery,
      queryFn: ({ signal }: { signal?: AbortSignal }) =>
        organizerService.getPublicOrganizerBySlug(slug, signal),
      staleTime: 5 * 60 * 1000,
      retry: false,
    })),
  });

  const organizerMap = useMemo(() => {
    const map = new Map<string, OrganizerProfile>();
    uniqueOrganizerSubjects.forEach((slug, idx) => {
      const profile = organizerQueries[idx]?.data;
      if (profile) {
        map.set(slug, profile);
      }
    });
    return map;
  }, [uniqueOrganizerSubjects, organizerQueries]);

  const normalizedSearch = searchQuery.trim().toLowerCase();
  const visibleRows = useMemo(() => {
    const items = tableQuery.data?.items ?? [];
    if (!normalizedSearch) return items;
    return items.filter((evt) => {
      const orgName = organizerMap.get(evt.organizerSubject)?.name?.toLowerCase() ?? "";
      return (
        evt.eventTitle.toLowerCase().includes(normalizedSearch) ||
        evt.eventId.toLowerCase().includes(normalizedSearch) ||
        evt.organizerSubject.toLowerCase().includes(normalizedSearch) ||
        orgName.includes(normalizedSearch)
      );
    });
  }, [tableQuery.data, normalizedSearch, organizerMap]);

  // SVG Chart geometry (aligned with OrganizerHubPage)
  const chartWidth = 720;
  const chartHeight = 250;
  const chartPadding = { top: 20, right: 20, bottom: 32, left: 56 };
  const plotWidth = chartWidth - chartPadding.left - chartPadding.right;
  const plotHeight = chartHeight - chartPadding.top - chartPadding.bottom;
  const baselineY = chartPadding.top + plotHeight;

  const chartItems = chartQuery.data?.items ?? [];
  const chartValues = useMemo(
    () =>
      chartItems.map((item) =>
        chartMetric === "tickets" ? item.soldTicketCount : Number(item.soldRevenue || 0),
      ),
    [chartItems, chartMetric],
  );
  const rawMax = useMemo(() => Math.max(0, ...chartValues), [chartValues]);
  const yMax = useMemo(() => {
    if (rawMax <= 0) return chartMetric === "tickets" ? 10 : 100000;
    const step = Math.pow(10, Math.floor(Math.log10(rawMax)));
    return Math.ceil((rawMax * 1.15) / step) * step;
  }, [rawMax, chartMetric]);

  const yTicks = useMemo(() => {
    const steps = 4;
    return Array.from({ length: steps + 1 }, (_, i) => {
      const val = (yMax / steps) * (steps - i);
      const y = chartPadding.top + (plotHeight / steps) * i;
      return { val, y };
    });
  }, [yMax, plotHeight, chartPadding.top]);

  const chartPoints = useMemo(() => {
    const n = chartItems.length;
    if (n === 0) return [];
    return chartItems.map((item, idx) => {
      const value =
        chartMetric === "tickets" ? item.soldTicketCount : Number(item.soldRevenue || 0);
      const x =
        n === 1
          ? chartPadding.left + plotWidth / 2
          : chartPadding.left + (idx / (n - 1)) * plotWidth;
      const y = baselineY - (yMax > 0 ? (value / yMax) * plotHeight : 0);
      return { x, y, item, value };
    });
  }, [chartItems, chartMetric, plotWidth, plotHeight, baselineY, yMax, chartPadding.left]);

  const linePath = useMemo(() => {
    if (chartPoints.length === 0) return "";
    return chartPoints
      .map((p, i) => `${i === 0 ? "M" : "L"} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`)
      .join(" ");
  }, [chartPoints]);

  const areaPath = useMemo(() => {
    if (chartPoints.length === 0) return "";
    const first = chartPoints[0];
    const last = chartPoints[chartPoints.length - 1];
    return `${linePath} L ${last.x.toFixed(1)} ${baselineY} L ${first.x.toFixed(1)} ${baselineY} Z`;
  }, [chartPoints, linePath, baselineY]);

  const hoveredPoint =
    hoveredIdx !== null && hoveredIdx >= 0 && hoveredIdx < chartPoints.length
      ? chartPoints[hoveredIdx]
      : null;

  const handleChartMouseMove = (e: React.MouseEvent<SVGSVGElement>) => {
    if (chartPoints.length === 0) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const relX = ((e.clientX - rect.left) / rect.width) * chartWidth;
    let closest = 0;
    let minDist = Infinity;
    chartPoints.forEach((p, i) => {
      const d = Math.abs(p.x - relX);
      if (d < minDist) {
        minDist = d;
        closest = i;
      }
    });
    setHoveredIdx(closest);
  };

  const handleCopy = async (value: string, label: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopiedId(value);
      toast.success(copyText(`Đã sao chép ${label}`, `Copied ${label}`));
    } catch {
      toast.error(copyText("Không thể sao chép vào clipboard.", "Could not copy to clipboard."));
    }
  };

  const getActiveRangeLabel = () => {
    const found = PRESET_OPTIONS.find((o) => o.key === dateRangeKey);
    return found ? (isVi ? found.vi : found.en) : copyText("Tháng này", "This month");
  };

  const getEventStatusLabel = (status: AdminEventSalesItem["eventStatus"]) => {
    switch (status) {
      case "PUBLISHED":
        return copyText("Đang mở bán", "Published");
      case "CANCELLED":
        return copyText("Đã hủy", "Cancelled");
      case "APPROVED":
        return copyText("Đã duyệt", "Approved");
      case "PENDING_APPROVAL":
        return copyText("Chờ duyệt", "Pending");
      case "DRAFT":
        return copyText("Bản nháp", "Draft");
      default:
        return status;
    }
  };

  const handleExportCsv = () => {
    if (visibleRows.length === 0) {
      toast.info(
        copyText(
          "Không có dữ liệu doanh số sự kiện trên trang hiện tại để xuất CSV.",
          "No event sales rows on current page to export.",
        ),
      );
      return;
    }

    const esc = (v: string | number | undefined | null) =>
      `"${String(v ?? "").replace(/"/g, '""')}"`;

    const headers = [
      copyText("Mã sự kiện", "Event ID"),
      copyText("Tên sự kiện", "Event Title"),
      copyText("Mã Ban tổ chức", "Organizer Subject"),
      copyText("Trạng thái", "Status"),
      copyText("Đơn hoàn tất", "Sold Orders"),
      copyText("Vé đã bán", "Sold Tickets"),
      copyText("Doanh thu", "Sold Revenue"),
      copyText("Từ ngày", "From"),
      copyText("Đến ngày", "To"),
    ];

    const rows = visibleRows.map((evt) => [
      esc(evt.eventId),
      esc(evt.eventTitle),
      esc(evt.organizerSubject),
      esc(getEventStatusLabel(evt.eventStatus)),
      evt.soldOrderCount,
      evt.soldTicketCount,
      esc(evt.soldRevenue),
      esc(activeRange.from),
      esc(activeRange.to),
    ]);

    const csv = [headers.map(esc).join(","), ...rows.map((r) => r.join(","))].join("\r\n");
    const blob = new Blob(["\uFEFF" + csv], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `admin_sales_${activeRange.from}_${activeRange.to}_p${tablePage + 1}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };

  if (!initialized) {
    return (
      <div className="tix-dashboard-page tix-admin-page">
        <div className="tix-dashboard-container">
          <TixAdminTopNav activeTab="sales" />
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
          <TixAdminTopNav activeTab="sales" />
          <div className="tix-orders-card tix-admin-state-box" role="alert">
            <ShieldAlert size={32} color="#dc2626" aria-hidden="true" />
            <strong style={{ color: "var(--text-strong, #15171a)" }}>
              {copyText("Truy cập bị từ chối", "Access Denied")}
            </strong>
            <span>
              {copyText(
                "Bạn cần đăng nhập với tài khoản Quản trị viên (ADMIN) để xem thống kê doanh số toàn hệ thống.",
                "You need an ADMIN account to view system-wide sales statistics.",
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

  const summary = summaryQuery.data;
  const currency = summary?.currency || tableQuery.data?.currency || "VND";
  const totalPages = Math.max(1, tableQuery.data?.totalPages ?? 1);
  const totalElements = tableQuery.data?.totalElements ?? 0;

  return (
    <div className="tix-dashboard-page tix-admin-page">
      <div className="tix-dashboard-container">
        <TixAdminTopNav activeTab="sales" />

        {/* 1. Overview Header */}
        <div className="tix-overview-header">
          <div className="tix-overview-header-left">
            <div className="tix-overview-title-block">
              <h1 className="tix-overview-main-title">
                {copyText("Thống kê doanh số toàn hệ thống", "System-Wide Sales Statistics")}
              </h1>
              <p className="tix-overview-subtitle">
                {copyText(
                  "Theo dõi tổng doanh thu, sản lượng vé, trạng thái đơn hàng và hiệu quả kinh doanh theo từng sự kiện trên toàn nền tảng.",
                  "Monitor total revenue, ticket volume, order states, and per-event performance across the platform.",
                )}
              </p>
            </div>

            <div className="tix-overview-badges-row">
              <span className="tix-overview-badge">
                <Calendar size={13} aria-hidden="true" />
                <span>
                  {tableQuery.isSuccess
                    ? copyText(`${totalElements} sự kiện trong thống kê`, `${totalElements} tracked events`)
                    : copyText("Đang tải số liệu...", "Loading stats...")}
                </span>
              </span>
              {summary?.asOf && (
                <span className="tix-overview-badge">
                  <Clock size={13} aria-hidden="true" />
                  <span>
                    {copyText(
                      `Cập nhật lúc ${formatDateTime(summary.asOf, locale)}`,
                      `Updated ${formatDateTime(summary.asOf, locale)}`,
                    )}
                  </span>
                </span>
              )}
            </div>
          </div>

          <div className="tix-overview-header-right">
            <div className="tix-subtabs-actions">
              <div
                className={`tix-dropdown-wrap ${isDateDropdownOpen ? "is-open" : ""}`}
                ref={dateDropdownRef}
              >
                <button
                  type="button"
                  className="tix-date-select-btn"
                  onClick={() => setIsDateDropdownOpen((prev) => !prev)}
                  aria-expanded={isDateDropdownOpen}
                  aria-haspopup="listbox"
                >
                  <span className="tix-date-btn-content">
                    <Calendar size={14} aria-hidden="true" />
                    <span>{getActiveRangeLabel()}</span>
                  </span>
                  <ChevronDown
                    size={14}
                    className={`tix-chevron-icon ${isDateDropdownOpen ? "rotated" : ""}`}
                  />
                </button>

                {isDateDropdownOpen && (
                  <div className="tix-dropdown-menu" role="listbox">
                    {PRESET_OPTIONS.map((opt) => (
                      <button
                        key={opt.key}
                        type="button"
                        role="option"
                        aria-selected={dateRangeKey === opt.key}
                        className={`tix-dropdown-item ${dateRangeKey === opt.key ? "is-active" : ""}`}
                        onClick={() => {
                          setDateRangeKey(opt.key);
                          setTablePage(0);
                          setIsDateDropdownOpen(false);
                        }}
                      >
                        <span>{isVi ? opt.vi : opt.en}</span>
                        {dateRangeKey === opt.key && <Check size={14} className="tix-check-icon" />}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>

            <Link to="/admin/reconciliation" className="tix-primary-cta-btn">
              <Scale size={16} aria-hidden="true" />
              <span>{copyText("Đối soát & chi trả", "Reconciliation & Payout")}</span>
            </Link>
          </div>
        </div>

        {/* 2. 4 KPI Cards (matching OrganizerHubPage) */}
        <div className="tix-kpi-grid">
          <div className="tix-kpi-card">
            <div className="tix-kpi-top">
              <span className="tix-kpi-label">
                {copyText("Tổng doanh thu", "Total revenue")}
              </span>
              <div className="tix-kpi-icon-circle">
                <ShoppingBag size={13} aria-hidden="true" />
              </div>
            </div>
            <div className="tix-kpi-value-lg" role={summaryQuery.isError ? "alert" : undefined}>
              {summary
                ? formatMoney(summary.soldRevenue, currency, locale)
                : summaryQuery.isError
                ? copyText("Lỗi tải dữ liệu", "Load failed")
                : "—"}
            </div>
            <div className="tix-kpi-note">
              {copyText("Doanh số vé trong kỳ đã chọn", "Ticket sales in selected period")}
            </div>
          </div>

          <div className="tix-kpi-card">
            <div className="tix-kpi-top">
              <span className="tix-kpi-label">{copyText("Tổng vé đã bán", "Total tickets sold")}</span>
              <div className="tix-kpi-icon-circle">
                <Ticket size={13} aria-hidden="true" />
              </div>
            </div>
            <div className="tix-kpi-value-lg">
              {summary ? (
                <>
                  {summary.soldTicketCount.toLocaleString(locale)}{" "}
                  <small className="tix-kpi-unit">{copyText("vé", "tickets")}</small>
                </>
              ) : summaryQuery.isError ? (
                copyText("Lỗi", "Error")
              ) : (
                "—"
              )}
            </div>
            <div className="tix-kpi-note">
              {copyText("Số vé đã giao dịch thành công", "Successfully transacted tickets")}
            </div>
          </div>

          <div className="tix-kpi-card">
            <div className="tix-kpi-top">
              <span className="tix-kpi-label">
                {copyText("Đơn đã bán", "Sold orders")}
              </span>
              <div className="tix-kpi-icon-circle">
                <ShoppingBag size={13} aria-hidden="true" />
              </div>
            </div>
            <div className="tix-kpi-value-lg">
              {summary ? (
                <>
                  {summary.soldOrderCount.toLocaleString(locale)}{" "}
                  <small className="tix-kpi-unit">{copyText("đơn", "orders")}</small>
                </>
              ) : summaryQuery.isError ? (
                copyText("Lỗi", "Error")
              ) : (
                "—"
              )}
            </div>
            <div className="tix-kpi-note">
              {copyText("Giao dịch thanh toán thành công", "Completed payment transactions")}
            </div>
          </div>

          <div className="tix-kpi-card">
            <div className="tix-kpi-top">
              <span className="tix-kpi-label">
                {copyText("Trạng thái đơn", "Order status")}
              </span>
              <div className="tix-kpi-icon-circle">
                <BarChart3 size={13} aria-hidden="true" />
              </div>
            </div>
            <div className="tix-kpi-sub-stats tix-admin-kpi-sub-stats">
              <div className="tix-kpi-sub-item">
                <span>{copyText("Hoàn tất", "Completed")}</span>
                <span style={{ color: "var(--success, #16a34a)" }}>
                  {summary ? stateCounts.completed.toLocaleString(locale) : "—"}
                </span>
              </div>
              <div className="tix-kpi-sub-item">
                <span>{copyText("Đang xử lý", "Pending")}</span>
                <span>
                  {summary
                    ? (stateCounts.issuing + stateCounts.pendingPayment).toLocaleString(locale)
                    : "—"}
                </span>
              </div>
              <div className="tix-kpi-sub-item">
                <span>{copyText("Hủy / Lỗi", "Cancelled")}</span>
                <span style={{ color: "var(--text-subtle, #565e69)" }}>
                  {summary ? stateCounts.cancelledOrFailed.toLocaleString(locale) : "—"}
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* 3. Middle Grid: Interactive Area Chart + Top Events */}
        <div className="tix-middle-grid">
          <div className="tix-chart-card">
            <div className="tix-card-header tix-admin-chart-header">
              <div>
                <h3 className="tix-card-title">
                  {copyText("Biểu đồ doanh số theo thời gian", "Sales Over Time")}
                </h3>
                <p className="tix-card-subtitle">
                  {chartMetric === "tickets"
                    ? copyText(
                        "Số lượng vé bán ra trên toàn hệ thống theo chu kỳ đã chọn",
                        "Platform-wide ticket sales trend over selected period",
                      )
                    : copyText(
                        "Doanh số bán vé trên toàn hệ thống theo chu kỳ đã chọn",
                        "Platform-wide ticket sales revenue trend over selected period",
                      )}
                </p>
              </div>

              <div className="tix-admin-chart-controls">
                <div className="tix-admin-segmented" role="group" aria-label="Chỉ số biểu đồ">
                  <button
                    type="button"
                    className={chartMetric === "tickets" ? "is-active" : ""}
                    onClick={() => setChartMetric("tickets")}
                  >
                    {copyText("Vé bán", "Tickets")}
                  </button>
                  <button
                    type="button"
                    className={chartMetric === "revenue" ? "is-active" : ""}
                    onClick={() => setChartMetric("revenue")}
                  >
                    {copyText("Doanh thu", "Revenue")}
                  </button>
                </div>

                <div className="tix-admin-segmented" role="group" aria-label="Nhóm thời gian">
                  {GROUP_BY_OPTIONS.map((opt) => (
                    <button
                      key={opt.key}
                      type="button"
                      className={groupBy === opt.key ? "is-active" : ""}
                      onClick={() => setGroupBy(opt.key)}
                    >
                      {isVi ? opt.vi : opt.en}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {chartQuery.isPending ? (
              <div className="tix-admin-state-box" role="status">
                <LoaderCircle size={22} className="tix-spin" aria-hidden="true" />
                <span>{copyText("Đang tải biểu đồ...", "Loading chart...")}</span>
              </div>
            ) : chartQuery.isError ? (
              <div className="tix-admin-error-box" role="alert">
                <span>{copyText("Không thể tải dữ liệu biểu đồ.", "Could not load chart data.")}</span>
              </div>
            ) : chartItems.length === 0 ? (
              <div className="tix-admin-state-box">
                <span>{copyText("Không có dữ liệu trong khoảng thời gian này.", "No data in this range.")}</span>
              </div>
            ) : (
              <div className="tix-area-chart-container">
                <svg
                  viewBox={`0 0 ${chartWidth} ${chartHeight}`}
                  className="tix-area-chart-svg"
                  onMouseMove={handleChartMouseMove}
                  onMouseLeave={() => setHoveredIdx(null)}
                >
                  <defs>
                    <linearGradient id="tixAdminSalesAreaGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#c85a32" stopOpacity="0.32" />
                      <stop offset="60%" stopColor="#c85a32" stopOpacity="0.08" />
                      <stop offset="100%" stopColor="#c85a32" stopOpacity="0.0" />
                    </linearGradient>
                  </defs>

                  {yTicks.map((tick, i) => (
                    <g key={`y-${tick.val}-${i}`}>
                      <line
                        x1={chartPadding.left}
                        y1={tick.y}
                        x2={chartPadding.left + plotWidth}
                        y2={tick.y}
                        stroke="#f1f3f7"
                        strokeWidth="1"
                      />
                      <text
                        x={chartPadding.left - 10}
                        y={tick.y + 4}
                        textAnchor="end"
                        className="tix-chart-axis-label"
                      >
                        {formatCompactAxisValue(tick.val, chartMetric)}
                      </text>
                    </g>
                  ))}

                  {chartPoints
                    .filter((_, idx) => {
                      const n = chartPoints.length;
                      return (
                        n <= 10 ||
                        idx === 0 ||
                        idx === n - 1 ||
                        (n <= 31 && idx % 4 === 0) ||
                        (n > 31 && idx % 10 === 0)
                      );
                    })
                    .map((p, i) => (
                      <line
                        key={`vg-${i}`}
                        x1={p.x}
                        y1={chartPadding.top}
                        x2={p.x}
                        y2={baselineY}
                        stroke="#e2e8f0"
                        strokeDasharray="4 4"
                        strokeWidth="1"
                      />
                    ))}

                  <line
                    x1={chartPadding.left}
                    y1={baselineY}
                    x2={chartPadding.left + plotWidth}
                    y2={baselineY}
                    stroke="#e2e8f0"
                    strokeWidth="1"
                  />

                  {areaPath && (
                    <path
                      d={areaPath}
                      fill="url(#tixAdminSalesAreaGrad)"
                      className="tix-area-fill"
                    />
                  )}
                  {linePath && (
                    <path
                      d={linePath}
                      fill="none"
                      stroke="#c85a32"
                      strokeWidth="2.2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      className="tix-area-stroke"
                    />
                  )}

                  {chartPoints.map((p, idx) => {
                    const n = chartPoints.length;
                    const shouldShow =
                      n <= 10 ||
                      idx === 0 ||
                      idx === n - 1 ||
                      (n <= 31 && idx % 4 === 0) ||
                      (n > 31 && idx % 10 === 0);
                    if (!shouldShow) return null;
                    return (
                      <text
                        key={`${p.item.periodStart}-${idx}`}
                        x={p.x}
                        y={baselineY + 20}
                        textAnchor="middle"
                        className="tix-chart-axis-label"
                      >
                        {formatShortPeriod(p.item.periodStart, p.item.periodEnd, groupBy)}
                      </text>
                    );
                  })}

                  {hoveredPoint && (
                    <g className="tix-chart-hover-indicator">
                      <line
                        x1={hoveredPoint.x}
                        y1={chartPadding.top}
                        x2={hoveredPoint.x}
                        y2={baselineY}
                        stroke="#c85a32"
                        strokeDasharray="3 3"
                        strokeWidth="1.2"
                        opacity="0.6"
                      />
                      <circle
                        cx={hoveredPoint.x}
                        cy={hoveredPoint.y}
                        r="4.5"
                        fill="#ffffff"
                        stroke="#c85a32"
                        strokeWidth="2.5"
                      />
                    </g>
                  )}
                </svg>

                {hoveredPoint && (
                  <div
                    className="tix-chart-floating-tooltip"
                    style={{
                      left: `${(hoveredPoint.x / chartWidth) * 100}%`,
                      top: `${Math.max(5, (hoveredPoint.y / chartHeight) * 100 - 16)}%`,
                    }}
                  >
                    <div className="tix-tooltip-date">
                      {formatFullPeriod(
                        hoveredPoint.item.periodStart,
                        hoveredPoint.item.periodEnd,
                      )}
                    </div>
                    <div className="tix-tooltip-value">
                      <strong>{hoveredPoint.item.soldTicketCount.toLocaleString(locale)}</strong>{" "}
                      {copyText("vé", "tickets")} ·{" "}
                      <strong>{hoveredPoint.item.soldOrderCount.toLocaleString(locale)}</strong>{" "}
                      {copyText("đơn", "orders")}
                    </div>
                    <div className="tix-tooltip-revenue">
                      {formatMoney(
                        hoveredPoint.item.soldRevenue,
                        chartQuery.data?.currency || currency,
                        locale,
                      )}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          <div className="tix-tickets-type-card tix-top-events-card">
            <div className="tix-card-header">
              <div>
                <h3 className="tix-card-title">
                  {copyText("Sự kiện doanh thu cao nhất", "Top Revenue Events")}
                </h3>
                <p className="tix-card-subtitle">
                  {copyText(
                    "Xếp hạng theo doanh số bán vé trong khoảng thời gian",
                    "Ranked by ticket sales revenue in the selected range",
                  )}
                </p>
              </div>
            </div>

            <div className="tix-tickets-type-list">
              {topEventsQuery.isPending ? (
                <div className="tix-admin-state-box" role="status">
                  <LoaderCircle size={20} className="tix-spin" aria-hidden="true" />
                </div>
              ) : topEventsQuery.isError ? (
                <p role="alert">
                  {copyText("Không thể tải danh sách xếp hạng.", "Failed to load top events.")}
                </p>
              ) : topEvents.length === 0 ? (
                <div className="tix-top-events-empty">
                  <Ticket size={28} className="tix-empty-icon" aria-hidden="true" />
                  <span className="tix-empty-title">
                    {copyText("Chưa phát sinh doanh số", "No sales recorded")}
                  </span>
                  <p className="tix-empty-desc">
                    {copyText(
                      "Danh sách sẽ tự động xếp hạng các sự kiện có giao dịch thành công trong khoảng thời gian đã chọn.",
                      "Top events appear here once paid orders exist in this date range.",
                    )}
                  </p>
                </div>
              ) : (
                topEvents.map((evt, idx) => (
                  <Link
                    key={evt.eventId}
                    to={`/admin/reconciliation?eventId=${encodeURIComponent(evt.eventId)}`}
                    className="tix-ticket-item-row tix-admin-top-event-link"
                    title={copyText("Mở đối soát cho sự kiện này", "Open event reconciliation")}
                  >
                    <div className="tix-ticket-item-left">
                      <div className={`tix-event-rank-badge rank-${idx + 1}`}>#{idx + 1}</div>
                      <div className="tix-ticket-item-details">
                        <span className="tix-ticket-name" title={evt.eventTitle}>
                          {evt.eventTitle}
                        </span>
                        <div className="tix-ticket-meta-row">
                          <span className="tix-ticket-price-sub">
                            {formatMoney(
                              evt.soldRevenue,
                              topEventsQuery.data?.currency || currency,
                              locale,
                            )}
                          </span>
                        </div>
                      </div>
                    </div>

                    <div className="tix-ticket-item-right">
                      <span className="tix-ticket-qty-badge">
                        <small>{copyText("Đã bán", "Sold")}</small>
                        {evt.soldTicketCount.toLocaleString(locale)} {copyText("vé", "tickets")}
                      </span>
                    </div>
                  </Link>
                ))
              )}
            </div>
          </div>
        </div>

        {/* 4. Bottom Table: System-wide Event Sales Statistics */}
        <div className="tix-orders-card">
          <div className="tix-orders-toolbar">
            <div>
              <h3 className="tix-card-title">
                {copyText("Bảng thống kê doanh số theo sự kiện", "Event Sales Breakdown")}
              </h3>
              <p className="tix-card-subtitle">
                {copyText(
                  "Số liệu phân trang trực tiếp từ Booking Service; bấm Đối soát để kiểm tra sổ cái Payment và chi trả.",
                  "Server-paginated statistics from Booking Service; open Reconciliation to inspect Payment ledger and payout.",
                )}
              </p>
            </div>
          </div>

          <div className="tix-admin-table-controls-bar">
            <div className="tix-search-input-wrap tix-admin-table-search">
              <Search size={15} aria-hidden="true" />
              <input
                type="text"
                className="tix-search-input"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder={copyText(
                  "Lọc nhanh trong trang (tên, ID, BTC)...",
                  "Filter current page (title, ID, organizer)...",
                )}
              />
              {searchQuery && (
                <button
                  type="button"
                  className="tix-search-clear-btn"
                  onClick={() => setSearchQuery("")}
                  aria-label={copyText("Xóa từ khóa", "Clear filter")}
                >
                  <X size={13} />
                </button>
              )}
            </div>

            <div className="tix-orders-actions tix-admin-table-controls">
            <div
              className={`tix-dropdown-wrap ${isSortDropdownOpen ? "is-open" : ""}`}
              ref={sortDropdownRef}
            >
              <button
                type="button"
                className="tix-filter-select-btn"
                onClick={() => setIsSortDropdownOpen((prev) => !prev)}
                aria-expanded={isSortDropdownOpen}
                aria-haspopup="listbox"
              >
                <span>
                  {copyText("Sắp xếp:", "Sort:")}{" "}
                  {isVi
                    ? SORT_OPTIONS.find((o) => o.key === sortBy)?.vi
                    : SORT_OPTIONS.find((o) => o.key === sortBy)?.en}
                </span>
                <ChevronDown
                  size={14}
                  className={`tix-chevron-icon ${isSortDropdownOpen ? "rotated" : ""}`}
                />
              </button>
              {isSortDropdownOpen && (
                <div className="tix-dropdown-menu tix-dropdown-menu--status" role="listbox">
                  {SORT_OPTIONS.map((opt) => (
                    <button
                      key={opt.key}
                      type="button"
                      role="option"
                      aria-selected={sortBy === opt.key}
                      className={`tix-dropdown-item ${sortBy === opt.key ? "is-active" : ""}`}
                      onClick={() => {
                        setSortBy(opt.key);
                        setTablePage(0);
                        setIsSortDropdownOpen(false);
                      }}
                    >
                      <span>{isVi ? opt.vi : opt.en}</span>
                      {sortBy === opt.key && <Check size={14} className="tix-check-icon" />}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <button
              type="button"
              className="tix-filter-select-btn"
              onClick={() => {
                setDirection((d) => (d === "DESC" ? "ASC" : "DESC"));
                setTablePage(0);
              }}
              title={copyText("Đổi chiều sắp xếp", "Toggle sort direction")}
            >
              <ArrowDownUp size={14} aria-hidden="true" />
              <span>
                {direction === "DESC"
                  ? copyText("Giảm dần", "Desc")
                  : copyText("Tăng dần", "Asc")}
              </span>
            </button>

            <button
              type="button"
              className="tix-export-btn"
              onClick={handleExportCsv}
              disabled={!tableQuery.isSuccess || visibleRows.length === 0}
              title={copyText(
                "Xuất các dòng đang hiển thị trên trang hiện tại",
                "Export rows shown on the current page",
              )}
            >
              <Download size={14} aria-hidden="true" />
              <span>{copyText("Xuất CSV trang hiện tại", "Export current page CSV")}</span>
            </button>
            </div>
          </div>

          <div className="tix-table-wrapper">
            <table className="tix-orders-table">
              <thead>
                <tr>
                  <th scope="col">{copyText("Sự kiện", "Event")}</th>
                  <th scope="col">{copyText("Ban tổ chức", "Organizer")}</th>
                  <th scope="col">{copyText("Trạng thái", "Status")}</th>
                  <th scope="col">{copyText("Đơn hàng", "Orders")}</th>
                  <th scope="col">{copyText("Vé đã bán", "Sold Tickets")}</th>
                  <th scope="col">{copyText("Doanh thu", "Revenue")}</th>
                  <th scope="col" style={{ textAlign: "center", width: "1%" }}>
                    {copyText("Thao tác", "Action")}
                  </th>
                </tr>
              </thead>
              <tbody>
                {tableQuery.isError ? (
                  <tr>
                    <td colSpan={7} role="alert" style={{ textAlign: "center", padding: "36px 16px" }}>
                      {copyText("Không thể tải bảng thống kê doanh số.", "Failed to load event sales table.")}
                    </td>
                  </tr>
                ) : tableQuery.isPending ? (
                  <tr>
                    <td colSpan={7} style={{ textAlign: "center", padding: "48px 16px" }}>
                      {copyText("Đang tải dữ liệu sự kiện...", "Loading event statistics...")}
                    </td>
                  </tr>
                ) : visibleRows.length === 0 ? (
                  <tr>
                    <td colSpan={7} style={{ textAlign: "center", padding: "48px 16px" }}>
                      {normalizedSearch && totalPages > 1
                        ? copyText(
                            "Không thấy sự kiện khớp trên trang hiện tại. Xóa bộ lọc hoặc chuyển trang để kiểm tra.",
                            "No matching events on this page. Clear filter or check other pages.",
                          )
                        : copyText(
                            "Chưa có sự kiện nào trong thống kê cho khoảng thời gian này.",
                            "No events found in statistics for this date range.",
                          )}
                    </td>
                  </tr>
                ) : (
                  visibleRows.map((evt) => (
                    <tr key={evt.eventId}>
                      <td>
                        <div className="tix-event-cell">
                          <span className="tix-event-title-main" title={evt.eventTitle}>
                            {evt.eventTitle}
                          </span>
                          <span className="tix-event-id-sub tix-admin-inline-id">
                            <span className="tix-admin-mono">{shortId(evt.eventId)}</span>
                            <button
                              type="button"
                              className="tix-admin-icon-copy"
                              onClick={() => void handleCopy(evt.eventId, "Event ID")}
                              aria-label={copyText("Sao chép Event ID", "Copy Event ID")}
                              title={evt.eventId}
                            >
                              {copiedId === evt.eventId ? (
                                <Check size={12} color="#16a34a" />
                              ) : (
                                <Copy size={12} />
                              )}
                            </button>
                          </span>
                        </div>
                      </td>
                      <td>
                        {(() => {
                          const org = organizerMap.get(evt.organizerSubject);
                          return (
                            <div className="tix-event-cell">
                              <span className="tix-admin-org-cell-name">
                                {org?.logoUrl ? (
                                  <img
                                    src={org.logoUrl}
                                    alt=""
                                    className="tix-admin-organizer-avatar"
                                    onError={(e) => {
                                      e.currentTarget.style.display = "none";
                                    }}
                                  />
                                ) : (
                                  <span
                                    className="tix-admin-organizer-avatar-fallback"
                                    aria-hidden="true"
                                  >
                                    <Building2 size={12} />
                                  </span>
                                )}
                                <span
                                  className="tix-event-title-main"
                                  title={org?.name || evt.organizerSubject}
                                >
                                  {org?.name || shortId(evt.organizerSubject)}
                                </span>
                              </span>
                              <span className="tix-event-id-sub tix-admin-inline-id">
                                <span className="tix-admin-mono" title={evt.organizerSubject}>
                                  {shortId(evt.organizerSubject)}
                                </span>
                                <button
                                  type="button"
                                  className="tix-admin-icon-copy"
                                  onClick={() =>
                                    void handleCopy(evt.organizerSubject, "Organizer Subject")
                                  }
                                  aria-label={copyText(
                                    "Sao chép mã Ban tổ chức",
                                    "Copy Organizer Subject",
                                  )}
                                  title={evt.organizerSubject}
                                >
                                  {copiedId === evt.organizerSubject ? (
                                    <Check size={12} color="#16a34a" />
                                  ) : (
                                    <Copy size={12} />
                                  )}
                                </button>
                              </span>
                            </div>
                          );
                        })()}
                      </td>
                      <td>
                        <span
                          className={`tix-status-badge ${
                            evt.eventStatus === "PUBLISHED"
                              ? "is-published"
                              : evt.eventStatus === "CANCELLED"
                              ? "is-cancelled"
                              : "is-draft"
                          }`}
                          data-status={evt.eventStatus}
                        >
                          {getEventStatusLabel(evt.eventStatus)}
                        </span>
                      </td>
                      <td>
                        <span className="tix-sold-count">
                          {evt.soldOrderCount.toLocaleString(locale)}
                        </span>
                      </td>
                      <td>
                        <span className="tix-sold-count">
                          {evt.soldTicketCount.toLocaleString(locale)}
                        </span>
                      </td>
                      <td>
                        <strong className="tix-revenue-text">
                          {formatMoney(evt.soldRevenue, currency, locale)}
                        </strong>
                      </td>
                      <td style={{ textAlign: "center", whiteSpace: "nowrap" }}>
                        <Link
                          to={`/admin/reconciliation?eventId=${encodeURIComponent(evt.eventId)}`}
                          className="tix-admin-btn tix-admin-btn-primary"
                          style={{ minHeight: 34, padding: "6px 14px", fontSize: "0.8rem" }}
                        >
                          <Scale size={14} aria-hidden="true" />
                          <span>{copyText("Đối soát", "Reconcile")}</span>
                        </Link>
                      </td>
                    </tr>
                  ))
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
                  disabled={tablePage === 0 || tableQuery.isFetching}
                  onClick={() => setTablePage((p) => Math.max(0, p - 1))}
                  aria-label={copyText("Trang trước", "Previous page")}
                >
                  <ChevronLeft size={15} />
                </button>

                <span className="tix-page-indicator">
                  {String(tablePage + 1).padStart(2, "0")}/{String(totalPages).padStart(2, "0")}
                </span>

                <button
                  type="button"
                  className="tix-page-arrow-btn"
                  disabled={tablePage >= totalPages - 1 || tableQuery.isFetching}
                  onClick={() => setTablePage((p) => p + 1)}
                  aria-label={copyText("Trang sau", "Next page")}
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

export default AdminSalesPage;
