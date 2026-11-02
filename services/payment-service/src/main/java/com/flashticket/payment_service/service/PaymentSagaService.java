package com.flashticket.payment_service.service;

import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.IssuanceResult;
import com.flashticket.payment_service.dto.message.OrderCompleted;
import com.flashticket.payment_service.dto.message.PaymentDecision;
import com.flashticket.payment_service.entity.Charge;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentSagaService {
  void recordSuccessfulCharge(Charge charge);

  void recordPaymentDecision(Envelope<PaymentDecision> incomingEnvelope);

  void recordIssuanceResult(Envelope<IssuanceResult> incomingEnvelope);

  void recordOrderCompleted(Envelope<OrderCompleted> incomingEnvelope);

  List<UUID> findRecoverableOrderIds(Instant dueAt, int limit);

  void recoverSaga(UUID orderId);
}
