import axiosClient from "../lib/axiosClient";

// Ticket API dev/tuyen: không có endpoint list toàn bộ vé theo buyer.
export interface TicketView {
  ticketId: string;
  eventId: string;
  orderItemId: string;
  ordinal: number;
  ticketTypeName: string;
  seatLabel: string | null;
  state: "VALID" | "USED" | "VOID";
}

export interface OrderTicketsView {
  issuanceState: "PENDING" | "COMPLETED" | "FAILED";
  tickets: TicketView[];
}

export const ticketService = {
  getOrderTickets: async (orderId: string, signal?: AbortSignal): Promise<OrderTicketsView> => {
    const response = await axiosClient.get<OrderTicketsView>(`/api/v1/orders/${orderId}/tickets`, { signal, timeout: 10000 });
    return response.data;
  },
  getQrImage: async (ticketId: string, signal?: AbortSignal): Promise<Blob> => {
    const response = await axiosClient.get<Blob>(`/api/v1/tickets/${ticketId}/qr`, { headers: { Accept: "image/png" }, responseType: "blob", signal, timeout: 10000 });
    if (response.data.type !== "image/png") throw new Error("QR_UNAVAILABLE");
    return response.data;
  },
};
