package com.flashticket.booking_service.exception;

import java.util.Objects;

public final class BusinessException extends RuntimeException {
  private final BusinessErrorCode errorCode;

  public BusinessException(BusinessErrorCode errorCode) {
    this(errorCode, null);
  }

  public BusinessException(BusinessErrorCode errorCode, Throwable cause) {
    super(Objects.requireNonNull(errorCode, "errorCode").message(), cause);
    this.errorCode = errorCode;
  }

  public BusinessErrorCode getErrorCode() {
    return errorCode;
  }
}
