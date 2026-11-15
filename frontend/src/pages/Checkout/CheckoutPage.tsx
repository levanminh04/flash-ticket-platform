import { useEffect, useRef, useState } from "react";
import { isAxiosError } from "axios";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import {
  ArrowLeft,
  Calendar,
  Check,
  CheckCircle2,
  CircleAlert,
  Copy,
  Info,
  LoaderCircle,
  LogIn,
  Mail,
  MapPin,
  Tag,
  ChevronDown,
} from "lucide-react";
import { orderIdPattern } from "../../components/common/OrderLookupForm";
import { bookingService } from "../../services/bookingService";
import { eventService } from "../../services/eventService";
import { OrderView } from "../../types/bookingContract";
import { EventSummary } from "../../types/api";
import { paymentProblem, paymentService, vnPayCheckoutUrl } from "../../services/paymentService";
import "./checkout.css";

function formatEventSchedule(isoString?: string): string {
  if (!isoString) return "";
  const d = new Date(isoString);
  if (isNaN(d.getTime())) return "";
  const hours = String(d.getHours()).padStart(2, "0");
  const mins = String(d.getMinutes()).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const year = d.getFullYear();
  return `${hours}:${mins} · ${day}/${month}/${year}`;
}

export default function CheckoutPage() {
  const { t } = useTranslation();
  const { keycloak, initialized } = useKeycloak();
  const [params] = useSearchParams();
  const orderId = (params.get("orderId") || "").toLowerCase();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [receipt, setReceipt] = useState<{ scope: string; order: OrderView | null; failed?: boolean } | null>(null);
  const [event, setEvent] = useState<EventSummary | null>(null);
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [promoExpanded, setPromoExpanded] = useState(false);
  const [voucherBlockReason, setVoucherBlockReason] = useState("");
  const [now, setNow] = useState(Date.now());
  const [copiedOrderId, setCopiedOrderId] = useState(false);
  const [showCancelModal, setShowCancelModal] = useState(false);

  const submittingRef = useRef(false);
  const subject = keycloak.tokenParsed?.sub;
  const authenticated = Boolean(keycloak.authenticated && subject);
  const checkoutScope = `${keycloak.tokenParsed?.iss}:${subject}:${orderId}:${authenticated}`;
  const checkoutScopeRef = useRef(checkoutScope);
  checkoutScopeRef.current = checkoutScope;

  const currentReceipt = receipt?.scope === checkoutScope ? receipt : null;
  const order = currentReceipt?.order;
  const setOrder = (order: OrderView) => setReceipt({ scope: checkoutScope, order });

  const money = (value: string | bigint | number) => {
    try {
      return `${BigInt(value).toLocaleString("vi-VN")} đ`;
    } catch {
      return `${value} đ`;
    }
  };

  const copyOrderIdToClipboard = async () => {
    if (!order?.orderId) return;
    try {
      await navigator.clipboard.writeText(order.orderId);
      setCopiedOrderId(true);
      window.setTimeout(() => setCopiedOrderId(false), 2000);
    } catch {
      // Fallback
      setCopiedOrderId(false);
    }
  };

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && showCancelModal) {
        setShowCancelModal(false);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [showCancelModal]);

  useEffect(() => {
    setReceipt(null);
    setCode("");
    setError("");
    setNotice("");
    setVoucherBlockReason("");
    setPromoExpanded(false);
    setCopiedOrderId(false);
    setShowCancelModal(false);
    setNow(Date.now());
    submittingRef.current = false;
    setBusy(false);

    if (!initialized || !authenticated || !orderIdPattern.test(orderId)) return;

    const controller = new AbortController();
    void bookingService
      .getOrder(orderId, controller.signal)
      .then((order) => {
        if (controller.signal.aborted) return;
        setReceipt({ scope: checkoutScope, order: order?.orderId.toLowerCase() === orderId ? order : null });
      })
      .catch((err) => {
        if (controller.signal.aborted) return;
        const missing = isAxiosError(err) && err.response?.status === 404;
        setReceipt({ scope: checkoutScope, order: null, failed: !missing });
      });

    return () => controller.abort();
  }, [orderId, initialized, authenticated, checkoutScope]);

  const eventId = order?.eventId;
  useEffect(() => {
    setEvent(null);
    if (!eventId) return;

    const controller = new AbortController();
    void eventService
      .getEventDetails(eventId, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setEvent(data);
      })
      .catch(() => {});

    return () => controller.abort();
  }, [eventId, checkoutScope]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const remaining = order ? Math.max(0, Math.ceil((Date.parse(order.expiresAt) - now) / 1000)) : 0;
  const remainingMinutes = Math.floor(remaining / 60);
  const remainingSeconds = remaining % 60;
  const pending = order?.state === "PENDING_PAYMENT";
  const paymentRequested = paymentService.hasPaymentRequest(orderId);
  const canApply = Boolean(pending && remaining > 0 && !order?.paymentFrozenAt && !voucherBlockReason && !paymentRequested);

  const startPayment = async () => {
    if (!order || !pending || remaining <= 0 || submittingRef.current) return;
    submittingRef.current = true;
    setBusy(true);
    setError("");
    setNotice("");
    const requestedScope = checkoutScope;
    setVoucherBlockReason("PAYMENT_IN_PROGRESS");

    try {
      const attempt = await paymentService.initiatePayment(order.orderId);
      if (checkoutScopeRef.current !== requestedScope) return;
      const url = vnPayCheckoutUrl(attempt);
      if (url) window.location.assign(url);
      else navigate(`/payment-result?orderId=${order.orderId}`);
    } catch (err) {
      if (checkoutScopeRef.current === requestedScope) {
        setError(paymentProblem(err).message || t("paymentV1.startFailed"));
      }
    } finally {
      if (checkoutScopeRef.current === requestedScope) {
        submittingRef.current = false;
        setBusy(false);
      }
    }
  };

  const applyPromotion = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!order || !canApply || !code.trim() || submittingRef.current) return;
    submittingRef.current = true;
    setBusy(true);
    setError("");
    setNotice("");
    const requestedScope = checkoutScope;

    try {
      const updatedOrder = await bookingService.applyPromotion(order.orderId, code);
      if (checkoutScopeRef.current !== requestedScope) return;
      setOrder(updatedOrder);
      setNotice(t("checkout.voucherApplied"));
    } catch (err: unknown) {
      if (checkoutScopeRef.current !== requestedScope) return;
      const problem = err as { response?: { data?: { code?: string; message?: string } } };
      setError(problem.response?.data?.message || t("checkout.voucherFailed"));
      const problemCode = problem.response?.data?.code || "";
      if (["PRICE_FROZEN", "PAYMENT_IN_PROGRESS", "ORDER_EXPIRED", "ORDER_NOT_PENDING_PAYMENT"].includes(problemCode)) {
        setVoucherBlockReason(problemCode);
      }
    } finally {
      if (checkoutScopeRef.current === requestedScope) {
        submittingRef.current = false;
        setBusy(false);
      }
    }
  };

  const cancelOrder = async () => {
    if (!order || !pending || submittingRef.current) return;
    submittingRef.current = true;
    setBusy(true);
    setError("");
    setNotice("");
    const requestedScope = checkoutScope;

    try {
      const updatedOrder = await bookingService.cancelOrder(order.orderId);
      if (checkoutScopeRef.current !== requestedScope) return;
      setOrder(updatedOrder);
      void queryClient.invalidateQueries({ queryKey: ["buyer-orders"] });
      void queryClient.invalidateQueries({ queryKey: ["buyer-order-detail"] });
      navigate("/my-orders", { replace: true });
    } catch (err: unknown) {
      if (checkoutScopeRef.current !== requestedScope) return;
      const problem = err as { response?: { data?: { message?: string } } };
      setError(problem.response?.data?.message || t("checkout.cancelFailed"));
    } finally {
      if (checkoutScopeRef.current === requestedScope) {
        submittingRef.current = false;
        setBusy(false);
      }
    }
  };

  // State 1: Loading
  if (!initialized || (orderIdPattern.test(orderId) && !currentReceipt)) {
    return (
      <div className="stripe-checkout-center-canvas" role="status" aria-live="polite">
        <div className="stripe-checkout-state-card">
          <LoaderCircle className="stripe-checkout-spinner" size={38} aria-hidden="true" />
          <h2 className="stripe-checkout-state-title">{t("checkout.loadingOrder")}</h2>
          <p className="stripe-checkout-state-description">
            {t("checkout.loadingHint", { defaultValue: "Vui lòng chờ trong giây lát, hệ thống đang lấy dữ liệu đơn hàng." })}
          </p>
        </div>
      </div>
    );
  }

  // State 2: Unauthenticated
  if (!authenticated) {
    return (
      <div className="stripe-checkout-center-canvas">
        <div className="stripe-checkout-state-card">
          <LogIn className="stripe-checkout-state-icon-auth" size={42} aria-hidden="true" />
          <h2 className="stripe-checkout-state-title">{t("checkout.loginRequired")}</h2>
          <p className="stripe-checkout-state-description">
            {t("checkout.loginRequiredHint", {
              defaultValue: "Vui lòng đăng nhập tài khoản để xem thông tin và tiến hành thanh toán đơn hàng.",
            })}
          </p>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => void keycloak.login({ redirectUri: window.location.href })}
          >
            {t("auth.signIn")}
          </button>
        </div>
      </div>
    );
  }

  // State 3: Order Not Found / Load Failed
  if (!order) {
    return (
      <div className="stripe-checkout-center-canvas">
        <div className="stripe-checkout-state-card">
          <CircleAlert className="stripe-checkout-state-icon-error" size={42} aria-hidden="true" />
          <h2 className="stripe-checkout-state-title" role="alert">
            {t(currentReceipt?.failed ? "checkout.orderLoadFailed" : "checkout.orderUnavailable")}
          </h2>
          <p className="stripe-checkout-state-description">
            {t("checkout.orderUnavailableHint", {
              defaultValue: "Đơn hàng có thể đã hết hạn hoặc không tồn tại trên hệ thống.",
            })}
          </p>
          <Link className="btn btn-primary" to="/search">
            {t("nav.events")}
          </Link>
        </div>
      </div>
    );
  }

  // Main Focused Stripe Checkout Layout
  const returnEventPath = `/event/${order.eventId}`;

  return (
    <div className="stripe-checkout-page">
      <div className="stripe-checkout-split">
        {/* ================= TOP ROW: Navigation & Brand (aligns both columns) ================= */}
        <div className="stripe-checkout-left-top">
          <div className="stripe-checkout-left-inner">
            <Link to={returnEventPath} className="stripe-checkout-back-link">
              <ArrowLeft size={16} aria-hidden="true" />
              <span>{t("checkout.backToEvent", { defaultValue: "Trở lại sự kiện" })}</span>
            </Link>

            <Link to="/" className="stripe-checkout-brand" aria-label="FlashTicket">
              <img src="/icons/icons8-black-cat.png" alt="" width={30} height={30} />
              <span>FlashTicket</span>
            </Link>
          </div>
        </div>
        <div className="stripe-checkout-right-top" aria-hidden="true" />

        {/* ================= LEFT PANEL: Order Summary ================= */}
        <section className="stripe-checkout-left" aria-label={t("checkout.orderInfo")}>
          <div className="stripe-checkout-left-inner">
            <p className="stripe-checkout-sublabel">
              {t("checkout.orderLabel", { defaultValue: "THÔNG TIN ĐƠN HÀNG" })}
            </p>

            <div className="stripe-checkout-order-id-bar">
              <span className="stripe-checkout-order-id-label">
                {t("checkout.orderCode", { defaultValue: "Mã đơn:" })}
              </span>
              <span className="stripe-checkout-order-id-value" title={order.orderId}>
                {order.orderId}
              </span>
              <button
                type="button"
                className={`stripe-checkout-copy-btn${copiedOrderId ? " is-copied" : ""}`}
                onClick={() => void copyOrderIdToClipboard()}
                title={
                  copiedOrderId
                    ? t("checkout.copiedCode", { defaultValue: "Đã sao chép" })
                    : t("checkout.copyCode", { defaultValue: "Sao chép mã đơn" })
                }
                aria-label={t("checkout.copyCode", { defaultValue: "Sao chép mã đơn" })}
              >
                {copiedOrderId ? (
                  <>
                    <Check size={12} className="stripe-checkout-copy-check" aria-hidden="true" />
                    <span>{t("checkout.copiedCode", { defaultValue: "Đã chép" })}</span>
                  </>
                ) : (
                  <>
                    <Copy size={12} aria-hidden="true" />
                    <span>{t("checkout.copyCode", { defaultValue: "Sao chép" })}</span>
                  </>
                )}
              </button>
            </div>

            {/* Event Summary Box */}
            <div className="stripe-checkout-event-box">
              <img
                src={event?.bannerUrl || "/event-placeholder.svg"}
                alt=""
                className="stripe-checkout-event-thumb"
                onError={(e) => {
                  (e.currentTarget as HTMLImageElement).src = "/event-placeholder.svg";
                }}
              />
              <div className="stripe-checkout-event-details">
                <h3 className="stripe-checkout-event-name">{event?.title || t("checkout.eventTicket")}</h3>
                {event?.startDatetime && (
                  <p className="stripe-checkout-event-meta">
                    <Calendar size={14} aria-hidden="true" />
                    <span>{formatEventSchedule(event.startDatetime)}</span>
                  </p>
                )}
                <p className="stripe-checkout-event-meta">
                  <MapPin size={14} aria-hidden="true" />
                  <span>{event?.venueName || t("searchPage.locationUpdating")}</span>
                </p>
              </div>
            </div>

            <hr className="stripe-checkout-divider" />

            {/* Ticket Line Items */}
            <div className="stripe-checkout-items">
              {order.items.map((item) => (
                <div key={item.orderItemId} className="stripe-checkout-item-row">
                  <div className="stripe-checkout-item-name-wrap">
                    <span className="stripe-checkout-item-name">
                      {item.ticketTypeName} × {item.quantity}
                    </span>
                    {item.seats && item.seats.length > 0 && (
                      <span className="stripe-checkout-item-seats">
                        {item.seats.map((s) => s.seatLabel).join(", ")}
                      </span>
                    )}
                  </div>
                  <span className="stripe-checkout-item-price">{money(item.lineSubtotal)}</span>
                </div>
              ))}
            </div>

            <hr className="stripe-checkout-divider" />

            {/* Pricing Breakdown */}
            <div className="stripe-checkout-pricing">
              <div className="stripe-checkout-price-row">
                <span>{t("checkout.subtotal", { defaultValue: "Tạm tính" })}</span>
                <span>{money(order.subtotal)}</span>
              </div>
              <div className="stripe-checkout-price-row">
                <span>{t("checkout.discount", { defaultValue: "Giảm giá" })}</span>
                <span className={BigInt(order.discount || "0") > 0n ? "has-discount" : ""}>
                  {BigInt(order.discount || "0") > 0n ? `−${money(order.discount)}` : money("0")}
                </span>
              </div>
              <hr className="stripe-checkout-divider" />
              <div className="stripe-checkout-price-row stripe-checkout-total-row">
                <strong>{t("checkout.totalLabel", { defaultValue: "Tổng cộng" })}</strong>
                <strong>{money(order.total)}</strong>
              </div>
            </div>
          </div>
        </section>

        {/* ================= RIGHT PANEL: Payment Flow ================= */}
        <section className="stripe-checkout-right" aria-label={t("checkout.paymentHeading")}>
          <div className="stripe-checkout-right-inner">
            <div className="stripe-checkout-payment-header">
              <div className="stripe-checkout-payment-header-copy">
                <p className="stripe-checkout-sublabel">
                  {t("checkout.paymentLabel", { defaultValue: "THANH TOÁN" })}
                </p>
                <h2 className="stripe-checkout-main-heading">
                  {t("checkout.paymentHeading", { defaultValue: "Hoàn tất thanh toán" })}
                </h2>
              </div>

              <div
                className={`stripe-checkout-inline-timer${remaining <= 120 ? " warning" : ""}`}
                role="status"
                aria-live="polite"
                aria-label={t("checkout.timerHeading", { defaultValue: "Hoàn tất đặt vé trong" })}
              >
                <div className="timer-badges">
                  <span className="timer-badge">{String(remainingMinutes).padStart(2, "0")}</span>
                  <span className="timer-colon" aria-hidden="true">:</span>
                  <span className="timer-badge">{String(remainingSeconds).padStart(2, "0")}</span>
                </div>
              </div>
            </div>

            {remaining === 0 ? (
              <p className="timer-warning-note">
                <CircleAlert size={14} aria-hidden="true" />
                <span>{t("checkout.expiredNotice", { defaultValue: "Đã hết thời gian giữ vé. Máy chủ sẽ xác nhận trạng thái đơn." })}</span>
              </p>
            ) : remaining <= 120 ? (
              <p className="timer-warning-note">
                <CircleAlert size={14} aria-hidden="true" />
                <span>{t("checkout.timerWarning", { defaultValue: "Sắp hết thời gian giữ vé, vui lòng hoàn tất thanh toán." })}</span>
              </p>
            ) : null}

            {/* Section 1: Contact Info */}
            <div className="stripe-checkout-section">
              <label className="stripe-checkout-section-label" htmlFor="checkout-contact-email">
                {t("checkout.contactInfo", { defaultValue: "Thông tin liên hệ" })}
              </label>
              <div className="stripe-checkout-input-wrap">
                <Mail size={16} className="stripe-checkout-input-icon" aria-hidden="true" />
                <input
                  id="checkout-contact-email"
                  type="email"
                  value={order.customerEmail}
                  readOnly
                  className="stripe-checkout-input stripe-checkout-input-readonly"
                />
              </div>
            </div>

            {/* Section 2: Payment Method */}
            <div className="stripe-checkout-section">
              <label className="stripe-checkout-section-label">
                {t("checkout.paymentMethod", { defaultValue: "Phương thức thanh toán" })}
              </label>
              <div className="stripe-checkout-payment-card stripe-checkout-payment-card-applied">
                <div className="stripe-checkout-vnpay-logo-box">
                  <img
                    src="/payment-logos/vnpay.svg"
                    alt="VNPay"
                    className="stripe-checkout-vnpay-logo"
                  />
                </div>
                <div className="stripe-checkout-payment-info">
                  <div className="stripe-checkout-payment-title-row">
                    <strong>VNPay</strong>
                    <span className="stripe-checkout-applied-badge">
                      <Check size={11} aria-hidden="true" />
                      <span>{t("checkout.activeMethod", { defaultValue: "Đang áp dụng" })}</span>
                    </span>
                  </div>
                  <p>{t("checkout.vnpayDescription", { defaultValue: "Thanh toán an toàn qua cổng VNPay" })}</p>
                </div>
              </div>
            </div>

            {/* Section 3: Voucher Promo Code */}
            <div className="stripe-checkout-promo-wrap">
              <button
                type="button"
                className="stripe-checkout-promo-toggle"
                onClick={() => setPromoExpanded((prev) => !prev)}
                aria-expanded={promoExpanded}
              >
                <Tag size={15} aria-hidden="true" />
                <span>{t("checkout.haveVoucherQuestion", { defaultValue: "Bạn có mã giảm giá?" })}</span>
                <ChevronDown
                  size={15}
                  className={`stripe-checkout-chevron${promoExpanded ? " is-expanded" : ""}`}
                  aria-hidden="true"
                />
              </button>

              {promoExpanded && (
                <form onSubmit={applyPromotion} className="stripe-checkout-promo-form">
                  <div className="stripe-checkout-promo-input-row">
                    <div className="stripe-checkout-input-wrap">
                      <input
                        type="text"
                        placeholder={t("checkout.voucherPlaceholder", { defaultValue: "Nhập mã giảm giá" })}
                        value={code}
                        onChange={(e) => setCode(e.target.value)}
                        disabled={!canApply || busy}
                        className="stripe-checkout-input"
                        autoComplete="off"
                      />
                    </div>
                    <button
                      type="submit"
                      className="stripe-checkout-promo-submit"
                      disabled={!canApply || busy || !code.trim()}
                    >
                      {busy
                        ? t("checkout.processing", { defaultValue: "Đang xử lý…" })
                        : t("checkout.applyCode", { defaultValue: "Áp dụng" })}
                    </button>
                  </div>
                </form>
              )}

              {notice && (
                <p role="status" className="stripe-checkout-notice-success">
                  <CheckCircle2 size={15} aria-hidden="true" />
                  <span>{notice}</span>
                </p>
              )}

              {error && (
                <p role="alert" className="stripe-checkout-notice-error">
                  <CircleAlert size={15} aria-hidden="true" />
                  <span>{error}</span>
                </p>
              )}

              {(order.paymentFrozenAt ||
                paymentRequested ||
                ["PRICE_FROZEN", "PAYMENT_IN_PROGRESS"].includes(voucherBlockReason)) && (
                <p className="stripe-checkout-notice-error">
                  <Info size={15} aria-hidden="true" />
                  <span>{t("checkout.priceFrozen", { defaultValue: "Đơn hàng đang trong quá trình thanh toán, không thể thay đổi mã giảm giá." })}</span>
                </p>
              )}
            </div>

            {/* Action Buttons */}
            <div className="stripe-checkout-actions-row">
              {pending && remaining > 0 && (
                <button
                  type="button"
                  onClick={() => setShowCancelModal(true)}
                  disabled={busy}
                  className="stripe-checkout-cancel-btn"
                >
                  {t("checkout.cancelOrder", { defaultValue: "Hủy đơn" })}
                </button>
              )}
              <button
                type="button"
                className="stripe-checkout-pay-btn"
                disabled={!pending || remaining <= 0 || busy}
                onClick={() => void startPayment()}
              >
                <span>
                  {busy
                    ? t("checkout.processing", { defaultValue: "Đang xử lý…" })
                    : t("checkout.payCta", { defaultValue: "Thanh toán" })}
                </span>
              </button>
            </div>

            {/* Redirect Notice */}
            <p className="stripe-checkout-redirect-notice">
              <Info size={14} aria-hidden="true" />
              <span>
                {t("checkout.redirectNotice", {
                  defaultValue: "Bạn sẽ được chuyển đến cổng thanh toán VNPay để hoàn tất giao dịch.",
                })}
              </span>
            </p>
          </div>
        </section>
      </div>

      {/* Cancel Order Confirmation Modal */}
      {showCancelModal && (
        <div
          className="stripe-checkout-modal-backdrop"
          role="dialog"
          aria-modal="true"
          aria-labelledby="stripe-checkout-modal-title"
          onClick={() => setShowCancelModal(false)}
        >
          <div
            className="stripe-checkout-modal-card"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="stripe-checkout-modal-icon-wrap" aria-hidden="true">
              <CircleAlert size={24} />
            </div>
            <h3 id="stripe-checkout-modal-title" className="stripe-checkout-modal-title">
              {t("checkout.cancelConfirmTitle", { defaultValue: "Xác nhận hủy đơn hàng?" })}
            </h3>
            <p className="stripe-checkout-modal-desc">
              {t("checkout.cancelConfirmDesc", {
                defaultValue:
                  "Bạn có chắc chắn muốn hủy đơn hàng này không? Tất cả các vị trí vé đang được giữ sẽ được giải phóng ngay lập tức cho người mua khác.",
              })}
            </p>
            <div className="stripe-checkout-modal-actions">
              <button
                type="button"
                className="stripe-checkout-modal-btn stripe-checkout-modal-btn-dismiss"
                onClick={() => setShowCancelModal(false)}
                disabled={busy}
              >
                {t("checkout.keepOrder", { defaultValue: "Giữ lại đơn hàng" })}
              </button>
              <button
                type="button"
                className="stripe-checkout-modal-btn stripe-checkout-modal-btn-confirm"
                onClick={() => {
                  setShowCancelModal(false);
                  void cancelOrder();
                }}
                disabled={busy}
              >
                {t("checkout.confirmCancel", { defaultValue: "Xác nhận hủy" })}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
