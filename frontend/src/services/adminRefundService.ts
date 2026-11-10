import axiosClient from "../lib/axiosClient";

export type RefundState = "FAILED" | "PENDING" | "PROCESSING" | "SUCCEEDED";

export type RefundReason =
  | "EVENT_CANCELLED"
  | "LATE_PAYMENT"
  | "TICKET_ISSUANCE_FAILED"
  | "DUPLICATE_PAYMENT"
  | "BUYER_REQUESTED";

export interface RefundAdminView {
  refundId: string;
  chargeId: string;
  orderId: string;
  amount: string;
  state: RefundState;
  reason: RefundReason;
  settledBy: string | null;
  externalReference: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface EventRefundProgressView {
  eventId: string;
  sourceCancellationId: string;
  selectionComplete: boolean;
  selectedOrderCount: number;
  pendingCount: number;
  refundedCount: number;
  needsAttentionCount: number;
  completedAt: string | null;
  checkedAt: string;
}

interface ApiResponse<T> {
  data: T;
  success: boolean;
  message: string;
}

export const adminRefundService = {
  getRefunds: async (
    state: RefundState = "FAILED",
    page = 0,
    size = 20,
    signal?: AbortSignal,
  ): Promise<PageResponse<RefundAdminView>> => {
    const response = await axiosClient.get<ApiResponse<PageResponse<RefundAdminView>>>(
      "/api/v1/admin/refunds",
      {
        params: { state, page, size },
        signal,
      },
    );
    return response.data.data;
  },

  retryRefund: async (refundId: string): Promise<RefundAdminView> => {
    const response = await axiosClient.post<ApiResponse<RefundAdminView>>(
      `/api/v1/admin/refunds/${refundId}/retry`,
    );
    return response.data.data;
  },

  settleRefundManually: async (
    refundId: string,
    externalReference: string,
  ): Promise<RefundAdminView> => {
    const response = await axiosClient.post<ApiResponse<RefundAdminView>>(
      `/api/v1/admin/refunds/${refundId}/manual-settlement`,
      { externalReference },
    );
    return response.data.data;
  },

  getEventRefundProgress: async (
    eventId: string,
    signal?: AbortSignal,
  ): Promise<EventRefundProgressView> => {
    const response = await axiosClient.get<ApiResponse<EventRefundProgressView>>(
      `/api/v1/admin/events/${eventId}/refund-progress`,
      { signal },
    );
    return response.data.data;
  },
};
