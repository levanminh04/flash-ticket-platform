package com.flashticket.payment_service.exception;

import java.util.Objects;
import org.springframework.http.HttpStatus;

/** A Payment failure with a stable code and safe public message. */
public final class PaymentException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final PaymentErrorCode errorCode;

  public PaymentException(PaymentErrorCode errorCode) {
    this(errorCode, null);
  }

  public PaymentException(PaymentErrorCode errorCode, Throwable cause) {
    super(Objects.requireNonNull(errorCode, "errorCode").message(), cause);
    this.errorCode = errorCode;
  }

  public PaymentErrorCode errorCode() {
    return errorCode;
  }

  public String code() {
    return errorCode.code();
  }

  public HttpStatus status() {
    return errorCode.status();
  }
}
