package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartPaymentResult(
    @NotNull UUID requestId,
    @NotNull UUID orderId,
    @NotNull StartPaymentResultState state,
    UUID attemptId,
    String failureCode) {
  @AssertTrue
  @JsonIgnore
  public boolean isResultConsistent() {
    return state == StartPaymentResultState.CREATED
        ? attemptId != null && failureCode == null
        : state == StartPaymentResultState.REJECTED
            && attemptId == null
            && failureCode != null
            && !failureCode.isBlank();
  }
}
