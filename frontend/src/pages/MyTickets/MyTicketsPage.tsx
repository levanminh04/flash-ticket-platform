import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import { QrCode, Ticket, X } from "lucide-react";
import AccountSidebar from "../../components/account/AccountSidebar";
import AccountCategoryNav from "../../components/common/AccountCategoryNav";
import OrderLookupForm, { orderIdPattern } from "../../components/common/OrderLookupForm";
import { ticketService, OrderTicketsView, TicketView } from "../../services/ticketService";
import { paymentProblem } from "../../services/paymentService";

export default function MyTicketsPage() {
  const { t } = useTranslation();
  const { keycloak, initialized } = useKeycloak();
  const [params, setParams] = useSearchParams();
  const orderId = (params.get("orderId") || "").toLowerCase();
  const scope = `${keycloak.tokenParsed?.iss}:${keycloak.tokenParsed?.sub}:${orderId}:${Boolean(keycloak.authenticated)}`;
  const [stored, setStored] = useState<{ scope: string; data: OrderTicketsView } | null>(null);
  const data = stored?.scope === scope ? stored.data : null;
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const [selected, setSelected] = useState<{ scope: string; ticket: TicketView } | null>(null);
  const selectedTicket = selected?.scope === scope ? selected.ticket : null;
  const [qrUrl, setQrUrl] = useState("");
  const [qrError, setQrError] = useState("");
  const dialogRef = useRef<HTMLDialogElement>(null);
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const authenticated = initialized && keycloak.authenticated;
  const validId = orderIdPattern.test(orderId);

  useEffect(() => {
    setStored(null); setError(""); setSelected(null);
    if (!authenticated || !validId) { setLoading(false); return; }
    const controller = new AbortController();
    setLoading(true);
    void ticketService.getOrderTickets(orderId, controller.signal).then(data => { if (!controller.signal.aborted) setStored({ scope, data }); }).catch(error => {
      if (!controller.signal.aborted) setError(paymentProblem(error).message || t("ticketV1.loadFailed"));
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [authenticated, scope, orderId, validId, refresh, t]);

  useEffect(() => {
    setQrUrl(""); setQrError("");
    if (!selectedTicket || !authenticated) return;
    const controller = new AbortController();
    const dialog = dialogRef.current;
    dialog?.showModal();
    let objectUrl = "";
    void ticketService.getQrImage(selectedTicket.ticketId, controller.signal).then(blob => {
      if (!controller.signal.aborted) { objectUrl = URL.createObjectURL(blob); setQrUrl(objectUrl); }
    }).catch(() => { if (!controller.signal.aborted) setQrError(t("ticketV1.qrFailed")); });
    return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl); dialog?.close(); triggerRef.current?.focus(); };
  }, [selectedTicket, authenticated, t]);

  return (
    <div className="my-tickets-page">
      <AccountCategoryNav />
      <div className="container account-layout-container">
        <AccountSidebar />
        <main className="account-main-content ticket-v1-page">
          <h1 className="profile-page-title">{t("nav.myTickets")}</h1>
          <section className="profile-card profile-editor-card">
            <div className="profile-editor-header">
              <p>{t("ticketV1.lookupNotice")}</p>
            </div>
            {!initialized ? (
              <p role="status">{t("ticketV1.loading")}</p>
            ) : !authenticated ? (
              <div>
                <p>{t("paymentV1.loginRequired")}</p>
                <button className="btn btn-primary" onClick={() => void keycloak.login({ redirectUri: window.location.href })}>
                  {t("auth.signIn")}
                </button>
              </div>
            ) : (
              <>
                <OrderLookupForm key={orderId} orderId={orderId} onLookup={id => {
                  if (id === orderId) setRefresh(value => value + 1);
                  else setParams({ orderId: id });
                }} />
                {orderId && !validId && <p role="status">{t("paymentV1.missingOrder")}</p>}
                {validId && (
                  <div className="payment-v1-refresh">
                    <button className="btn btn-outline" disabled={loading} onClick={() => setRefresh(value => value + 1)}>
                      {t("paymentV1.refresh")}
                    </button>
                    {loading && <span role="status">{t("ticketV1.loading")}</span>}
                  </div>
                )}
                {error && <p role="alert" className="payment-v1-error">{error}</p>}
              </>
            )}
          </section>

          {data && authenticated && (
            <section className="profile-card profile-editor-card ticket-results-card" aria-label={t("nav.myTickets")}>
              <div className="profile-editor-header">
                <h2>{t(`issuanceState.${data.issuanceState}`)}</h2>
                {data.issuanceState === "PENDING" && <p>{t("ticketV1.pendingNotice")}</p>}
                {data.issuanceState === "FAILED" && <p>{t("ticketV1.failedNotice")}</p>}
                {data.issuanceState === "COMPLETED" && data.tickets.length === 0 && <p>{t("ticketV1.empty")}</p>}
              </div>
              {data.issuanceState === "COMPLETED" && data.tickets.length > 0 && (
                <div className="ticket-v1-list">
                  {data.tickets.map(ticket => (
                    <article key={ticket.ticketId} className="ticket-v1-item">
                      <Ticket size={22} aria-hidden="true" />
                      <div>
                        <h3>{ticket.ticketTypeName}</h3>
                        <p>{ticket.seatLabel || t("ticketV1.noSeat")}</p>
                        <p className="ticket-v1-state">{t(`ticketState.${ticket.state}`)}</p>
                      </div>
                      {ticket.state === "VALID" && (
                        <button className="btn btn-outline" onClick={event => {
                          triggerRef.current = event.currentTarget;
                          setSelected({ scope, ticket });
                        }}>
                          <QrCode size={18} aria-hidden="true" />
                          {t("ticketV1.viewQr")}
                        </button>
                      )}
                    </article>
                  ))}
                </div>
              )}
              <Link className="payment-v1-return" to={`/payment-result?orderId=${orderId}`}>{t("paymentV1.checkPayment")}</Link>
            </section>
          )}
          {selectedTicket && authenticated && <dialog className="ticket-qr-dialog" ref={dialogRef} onCancel={() => setSelected(null)} aria-labelledby="ticket-qr-title">
            <header><h2 id="ticket-qr-title">{selectedTicket.ticketTypeName}</h2><button className="btn btn-outline" aria-label={t("common.close")} onClick={() => setSelected(null)}><X size={20} /></button></header>
            {qrError ? <p role="alert">{qrError}</p> : qrUrl ? <img src={qrUrl} alt={t("ticketV1.qrAlt", { seat: selectedTicket.seatLabel || selectedTicket.ordinal })} width={280} height={280} /> : <p role="status">{t("ticketV1.loading")}</p>}
            <p>{t("ticketV1.qrPrivacy")}</p>
          </dialog>}
        </main>
      </div>
    </div>
  );
}
