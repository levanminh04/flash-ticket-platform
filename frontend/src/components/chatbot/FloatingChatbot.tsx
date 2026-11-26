import { useEffect, useId, useLayoutEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { Link } from "react-router-dom";
import { ArrowLeftRight, Bot, CalendarCheck, ChevronRight, Expand, LayoutGrid, LoaderCircle, LogIn, Plus, SendHorizontal, Ticket, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import robot from "../../assets/images/chatbot/chatbot-robot.webp";
import { useAssistantConversation } from "./AssistantConversationProvider";
import AssistantPurchaseForm from "./AssistantPurchaseForm";
import { orderIdPattern } from "../common/OrderLookupForm";
import { formatAssistantMoney } from "./assistantMoney";

export const CHATBOT_PROMPT_EVENT = "flashTicket:chatbotPrompt";

const workspaceLinks = [
  { target: "buyer", role: "BUYER", href: "/chatbot", label: "chatbot.switchToBuyer" },
  { target: "organizer", role: "ORGANIZER", href: "/organizer/chatbot", label: "chatbot.switchToOrganizer" },
  { target: "admin", role: "ADMIN", href: "/admin/chatbot", label: "chatbot.switchToAdmin" },
] as const;

export default function FloatingChatbot({ fullPage = false }: { fullPage?: boolean }) {
  const { keycloak, initialized } = useKeycloak();
  const { t, i18n } = useTranslation();
  const locale = i18n.language === "vi" ? "vi-VN" : "en";
  const assistant = useAssistantConversation();
  const [opened, setOpened] = useState(fullPage);
  const [draft, setDraft] = useState<{ text: string; id: string; scope: string; revision: number } | null>(null);
  const nearBottom = useRef(true);
  const previousHistory = useRef<{ scope: string; conversationId: string; tailId: string | undefined; revision: number } | null>(null);
  const root = useRef<HTMLDivElement>(null);
  const messages = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const composer = useRef<HTMLInputElement>(null);
  const panelId = useId();
  const visible = fullPage || opened;
  const { workspace, allowed, open, conversation, busy, turnPhase, operation, ownerScope, error, notice,
    confirmedDraftId, composerRevision, historyRevision } = assistant;
  const scope = allowed ? ownerScope : null;
  const activeDraft = scope && draft?.scope === scope && draft.revision === composerRevision
    && draft.id !== confirmedDraftId ? draft : null;
  const input = activeDraft?.text ?? "";
  const conversationId = conversation?.conversationId;
  const tailId = conversation?.messages[conversation.messages.length - 1]?.id;
  const isBuyer = workspace === "buyer";
  const isOrganizer = workspace === "organizer";
  const isAdmin = workspace === "admin";
  const title = t(isAdmin ? "chatbot.adminTitle" : isOrganizer ? "chatbot.organizerTitle" : "chatbot.title");
  const hasMessages = Boolean(conversation?.messages.length);

  useEffect(() => {
    setDraft(current => current && (current.scope !== scope || current.revision !== composerRevision
      || current.id === confirmedDraftId) ? null : current);
  }, [scope, composerRevision, confirmedDraftId]);
  useEffect(() => {
    if (!visible || !allowed) return;
    // Đợi provider dọn phiên cũ trước khi mở hội thoại sau khi khởi tạo tài khoản.
    const timer = window.setTimeout(() => void open(), 0);
    return () => window.clearTimeout(timer);
  }, [visible, allowed, ownerScope, open]);
  useLayoutEffect(() => {
    const transcript = messages.current;
    if (!visible || !scope || !conversationId || !transcript) {
      previousHistory.current = null;
      nearBottom.current = true;
      return;
    }
    const previous = previousHistory.current;
    previousHistory.current = { scope, conversationId, tailId, revision: historyRevision };
    const loadedHistory = !previous || previous.scope !== scope || previous.conversationId !== conversationId
      || previous.revision !== historyRevision;
    if (!loadedHistory && previous.tailId === tailId) return;
    const isAppend = !loadedHistory && tailId && (!previous.tailId
      || conversation?.messages.some(message => message.id === previous.tailId));
    if (isAppend && !nearBottom.current) return;
    const smooth = isAppend && !window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    transcript.scrollTo({ top: transcript.scrollHeight, behavior: smooth ? "smooth" : "instant" });
    if (!smooth) nearBottom.current = true;
  }, [visible, scope, conversationId, tailId, historyRevision, conversation]);
  useEffect(() => {
    const handlePrompt = (event: Event) => {
      const prompt = (event as CustomEvent<string>).detail;
      if (typeof prompt !== "string") return;
      setOpened(true);
      if (scope) setDraft({ text: prompt.slice(0, 2000), id: crypto.randomUUID(), scope, revision: composerRevision });
    };
    window.addEventListener(CHATBOT_PROMPT_EVENT, handlePrompt);
    return () => window.removeEventListener(CHATBOT_PROMPT_EVENT, handlePrompt);
  }, [scope, composerRevision]);
  useEffect(() => {
    if (!opened || fullPage) return;
    const closeOutside = (event: PointerEvent) => {
      if (root.current && !root.current.contains(event.target as Node)) setOpened(false);
    };
    document.addEventListener("pointerdown", closeOutside);
    return () => document.removeEventListener("pointerdown", closeOutside);
  }, [opened, fullPage]);

  const close = () => { setOpened(false); trigger.current?.focus(); };
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (activeDraft) await assistant.send(activeDraft.text, activeDraft.id);
  };

  return <div className={fullPage ? "assistant-full-page" : "floating-chatbot"} ref={root}
    onKeyDown={event => { if (!fullPage && opened && event.key === "Escape") close(); }}>
    {!fullPage && !opened && <button ref={trigger} type="button" className="floating-chatbot-trigger"
      onClick={() => setOpened(true)} aria-expanded={false}
      aria-label={t("chatbot.open")}>
      <img src={robot} alt="" />
    </button>}
    {visible && <section id={panelId} className={fullPage ? "chatbot-shell" : "floating-chatbot-panel"} aria-label={title}>
      <header className="floating-chatbot-header">
        <div className="assistant-identity">
          <span className="assistant-avatar" aria-hidden="true">
            <Bot size={18} />
          </span>
          {isBuyer ? <strong>{title}</strong> : <h1>{title}</h1>}
        </div>
        <div className="assistant-header-actions">
          {isOrganizer && <Link to="/organizer">
            <LayoutGrid size={16} aria-hidden="true" />
            {t("organizer.dashboard")}
          </Link>}
          {isAdmin && <>
            <Link to="/admin/sales">
              <LayoutGrid size={16} aria-hidden="true" />
              {t("chatbot.adminSales")}
            </Link>
            <Link to="/admin/events">
              <CalendarCheck size={16} aria-hidden="true" />
              {t("chatbot.adminReview")}
            </Link>
          </>}
          {fullPage && allowed && workspaceLinks
            .filter(({ target, role }) => target !== workspace && keycloak.hasRealmRole(role))
            .map(({ target, href, label }) => <Link key={target} to={href}>
              <ArrowLeftRight size={16} aria-hidden="true" />
              {t(label)}
            </Link>)}
          {allowed && <button type="button" onClick={() => void assistant.reset()} disabled={!conversation}>
            <Plus size={16} aria-hidden="true" />{t("chatbot.newConversation")}
          </button>}
          {!fullPage && <>
            <Link to="/chatbot" aria-label={t("chatbot.expand")} title={t("chatbot.expand")}><Expand size={16} aria-hidden="true" /></Link>
            <button type="button" onClick={close} aria-label={t("chatbot.close")}><X size={20} aria-hidden="true" /></button>
          </>}
        </div>
      </header>
      {!allowed ? <div className="assistant-gate">
        <div className="assistant-gate-icon" aria-hidden="true">
          <Bot size={28} />
        </div>
        <p role="status">{t(!initialized ? "chatbot.loadingAccount" : keycloak.authenticated
          ? isAdmin ? "chatbot.adminOnly" : isOrganizer ? "chatbot.organizerOnly" : "chatbot.buyerOnly"
          : "chatbot.loginRequired")}</p>
        {initialized && !keycloak.authenticated && (
          <button className="assistant-primary" type="button" onClick={() => void keycloak.login()}>
            <LogIn size={16} aria-hidden="true" />
            {t("auth.signIn")}
          </button>
        )}
      </div> : <>
        <div className="assistant-transcript" ref={messages} tabIndex={0} role="region" aria-label={t("chatbot.transcript")}
          onScroll={event => {
            const { scrollHeight, scrollTop, clientHeight } = event.currentTarget;
            nearBottom.current = scrollHeight - scrollTop - clientHeight <= 64;
          }}>
          {!hasMessages && !busy && !error && <div className="assistant-welcome">
            <Ticket size={24} aria-hidden="true" />
            <h2>{t("chatbot.welcomeTitle")}</h2>
            <p>{t(isAdmin ? "chatbot.adminWelcome" : isOrganizer ? "chatbot.organizerWelcome" : "chatbot.welcomeMessage")}</p>
          </div>}
          <div className="assistant-messages" role="log" aria-label={t("chatbot.transcript")}>
            {conversation?.messages.map(message => <article key={message.id} className={`floating-msg floating-msg--${message.role}`}>
              <div className="floating-msg-body">
                <span className="assistant-message-author">{t(message.role === "user" ? "chatbot.you" : "chatbot.assistant")}</span>
                <p className="assistant-message-text">{message.text}</p>
                {isBuyer && message.events?.map(event => <Link className="assistant-event-link" key={event.eventId} to={`/event/${encodeURIComponent(event.eventId)}`}>
                  <Ticket size={16} aria-hidden="true" /><span>{event.title}</span><ChevronRight size={16} aria-hidden="true" />
                </Link>)}
                {isBuyer && message.role === "assistant" && Array.isArray(message.orders) && (message.orders.length === 0
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
                      <p className="assistant-order-state">{knownState ? t(`bookingState.${order.state}`) : t("chatbot.orderSnapshot.unknownState")}</p>
                      {Number.isFinite(holdExpiresAt) && <p>{t("chatbot.orderSnapshot.holdsUntil", { time: new Date(holdExpiresAt).toLocaleString(locale) })}</p>}
                      <small>{t("chatbot.orderSnapshot.asOf", { time: Number.isFinite(lookedUpAt) ? new Date(lookedUpAt).toLocaleString(locale) : "—" })}</small>
                      <p className="assistant-ticket-total"><span>{t("chatbot.orderTotal")}</span><b>{formatAssistantMoney(order.total, order.currency, locale)}</b></p>
                      <Link className="assistant-card-link" to={`/my-orders?orderId=${encodeURIComponent(order.orderId)}`}>{t("chatbot.orderSnapshot.view")}</Link>
                    </div>;
                  }))}
                {isBuyer && message.draft && <AssistantPurchaseForm key={`${ownerScope}:${conversation.conversationId}:${message.draft.draftId}`} draft={message.draft} conversationId={conversation.conversationId} />}
                <time className="floating-msg-time" dateTime={message.createdAt}>{new Date(message.createdAt).toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" })}</time>
              </div>
            </article>)}
          </div>
          <div className="assistant-progress" role="status">{busy && <>
            <LoaderCircle size={16} aria-hidden="true" />{t(turnPhase === "processing" && operation
              ? `chatbot.toolProgress.${operation}` : turnPhase ? `chatbot.turn.${turnPhase}` : "chatbot.loadingConversation")}
          </>}</div>
        </div>
        <footer className="assistant-footer">
          <div className="assistant-status">
            <p role="status">{notice}</p>
            <p role="alert">{error}</p>
          </div>
          <form className="floating-chatbot-input" onSubmit={submit}>
            <input ref={composer} aria-label={t("chatbot.messageLabel")} value={input} onChange={event => {
              const text = event.target.value;
              if (scope && text !== input) setDraft({ text, id: crypto.randomUUID(), scope, revision: composerRevision });
            }} placeholder={t("chatbot.placeholder")} maxLength={2000} disabled={!conversation || busy} />
            <button className="assistant-primary" type="submit" disabled={!conversation || busy || !input.trim()} aria-label={t("chatbot.send")}>
              <SendHorizontal size={20} aria-hidden="true" />
            </button>
          </form>
          <div className="assistant-footer-meta">
            <small>{t("chatbot.privacyHint")}</small>
            <button type="button" disabled={busy} onClick={() => void open()}>
              {t(conversation ? "chatbot.reloadConversation" : "chatbot.openConversation")}
            </button>
          </div>
        </footer>
      </>}
    </section>}
  </div>;
}
