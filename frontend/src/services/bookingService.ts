import axiosClient from "../lib/axiosClient";
import keycloak from "../lib/keycloak";
import { Availability, CreateOrderRequest, OrderSelection, OrderView } from "../types/bookingContract";

function buyerSessionScope() {
  if (!keycloak.authenticated || !keycloak.tokenParsed?.sub) throw new Error("UNAUTHENTICATED");
  return `flashTicket.booking:${import.meta.env.VITE_API_GATEWAY_URL}:${keycloak.tokenParsed.iss}:${keycloak.tokenParsed.sub}`;
}

function rememberOrder(order: OrderView, scope: string) {
  sessionStorage.setItem(`${scope}:order:${order.orderId}`, JSON.stringify(order));
}

export const bookingService = {
  getAvailability: async (eventId: string): Promise<Availability> => {
    const response = await axiosClient.get<Availability>(`/api/v1/events/${eventId}/availability`);
    return response.data;
  },

  createOrder: async (input: CreateOrderRequest): Promise<OrderView> => {
    const scope = buyerSessionScope();
    const selected = input.selection;
    const selection: OrderSelection = selected.mode === "SEAT_MAP" && selected.kind === "SEATED"
      ? { mode: "SEAT_MAP", kind: "SEATED", sectorId: selected.sectorId, seatIds: [...selected.seatIds].sort() }
      : {
          ...(selected.mode === "QUANTITY" ? { mode: "QUANTITY" as const } : { mode: "SEAT_MAP" as const, kind: "STANDING" as const, sectorId: selected.sectorId }),
          items: selected.items.map(item => ({ ticketTypeId: item.ticketTypeId, quantity: item.quantity })).sort((a, b) => a.ticketTypeId.localeCompare(b.ticketTypeId)),
        };
    const request = { eventId: input.eventId, customerEmail: input.customerEmail.trim(), selection };
    const body = JSON.stringify(request);
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(body));
    const fingerprint = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
    const pendingKey = `${scope}:pending:${fingerprint}`;
    const stored = sessionStorage.getItem(pendingKey);
    const pending: { body: string; key: string } | null = stored ? JSON.parse(stored) : null;
    const key = pending?.body === body ? pending.key : crypto.randomUUID();
    // Ghi trước khi gửi; timeout/5xx/reload phải thử lại cùng key và cùng request.
    sessionStorage.setItem(pendingKey, JSON.stringify({ body, key }));
    const response = await axiosClient.post<OrderView>("/api/v1/orders", request, { headers: { "Idempotency-Key": key } });
    rememberOrder(response.data, scope);
    if (sessionStorage.getItem(pendingKey) === JSON.stringify({ body, key })) sessionStorage.removeItem(pendingKey);
    return response.data;
  },

  getCachedOrder: (orderId: string): OrderView | null => {
    if (!keycloak.authenticated || !keycloak.tokenParsed?.sub) return null;
    try {
      const stored = sessionStorage.getItem(`${buyerSessionScope()}:order:${orderId}`);
      const order: OrderView | null = stored ? JSON.parse(stored) : null;
      return order?.orderId === orderId ? order : null;
    } catch {
      return null;
    }
  },

  applyPromotion: async (orderId: string, code: string): Promise<OrderView> => {
    const scope = buyerSessionScope();
    const response = await axiosClient.post<OrderView>(`/api/v1/orders/${orderId}/promotion`, { code: code.trim() });
    rememberOrder(response.data, scope);
    return response.data;
  },

  cancelOrder: async (orderId: string): Promise<OrderView> => {
    const scope = buyerSessionScope();
    const response = await axiosClient.post<OrderView>(`/api/v1/orders/${orderId}/cancel`, {});
    rememberOrder(response.data, scope);
    return response.data;
  },
};
