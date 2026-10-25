package com.flashticket.payment_service.gateway;

/** Safe provider boundary failure; never embeds request, response or credential values. */
public final class GatewayException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public enum Code {
    INVALID_REQUEST("99"),
    INVALID_SIGNATURE("97"),
    MERCHANT_MISMATCH("97"),
    ATTEMPT_NOT_FOUND("01"),
    AMOUNT_MISMATCH("04"),
    UNAVAILABLE("99"),
    CAPABILITY_UNAVAILABLE("99");
    private final String ack;

    Code(String ack) {
      this.ack = ack;
    }

    public String ackCode() {
      return ack;
    }
  }

  private final Code code;

  public GatewayException(Code code) {
    super("VNPay " + code.name());
    this.code = code;
  }

  public Code code() {
    return code;
  }
}
