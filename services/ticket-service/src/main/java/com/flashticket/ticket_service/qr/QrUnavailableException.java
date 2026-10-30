package com.flashticket.ticket_service.qr;

/** A retryable QR dependency failure; never a terminal business issuance result. */
public final class QrUnavailableException extends RuntimeException {
  public QrUnavailableException() {
    super("QR_UNAVAILABLE");
  }

  public String code() {
    return "QR_UNAVAILABLE";
  }
}
