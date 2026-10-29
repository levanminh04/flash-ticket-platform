package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.enums.*;
import com.flashticket.payment_service.enums.Currency;
import com.flashticket.payment_service.validation.ContractRules;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class FinanceConfiguration {
  @NotNull private final UUID eventId;
  @NotBlank private final String organizerSubject;
  @NotNull private final Instant eventEndsAt;
  @NotBlank private final String feeRate;

  @NotBlank
  @Pattern(regexp = "^[1-9][0-9]{0,18}$")
  @DecimalMax("9223372036854775807")
  private final String sourceFeeVersion;

  @NotNull private final Currency currency;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public FinanceConfiguration(
      @JsonProperty(value = "eventId", required = true) UUID eventId,
      @JsonProperty(value = "organizerSubject", required = true) String organizerSubject,
      @JsonProperty(value = "eventEndsAt", required = true) Instant eventEndsAt,
      @JsonProperty(value = "feeRate", required = true) String feeRate,
      @JsonProperty(value = "sourceFeeVersion", required = true) String sourceFeeVersion,
      @JsonProperty(value = "currency", required = true) Currency currency) {
    this.eventId = eventId;
    this.organizerSubject = organizerSubject;
    this.eventEndsAt = eventEndsAt;
    this.feeRate = feeRate;
    this.sourceFeeVersion = sourceFeeVersion;
    this.currency = currency;
  }

  @AssertTrue
  @JsonIgnore
  public boolean isValidConfiguration() {
    return ContractRules.validFeeRate(feeRate)
        && ContractRules.validUInt64(sourceFeeVersion)
        && currency == Currency.VND;
  }
}
