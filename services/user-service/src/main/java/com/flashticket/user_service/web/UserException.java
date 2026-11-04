package com.flashticket.user_service.web;

public final class UserException extends RuntimeException {
  private final int status;
  private final String code;

  public UserException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }
}
