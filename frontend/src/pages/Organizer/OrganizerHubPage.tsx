import { useState, useMemo, useRef, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useKeycloak } from "@react-keycloak/web";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  Armchair,
  Building2,
  Calendar,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Copy,
  Download,
  ExternalLink,
  FileEdit,
  LayoutGrid,
  MessageCircle,
  LogOut,
  MoreHorizontal,
  PlusCircle,
  QrCode,
  Search,
  ShoppingBag,
  Ticket,
  User,
  UserRound,
  X,
} from "lucide-react";
import OrganizerLayout from "../../components/organizer/OrganizerLayout";
import { useOrganizerGate } from "./useOrganizerGate";
import { organizerDashboardService } from "../../services/organizerDashboardService";
import { useUserProfile } from "../../hooks/useUserProfile";
import LanguageSwitcher from "../../components/common/LanguageSwitcher";
import { confirmDestructiveAction } from "../../lib/swal";
import "./organizer-dashboard.css";

const reportTimeZone = "Asia/Ho_Chi_Minh";
const reportCalendar = new Intl.DateTimeFormat("sv-SE", { timeZone: reportTimeZone, dateStyle: "short" });

function reportRange(days: number | "month") {
  const to = reportCalendar.format(new Date());
  if (days === "month") return { from: to.slice(0, 7) + "-01", to };
  const first = new Date(to + "T00:00:00Z");
  first.setUTCDate(first.getUTCDate() - (days - 1));
  return { from: first.toISOString().slice(0, 10), to };
}

export type DateRangeKey = "7d" | "30d" | "this_month" | "today" | "90d";

export interface DateRangeConfig {
  key: DateRangeKey;
  label: string;
  calculate: () => { from: string; to: string };
}

export const DATE_RANGE_OPTIONS: DateRangeConfig[] = [
  { key: "7d", label: "7 ngày qua", calculate: () => reportRange(7) },
  { key: "30d", label: "30 ngày qua", calculate: () => reportRange(30) },
  { key: "this_month", label: "Tháng này", calculate: () => reportRange("month") },
  { key: "today", label: "Hôm nay", calculate: () => reportRange(1) },
  { key: "90d", label: "90 ngày qua", calculate: () => reportRange(90) },
];

const SALES_EVENT_STATUSES = ["PUBLISHED", "CANCELLED"] as const;
type SalesEventStatus = (typeof SALES_EVENT_STATUSES)[number];

const TABLE_PAGE_SIZE = 10;

function formatEventSchedule(isoString?: string): { date: string; time: string } | null {
  if (!isoString) return null;
  const d = new Date(isoString);
  if (isNaN(d.getTime())) return null;
  const day = String(d.getDate()).padStart(2, "0");
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const year = d.getFullYear();
  const hours = String(d.getHours()).padStart(2, "0");
  const mins = String(d.getMinutes()).padStart(2, "0");
  return {
    date: `${day}/${month}/${year}`,
    time: `${hours}:${mins}`,
  };
}

