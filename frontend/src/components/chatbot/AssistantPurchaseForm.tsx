import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { bookingService } from "../../services/bookingService";
import { chatService } from "../../services/chatService";
import type { CreateOrderRequest } from "../../types/bookingContract";
import type { PurchaseDraft } from "../../types/assistantContract";
import { useAssistantConversation } from "./AssistantConversationProvider";

export default function AssistantPurchaseForm({ draft, conversationId }: { draft: PurchaseDraft; conversationId: string }) {
  const assistant = useAssistantConversation();
  const navigate = useNavigate();
  const owner = assistant.ownerScope;
  const generation = assistant.generation;
  const storageKey = `flashTicket.assistant:${owner}:draft:${draft.draftId}`;
  const [email, setEmail] = useState("");
  const [frozen, setFrozen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [now, setNow] = useState(Date.now());
  const conversationExpiry = Date.parse(assistant.conversation?.expiresAt || "");
  const expired = !Number.isFinite(Date.parse(draft.expiresAt)) || now >= Date.parse(draft.expiresAt)
    || !Number.isFinite(conversationExpiry) || now >= conversationExpiry;

  useEffect(() => {
    try {
      const saved = localStorage.getItem(storageKey);
      if (saved) {
        const receipt: { request: CreateOrderRequest } = JSON.parse(saved);
        if (!receipt.request?.customerEmail || receipt.request.eventId !== draft.eventId) throw new Error("INVALID_RECEIPT");
        setEmail(receipt.request.customerEmail); setFrozen(true);
      }
    } catch { setFrozen(true); setError("Không đọc được thông tin xác nhận. Không gửi lại với payload khác."); }
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [storageKey, draft.eventId]);

  const confirm = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!owner || busy || expired || !assistant.isCurrent(conversationId, owner, generation)) return;
    setBusy(true); setError("");
    try {
      // Khóa cùng draft giữa các tab; receipt được ghi trước lần gửi đầu tiên.
      if (!navigator.locks) throw new Error("BROWSER_LOCK_UNAVAILABLE");
      await navigator.locks.request(storageKey, async () => {
        if (!assistant.isCurrent(conversationId, owner, generation) || Date.now() >= Date.parse(draft.expiresAt)) return;
        const proposed: CreateOrderRequest = { eventId: draft.eventId, customerEmail: email.trim(),
          selection: { mode: "QUANTITY", items: draft.items } };
        const saved = localStorage.getItem(storageKey);
        const receipt: { request: CreateOrderRequest; orderId?: string } = saved ? JSON.parse(saved) : { request: proposed };
        if (JSON.stringify(receipt.request) !== JSON.stringify(proposed)) {
          setEmail(receipt.request.customerEmail); setFrozen(true);
          throw new Error("DRAFT_PAYLOAD_CONFLICT");
        }
        localStorage.setItem(storageKey, JSON.stringify(receipt)); setFrozen(true);
        if (!assistant.isCurrent(conversationId, owner, generation)) return;
        // Đọc lại server để tab giữ form cũ không gửi đơn sau reset/expiry ở tab khác.
        const latest = await chatService.getConversation();
        if (!assistant.isCurrent(conversationId, owner, generation)) return;
        if (latest.conversationId !== conversationId || !latest.messages.some(message => message.draft?.draftId === draft.draftId)) {
          throw new Error("DRAFT_CONTEXT_CHANGED");
        }
        const order = await bookingService.createOrder(receipt.request, draft.draftId,
          () => assistant.isCurrent(conversationId, owner, generation)
            && Date.now() < Date.parse(draft.expiresAt) && Date.now() < Date.parse(latest.expiresAt));
        if (!assistant.isCurrent(conversationId, owner, generation)) return;
        localStorage.setItem(storageKey, JSON.stringify({ ...receipt, orderId: order.orderId }));
        navigate(`/checkout?orderId=${encodeURIComponent(order.orderId)}`);
      });
    } catch {
      if (assistant.isCurrent(conversationId, owner, generation)) {
        setError("Chưa xác nhận được đơn. Thử lại giữ nguyên email và khóa của bản nháp; không tạo khóa mới.");
      }
    } finally { setBusy(false); }
  };

  if (!owner || !assistant.isCurrent(conversationId, owner, generation)) return null;
  return <form className="assistant-purchase-form" onSubmit={confirm}>
    <strong>Xác nhận vé: {draft.eventTitle}</strong>
    <ul>{draft.items.map(line => <li key={line.ticketTypeId}>
      {draft.ticketTypes.find(ticket => ticket.ticketTypeId === line.ticketTypeId)?.name || line.ticketTypeId} × {line.quantity}
    </li>)}</ul>
    <p>Tạm tính: {draft.estimatedTotal} {draft.currency}</p>
    <p>Bản nháp không giữ vé hay khóa giá. Booking sẽ kiểm tra lại khi bạn xác nhận.</p>
    <label>Email nhận vé (không gửi cho AI)
      <input type="email" required maxLength={254} value={email} disabled={frozen || busy || expired}
        onChange={event => setEmail(event.target.value)} autoComplete="email" />
    </label>
    <p>Hiệu lực đến {new Date(draft.expiresAt).toLocaleString("vi-VN")}</p>
    {error && <p role="alert">{error}</p>}
    <button type="submit" className="btn btn-primary" disabled={busy || expired || !email.trim()}>
      {expired ? "Bản nháp hết hạn" : busy ? "Đang xác nhận…" : frozen ? "Thử lại cùng bản nháp" : "Xác nhận tạo đơn"}
    </button>
  </form>;
}
