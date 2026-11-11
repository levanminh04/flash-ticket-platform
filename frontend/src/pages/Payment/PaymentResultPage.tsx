import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import { CheckCircle2, Clock3, RefreshCw, TriangleAlert } from "lucide-react";
import AccountSidebar from "../../components/account/AccountSidebar";
import AccountCategoryNav from "../../components/common/AccountCategoryNav";
import OrderLookupForm, { orderIdPattern } from "../../components/common/OrderLookupForm";
import { paymentProblem, paymentService, PaymentView, vnPayCheckoutUrl } from "../../services/paymentService";

import { bookingService } from "../../services/bookingService";
import type { OrderView } from "../../types/bookingContract";

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
  const creating = useRef(false);
  const scope = `${keycloak.tokenParsed?.iss}:${keycloak.tokenParsed?.sub}:${orderId}:${Boolean(keycloak.authenticated)}`;
  const scopeRef = useRef(scope); scopeRef.current = scope;
  const order = storedOrder?.scope === scope ? storedOrder.order : null;
  const view = storedView?.scope === scope ? storedView.view : null;
  const validId = orderIdPattern.test(orderId);
  const authenticated = initialized && keycloak.authenticated;
  const money = (value: string) => `${BigInt(value).toLocaleString(i18n.resolvedLanguage === "en" ? "en-US" : "vi-VN")} ₫`;

  useEffect(() => {
    setStoredOrder(null); setStoredView(null); setError(""); setExhausted(false); setBusy(false); creating.current = false;
    if (!authenticated || !validId) { setChecking(false); return; }
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    let count = 0;
    const poll = async () => {
      if (controller.signal.aborted) return;
      setChecking(true);
      try {
        if (history) {
          const currentOrder = await bookingService.getOrder(orderId, controller.signal);
          if (controller.signal.aborted) return;
          setStoredOrder(currentOrder ? { scope, order: currentOrder } : null);
          if (currentOrder && (currentOrder.state === "CANCELLED" || !currentOrder.paymentFrozenAt)) {
            setStoredView(null);
            setError("");
            return;
          }
        }
        const current = await paymentService.getPaymentStatus(orderId, controller.signal);
        if (controller.signal.aborted) return;
        setStoredView({ scope, view: current }); setError(""); count += 1;
        const unresolved = !current.acceptedChargeId && current.latestAttempt?.attemptState !== "FAILED";
        if (unresolved && count < 10) timer = setTimeout(() => void poll(), 3000);
        else setExhausted(unresolved);
      } catch (error) {
        if (controller.signal.aborted) return;
        setStoredView(null);
        const problem = paymentProblem(error);
        setError(history && problem.code === "PAYMENT_NOT_READY" ? t("paymentV1.noAttempt") : problem.message || t("paymentV1.readFailed"));
      } finally { if (!controller.signal.aborted) setChecking(false); }
    };
    void poll();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [authenticated, scope, orderId, validId, refresh, history, t]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const retryPayment = async () => {
    if (creating.current || !view || view.acceptedChargeId || view.latestAttempt?.attemptState !== "FAILED") return;
    creating.current = true; setBusy(true); setError("");
    const requestedScope = scope;
    try {
      const attempt = await paymentService.initiatePayment(orderId, true);
      if (scopeRef.current !== requestedScope) return;
      const url = vnPayCheckoutUrl(attempt);
      if (url) window.location.assign(url);
      else setRefresh(value => value + 1);
    } catch (error) {
      if (scopeRef.current === requestedScope) setError(paymentProblem(error).message || t("paymentV1.retryFailed"));
    } finally { if (scopeRef.current === requestedScope) { creating.current = false; setBusy(false); } }
  };

  const attempt = view?.latestAttempt;
  const paid = Boolean(view?.acceptedChargeId);
  const state = attempt?.attemptState;
  const continueUrl = attempt && !paid ? vnPayCheckoutUrl(attempt) : null;
  const retryAllowed = !paid && state === "FAILED" && attempt && Date.parse(attempt.expiresAt) > now;
  const title = paid ? "accepted" : state === "FAILED" ? "failed" : state === "SUCCEEDED" ? "awaitingBooking" : state === "UNKNOWN" ? "unknown" : "pending";

  const noAttempt = history && !paid && !attempt;
  const lookup = <>
    {history && <div className="profile-editor-header"><p>{t("paymentV1.historyNotice")}</p></div>}
    {!initialized ? <p role="status">{t("paymentV1.checking")}</p> : !authenticated ? <section className="payment-v1-empty"><p>{t("paymentV1.loginRequired")}</p><button className="btn btn-primary" onClick={() => void keycloak.login({ redirectUri: window.location.href })}>{t("auth.signIn")}</button></section> : <>
      <OrderLookupForm key={orderId} orderId={orderId} onLookup={id => { if (id === orderId) setRefresh(value => value + 1); else setParams({ orderId: id }); }} />
      {!validId && (!history || orderId) && <p role="alert" className="payment-v1-hint">{t("paymentV1.missingOrder")}</p>}
      {validId && <>
        <div className="payment-v1-refresh"><button type="button" className="btn btn-outline" onClick={() => setRefresh(value => value + 1)} disabled={checking || busy}><RefreshCw size={17} />{t("paymentV1.refresh")}</button><span role="status">{checking ? t("paymentV1.checking") : exhausted ? t("paymentV1.pollEnded") : ""}</span></div>
        {error && <p role="alert" className="payment-v1-error">{error}</p>}
      </>}
    </>}
  </>;

  const result = validId && view && <section className={history ? "profile-card payment-v1-status" : "payment-v1-status"} data-payment-state={state || "NO_ATTEMPT"}>
          {paid ? <CheckCircle2 className="payment-status-icon is-confirmed" size={32} /> : state === "FAILED" ? <TriangleAlert className="payment-status-icon" size={32} /> : <Clock3 className="payment-status-icon" size={32} />}
          <h2 aria-live="polite" aria-atomic="true">{t(noAttempt ? "paymentV1.noAttempt" : `paymentV1.${title}`)}</h2>
          {!noAttempt && <p>{t(paid ? "paymentV1.ticketsSeparate" : state === "SUCCEEDED" ? "paymentV1.chargeRecorded" : state === "FAILED" ? "paymentV1.failureNotice" : "paymentV1.waitNotice")}</p>}
          <dl><div><dt>{t("paymentV1.orderId")}</dt><dd>{view.orderId}</dd></div>
            {attempt && <><div><dt>{t("paymentV1.amount")}</dt><dd>{money(attempt.amount)}</dd></div>{!history && <div><dt>{t("paymentV1.attemptId")}</dt><dd>{attempt.attemptId}</dd></div>}</>}
          </dl>
          <div className="payment-v1-actions">
            {continueUrl && <a className="btn btn-primary" href={continueUrl} rel="noreferrer">{t("paymentV1.continuePay")}</a>}
            {retryAllowed && <button type="button" className="btn btn-primary" disabled={busy || checking} onClick={() => void retryPayment()}>{busy ? t("checkout.processing") : t("paymentV1.newAttempt")}</button>}
            <Link className="btn btn-outline" to={`/my-tickets?orderId=${orderId}`}>{t("paymentV1.viewTickets")}</Link>
          </div>
          {view.refunds.length > 0 && <section className="payment-v1-refunds"><h3>{t("paymentV1.refunds")}</h3>{view.refunds.map(refund => <p key={refund.refundId}>{money(refund.amount)} · {t(`refundState.${refund.state}`)}</p>)}</section>}
        </section>;

  if (history) {
    return (
      <div className="my-orders-page">
        <AccountCategoryNav />
        <div className="container account-layout-container">
          <AccountSidebar />
          <main className="account-main-content order-payment-content">
            <h1 className="profile-page-title">{t("paymentV1.historyTitle")}</h1>
            <section className="profile-card profile-editor-card">{lookup}</section>
            {order && <section className="profile-card payment-v1-status" data-order-state={order.state}>
              <h2>{t(`bookingState.${order.state}`)}</h2>
              <dl>
                <div><dt>{t("paymentV1.orderId")}</dt><dd>{order.orderId}</dd></div>
                <div><dt>{t("paymentV1.amount")}</dt><dd>{money(order.total)}</dd></div>
              </dl>
            </section>}
            {result}
          </main>
        </div>
      </div>
    );
  }

  return (
    <main className="payment-v1-page container">
      <header className="payment-v1-heading">
        <h1>{t("paymentV1.resultTitle")}</h1>
        <p>{t("paymentV1.verificationNotice")}</p>
      </header>
      {lookup}
      {result}
      <Link className="payment-v1-return" to="/search">{t("paymentV1.backToEvents")}</Link>
    </main>
  );
}
