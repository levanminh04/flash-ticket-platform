package com.flashticket.booking_service.dto.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

public record PromotionConfig(
    @NotNull UUID promotionId,
    @NotBlank String code,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") @DecimalMax("9223372036854775807")
        String maximumUses,
    @Positive int maximumUsesPerUser,
    @NotNull @Valid Discount discount) {
  @AssertTrue
  @JsonIgnore
  public boolean isValidWindow() {
    return startsAt != null
        && endsAt != null
        && startsAt.isBefore(endsAt)
        && maximumUsesPerUser == 1
        && com.flashticket.booking_service.validation.ContractRules.validUInt64(maximumUses);
  }

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXISTING_PROPERTY,
      property = "kind",
      visible = true)
  @JsonSubTypes({
    @JsonSubTypes.Type(value = FixedDiscount.class, name = "FIXED"),
    @JsonSubTypes.Type(value = PercentDiscount.class, name = "PERCENT")
  })
  public sealed interface Discount permits FixedDiscount, PercentDiscount {
    String kind();
  }

  public record FixedDiscount(
      @NotBlank String kind, @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") String amount)
      implements Discount {}

  public record PercentDiscount(@NotBlank String kind, @Min(1) @Max(100) int percent)
      implements Discount {}
}
