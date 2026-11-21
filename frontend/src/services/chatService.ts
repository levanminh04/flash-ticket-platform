import { isAxiosError } from "axios";
import axiosClient from "../lib/axiosClient";
import keycloak from "../lib/keycloak";
import { toolProgressOperations } from "../types/assistantContract";
import type { ChatRequest, ConversationView, ResetConversationRequest, ToolProgressOperation } from "../types/assistantContract";

export type AssistantWorkspace = "buyer" | "organizer" | "admin";

export function assistantOwnerScope(workspace: AssistantWorkspace = "buyer"): string | null {
  if (!keycloak.authenticated || !keycloak.tokenParsed?.iss || !keycloak.tokenParsed?.sub) return null;
  const scope = [import.meta.env.VITE_API_GATEWAY_URL || "", keycloak.tokenParsed.iss, keycloak.tokenParsed.sub];
  if (workspace !== "buyer") scope.push(workspace);
  return JSON.stringify(scope);
}

function requestConfig(signal?: AbortSignal) {
  if (!assistantOwnerScope()) throw new Error("UNAUTHENTICATED");
  return {
    signal,
    timeout: 60000,
    expectedAuthOwner: { issuer: keycloak.tokenParsed!.iss!, subject: keycloak.tokenParsed!.sub! },
  };
}

const roots: Record<AssistantWorkspace, string> = {
  buyer: "/api/v1/assistant",
  organizer: "/api/v1/assistant/organizer",
  admin: "/api/v1/assistant/admin",
};
const uuidPattern = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;

async function readTurnStream(body: ReadableStream<Uint8Array>, request: ChatRequest, workspace: AssistantWorkspace,
  onProcessing: () => void, onToolProgress: (operation: ToolProgressOperation) => void): Promise<string | undefined> {
  const decoder = new TextDecoder("utf-8", { fatal: true });
  const reader = body.getReader();
  const allowedOperations: readonly ToolProgressOperation[] = toolProgressOperations[workspace];
  let buffer = "";
  let skipLeadingLf = false;
  let event = "";
  let data: string[] = [];
  let processing = false;
  let progressCount = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) throw new Error("ASSISTANT_STREAM_INCOMPLETE");
      const chunk = decoder.decode(value, { stream: true });
      if (!chunk) continue;
      // CR có thể đứng cuối chunk, LF của cùng dấu xuống dòng đến ở chunk sau.
      buffer += skipLeadingLf && chunk.startsWith("\n") ? chunk.slice(1) : chunk;
      skipLeadingLf = false;
      const lineBreak = /\r\n|[\r\n]/g;
      let offset = 0;
      for (let match = lineBreak.exec(buffer); match; match = lineBreak.exec(buffer)) {
        const line = buffer.slice(offset, match.index);
        offset = lineBreak.lastIndex;
        skipLeadingLf = match[0] === "\r" && offset === buffer.length;
        if (line) {
          const colon = line.indexOf(":");
          const field = colon < 0 ? line : line.slice(0, colon);
          const value = colon < 0 ? "" : line.slice(colon + 1).replace(/^ /, "");
          if (field === "event") event = value;
          if (field === "data") data.push(value);
          continue;
        }
        if (data.length) {
          const payload: { requestId?: unknown; conversationId?: unknown; version?: unknown; code?: unknown; operation?: unknown } | null = JSON.parse(data.join("\n"));
          if (!payload || payload.requestId !== request.requestId) throw new Error("ASSISTANT_STREAM_INVALID");
          if (event === "processing" && !processing) {
            processing = true;
            onProcessing();
          } else if (event === "tool_progress" && processing && Object.keys(payload).length === 2
              && typeof payload.operation === "string" && ++progressCount <= 6) {
            const operation = allowedOperations.find(value => value === payload.operation);
            if (!operation) throw new Error("ASSISTANT_STREAM_INVALID");
            onToolProgress(operation);
          } else if (event === "result" && processing && payload.conversationId === request.conversationId
              && typeof payload.version === "string" && uuidPattern.test(payload.version)) {
            return;
          } else if (event === "turn_error" && processing && typeof payload.code === "string" && payload.code.trim()) {
            return payload.code;
          } else {
            throw new Error("ASSISTANT_STREAM_INVALID");
          }
        }
        event = "";
        data = [];
      }
      buffer = buffer.slice(offset);
    }
  } finally {
    void reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}

export const chatService = {
  openConversation: async (signal?: AbortSignal, workspace: AssistantWorkspace = "buyer"): Promise<ConversationView> =>
    (await axiosClient.post<ConversationView>(`${roots[workspace]}/conversations/current`, undefined, requestConfig(signal))).data,
  getConversation: async (signal?: AbortSignal, workspace: AssistantWorkspace = "buyer"): Promise<ConversationView> =>
    (await axiosClient.get<ConversationView>(`${roots[workspace]}/conversations/current`, requestConfig(signal))).data,
  streamMessage: async (payload: ChatRequest, signal: AbortSignal, isRequestContextCurrent: () => boolean,
    onProcessing: () => void, onToolProgress: (operation: ToolProgressOperation) => void,
    workspace: AssistantWorkspace): Promise<string | undefined> => {
    if (typeof ReadableStream === "undefined" || typeof TextDecoder === "undefined") throw new Error("ASSISTANT_STREAM_UNAVAILABLE");
    const response = await axiosClient.post<ReadableStream<Uint8Array>>(`${roots[workspace]}/messages/stream?progress=tool-v1`, payload, {
      ...requestConfig(signal), isRequestContextCurrent, adapter: "fetch", responseType: "stream",
      fetchOptions: { redirect: "error" }, maxContentLength: 8192,
      headers: { Accept: "text/event-stream, application/problem+json" },
    }).catch((error: unknown) => {
      if (isAxiosError<ReadableStream<Uint8Array>>(error)) void error.response?.data?.cancel?.().catch(() => {});
      throw error;
    });
    if (response.status !== 200 || !/^text\/event-stream(?:\s*;|$)/i.test(String(response.headers["content-type"])) || !response.data?.getReader) {
      void response.data?.cancel?.().catch(() => {});
      throw new Error("ASSISTANT_STREAM_INVALID");
    }
    return readTurnStream(response.data, payload, workspace, onProcessing, onToolProgress);
  },
  resetConversation: async (payload: ResetConversationRequest, signal?: AbortSignal,
    workspace: AssistantWorkspace = "buyer"): Promise<ConversationView> =>
    (await axiosClient.post<ConversationView>(`${roots[workspace]}/conversations/reset`, payload, requestConfig(signal))).data,
};
