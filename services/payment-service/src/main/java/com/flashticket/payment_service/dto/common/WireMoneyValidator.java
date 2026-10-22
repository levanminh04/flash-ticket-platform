package com.flashticket.payment_service.dto.common;
import jakarta.validation.*;
public final class WireMoneyValidator implements ConstraintValidator<WireMoney,String> {
private boolean positive; public void initialize(WireMoney a){positive=a.positive();}
public boolean isValid(String value,ConstraintValidatorContext context) {return value==null || value.matches("0|[1-9][0-9]{0,18}") && (!positive || !value.equals("0"));}
}
