import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import {
  Check,
  ChevronDown,
  ChevronRight,
  Clock3,
  Copy,
  LoaderCircle,
  LogIn,
  RefreshCw,
  TriangleAlert,
} from "lucide-react";
import AccountSidebar from "../../components/account/AccountSidebar";
import Footer from "../../components/common/Footer";
import OrderLookupForm, { orderIdPattern } from "../../components/common/OrderLookupForm";
import { paymentProblem, paymentService, PaymentView, vnPayCheckoutUrl } from "../../services/paymentService";
import { bookingService } from "../../services/bookingService";
import type { OrderView } from "../../types/bookingContract";
import "./payment-result.css";

function InvoiceIllustration({ tone }: { tone: "success" | "warning" | "danger" | "neutral" }) {
  return (
    <div className={`payment-v1-illus is-${tone}`} aria-hidden="true">
      <div className="payment-v1-illus-paper">
        <div className="payment-v1-illus-top">
          <span className="payment-v1-illus-dot" />
          <span className="payment-v1-illus-line is-short" />
        </div>
        <div className="payment-v1-illus-rows">
          <span className="payment-v1-illus-line is-xs" />
          <span className="payment-v1-illus-line is-md" />
          <span className="payment-v1-illus-line is-xs" />
          <span className="payment-v1-illus-line is-sm" />
        </div>
        <div className="payment-v1-illus-foot">
          <span className="payment-v1-illus-line is-full" />
          <span className="payment-v1-illus-line is-mid" />
        </div>
      </div>

      <span className={`payment-v1-illus-badge is-${tone}`}>
        {tone === "success" ? (
          <Check size={14} strokeWidth={3} />
        ) : tone === "danger" ? (
          <TriangleAlert size={13} strokeWidth={2.5} />
        ) : (
          <Clock3 size={13} strokeWidth={2.5} />
        )}
      </span>
    </div>
  );
}

