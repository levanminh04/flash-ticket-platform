package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.payment.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.constraints.*;
import java.util.*;

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
