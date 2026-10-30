package com.flashticket.ticket_service.web;

/** A public, safe error code. Never construct from exception text, a QR token or user input. */
public final class TicketProblem extends RuntimeException {
  private final int status;
  private final String code;

  public TicketProblem(int status, String code) {
    super(safeCode(code));
    if (status < 400 || status > 599) throw new IllegalArgumentException("Invalid problem status");
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  private static String safeCode(String code) {
    if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}")) {
      throw new IllegalArgumentException("Invalid problem code");
    }
    return code;
  }
}
