import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import { Calendar, Clock3, Mail, MapPin, Ticket } from "lucide-react";
import { bookingService } from "../../services/bookingService";
import { eventService } from "../../services/eventService";
import { OrderView } from "../../types/bookingContract";
import { EventSummary } from "../../types/api";
import { paymentProblem, paymentService, vnPayCheckoutUrl } from "../../services/paymentService";

export default function CheckoutPage() {
  const { t, i18n } = useTranslation();
  const { keycloak, initialized } = useKeycloak();
  const [params] = useSearchParams();
  const orderId = params.get("orderId") || "";
  const navigate = useNavigate();
  const [receipt, setReceipt] = useState<{ scope: string; order: OrderView } | null>(null);
  const [event, setEvent] = useState<EventSummary | null>(null);
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [voucherBlockReason, setVoucherBlockReason] = useState("");
  const [now, setNow] = useState(Date.now());
  const submittingRef = useRef(false);
  const subject = keycloak.tokenParsed?.sub;
  const checkoutScope = `${keycloak.tokenParsed?.iss}:${subject}:${orderId}:${Boolean(keycloak.authenticated)}`;
  const checkoutScopeRef = useRef(checkoutScope); checkoutScopeRef.current = checkoutScope;
  const order = receipt?.scope === checkoutScope ? receipt.order : null;
  const setOrder = (order: OrderView | null) => setReceipt(order ? { scope: checkoutScope, order } : null);
  const language = i18n.resolvedLanguage === "en" ? "en-US" : "vi-VN";
  const money = (value: string) => `${BigInt(value).toLocaleString(language)} ₫`;

  useEffect(() => {
    if (!initialized || !keycloak.authenticated) { setOrder(null); return; }
    // Đây là phản hồi gần nhất trong phiên, không phải GET trạng thái mới từ server.
    const cachedOrder = bookingService.getCachedOrder(orderId);
    setOrder(cachedOrder);
    setEvent(null);
    setCode(""); setError(""); setNotice(""); setVoucherBlockReason("");
    setNow(Date.now());
    submittingRef.current = false; setBusy(false);
    let cancelled = false;
    if (cachedOrder) void eventService.getEventDetails(cachedOrder.eventId).then(event => { if (!cancelled) setEvent(event); }).catch(() => {});
    return () => { cancelled = true; };
  }, [orderId, initialized, keycloak.authenticated, subject]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const remaining = order ? Math.max(0, Math.ceil((Date.parse(order.expiresAt) - now) / 1000)) : 0;
  const pending = order?.state === "PENDING_PAYMENT";
  const paymentRequested = paymentService.hasPaymentRequest(orderId);
  const canApply = Boolean(pending && remaining > 0 && !order?.paymentFrozenAt && !voucherBlockReason && !paymentRequested);

  const startPayment = async () => {
    if (!order || !pending || remaining <= 0 || submittingRef.current) return;
    submittingRef.current = true; setBusy(true); setError(""); setNotice("");
    const requestedScope = checkoutScope;
    setVoucherBlockReason("PAYMENT_IN_PROGRESS");
    try {
      const attempt = await paymentService.initiatePayment(order.orderId);
      if (checkoutScopeRef.current !== requestedScope) return;
      const url = vnPayCheckoutUrl(attempt);
      if (url) window.location.assign(url);
      else navigate(`/payment-result?orderId=${order.orderId}`);
    } catch (error) {
      if (checkoutScopeRef.current === requestedScope) setError(paymentProblem(error).message || t("paymentV1.startFailed"));
    } finally { if (checkoutScopeRef.current === requestedScope) { submittingRef.current = false; setBusy(false); } }
  };

  const applyPromotion = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!order || !canApply || !code.trim() || submittingRef.current) return;
    submittingRef.current = true;
    setBusy(true); setError(""); setNotice("");
    try {
      const updatedOrder = await bookingService.applyPromotion(order.orderId, code);
      setOrder(updatedOrder);
      setNotice(t("checkout.voucherApplied"));
    } catch (error: unknown) {
      const problem = error as { response?: { data?: { code?: string; message?: string } } };
      setError(problem.response?.data?.message || t("checkout.voucherFailed"));
      const code = problem.response?.data?.code || "";
      if (["PRICE_FROZEN", "PAYMENT_IN_PROGRESS", "ORDER_EXPIRED", "ORDER_NOT_PENDING_PAYMENT"].includes(code)) setVoucherBlockReason(code);
    } finally { submittingRef.current = false; setBusy(false); }
  };

  const cancelOrder = async () => {
    if (!order || !pending || submittingRef.current) return;
    submittingRef.current = true; setBusy(true); setError(""); setNotice("");
    try { setOrder(await bookingService.cancelOrder(order.orderId)); }
    catch (error: unknown) {
      const problem = error as { response?: { data?: { message?: string } } };
      setError(problem.response?.data?.message || t("checkout.cancelFailed"));
    } finally { submittingRef.current = false; setBusy(false); }
  };

  if (!initialized) return <main className="container booking-checkout-empty" role="status">{t("checkout.loadingOrder")}</main>;
  if (!keycloak.authenticated) return <main className="container booking-checkout-empty"><p>{t("checkout.loginRequired")}</p><button className="btn btn-primary" onClick={() => void keycloak.login({ redirectUri: window.location.href })}>{t("auth.signIn")}</button></main>;
  if (!order) return <main className="container booking-checkout-empty"><p role="alert">{t("checkout.receiptUnavailable")}</p><Link className="btn btn-primary" to="/search">{t("nav.events")}</Link></main>;

  return <div className="checkout-page booking-v1-checkout">
    <main className="container checkout-layout">
      <div className="checkout-main">
        <div className="booking-checkout-heading">
          <h1>{t("checkout.orderInfo")}</h1>
          <p className="booking-order-id">{order.orderId}</p>
          <p>{t("checkout.receiptNotice")}</p>
        </div>
        <section className="checkout-form-section booking-event-summary">
          <h2>{event?.title || t("checkout.eventTicket")}</h2>
          {event && <>
            <p><Calendar size={18} /><time dateTime={event.startDatetime}>{new Date(event.startDatetime).toLocaleString(language)}</time></p>
            <p><MapPin size={18} />{event.venueName || t("searchPage.locationUpdating")}</p>
          </>}
          <p><Mail size={18} />{order.customerEmail}</p>
          <p className="booking-hold-deadline"><Clock3 size={18} />{t("checkout.expiresAt")}: <time dateTime={order.expiresAt}>{new Date(order.expiresAt).toLocaleString(language)}</time></p>
          <p role="status" className="booking-hold-status">{!pending ? t(`bookingState.${order.state}`) : remaining === 0 ? t("checkout.expiredNotice") : t("checkout.holdRemaining", { time: `${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, "0")}` })}</p>
        </section>
        <section className="checkout-form-section booking-order-items" aria-label={t("nav.tickets")}>
          <h2><Ticket size={20} />{t("nav.tickets")}</h2>
          {order.items.map(item => <article key={item.orderItemId}>
            <h3>{item.ticketTypeName} × {item.quantity}</h3>
            <p>{money(item.unitPrice)} / {t("checkout.eventTicket")}</p>
            {item.seats.length > 0 && <p>{item.seats.map(seat => seat.seatLabel).join(", ")}</p>}
            <strong>{money(item.lineSubtotal)}</strong>
          </article>)}
        </section>
      </div>
      <aside className="checkout-sidebar">
        <section className="checkout-form-section booking-price-summary">
          <h2>{t("checkout.orderSummary")}</h2>
          <dl>
            <div><dt>{t("checkout.subtotal")}</dt><dd>{money(order.subtotal)}</dd></div>
            <div><dt>{t("checkout.discount")}</dt><dd>−{money(order.discount)}</dd></div>
            <div className="booking-order-total"><dt>{t("checkout.total")}</dt><dd>{money(order.total)}</dd></div>
          </dl>
          <form onSubmit={applyPromotion} className="booking-promotion-form">
            <label htmlFor="booking-promotion-code">{t("checkout.voucherCode")}</label>
            <input id="booking-promotion-code" value={code} onChange={event => setCode(event.target.value)} disabled={!canApply || busy} autoComplete="off" />
            <button className="btn btn-primary" disabled={!canApply || busy || !code.trim()}>{busy ? t("checkout.processing") : t("checkout.applyCode")}</button>
          </form>
          {notice && <p role="status" className="booking-operation-notice">{notice}</p>}
          {error && <p role="alert" className="booking-operation-error">{error}</p>}
          {(order.paymentFrozenAt || paymentRequested || ["PRICE_FROZEN", "PAYMENT_IN_PROGRESS"].includes(voucherBlockReason)) && <p>{t("checkout.priceFrozen")}</p>}
          {pending && remaining > 0 && <button type="button" className="btn btn-primary booking-pay-button" disabled={busy} onClick={() => void startPayment()}>{busy ? t("checkout.processing") : t("paymentV1.pay")}</button>}
          <Link className="booking-payment-status-link" to={`/payment-result?orderId=${order.orderId}`}>{t("paymentV1.checkPayment")}</Link>
          {pending && remaining > 0 && <button type="button" className="btn btn-outline booking-cancel-order" disabled={busy} onClick={() => void cancelOrder()}>{t("checkout.cancelOrder")}</button>}
          <p className="booking-payment-availability">{t("paymentV1.redirectNotice")}</p>
        </section>
        <Link className="booking-return-event" to={`/event/${order.eventId}`}>{t("checkout.backToEvent")}</Link>
      </aside>
    </main>
  </div>;
}
