package com.flashticket.booking_service.exception;

import java.util.Objects;

/** Lỗi nghiệp vụ mang mã lỗi thống nhất và có thể giữ lại nguyên nhân gốc. */
public final class BusinessException extends RuntimeException {
  private final ErrorCode errorCode;

  /** Tạo lỗi khi điều kiện nghiệp vụ không đạt, không có exception gốc. */
  public BusinessException(ErrorCode errorCode) {
    this(errorCode, null);
  }

  /** Tạo lỗi nghiệp vụ và giữ cause để debug; message vẫn lấy từ mã lỗi. */
  public BusinessException(ErrorCode errorCode, Throwable cause) {
    super(Objects.requireNonNull(errorCode, "errorCode").message(), cause);
    this.errorCode = errorCode;
  }

  /** Cung cấp mã lỗi để global handler chọn HTTP status và message. */
  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
