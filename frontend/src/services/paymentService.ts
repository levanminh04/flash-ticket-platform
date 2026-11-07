import axiosClient from "../lib/axiosClient";
import keycloak from "../lib/keycloak";

export interface PaymentStart {
  orderId: string;
  attemptId: string;
  attemptState: "PENDING" | "UNKNOWN" | "SUCCEEDED" | "FAILED";
  amount: string;
  currency: "VND";
  paymentUrl: string | null;
  expiresAt: string;
}

export interface PaymentView {
  orderId: string;
  acceptedChargeId: string | null;
  latestAttempt: PaymentStart | null;
  startRequest: { requestId: string; state: "WAITING" | "CREATED" | "REJECTED"; failureCode: string | null } | null;
  refunds: { refundId: string; chargeId: string; amount: string; state: "PENDING" | "PROCESSING" | "SUCCEEDED" | "FAILED"; reason: string }[];
}

interface PaymentResponse<T> { success: boolean; message?: string; data: T; }

function unwrap<T>(response: PaymentResponse<T>): T {
  if (!response.success) throw { response: { data: response } };
  return response.data;
}

function paymentKeyPath(orderId: string) {
  if (!keycloak.authenticated || !keycloak.tokenParsed?.sub) throw new Error("UNAUTHENTICATED");
  return `flashTicket.payment:${import.meta.env.VITE_API_GATEWAY_URL}:${keycloak.tokenParsed.iss}:${keycloak.tokenParsed.sub}:${orderId.toLowerCase()}`;
}

export function paymentProblem(error: unknown): { code?: string; message?: string } {
  const body = (error as { response?: { data?: { data?: { code?: string; message?: string }; code?: string; message?: string } } })?.response?.data;
  return body?.data || body || {};
}

export function vnPayCheckoutUrl(attempt: PaymentStart): string | null {
  if (attempt.attemptState !== "PENDING" || !attempt.paymentUrl || !Number.isFinite(Date.parse(attempt.expiresAt)) || Date.parse(attempt.expiresAt) <= Date.now()) return null;
  try {
    const url = new URL(attempt.paymentUrl);
    return url.protocol === "https:" && !url.username && !url.password && !url.port && ["sandbox.vnpayment.vn", "payment.vnpay.vn"].includes(url.hostname) ? url.href : null;
  } catch { return null; }
}

export const paymentService = {
  hasPaymentRequest: (orderId: string) => Boolean(keycloak.authenticated && keycloak.tokenParsed?.sub && sessionStorage.getItem(paymentKeyPath(orderId))),

  initiatePayment: async (orderId: string, newAttempt = false): Promise<PaymentStart> => {
    const path = paymentKeyPath(orderId);
    const stored = sessionStorage.getItem(path);
    const pending: { key: string; retryOf: string | null } | null = stored ? (stored.startsWith("{") ? JSON.parse(stored) : { key: stored, retryOf: null }) : null;
    let key = pending?.key || crypto.randomUUID();
    let retryOf = pending?.retryOf || null;
    if (newAttempt) {
      // Chỉ đổi key sau khi server xác nhận attempt cũ FAILED; UNKNOWN không được thu lại.
      const current = await paymentService.getPaymentStatus(orderId);
      if (paymentKeyPath(orderId) !== path) throw new Error("UNAUTHENTICATED");
      if (current.acceptedChargeId || current.latestAttempt?.attemptState !== "FAILED") throw new Error("PAYMENT_IN_PROGRESS");
      if (retryOf !== current.latestAttempt.attemptId) { key = crypto.randomUUID(); retryOf = current.latestAttempt.attemptId; }
    }
    sessionStorage.setItem(path, JSON.stringify({ key, retryOf }));
    const response = await axiosClient.post<PaymentResponse<PaymentStart>>("/api/v1/payments", { orderId }, { headers: { "Idempotency-Key": key }, timeout: 15000 });
    const attempt = unwrap(response.data);
    if (paymentKeyPath(orderId) !== path) throw new Error("UNAUTHENTICATED");
    if (attempt.orderId.toLowerCase() !== orderId.toLowerCase()) throw new Error("PAYMENT_SCOPE_MISMATCH");
    return attempt;
  },

  getPaymentStatus: async (orderId: string, signal?: AbortSignal): Promise<PaymentView> => {
    // requestId của API tra inbox Rabbit, không phải Idempotency-Key của HTTP create.
    const response = await axiosClient.get<PaymentResponse<PaymentView>>(`/api/v1/orders/${orderId}/payment`, { signal, timeout: 10000 });
    const view = unwrap(response.data);
    if (view.orderId.toLowerCase() !== orderId.toLowerCase() || (view.latestAttempt && view.latestAttempt.orderId.toLowerCase() !== orderId.toLowerCase())) throw new Error("PAYMENT_SCOPE_MISMATCH");
    return view;
  },
};
