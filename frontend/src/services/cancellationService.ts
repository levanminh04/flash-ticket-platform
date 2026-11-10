import axiosClient from "../lib/axiosClient";

export interface CancellationRequest {
  requestId: string;
  eventId: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  requestedBy: string;
  requestedAt: string;
  reason: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewReason: string | null;
  eventRowVersion: string;
}

export interface PageResponse<T> {
  items: T[];
  nextCursor: string | null;
}

export const cancellationService = {
  // BTC (Organizer) gửi yêu cầu hủy sự kiện đã mở bán vé
  requestCancellation: async (
    eventId: string,
    rowVersion: string,
    reason: string,
  ): Promise<CancellationRequest> => {
    const response = await axiosClient.post<CancellationRequest>(
      `/api/v1/organizer/events/${encodeURIComponent(eventId)}/cancellation-requests`,
      { rowVersion, reason },
    );
    return response.data;
  },

  // BTC xem lịch sử các yêu cầu hủy của sự kiện
  getOrganizerCancellationHistory: async (
    eventId: string,
    size = 20,
    cursor?: string,
  ): Promise<PageResponse<CancellationRequest>> => {
    const response = await axiosClient.get<PageResponse<CancellationRequest>>(
      `/api/v1/organizer/events/${encodeURIComponent(eventId)}/cancellation-requests`,
      { params: { size, cursor } },
    );
    return response.data;
  },

  // Admin xem danh sách các yêu cầu hủy sự kiện chờ duyệt
  getAdminPendingCancellationRequests: async (
    status = "PENDING",
    size = 20,
    cursor?: string,
    signal?: AbortSignal,
  ): Promise<PageResponse<CancellationRequest>> => {
    const response = await axiosClient.get<PageResponse<CancellationRequest>>(
      "/api/v1/admin/cancellation-requests",
      {
        params: { status, size, cursor },
        signal,
      },
    );
    return response.data;
  },

  // Admin duyệt yêu cầu hủy sự kiện (kích hoạt quy trình hủy và hoàn tiền toàn bộ vé)
  approveCancellationRequest: async (
    requestId: string,
    rowVersion: string,
  ): Promise<CancellationRequest> => {
    const response = await axiosClient.post<CancellationRequest>(
      `/api/v1/admin/cancellation-requests/${encodeURIComponent(requestId)}/approve`,
      { rowVersion },
    );
    return response.data;
  },

  // Admin từ chối yêu cầu hủy sự kiện
  rejectCancellationRequest: async (
    requestId: string,
    rowVersion: string,
    reason?: string,
  ): Promise<CancellationRequest> => {
    const response = await axiosClient.post<CancellationRequest>(
      `/api/v1/admin/cancellation-requests/${encodeURIComponent(requestId)}/reject`,
      { rowVersion, reason },
    );
    return response.data;
  },
};
