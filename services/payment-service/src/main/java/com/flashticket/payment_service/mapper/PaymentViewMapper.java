package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.dto.payment.*;
import java.util.*;

public final class PaymentViewMapper {
  public PaymentView toDto(
      UUID orderId,
      UUID acceptedChargeId,
      PaymentStart latestAttempt,
      StartRequestView startRequest,
      List<RefundView> refunds) {
    return new PaymentView(orderId, acceptedChargeId, latestAttempt, startRequest, refunds);
  }
}
