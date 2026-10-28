package com.flashticket.payment_service.exception;

import org.springframework.http.HttpStatus;

/** Stable public error codes for Payment use cases and HTTP boundaries. */
public enum PaymentErrorCode {
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
