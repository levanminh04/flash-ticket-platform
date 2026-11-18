import axiosClient from "../lib/axiosClient";
import keycloak from "../lib/keycloak";
import type { ChatRequest, ChatResponse, ConversationView, ResetConversationRequest } from "../types/assistantContract";

export function assistantOwnerScope(): string | null {
  if (!keycloak.authenticated || !keycloak.tokenParsed?.iss || !keycloak.tokenParsed?.sub) return null;
  return JSON.stringify([import.meta.env.VITE_API_GATEWAY_URL || "", keycloak.tokenParsed.iss, keycloak.tokenParsed.sub]);
}

function requestConfig(signal?: AbortSignal) {
  if (!assistantOwnerScope()) throw new Error("UNAUTHENTICATED");
  return {
    signal,
    timeout: 60000,
    expectedAuthOwner: { issuer: keycloak.tokenParsed!.iss!, subject: keycloak.tokenParsed!.sub! },
  };
}

const root = "/api/v1/assistant";
export const chatService = {
  openConversation: async (signal?: AbortSignal): Promise<ConversationView> =>
    (await axiosClient.post<ConversationView>(`${root}/conversations/current`, undefined, requestConfig(signal))).data,
  getConversation: async (signal?: AbortSignal): Promise<ConversationView> =>
    (await axiosClient.get<ConversationView>(`${root}/conversations/current`, requestConfig(signal))).data,
  sendMessage: async (payload: ChatRequest, signal?: AbortSignal): Promise<ChatResponse> =>
    (await axiosClient.post<ChatResponse>(`${root}/messages`, payload, requestConfig(signal))).data,
  resetConversation: async (payload: ResetConversationRequest, signal?: AbortSignal): Promise<ConversationView> =>
    (await axiosClient.post<ConversationView>(`${root}/conversations/reset`, payload, requestConfig(signal))).data,
};
