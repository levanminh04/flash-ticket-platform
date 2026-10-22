package com.flashticket.payment_service.dto.common;

public final class ContractValidationException extends IllegalArgumentException {
  private static final long serialVersionUID=1L;
  private final String field;
  private final String code;
  public ContractValidationException(String field,String code){super(field+": "+code);this.field=field;this.code=code;}
  public String field(){return field;}
  public String code(){return code;}
}
