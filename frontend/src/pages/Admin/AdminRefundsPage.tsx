import { useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  RotateCcw,
  CheckCircle2,
  AlertTriangle,
  Clock,
  Search,
  Copy,
  Check,
  FileText,
  ShieldAlert,
  LoaderCircle,
} from "lucide-react";
import { toast } from "react-toastify";
import Swal from "sweetalert2";
import { hasRealmRole } from "../../lib/auth";
import {
  adminRefundService,
  RefundState,
  RefundReason,
  RefundAdminView,
} from "../../services/adminRefundService";
import "./admin-refunds.css";

const TAB_STATES: { state: RefundState; labelKey: string }[] = [
  { state: "FAILED", labelKey: "adminRefunds.tabs.FAILED" },
  { state: "PENDING", labelKey: "adminRefunds.tabs.PENDING" },
  { state: "PROCESSING", labelKey: "adminRefunds.tabs.PROCESSING" },
  { state: "SUCCEEDED", labelKey: "adminRefunds.tabs.SUCCEEDED" },
];

function formatVnd(amount: string | number) {
  const num = typeof amount === "string" ? parseFloat(amount) : amount;
  if (isNaN(num)) return "0 ₫";
  return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(num);
}

function formatDate(isoString: string | null) {
  if (!isoString) return "—";
  try {
    const d = new Date(isoString);
    return d.toLocaleString("vi-VN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  } catch {
    return isoString;
  }
}

export default function AdminRefundsPage() {
  const { t } = useTranslation();
  const { keycloak, initialized } = useKeycloak();
  const queryClient = useQueryClient();

  const [selectedState, setSelectedState] = useState<RefundState>("FAILED");
  const [page, setPage] = useState(0);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // Search event refund progress
  const [eventInput, setEventInput] = useState("");
  const [activeEventId, setActiveEventId] = useState<string | null>(null);

  const isAuthenticated = initialized && !!keycloak?.authenticated;
  const isAdmin = hasRealmRole(keycloak?.tokenParsed, "ADMIN");

  // Fetch Refunds by State
  const refundsQuery = useQuery({
    queryKey: ["admin-refunds", selectedState, page],
    queryFn: ({ signal }) => adminRefundService.getRefunds(selectedState, page, 20, signal),
    enabled: isAuthenticated && isAdmin,
    retry: 1,
  });

  // Fetch Event Progress
  const eventProgressQuery = useQuery({
    queryKey: ["admin-event-refund-progress", activeEventId],
    queryFn: ({ signal }) =>
      activeEventId ? adminRefundService.getEventRefundProgress(activeEventId, signal) : null,
    enabled: isAuthenticated && isAdmin && Boolean(activeEventId),
    retry: 1,
  });

  // Mutation: Retry refund
  const retryMutation = useMutation({
    mutationFn: (refundId: string) => adminRefundService.retryRefund(refundId),
    onSuccess: () => {
      toast.success(t("adminRefunds.retrySuccess", "Thử lại yêu cầu hoàn thành công."));
      void queryClient.invalidateQueries({ queryKey: ["admin-refunds"] });
      if (activeEventId) {
        void queryClient.invalidateQueries({ queryKey: ["admin-event-refund-progress"] });
      }
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        t("adminRefunds.retryFailed", "Thử lại thất bại.");
      toast.error(msg);
    },
  });

  // Mutation: Manual settlement
  const manualSettlementMutation = useMutation({
    mutationFn: ({
      refundId,
      externalReference,
    }: {
      refundId: string;
      externalReference: string;
    }) => adminRefundService.settleRefundManually(refundId, externalReference),
    onSuccess: () => {
      toast.success(
        t("adminRefunds.manualSettlementSuccess", "Ghi nhận hoàn tiền ngoài hệ thống thành công."),
      );
      void queryClient.invalidateQueries({ queryKey: ["admin-refunds"] });
      if (activeEventId) {
        void queryClient.invalidateQueries({ queryKey: ["admin-event-refund-progress"] });
      }
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        t("adminRefunds.manualSettlementFailed", "Ghi nhận thất bại.");
      toast.error(msg);
    },
  });

  const handleCopy = (id: string) => {
    navigator.clipboard.writeText(id);
    setCopiedId(id);
    toast.info(t("adminRefunds.copied", "Đã sao chép mã!"), { autoClose: 1500 });
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleManualSettlementPrompt = async (item: RefundAdminView) => {
    const result = await Swal.fire({
      title: t("adminRefunds.manualSettlementTitle", "Ghi nhận hoàn tiền thủ công"),
      html: `
        <p style="font-size: 14px; color: #475569; margin-bottom: 12px;">
          Số tiền: <strong>${formatVnd(item.amount)}</strong><br/>
          Mã hoàn tiền: <code>${item.refundId}</code>
        </p>
      `,
      input: "text",
      inputLabel: t("adminRefunds.externalReferenceLabel", "Mã tham chiếu ngân hàng / giao dịch ngoài"),
      inputPlaceholder: "Ví dụ: FT260312345678, MB-123456789...",
      inputAttributes: {
        maxlength: "120",
        autocomplete: "off",
      },
      showCancelButton: true,
      confirmButtonText: t("adminRefunds.confirmSettlement", "Xác nhận đã hoàn tiền"),
      cancelButtonText: t("adminRefunds.cancel", "Hủy bỏ"),
      confirmButtonColor: "#0f766e",
      inputValidator: (val) => {
        const trimmed = val ? val.trim() : "";
        if (!trimmed) {
          return t("adminRefunds.refRequired", "Vui lòng nhập mã tham chiếu giao dịch ngoài!");
        }
        if (trimmed.length > 120) {
          return t("adminRefunds.refTooLong", "Mã tham chiếu tối đa 120 ký tự.");
        }
        return null;
      },
    });

    if (result.isConfirmed && result.value) {
      manualSettlementMutation.mutate({
        refundId: item.refundId,
        externalReference: result.value.trim(),
      });
    }
  };

  const handleEventSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = eventInput.trim();
    if (!trimmed) {
      setActiveEventId(null);
      return;
    }
    setActiveEventId(trimmed);
  };

  if (!isAuthenticated || !isAdmin) {
    return (
      <main className="admin-refunds-page">
        <div className="admin-refunds-container">
          <div className="admin-refunds-state">
            <ShieldAlert size={48} color="#dc2626" style={{ margin: "0 auto 16px" }} />
            <h2>{t("adminRefunds.accessDeniedTitle", "Truy cập bị từ chối")}</h2>
            <p>
              {t(
                "adminRefunds.accessDeniedDesc",
                "Bạn cần đăng nhập với tài khoản có quyền Quản trị viên (ADMIN) để xem trang này.",
              )}
            </p>
            {!isAuthenticated && (
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => void keycloak.login()}
                style={{ marginTop: 16 }}
              >
                {t("nav.login", "Đăng nhập")}
              </button>
            )}
          </div>
        </div>
      </main>
    );
  }

  const data = refundsQuery.data;
  const refunds = data?.items || [];
  const totalPages = data?.totalPages || 0;
  const totalElements = data?.totalElements || 0;

  const renderReasonBadge = (reason: RefundReason) => {
    switch (reason) {
      case "EVENT_CANCELLED":
        return <span className="badge-reason">{t("adminRefunds.reasons.EVENT_CANCELLED", "Hủy sự kiện")}</span>;
      case "LATE_PAYMENT":
        return <span className="badge-reason">{t("adminRefunds.reasons.LATE_PAYMENT", "Thanh toán muộn / hết hạn")}</span>;
      case "TICKET_ISSUANCE_FAILED":
        return <span className="badge-reason">{t("adminRefunds.reasons.TICKET_ISSUANCE_FAILED", "Lỗi phát vé")}</span>;
      case "DUPLICATE_PAYMENT":
        return <span className="badge-reason">{t("adminRefunds.reasons.DUPLICATE_PAYMENT", "Thanh toán trùng")}</span>;
      default:
        return <span className="badge-reason">{reason}</span>;
    }
  };

  const renderStateBadge = (state: RefundState) => {
    switch (state) {
      case "FAILED":
        return (
          <span className="badge badge-state-FAILED">
            <AlertTriangle size={14} />
            {t("adminRefunds.states.FAILED", "Thất bại (Cần xử lý)")}
          </span>
        );
      case "PENDING":
        return (
          <span className="badge badge-state-PENDING">
            <Clock size={14} />
            {t("adminRefunds.states.PENDING", "Chờ gửi cổng")}
          </span>
        );
      case "PROCESSING":
        return (
          <span className="badge badge-state-PROCESSING">
            <LoaderCircle size={14} className="spin" />
            {t("adminRefunds.states.PROCESSING", "Đang xử lý")}
          </span>
        );
      case "SUCCEEDED":
        return (
          <span className="badge badge-state-SUCCEEDED">
            <CheckCircle2 size={14} />
            {t("adminRefunds.states.SUCCEEDED", "Đã hoàn thành")}
          </span>
        );
      default:
        return <span className="badge">{state}</span>;
    }
  };

  return (
    <main className="admin-refunds-page">
      <div className="admin-refunds-container">
        {/* Header */}
        <div className="admin-refunds-header">
          <div>
            <h1>
              <RotateCcw size={28} color="#0f766e" />
              {t("adminRefunds.title", "Quản trị Hoàn tiền")}
            </h1>
            <p>
              {t(
                "adminRefunds.description",
                "Theo dõi, thử lại lệnh hoàn tiền qua cổng VNPay hoặc ghi nhận hoàn tiền thủ công ngoài hệ thống.",
              )}
            </p>
          </div>
        </div>

        {/* Event Progress Lookup Card */}
        <div className="admin-refunds-progress-card">
          <div className="progress-card-header">
            <h2>
              <Search size={18} color="#0f766e" />
              {t("adminRefunds.eventLookupTitle", "Tra cứu tiến độ hoàn tiền theo Sự kiện")}
            </h2>
            <form onSubmit={handleEventSearch} className="progress-search-form">
              <input
                type="text"
                className="progress-input"
                placeholder={t("adminRefunds.eventInputPlaceholder", "Nhập mã sự kiện (Event UUID)...")}
                value={eventInput}
                onChange={(e) => setEventInput(e.target.value)}
              />
              <button type="submit" className="btn btn-secondary">
                {t("adminRefunds.searchBtn", "Tra cứu")}
              </button>
            </form>
          </div>

          {activeEventId && (
            <div>
              {eventProgressQuery.isPending && (
                <div style={{ padding: "12px 0", color: "#64748b", display: "flex", alignItems: "center", gap: 8 }}>
                  <LoaderCircle size={16} className="spin" />
                  {t("adminRefunds.loadingProgress", "Đang tải dữ liệu tiến độ sự kiện...")}
                </div>
              )}
              {eventProgressQuery.isError && (
                <div style={{ padding: "12px 0", color: "#dc2626" }}>
                  {t("adminRefunds.eventNotFound", "Không tìm thấy dữ liệu hoàn tiền cho sự kiện này (hoặc sự kiện chưa từng bị hủy).")}
                </div>
              )}
              {eventProgressQuery.data && (
                <div className="progress-stats-grid">
                  <div className="progress-stat-item">
                    <span className="progress-stat-label">{t("adminRefunds.stats.selected", "Tổng đơn chọn hoàn")}</span>
                    <span className="progress-stat-value">{eventProgressQuery.data.selectedOrderCount}</span>
                  </div>
                  <div className="progress-stat-item refunded">
                    <span className="progress-stat-label">{t("adminRefunds.stats.refunded", "Đã hoàn thành")}</span>
                    <span className="progress-stat-value">{eventProgressQuery.data.refundedCount}</span>
                  </div>
                  <div className="progress-stat-item">
                    <span className="progress-stat-label">{t("adminRefunds.stats.pending", "Đang xử lý")}</span>
                    <span className="progress-stat-value">{eventProgressQuery.data.pendingCount}</span>
                  </div>
                  <div className="progress-stat-item needs-attention">
                    <span className="progress-stat-label">{t("adminRefunds.stats.needsAttention", "Cần can thiệp (Lỗi)")}</span>
                    <span className="progress-stat-value">{eventProgressQuery.data.needsAttentionCount}</span>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Tabs */}
        <div className="admin-refunds-tabs">
          {TAB_STATES.map((tab) => {
            const isActive = selectedState === tab.state;
            return (
              <button
                key={tab.state}
                type="button"
                className={`refund-tab-btn ${isActive ? "active" : ""}`}
                onClick={() => {
                  setSelectedState(tab.state);
                  setPage(0);
                }}
              >
                {t(tab.labelKey, tab.state)}
                {isActive && totalElements > 0 && (
                  <span className="refund-tab-badge">{totalElements}</span>
                )}
              </button>
            );
          })}
        </div>

        {/* Table Card */}
        <div className="admin-refunds-table-card">
          {refundsQuery.isPending && (
            <div className="admin-refunds-state">
              <LoaderCircle size={32} className="spin" color="#0f766e" style={{ margin: "0 auto 12px" }} />
              <p>{t("adminRefunds.loadingList", "Đang tải danh sách yêu cầu hoàn tiền...")}</p>
            </div>
          )}

          {refundsQuery.isError && (
            <div className="admin-refunds-state">
              <AlertTriangle size={32} color="#dc2626" style={{ margin: "0 auto 12px" }} />
              <p>{t("adminRefunds.loadFailed", "Không thể tải danh sách hoàn tiền. Vui lòng thử lại sau.")}</p>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => void refundsQuery.refetch()}
              >
                {t("adminRefunds.retryBtn", "Tải lại")}
              </button>
            </div>
          )}

          {refundsQuery.isSuccess && refunds.length === 0 && (
            <div className="admin-refunds-state">
              <CheckCircle2 size={36} color="#10b981" style={{ margin: "0 auto 12px" }} />
              <p>
                {t(
                  "adminRefunds.emptyList",
                  `Không có yêu cầu hoàn tiền nào ở trạng thái ${selectedState}.`,
                )}
              </p>
            </div>
          )}

          {refundsQuery.isSuccess && refunds.length > 0 && (
            <>
              <div style={{ overflowX: "auto" }}>
                <table className="admin-refunds-table">
                  <thead>
                    <tr>
                      <th>{t("adminRefunds.table.refundId", "Mã hoàn tiền")}</th>
                      <th>{t("adminRefunds.table.orderId", "Mã đơn hàng")}</th>
                      <th>{t("adminRefunds.table.amount", "Số tiền")}</th>
                      <th>{t("adminRefunds.table.reason", "Lý do hoàn")}</th>
                      <th>{t("adminRefunds.table.status", "Trạng thái")}</th>
                      <th>{t("adminRefunds.table.createdAt", "Thời gian tạo")}</th>
                      <th>{t("adminRefunds.table.actions", "Thao tác")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {refunds.map((item) => (
                      <tr key={item.refundId}>
                        <td>
                          <div className="id-cell" title={item.refundId}>
                            <span>{item.refundId.substring(0, 8)}...</span>
                            <button
                              type="button"
                              className="copy-btn"
                              onClick={() => handleCopy(item.refundId)}
                              title="Sao chép đầy đủ UUID"
                            >
                              {copiedId === item.refundId ? <Check size={14} color="#10b981" /> : <Copy size={14} />}
                            </button>
                          </div>
                        </td>
                        <td>
                          <div className="id-cell" title={item.orderId}>
                            <span>{item.orderId.substring(0, 8)}...</span>
                            <button
                              type="button"
                              className="copy-btn"
                              onClick={() => handleCopy(item.orderId)}
                              title="Sao chép đầy đủ UUID"
                            >
                              {copiedId === item.orderId ? <Check size={14} color="#10b981" /> : <Copy size={14} />}
                            </button>
                          </div>
                        </td>
                        <td>
                          <div className="amount-cell">{formatVnd(item.amount)}</div>
                        </td>
                        <td>{renderReasonBadge(item.reason)}</td>
                        <td>
                          {renderStateBadge(item.state)}
                          {item.externalReference && (
                            <div className="settled-info" title={item.externalReference}>
                              <strong>Mã GD ngoài:</strong> {item.externalReference}
                            </div>
                          )}
                          {item.settledBy && (
                            <div className="settled-info">
                              <strong>Duyệt bởi:</strong> {item.settledBy.substring(0, 8)}...
                            </div>
                          )}
                        </td>
                        <td>{formatDate(item.createdAt)}</td>
                        <td>
                          <div className="actions-cell">
                            {item.state === "FAILED" && (
                              <>
                                <button
                                  type="button"
                                  className="action-btn-retry"
                                  disabled={retryMutation.isPending}
                                  onClick={() => retryMutation.mutate(item.refundId)}
                                  title="Gửi lại yêu cầu hoàn tiền sang cổng VNPay"
                                >
                                  <RotateCcw size={14} />
                                  {t("adminRefunds.actions.retry", "Thử lại")}
                                </button>
                                <button
                                  type="button"
                                  className="action-btn-manual"
                                  disabled={manualSettlementMutation.isPending}
                                  onClick={() => handleManualSettlementPrompt(item)}
                                  title="Xác nhận đã chuyển khoản/hoàn tiền thủ công cho khách"
                                >
                                  <FileText size={14} />
                                  {t("adminRefunds.actions.manual", "Hoàn thủ công")}
                                </button>
                              </>
                            )}

                            {item.state === "SUCCEEDED" && (
                              <span style={{ color: "#059669", fontSize: 13, fontWeight: 600, display: "flex", alignItems: "center", gap: 4 }}>
                                <CheckCircle2 size={16} />
                                {t("adminRefunds.actions.completed", "Đã xong")}
                              </span>
                            )}

                            {(item.state === "PENDING" || item.state === "PROCESSING") && (
                              <span style={{ color: "#64748b", fontSize: 13, display: "flex", alignItems: "center", gap: 4 }}>
                                <LoaderCircle size={14} className="spin" />
                                {t("adminRefunds.actions.autoProcessing", "Hệ thống tự xử lý")}
                              </span>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Pagination */}
              <div className="admin-refunds-pagination">
                <div>
                  {t("adminRefunds.paginationInfo", {
                    total: totalElements,
                    page: page + 1,
                    pages: Math.max(totalPages, 1),
                    defaultValue: `Tổng cộng ${totalElements} bản ghi · Trang ${page + 1}/${Math.max(totalPages, 1)}`,
                  })}
                </div>
                <div className="pagination-controls">
                  <button
                    type="button"
                    className="page-btn"
                    disabled={page <= 0}
                    onClick={() => setPage((prev) => Math.max(0, prev - 1))}
                  >
                    {t("adminRefunds.prevBtn", "Trang trước")}
                  </button>
                  <button
                    type="button"
                    className="page-btn"
                    disabled={page >= totalPages - 1}
                    onClick={() => setPage((prev) => prev + 1)}
                  >
                    {t("adminRefunds.nextBtn", "Trang sau")}
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </main>
  );
}
