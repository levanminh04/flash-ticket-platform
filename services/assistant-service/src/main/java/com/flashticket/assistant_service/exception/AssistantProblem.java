package com.flashticket.assistant_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public final class AssistantProblem extends RuntimeException {
  private final HttpStatus status;
  private final String code;
  private final String safeDetail;

  public AssistantProblem(HttpStatus status, String code, String safeDetail) {
    super(code);
    this.status = status;
    this.code = code;
    this.safeDetail = safeDetail;
  }
}
