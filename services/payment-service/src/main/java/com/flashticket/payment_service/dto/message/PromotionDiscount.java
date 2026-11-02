package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.flashticket.payment_service.dto.common.WireMoney;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = PromotionDiscount.Fixed.class, name = "FIXED"),
  @JsonSubTypes.Type(value = PromotionDiscount.Percent.class, name = "PERCENT")
})
public sealed interface PromotionDiscount {
  record Fixed(@NotNull @WireMoney(positive = true) String amount) implements PromotionDiscount {}

  record Percent(@NotNull @Min(1) @Max(100) Integer percent) implements PromotionDiscount {}
}
