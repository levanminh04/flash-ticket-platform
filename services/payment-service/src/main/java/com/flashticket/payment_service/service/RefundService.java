package com.flashticket.payment_service.service;

import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.EventCancellation;
import com.flashticket.payment_service.dto.request.ManualSettlementRequest;
import com.flashticket.payment_service.dto.response.EventRefundProgressView;
import com.flashticket.payment_service.dto.response.PageResponse;
import com.flashticket.payment_service.dto.response.RefundAdminView;
import com.flashticket.payment_service.entity.Charge;
import com.flashticket.payment_service.entity.Refund;
import com.flashticket.payment_service.entity.enums.RefundReason;
import com.flashticket.payment_service.entity.enums.RefundState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RefundService {
  Refund ensureRefund(Charge charge, RefundReason reason, Instant createdAt);

  void recordEventCancellation(Envelope<EventCancellation> incomingEnvelope);

  List<UUID> findEventRefundRunsToSelect(int limit);

  void selectEventRefundOrders(UUID runId, int limit);

  List<UUID> findRefundsToSend(int limit);

  List<UUID> findRefundsToPoll(Instant startedBefore, int limit);

  void sendRefund(UUID refundId);

  void pollRefund(UUID refundId);

  PageResponse<RefundAdminView> getRefunds(RefundState state, int page, int size);

  RefundAdminView retryRefund(UUID refundId);

  RefundAdminView settleRefundManually(
      UUID refundId, ManualSettlementRequest request, String adminSubject);

  EventRefundProgressView getEventRefundProgress(UUID eventId);
}
