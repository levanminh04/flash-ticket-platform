package com.flashticket.payment_service.dto.common;

import jakarta.validation.*;
import java.lang.annotation.*;

@Target({
  ElementType.FIELD,
  ElementType.PARAMETER,
  ElementType.RECORD_COMPONENT,
  ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WireVersionValidator.class)
public @interface WireVersion {
  boolean allowZero() default false;

  String message() default "INVALID_VERSION";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
