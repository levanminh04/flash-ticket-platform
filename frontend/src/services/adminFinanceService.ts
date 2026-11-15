import { isAxiosError } from "axios";
import axiosClient from "../lib/axiosClient";

export interface EventReconciliationView {
  eventId: string;
  organizerSubject: string;
  currency: string;
  validGross: string;
  validRefunds: string;
  feeAmount: string;
  netAmount: string;
  feeRate: string;
  sourceFeeVersion: number;
  eventEndsAt: string;
  cancelled: boolean;
  paymentPendingCount: number;
  refundPendingCount: number;
  eligibleForReconciliation: boolean;
  reconciledBy: string | null;
  reconciledAt: string | null;
  reconciliationEvidence: string | null;
  paidBy: string | null;
  paidAt: string | null;
  asOf: string;
}

interface PaymentViolation {
  field: string;
  code: string;
}

interface PaymentProblem {
  code?: string;
  message?: string;
  correlationId?: string;
  violations?: PaymentViolation[];
}

interface ApiResponse<T> {
  data: T;
  success: boolean;
  message: string;
}

export interface ParsedPaymentError {
  status?: number;
  code?: string;
  message: string;
  correlationId?: string;
}

const PAYMENT_ERROR_MESSAGES_VI: Record<string, string> = {
  FINANCE_NOT_CONFIGURED:
    "Sự kiện chưa được cấu hình thông số tài chính (thường do chưa duyệt hoặc chưa đồng bộ snapshot tài chính).",
  RESOURCE_NOT_FOUND:
    "Không tìm thấy dữ liệu đối soát tài chính cho sự kiện này (sự kiện chưa được cấu hình phí hoặc chưa tồn tại trên Payment Service).",
  EVENT_CANCELLED: "Sự kiện đã bị hủy nên không thể thực hiện đối soát hoặc chi trả.",
  EVENT_NOT_ENDED: "Sự kiện chưa kết thúc theo thời điểm kết thúc đã cấu hình.",
  PAYMENT_PENDING: "Vẫn còn giao dịch thanh toán đang chờ hoàn tất. Không thể đối soát lúc này.",
  REFUND_PENDING: "Vẫn còn khoản hoàn tiền đang chờ xử lý. Hãy hoàn tất hoàn tiền trước khi đối soát.",
  RECONCILIATION_REQUIRED: "Cần xác nhận đối soát trước khi đánh dấu đã chi trả.",
  RECONCILIATION_STALE:
    "Sổ cái thanh toán hoặc hoàn tiền đã thay đổi sau lần đối soát trước. Vui lòng kiểm tra lại số liệu mới nhất và xác nhận lại đối soát.",
  PAYOUT_ALREADY_PAID: "Sự kiện này đã được đánh dấu chi trả trước đó.",
  INVALID_REQUEST: "Dữ liệu gửi lên không hợp lệ. Vui lòng kiểm tra lại thông tin.",
  ACCESS_DENIED: "Bạn không có quyền Quản trị viên (ADMIN) để thực hiện thao tác này.",
};

export function extractPaymentError(error: unknown, isVi = true): ParsedPaymentError {
  if (isAxiosError<ApiResponse<PaymentProblem> | PaymentProblem>(error)) {
    const status = error.response?.status;
    const raw = error.response?.data;
    const problem: PaymentProblem | undefined =
      raw && typeof raw === "object" && "data" in raw && raw.data && typeof raw.data === "object"
        ? (raw.data as PaymentProblem)
        : (raw as PaymentProblem | undefined);

    const code = problem?.code;
    const correlationId = problem?.correlationId;
    const fallbackMessage =
      (raw && typeof raw === "object" && "message" in raw && typeof raw.message === "string"
        ? raw.message
        : undefined) ||
      problem?.message ||
      error.message;

    if (isVi && code && PAYMENT_ERROR_MESSAGES_VI[code]) {
      return {
        status,
        code,
        message: PAYMENT_ERROR_MESSAGES_VI[code],
        correlationId,
      };
    }

    return {
      status,
      code,
      message:
        fallbackMessage ||
        (isVi ? "Không thể xử lý yêu cầu đối soát." : "Failed to process reconciliation request."),
      correlationId,
    };
  }

  return {
    message: isVi ? "Đã xảy ra lỗi không xác định." : "An unexpected error occurred.",
  };
}

export const adminFinanceService = {
  getReconciliation: async (
    eventId: string,
    signal?: AbortSignal,
  ): Promise<EventReconciliationView> => {
    const response = await axiosClient.get<ApiResponse<EventReconciliationView>>(
      `/api/v1/events/${eventId}/reconciliation`,
      { signal },
    );
    return response.data.data;
  },

  confirmReconciliation: async (
    eventId: string,
    evidence: string,
  ): Promise<EventReconciliationView> => {
    const response = await axiosClient.post<ApiResponse<EventReconciliationView>>(
      `/api/v1/admin/events/${eventId}/reconciliation`,
      { evidence },
    );
    return response.data.data;
  },

  markEventPaid: async (eventId: string): Promise<EventReconciliationView> => {
    const response = await axiosClient.post<ApiResponse<EventReconciliationView>>(
      `/api/v1/admin/events/${eventId}/payout`,
    );
    return response.data.data;
  },
};
