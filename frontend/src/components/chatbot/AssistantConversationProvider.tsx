import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { isAxiosError } from "axios";
import { useQueryClient } from "@tanstack/react-query";
import { assistantOwnerScope, chatService } from "../../services/chatService";
import { confirmDestructiveAction } from "../../lib/swal";
import type { ChatRequest, ConversationView } from "../../types/assistantContract";

interface AssistantConversationContextValue {
  conversation: ConversationView | null;
  ownerScope: string | null;
  generation: number;
  busy: boolean;
  error: string;
  notice: string;
  allowed: boolean;
  open(): Promise<void>;
  send(message: string): Promise<void>;
  reset(): Promise<void>;
  clearOwner(): void;
  isCurrent(conversationId: string, ownerScope: string, generation?: number): boolean;
}
interface ScopedState {
  owner: string | null;
  conversation: ConversationView | null;
  busy: boolean;
  error: string;
  notice: string;
}
const AssistantContext = createContext<AssistantConversationContextValue | null>(null);

export function AssistantConversationProvider({ children }: { children: ReactNode }): JSX.Element {
  const { keycloak, initialized } = useKeycloak();
  const queryClient = useQueryClient();
  const owner = assistantOwnerScope();
  const allowed = Boolean(initialized && owner && keycloak.hasRealmRole("BUYER"));
  const [state, setState] = useState<ScopedState>({ owner, conversation: null, busy: false, error: "", notice: "" });
  const current = useRef(state);
  const generation = useRef(0);
  const controller = useRef<AbortController | null>(null);
  const pending = useRef<{ owner: string; request: ChatRequest } | null>(null);
  const lifecycleOwner = useRef(owner);
  const lifecycleAllowed = useRef(allowed);
  const renderedOwner = useRef(owner);
  const renderedAllowed = useRef(allowed);
  renderedOwner.current = owner;
  renderedAllowed.current = allowed;

  const clearOwner = useCallback(() => {
    generation.current++;
    controller.current?.abort();
    controller.current = null;
    pending.current = null;
    const next = { owner: assistantOwnerScope(), conversation: null, busy: false, error: "", notice: "" };
    current.current = next;
    setState(next);
    queryClient.removeQueries({ queryKey: ["assistant"] });
  }, [queryClient]);

  useEffect(() => {
    if (lifecycleOwner.current !== owner || lifecycleAllowed.current !== allowed) {
      const previousOwner = lifecycleOwner.current;
      lifecycleOwner.current = owner;
      lifecycleAllowed.current = allowed;
      clearOwner();
      if (previousOwner) {
        try {
          const prefix = `flashTicket.assistant:${previousOwner}:draft:`;
          for (const key of Object.keys(localStorage)) if (key.startsWith(prefix)) localStorage.removeItem(key);
        } catch {
          // Storage bị chặn không được ngăn xóa state trong RAM và che owner cũ.
        }
      }
    }
  }, [owner, allowed, clearOwner]);
  useEffect(() => () => {
    generation.current++;
    controller.current?.abort();
    current.current = { ...current.current, busy: false };
  }, []);

  const isCurrent = useCallback((conversationId: string, requestedOwner: string, requestedGeneration?: number) =>
    renderedAllowed.current && assistantOwnerScope() === requestedOwner && renderedOwner.current === requestedOwner
      && current.current.owner === requestedOwner && current.current.conversation?.conversationId === conversationId
      && (requestedGeneration === undefined || generation.current === requestedGeneration), []);

  const open = useCallback(async () => {
    const requestedOwner = assistantOwnerScope();
    if (!allowed || !requestedOwner || (current.current.owner === requestedOwner && current.current.busy)) return;
    if (current.current.owner !== requestedOwner) {
      generation.current++;
      controller.current?.abort(); pending.current = null;
      current.current = { owner: requestedOwner, conversation: null, busy: false, error: "", notice: "" };
    }
    const capturedGeneration = generation.current;
    const abort = new AbortController(); controller.current = abort;
    current.current = { ...current.current, owner: requestedOwner, busy: true, error: "" };
    setState(current.current);
    try {
      const conversation = await chatService.openConversation(abort.signal);
      if (abort.signal.aborted || assistantOwnerScope() !== requestedOwner || generation.current !== capturedGeneration) return;
      if (pending.current && (conversation.lastRequestId === pending.current.request.requestId
          || conversation.version !== pending.current.request.version)) pending.current = null;
      current.current = { owner: requestedOwner, conversation, busy: false, error: "",
        notice: conversation.notice === "NEW_CONVERSATION" ? "Đã mở hội thoại mới." : "" };
      setState(current.current);
    } catch {
      if (!abort.signal.aborted && assistantOwnerScope() === requestedOwner && generation.current === capturedGeneration) {
        current.current = { ...current.current, owner: requestedOwner, busy: false, error: "Không mở được hội thoại. Vui lòng thử lại." };
        setState(current.current);
      }
    }
  }, [allowed]);

  const send = useCallback(async (message: string) => {
    const requestedOwner = assistantOwnerScope();
    const stored = current.current;
    const text = message.trim();
    if (!allowed || !requestedOwner || stored.owner !== requestedOwner || !stored.conversation || stored.busy || !text) return;
    if (text.length > 2000) return;
    if (pending.current && pending.current.request.message !== text) {
      current.current = { ...stored, error: "Lượt trước chưa rõ kết quả. Hãy thử lại cùng nội dung hoặc đọc lại hội thoại." };
      setState(current.current); return;
    }
    const capturedGeneration = generation.current;
    const conversationId = stored.conversation.conversationId;
    const request: ChatRequest = pending.current?.request || {
      conversationId, version: stored.conversation.version, requestId: crypto.randomUUID(), message: text,
    };
    pending.current = { owner: requestedOwner, request };
    const abort = new AbortController(); controller.current = abort;
    current.current = { ...stored, busy: true, error: "", notice: "" }; setState(current.current);
    try {
      await chatService.sendMessage(request, abort.signal);
      if (!isCurrent(conversationId, requestedOwner, capturedGeneration) || abort.signal.aborted) return;
      const conversation = await chatService.getConversation(abort.signal);
      if (!isCurrent(conversationId, requestedOwner, capturedGeneration) || abort.signal.aborted) return;
      pending.current = null;
      current.current = { owner: requestedOwner, conversation, busy: false, error: "", notice: "" }; setState(current.current);
    } catch (error: unknown) {
      if (!isCurrent(conversationId, requestedOwner, capturedGeneration) || abort.signal.aborted) return;
      let conversation: ConversationView | null = stored.conversation;
      let notice = "";
      let failure = isAxiosError(error) && error.response?.status === 429
        ? "Bạn đã đạt giới hạn yêu cầu. Vui lòng đợi một phút."
        : "Chưa xác nhận được kết quả. Bạn có thể thử lại cùng nội dung; không tự gửi lại lượt cũ.";
      try {
        conversation = await chatService.getConversation(abort.signal);
        if (!isCurrent(conversationId, requestedOwner, capturedGeneration) || abort.signal.aborted) return;
        if (conversation.lastRequestId === request.requestId) { pending.current = null; failure = ""; }
        else if (conversation.version !== request.version || conversation.conversationId !== conversationId) {
          pending.current = null; notice = "Hội thoại đã thay đổi ở nơi khác. Đã tải trạng thái mới.";
        }
      } catch (readError: unknown) {
        if (!isCurrent(conversationId, requestedOwner, capturedGeneration) || abort.signal.aborted) return;
        if (isAxiosError(readError) && readError.response?.status === 410) {
          conversation = null; pending.current = null;
          notice = "Hội thoại đã hết hạn. Hãy mở hội thoại mới; tin nhắn cũ sẽ không được gửi lại.";
        }
      }
      current.current = { owner: requestedOwner, conversation, busy: false, error: failure, notice }; setState(current.current);
    }
  }, [allowed, isCurrent]);

  const reset = useCallback(async () => {
    const requestedOwner = assistantOwnerScope();
    const stored = current.current;
    if (!allowed || !requestedOwner || stored.owner !== requestedOwner || !stored.conversation) return;
    const originalGeneration = generation.current;
    const confirmed = await confirmDestructiveAction({ title: "Bắt đầu hội thoại mới?",
      text: "Ngữ cảnh, lịch sử và form của phiên hiện tại sẽ bị xóa. Đơn đã tạo không bị hủy.", confirmButtonText: "Xóa hội thoại" });
    if (!confirmed || !isCurrent(stored.conversation.conversationId, requestedOwner, originalGeneration)) return;
    generation.current++;
    const capturedGeneration = generation.current;
    controller.current?.abort(); pending.current = null;
    const abort = new AbortController(); controller.current = abort;
    current.current = { owner: requestedOwner, conversation: null, busy: true, error: "", notice: "Đang đặt lại hội thoại…" };
    setState(current.current);
    try {
      const conversation = await chatService.resetConversation({ conversationId: stored.conversation.conversationId,
        version: stored.conversation.version }, abort.signal);
      if (abort.signal.aborted || assistantOwnerScope() !== requestedOwner || generation.current !== capturedGeneration) return;
      try {
        const prefix = `flashTicket.assistant:${requestedOwner}:draft:`;
        for (const key of Object.keys(localStorage)) if (key.startsWith(prefix)) localStorage.removeItem(key);
      } catch {
        // Reset đã thành công trên server; lỗi storage không đổi kết quả đó.
      }
      current.current = { owner: requestedOwner, conversation, busy: false, error: "", notice: "Đã bắt đầu hội thoại mới." };
    } catch {
      if (abort.signal.aborted || assistantOwnerScope() !== requestedOwner || generation.current !== capturedGeneration) return;
      try {
        const conversation = await chatService.getConversation(abort.signal);
        if (abort.signal.aborted || assistantOwnerScope() !== requestedOwner || generation.current !== capturedGeneration) return;
        current.current = { owner: requestedOwner, conversation, busy: false, error: "Chưa xác nhận được reset. Đã đọc lại trạng thái hiện hành.", notice: "" };
      } catch {
        if (abort.signal.aborted || assistantOwnerScope() !== requestedOwner || generation.current !== capturedGeneration) return;
        current.current = { owner: requestedOwner, conversation: null, busy: false, error: "Không đặt lại được hội thoại. Vui lòng đọc lại trước khi tiếp tục.", notice: "" };
      }
    }
    setState(current.current);
  }, [allowed, isCurrent]);

  // Che dữ liệu ngay trong render khi đổi account; không đợi effect cleanup.
  const visible = allowed && state.owner === owner ? state : null;
  return <AssistantContext.Provider value={{ conversation: visible?.conversation || null, ownerScope: owner,
    generation: generation.current, busy: visible?.busy || false, error: visible?.error || "", notice: visible?.notice || "",
    allowed, open, send, reset, clearOwner, isCurrent }}>{children}</AssistantContext.Provider>;
}

export function useAssistantConversation(): AssistantConversationContextValue {
  const value = useContext(AssistantContext);
  if (!value) throw new Error("AssistantConversationProvider is required");
  return value;
}
