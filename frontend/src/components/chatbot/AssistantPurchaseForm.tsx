import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { isAxiosError } from "axios";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { bookingService } from "../../services/bookingService";
import { chatService } from "../../services/chatService";
import type { CreateOrderRequest, OrderView } from "../../types/bookingContract";
import type { PurchaseDraft } from "../../types/assistantContract";
import { orderIdPattern } from "../common/OrderLookupForm";
import { useAssistantConversation } from "./AssistantConversationProvider";
import { formatAssistantMoney, normalizeAssistantVnd } from "./assistantMoney";

type PurchaseOutcome =
  | { kind: "ready" | "notSent" | "stale" | "blocked" | "conflict" }
  | { kind: "uncertain"; retry: boolean }
  | { kind: "rejected"; reason: "soldOut" | "purchaseLimit" | "notOnSale" }
  | { kind: "knownOrder"; orderId: string }
  | { kind: "placed"; order: OrderView; matchesDraft: boolean };

export default function AssistantPurchaseForm({ draft, conversationId }: { draft: PurchaseDraft; conversationId: string }) {
  const assistant = useAssistantConversation();
  const { t, i18n } = useTranslation();
  const locale = i18n.language === "vi" ? "vi-VN" : "en";
  const owner = assistant.ownerScope;
  const generation = assistant.generation;
  const storageKey = `flashTicket.assistant:${owner}:draft:${draft.draftId}`;
  const [email, setEmail] = useState("");
  const [outcome, setOutcome] = useState<PurchaseOutcome>({ kind: "ready" });
  const [busy, setBusy] = useState(false);
  const submitting = useRef(false);
  const [now, setNow] = useState(Date.now());
  const draftExpiry = Date.parse(draft.expiresAt);
  const conversationExpiry = Date.parse(assistant.conversation?.expiresAt || "");
  const expired = !(now < draftExpiry && now < conversationExpiry);
  const selection = JSON.stringify(draft.items.map(line => [line.ticketTypeId, line.quantity]));
  const canSubmit = outcome.kind === "ready" || outcome.kind === "notSent" || (outcome.kind === "uncertain" && outcome.retry);

  useEffect(() => {
    try {
      const saved = localStorage.getItem(storageKey);
      if (saved !== null) {
        const receipt: { request: CreateOrderRequest; orderId?: string } = JSON.parse(saved);
        const request = receipt?.request;
        if (typeof request?.customerEmail !== "string" || request.customerEmail.length > 254
          || !/^[^\s@]+@[^\s@]+$/.test(request.customerEmail) || request.eventId !== draft.eventId
          || request.selection?.mode !== "QUANTITY" || !Array.isArray(request.selection.items)
          || JSON.stringify(request.selection.items.map(line => [line?.ticketTypeId, line?.quantity])) !== selection
          || (receipt.orderId !== undefined && (typeof receipt.orderId !== "string" || !orderIdPattern.test(receipt.orderId)))) {
          throw new Error("INVALID_RECEIPT");
        }
        setEmail(request.customerEmail);
        setOutcome(receipt.orderId ? { kind: "knownOrder", orderId: receipt.orderId } : { kind: "uncertain", retry: true });
      }
    } catch { setOutcome({ kind: "blocked" }); }
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [storageKey, draft.eventId, selection]);

  const confirm = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!owner || submitting.current || !canSubmit || !(Date.now() < draftExpiry && Date.now() < conversationExpiry)
      || !assistant.isCurrent(conversationId, owner, generation)) return;
    if (!navigator.locks || !orderIdPattern.test(draft.draftId) || !orderIdPattern.test(draft.eventId)) {
      setOutcome({ kind: "blocked" });
      return;
    }
    // Chặn hai submit cùng form trước khi React kịp render nút disabled.
    submitting.current = true;
    setBusy(true);
    let uncertain = outcome.kind === "uncertain";
    let bookingAttempted = false;
    try {
      // Đọc receipt trong khóa: tab khác có thể đã lưu ID sau khi form này mở.
      await navigator.locks.request(storageKey, async () => {
        if (!assistant.isCurrent(conversationId, owner, generation)) return;
        if (!(Date.now() < draftExpiry && Date.now() < conversationExpiry)) {
          setOutcome(uncertain ? { kind: "uncertain", retry: false } : { kind: "stale" });
          return;
        }
        let request: CreateOrderRequest = { eventId: draft.eventId, customerEmail: email.trim(),
          selection: { mode: "QUANTITY", items: draft.items } };
        try {
          const saved = localStorage.getItem(storageKey);
          if (saved === null && uncertain) {
            setOutcome({ kind: "uncertain", retry: false });
            return;
          }
          if (saved !== null) {
            const receipt: { request: CreateOrderRequest; orderId?: string } = JSON.parse(saved);
            const stored = receipt?.request;
            if (typeof stored?.customerEmail !== "string" || stored.customerEmail.length > 254
              || !/^[^\s@]+@[^\s@]+$/.test(stored.customerEmail) || stored.eventId !== draft.eventId
              || stored.selection?.mode !== "QUANTITY" || !Array.isArray(stored.selection.items)
              || JSON.stringify(stored.selection.items.map(line => [line?.ticketTypeId, line?.quantity])) !== selection
              || (receipt.orderId !== undefined && (typeof receipt.orderId !== "string" || !orderIdPattern.test(receipt.orderId)))) {
              throw new Error("INVALID_RECEIPT");
            }
            if (receipt.orderId) { setOutcome({ kind: "knownOrder", orderId: receipt.orderId }); return; }
            uncertain = true;
            if (stored.customerEmail !== request.customerEmail) { setOutcome({ kind: "conflict" }); return; }
            request = stored;
          }
        } catch { setOutcome({ kind: "blocked" }); return; }

        const latest = await chatService.getConversation();
        if (!assistant.isCurrent(conversationId, owner, generation)) return;
        const latestDraft = latest.messages.find(message => message.draft?.draftId === draft.draftId)?.draft;
        const latestExpiry = Date.parse(latest.expiresAt);
        if (latest.conversationId !== conversationId || !latestDraft || latestDraft.eventId !== draft.eventId
          || Date.parse(latestDraft.expiresAt) !== draftExpiry || !(Date.now() < draftExpiry && Date.now() < latestExpiry)
          || JSON.stringify(latestDraft.items.map(line => [line.ticketTypeId, line.quantity])) !== selection) {
          setOutcome(uncertain ? { kind: "uncertain", retry: false } : { kind: "stale" });
          return;
        }
        try { localStorage.setItem(storageKey, JSON.stringify({ request })); }
        catch { setOutcome({ kind: "blocked" }); return; }

        let order: OrderView;
        try {
          bookingAttempted = true;
          order = await bookingService.createOrder(request, draft.draftId, () => assistant.isCurrent(conversationId, owner, generation)
            && Date.now() < draftExpiry && Date.now() < latestExpiry);
        } catch (error) {
          if (!assistant.isCurrent(conversationId, owner, generation)) return;
          const code = isAxiosError(error) && error.response?.status === 409 ? error.response.data?.code : undefined;
          const reason = code === "INSUFFICIENT_QUANTITY" ? "soldOut" : code === "PURCHASE_LIMIT_EXCEEDED" ? "purchaseLimit"
            : code === "EVENT_NOT_ON_SALE" ? "notOnSale" : undefined;
          if (code === "IDEMPOTENCY_CONFLICT") setOutcome({ kind: "conflict" });
          else if (!uncertain && reason) setOutcome({ kind: "rejected", reason });
          else setOutcome({ kind: "uncertain", retry: true });
          return;
        }
        if (!assistant.isCurrent(conversationId, owner, generation)) return;
        if (typeof order?.orderId !== "string" || !orderIdPattern.test(order.orderId) || order.eventId !== draft.eventId
          || typeof order.state !== "string" || !order.state || !Array.isArray(order.items)) {
          setOutcome({ kind: "uncertain", retry: true });
          return;
        }
        const estimate = normalizeAssistantVnd(draft.estimatedTotal);
        const expected = new Map(draft.items.map(line => [line.ticketTypeId, line.quantity]));
        let matchesDraft = order.currency === "VND" && draft.currency === "VND" && estimate !== null
          && estimate === normalizeAssistantVnd(order.total) && expected.size === draft.items.length && order.items.length === expected.size;
        for (const line of order.items) {
          const ticket = draft.ticketTypes.find(type => type.ticketTypeId === line?.ticketTypeId);
          const unit = ticket?.currency === "VND" ? normalizeAssistantVnd(ticket.unitPrice) : null;
          if (!line || !expected.has(line.ticketTypeId) || expected.get(line.ticketTypeId) !== line.quantity
            || unit === null || unit !== normalizeAssistantVnd(line.unitPrice)) {
            matchesDraft = false;
            break;
          }
          expected.delete(line.ticketTypeId);
        }
        // Booking đã trả đơn: lỗi ghi receipt không được biến kết quả thành lỗi đặt vé.
        setOutcome({ kind: "placed", order, matchesDraft });
        try { localStorage.setItem(storageKey, JSON.stringify({ request, orderId: order.orderId })); }
        catch { /* Lần tải lại không khôi phục được kết quả; vẫn dùng cùng receipt và khóa. */ }
      });
    } catch (error) {
      if (assistant.isCurrent(conversationId, owner, generation)) {
        const ended = isAxiosError(error) && error.response?.status === 410;
        setOutcome(uncertain || bookingAttempted ? { kind: "uncertain", retry: !ended } : { kind: ended ? "stale" : "notSent" });
      }
    } finally {
      submitting.current = false;
      setBusy(false);
    }
  };

  if (!owner || !assistant.isCurrent(conversationId, owner, generation)) return null;
  const eventPath = orderIdPattern.test(draft.eventId) ? encodeURIComponent(draft.eventId) : null;
  const estimate = formatAssistantMoney(draft.estimatedTotal, draft.currency, locale);
  if (outcome.kind === "placed" || outcome.kind === "knownOrder") {
    const orderId = outcome.kind === "placed" ? outcome.order.orderId : outcome.orderId;
    const holdExpiry = outcome.kind === "placed" && outcome.order.state === "PENDING_PAYMENT" ? Date.parse(outcome.order.expiresAt) : NaN;
    const checkout = outcome.kind === "placed" && outcome.matchesDraft && now < holdExpiry;
    return <div className="assistant-purchase-form" role="status">
      <strong>{t("chatbot.purchaseDraft.complete")}</strong>
      <p>{t("paymentV1.orderId")}: {orderId}</p>
      {outcome.kind === "placed" ? <>
        <p className="assistant-ticket-total"><span>{t("chatbot.purchaseDraft.bookingTotal")}</span>
          <b>{formatAssistantMoney(outcome.order.total, outcome.order.currency, locale)}</b></p>
        {Number.isFinite(holdExpiry) && <p>{t("chatbot.orderSnapshot.holdsUntil", { time: new Date(holdExpiry).toLocaleString(locale) })}</p>}
        {!outcome.matchesDraft && <>
          <p>{t("chatbot.purchaseDraft.estimate")}: {estimate}</p>
          <p>{t("chatbot.purchaseDraft.priceReview")}</p>
        </>}
      </> : <p>{t("chatbot.purchaseDraft.knownOrder")}</p>}
      <Link className="assistant-card-link" to={checkout ? `/checkout?orderId=${encodeURIComponent(orderId)}` : `/my-orders?orderId=${encodeURIComponent(orderId)}`}>
        {t(checkout ? "chatbot.purchaseDraft.checkout" : "chatbot.orderSnapshot.view")}
      </Link>
    </div>;
  }

  const status = outcome.kind === "rejected" ? outcome.reason : outcome.kind === "uncertain" && (!outcome.retry || expired)
    ? "uncertainNoRetry" : outcome.kind;
  const chooseAgain = outcome.kind === "ready" || outcome.kind === "notSent" || (outcome.kind === "rejected" && outcome.reason !== "notOnSale");
  return <form className="assistant-purchase-form" onSubmit={confirm}>
    <strong>{t("chatbot.purchaseDraft.title", { event: draft.eventTitle })}</strong>
    <ul className="assistant-ticket-lines">{draft.items.map(line => {
      const ticket = draft.ticketTypes.find(type => type.ticketTypeId === line.ticketTypeId);
      return <li key={line.ticketTypeId}>
        <span>{ticket?.name || line.ticketTypeId} × {line.quantity}</span>
        <span>{t("chatbot.purchaseDraft.unitPrice", { price: formatAssistantMoney(ticket?.unitPrice, ticket?.currency, locale) })}</span>
      </li>;
    })}</ul>
    <p className="assistant-ticket-total"><span>{t("chatbot.purchaseDraft.estimate")}</span><b>{estimate}</b></p>
    <p className="assistant-receipt-note">{t("chatbot.purchaseDraft.note")}</p>
    {Number.isFinite(draftExpiry) && <p>{t("chatbot.purchaseDraft.draftUntil", { time: new Date(draftExpiry).toLocaleString(locale) })}</p>}
    {canSubmit && <label>{t("chatbot.purchaseDraft.email")}
      <input type="email" required maxLength={254} value={email} disabled={outcome.kind === "uncertain" || busy || expired}
        onChange={event => setEmail(event.target.value)} autoComplete="email" />
    </label>}
    {status !== "ready" && <p role="alert">{t(`chatbot.purchaseDraft.${status}`)}</p>}
    <div className="assistant-purchase-actions">
      {canSubmit && <button type="submit" className="btn btn-primary" disabled={busy || expired || !email.trim()}>
        {t(expired ? "chatbot.purchaseDraft.expired" : busy ? "chatbot.purchaseDraft.confirming"
          : outcome.kind === "uncertain" ? "chatbot.purchaseDraft.retry" : "chatbot.purchaseDraft.confirm")}
      </button>}
      {eventPath && chooseAgain && !busy && <Link className="assistant-card-link" to={`/events/${eventPath}/book`}>{t("chatbot.purchaseDraft.chooseAgain")}</Link>}
      {eventPath && outcome.kind === "rejected" && outcome.reason === "notOnSale" && <Link className="assistant-card-link" to={`/event/${eventPath}`}>{t("chatbot.purchaseDraft.viewEvent")}</Link>}
      {(outcome.kind === "uncertain" || outcome.kind === "blocked" || outcome.kind === "conflict")
        && <Link className="assistant-card-link" to="/my-orders">{t("chatbot.orderSnapshot.myOrders")}</Link>}
    </div>
  </form>;
}
