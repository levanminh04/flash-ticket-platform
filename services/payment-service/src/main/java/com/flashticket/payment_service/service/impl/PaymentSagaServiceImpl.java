package com.flashticket.payment_service.service.impl;

import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.payment_service.entity.*;
import com.flashticket.payment_service.entity.enums.*;
import com.flashticket.payment_service.observability.BusinessTelemetry;
import com.flashticket.payment_service.repository.*;
import com.flashticket.payment_service.repository.PaymentSagaRepository;
import com.flashticket.payment_service.service.MessagingService;
import com.flashticket.payment_service.service.PaymentSagaService;
import com.flashticket.payment_service.service.RefundService;
import com.flashticket.payment_service.validation.PurchaseSnapshotValidator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentSagaServiceImpl implements PaymentSagaService {
  private final PaymentSagaRepository paymentSagaRepository;
  private final OrderPaymentSnapshotRepository snapshots;
  private final PaymentConfirmationRepository confirmations;
  private final ChargeRepository charges;
  private final EventFinanceSnapshotRepository finance;
  private final OutboxMessageRepository outbox;
  private final MessagingService messaging;
  private final RefundService refunds;
  private final ContractCodec codec;
  private final PurchaseSnapshotValidator purchases;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordSuccessfulCharge(Charge charge) {
    // Caller đã khóa snapshot/attempt và lưu charge trong cùng TX webhook.
    var storedSaga = paymentSagaRepository.findForUpdateByOrderId(charge.getOrderId());
    if (storedSaga.isPresent()) {
      if (!storedSaga.get().getChargeId().equals(charge.getId()))
        refunds.ensureRefund(charge, RefundReason.DUPLICATE_PAYMENT, Instant.now());
      return;
    }
    var snapshot = snapshots.findById(charge.getOrderId()).orElseThrow();
    Instant now = Instant.now();
    UUID commandId = UUID.randomUUID();
    paymentSagaRepository.save(
        PaymentSaga.waitingBooking(charge.getOrderId(), charge.getId(), commandId, now));
    messaging.enqueue(
        new Envelope<>(
            commandId,
            "booking.accept-payment.v1",
            1,
            charge.getOrderId(),
            snapshot.getSourceOrderVersion().toString(),
            now,
            charge.getOrderId(),
            null,
            null,
            new AcceptPayment(
                charge.getOrderId(),
                charge.getId(),
                charge.getAttemptId(),
                charge.getAmount().toPlainString(),
                charge.getCurrency(),
                snapshot.getSourceOrderVersion().toString())));
  }

  @Override
  @Transactional
  public void recordPaymentDecision(Envelope<PaymentDecision> incomingEnvelope) {
    var decision = incomingEnvelope.data();
    var observedSnapshot =
        snapshots
            .findById(decision.orderId())
            .orElseThrow(
                () -> new ContractValidationException("orderId", "PAYMENT_SNAPSHOT_NOT_FOUND"));
    var eventMarker = finance.findForUpdateByEventId(observedSnapshot.getEventId());
    var snapshot =
        snapshots
            .findForUpdateByOrderId(decision.orderId())
            .orElseThrow(
                () -> new ContractValidationException("orderId", "PAYMENT_SNAPSHOT_NOT_FOUND"));
    var saga =
        paymentSagaRepository
            .findForUpdateByOrderId(decision.orderId())
            .orElseThrow(
                () -> new ContractValidationException("orderId", "PAYMENT_SAGA_NOT_FOUND"));
    if (!saga.getChargeId().equals(decision.chargeId()))
      throw new ContractValidationException("chargeId", "SAGA_CHARGE_MISMATCH");
    if (!messaging.recordInbox(
        "payment.payment-decision.v1", incomingEnvelope, codec.toTree(decision))) return;
    if (saga.getState() != PaymentSagaState.WAITING_BOOKING) return;
    var charge = charges.findById(saga.getChargeId()).orElseThrow();
    if (decision.outcome() == PaymentDecisionOutcome.ACCEPTED
        && eventMarker.map(marker -> marker.getCancellationId() == null).orElse(true)) {
      var purchase = codec.fromTree(snapshot.getPurchaseSnapshot(), PurchaseSnapshot.class);
      purchases.validate(purchase);
      confirmations.save(PaymentConfirmation.accepted(charge, decision.decisionAt()));
      UUID commandId = UUID.randomUUID();
      BusinessTelemetry.committed(
          "saga-step",
          saga.getOrderId(),
          saga.getState(),
          PaymentSagaState.WAITING_TICKETS,
          "SAGA_STATE_CHANGED");
      saga.setState(PaymentSagaState.WAITING_TICKETS);
      saga.setCommandId(commandId);
      messaging.enqueue(
          new Envelope<>(
              commandId,
              "ticket.issue.v1",
              1,
              saga.getOrderId(),
              Long.toString(saga.getRowVersion() + 2),
              Instant.now(),
              saga.getOrderId(),
              incomingEnvelope.messageId(),
              incomingEnvelope.traceparent(),
              new IssueTickets(saga.getOrderId(), saga.getChargeId(), purchase)));
    } else {
      RefundReason reason =
          eventMarker.map(marker -> marker.getCancellationId() != null).orElse(false)
              ? RefundReason.EVENT_CANCELLED
              : decision.outcome() == PaymentDecisionOutcome.DUPLICATE
                  ? RefundReason.DUPLICATE_PAYMENT
                  : RefundReason.LATE_PAYMENT;
      refunds.ensureRefund(charge, reason, Instant.now());
      BusinessTelemetry.committed(
          "saga-step",
          saga.getOrderId(),
          saga.getState(),
          PaymentSagaState.COMPENSATING,
          "SAGA_STATE_CHANGED");
      saga.setState(PaymentSagaState.COMPENSATING);
    }
    saga.setUpdatedAt(Instant.now());
    saga.setNextAttemptAt(Instant.now().plusSeconds(30));
    paymentSagaRepository.save(saga);
  }

  @Override
  @Transactional
  public void recordIssuanceResult(Envelope<IssuanceResult> incomingEnvelope) {
    var result = incomingEnvelope.data();
    var observedSnapshot = snapshots.findById(result.orderId()).orElseThrow();
    var eventMarker = finance.findForUpdateByEventId(observedSnapshot.getEventId());
    var snapshot = snapshots.findForUpdateByOrderId(result.orderId()).orElseThrow();
    var saga = paymentSagaRepository.findForUpdateByOrderId(result.orderId()).orElseThrow();
    var confirmation = confirmations.findById(result.orderId()).orElseThrow();
    if (!saga.getChargeId().equals(result.chargeId())
        || !confirmation.getChargeId().equals(result.chargeId()))
      throw new ContractValidationException("chargeId", "SAGA_CHARGE_MISMATCH");
    var purchase = codec.fromTree(snapshot.getPurchaseSnapshot(), PurchaseSnapshot.class);
    if (result.state() == IssuanceState.COMPLETED
        && result.ticketCount() != purchases.expectedTicketCount(purchase))
      throw new ContractValidationException("ticketCount", "TICKET_COUNT_MISMATCH");
    if (!messaging.recordInbox(
        "payment.issuance-result.v1", incomingEnvelope, codec.toTree(result))) return;
    var previousNotification =
        outbox.findFirstByAggregateIdAndMessageTypeInOrderByOccurredAtDescMessageIdDesc(
            result.orderId(),
            List.of(
                "ticket.issued.v1", "booking.issuance-failed.v1", "booking.issuance-result.v1"));
    if (previousNotification.isPresent()) {
      var previousResult =
          codec
              .readEnvelope(
                  previousNotification.get().getPayload().toString(), BookingIssuanceResult.class)
              .data();
      if (!previousResult.issuanceId().equals(result.issuanceId())
          || !previousResult.chargeId().equals(result.chargeId())
          || !previousResult.state().name().equals(result.state().name())
          || !previousResult.ticketCount().equals(result.ticketCount())
          || !java.util.Objects.equals(previousResult.failureCode(), result.failureCode()))
        throw new ContractValidationException("issuanceId", "ISSUANCE_RESULT_CONFLICT");
      return;
    }
    // So checkpoint trước state guard để không ACK nhầm kết quả trái nhau đến muộn.
    if (saga.getState() != PaymentSagaState.WAITING_TICKETS) return;
    if (eventMarker.map(marker -> marker.getCancellationId() != null).orElse(false)) {
      refunds.ensureRefund(
          charges.findById(saga.getChargeId()).orElseThrow(),
          RefundReason.EVENT_CANCELLED,
          Instant.now());
      BusinessTelemetry.committed(
          "saga-step",
          saga.getOrderId(),
          saga.getState(),
          PaymentSagaState.COMPENSATING,
          "SAGA_STATE_CHANGED");
      saga.setState(PaymentSagaState.COMPENSATING);
    } else {
      if (result.state() == IssuanceState.FAILED) {
        refunds.ensureRefund(
            charges.findById(saga.getChargeId()).orElseThrow(),
            RefundReason.TICKET_ISSUANCE_FAILED,
            Instant.now());
        BusinessTelemetry.committed(
            "saga-step",
            saga.getOrderId(),
            saga.getState(),
            PaymentSagaState.COMPENSATING,
            "SAGA_STATE_CHANGED");
        saga.setState(PaymentSagaState.COMPENSATING);
      }
      UUID commandId = UUID.randomUUID();
      saga.setCommandId(commandId);
      // Ticket success vẫn WAITING_TICKETS cho tới khi Booking commit COMPLETED.
      messaging.enqueue(
          new Envelope<>(
              commandId,
              result.state() == IssuanceState.COMPLETED
                  ? "ticket.issued.v1"
                  : "booking.issuance-failed.v1",
              1,
              saga.getOrderId(),
              snapshot.getSourceOrderVersion().toString(),
              Instant.now(),
              saga.getOrderId(),
              incomingEnvelope.messageId(),
              incomingEnvelope.traceparent(),
              new BookingIssuanceResult(
                  result.orderId(),
                  result.chargeId(),
                  result.issuanceId(),
                  BookingIssuanceState.valueOf(result.state().name()),
                  result.ticketCount(),
                  result.failureCode())));
    }
    saga.setUpdatedAt(Instant.now());
    saga.setNextAttemptAt(Instant.now().plusSeconds(30));
    paymentSagaRepository.save(saga);
  }

  @Override
  @Transactional
  public void recordOrderCompleted(Envelope<OrderCompleted> incomingEnvelope) {
    var result = incomingEnvelope.data();
    var observedSnapshot =
        snapshots
            .findById(result.orderId())
            .orElseThrow(
                () -> new ContractValidationException("orderId", "PAYMENT_SNAPSHOT_NOT_FOUND"));
    var eventMarker = finance.findForUpdateByEventId(observedSnapshot.getEventId());
    var snapshot =
        snapshots
            .findForUpdateByOrderId(result.orderId())
            .orElseThrow(
                () -> new ContractValidationException("orderId", "PAYMENT_SNAPSHOT_NOT_FOUND"));
    var saga =
        paymentSagaRepository
            .findForUpdateByOrderId(result.orderId())
            .orElseThrow(
                () -> new ContractValidationException("orderId", "PAYMENT_SAGA_NOT_FOUND"));
    var confirmation =
        confirmations
            .findById(result.orderId())
            .orElseThrow(
                () -> new ContractValidationException("orderId", "PAYMENT_CONFIRMATION_NOT_FOUND"));
    if (!saga.getChargeId().equals(result.chargeId())
        || !confirmation.getChargeId().equals(result.chargeId()))
      throw new ContractValidationException("chargeId", "SAGA_CHARGE_MISMATCH");
    if (saga.getState() == PaymentSagaState.COMPENSATING
        || saga.getState() == PaymentSagaState.COMPENSATED) {
      messaging.recordInbox("payment.order-completed.v1", incomingEnvelope, codec.toTree(result));
      return;
    }
    if (eventMarker.map(marker -> marker.getCancellationId() != null).orElse(false)) {
      if (!messaging.recordInbox(
          "payment.order-completed.v1", incomingEnvelope, codec.toTree(result))) return;
      refunds.ensureRefund(
          charges.findById(saga.getChargeId()).orElseThrow(),
          RefundReason.EVENT_CANCELLED,
          Instant.now());
      BusinessTelemetry.committed(
          "saga-step",
          saga.getOrderId(),
          saga.getState(),
          PaymentSagaState.COMPENSATING,
          "SAGA_STATE_CHANGED");
      saga.setState(PaymentSagaState.COMPENSATING);
    } else {
      var notification =
          outbox.findFirstByAggregateIdAndMessageTypeInOrderByOccurredAtDescMessageIdDesc(
              result.orderId(),
              List.of(
                  "ticket.issued.v1", "booking.issuance-failed.v1", "booking.issuance-result.v1"));
      var purchase = codec.fromTree(snapshot.getPurchaseSnapshot(), PurchaseSnapshot.class);
      if (result.ticketCount() != purchases.expectedTicketCount(purchase))
        throw new ContractValidationException("issuanceId", "ORDER_COMPLETION_MISMATCH");
      // Outbox đã commit trước khi gửi: không cần chờ worker ghi publishedAt.
      if (notification.isEmpty())
        throw new ContractValidationException("issuanceId", "ORDER_COMPLETION_MISMATCH");
      if (!incomingEnvelope.aggregateVersion().equals(snapshot.getSourceOrderVersion().toString())
          || !notification
              .get()
              .getPayload()
              .get("aggregateVersion")
              .asString()
              .equals(snapshot.getSourceOrderVersion().toString()))
        throw new ContractValidationException("aggregateVersion", "ORDER_COMPLETION_MISMATCH");
      var ticketResult =
          codec
              .readEnvelope(notification.get().getPayload().toString(), BookingIssuanceResult.class)
              .data();
      if (ticketResult.state() != BookingIssuanceState.COMPLETED
          || !ticketResult.issuanceId().equals(result.issuanceId())
          || !ticketResult.chargeId().equals(result.chargeId())
          || !ticketResult.ticketCount().equals(result.ticketCount()))
        throw new ContractValidationException("issuanceId", "ORDER_COMPLETION_MISMATCH");
      if (saga.getState() != PaymentSagaState.WAITING_TICKETS
          && saga.getState() != PaymentSagaState.COMPLETED)
        throw new ContractValidationException("state", "SAGA_STATE_MISMATCH");
      // Metadata cố định theo order giúp phát hiện completion mâu thuẫn dù messageId khác nhau.
      messaging.recordInbox(
          "payment.order-completed.checkpoint.v1",
          new Envelope<>(
              result.orderId(),
              "booking.order-completed.v1",
              1,
              result.orderId(),
              snapshot.getSourceOrderVersion().toString(),
              snapshot.getReceivedAt(),
              result.orderId(),
              null,
              null,
              result),
          codec.toTree(result));
      if (!messaging.recordInbox(
          "payment.order-completed.v1", incomingEnvelope, codec.toTree(result))) return;
      if (saga.getState() == PaymentSagaState.COMPLETED) return;
      BusinessTelemetry.committed(
          "saga-step",
          saga.getOrderId(),
          saga.getState(),
          PaymentSagaState.COMPLETED,
          "SAGA_STATE_CHANGED");
      saga.setState(PaymentSagaState.COMPLETED);
    }
    saga.setUpdatedAt(Instant.now());
    paymentSagaRepository.save(saga);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> findRecoverableOrderIds(Instant dueAt, int limit) {
    return paymentSagaRepository.findRecoverableOrderIds(
        dueAt, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
  }

  @Override
  @Transactional
  public void recoverSaga(UUID orderId) {
    var observedSnapshot = snapshots.findById(orderId).orElseThrow();
    var eventMarker = finance.findForUpdateByEventId(observedSnapshot.getEventId());
    var snapshot = snapshots.findForUpdateByOrderId(orderId).orElseThrow();
    var saga = paymentSagaRepository.findForUpdateByOrderId(orderId).orElseThrow();
    if ((saga.getState() != PaymentSagaState.WAITING_BOOKING
            && saga.getState() != PaymentSagaState.WAITING_TICKETS)
        || saga.getNextAttemptAt().isAfter(Instant.now())) return;
    if (eventMarker.map(marker -> marker.getCancellationId() != null).orElse(false)) {
      refunds.ensureRefund(
          charges.findById(saga.getChargeId()).orElseThrow(),
          RefundReason.EVENT_CANCELLED,
          Instant.now());
      BusinessTelemetry.committed(
          "saga-step",
          saga.getOrderId(),
          saga.getState(),
          PaymentSagaState.COMPENSATING,
          "SAGA_STATE_CHANGED");
      saga.setState(PaymentSagaState.COMPENSATING);
    } else {
      var previousNotification =
          saga.getState() == PaymentSagaState.WAITING_TICKETS
              ? outbox.findFirstByAggregateIdAndMessageTypeInOrderByOccurredAtDescMessageIdDesc(
                  orderId,
                  List.of(
                      "ticket.issued.v1",
                      "booking.issuance-failed.v1",
                      "booking.issuance-result.v1"))
              : java.util.Optional.<OutboxMessage>empty();
      var previousCommand =
          previousNotification.orElseGet(
              () ->
                  outbox
                      .findFirstByAggregateIdAndMessageTypeOrderByOccurredAtDescMessageIdDesc(
                          orderId,
                          saga.getState() == PaymentSagaState.WAITING_BOOKING
                              ? "booking.accept-payment.v1"
                              : "ticket.issue.v1")
                      .orElseThrow());
      var previousEnvelope = previousCommand.getPayload();
      String messageType = previousCommand.getMessageType();
      // Chỉ đọc type cũ trong lịch sử; message mới luôn gửi theo contract hiện hành.
      if ("booking.issuance-result.v1".equals(messageType))
        messageType =
            "COMPLETED".equals(previousEnvelope.get("data").get("state").asString())
                ? "ticket.issued.v1"
                : "booking.issuance-failed.v1";
      // Chưa gửi/đang retry transport thì không nhân thêm lệnh recovery.
      if (previousCommand.getPublishedAt() != null) {
        UUID commandId = UUID.randomUUID();
        messaging.enqueue(
            new Envelope<>(
                commandId,
                messageType,
                1,
                orderId,
                // Recovery giữ version dữ liệu gốc, không lấy rowVersion Saga.
                "ticket.issue.v1".equals(messageType)
                    ? previousEnvelope.get("aggregateVersion").asString()
                    : snapshot.getSourceOrderVersion().toString(),
                Instant.now(),
                orderId,
                previousCommand.getMessageId(),
                null,
                previousEnvelope.get("data")));
        saga.setCommandId(commandId);
      }
    }
    saga.setNextAttemptAt(Instant.now().plusSeconds(30));
    saga.setUpdatedAt(Instant.now());
    paymentSagaRepository.save(saga);
  }
}
