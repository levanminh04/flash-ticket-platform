package com.flashticket.payment_service.exception;

import org.springframework.http.HttpStatus;

/** Stable public error codes for Payment use cases and HTTP boundaries. */
public enum PaymentErrorCode {
  PAYMENT_IN_PROGRESS(HttpStatus.CONFLICT, "A payment attempt is still in progress."),
  IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "The payment request key has different input."),
  PURCHASE_SNAPSHOT_CONFLICT(HttpStatus.CONFLICT, "The stored purchase snapshot differs."),
  BOOKING_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Booking is temporarily unavailable."),
  PAYMENT_NOT_READY(HttpStatus.NOT_FOUND, "Payment is not ready."),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource was not found."),
  AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "A valid bearer token is required."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied."),
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Request parameters are invalid."),
  PAYMENT_EXPIRED(HttpStatus.CONFLICT, "The order payment window has expired."),
  PAYMENT_STATE_INVALID(HttpStatus.CONFLICT, "Payment cannot be started in its current state."),
  PAYMENT_INVALID_SIGNATURE(HttpStatus.BAD_REQUEST, "The payment signature is invalid."),
  PAYMENT_STATE_UNAVAILABLE(
      HttpStatus.SERVICE_UNAVAILABLE, "Payment state is temporarily unavailable."),
  REFUND_ALREADY_SUCCEEDED(HttpStatus.CONFLICT, "The refund has already succeeded."),
  REFUND_NOT_FAILED(HttpStatus.CONFLICT, "Only a failed refund can be settled manually."),
  REFUND_ATTEMPT_IN_FLIGHT(
      HttpStatus.CONFLICT, "A refund attempt is still waiting for the gateway."),
  FINANCE_NOT_CONFIGURED(HttpStatus.CONFLICT, "Finance configuration is missing."),
  EVENT_CANCELLED(HttpStatus.CONFLICT, "The event is cancelled."),
  EVENT_NOT_ENDED(HttpStatus.CONFLICT, "The event has not ended."),
  PAYMENT_PENDING(HttpStatus.CONFLICT, "A payment attempt is still pending."),
  REFUND_PENDING(HttpStatus.CONFLICT, "A refund is still pending."),
  RECONCILIATION_REQUIRED(HttpStatus.CONFLICT, "Reconciliation is required."),
  RECONCILIATION_STALE(HttpStatus.CONFLICT, "Ledger changed since reconciliation."),
  PAYOUT_ALREADY_PAID(HttpStatus.CONFLICT, "The event payout is already marked paid."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "The request could not be processed.");

  private final HttpStatus status;
  private final String message;

  PaymentErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  public String code() {
    return name();
  }

  public HttpStatus status() {
    return status;
  }

  public String message() {
    return message;
  }
}
