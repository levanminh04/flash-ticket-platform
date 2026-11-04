package com.flashticket.payment_service.dto.common;

import jakarta.validation.*;

public final class WireVersionValidator implements ConstraintValidator<WireVersion, String> {
  private boolean allowZero;

  public void initialize(WireVersion annotation) {
    allowZero = annotation.allowZero();
  }

  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null
        || value.matches(allowZero ? "0|[1-9][0-9]{0,18}" : "[1-9][0-9]{0,18}")
            && new java.math.BigInteger(value)
                    .compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE))
                <= 0;
  }
}
