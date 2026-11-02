package com.flashticket.payment_service.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartRequestView(
    @NotNull UUID requestId, @NotNull StartRequestState state, String failureCode) {
  @AssertTrue
  @JsonIgnore
  public boolean isFailureConsistent() {
    return state == StartRequestState.REJECTED
        ? failureCode != null && !failureCode.isBlank()
        : failureCode == null;
  }
}
