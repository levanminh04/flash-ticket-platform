import { useEffect, useRef, useState } from "react";
import {
  ArrowRight,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  Clock,
  Copy,
  LoaderCircle,
  LogIn,
  ReceiptText,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router-dom";
import AccountSidebar from "../../components/account/AccountSidebar";
import Footer from "../../components/common/Footer";
import { orderIdPattern } from "../../components/common/OrderLookupForm";
import { bookingService } from "../../services/bookingService";
import { ticketService } from "../../services/ticketService";
import type { OrderView } from "../../types/bookingContract";
import "./my-orders.css";

const states: OrderView["state"][] = [
  "PENDING_PAYMENT", "ISSUING", "COMPLETED", "CANCELLED", "EXPIRED", "ISSUANCE_FAILED",
];
const pageSize = 10;

export default function MyOrdersPage() {
  const { t, i18n } = useTranslation();
  const { keycloak, initialized } = useKeycloak();
  const [params, setParams] = useSearchParams();
  const [copiedOrderId, setCopiedOrderId] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const filterRef = useRef<HTMLDetailsElement>(null);
  const filterTriggerRef = useRef<HTMLElement>(null);

  const handleCopyOrderId = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    void navigator.clipboard.writeText(id);
    setCopiedOrderId(id);
    setTimeout(() => {
      setCopiedOrderId((prev) => (prev === id ? null : prev));
    }, 1500);
  };

  useEffect(() => {
    if (!initialized || !keycloak.authenticated) return;
    const closeOutside = (event: PointerEvent) => {
      const filter = filterRef.current;
      if (filter?.open && event.target instanceof Node && !filter.contains(event.target)) {
        filter.open = false;
      }
    };
    document.addEventListener("pointerdown", closeOutside);
    return () => document.removeEventListener("pointerdown", closeOutside);
  }, [initialized, keycloak.authenticated]);

  const requestedState = params.get("state");
  const state = states.find(value => value === requestedState);
  const requestedPage = Number(params.get("page"));
  const page = Number.isInteger(requestedPage) && requestedPage >= 0 && requestedPage <= 2147483647
    ? requestedPage : 0;
  const orderId = params.get("orderId") || "";
  const scope = [keycloak.tokenParsed?.iss, keycloak.tokenParsed?.sub];
  const enabled = Boolean(initialized && keycloak.authenticated && scope[1]);
  const list = useQuery({
    queryKey: ["buyer-orders", ...scope, state, page],
    queryFn: ({ signal }) => bookingService.getBuyerOrders({ page, size: pageSize, state }, signal),
    enabled,
    retry: false,
  });
  const listedOrder = list.data?.items.find(order => order.orderId === orderId);
  const detail = useQuery({
    queryKey: ["buyer-order-detail", ...scope, orderId],
    queryFn: ({ signal }) => bookingService.getOrder(orderId, signal),
    enabled: enabled && orderIdPattern.test(orderId) && !listedOrder && !list.isPending,
    retry: false,
  });
  const selectedOrder = listedOrder ?? detail.data;
  const isPendingPayment = selectedOrder?.state === "PENDING_PAYMENT";
  const isHoldActive = Boolean(
    isPendingPayment
      && (!Number.isFinite(Date.parse(selectedOrder.expiresAt)) || Date.parse(selectedOrder.expiresAt) > now),
  );

  useEffect(() => {
    if (!isPendingPayment) return;
    setNow(Date.now());
    const timer = window.setInterval(() => setNow(Date.now()), 5000);
    return () => window.clearInterval(timer);
  }, [isPendingPayment, selectedOrder?.orderId]);
  const orderTickets = useQuery({
    queryKey: ["buyer-order-tickets", ...scope, selectedOrder?.orderId],
    queryFn: ({ signal }) => ticketService.getOrderTickets(selectedOrder!.orderId, signal),
    enabled: Boolean(enabled && selectedOrder && selectedOrder.state === "COMPLETED"),
    retry: false,
  });
  const money = (amount: string, currency: string) => new Intl.NumberFormat(i18n.resolvedLanguage, {
    style: "currency", currency,
  }).format(BigInt(amount));
  const dateTime = new Intl.DateTimeFormat(i18n.resolvedLanguage, {
    dateStyle: "medium", timeStyle: "short",
  });
  const updateParams = (changes: Record<string, string>) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value);
      else next.delete(key);
    }
    setParams(next);
  };

  const loadingState = (
    <div className="buyer-orders-empty" role="status">
      <LoaderCircle className="buyer-orders-spinner" size={24} aria-hidden="true" />
      <p>{t("paymentV1.checking")}</p>
    </div>
  );

  const orderDetails = (
    selectedOrder ? (
      <div className="buyer-orders-detail-inner">
        {!listedOrder && (
          <div className="buyer-orders-detail-direct-header">
            <div className="buyer-orders-id-group">
              <span className="buyer-orders-id-label">{t("buyerOrders.orderCode")}:</span>
              <span className="buyer-orders-id-wrap">
                <span className="buyer-orders-id">{selectedOrder.orderId}</span>
              </span>
            </div>
            <div className="buyer-orders-detail-direct-meta">
              <span className="buyer-orders-status" data-state={selectedOrder.state}>
                {t(`bookingState.${selectedOrder.state}`)}
              </span>
              <span className="buyer-orders-total-val">{money(selectedOrder.total, selectedOrder.currency)}</span>
            </div>
          </div>
        )}

        <div className="buyer-orders-items-section">
          <ul className="buyer-orders-items">
            {selectedOrder.items.map(item => {
              const itemTickets = (orderTickets.data?.tickets ?? [])
                .filter(ticket => ticket.orderItemId === item.orderItemId)
                .sort((a, b) => a.ordinal - b.ordinal);
              return (
                <li key={item.orderItemId} className="buyer-orders-item-card">
                  <div className="buyer-orders-item-row-top">
                    <div className="buyer-orders-item-info">
                      <div className="buyer-orders-item-title-row">
                        <strong className="buyer-orders-item-name">{item.ticketTypeName}</strong>
                        <span className="buyer-orders-item-quantity">× {item.quantity}</span>
                      </div>
                      {item.seats.length > 0 && (
                        <div className="buyer-orders-seat-pill-group">
                          {item.seats.map(seat => (
                            <span key={seat.seatId} className="buyer-orders-seat-pill">
                              {seat.seatLabel || [seat.rowName, seat.seatNumber].filter(Boolean).join(" ") || seat.seatId}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                    <span className="buyer-orders-item-subtotal">{money(item.lineSubtotal, selectedOrder.currency)}</span>
                  </div>

                  {selectedOrder.state === "COMPLETED" && (
                    <div className="buyer-orders-ticket-ids">
                      {orderTickets.isPending ? (
                        <span className="buyer-orders-ticket-hint" role="status">{t("ticketV1.loading")}</span>
                      ) : orderTickets.isError ? (
                        <span className="buyer-orders-ticket-hint is-error">{t("ticketV1.loadFailed")}</span>
                      ) : itemTickets.length > 0 ? (
                        <ul className="buyer-orders-ticket-list">
                          {itemTickets.map(ticket => (
                            <li key={ticket.ticketId} className="buyer-orders-ticket-row">
                              <div className="buyer-orders-ticket-main">
                                <span className="buyer-orders-ticket-ordinal">
                                  #{ticket.ordinal}{ticket.seatLabel ? ` · ${ticket.seatLabel}` : ""}
                                </span>
                                <span className="buyer-orders-id-wrap buyer-orders-ticket-id-wrap">
                                  <span className="buyer-orders-id">{ticket.ticketId}</span>
                                  <button
                                    type="button"
                                    className="buyer-orders-copy-btn"
                                    onClick={(e) => handleCopyOrderId(ticket.ticketId, e)}
                                    aria-label={ticket.ticketId}
                                    title={ticket.ticketId}
                                  >
                                    {copiedOrderId === ticket.ticketId ? (
                                      <Check size={12} className="is-copied" aria-hidden="true" />
                                    ) : (
                                      <Copy size={12} aria-hidden="true" />
                                    )}
                                  </button>
                                </span>
                              </div>
                              <span className="buyer-orders-ticket-state" data-ticket-state={ticket.state}>
                                {t(`ticketState.${ticket.state}`)}
                              </span>
                            </li>
                          ))}
                        </ul>
                      ) : orderTickets.data && orderTickets.data.issuanceState !== "COMPLETED" ? (
                        <span className="buyer-orders-ticket-hint">
                          {t(`issuanceState.${orderTickets.data.issuanceState}`)}
                        </span>
                      ) : null}
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        </div>

        <div className="buyer-orders-breakdown-card">
          <div className="buyer-orders-breakdown-row">
            <span>{t("buyerOrders.subtotal")}</span>
            <span>{money(selectedOrder.subtotal, selectedOrder.currency)}</span>
          </div>
          <div className={`buyer-orders-breakdown-row ${BigInt(selectedOrder.discount) > BigInt(0) ? "is-discount" : ""}`}>
            <span>{t("buyerOrders.discount")}</span>
            <span>
              {BigInt(selectedOrder.discount) > BigInt(0)
                ? `-${money(selectedOrder.discount, selectedOrder.currency)}`
                : money("0", selectedOrder.currency)}
            </span>
          </div>
          <div className="buyer-orders-breakdown-row is-total">
            <span>{t("buyerOrders.total")}</span>
            <span className="buyer-orders-breakdown-total">{money(selectedOrder.total, selectedOrder.currency)}</span>
          </div>
        </div>

        {(isHoldActive || selectedOrder.paymentFrozenAt || selectedOrder.state === "COMPLETED") && (
          <div className="buyer-orders-detail-actions">
            {selectedOrder.paymentFrozenAt && (
              <Link className="tix-order-btn-secondary" to={`/payment-result?orderId=${selectedOrder.orderId}`}>
                {t("paymentV1.checkPayment")}
              </Link>
            )}
            {isHoldActive && (
              <Link className="tix-order-btn-primary" to={`/checkout?orderId=${selectedOrder.orderId}`}>
                <span>{t("buyerOrders.proceedToPayment")}</span>
                <ArrowRight size={16} aria-hidden="true" />
              </Link>
            )}
            {selectedOrder.state === "COMPLETED" && (
              <Link className="tix-order-btn-primary" to={`/my-tickets?orderId=${selectedOrder.orderId}`}>
                <span>{t("paymentV1.viewTickets")}</span>
              </Link>
            )}
          </div>
        )}
      </div>
    ) : !orderIdPattern.test(orderId) || detail.isError || detail.isSuccess ? (
      <p role="alert" className="buyer-orders-error-alert">{t("buyerOrders.detailFailed")}</p>
    ) : loadingState
  );

  return (
    <div className="my-orders-page">
      <div className="container account-layout-container">
        <AccountSidebar />
        <main className="account-main-content buyer-orders">
          <header className="buyer-orders-header">
            <h1 className="profile-page-title">{t("nav.orders")}</h1>
            {enabled && (
              <details
                className="tix-custom-select-wrap buyer-orders-filter"
                ref={filterRef}
                onBlur={event => {
                  if (!event.currentTarget.contains(event.relatedTarget)) event.currentTarget.open = false;
                }}
                onKeyDown={event => {
                  if (event.key === "Escape" && event.currentTarget.open) {
                    event.preventDefault();
                    event.currentTarget.open = false;
                    filterTriggerRef.current?.focus();
                  }
                }}
              >
                <summary
                  ref={filterTriggerRef}
                  className="tix-custom-select-trigger"
                  aria-label={`${t("buyerOrders.state")}: ${t(state ? `bookingState.${state}` : "buyerOrders.allStates")}`}
                >
                  <span className="tix-custom-select-label">{t(state ? `bookingState.${state}` : "buyerOrders.allStates")}</span>
                  <ChevronDown className="tix-custom-select-chevron" size={16} aria-hidden="true" />
                </summary>
                <div className="tix-custom-select-menu" role="group" aria-label={t("buyerOrders.state")}>
                  {(["", ...states] as const).map(value => {
                    const active = value === (state ?? "");
                    return (
                      <button
                        key={value}
                        type="button"
                        className={`tix-custom-select-item ${active ? "is-active" : ""}`}
                        aria-pressed={active}
                        onClick={() => {
                          if (filterRef.current) filterRef.current.open = false;
                          filterTriggerRef.current?.focus();
                          if (!active) updateParams({ state: value, page: "", orderId: "" });
                        }}
                      >
                        <span>{t(value ? `bookingState.${value}` : "buyerOrders.allStates")}</span>
                        {active && <Check className="tix-select-check-icon" size={16} aria-hidden="true" />}
                      </button>
                    );
                  })}
                </div>
              </details>
            )}
          </header>

          {!initialized ? loadingState : !enabled ? (
            <section className="buyer-orders-empty buyer-orders-auth-card">
              <span className="buyer-orders-empty-icon"><LogIn size={24} aria-hidden="true" /></span>
              <h2>{t("paymentV1.loginRequired")}</h2>
              <p>{t("auth.signInPrompt")}</p>
              <button
                type="button"
                className="tix-order-btn-primary"
                onClick={() => void keycloak.login({ redirectUri: window.location.href })}
              >
                <LogIn size={16} aria-hidden="true" />
                <span>{t("auth.signIn")}</span>
              </button>
            </section>
          ) : <>
            {orderId && !listedOrder && (
              <section className="buyer-orders-detail-card">
                <div className="buyer-orders-detail-heading">
                  <h2>{t("buyerOrders.details")}</h2>
                  <button
                    type="button"
                    className="tix-order-btn-secondary"
                    onClick={() => updateParams({ orderId: "" })}
                  >
                    {t("buyerOrders.close")}
                  </button>
                </div>
                {orderDetails}
              </section>
            )}

            <section className="buyer-orders-panel" aria-label={t("nav.orders")}>
              {list.isPending ? loadingState : list.isError ? (
                <div className="buyer-orders-empty" role="alert">
                  <span className="buyer-orders-empty-icon is-error"><CircleAlert size={24} aria-hidden="true" /></span>
                  <h2>{t("buyerOrders.listFailed")}</h2>
                  <button type="button" className="tix-order-btn-secondary" onClick={() => void list.refetch()}>
                    {t("paymentV1.refresh")}
                  </button>
                </div>
              ) : <>
                {list.data.items.length === 0 ? (
                  <div className="buyer-orders-empty">
                    <span className="buyer-orders-empty-icon"><ReceiptText size={24} aria-hidden="true" /></span>
                    <h2>{t(state ? "buyerOrders.emptyFiltered" : "buyerOrders.empty")}</h2>
                  </div>
                ) : (
                  <ul className="buyer-orders-list">
                    {list.data.items.map(order => {
                      const isExpanded = orderId === order.orderId;
                      const ticketCount = order.items.reduce((sum, item) => sum + item.quantity, 0);
                      return (
                        <li key={order.orderId} className={`buyer-orders-row ${isExpanded ? "is-expanded" : ""}`}>
                          {/* Top Bar / Card Header */}
                          <div className="buyer-orders-card-header">
                            <div className="buyer-orders-id-group">
                              <span className="buyer-orders-id-label">{t("buyerOrders.orderCode")}:</span>
                              <div className="buyer-orders-id-wrap">
                                <span className="buyer-orders-id">{order.orderId}</span>
                                <button
                                  type="button"
                                  className="buyer-orders-copy-btn"
                                  onClick={(e) => handleCopyOrderId(order.orderId, e)}
                                  aria-label={t("buyerOrders.orderCode")}
                                  title={order.orderId}
                                >
                                  {copiedOrderId === order.orderId ? (
                                    <Check size={13} className="is-copied" aria-hidden="true" />
                                  ) : (
                                    <Copy size={13} aria-hidden="true" />
                                  )}
                                </button>
                              </div>
                            </div>
                            <span className="buyer-orders-status" data-state={order.state}>
                              {t(`bookingState.${order.state}`)}
                            </span>
                          </div>

                          {/* Main Content / Card Body */}
                          <div className="buyer-orders-card-body">
                            <div className="buyer-orders-summary">
                              <span className="buyer-orders-icon">
                                <ReceiptText size={20} aria-hidden="true" />
                              </span>
                              <div className="buyer-orders-identity">
                                <h3 className="buyer-orders-title">
                                  {order.eventTitle || t("nav.orders")}
                                </h3>
                                <div className="buyer-orders-submeta">
                                  {order.items.length > 0 && (
                                    <span className="buyer-orders-meta-chip">
                                      x{t("buyerOrders.ticketCount", { count: ticketCount })}
                                    </span>
                                  )}
                                  {BigInt(order.discount) > BigInt(0) && (
                                    <span className="buyer-orders-meta-chip is-discount">
                                      {t("buyerOrders.discount")}: -{money(order.discount, order.currency)}
                                    </span>
                                  )}
                                  {order.state === "PENDING_PAYMENT" && Number.isFinite(Date.parse(order.expiresAt)) && (
                                    <span className="buyer-orders-meta-chip is-deadline">
                                      <Clock size={13} aria-hidden="true" />
                                      <span>
                                        {t("buyerOrders.paymentDeadline")}:{" "}
                                        <time dateTime={order.expiresAt}>{dateTime.format(new Date(order.expiresAt))}</time>
                                      </span>
                                    </span>
                                  )}
                                </div>
                              </div>
                            </div>

                            <div className="buyer-orders-actions-wrap">
                              <div className="buyer-orders-total-box">
                                <span className="buyer-orders-total-label">{t("buyerOrders.total")}</span>
                                <span className="buyer-orders-total-val">{money(order.total, order.currency)}</span>
                              </div>
                              <button
                                type="button"
                                className={`buyer-orders-toggle ${isExpanded ? "is-active" : ""}`}
                                aria-expanded={isExpanded}
                                aria-controls={isExpanded ? `buyer-order-${order.orderId}` : undefined}
                                onClick={() => updateParams({ orderId: isExpanded ? "" : order.orderId })}
                              >
                                <span>{t(isExpanded ? "buyerOrders.close" : "buyerOrders.details")}</span>
                                <ChevronDown className="buyer-orders-row-chevron" size={16} aria-hidden="true" />
                              </button>
                            </div>
                          </div>

                          {/* Expanded Inline Detail */}
                          {listedOrder?.orderId === order.orderId && (
                            <div className="buyer-orders-inline-detail" id={`buyer-order-${order.orderId}`}>
                              {orderDetails}
                            </div>
                          )}
                        </li>
                      );
                    })}
                  </ul>
                )}

                {(list.data.totalPages > 1 || page > 0) && (
                  <nav className="buyer-orders-pagination" aria-label={t("buyerOrders.pagination")}>
                    <div className="tix-orders-pagination-box">
                      <button
                        type="button"
                        className="tix-orders-page-btn"
                        disabled={page === 0}
                        onClick={() => updateParams({ page: String(Math.min(page - 1, Math.max(0, list.data.totalPages - 1))), orderId: "" })}
                        aria-label={t("buyerOrders.previous")}
                      >
                        <ChevronLeft size={16} aria-hidden="true" />
                      </button>
                      {page < list.data.totalPages && (
                        <span className="tix-orders-page-indicator">
                          {String(page + 1).padStart(2, "0")}/{String(list.data.totalPages).padStart(2, "0")}
                        </span>
                      )}
                      <button
                        type="button"
                        className="tix-orders-page-btn"
                        disabled={page + 1 >= list.data.totalPages}
                        onClick={() => updateParams({ page: String(page + 1), orderId: "" })}
                        aria-label={t("buyerOrders.next")}
                      >
                        <ChevronRight size={16} aria-hidden="true" />
                      </button>
                    </div>
                  </nav>
                )}
              </>}
            </section>
          </>}
        </main>
      </div>
      <Footer />
    </div>
  );
}
