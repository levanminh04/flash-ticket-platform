import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import { isAxiosError } from "axios";
import {
  AlertTriangle,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  Copy,
  FileText,
  LoaderCircle,
  RotateCw,
  Search,
  ShieldAlert,
  X,
} from "lucide-react";
import { toast } from "react-toastify";
import Swal from "sweetalert2";

import TixAdminTopNav from "../../components/admin/TixAdminTopNav";
import { hasRealmRole } from "../../lib/auth";
import {
  adminRefundService,
  type RefundAdminView,
  type RefundReason,
  type RefundState,
} from "../../services/adminRefundService";
import "../Organizer/organizer-dashboard.css";
import "./admin-tix.css";

function formatCurrency(amount: number, currency = "VND"): string {
  if (!Number.isFinite(amount)) return `0 ${currency}`;
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
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

const TAB_STATES: RefundState[] = ["FAILED", "PENDING", "PROCESSING", "SUCCEEDED"];

const TAB_ICONS: Record<RefundState, typeof AlertTriangle> = {
  FAILED: AlertTriangle,
  PENDING: Clock,
  PROCESSING: RotateCw,
  SUCCEEDED: CheckCircle2,
};

export function AdminRefundsPage() {
  const { t, i18n } = useTranslation();
  const { keycloak, initialized } = useKeycloak();
  const qc = useQueryClient();

  const isVi = !i18n.resolvedLanguage?.startsWith("en");
  const locale = isVi ? "vi-VN" : "en-US";
  const copyText = (vi: string, en: string) => (isVi ? vi : en);

  const [selectedState, setSelectedState] = useState<RefundState>("FAILED");
  const [page, setPage] = useState(0);
  const [searchTerm, setSearchTerm] = useState("");
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [activeActionRefundId, setActiveActionRefundId] = useState<string | null>(null);

  useEffect(() => {
    if (!copiedId) return;
    const timer = window.setTimeout(() => setCopiedId(null), 1500);
    return () => window.clearTimeout(timer);
  }, [copiedId]);

  const isAuthenticated = Boolean(keycloak.authenticated);
  const isAdmin = hasRealmRole(keycloak.tokenParsed, "ADMIN");

  const refundsQuery = useQuery({
    queryKey: ["admin-refunds", selectedState, page],
    queryFn: ({ signal }) => adminRefundService.getRefunds(selectedState, page, 20, signal),
    enabled: initialized && isAuthenticated && isAdmin,
  });

  const retryMutation = useMutation({
    mutationFn: (refundId: string) => adminRefundService.retryRefund(refundId),
    onMutate: (refundId) => {
      setActiveActionRefundId(refundId);
    },
    onSuccess: async () => {
      toast.success(
        t("adminRefunds.retrySuccess", copyText("Đã gửi yêu cầu thử lại hoàn tiền thành công.", "Refund retry requested.")),
      );
      await qc.invalidateQueries({ queryKey: ["admin-refunds"] });
    },
    onError: (err) => {
      const msg = isAxiosError<{ message?: string }>(err)
        ? err.response?.data?.message || t("adminRefunds.retryFailed", copyText("Thao tác thử lại thất bại.", "Retry failed."))
        : t("adminRefunds.retryFailed", copyText("Thao tác thử lại thất bại.", "Retry failed."));
      toast.error(msg);
    },
    onSettled: () => {
      setActiveActionRefundId(null);
    },
  });

  const manualSettlementMutation = useMutation({
    mutationFn: ({
      refundId,
      externalReference,
    }: {
      refundId: string;
      externalReference: string;
    }) => adminRefundService.settleRefundManually(refundId, externalReference),
    onMutate: ({ refundId }) => {
      setActiveActionRefundId(refundId);
    },
    onSuccess: async () => {
      toast.success(
        t(
          "adminRefunds.manualSettlementSuccess",
          copyText("Đã xác nhận đối soát hoàn tiền thủ công thành công.", "Manual settlement recorded."),
        ),
      );
      await qc.invalidateQueries({ queryKey: ["admin-refunds"] });
    },
    onSettled: () => {
      setActiveActionRefundId(null);
    },
  });

  const handleCopy = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedId(text);
      toast.success(copyText(`Đã sao chép ${label}`, `Copied ${label}`));
    } catch {
      toast.error(copyText("Không thể sao chép vào clipboard.", "Unable to copy to clipboard."));
    }
  };

  const handleManualSettlement = async (item: RefundAdminView) => {
    const contextText = copyText(
      `Nhập mã tham chiếu giao dịch hoàn tiền ngoài hệ thống cho giao dịch ${shortId(item.refundId)} (${formatCurrency(Number(item.amount), item.currency || "VND")}).`,
      `Enter the external refund transaction reference for ${shortId(item.refundId)} (${formatCurrency(Number(item.amount), item.currency || "VND")}).`,
    );

    await Swal.fire({
      title: t("adminRefunds.manualSettlementTitle", copyText("Đối soát hoàn tiền thủ công", "Manual refund settlement")),
      html: `<p class="tix-admin-swal-context">${escapeHtml(contextText)}</p>`,
      input: "text",
      inputLabel: t("adminRefunds.externalReferenceLabel", copyText("Mã tham chiếu giao dịch ngoài", "External transaction reference")),
      inputAttributes: {
        maxlength: "255",
        placeholder: copyText("VD: VCB-FT240123456", "e.g. VCB-FT240123456"),
        "aria-label": t("adminRefunds.externalReferenceLabel", copyText("Mã tham chiếu giao dịch ngoài", "External transaction reference")),
      },
      showCancelButton: true,
      reverseButtons: true,
      confirmButtonText: t("adminRefunds.confirmSettlement", copyText("Xác nhận đã hoàn tiền", "Confirm settlement")),
      cancelButtonText: t("adminRefunds.cancel", copyText("Hủy", "Cancel")),
      buttonsStyling: false,
      showLoaderOnConfirm: true,
      allowOutsideClick: () => !Swal.isLoading(),
      allowEscapeKey: () => !Swal.isLoading(),
      customClass: {
        container: "tix-admin-swal-container",
        popup: "tix-admin-swal-popup",
        title: "tix-admin-swal-title",
        htmlContainer: "tix-admin-swal-html",
        inputLabel: "tix-admin-swal-label",
        input: "tix-admin-swal-input",
        validationMessage: "tix-admin-swal-validation",
        actions: "tix-admin-swal-actions",
        confirmButton: "tix-admin-swal-confirm-btn",
        cancelButton: "tix-admin-swal-cancel-btn",
      },
      didOpen: () => {
        const input = Swal.getInput();
        if (input) setTimeout(() => input.focus(), 60);
      },
      inputValidator: (val) => {
        const trimmed = val ? val.trim() : "";
        if (!trimmed) {
          return t("adminRefunds.refRequired", copyText("Vui lòng nhập mã tham chiếu giao dịch ngoài.", "Please enter an external reference."));
        }
        if (trimmed.length > 255) {
          return copyText("Mã tham chiếu tối đa 255 ký tự.", "Reference must be at most 255 characters.");
        }
        return null;
      },
      preConfirm: async (val) => {
        try {
          await manualSettlementMutation.mutateAsync({
            refundId: item.refundId,
            externalReference: String(val).trim(),
          });
        } catch (err) {
          const msg = isAxiosError<{ message?: string }>(err)
            ? err.response?.data?.message || t("adminRefunds.manualSettlementFailed", copyText("Ghi nhận hoàn tiền thất bại.", "Settlement failed."))
            : t("adminRefunds.manualSettlementFailed", copyText("Ghi nhận hoàn tiền thất bại.", "Settlement failed."));
          Swal.showValidationMessage(msg);
          return false;
        }
      },
    });
  };

  const renderReasonLabel = (reason: RefundReason) => {
    switch (reason) {
      case "EVENT_CANCELLED":
        return t("adminRefunds.reasons.EVENT_CANCELLED", copyText("Hủy sự kiện", "Event cancelled"));
      case "LATE_PAYMENT":
        return t("adminRefunds.reasons.LATE_PAYMENT", copyText("Thanh toán muộn / hết hạn", "Late / expired payment"));
      case "TICKET_ISSUANCE_FAILED":
        return t("adminRefunds.reasons.TICKET_ISSUANCE_FAILED", copyText("Lỗi phát vé", "Ticket issuance failed"));
      case "DUPLICATE_PAYMENT":
        return t("adminRefunds.reasons.DUPLICATE_PAYMENT", copyText("Thanh toán trùng", "Duplicate payment"));
      default:
        return reason;
    }
  };

  const renderStateBadge = (state: RefundState) => {
    switch (state) {
      case "FAILED":
        return (
          <span className="tix-status-badge is-failed">
            <AlertTriangle size={12} aria-hidden="true" />
            <span>{copyText("Thất bại", "Failed")}</span>
          </span>
        );
      case "PENDING":
        return (
          <span className="tix-status-badge is-pending">
            <Clock size={12} aria-hidden="true" />
            <span>{t("adminRefunds.states.PENDING", copyText("Chờ xử lý", "Pending"))}</span>
          </span>
        );
      case "PROCESSING":
        return (
          <span className="tix-status-badge is-processing">
            <RotateCw size={12} aria-hidden="true" />
            <span>{t("adminRefunds.states.PROCESSING", copyText("Đang xử lý", "Processing"))}</span>
          </span>
        );
      case "SUCCEEDED":
        return (
          <span className="tix-status-badge is-succeeded">
            <CheckCircle2 size={12} aria-hidden="true" />
            <span>{t("adminRefunds.states.SUCCEEDED", copyText("Đã ghi nhận hoàn tất", "Recorded as complete"))}</span>
          </span>
        );
      default:
        return <span className="tix-status-badge">{state}</span>;
    }
  };

  const getTabLabel = (state: RefundState) => {
    switch (state) {
      case "FAILED":
        return copyText("Thất bại", "Failed");
      case "PENDING":
        return t("adminRefunds.tabs.PENDING", copyText("Chờ xử lý", "Pending"));
      case "PROCESSING":
        return t("adminRefunds.tabs.PROCESSING", copyText("Đang xử lý", "Processing"));
      case "SUCCEEDED":
        return t("adminRefunds.tabs.SUCCEEDED", copyText("Đã ghi nhận hoàn tất", "Recorded as complete"));
    }
  };

  const data = refundsQuery.data;
  const refunds = data?.items ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;
  const showActionsColumn = selectedState === "FAILED";

  const normalizedSearch = searchTerm.trim().toLowerCase();
  const visibleRefunds = useMemo(() => {
    if (!normalizedSearch) return refunds;
    return refunds.filter((item) => {
      const reasonText = renderReasonLabel(item.reason).toLowerCase();
      return (
        item.refundId.toLowerCase().includes(normalizedSearch) ||
        item.orderId.toLowerCase().includes(normalizedSearch) ||
        item.chargeId.toLowerCase().includes(normalizedSearch) ||
        (item.externalReference ?? "").toLowerCase().includes(normalizedSearch) ||
        (item.settledBy ?? "").toLowerCase().includes(normalizedSearch) ||
        (item.lastAttemptOutcome ?? "").toLowerCase().includes(normalizedSearch) ||
        reasonText.includes(normalizedSearch)
      );
    });
  }, [refunds, normalizedSearch, isVi]);

  if (!initialized) {
    return (
      <div className="tix-dashboard-page tix-admin-page">
        <div className="tix-dashboard-container">
          <TixAdminTopNav activeTab="refunds" />
          <div className="tix-orders-card tix-admin-state-box" role="status">
            <LoaderCircle size={26} className="tix-spin" aria-hidden="true" />
            <span>{t("adminRefunds.loadingList", copyText("Đang tải danh sách hoàn tiền...", "Loading refunds..."))}</span>
          </div>
        </div>
      </div>
    );
  }

  if (!isAuthenticated || !isAdmin) {
    return (
      <div className="tix-dashboard-page tix-admin-page">
        <div className="tix-dashboard-container">
          <TixAdminTopNav activeTab="refunds" />
          <div className="tix-orders-card tix-admin-state-box" role="alert">
            <ShieldAlert size={32} color="#dc2626" aria-hidden="true" />
            <strong style={{ color: "var(--text-strong, #15171a)" }}>
              {t("adminRefunds.accessDeniedTitle", copyText("Truy cập bị từ chối", "Access denied"))}
            </strong>
            <span>
              {t(
                "adminRefunds.accessDeniedDesc",
                copyText(
                  "Bạn cần đăng nhập với tài khoản có quyền Quản trị viên (ADMIN) để xem trang này.",
                  "You must sign in with an Administrator (ADMIN) account to access this page.",
                ),
              )}
            </span>
            {!isAuthenticated && (
              <button
                type="button"
                className="tix-admin-btn tix-admin-btn-primary"
                onClick={() => void keycloak.login()}
              >
                {t("nav.login", copyText("Đăng nhập", "Sign in"))}
              </button>
            )}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="tix-dashboard-page tix-admin-page">
      <div className="tix-dashboard-container">
        <TixAdminTopNav activeTab="refunds" />

        {/* Overview Header */}
        <div className="tix-overview-header">
          <div className="tix-overview-header-left">
            <div className="tix-overview-title-block">
              <h1 className="tix-overview-main-title">
                {t("adminRefunds.title", copyText("Quản lý hoàn tiền", "Refund Management"))}
              </h1>
              <p className="tix-overview-subtitle">
                {copyText(
                  "Theo dõi yêu cầu hoàn tiền, thử lại giao dịch lỗi và ghi nhận đối soát hoàn tiền thủ công.",
                  "Track refund requests, retry failed refunds, and record manual settlements.",
                )}
              </p>
            </div>
          </div>
        </div>

        {/* Sub-tabs & Search */}
        <div className="tix-subtabs-row">
          <div className="tix-subtabs-group" role="group" aria-label={copyText("Trạng thái hoàn tiền", "Refund status")}>
            {TAB_STATES.map((state) => {
              const Icon = TAB_ICONS[state];
              return (
                <button
                  key={state}
                  type="button"
                  className={`tix-subtab-btn ${selectedState === state ? "active" : ""}`}
                  onClick={() => {
                    setSelectedState(state);
                    setPage(0);
                  }}
                >
                  <Icon size={14} aria-hidden="true" />
                  <span>{getTabLabel(state)}</span>
                </button>
              );
            })}
          </div>

          <div className="tix-search-input-wrap">
            <Search size={15} aria-hidden="true" />
            <input
              type="search"
              className="tix-search-input"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder={copyText("Lọc trên trang hiện tại (mã hoàn, mã đơn, tham chiếu)...", "Filter current page (refund ID, order ID, ref)...")}
              aria-label={copyText("Tìm giao dịch hoàn tiền trên trang hiện tại", "Filter refund transactions on current page")}
            />
            {searchTerm && (
              <button
                type="button"
                className="tix-search-clear-btn"
                onClick={() => setSearchTerm("")}
                aria-label={t("organizerHub.clearSearch", copyText("Xóa từ khóa tìm kiếm", "Clear search"))}
              >
                <X size={13} />
              </button>
            )}
          </div>
        </div>

        {/* Refunds Table Card */}
        <section className="tix-orders-card" aria-busy={refundsQuery.isFetching}>
          <div className="tix-card-header" style={{ marginBottom: 16 }}>
            <div>
              <h2 className="tix-card-title">
                {copyText("Danh sách giao dịch hoàn tiền", "Refund Transactions")}
              </h2>
              <p className="tix-card-subtitle">
                {refundsQuery.isSuccess
                  ? normalizedSearch
                    ? copyText(
                        `${visibleRefunds.length} giao dịch khớp trong ${refunds.length} giao dịch trang ${page + 1} (tổng ${totalElements.toLocaleString(locale)})`,
                        `${visibleRefunds.length} matching in ${refunds.length} items on page ${page + 1} (${totalElements.toLocaleString(locale)} total)`,
                      )
                    : copyText(
                        `Tổng ${totalElements.toLocaleString(locale)} giao dịch`,
                        `${totalElements.toLocaleString(locale)} total transactions`,
                      )
                  : copyText(
                      "Theo dõi trạng thái xử lý và đối soát từng giao dịch hoàn tiền.",
                      "Track processing state and settlement details for each refund.",
                    )}
              </p>
            </div>
          </div>

          {refundsQuery.isPending ? (
            <div className="tix-admin-state-box" role="status">
              <LoaderCircle size={26} className="tix-spin" aria-hidden="true" />
              <span>{t("adminRefunds.loadingList", copyText("Đang tải danh sách yêu cầu hoàn tiền...", "Loading refund transactions..."))}</span>
            </div>
          ) : refundsQuery.isError ? (
            <div className="tix-admin-error-box" role="alert">
              <span>{t("adminRefunds.loadFailed", copyText("Không thể tải danh sách giao dịch hoàn tiền.", "Failed to load refund transactions."))}</span>
              <button
                type="button"
                className="tix-admin-btn tix-admin-btn-secondary"
                disabled={refundsQuery.isFetching}
                onClick={() => void refundsQuery.refetch()}
              >
                {t("profile.retry", copyText("Thử lại", "Retry"))}
              </button>
            </div>
          ) : visibleRefunds.length === 0 ? (
            <div className="tix-admin-state-box">
              {normalizedSearch ? (
                <FileText size={28} color="var(--text-muted, #666b74)" aria-hidden="true" />
              ) : (
                <CheckCircle2 size={28} color="#16a34a" aria-hidden="true" />
              )}
              <span>
                {normalizedSearch
                  ? copyText(
                      "Không tìm thấy giao dịch nào khớp với từ khóa trên trang hiện tại.",
                      "No transactions match your search on the current page.",
                    )
                  : t(
                      "adminRefunds.emptyList",
                      copyText("Không có yêu cầu hoàn tiền nào ở trạng thái này.", "No refund requests in this state."),
                    )}
              </span>
            </div>
          ) : (
            <>
              <div className="tix-table-wrapper">
                <table className="tix-orders-table">
                  <thead>
                    <tr>
                      <th scope="col">{t("adminRefunds.table.refundId", copyText("Mã hoàn tiền", "Refund ID"))}</th>
                      <th scope="col">{t("adminRefunds.table.orderId", copyText("Mã đơn hàng", "Order ID"))}</th>
                      <th scope="col">{t("adminRefunds.table.amount", copyText("Số tiền", "Amount"))}</th>
                      <th scope="col">{t("adminRefunds.table.reason", copyText("Lý do hoàn", "Reason"))}</th>
                      <th scope="col">{t("adminRefunds.table.status", copyText("Trạng thái", "Status"))}</th>
                      <th scope="col">{t("adminRefunds.table.createdAt", copyText("Thời gian tạo", "Created At"))}</th>
                      <th scope="col">{copyText("Đối soát", "Settlement")}</th>
                      {showActionsColumn && (
                        <th scope="col" style={{ textAlign: "left", width: "1%" }}>
                          {t("adminRefunds.table.actions", copyText("Thao tác", "Actions"))}
                        </th>
                      )}
                    </tr>
                  </thead>
                  <tbody>
                    {visibleRefunds.map((item) => {
                      const hasSettlementInfo = Boolean(
                        item.completedAt || item.externalReference || item.settledBy,
                      );
                      const isMutatingThisRow =
                        activeActionRefundId === item.refundId &&
                        (retryMutation.isPending || manualSettlementMutation.isPending);

                      return (
                        <tr key={item.refundId}>
                          <td>
                            <div className="tix-admin-id-cell">
                              <span title={item.refundId}>{shortId(item.refundId)}</span>
                              <button
                                type="button"
                                className="tix-admin-copy-icon-btn"
                                onClick={() => void handleCopy(item.refundId, "Refund ID")}
                                aria-label={copyText("Sao chép mã hoàn tiền", "Copy Refund ID")}
                                title={item.refundId}
                              >
                                {copiedId === item.refundId ? (
                                  <Check size={13} aria-hidden="true" />
                                ) : (
                                  <Copy size={13} aria-hidden="true" />
                                )}
                              </button>
                            </div>
                          </td>
                          <td>
                            <div className="tix-admin-id-cell">
                              <span title={item.orderId}>{shortId(item.orderId)}</span>
                              <button
                                type="button"
                                className="tix-admin-copy-icon-btn"
                                onClick={() => void handleCopy(item.orderId, "Order ID")}
                                aria-label={copyText("Sao chép mã đơn hàng", "Copy Order ID")}
                                title={item.orderId}
                              >
                                {copiedId === item.orderId ? (
                                  <Check size={13} aria-hidden="true" />
                                ) : (
                                  <Copy size={13} aria-hidden="true" />
                                )}
                              </button>
                            </div>
                          </td>
                          <td>
                            <span className="tix-revenue-text">
                              {formatCurrency(Number(item.amount), item.currency || "VND")}
                            </span>
                          </td>
                          <td>
                            <span className="tix-admin-reason-text">
                              {renderReasonLabel(item.reason)}
                            </span>
                          </td>
                          <td>{renderStateBadge(item.state)}</td>
                          <td style={{ whiteSpace: "nowrap", color: "var(--text-subtle, #565e69)" }}>
                            {formatDateTime(item.createdAt, locale)}
                          </td>
                          <td>
                            {hasSettlementInfo ? (
                              <div className="tix-admin-settlement-meta">
                                {item.completedAt && (
                                  <span className="tix-admin-settlement-time">
                                    {formatDateTime(item.completedAt, locale)}
                                  </span>
                                )}
                                {item.externalReference && (
                                  <span className="tix-admin-mono" title={item.externalReference}>
                                    Ref: {item.externalReference}
                                  </span>
                                )}
                                {item.settledBy && (
                                  <span className="tix-admin-settlement-by" title={item.settledBy}>
                                    {copyText("Bởi", "By")}: {shortId(item.settledBy)}
                                  </span>
                                )}
                              </div>
                            ) : (
                              <span className="tix-admin-empty-text">
                                {copyText("Chưa đối soát", "Not settled")}
                              </span>
                            )}
                          </td>
                          {showActionsColumn && (
                            <td style={{ textAlign: "left", whiteSpace: "nowrap", width: "1%" }}>
                              <div className="tix-admin-table-actions">
                                <button
                                  type="button"
                                  className="tix-admin-btn tix-admin-btn-primary"
                                  style={{ minHeight: 32, padding: "5px 12px", fontSize: "0.8rem" }}
                                  disabled={
                                    isMutatingThisRow ||
                                    retryMutation.isPending ||
                                    manualSettlementMutation.isPending
                                  }
                                  onClick={() => retryMutation.mutate(item.refundId)}
                                >
                                  <span>{t("adminRefunds.actions.retry", copyText("Thử lại", "Retry"))}</span>
                                </button>
                                <button
                                  type="button"
                                  className="tix-admin-btn tix-admin-btn-secondary"
                                  style={{ minHeight: 32, padding: "5px 12px", fontSize: "0.8rem" }}
                                  disabled={
                                    isMutatingThisRow ||
                                    retryMutation.isPending ||
                                    manualSettlementMutation.isPending
                                  }
                                  onClick={() => void handleManualSettlement(item)}
                                >
                                  <span>{copyText("Đối soát", "Settle")}</span>
                                </button>
                              </div>
                            </td>
                          )}
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {totalPages > 1 && (
                <div className="tix-pagination-wrapper">
                  <div className="tix-pagination-box">
                    <button
                      type="button"
                      className="tix-page-arrow-btn"
                      disabled={page <= 0}
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                      aria-label={t("adminRefunds.prevBtn", copyText("Trang trước", "Previous page"))}
                    >
                      <ChevronLeft size={16} aria-hidden="true" />
                    </button>
                    <span className="tix-page-indicator">
                      {page + 1} / {totalPages}
                    </span>
                    <button
                      type="button"
                      className="tix-page-arrow-btn"
                      disabled={page + 1 >= totalPages}
                      onClick={() => setPage((p) => p + 1)}
                      aria-label={t("adminRefunds.nextBtn", copyText("Trang sau", "Next page"))}
                    >
                      <ChevronRight size={16} aria-hidden="true" />
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </section>
      </div>
    </div>
  );
}

export default AdminRefundsPage;
