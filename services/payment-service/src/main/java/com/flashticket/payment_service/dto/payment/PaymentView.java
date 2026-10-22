package com.flashticket.payment_service.dto.payment;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public record PaymentView(
    @NotNull UUID orderId,
    UUID acceptedChargeId,
    @Valid PaymentStart latestAttempt,
    @Valid StartRequestView startRequest,
    @NotNull List<@Valid RefundView> refunds) {
  public PaymentView {
    if (refunds != null) refunds = List.copyOf(refunds);
  }

  public List<RefundView> refunds() {
    return refunds == null ? null : List.copyOf(refunds);
  }
}
