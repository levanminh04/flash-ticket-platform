package com.flashticket.payment_service.dto.payment;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.constraints.*;
import java.util.*;

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