export default function PaymentResultPage({ history = false }: { history?: boolean }) {
  const { t, i18n } = useTranslation();
  const { keycloak, initialized } = useKeycloak();
  const [params, setParams] = useSearchParams();
  const orderId = (params.get("orderId") || "").toLowerCase();
  const [storedOrder, setStoredOrder] = useState<{ scope: string; order: OrderView } | null>(null);
  const [storedView, setStoredView] = useState<{ scope: string; view: PaymentView } | null>(null);
  const [error, setError] = useState("");
  const [checking, setChecking] = useState(false);
  const [exhausted, setExhausted] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(Date.now());
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const creating = useRef(false);
  const scope = `${keycloak.tokenParsed?.iss}:${keycloak.tokenParsed?.sub}:${orderId}:${Boolean(keycloak.authenticated)}`;
  const scopeRef = useRef(scope);
  scopeRef.current = scope;
  const order = storedOrder?.scope === scope ? storedOrder.order : null;
  const view = storedView?.scope === scope ? storedView.view : null;
  const validId = orderIdPattern.test(orderId);
  const authenticated = initialized && keycloak.authenticated;
  const isEn = i18n.resolvedLanguage === "en";
  const money = (value: string) => `${BigInt(value).toLocaleString(isEn ? "en-US" : "vi-VN")} ₫`;

  const handleCopy = (id: string) => {
    void navigator.clipboard.writeText(id);
    setCopiedId(id);
    window.setTimeout(() => {
      setCopiedId(prev => (prev === id ? null : prev));
    }, 1500);
  };

  useEffect(() => {
    setStoredOrder(null);
    setStoredView(null);
    setError("");
    setExhausted(false);
    setBusy(false);
    setDetailsOpen(false);
    creating.current = false;
    if (!authenticated || !validId) {
      setChecking(false);
      return;
    }
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    let count = 0;

    const poll = async () => {
      if (controller.signal.aborted) return;
      setChecking(true);
      let closedOrder = false;
      try {
        let currentOrder: OrderView | null = null;
        try {
          currentOrder = await bookingService.getOrder(orderId, controller.signal);
          if (controller.signal.aborted) return;
          setStoredOrder(currentOrder ? { scope, order: currentOrder } : null);
        } catch (err) {
          if (controller.signal.aborted) return;
          if (paymentProblem(err).code === "ORDER_NOT_FOUND") {
            setStoredOrder(null);
            setStoredView(null);
            setError(t("checkout.orderNotFound"));
            return;
          }
          if (history) setStoredOrder(null);
        }

        closedOrder = currentOrder?.state === "CANCELLED" || currentOrder?.state === "EXPIRED";
        if (currentOrder && !currentOrder.paymentFrozenAt
          && (currentOrder.state === "EXPIRED" || (history && !closedOrder))) {
          setStoredView(null);
          setError("");
          return;
        }

        const current = await paymentService.getPaymentStatus(orderId, controller.signal);
        if (controller.signal.aborted) return;
        setStoredView({ scope, view: current });
        setError("");
        count += 1;
        const waitingForBooking = Boolean(current.acceptedChargeId)
          && currentOrder?.state !== "COMPLETED" && currentOrder?.state !== "ISSUANCE_FAILED";
        const unresolved = !closedOrder && (waitingForBooking
          || (!current.acceptedChargeId && current.latestAttempt?.attemptState !== "FAILED"));
        if (unresolved && count < 10) timer = setTimeout(() => void poll(), 3000);
        else setExhausted(unresolved);
      } catch (err) {
        if (controller.signal.aborted) return;
        setStoredView(null);
        const problem = paymentProblem(err);
        setError(problem.code === "PAYMENT_NOT_READY"
          ? (closedOrder ? "" : t("paymentV1.noAttempt"))
          : problem.message || t("paymentV1.readFailed"));
      } finally {
        if (!controller.signal.aborted) setChecking(false);
      }
    };

    void poll();
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [authenticated, scope, orderId, validId, refresh, history, t]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const retryPayment = async () => {
    if (creating.current || !view || view.acceptedChargeId || view.latestAttempt?.attemptState !== "FAILED") return;
    creating.current = true;
    setBusy(true);
    setError("");
    const requestedScope = scope;
    try {
      const attempt = await paymentService.initiatePayment(orderId, true);
      if (scopeRef.current !== requestedScope) return;
      const url = vnPayCheckoutUrl(attempt);
      if (url) window.location.assign(url);
      else setRefresh(value => value + 1);
    } catch (err) {
      if (scopeRef.current === requestedScope) setError(paymentProblem(err).message || t("paymentV1.retryFailed"));
    } finally {
      if (scopeRef.current === requestedScope) {
        creating.current = false;
        setBusy(false);
      }
    }
  };

  const attempt = view?.latestAttempt;
  const paid = Boolean(view?.acceptedChargeId);
  const state = attempt?.attemptState;
  const closedOrder = order?.state === "CANCELLED" || order?.state === "EXPIRED";
  const waitingForBooking = paid && order?.state !== "COMPLETED" && order?.state !== "ISSUANCE_FAILED";
  const unresolved = Boolean(view && !closedOrder && (waitingForBooking || (!paid && state !== "FAILED")));
  const continueUrl = attempt && !paid && !closedOrder ? vnPayCheckoutUrl(attempt) : null;
  const retryAllowed = !closedOrder && !paid && state === "FAILED" && attempt && Date.parse(attempt.expiresAt) > now;
  const title = paid ? "accepted" : state === "FAILED" ? "failed" : state === "SUCCEEDED" ? "awaitingBooking" : state === "UNKNOWN" ? "unknown" : "pending";
  const orderTone: "success" | "warning" | "danger" | "neutral" =
    order?.state === "COMPLETED"
      ? "success"
      : order?.state === "CANCELLED" || order?.state === "EXPIRED"
        ? "danger"
        : order?.state
          ? "warning"
          : "neutral";
  const badgeTone: "success" | "warning" | "danger" | "neutral" = view && !closedOrder
    ? (paid ? "success" : state === "FAILED" ? "danger" : "warning")
    : orderTone;
  const noAttempt = (history || !view) && !paid && !attempt;
  const hasDiscount = Boolean(order && BigInt(order.discount || "0") > 0n);
  const finalAmount = attempt?.amount || order?.total || null;
  const showManualLookup = !validId;
  const activeOrderId = view?.orderId || order?.orderId || orderId;
  const hasExpandableDetails = Boolean((order && order.items.length > 0) || (view && view.refunds.length > 0));
  const hasIssuedOrPendingTickets = !closedOrder && (paid || state === "SUCCEEDED" || order?.state === "COMPLETED");

  const loadingCard = (
    <section className="payment-v1-bill payment-v1-bill--loading" role="status">
      <LoaderCircle className="payment-v1-spinner" size={24} aria-hidden="true" />
      <p className="payment-v1-loading-text">{t("paymentV1.checking")}</p>
    </section>
  );

  const authOrLookupCard = !initialized ? (
    loadingCard
  ) : (!authenticated || showManualLookup) && (
    <section className="payment-v1-lookup-card">
      <div className="payment-v1-lookup-inner">
        {history && <p className="payment-v1-history-notice">{t("paymentV1.historyNotice")}</p>}
        {!authenticated ? (
          <section className="payment-v1-empty-box">
            <span className="payment-v1-empty-icon"><LogIn size={22} aria-hidden="true" /></span>
            <p>{t("paymentV1.loginRequired")}</p>
            <button
              type="button"
              className="tix-payment-btn-primary"
              onClick={() => void keycloak.login({ redirectUri: window.location.href })}
            >
              {t("auth.signIn")}
            </button>
          </section>
        ) : (
          <>
            <OrderLookupForm
              key={orderId}
              orderId={orderId}
              onLookup={id => {
                if (id === orderId) setRefresh(value => value + 1);
                else setParams({ orderId: id });
              }}
            />
            {!validId && (!history || orderId) && (
              <p role="alert" className="payment-v1-hint">{t("paymentV1.missingOrder")}</p>
            )}
          </>
        )}
      </div>
    </section>
  );

  const loadingOrErrorCard = authenticated && validId && !view && !order && (
    checking ? (
      loadingCard
    ) : error ? (
      <section className="payment-v1-bill payment-v1-bill--state">
        <div className="payment-v1-empty-box">
          <span className="payment-v1-empty-icon is-error">
            <TriangleAlert size={20} aria-hidden="true" />
          </span>
          <p role="alert" className="payment-v1-error">{error}</p>
          <button
            type="button"
            className="tix-payment-btn-secondary"
            onClick={() => setRefresh(value => value + 1)}
            disabled={checking || busy}
          >
            <RefreshCw size={15} aria-hidden="true" />
            <span>{t("paymentV1.refresh")}</span>
          </button>
        </div>
      </section>
    ) : null
  );

  const invoiceCard = authenticated && validId && (view || order) && (
    <article
      className="payment-v1-bill"
      data-payment-state={state || (order ? order.state : "NO_ATTEMPT")}
    >
      {/* Centered Hero */}
      <header className="payment-v1-bill-hero">
        <InvoiceIllustration tone={badgeTone} />

        <p className="payment-v1-bill-status" aria-live="polite" aria-atomic="true">
          {view && !closedOrder
            ? t(noAttempt ? "paymentV1.noAttempt" : `paymentV1.${title}`)
            : order
              ? t(`bookingState.${order.state}`)
              : ""}
        </p>

        {finalAmount && (
          <div className="payment-v1-bill-amount">{money(finalAmount)}</div>
        )}

        {hasExpandableDetails && (
          <button
            type="button"
            className="payment-v1-bill-toggle"
            onClick={() => setDetailsOpen(prev => !prev)}
            aria-expanded={detailsOpen}
          >
            <span>
              {detailsOpen
                ? (isEn ? "Hide invoice details" : "Thu gọn chi tiết hóa đơn")
                : (isEn ? "View invoice details" : "Xem chi tiết hóa đơn")}
            </span>
            {detailsOpen ? (
              <ChevronDown size={15} aria-hidden="true" />
            ) : (
              <ChevronRight size={15} aria-hidden="true" />
            )}
          </button>
        )}
      </header>

      {/* Expandable Line Items & Totals */}
      {detailsOpen && hasExpandableDetails && (
        <section className="payment-v1-bill-details">
          {order && order.items.length > 0 && (
            <ul className="payment-v1-bill-items">
              {order.items.map(item => (
                <li key={item.orderItemId} className="payment-v1-bill-item">
                  <div className="payment-v1-bill-item-info">
                    <span className="payment-v1-bill-item-name">{item.ticketTypeName}</span>
                    <span className="payment-v1-bill-item-sub">
                      {item.quantity} × {money(item.unitPrice)}
                      {item.seats && item.seats.length > 0 ? ` · ${item.seats.join(", ")}` : ""}
                    </span>
                  </div>
                  <span className="payment-v1-bill-item-total">{money(item.lineSubtotal)}</span>
                </li>
              ))}
            </ul>
          )}

          {order && (
            <dl className="payment-v1-bill-subtotals">
              <div className="payment-v1-bill-subtotal-row">
                <dt>{isEn ? "Subtotal" : "Tạm tính"}</dt>
                <dd>{money(order.subtotal)}</dd>
              </div>
              <div className={`payment-v1-bill-subtotal-row ${hasDiscount ? "is-discount" : ""}`}>
                <dt>{isEn ? "Discount" : "Giảm giá"}</dt>
                <dd>{hasDiscount ? `-${money(order.discount)}` : money("0")}</dd>
              </div>
              {finalAmount && (
                <div className="payment-v1-bill-subtotal-row is-total">
                  <dt>{isEn ? "Total" : "Tổng cộng"}</dt>
                  <dd>{money(finalAmount)}</dd>
                </div>
              )}
            </dl>
          )}

          {view && view.refunds.length > 0 && (
            <div className="payment-v1-bill-refunds">
              <span className="payment-v1-bill-refunds-title">{t("paymentV1.refunds")}</span>
              <ul className="payment-v1-refund-list">
                {view.refunds.map(refund => (
                  <li key={refund.refundId} className="payment-v1-refund-item">
                    <span className="payment-v1-refund-amount">{money(refund.amount)}</span>
                    <span className="payment-v1-badge is-neutral">{t(`refundState.${refund.state}`)}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>
      )}

      {/* Clean Left-Right Metadata Rows */}
      <dl className="payment-v1-bill-meta">
        <div className="payment-v1-bill-meta-row">
          <dt>{t("paymentV1.orderId")}</dt>
          <dd>
            <span className="payment-v1-code-inline">
              <span className="payment-v1-bill-mono">{activeOrderId}</span>
              <button
                type="button"
                className="payment-v1-copy-btn"
                onClick={() => handleCopy(activeOrderId)}
                aria-label={activeOrderId}
                title={activeOrderId}
              >
                {copiedId === activeOrderId ? (
                  <Check size={13} className="is-copied" aria-hidden="true" />
                ) : (
                  <Copy size={13} aria-hidden="true" />
                )}
              </button>
            </span>
          </dd>
        </div>

        {attempt && !history && (
          <div className="payment-v1-bill-meta-row">
            <dt>{t("paymentV1.attemptId")}</dt>
            <dd>
              <span className="payment-v1-code-inline">
                <span className="payment-v1-bill-mono">{attempt.attemptId}</span>
                <button
                  type="button"
                  className="payment-v1-copy-btn"
                  onClick={() => handleCopy(attempt.attemptId)}
                  aria-label={attempt.attemptId}
                  title={attempt.attemptId}
                >
                  {copiedId === attempt.attemptId ? (
                    <Check size={13} className="is-copied" aria-hidden="true" />
                  ) : (
                    <Copy size={13} aria-hidden="true" />
                  )}
                </button>
              </span>
            </dd>
          </div>
        )}

        {order?.customerEmail && (
          <div className="payment-v1-bill-meta-row">
            <dt>{isEn ? "Customer email" : "Email nhận vé"}</dt>
            <dd>{order.customerEmail}</dd>
          </div>
        )}

        {order?.state && (
          <div className="payment-v1-bill-meta-row">
            <dt>{isEn ? "Order status" : "Trạng thái đơn"}</dt>
            <dd>{t(`bookingState.${order.state}`)}</dd>
          </div>
        )}
      </dl>

      {(error || checking || exhausted) && (
        <div className="payment-v1-bill-notices">
          {error && <p role="alert" className="payment-v1-error">{error}</p>}
          {!error && (checking || exhausted) && (
            <p className="payment-v1-poll-note" role="status">
              {checking ? t("paymentV1.checking") : t("paymentV1.pollEnded")}
            </p>
          )}
        </div>
      )}

      {/* Twin Action Buttons matching reference */}
      <div className={`payment-v1-bill-actions ${!continueUrl && !retryAllowed && !hasIssuedOrPendingTickets ? "is-single" : ""}`}>
        <Link
          className={continueUrl || retryAllowed || hasIssuedOrPendingTickets ? "tix-payment-btn-secondary payment-v1-bill-btn" : "tix-payment-btn-primary payment-v1-bill-btn"}
          to="/my-orders"
        >
          {isEn ? "My orders" : "Đơn hàng của tôi"}
        </Link>

        {continueUrl ? (
          <a className="tix-payment-btn-primary payment-v1-bill-btn" href={continueUrl} rel="noreferrer">
            {t("paymentV1.continuePay")}
          </a>
        ) : retryAllowed ? (
          <button
            type="button"
            className="tix-payment-btn-primary payment-v1-bill-btn"
            disabled={busy || checking}
            onClick={() => void retryPayment()}
          >
            {busy ? t("checkout.processing") : t("paymentV1.newAttempt")}
          </button>
        ) : hasIssuedOrPendingTickets ? (
          <Link
            className="tix-payment-btn-primary payment-v1-bill-btn"
            to={`/my-tickets?orderId=${orderId}`}
          >
            {t("paymentV1.viewTickets")}
          </Link>
        ) : null}
      </div>

      {(unresolved || error) && (
        <div className="payment-v1-bill-refresh-row">
          <button
            type="button"
            className="payment-v1-bill-refresh-link"
            onClick={() => setRefresh(value => value + 1)}
            disabled={checking || busy}
          >
            <RefreshCw className={checking ? "is-spinning" : ""} size={13} aria-hidden="true" />
            <span>{t("paymentV1.refresh")}</span>
          </button>
        </div>
      )}
    </article>
  );

  if (history) {
    return (
      <div className="my-orders-page">
        <div className="container account-layout-container">
          <AccountSidebar />
          <main className="account-main-content order-payment-content">
            <h1 className="profile-page-title">{t("paymentV1.historyTitle")}</h1>
            {authOrLookupCard}
            {loadingOrErrorCard}
            {invoiceCard}
          </main>
        </div>
        <Footer />
      </div>
    );
  }

  return (
    <div className="payment-v1-shell">
      <main className="payment-v1-page container">
        {authOrLookupCard}
        {loadingOrErrorCard}
        {invoiceCard}
      </main>
      <Footer />
    </div>
  );
}
