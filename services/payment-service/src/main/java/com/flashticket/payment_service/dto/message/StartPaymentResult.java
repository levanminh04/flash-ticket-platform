package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.enums.*;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class StartPaymentResult {
  @NotNull private final UUID requestId;
  @NotNull private final UUID orderId;
  @NotNull private final StartPaymentResultState state;
  private final UUID attemptId;
  private final String failureCode;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public StartPaymentResult(
      @JsonProperty(value = "requestId", required = true) UUID requestId,
      @JsonProperty(value = "orderId", required = true) UUID orderId,
      @JsonProperty(value = "state", required = true) StartPaymentResultState state,
      @JsonProperty(value = "attemptId", required = true) UUID attemptId,
      @JsonProperty(value = "failureCode", required = true) String failureCode) {
    this.requestId = requestId;
    this.orderId = orderId;
    this.state = state;
    this.attemptId = attemptId;
    this.failureCode = failureCode;
  }

  @AssertTrue
  @JsonIgnore
  public boolean isValidResult() {
    return state != null
        && (state == StartPaymentResultState.CREATED
            ? attemptId != null && failureCode == null
            : attemptId == null && failureCode != null && !failureCode.isBlank());
  }
}
