package com.flashticket.payment_service.dto.snapshot;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.enums.*;
import com.flashticket.payment_service.validation.ContractRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class PromotionConfig {
  @NotNull private final UUID promotionId;
  @NotBlank private final String code;
  @NotNull private final Instant startsAt;
  @NotNull private final Instant endsAt;

  @NotBlank
  @Pattern(regexp = "^[1-9][0-9]{0,18}$")
  @DecimalMax("9223372036854775807")
  private final String maximumUses;

  @NotNull
  @Min(1)
  @Max(1)
  private final Integer maximumUsesPerUser;

  @NotNull @Valid private final Discount discount;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public PromotionConfig(
      @JsonProperty(value = "promotionId", required = true) UUID promotionId,
      @JsonProperty(value = "code", required = true) String code,
      @JsonProperty(value = "startsAt", required = true) Instant startsAt,
      @JsonProperty(value = "endsAt", required = true) Instant endsAt,
      @JsonProperty(value = "maximumUses", required = true) String maximumUses,
      @JsonProperty(value = "maximumUsesPerUser", required = true) Integer maximumUsesPerUser,
      @JsonProperty(value = "discount", required = true) PromotionConfig.Discount discount) {
    this.promotionId = promotionId;
    this.code = code;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.maximumUses = maximumUses;
    this.maximumUsesPerUser = maximumUsesPerUser;
    this.discount = discount;
  }

  @AssertTrue
  @JsonIgnore
  public boolean isValidWindow() {
    return startsAt != null
        && endsAt != null
        && startsAt.isBefore(endsAt)
        && Integer.valueOf(1).equals(maximumUsesPerUser)
        && ContractRules.validUInt64(maximumUses);
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
  public interface Discount {
    String getKind();
  }

  @Getter
  public static final class FixedDiscount implements Discount {
    @NotBlank
    @Pattern(regexp = "FIXED")
    private final String kind;

    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private final String amount;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public FixedDiscount(
        @JsonProperty(value = "kind", required = true) String kind,
        @JsonProperty(value = "amount", required = true) String amount) {
      this.kind = kind;
      this.amount = amount;
    }
  }

  @Getter
  public static final class PercentDiscount implements Discount {
    @NotBlank
    @Pattern(regexp = "PERCENT")
    private final String kind;

    @NotNull
    @Min(1)
    @Max(100)
    private final Integer percent;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public PercentDiscount(
        @JsonProperty(value = "kind", required = true) String kind,
        @JsonProperty(value = "percent", required = true) Integer percent) {
      this.kind = kind;
      this.percent = percent;
    }
  }
}
