package com.flashticket.payment_service.dto.response;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class PaymentView {
  @NotNull private final UUID orderId;
  private final UUID acceptedChargeId;
  @Valid private final PaymentStart latestAttempt;
  @Valid private final StartRequest startRequest;
  @NotNull @Valid private final List<@NotNull RefundView> refunds;

  @Getter
  @AllArgsConstructor
  public static final class StartRequest {
    @NotNull private final UUID requestId;
    @NotNull private final StartRequestState state;
    private final String failureCode;
  }

  @Getter
  @AllArgsConstructor
  public static final class RefundView {
    @NotNull private final UUID refundId;
    @NotNull private final UUID chargeId;

    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private final String amount;

    @NotNull private final RefundState state;
    @NotNull private final RefundReason reason;
  }
}
