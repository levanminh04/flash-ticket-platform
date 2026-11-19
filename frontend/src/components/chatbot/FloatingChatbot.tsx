import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { Link } from "react-router-dom";
import { MessageCircle, X, SendHorizontal, LoaderCircle } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useAssistantConversation } from "./AssistantConversationProvider";
import AssistantPurchaseForm from "./AssistantPurchaseForm";
import { orderIdPattern } from "../common/OrderLookupForm";
import { formatAssistantMoney } from "./assistantMoney";

export const CHATBOT_PROMPT_EVENT = "flashTicket:chatbotPrompt";

export default function FloatingChatbot({ fullPage = false }: { fullPage?: boolean }) {
  const { keycloak } = useKeycloak();
  const { t, i18n } = useTranslation();
  const locale = i18n.language === "vi" ? "vi-VN" : "en";
  const assistant = useAssistantConversation();
  const [opened, setOpened] = useState(fullPage);
  const [input, setInput] = useState("");
  const root = useRef<HTMLDivElement>(null);
  const messages = useRef<HTMLDivElement>(null);
  const visible = fullPage || opened;
  const { allowed, open, conversation, busy, ownerScope } = assistant;

  useEffect(() => { setInput(""); }, [ownerScope, conversation?.conversationId]);
  useEffect(() => { if (visible && allowed) void open(); }, [visible, allowed, ownerScope, open]);
  useEffect(() => { messages.current?.scrollTo({ top: messages.current.scrollHeight, behavior: "smooth" }); }, [conversation, busy]);
  useEffect(() => {
    const handlePrompt = (event: Event) => {
      const prompt = (event as CustomEvent<string>).detail;
      if (typeof prompt !== "string") return;
      setOpened(true); setInput(prompt.slice(0, 2000));
    };
    window.addEventListener(CHATBOT_PROMPT_EVENT, handlePrompt);
    return () => window.removeEventListener(CHATBOT_PROMPT_EVENT, handlePrompt);
  }, []);
  useEffect(() => {
    if (!opened || fullPage) return;
    const closeOutside = (event: PointerEvent) => {
      if (root.current && !root.current.contains(event.target as Node)) setOpened(false);
    };
    document.addEventListener("pointerdown", closeOutside);
    return () => document.removeEventListener("pointerdown", closeOutside);
  }, [opened, fullPage]);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await assistant.send(input);
    // Giữ nội dung để retry cùng requestId khi kết quả chưa rõ.
  };
  const quickPrompts = ["quickHot", "quickUpcoming", "quickLocation", "quickTicketTypes", "quickCheckOrders", "quickGuide"];
  return <div className={fullPage ? "assistant-full-page" : "floating-chatbot"} ref={root}>
    {visible && <section className={fullPage ? "chatbot-shell" : "floating-chatbot-panel"} aria-label="FlashTicket Assistant">
      <header className="floating-chatbot-header">
        <strong>FlashTicket Assistant</strong>
        <div>
          {allowed && <button type="button" onClick={() => void assistant.reset()} disabled={!conversation}>Hội thoại mới</button>}
          {!fullPage && <><Link to="/chatbot">Mở rộng</Link><button type="button" onClick={() => setOpened(false)} aria-label={t("chatbot.close")}><X size={16} /></button></>}
        </div>
      </header>
      {!allowed ? <div className="assistant-status">
        <p>{keycloak.authenticated ? "Trợ lý hiện chỉ dành cho tài khoản BUYER." : t("chatbot.loginRequired")}</p>
        {!keycloak.authenticated && <button type="button" onClick={() => void keycloak.login()}>{t("auth.signIn")}</button>}
      </div> : <>
        <div className={fullPage ? "chatbot-messages" : "floating-chatbot-messages"} ref={messages} aria-live="polite" aria-busy={busy}>
          {!conversation?.messages.length && <><p>{t("chatbot.welcomeMessage")}</p><p>Không gửi mật khẩu, token hoặc thông tin nhạy cảm vào chat.</p></>}
          {conversation?.messages.map(message => <article key={message.id} className={`floating-msg floating-msg--${message.role}`}>
            <div className="floating-msg-body">
              <p className="assistant-message-text">{message.text}</p>
              {message.events?.map(event => <Link className="assistant-event-link" key={event.eventId} to={`/event/${encodeURIComponent(event.eventId)}`}>{event.title}</Link>)}
              {message.role === "assistant" && Array.isArray(message.orders) && (message.orders.length === 0
                ? <div className="assistant-order-card">
                  <p>{t("chatbot.orderSnapshot.empty")}</p>
                  <Link className="assistant-card-link" to="/my-orders">{t("chatbot.orderSnapshot.myOrders")}</Link>
                </div>
                : message.orders.map(order => {
                  if (typeof order?.orderId !== "string" || !orderIdPattern.test(order.orderId)) return null;
                  const lookedUpAt = Date.parse(message.createdAt);
                  const holdExpiresAt = order.state === "PENDING_PAYMENT" ? Date.parse(order.expiresAt || "") : NaN;
                  const knownState = ["PENDING_PAYMENT", "ISSUING", "COMPLETED", "CANCELLED", "EXPIRED", "ISSUANCE_FAILED"].includes(order.state || "");
                  return <div className="assistant-order-card" key={order.orderId}>
                    <strong>{typeof order.eventTitle === "string" && order.eventTitle.trim() ? order.eventTitle : t("chatbot.orderSnapshot.unknownEvent")}</strong>
                    <small>{t("paymentV1.orderId")}: {order.orderId}</small>
                    <p>{knownState ? t(`bookingState.${order.state}`) : t("chatbot.orderSnapshot.unknownState")}</p>
                    <p><b>{formatAssistantMoney(order.total, order.currency, locale)}</b></p>
                    {Number.isFinite(holdExpiresAt) && <p>{t("chatbot.orderSnapshot.holdsUntil", { time: new Date(holdExpiresAt).toLocaleString(locale) })}</p>}
                    <small>{t("chatbot.orderSnapshot.asOf", { time: Number.isFinite(lookedUpAt) ? new Date(lookedUpAt).toLocaleString(locale) : "—" })}</small>
                    <Link className="assistant-card-link" to={`/my-orders?orderId=${encodeURIComponent(order.orderId)}`}>{t("chatbot.orderSnapshot.view")}</Link>
                  </div>;
                }))}
              {message.draft && <AssistantPurchaseForm key={`${ownerScope}:${conversation.conversationId}:${message.draft.draftId}`} draft={message.draft} conversationId={conversation.conversationId} />}
              <time className="floating-msg-time" dateTime={message.createdAt}>{new Date(message.createdAt).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })}</time>
            </div>
          </article>)}
          {busy && <p><LoaderCircle size={14} className="spin" /> {t("chatbot.replying")}</p>}
        </div>
        <div className="assistant-status">
          {assistant.notice && <p role="status">{assistant.notice}</p>}
          {assistant.error && <p role="alert">{assistant.error}</p>}
          <button type="button" disabled={busy} onClick={() => void open()}>{conversation ? "Đọc lại hội thoại" : "Mở hội thoại"}</button>
        </div>
        {!conversation?.messages.length && <div className="floating-chatbot-options">{quickPrompts.map(key => <button key={key} type="button" onClick={() => setInput(t(`chatbot.${key}Prompt`))}>{t(`chatbot.${key}`)}</button>)}</div>}
        <form className="floating-chatbot-input" onSubmit={submit}>
          <input aria-label="Tin nhắn cho trợ lý" value={input} onChange={event => setInput(event.target.value)} placeholder={t("chatbot.placeholder")} maxLength={2000} disabled={!conversation || busy} />
          <button type="submit" disabled={!conversation || busy || !input.trim()} aria-label={t("chatbot.send")}><SendHorizontal size={16} /></button>
        </form>
      </>}
    </section>}
    {!fullPage && <button type="button" className="floating-chatbot-trigger" onClick={() => setOpened(value => !value)} aria-expanded={opened} aria-label={t("chatbot.open")}><MessageCircle size={20} /></button>}
  </div>;
}
