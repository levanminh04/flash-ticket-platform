package com.flashticket.payment_service.service.impl;

import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.EventCancellation;
import com.flashticket.payment_service.dto.request.ManualSettlementRequest;
import com.flashticket.payment_service.dto.response.*;
import com.flashticket.payment_service.entity.*;
import com.flashticket.payment_service.entity.enums.*;
import com.flashticket.payment_service.exception.PaymentErrorCode;
import com.flashticket.payment_service.exception.PaymentException;
import com.flashticket.payment_service.mapper.RefundMapper;
import com.flashticket.payment_service.repository.*;
import com.flashticket.payment_service.service.MessagingService;
import com.flashticket.payment_service.service.RefundService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundServiceImpl implements RefundService {
  private final RefundRepository refundRepository;
  private final RefundAttemptRepository refundAttemptRepository;
  private final EventRefundRunRepository eventRefundRunRepository;
  private final EventRefundItemRepository eventRefundItemRepository;
  private final PaymentSagaRepository paymentSagaRepository;
  private final OrderPaymentSnapshotRepository snapshots;
  private final EventFinanceSnapshotRepository finance;
  private final PaymentConfirmationRepository confirmations;
  private final ChargeRepository charges;
  private final MessagingService messaging;
  private final ContractCodec codec;
  private final RefundMapper refundMapper;
  private final TransactionTemplate paymentTransactionTemplate;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Refund ensureRefund(Charge charge, RefundReason reason, Instant createdAt) {
    // Khóa order của caller và unique(charge_id) giữ một nghĩa vụ hoàn, không đổi reason đã lưu.
    return refundRepository
        .findByChargeId(charge.getId())
        .orElseGet(() -> refundRepository.save(Refund.pending(charge, reason, createdAt)));
  }

  @Override
  @Transactional
  public void recordEventCancellation(Envelope<EventCancellation> incomingEnvelope) {
    var data = incomingEnvelope.data();
    // Khóa marker trước inbox để tuần tự hóa việc hủy cùng sự kiện.
    var marker = finance.findForUpdateByEventId(data.eventId()).orElse(null);
    if (marker == null) {
      // Chèn đồng thời vi phạm unique thì rollback; message được requeue.
      marker =
          finance.save(
              EventFinanceSnapshot.cancelledMarker(
                  data.eventId(), data.cancellationId(), data.cancelledAt(), Instant.now()));
    }
    if (!messaging.recordInbox("payment.event-cancelled.v1", incomingEnvelope, codec.toTree(data)))
      return;
    if (marker.getCancellationId() == null) {
      marker.setCancellationId(data.cancellationId());
      marker.setCancelledAt(data.cancelledAt());
    } else if (!marker.getCancellationId().equals(data.cancellationId())) {
      log.warn(
          "Event already cancelled: eventId={}, cancellationId={}",
          data.eventId(),
          data.cancellationId());
      return;
    }
    if (eventRefundRunRepository.findBySourceCancellationId(marker.getCancellationId()).isEmpty()) {
      eventRefundRunRepository.save(
          EventRefundRun.started(data.eventId(), marker.getCancellationId(), Instant.now()));
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> findEventRefundRunsToSelect(int limit) {
    return eventRefundRunRepository.findIdsToSelect(
        PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
  }

  @Override
  @Transactional
  public void selectEventRefundOrders(UUID runId, int limit) {
    var observedRun = eventRefundRunRepository.findById(runId).orElseThrow();
    // Khóa marker trước run; mọi xử lý đơn trong lô cùng giữ marker này.
    finance.findForUpdateByEventId(observedRun.getEventId());
    var run = eventRefundRunRepository.findForUpdateById(runId).orElseThrow();
    if (run.getSelectionComplete()) return;
    UUID cursor =
        run.getSelectionCursor() == null ? null : UUID.fromString(run.getSelectionCursor());
    var orderIds =
        confirmations.findOrderIdsByEventIdAfter(
            run.getEventId(), cursor, PageRequest.of(0, limit));
    Instant now = Instant.now();
    for (UUID orderId : orderIds) {
      // Sau marker, khóa snapshot rồi saga của từng đơn.
      snapshots.findForUpdateByOrderId(orderId);
      var saga = paymentSagaRepository.findForUpdateByOrderId(orderId);
      var confirmation = confirmations.findById(orderId).orElseThrow();
      var charge = charges.findById(confirmation.getChargeId()).orElseThrow();
      var item = EventRefundItem.selected(runId, run.getEventId(), orderId, charge.getId(), now);
      var refund = ensureRefund(charge, RefundReason.EVENT_CANCELLED, now);
      item.setRefundId(refund.getId());
      item.setState(
          switch (refund.getState()) {
            case SUCCEEDED -> EventRefundItemState.REFUNDED;
            case FAILED -> EventRefundItemState.NEEDS_ATTENTION;
            default -> EventRefundItemState.REFUND_PENDING;
          });
      eventRefundItemRepository.save(item);
      if (saga.isPresent()
          && (saga.get().getState() == PaymentSagaState.COMPLETED
              || saga.get().getState() == PaymentSagaState.WAITING_TICKETS)) {
        saga.get().setState(PaymentSagaState.COMPENSATING);
        saga.get().setUpdatedAt(now);
      }
    }
    if (!orderIds.isEmpty()) {
      run.setSelectionCursor(orderIds.getLast().toString());
      run.setSelectedOrderCount(run.getSelectedOrderCount() + orderIds.size());
    }
    if (orderIds.size() < limit) {
      run.setSelectionComplete(true);
      completeRunIfFinished(runId, now);
    }
    eventRefundRunRepository.save(run);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> findRefundsToSend(int limit) {
    return refundRepository.findIdsByState(
        RefundState.PENDING, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> findRefundsToPoll(Instant startedBefore, int limit) {
    return refundRepository.findIdsToPoll(
        startedBefore, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
  }

  @Override
  @Transactional(propagation = Propagation.NEVER)
  public void sendRefund(UUID refundId) {
    paymentTransactionTemplate.executeWithoutResult(
        status -> {
          var refund = lockRefund(refundId);
          if (refund.getState() != RefundState.PENDING
              && refund.getState() != RefundState.PROCESSING) return;
          Instant now = Instant.now();
          var attempt =
              refundAttemptRepository
                  .findFirstByRefundIdAndFinishedAtIsNull(refundId)
                  .orElseGet(
                      () ->
                          RefundAttempt.started(
                              refundId, UUID.randomUUID().toString().replace("-", ""), now));
          // Hoàn tiền nội bộ: chỉ cập nhật DB, không gửi lệnh hoặc query sang VNPay.
          // Dùng lại attempt đang mở để xử lý cả refund PROCESSING từ luồng cũ.
          attempt.setFinishedAt(now);
          attempt.setOutcome("DB_ONLY");
          refundAttemptRepository.save(attempt);
          refund.setState(RefundState.SUCCEEDED);
          refund.setCompletedAt(now);
          completeRefund(refund, now);
        });
  }

  @Override
  @Transactional(propagation = Propagation.NEVER)
  public void pollRefund(UUID refundId) {
    sendRefund(refundId);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<RefundAdminView> getRefunds(RefundState state, int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new PaymentException(PaymentErrorCode.INVALID_REQUEST);
    Page<Refund> stored =
        refundRepository.findAllByState(
            state, PageRequest.of(page, size, Sort.by("createdAt", "id")));
    return new PageResponse<>(
        stored.getContent().stream().map(this::toAdminView).toList(),
        stored.getNumber(),
        stored.getSize(),
        stored.getTotalElements(),
        stored.getTotalPages());
  }

  @Override
  @Transactional
  public RefundAdminView retryRefund(UUID refundId) {
    var refund = lockRefund(refundId);
    if (refund.getState() == RefundState.SUCCEEDED)
      throw new PaymentException(PaymentErrorCode.REFUND_ALREADY_SUCCEEDED);
    if (refund.getState() == RefundState.FAILED) {
      refund.setState(RefundState.PENDING);
      var item = eventRefundItemRepository.findFirstByRefundIdOrderByUpdatedAtDesc(refundId);
      if (item.isPresent() && item.get().getState() == EventRefundItemState.NEEDS_ATTENTION) {
        item.get().setState(EventRefundItemState.REFUND_PENDING);
        item.get().setUpdatedAt(Instant.now());
      }
    }
    return toAdminView(refund);
  }

  @Override
  @Transactional
  public RefundAdminView settleRefundManually(
      UUID refundId, ManualSettlementRequest request, String adminSubject) {
    if (adminSubject == null || adminSubject.isBlank())
      throw new PaymentException(PaymentErrorCode.AUTHENTICATION_REQUIRED);
    var refund = lockRefund(refundId);
    if (refund.getState() == RefundState.SUCCEEDED) {
      if (refund.getSettledBy() != null
          && refund.getExternalReference().equals(request.externalReference()))
        return toAdminView(refund);
      throw new PaymentException(PaymentErrorCode.REFUND_ALREADY_SUCCEEDED);
    }
    if (refundAttemptRepository.existsByRefundIdAndFinishedAtIsNull(refundId))
      throw new PaymentException(PaymentErrorCode.REFUND_ATTEMPT_IN_FLIGHT);
    if (refund.getState() != RefundState.FAILED)
      throw new PaymentException(PaymentErrorCode.REFUND_NOT_FAILED);
    Instant now = Instant.now();
    refund.setState(RefundState.SUCCEEDED);
    refund.setSettledBy(adminSubject);
    refund.setExternalReference(request.externalReference());
    refund.setCompletedAt(now);
    completeRefund(refund, now);
    log.info("Refund settled manually: refundId={}, adminSubject={}", refundId, adminSubject);
    return toAdminView(refund);
  }

  @Override
  @Transactional(readOnly = true)
  public EventRefundProgressView getEventRefundProgress(UUID eventId) {
    var run =
        eventRefundRunRepository
            .findFirstByEventIdOrderByStartedAtDesc(eventId)
            .orElseThrow(() -> new PaymentException(PaymentErrorCode.RESOURCE_NOT_FOUND));
    long pending =
        eventRefundItemRepository.countByIdRunIdAndState(run.getId(), EventRefundItemState.SELECTED)
            + eventRefundItemRepository.countByIdRunIdAndState(
                run.getId(), EventRefundItemState.REFUND_PENDING);
    long refunded =
        eventRefundItemRepository.countByIdRunIdAndState(
            run.getId(), EventRefundItemState.REFUNDED);
    long needsAttention =
        eventRefundItemRepository.countByIdRunIdAndState(
            run.getId(), EventRefundItemState.NEEDS_ATTENTION);
    return new EventRefundProgressView(
        eventId,
        run.getSourceCancellationId(),
        run.getSelectionComplete(),
        run.getSelectedOrderCount(),
        pending,
        refunded,
        needsAttention,
        run.getCompletedAt(),
        Instant.now());
  }

  private Refund lockRefund(UUID refundId) {
    var observed =
        refundRepository
            .findById(refundId)
            .orElseThrow(() -> new PaymentException(PaymentErrorCode.RESOURCE_NOT_FOUND));
    var observedSnapshot = snapshots.findById(observed.getOrderId()).orElseThrow();
    // Khóa đúng thứ tự: marker → snapshot → saga → refund.
    finance.findForUpdateByEventId(observedSnapshot.getEventId());
    snapshots.findForUpdateByOrderId(observed.getOrderId());
    paymentSagaRepository.findForUpdateByOrderId(observed.getOrderId());
    return refundRepository.findForUpdateById(refundId).orElseThrow();
  }

  private void completeRefund(Refund refund, Instant completedAt) {
    var saga = paymentSagaRepository.findForUpdateByOrderId(refund.getOrderId());
    // Refund của khoản thu thừa không đổi saga của khoản thu chính.
    if (saga.isPresent()
        && saga.get().getChargeId().equals(refund.getChargeId())
        && saga.get().getState() == PaymentSagaState.COMPENSATING) {
      saga.get().setState(PaymentSagaState.COMPENSATED);
      saga.get().setUpdatedAt(completedAt);
    }
    var item = eventRefundItemRepository.findFirstByRefundIdOrderByUpdatedAtDesc(refund.getId());
    if (item.isPresent() && item.get().getState() != EventRefundItemState.REFUNDED) {
      item.get().setState(EventRefundItemState.REFUNDED);
      item.get().setLastErrorCode(null);
      item.get().setUpdatedAt(completedAt);
      completeRunIfFinished(item.get().getId().getRunId(), completedAt);
    }
  }

  private void completeRunIfFinished(UUID runId, Instant now) {
    var run = eventRefundRunRepository.findForUpdateById(runId).orElseThrow();
    if (run.getCompletedAt() != null || !run.getSelectionComplete()) return;
    if (!eventRefundItemRepository.existsByIdRunIdAndStateNot(runId, EventRefundItemState.REFUNDED))
      run.setCompletedAt(now);
  }

  private RefundAdminView toAdminView(Refund refund) {
    return refundMapper.toAdminView(
        refund,
        refundAttemptRepository
            .findFirstByRefundIdOrderByStartedAtDescIdDesc(refund.getId())
            .orElse(null));
  }
}
