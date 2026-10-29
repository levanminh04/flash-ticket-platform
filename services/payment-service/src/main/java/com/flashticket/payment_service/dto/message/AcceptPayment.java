package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.enums.*;
import com.flashticket.payment_service.enums.Currency;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class AcceptPayment {
  @NotNull private final UUID orderId;
  @NotNull private final UUID chargeId;
  @NotNull private final UUID attemptId;

  @NotBlank
  @Pattern(regexp = "^[1-9][0-9]{0,18}$")
  private final String amount;

  @NotNull private final Currency currency;

  @NotBlank
  @Pattern(regexp = "^[1-9][0-9]{0,18}$")
  @DecimalMax("9223372036854775807")
  private final String sourceOrderVersion;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public AcceptPayment(
      @JsonProperty(value = "orderId", required = true) UUID orderId,
      @JsonProperty(value = "chargeId", required = true) UUID chargeId,
      @JsonProperty(value = "attemptId", required = true) UUID attemptId,
      @JsonProperty(value = "amount", required = true) String amount,
      @JsonProperty(value = "currency", required = true) Currency currency,
      @JsonProperty(value = "sourceOrderVersion", required = true) String sourceOrderVersion) {
    this.orderId = orderId;
    this.chargeId = chargeId;
    this.attemptId = attemptId;
    this.amount = amount;
    this.currency = currency;
    this.sourceOrderVersion = sourceOrderVersion;
  }
}
