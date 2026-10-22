package com.flashticket.payment_service.dto.message;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.*;
import com.flashticket.payment_service.dto.common.WireMoney;
@JsonTypeInfo(use=JsonTypeInfo.Id.NAME,property="kind")
@JsonSubTypes({@JsonSubTypes.Type(value=PromotionDiscount.Fixed.class,name="FIXED"),@JsonSubTypes.Type(value=PromotionDiscount.Percent.class,name="PERCENT")})
public sealed interface PromotionDiscount {
 record Fixed(@NotNull @WireMoney(positive=true) String amount) implements PromotionDiscount {}
 record Percent(@NotNull @Min(1) @Max(100) Integer percent) implements PromotionDiscount {}
}
