export interface QuantityLine { ticketTypeId: string; quantity: number }
export interface AssistantEvent {
  eventId: string;
  title: string;
  description?: string | null;
  venueName?: string | null;
  mode: string;
  startsAt?: string | null;
  endsAt?: string | null;
  ticketTypes: { ticketTypeId: string; name: string; unitPrice: string; currency: string }[];
}
export interface PurchaseDraft {
  draftId: string;
  eventId: string;
  eventTitle: string;
  estimatedTotal: string;
  currency: string;
  items: QuantityLine[];
  ticketTypes: AssistantEvent["ticketTypes"];
  availabilityAsOf: string;
  expiresAt: string;
}
export interface ChatRequest {
  conversationId: string;
  version: string;
  requestId: string;
  message: string;
}
export interface ChatResponse {
  conversationId: string;
  version: string;
  requestId: string;
  message: string;
  draft?: PurchaseDraft | null;
  events: AssistantEvent[];
}
export interface TranscriptMessage {
  id: string;
  role: "user" | "assistant";
  text: string;
  createdAt: string;
  draft?: PurchaseDraft | null;
  events?: AssistantEvent[];
}
export interface ConversationView {
  conversationId: string;
  version: string;
  lastRequestId?: string | null;
  expiresAt: string;
  notice?: string | null;
  messages: TranscriptMessage[];
}
export interface ResetConversationRequest { conversationId: string; version: string }
