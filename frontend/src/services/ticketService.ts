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

export type CheckInResultCode =
  | "SUCCESS"
  | "NOT_EVENT_OWNER"
  | "EVENT_CANCELLED"
  | "OUTSIDE_WINDOW"
  | "UNKNOWN_QR"
  | "WRONG_EVENT"
  | "NOT_ISSUED"
  | "VOID"
  | "ALREADY_USED";

export interface CheckInResult {
  attemptId: string;
  eventId: string;
  ticketId: string | null;
  result: CheckInResultCode;
  succeeded: boolean;
  processedAt: string;
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
  getQrToken: async (ticketId: string, signal?: AbortSignal): Promise<{ qrToken: string }> => {
    const response = await axiosClient.get<{ qrToken: string }>(`/api/v1/tickets/${ticketId}/qr`, {
      headers: { Accept: "application/json" },
      signal,
      timeout: 10000,
    });
    return response.data;
  },
  checkIn: async (
    eventId: string,
    qrToken: string,
    idempotencyKey?: string,
    signal?: AbortSignal,
  ): Promise<CheckInResult> => {
    const key = idempotencyKey || crypto.randomUUID();
    const response = await axiosClient.post<CheckInResult>(
      `/api/v1/events/${eventId}/check-ins`,
      { qrToken },
      {
        headers: {
          "Idempotency-Key": key,
        },
        signal,
        timeout: 10000,
      },
    );
    return response.data;
  },
};