export function TixOrganizerDashboard({ subject, issuer }: { subject: string; issuer?: string }) {
  const { keycloak } = useKeycloak();
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const locale = i18n.resolvedLanguage === "en" ? "en-US" : "vi-VN";

  // State
  const [dateRangeKey, setDateRangeKey] = useState<DateRangeKey>("7d");
  const [isDateDropdownOpen, setIsDateDropdownOpen] = useState(false);
  const dateDropdownRef = useRef<HTMLDivElement>(null);

  const [searchQuery, setSearchQuery] = useState<string>("");
  const [statusFilter, setStatusFilter] = useState<SalesEventStatus>("PUBLISHED");
  const [isStatusDropdownOpen, setIsStatusDropdownOpen] = useState(false);
  const statusDropdownRef = useRef<HTMLDivElement>(null);

  const [cursorStack, setCursorStack] = useState<(string | null)[]>([null]);
  const [tablePage, setTablePage] = useState<number>(0);

  const [hoveredPointIndex, setHoveredPointIndex] = useState<number | null>(null);

  // Action Menu dạng Shadcn UI cho từng dòng sự kiện
  const [openActionMenuId, setOpenActionMenuId] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const actionMenuRef = useRef<HTMLDivElement>(null);

  const handleCopyId = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    navigator.clipboard.writeText(id);
    setCopiedId(id);
    setTimeout(() => {
      setCopiedId((prev) => (prev === id ? null : prev));
    }, 1500);
  };

  // User Profile thật & Quản lý Account Menu
  const { data: userProfile } = useUserProfile();
  const [avatarBroken, setAvatarBroken] = useState(false);
  const accountMenuRef = useRef<HTMLDetailsElement>(null);

  const accountName = (
    userProfile?.displayName ||
    `${userProfile?.firstName || ""} ${userProfile?.lastName || ""}`.trim() ||
    (keycloak.tokenParsed?.name as string | undefined) ||
    `${keycloak.tokenParsed?.given_name || ""} ${keycloak.tokenParsed?.family_name || ""}`.trim() ||
    (keycloak.tokenParsed?.preferred_username as string | undefined) ||
    "Ban tổ chức"
  ).trim();

  const avatarSrc = avatarBroken ? undefined : userProfile?.avatarUrl;
  const avatarInitials =
    accountName
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((word) => Array.from(word)[0])
      .join("")
      .toLocaleUpperCase() || "TC";

  const handleLogout = async () => {
    const confirmed = await confirmDestructiveAction({
      title: t("swal.logoutTitle", { defaultValue: "Đăng xuất" }),
      text: t("swal.logoutText", { defaultValue: "Bạn có chắc chắn muốn đăng xuất không?" }),
      confirmButtonText: t("swal.logoutConfirm", { defaultValue: "Đăng xuất" }),
      cancelButtonText: t("swal.logoutCancel", { defaultValue: "Hủy" }),
    });
    if (!confirmed) return;

    await keycloak.logout({
      redirectUri: `${window.location.origin}/`,
    });
  };

  // Đóng dropdown khi click outside hoặc nhấn Esc
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      if (dateDropdownRef.current && !dateDropdownRef.current.contains(target)) {
        setIsDateDropdownOpen(false);
      }
      if (statusDropdownRef.current && !statusDropdownRef.current.contains(target)) {
        setIsStatusDropdownOpen(false);
      }
      if (actionMenuRef.current && !actionMenuRef.current.contains(target)) {
        setOpenActionMenuId(null);
      }
      if (accountMenuRef.current && !accountMenuRef.current.contains(target)) {
        accountMenuRef.current.removeAttribute("open");
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setIsDateDropdownOpen(false);
        setIsStatusDropdownOpen(false);
        setOpenActionMenuId(null);
        if (accountMenuRef.current) {
          accountMenuRef.current.removeAttribute("open");
        }
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

  const getDateRangeLabel = (key: DateRangeKey) => {
    switch (key) {
      case "7d":
        return t("organizerHub.range7d", { defaultValue: "7 ngày qua" });
      case "30d":
        return t("organizerHub.range30d", { defaultValue: "30 ngày qua" });
      case "this_month":
        return t("organizerHub.rangeThisMonth", { defaultValue: "Tháng này" });
      case "today":
        return t("organizerHub.rangeToday", { defaultValue: "Hôm nay" });
      case "90d":
        return t("organizerHub.range90d", { defaultValue: "90 ngày qua" });
      default:
        return key;
    }
  };

  const getStatusLabel = (key: string) => {
    switch (key) {
      case "ALL":
        return t("organizerHub.statusAll", { defaultValue: "Tất cả trạng thái" });
      case "PUBLISHED":
        return t("organizerHub.statusPublished", { defaultValue: "Đã công bố" });
      case "DRAFT":
        return t("organizerHub.statusDraft", { defaultValue: "Bản nháp" });
      case "CANCELLED":
        return t("organizerHub.statusCancelled", { defaultValue: "Đã hủy" });
      case "PENDING_APPROVAL":
        return t("organizerHub.statusPending", { defaultValue: "Chờ duyệt" });
      case "APPROVED":
        return t("organizerHub.statusApproved", { defaultValue: "Đã duyệt" });
      default:
        return key;
    }
  };

  const activeRangeOption = useMemo(() => {
    return DATE_RANGE_OPTIONS.find((opt) => opt.key === dateRangeKey) || DATE_RANGE_OPTIONS[0];
  }, [dateRangeKey]);

  const { from: fromDate, to: toDate } = activeRangeOption.calculate();
  const scope = [issuer, subject];
  const noData = t("organizerHub.dataUnavailable");
  const loadingData = t("organizerHub.dataLoading");
  const loadFailed = t("organizerHub.dataLoadFailed");

  // 1. Profile của Organizer: GET /api/v1/organizers/{subject}
  const { data: organizerProfile } = useQuery({
    queryKey: ["organizer-dashboard-profile", ...scope],
    queryFn: ({ signal }) => organizerDashboardService.profile(subject, signal),
    retry: false,
  });

  const currentCursor = cursorStack[tablePage] ?? null;
  const overviewEventsQuery = useQuery({
    queryKey: ["organizer-events-overview", ...scope],
    queryFn: ({ signal }) => organizerDashboardService.events(null, signal),
    retry: false,
  });
  const overviewEvents = overviewEventsQuery.data?.items ?? [];
  const overviewHasMore = Boolean(overviewEventsQuery.data?.nextCursor);

  const eventsQuery = useQuery({
    queryKey: ["organizer-events-page", ...scope, statusFilter, TABLE_PAGE_SIZE, currentCursor],
    queryFn: ({ signal }) =>
      organizerDashboardService.events(currentCursor, signal, statusFilter, TABLE_PAGE_SIZE),
    retry: false,
  });
  const salesSummaryQuery = useQuery({
    queryKey: ["organizer-sales-summary", ...scope, fromDate, toDate],
    queryFn: ({ signal }) => organizerDashboardService.salesSummary(subject, fromDate, toDate, signal),
    retry: false,
  });
  const salesChartQuery = useQuery({
    queryKey: ["organizer-sales-chart", ...scope, fromDate, toDate],
    queryFn: ({ signal }) => organizerDashboardService.salesChart(subject, fromDate, toDate, "DAY", signal),
    retry: false,
  });
  const eventSalesQuery = useQuery({
    queryKey: ["organizer-event-sales-by-tickets", ...scope, fromDate, toDate],
    queryFn: async ({ signal }) => {
      const firstPage = await organizerDashboardService.eventSales(subject, 0, fromDate, toDate, signal);
      const items = [...firstPage.items];
      for (let page = 1; page < firstPage.totalPages; page += 1) {
        const nextPage = await organizerDashboardService.eventSales(subject, page, fromDate, toDate, signal);
        items.push(...nextPage.items);
      }
      return { items, currency: firstPage.currency };
    },
    retry: false,
  });
  const eventsList = useMemo(
    () => (eventsQuery.isSuccess ? eventsQuery.data.items : []),
    [eventsQuery.isSuccess, eventsQuery.data]
  );
  const nextCursor =
    eventsQuery.isSuccess &&
    eventsQuery.data.nextCursor &&
    !cursorStack.includes(eventsQuery.data.nextCursor)
      ? eventsQuery.data.nextCursor
      : null;
  const canGoPrev = tablePage > 0;
  const canGoNext = Boolean(nextCursor);

  const salesSummary = salesSummaryQuery.isSuccess ? salesSummaryQuery.data : undefined;
  const salesChart = salesChartQuery.isSuccess ? salesChartQuery.data : undefined;
  const eventSalesData = eventSalesQuery.isSuccess ? eventSalesQuery.data : undefined;
  const summaryPlaceholder = salesSummaryQuery.isPending ? loadingData : salesSummaryQuery.isError ? loadFailed : noData;
  const eventsPlaceholder = overviewEventsQuery.isPending ? loadingData : overviewEventsQuery.isError ? loadFailed : noData;

  const salesByEvent = useMemo(
    () => new Map(eventSalesData?.items.map((item) => [item.eventId, item])),
    [eventSalesData],
  );
  const salesPlaceholder = eventSalesQuery.isPending
    ? loadingData
    : eventSalesQuery.isError ? loadFailed : noData;

  // Helpers
  const formatMoney = (value: string | number, currency = "VND") => {
    try {
      const num = typeof value === "string" ? BigInt(value) : BigInt(Math.round(value));
      return `${num.toLocaleString(locale)} ${currency === "VND" ? "đ" : currency}`;
    } catch {
      return `${value} đ`;
    }
  };

  // Thống kê số lượng theo trạng thái
  const publishedCount = overviewEvents.filter((e) => e.status === "PUBLISHED").length;
  const draftCount = overviewEvents.filter((e) => e.status === "DRAFT").length;

  const chartItems = useMemo(() => (salesChart?.items ?? []).map(item => ({
    date: item.periodStart,
    label: new Intl.DateTimeFormat(locale, { timeZone: "UTC", day: "2-digit", month: "2-digit" }).format(new Date(item.periodStart)),
    tickets: item.soldTicketCount,
    revenue: item.soldRevenue,
    orders: item.soldOrderCount,
  })), [salesChart, locale]);

  // Cấu hình tọa độ và tỷ lệ cho SVG Area Chart (tăng chiều cao để trục Ox và biểu đồ không bị bẹt)
  const chartWidth = 900;
  const chartHeight = 310;
  const chartPadding = useMemo(() => ({ top: 14, right: 20, bottom: 28, left: 36 }), []);
  const plotWidth = chartWidth - chartPadding.left - chartPadding.right;
  const plotHeight = chartHeight - chartPadding.top - chartPadding.bottom;
  const baselineY = chartPadding.top + plotHeight;

  const maxTickets = useMemo(() => {
    return Math.max(...chartItems.map((c) => c.tickets), 0);
  }, [chartItems]);

  const yMax = useMemo(() => {
    if (maxTickets === 0) return 10;
    if (maxTickets <= 5) return 5;
    if (maxTickets <= 10) return 10;
    if (maxTickets <= 15) return 15;
    if (maxTickets <= 25) return 25;
    if (maxTickets <= 50) return 50;
    return Math.ceil((maxTickets * 1.15) / 10) * 10;
  }, [maxTickets]);

  const yTicks = useMemo(() => {
    return [
      { val: yMax, y: chartPadding.top },
      { val: Math.round(yMax * 0.75), y: chartPadding.top + plotHeight * 0.25 },
      { val: Math.round(yMax * 0.5), y: chartPadding.top + plotHeight * 0.5 },
      { val: Math.round(yMax * 0.25), y: chartPadding.top + plotHeight * 0.75 },
      { val: 0, y: baselineY },
    ];
  }, [yMax, plotHeight, baselineY, chartPadding.top]);

  const points = useMemo(() => {
    const n = chartItems.length;
    if (n === 0) return [];
    return chartItems.map((item, i) => {
      const x = chartPadding.left + (n > 1 ? (i / (n - 1)) * plotWidth : plotWidth / 2);
      const y = baselineY - (item.tickets / yMax) * plotHeight;
      return {
        x,
        y,
        item,
        index: i,
      };
    });
  }, [chartItems, yMax, plotWidth, plotHeight, baselineY, chartPadding.left]);

  const { linePath, areaPath } = useMemo(() => {
    if (points.length === 0) return { linePath: "", areaPath: "" };
    if (points.length === 1) {
      const p = points[0];
      const lp = `M ${chartPadding.left} ${p.y.toFixed(1)} L ${chartPadding.left + plotWidth} ${p.y.toFixed(1)}`;
      const ap = `${lp} L ${chartPadding.left + plotWidth} ${baselineY} L ${chartPadding.left} ${baselineY} Z`;
      return { linePath: lp, areaPath: ap };
    }

    const lp = points
      .map((p, i) => `${i === 0 ? "M" : "L"} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`)
      .join(" ");

    const ap = `${lp} L ${points[points.length - 1].x.toFixed(1)} ${baselineY} L ${points[0].x.toFixed(1)} ${baselineY} Z`;
    return { linePath: lp, areaPath: ap };
  }, [points, baselineY, chartPadding.left, plotWidth]);

  // Đường kẻ sọc dọc nét đứt (dashed vertical grid) theo đúng mẫu ảnh
  const verticalGridXList = useMemo(() => {
    if (points.length <= 1) return [];
    const count = Math.min(5, Math.max(2, Math.floor(points.length / 4)));
    const step = (points.length - 1) / (count + 1);
    const list: number[] = [];
    for (let i = 1; i <= count; i++) {
      const idx = Math.round(i * step);
      if (points[idx]) {
        list.push(points[idx].x);
      }
    }
    return list;
  }, [points]);

  const handleMouseMove = (e: React.MouseEvent<SVGSVGElement>) => {
    if (points.length === 0) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const mouseX = ((e.clientX - rect.left) / rect.width) * chartWidth;
    let closestIdx = 0;
    let minDiff = Infinity;
    points.forEach((p, idx) => {
      const diff = Math.abs(p.x - mouseX);
      if (diff < minDiff) {
        minDiff = diff;
        closestIdx = idx;
      }
    });
    setHoveredPointIndex(closestIdx);
  };

  const handleMouseLeave = () => {
    setHoveredPointIndex(null);
  };

  const hoveredPoint = hoveredPointIndex !== null ? points[hoveredPointIndex] : null;

  const combinedEvents = useMemo(() => {
    return eventsList.map((evt) => {
      const sales = salesByEvent.get(evt.eventId);
      const schedule = formatEventSchedule(evt.startsAt);

      return {
        id: evt.eventId,
        title: evt.title,
        status: evt.status,
        mode: evt.mode,
        date: schedule?.date || "Chưa ấn định",
        time: schedule?.time || "",
        soldTickets: sales?.soldTicketCount,
        revenue: sales?.soldRevenue,
        currency: eventSalesData?.currency,
        salesPlaceholder,
      };
    });
  }, [eventsList, salesByEvent, eventSalesData?.currency, salesPlaceholder]);

  // Hàm loại bỏ dấu tiếng Việt để hỗ trợ tìm kiếm không dấu
  const removeAccents = (str: string): string => {
    return str
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/đ/g, "d")
      .replace(/Đ/g, "D")
      .toLowerCase();
  };

  // Lọc nhanh trên trang hiện tại theo từ khóa tìm kiếm (có dấu / không dấu)
  const filteredEvents = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    const queryWithoutAccents = removeAccents(query);
    if (!query) return combinedEvents;

    return combinedEvents.filter((evt) => {
      const titleLower = evt.title.toLowerCase();
      const titleWithoutAccents = removeAccents(evt.title);
      const idLower = evt.id.toLowerCase();

      return (
        titleLower.includes(query) ||
        titleWithoutAccents.includes(queryWithoutAccents) ||
        idLower.includes(query)
      );
    });
  }, [combinedEvents, searchQuery]);

  const topEvents = (eventSalesData?.items ?? [])
    .filter((event) => event.soldTicketCount > 0)
    .slice(0, 4);

  const paginatedEvents = filteredEvents;
  const isExportReady = eventsQuery.isSuccess && eventSalesQuery.isSuccess;

  // Export CSV chuẩn UTF-8 BOM, tương thích Excel trên Windows không bị lỗi font tiếng Việt
  const handleExportCSV = () => {
    if (!isExportReady) return;
    if (filteredEvents.length === 0) {
      alert(
        t("organizerHub.noDataToExport", {
          defaultValue: "Không có dữ liệu sự kiện để xuất báo cáo.",
        })
      );
      return;
    }

    const escapeCsv = (val: string | number | undefined | null) => {
      const s = String(val ?? "").replace(/"/g, '""');
      return `"${s}"`;
    };

    const headers = [
      t("organizerHub.colEventId", { defaultValue: "Mã sự kiện" }),
      t("organizerHub.colEventName", { defaultValue: "Tên sự kiện" }),
      t("organizerHub.colStatus", { defaultValue: "Trạng thái" }),
      t("organizerHub.colMode", { defaultValue: "Hình thức" }),
      t("organizerHub.colTime", { defaultValue: "Thời gian" }),
      t("organizerHub.colSoldTickets", { defaultValue: "Vé đã bán" }),
      t("organizerHub.colRevenue", { defaultValue: "Doanh thu" }),
    ];

    const rows = filteredEvents.map((e) => [
      escapeCsv(e.id),
      escapeCsv(e.title),
      escapeCsv(getStatusLabel(e.status)),
      escapeCsv(
        e.mode === "SEAT_MAP"
          ? t("organizerHub.modeSeatMap", { defaultValue: "Sơ đồ ghế" })
          : t("organizerHub.modeQuantity", { defaultValue: "Vé số lượng" })
      ),
      escapeCsv(e.time ? `${e.date} ${e.time}` : e.date),
      e.soldTickets ?? "",
      escapeCsv(e.revenue),
    ]);

    const csvContent = [headers.map(escapeCsv).join(","), ...rows.map((r) => r.join(","))].join("\r\n");

    // Thêm UTF-8 BOM (\uFEFF) giúp Excel trên Windows đọc đúng ký tự tiếng Việt
    const blob = new Blob(["\uFEFF" + csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    const dateStr = new Date().toISOString().slice(0, 10);
    link.href = url;
    link.download = `bao_cao_su_kien_${dateStr}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };

  return (
    <div className="tix-dashboard-page">
      <div className="tix-dashboard-container">
        {/* ============================================================
            1. TOP NAVIGATION BAR (FLOATING PILL MENU)
            ============================================================ */}
        <header className="tix-topbar">
          <Link to="/" className="logo" aria-label="Trang chủ FlashTicket">
            <img
              className="nav-brand-cat"
              src="/icons/icons8-black-cat.png"
              alt=""
              width={32}
              height={32}
            />
            <span>FlashTicket</span>
          </Link>

          <nav className="tix-pill-nav" aria-label="Organizer Navigation">
            <Link to="/organizer" className="tix-pill-item active">
              <LayoutGrid size={15} aria-hidden="true" />
              <span>{t("organizer.dashboard", { defaultValue: "Dashboard" })}</span>
            </Link>
            <Link to="/organizer/events/new" className="tix-pill-item">
              <PlusCircle size={15} aria-hidden="true" />
              <span>{t("organizer.createEvent", { defaultValue: "Tạo sự kiện" })}</span>
            </Link>
            <Link to="/organizer/events" className="tix-pill-item">
              <Calendar size={15} aria-hidden="true" />
              <span>{t("organizer.events", { defaultValue: "Sự kiện của tôi" })}</span>
            </Link>
            <Link to="/organizer/chatbot" className="tix-pill-item">
              <MessageCircle size={15} aria-hidden="true" />
              <span>{t("chatbot.switchToOrganizer")}</span>
            </Link>
            <Link to="/organizer/profile" className="tix-pill-item">
              <Building2 size={15} aria-hidden="true" />
              <span>{t("organizer.profile", { defaultValue: "Hồ sơ" })}</span>
            </Link>
            <Link to="/organizer/check-in" className="tix-pill-item">
              <QrCode size={15} aria-hidden="true" />
              <span>{t("organizer.checkIn", { defaultValue: "Check-in" })}</span>
            </Link>
          </nav>

          <div className="tix-topbar-right">
            <details
              className="account-dropdown tix-account-dropdown"
              ref={accountMenuRef}
              onKeyDown={(event) => {
                if (event.key === "Escape") {
                  event.currentTarget.open = false;
                  event.currentTarget.querySelector("summary")?.focus();
                }
              }}
            >
              <summary
                className="account-trigger tix-account-trigger"
                aria-label={t("nav.accountLabel", { defaultValue: "Tài khoản" })}
                title={accountName}
              >
                {avatarSrc ? (
                  <img
                    src={avatarSrc}
                    alt=""
                    className="nav-avatar tix-nav-avatar"
                    onError={() => setAvatarBroken(true)}
                  />
                ) : (
                  <span className="nav-avatar nav-avatar-initials tix-nav-avatar" aria-hidden="true">
                    {avatarInitials}
                  </span>
                )}
                <span className="nav-account-label tix-nav-account-label">
                  {t("nav.accountLabel", { defaultValue: "Tài khoản" })}
                </span>
                <ChevronDown className="account-trigger-chevron" size={15} />
              </summary>

              <div className="dropdown-menu tix-account-menu">
                <div className="tix-user-info-box">
                  <div className="tix-user-name">{accountName}</div>
                  <div className="tix-user-role">
                    {userProfile?.email ||
                      (keycloak.tokenParsed?.email as string | undefined) ||
                      t("organizer.organizerRole", { defaultValue: "Ban tổ chức" })}
                  </div>
                </div>
                <hr />
                <Link
                  to="/profile"
                  className="dropdown-item"
                  onClick={() => accountMenuRef.current?.removeAttribute("open")}
                >
                  <UserRound size={16} />
                  <span>{t("nav.personalProfile", { defaultValue: "Hồ sơ cá nhân" })}</span>
                </Link>
                <hr />
                <button
                  type="button"
                  onClick={() => void handleLogout()}
                  className="dropdown-item logout w-full text-left"
                >
                  <LogOut size={16} />
                  <span>{t("organizer.logout", { defaultValue: "Đăng xuất" })}</span>
                </button>
              </div>
            </details>

            <div className="tix-topbar-divider" />

            <LanguageSwitcher className="language-switcher-nav tix-language-switcher" />
          </div>
        </header>

        {/* ============================================================
            2. ORGANIZER GENERAL OVERVIEW HEADER
            ============================================================ */}
        <div className="tix-overview-header">
          <div className="tix-overview-header-left">
            <div className="tix-overview-title-block">
              <h1 className="tix-overview-main-title">
                {organizerProfile?.organizationName ||
                  t("organizerHub.overviewTitle", { defaultValue: "Tổng quan Ban tổ chức" })}
              </h1>
              <p className="tix-overview-subtitle">
                {organizerProfile?.shortDescription ||
                  t("organizerHub.overviewSubtitle", {
                    defaultValue:
                      "Theo dõi toàn diện hiệu quả kinh doanh, số lượng vé và các sự kiện đang vận hành.",
                  })}
              </p>
            </div>

            <div className="tix-overview-badges-row">
              <span className="tix-overview-badge">
                <Calendar size={13} aria-hidden="true" />
                <span>
                  {overviewEventsQuery.isSuccess
                    ? t("organizerHub.eventsCount", {
                        count: overviewEvents.length,
                      }) + (overviewHasMore ? " +" : "")
                    : eventsPlaceholder}
                </span>
              </span>
              {typeof organizerProfile?.followerCount === "number" && (
                <span className="tix-overview-badge">
                  <User size={13} aria-hidden="true" />
                  <span>
                    {t("organizerHub.followersCount", {
                      count: organizerProfile.followerCount,
                      defaultValue: `${organizerProfile.followerCount.toLocaleString(locale)} người theo dõi`,
                    })}
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
                  title={t("organizerHub.selectDateRange", { defaultValue: "Chọn khoảng thời gian thống kê" })}
                  aria-expanded={isDateDropdownOpen}
                  aria-haspopup="listbox"
                >
                  <span className="tix-date-btn-content">
                    <Calendar size={14} />
                    <span>{getDateRangeLabel(activeRangeOption.key)}</span>
                  </span>
                  <ChevronDown
                    size={14}
                    className={`tix-chevron-icon ${isDateDropdownOpen ? "rotated" : ""}`}
                  />
                </button>

                {isDateDropdownOpen && (
                  <div className="tix-dropdown-menu" role="listbox">
                    {DATE_RANGE_OPTIONS.map((opt) => (
                      <button
                        key={opt.key}
                        type="button"
                        role="option"
                        aria-selected={dateRangeKey === opt.key}
                        className={`tix-dropdown-item ${dateRangeKey === opt.key ? "is-active" : ""}`}
                        onClick={() => {
                          setDateRangeKey(opt.key);
                          setIsDateDropdownOpen(false);
                        }}
                      >
                        <span>{getDateRangeLabel(opt.key)}</span>
                        {dateRangeKey === opt.key && (
                          <Check size={14} className="tix-check-icon" />
                        )}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>

            <Link to="/organizer/events/new" className="tix-primary-cta-btn">
              <PlusCircle size={16} />
              <span>{t("organizerHub.createNewEvent", { defaultValue: "Tạo sự kiện mới" })}</span>
            </Link>
          </div>
        </div>

        {/* ============================================================
            3. KPI METRIC CARDS (4 CARDS CHỈ SỐ THEO KỲ & VẬN HÀNH)
            ============================================================ */}
        <div className="tix-kpi-grid">
          {/* Card 1: Doanh số trong kỳ */}
          <div className="tix-kpi-card">
            <div className="tix-kpi-top">
              <span className="tix-kpi-label">
                {t("organizerHub.kpiTotalRevenue", { defaultValue: "Doanh số trong kỳ" })}
              </span>
              <div className="tix-kpi-icon-circle">
                <ShoppingBag size={13} />
              </div>
            </div>
            <div className="tix-kpi-value-lg" role={salesSummaryQuery.isError ? "alert" : undefined}>{salesSummary ? formatMoney(salesSummary.soldRevenue, salesSummary.currency) : summaryPlaceholder}</div>
            <div className="tix-kpi-note">
              {t("organizerHub.kpiTotalRevenueNote", { defaultValue: "Doanh số vé trong kỳ đã chọn" })}
            </div>
          </div>

          {/* Card 2: Vé bán trong kỳ */}
          <div className="tix-kpi-card">
            <div className="tix-kpi-top">
              <span className="tix-kpi-label">
                {t("organizerHub.kpiSoldTickets", { defaultValue: "Vé bán trong kỳ" })}
              </span>
              <div className="tix-kpi-icon-circle">
                <Ticket size={13} />
              </div>
            </div>
            <div className="tix-kpi-value-lg">
              {salesSummary ? <>
                {salesSummary.soldTicketCount.toLocaleString(locale)}{" "}
                <small className="tix-kpi-unit">{t("organizerHub.unitTickets")}</small>
              </> : summaryPlaceholder}
            </div>
            <div className="tix-kpi-note">
              {t("organizerHub.kpiSoldTicketsNote", { defaultValue: "Số vé đã giao dịch thành công" })}
            </div>
          </div>

          {/* Card 3: Đơn đã bán trong kỳ */}
          <div className="tix-kpi-card">
            <div className="tix-kpi-top">
              <span className="tix-kpi-label">
                {t("organizerHub.kpiCompletedOrders", { defaultValue: "Đơn đã bán trong kỳ" })}
              </span>
              <div className="tix-kpi-icon-circle">
                <ShoppingBag size={13} />
              </div>
            </div>
            <div className="tix-kpi-value-lg">
              {salesSummary ? <>
                {salesSummary.soldOrderCount.toLocaleString(locale)}{" "}
                <small className="tix-kpi-unit">{t("organizerHub.unitOrders")}</small>
              </> : summaryPlaceholder}
            </div>
            <div className="tix-kpi-note">
              {t("organizerHub.kpiCompletedOrdersNote", { defaultValue: "Giao dịch thanh toán thành công" })}
            </div>
          </div>

          {/* Card 4: Sự kiện vận hành */}
          <div className="tix-kpi-card">
            <div className="tix-kpi-top">
              <span className="tix-kpi-label">
                {t("organizerHub.kpiOperatingEvents", { defaultValue: "Sự kiện vận hành" })}
              </span>
              <div className="tix-kpi-icon-circle">
                <Calendar size={13} />
              </div>
            </div>
            <div className="tix-kpi-sub-stats">
              <div className="tix-kpi-sub-item">
                <span>{t("organizerHub.kpiTotalCount", { defaultValue: "Tổng số" })}</span>
                <span>
                  {overviewEventsQuery.isSuccess
                    ? `${overviewEvents.length}${overviewHasMore ? "+" : ""}`
                    : eventsPlaceholder}
                </span>
              </div>
              <div className="tix-kpi-sub-item">
                <span>{t("organizerHub.kpiPublishedCount", { defaultValue: "Đã công bố" })}</span>
                <span style={{ color: "var(--success, #16a34a)" }}>
                  {overviewEventsQuery.isSuccess ? publishedCount : eventsPlaceholder}
                </span>
              </div>
              <div className="tix-kpi-sub-item">
                <span>{t("organizerHub.kpiDraftCount", { defaultValue: "Bản nháp" })}</span>
                <span style={{ color: "var(--text-subtle, #565e69)" }}>
                  {overviewEventsQuery.isSuccess ? draftCount : eventsPlaceholder}
                </span>
              </div>
            </div>
            {overviewEventsQuery.isSuccess && overviewHasMore && (
              <div className="tix-kpi-note">
                {t("organizerHub.kpiOperatingPageNote", {
                  defaultValue: "Đếm theo trang đầu của danh sách sự kiện",
                })}
              </div>
            )}
          </div>
        </div>

        {/* ============================================================
            4. MIDDLE ROW: TICKET SALES CHART & EVENT STATUS OVERVIEW
            ============================================================ */}
        <div className="tix-middle-grid">
          {/* Left: Ticket Sales Interactive Area Chart (Biểu đồ miền như thiết kế mẫu) */}
          <div className="tix-chart-card">
            <div className="tix-card-header">
              <div>
                <h3 className="tix-card-title">
                  {t("organizerHub.chartTitle", { defaultValue: "Biểu đồ bán vé theo thời gian" })}
                </h3>
                <p className="tix-card-subtitle">
                  {t("organizerHub.chartSubtitle", {
                    defaultValue: "Xu hướng số lượng vé bán ra của ban tổ chức gần đây",
                  })}
                </p>
              </div>
            </div>

            {chartItems.length === 0 ? (
              <p role={salesChartQuery.isError ? "alert" : "status"}>{salesChartQuery.isPending ? loadingData : salesChartQuery.isError ? loadFailed : noData}</p>
            ) : <div className="tix-area-chart-container">
              <svg
                viewBox={`0 0 ${chartWidth} ${chartHeight}`}
                className="tix-area-chart-svg"
                onMouseMove={handleMouseMove}
                onMouseLeave={handleMouseLeave}
              >
                <defs>
                  {/* Gradient fill nhạt dần xuống dưới theo đúng yêu cầu */}
                  <linearGradient id="tixSalesAreaGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#c85a32" stopOpacity="0.32" />
                    <stop offset="60%" stopColor="#c85a32" stopOpacity="0.08" />
                    <stop offset="100%" stopColor="#c85a32" stopOpacity="0.0" />
                  </linearGradient>
                </defs>

                {/* Các đường kẻ ngang nét liền mảnh (Horizontal Solid Grid) */}
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
                      {tick.val}
                    </text>
                  </g>
                ))}

                {/* Các đường kẻ dọc nét đứt (Vertical Dashed Grid Lines như mẫu) */}
                {verticalGridXList.map((gx, i) => (
                  <line
                    key={`vgrid-${i}`}
                    x1={gx}
                    y1={chartPadding.top}
                    x2={gx}
                    y2={baselineY}
                    stroke="#e2e8f0"
                    strokeDasharray="4 4"
                    strokeWidth="1"
                  />
                ))}

                {/* Trục hoành cơ sở */}
                <line
                  x1={chartPadding.left}
                  y1={baselineY}
                  x2={chartPadding.left + plotWidth}
                  y2={baselineY}
                  stroke="#e2e8f0"
                  strokeWidth="1"
                />

                {/* Phần màu miền ở dưới nhạt dần (Area Fill) */}
                {areaPath && (
                  <path d={areaPath} fill="url(#tixSalesAreaGrad)" className="tix-area-fill" />
                )}

                {/* Đường biểu đồ chính (Stroke Line màu đất nung/terracotta như mẫu) */}
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

                {/* Nhãn ngày trục hoành (X-Axis date labels) */}
                {points.map((p, idx) => {
                  const n = points.length;
                  const shouldShow =
                    n <= 10 ||
                    idx === 0 ||
                    idx === n - 1 ||
                    (n <= 31 && idx % 4 === 0) ||
                    (n > 31 && idx % 10 === 0);

                  if (!shouldShow) return null;

                  return (
                    <text
                      key={p.item.label + idx}
                      x={p.x}
                      y={baselineY + 20}
                      textAnchor="middle"
                      className="tix-chart-axis-label"
                    >
                      {p.item.label}
                    </text>
                  );
                })}

                {/* Con trỏ hover với đường dọc nét đứt và chấm điểm */}
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

              {/* Tooltip nổi chi tiết khi di chuột */}
              {hoveredPoint && (
                <div
                  className="tix-chart-floating-tooltip"
                  style={{
                    left: `${(hoveredPoint.x / chartWidth) * 100}%`,
                    top: `${Math.max(5, (hoveredPoint.y / chartHeight) * 100 - 14)}%`,
                  }}
                >
                  <div className="tix-tooltip-value">
                    <strong>{hoveredPoint.item.tickets}</strong>{" "}
                    {t("organizerHub.unitTickets", { defaultValue: "vé" })}
                  </div>
                  {Number(hoveredPoint.item.revenue) > 0 && (
                    <div className="tix-tooltip-revenue">
                      {formatMoney(hoveredPoint.item.revenue, salesChart?.currency)}
                    </div>
                  )}
                </div>
              )}
            </div>}
          </div>

          {/* Right: Thuần Sự Kiện Bán Chạy Nhất (Top Selling Events) */}
          <div className="tix-tickets-type-card tix-top-events-card">
            <div className="tix-card-header">
              <div>
                <h3 className="tix-card-title">
                  {t("organizerHub.topEventsTitle", { defaultValue: "Sự kiện bán chạy nhất" })}
                </h3>
                <p className="tix-card-subtitle">
                  {t("organizerHub.topEventsSubtitle", {
                    defaultValue: "Xếp hạng theo số vé bán ra và doanh thu thực tế",
                  })}
                </p>
              </div>
            </div>

            <div className="tix-tickets-type-list">
              {eventSalesQuery.isPending || eventSalesQuery.isError ? (
                <p role={eventSalesQuery.isError ? "alert" : "status"}>{eventSalesQuery.isError ? loadFailed : loadingData}</p>
              ) : topEvents.length === 0 ? (
                <div className="tix-top-events-empty">
                  <Ticket size={28} className="tix-empty-icon" />
                  <span className="tix-empty-title">
                    {t("organizerHub.topEventsEmptyTitle", { defaultValue: "Chưa có vé bán ra" })}
                  </span>
                  <p className="tix-empty-desc">
                    {t("organizerHub.topEventsEmptyDesc", {
                      defaultValue:
                        "Danh sách sẽ tự động xếp hạng các sự kiện có lượt mua vé cao nhất khi phát sinh doanh số.",
                    })}
                  </p>
                </div>
              ) : (
                topEvents.map((evt, idx) => (
                  <div key={evt.eventId} className="tix-ticket-item-row">
                    <div className="tix-ticket-item-left">
                      <div className={`tix-event-rank-badge rank-${idx + 1}`}>#{idx + 1}</div>
                      <div className="tix-ticket-item-details">
                        <span className="tix-ticket-name" title={evt.eventTitle}>
                          {evt.eventTitle}
                        </span>
                        <div className="tix-ticket-meta-row">
                          <span className="tix-ticket-price-sub">
                            {formatMoney(evt.soldRevenue, eventSalesData?.currency)}
                          </span>
                        </div>
                      </div>
                    </div>

                    <div className="tix-ticket-item-right">
                      <span className="tix-ticket-qty-badge">
                        <small>{t("organizerHub.soldLabel", { defaultValue: "Đã bán" })}</small>
                        {evt.soldTicketCount.toLocaleString(locale)}{" "}
                        {t("organizerHub.unitTickets", { defaultValue: "vé" })}
                      </span>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        {/* ============================================================
            5. BOTTOM ROW: BẢNG HIỆU QUẢ KINH DOANH TỪNG SỰ KIỆN
            ============================================================ */}
        <div className="tix-orders-card">
          <div className="tix-orders-toolbar">
            <div>
              <h3 className="tix-card-title">
                {t("organizerHub.tableTitle", { defaultValue: "Hiệu quả kinh doanh theo sự kiện" })}
              </h3>
              <p className="tix-card-subtitle">
                {t("organizerHub.tableSubtitle", {
                  defaultValue: "Danh sách sự kiện theo trang kèm số vé và doanh số trong kỳ",
                })}
              </p>
            </div>

            <div className="tix-orders-actions">
              {/* Lọc nhanh trong trang */}
              <div className="tix-search-input-wrap tix-hub-search-wrap">
                <Search size={15} />
                <input
                  type="text"
                  placeholder={t("organizerHub.searchPagePlaceholder", {
                    defaultValue: "Lọc nhanh trong trang (tên, ID)...",
                  })}
                  value={searchQuery}
                  onChange={(e) => {
                    setSearchQuery(e.target.value);
                  }}
                  className="tix-search-input"
                />
                {searchQuery && (
                  <button
                    type="button"
                    className="tix-search-clear-btn"
                    onClick={() => {
                      setSearchQuery("");
                    }}
                    title={t("organizerHub.clearSearch", { defaultValue: "Xóa từ khóa tìm kiếm" })}
                    aria-label={t("organizerHub.clearSearch", { defaultValue: "Xóa từ khóa tìm kiếm" })}
                  >
                    <X size={13} />
                  </button>
                )}
              </div>

              {/* Dropdown Lọc theo trạng thái */}
              <div
                className={`tix-dropdown-wrap ${isStatusDropdownOpen ? "is-open" : ""}`}
                ref={statusDropdownRef}
              >
                <button
                  type="button"
                  className="tix-filter-select-btn"
                  onClick={() => {
                    setIsStatusDropdownOpen((prev) => !prev);
                  }}
                  title="Lọc theo trạng thái sự kiện"
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
                    {SALES_EVENT_STATUSES.map((status) => (
                      <button
                        key={status}
                        type="button"
                        role="option"
                        aria-selected={statusFilter === status}
                        className={`tix-dropdown-item ${statusFilter === status ? "is-active" : ""}`}
                        onClick={() => {
                          setStatusFilter(status);
                          setCursorStack([null]);
                          setTablePage(0);
                          setIsStatusDropdownOpen(false);
                        }}
                      >
                        <span>{getStatusLabel(status)}</span>
                        {statusFilter === status && (
                          <Check size={14} className="tix-check-icon" />
                        )}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              {/* Nút Xuất báo cáo CSV chuẩn UI */}
              <button
                type="button"
                className="tix-export-btn"
                onClick={handleExportCSV}
                disabled={!isExportReady}
                title={t("organizerHub.exportReportTitle", {
                  defaultValue: "Xuất báo cáo danh sách sự kiện (file CSV)",
                })}
                aria-label={t("organizerHub.exportReport", { defaultValue: "Xuất báo cáo" })}
              >
                <Download size={14} />
                <span>{t("organizerHub.exportReport", { defaultValue: "Xuất báo cáo" })}</span>
              </button>
            </div>
          </div>

          <div className="tix-table-wrapper">
            <table className="tix-orders-table">
              <thead>
                <tr>
                  <th scope="col">{t("organizerHub.colEventName", { defaultValue: "Tên sự kiện" })}</th>
                  <th scope="col">{t("organizerHub.colStatus", { defaultValue: "Trạng thái" })}</th>
                  <th scope="col">{t("organizerHub.colMode", { defaultValue: "Hình thức" })}</th>
                  <th scope="col">{t("organizerHub.colTime", { defaultValue: "Thời gian" })}</th>
                  <th scope="col">{t("organizerHub.colSoldTickets", { defaultValue: "Vé đã bán" })}</th>
                  <th scope="col">{t("organizerHub.colRevenue", { defaultValue: "Doanh thu" })}</th>
                  <th scope="col" style={{ textAlign: "right" }}>
                    {t("organizerHub.colActions", { defaultValue: "Thao tác" })}
                  </th>
                </tr>
              </thead>
              <tbody>
                {eventsQuery.isError ? (
                  <tr><td colSpan={7} role="alert">{loadFailed}</td></tr>
                ) : eventsQuery.isPending ? (
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
                      {t("organizerHub.loadingEvents", {
                        defaultValue: "Đang tải danh sách sự kiện...",
                      })}
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
                            {t("organizerHub.emptyEvents", {
                              defaultValue:
                                "Bạn chưa tạo sự kiện nào. Bắt đầu ngay bằng cách tạo sự kiện đầu tiên!",
                            })}
                          </span>
                          <Link
                            to="/organizer/events/new"
                            className="tix-primary-cta-btn"
                            style={{ marginTop: "8px" }}
                          >
                            <PlusCircle size={15} />
                            <span>
                              {t("organizerHub.createEventNow", {
                                defaultValue: "Tạo sự kiện ngay",
                              })}
                            </span>
                          </Link>
                        </div>
                      ) : (
                        t("organizerHub.noMatchingEvents", {
                          defaultValue: "Không tìm thấy sự kiện nào phù hợp với bộ lọc.",
                        })
                      )}
                    </td>
                  </tr>
                ) : (
                  paginatedEvents.map((evt, index) => (
                    <tr key={evt.id}>
                      <td>
                        <div className="tix-event-cell">
                          <span className="tix-event-title-main" title={evt.title}>
                            {evt.title}
                          </span>
                          <span className="tix-event-id-sub">ID: {evt.id.slice(0, 8)}...</span>
                        </div>
                      </td>
                      <td>
                        <span
                          className={`tix-status-badge ${
                            evt.status === "PUBLISHED"
                              ? "is-published"
                              : evt.status === "APPROVED"
                              ? "is-approved"
                              : evt.status === "PENDING_APPROVAL"
                              ? "is-pending"
                              : evt.status === "CANCELLED"
                              ? "is-cancelled"
                              : "is-draft"
                          }`}
                          data-status={evt.status}
                        >
                          {getStatusLabel(evt.status)}
                        </span>
                      </td>
                      <td>
                        <span
                          style={{
                            fontSize: "12px",
                            color: "var(--text-subtle, #565e69)",
                            fontWeight: 600,
                          }}
                        >
                          {evt.mode === "SEAT_MAP"
                            ? t("organizerHub.modeSeatMap", { defaultValue: "Sơ đồ ghế" })
                            : t("organizerHub.modeQuantity", { defaultValue: "Vé số lượng" })}
                        </span>
                      </td>
                      <td>
                        <div className="tix-schedule-cell">
                          <span>{evt.date}</span>
                          {evt.time && <span className="tix-schedule-time">{evt.time}</span>}
                        </div>
                      </td>
                      <td>
                        <span className="tix-sold-count">
                          {evt.soldTickets?.toLocaleString(locale) ?? evt.salesPlaceholder}
                        </span>
                      </td>
                      <td>
                        <strong className="tix-revenue-text">
                          {evt.revenue === undefined ? evt.salesPlaceholder : formatMoney(evt.revenue, evt.currency)}
                        </strong>
                      </td>
                      <td style={{ textAlign: "right" }}>
                        <div
                          className="tix-row-action-wrap"
                          ref={openActionMenuId === evt.id ? actionMenuRef : null}
                        >
                          <button
                            type="button"
                            className={`tix-row-action-trigger ${openActionMenuId === evt.id ? "is-open" : ""}`}
                            onClick={() =>
                              setOpenActionMenuId((prev) => (prev === evt.id ? null : evt.id))
                            }
                            title={t("organizerHub.actionOptions", {
                              defaultValue: "Tùy chọn thao tác",
                            })}
                            aria-label={t("organizerHub.actionOptions", {
                              defaultValue: "Tùy chọn thao tác",
                            })}
                            aria-haspopup="true"
                            aria-expanded={openActionMenuId === evt.id}
                          >
                            <MoreHorizontal size={16} />
                          </button>

                          {openActionMenuId === evt.id && (
                            <div
                              className={`tix-row-action-menu ${
                                index >= paginatedEvents.length - 2 && paginatedEvents.length >= 3
                                  ? "open-up"
                                  : ""
                              }`}
                              role="menu"
                            >
                              <button
                                type="button"
                                role="menuitem"
                                className="tix-action-menu-item"
                                onClick={() => {
                                  setOpenActionMenuId(null);
                                  navigate(`/organizer/events/${evt.id}/edit`);
                                }}
                              >
                                <FileEdit size={14} />
                                <span>
                                  {t("organizerHub.actionEditEvent", {
                                    defaultValue: "Chỉnh sửa sự kiện",
                                  })}
                                </span>
                              </button>

                              <button
                                type="button"
                                role="menuitem"
                                className="tix-action-menu-item"
                                onClick={() => {
                                  setOpenActionMenuId(null);
                                  navigate(
                                    evt.mode === "SEAT_MAP"
                                      ? `/organizer/events/${evt.id}/seat-map`
                                      : `/organizer/events/${evt.id}/ticket-types`
                                  );
                                }}
                              >
                                {evt.mode === "SEAT_MAP" ? (
                                  <Armchair size={14} />
                                ) : (
                                  <Ticket size={14} />
                                )}
                                <span>
                                  {evt.mode === "SEAT_MAP"
                                    ? t("organizerHub.actionManageSeatMap", {
                                        defaultValue: "Quản lý sơ đồ ghế",
                                      })
                                    : t("organizerHub.actionManageTicketTypes", {
                                        defaultValue: "Quản lý hạng vé",
                                      })}
                                </span>
                              </button>

                              <button
                                type="button"
                                role="menuitem"
                                className="tix-action-menu-item"
                                onClick={(e) => handleCopyId(evt.id, e)}
                              >
                                {copiedId === evt.id ? (
                                  <>
                                    <Check size={14} color="#10b981" />
                                    <span style={{ color: "#10b981", fontWeight: 600 }}>
                                      {t("organizerHub.copiedId", { defaultValue: "Đã sao chép mã!" })}
                                    </span>
                                  </>
                                ) : (
                                  <>
                                    <Copy size={14} />
                                    <span>
                                      {t("organizerHub.actionCopyId", {
                                        defaultValue: "Sao chép ID sự kiện",
                                      })}
                                    </span>
                                  </>
                                )}
                              </button>

                              {evt.status === "PUBLISHED" && (
                                <>
                                  <div className="tix-action-menu-separator" />
                                  <a
                                    href={`/event/${evt.id}`}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    role="menuitem"
                                    className="tix-action-menu-item"
                                    onClick={() => setOpenActionMenuId(null)}
                                  >
                                    <ExternalLink size={14} />
                                    <span>
                                      {t("organizerHub.actionViewPublic", {
                                        defaultValue: "Xem trang bán vé",
                                      })}
                                    </span>
                                  </a>
                                </>
                              )}
                            </div>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* Phân trang theo cursor */}
          {(canGoPrev || canGoNext) && (
            <div className="tix-pagination-wrapper">
              <div className="tix-pagination-box">
                <button
                  type="button"
                  className="tix-page-arrow-btn"
                  disabled={!canGoPrev || eventsQuery.isPending}
                  onClick={() => setTablePage((p) => Math.max(0, p - 1))}
                  aria-label={t("organizerHub.prevPage", { defaultValue: "Trang trước" })}
                >
                  <ChevronLeft size={15} />
                </button>

                <span className="tix-page-indicator">
                  {String(tablePage + 1).padStart(2, "0")}
                </span>

                <button
                  type="button"
                  className="tix-page-arrow-btn"
                  disabled={!canGoNext || eventsQuery.isPending}
                  onClick={() => {
                    if (!nextCursor) return;
                    setCursorStack((prev) => [...prev.slice(0, tablePage + 1), nextCursor]);
                    setTablePage((p) => p + 1);
                  }}
                  aria-label={t("organizerHub.nextPage", { defaultValue: "Trang sau" })}
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

export default function OrganizerHubPage() {
  const { keycloak } = useKeycloak();
  const { ready } = useOrganizerGate();
  const subject = keycloak.tokenParsed?.sub;

  return (
    <OrganizerLayout hideSidebar hideHeader className="organizer-hub-page" requireOrganizer>
      {ready && subject && (
        <TixOrganizerDashboard
          key={`${keycloak.tokenParsed?.iss}:${subject}`}
          subject={subject}
          issuer={keycloak.tokenParsed?.iss}
        />
      )}
    </OrganizerLayout>
  );
}
