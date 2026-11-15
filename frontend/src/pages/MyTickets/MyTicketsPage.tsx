import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import { toast } from "react-toastify";
import {
  Check,
  Copy,
  LoaderCircle,
  LogIn,
  QrCode,
  Ticket,
  X,
} from "lucide-react";
import AccountSidebar from "../../components/account/AccountSidebar";
import OrderLookupForm, { orderIdPattern } from "../../components/common/OrderLookupForm";
import Footer from "../../components/common/Footer";
import { ticketService, OrderTicketsView, TicketView } from "../../services/ticketService";
import { paymentProblem } from "../../services/paymentService";
import "./my-tickets.css";

export default function MyTicketsPage() {
  const { t, i18n } = useTranslation();
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
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const authenticated = initialized && keycloak.authenticated;
  const validId = orderIdPattern.test(orderId);
  const isEn = i18n.resolvedLanguage === "en";

  const handleCopyTicketId = async (id: string) => {
    try {
      await navigator.clipboard.writeText(id);
    } catch {
      toast.error(isEn ? "Could not copy the ticket ID." : "Không thể sao chép mã vé.");
      return;
    }

    setCopiedId(id);
    window.setTimeout(() => {
      setCopiedId(prev => (prev === id ? null : prev));
    }, 1500);
  };

  useEffect(() => {
    setStored(null);
    setError("");
    setSelected(null);
    if (!authenticated || !validId) {
      setLoading(false);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    void ticketService
      .getOrderTickets(orderId, controller.signal)
      .then(result => {
        if (!controller.signal.aborted) setStored({ scope, data: result });
      })
      .catch(err => {
        if (!controller.signal.aborted) setError(paymentProblem(err).message || t("ticketV1.loadFailed"));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [authenticated, scope, orderId, validId, refresh, t]);

  useEffect(() => {
    setQrUrl("");
    setQrError("");
    if (!selectedTicket || !authenticated) return;
    const controller = new AbortController();
    const dialog = dialogRef.current;
    dialog?.showModal();
    let objectUrl = "";
    void ticketService
      .getQrImage(selectedTicket.ticketId, controller.signal)
      .then(blob => {
        if (!controller.signal.aborted) {
          objectUrl = URL.createObjectURL(blob);
          setQrUrl(objectUrl);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setQrError(t("ticketV1.qrFailed"));
      });
    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      dialog?.close();
      triggerRef.current?.focus();
    };
  }, [selectedTicket, authenticated, t]);

  const issuanceTone = (state: OrderTicketsView["issuanceState"]) =>
    state === "COMPLETED" ? "is-success" : state === "FAILED" ? "is-danger" : "is-warning";

  const ticketStateTone = (state: TicketView["state"]) =>
    state === "VALID" ? "is-success" : state === "VOID" ? "is-danger" : "is-neutral";

  return (
    <div className="my-tickets-page">
      <div className="container account-layout-container">
        <AccountSidebar />
        <main className="account-main-content ticket-v1-page">
          <div className="ticket-v1-header">
            <h1 className="profile-page-title">{t("nav.myTickets")}</h1>
          </div>

          {!initialized ? (
            <section className="tix-tickets-state-card" role="status">
              <LoaderCircle className="tix-tickets-spinner" size={24} aria-hidden="true" />
              <p>{t("ticketV1.loading")}</p>
            </section>
          ) : !authenticated ? (
            <section className="tix-tickets-state-card">
              <span className="tix-tickets-state-icon">
                <LogIn size={24} aria-hidden="true" />
              </span>
              <p>{t("paymentV1.loginRequired")}</p>
              <button
                type="button"
                className="tix-ticket-btn-primary"
                onClick={() => void keycloak.login({ redirectUri: window.location.href })}
              >
                {t("auth.signIn")}
              </button>
            </section>
          ) : (
            <>
              <section className="tix-tickets-lookup-card">
                <p className="tix-tickets-notice">{t("ticketV1.lookupNotice")}</p>
                <OrderLookupForm
                  key={orderId}
                  orderId={orderId}
                  onLookup={id => {
                    if (id === orderId) setRefresh(value => value + 1);
                    else setParams({ orderId: id });
                  }}
                />
                {orderId && !validId && (
                  <p role="status" className="tix-tickets-hint">{t("paymentV1.missingOrder")}</p>
                )}
                {error && <p role="alert" className="tix-tickets-error">{error}</p>}
              </section>

              {validId && loading && !data && (
                <section className="tix-tickets-state-card" role="status">
                  <LoaderCircle className="tix-tickets-spinner" size={24} aria-hidden="true" />
                  <p>{t("ticketV1.loading")}</p>
                </section>
              )}

              {data && (
                <section className="tix-tickets-results-card" aria-label={t("nav.myTickets")}>
                  <header className="tix-tickets-results-header">
                    <div className="tix-tickets-results-title-wrap">
                      <div className="tix-tickets-results-title-row">
                        <h2>{t(`issuanceState.${data.issuanceState}`)}</h2>
                        <span className={`tix-ticket-badge ${issuanceTone(data.issuanceState)}`}>
                          {t(`issuanceState.${data.issuanceState}`)}
                        </span>
                      </div>
                      {data.issuanceState === "PENDING" && (
                        <p className="tix-tickets-results-desc">{t("ticketV1.pendingNotice")}</p>
                      )}
                      {data.issuanceState === "FAILED" && (
                        <p className="tix-tickets-results-desc">{t("ticketV1.failedNotice")}</p>
                      )}
                      {data.issuanceState === "COMPLETED" && data.tickets.length === 0 && (
                        <p className="tix-tickets-results-desc">{t("ticketV1.empty")}</p>
                      )}
                    </div>
                  </header>

                  <div className="tix-tickets-results-body">
                    {data.issuanceState === "COMPLETED" && data.tickets.length > 0 && (
                      <ul className="tix-tickets-list">
                        {data.tickets.map(ticket => (
                          <li key={ticket.ticketId} className="tix-ticket-card">
                            <div className="tix-ticket-main">
                              <span className="tix-ticket-icon" aria-hidden="true">
                                <Ticket size={20} />
                              </span>
                              <div className="tix-ticket-info">
                                <div className="tix-ticket-title-row">
                                  <h3>{ticket.ticketTypeName}</h3>
                                  <span className={`tix-ticket-badge ${ticketStateTone(ticket.state)}`}>
                                    {t(`ticketState.${ticket.state}`)}
                                  </span>
                                </div>

                                <div className="tix-ticket-id-row">
                                  <span className="tix-ticket-ordinal">#{ticket.ordinal}</span>
                                  <div className="tix-ticket-id-pill">
                                    <span className="tix-ticket-id-code">{ticket.ticketId}</span>
                                    <button
                                      type="button"
                                      className="tix-ticket-copy-btn"
                                      onClick={() => void handleCopyTicketId(ticket.ticketId)}
                                      aria-label={ticket.ticketId}
                                      title={ticket.ticketId}
                                    >
                                      {copiedId === ticket.ticketId ? (
                                        <Check size={13} className="is-copied" aria-hidden="true" />
                                      ) : (
                                        <Copy size={13} aria-hidden="true" />
                                      )}
                                    </button>
                                  </div>
                                </div>

                                {ticket.seatLabel && (
                                  <p className="tix-ticket-seat">{ticket.seatLabel}</p>
                                )}
                              </div>
                            </div>

                            {ticket.state === "VALID" && (
                              <button
                                type="button"
                                className="tix-ticket-btn-primary"
                                onClick={event => {
                                  triggerRef.current = event.currentTarget;
                                  setSelected({ scope, ticket });
                                }}
                              >
                                <QrCode size={16} aria-hidden="true" />
                                <span>{t("ticketV1.viewQr")}</span>
                              </button>
                            )}
                          </li>
                        ))}
                      </ul>
                    )}

                    <div className="tix-tickets-footer-bar">
                      <Link className="tix-ticket-btn-secondary" to="/my-orders">
                        {isEn ? "My orders" : "Đơn hàng của tôi"}
                      </Link>
                      <Link className="tix-ticket-btn-primary" to={`/payment-result?orderId=${orderId}`}>
                        {t("paymentV1.checkPayment")}
                      </Link>
                    </div>
                  </div>
                </section>
              )}
            </>
          )}

          {selectedTicket && authenticated && (
            <dialog
              className="tix-qr-dialog"
              ref={dialogRef}
              onCancel={() => setSelected(null)}
              onClick={event => {
                if (event.target === dialogRef.current) setSelected(null);
              }}
              aria-label={t("ticketV1.viewQr")}
            >
              <div className="tix-qr-dialog-surface">
                <button
                  type="button"
                  className="tix-qr-close-btn"
                  aria-label={t("common.close")}
                  onClick={() => setSelected(null)}
                >
                  <X size={18} aria-hidden="true" />
                </button>
                <div className="tix-qr-frame">
                  {qrError ? (
                    <p role="alert" className="tix-tickets-error">{qrError}</p>
                  ) : qrUrl ? (
                    <img
                      src={qrUrl}
                      alt={t("ticketV1.qrAlt", { seat: selectedTicket.seatLabel || selectedTicket.ordinal })}
                      width={260}
                      height={260}
                    />
                  ) : (
                    <LoaderCircle className="tix-tickets-spinner" size={24} aria-hidden="true" />
                  )}
                </div>
              </div>
            </dialog>
          )}
        </main>
      </div>
      <Footer />
    </div>
  );
}
