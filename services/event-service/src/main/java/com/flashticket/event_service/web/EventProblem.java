package com.flashticket.event_service.web;

public final class EventProblem extends RuntimeException {
  private final int status;
  private final String code;
  private final String field;

  public EventProblem(int status, String code, String field) {
    super(code);
    this.status = status;
    this.code = code;
    this.field = field;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public String field() {
    return field;
  }

  public static EventProblem invalid(String field) {
    return new EventProblem(422, "INVALID_EVENT_CONFIGURATION", field);
  }

  public static EventProblem conflict(String code) {
    return new EventProblem(409, code, null);
  }

  public static EventProblem missing() {
    return new EventProblem(404, "EVENT_NOT_FOUND", null);
  }
}
