package com.flashticket.payment_service.exception;
import org.springframework.http.HttpStatus;
public final class PaymentQueryException extends RuntimeException {
  private static final long serialVersionUID=1L;
  private final HttpStatus status; private final String code;
  public PaymentQueryException(HttpStatus status,String code){super(code);this.status=status;this.code=code;}
  public HttpStatus status(){return status;}
  public String code(){return code;}
}
