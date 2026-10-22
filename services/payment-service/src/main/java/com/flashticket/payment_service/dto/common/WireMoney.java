package com.flashticket.payment_service.dto.common;
import java.lang.annotation.*;
import jakarta.validation.*;
@Target({ElementType.FIELD,ElementType.PARAMETER,ElementType.RECORD_COMPONENT,ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy=WireMoneyValidator.class)
public @interface WireMoney {
 String message() default "INVALID_MONEY";
 Class<?>[] groups() default {};
 Class<? extends Payload>[] payload() default {};
 boolean positive() default false;
}
