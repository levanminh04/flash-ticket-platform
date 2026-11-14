package com.flashticket.payment_service.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.payment_service.client.BookingPurchaseClient;
import com.flashticket.payment_service.client.VNPayRefundClient;
import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.EventCancellation;
import com.flashticket.payment_service.dto.request.ManualSettlementRequest;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.payment_service.entity.RefundAttempt;
import com.flashticket.payment_service.entity.enums.*;
import com.flashticket.payment_service.exception.PaymentErrorCode;
import com.flashticket.payment_service.exception.PaymentException;
import com.flashticket.payment_service.repository.*;
import com.flashticket.payment_service.validation.PaymentContractFixtures;
import com.flashticket.payment_service.worker.PaymentWorker;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@ActiveProfiles("payment-flow-test")
class RefundFlowIntegrationTest {
  @MockitoBean VNPayRefundClient refundClient;
  @MockitoBean BookingPurchaseClient bookingClient;
  @Autowired RefundService refundService;
  @Autowired PaymentWorker worker;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate tx;
  @Autowired ContractCodec codec;
  @Autowired JsonMapper json;
  @Autowired RefundRepository refunds;
  @Autowired RefundAttemptRepository refundAttempts;
  @Autowired PaymentSagaRepository sagas;
  @Autowired EventRefundRunRepository runs;
  @Autowired EventRefundItemRepository items;
  @Autowired ChargeRepository charges;

  @BeforeEach
  void setUp() {
    jdbc.execute(
        "TRUNCATE payment_schema.tbl_order_payment_snapshots,"
            + " payment_schema.tbl_event_finance_snapshots, payment_schema.tbl_event_refund_runs,"
            + " payment_schema.tbl_outbox_messages, payment_schema.tbl_inbox_messages CASCADE");
    reset(refundClient);
  }

  @Test
  void sendsFullRefundOnceAndCompensatesSaga() {
    UUID orderId = seedConfirmedOrder(UUID.randomUUID(), PaymentSagaState.COMPENSATING);
    var charge = charges.findAll().getFirst();
    var refund =
        tx.execute(
            s ->
                refundService.ensureRefund(
                    charge, RefundReason.TICKET_ISSUANCE_FAILED, Instant.now()));

    jdbc.update(
        "update payment_schema.tbl_payment_attempts set payment_url=null where id=?",
        charge.getAttemptId());
    worker.sendRefunds();

    var stored = refunds.findById(refund.getId()).orElseThrow();
    assertThat(stored.getState()).isEqualTo(RefundState.SUCCEEDED);
    assertThat(stored.getCompletedAt()).isNotNull();
    assertThat(refundAttempts.findAll()).hasSize(1);
    assertThat(refundAttempts.findAll().getFirst().getFinishedAt()).isNotNull();
    assertThat(stored.getAmount()).isEqualByComparingTo("100000");
    assertThat(refundAttempts.findAll().getFirst().getOutcome()).isEqualTo("DB_ONLY");
    assertThat(refundAttempts.findAll().getFirst().getProviderRefundId()).isNull();
    assertThat(sagas.findById(orderId).orElseThrow().getState())
        .isEqualTo(PaymentSagaState.COMPENSATED);
    worker.sendRefunds();
    assertThat(refundAttempts.count()).isEqualTo(1);
    verifyNoInteractions(refundClient);
  }

  @Test
  void completesExistingProcessingRefundWithoutGateway() {
    UUID orderId = seedConfirmedOrder(UUID.randomUUID(), PaymentSagaState.COMPENSATING);
    var charge = charges.findAll().getFirst();
    var refund =
        tx.execute(
            s ->
                refundService.ensureRefund(
                    charge, RefundReason.TICKET_ISSUANCE_FAILED, Instant.now()));
    var attempt =
        RefundAttempt.started(refund.getId(), "legacy-request", Instant.now().minusSeconds(120));
    attempt.setProviderRefundId("legacy-provider-refund");
    refundAttempts.save(attempt);
    jdbc.update(
        "update payment_schema.tbl_refunds set state='PROCESSING' where id=?", refund.getId());
    jdbc.update(
        "update payment_schema.tbl_payment_attempts set payment_url=null where id=?",
        charge.getAttemptId());

    worker.pollRefunds();
    var stored = refunds.findById(refund.getId()).orElseThrow();
    assertThat(stored.getState()).isEqualTo(RefundState.SUCCEEDED);
    assertThat(stored.getCompletedAt()).isNotNull();
    var storedAttempt = refundAttempts.findById(attempt.getId()).orElseThrow();
    assertThat(storedAttempt.getFinishedAt()).isNotNull();
    assertThat(storedAttempt.getOutcome()).isEqualTo("DB_ONLY");
    assertThat(storedAttempt.getProviderRefundId()).isEqualTo("legacy-provider-refund");
    assertThat(sagas.findById(orderId).orElseThrow().getState())
        .isEqualTo(PaymentSagaState.COMPENSATED);
    refundService.pollRefund(refund.getId());
    worker.sendRefunds();
    assertThat(refundAttempts.count()).isEqualTo(1);
    assertThat(refunds.findById(refund.getId()).orElseThrow().getCompletedAt())
        .isEqualTo(stored.getCompletedAt());
    verifyNoInteractions(refundClient);
  }

  @Test
  void retriesLegacyFailedRefundWithoutGateway() {
    UUID eventId = UUID.randomUUID();
    seedConfirmedOrder(eventId, PaymentSagaState.COMPLETED);
    refundService.recordEventCancellation(
        cancellationEnvelope(eventId, UUID.randomUUID(), UUID.randomUUID()));
    UUID runId = runs.findAll().getFirst().getId();
    refundService.selectEventRefundOrders(runId, 2);
    UUID refundId = refunds.findAll().getFirst().getId();
    var failedAttempt = RefundAttempt.started(refundId, "failed-request", Instant.now());
    failedAttempt.setFinishedAt(Instant.now());
    failedAttempt.setOutcome("95");
    refundAttempts.save(failedAttempt);
    jdbc.update("update payment_schema.tbl_refunds set state='FAILED' where id=?", refundId);
    jdbc.update(
        "update payment_schema.tbl_event_refund_items set state='NEEDS_ATTENTION' where refund_id=?",
        refundId);
    refundService.sendRefund(refundId);
    assertThat(refunds.findById(refundId).orElseThrow().getState()).isEqualTo(RefundState.FAILED);
    assertThat(refundService.retryRefund(refundId).state()).isEqualTo(RefundState.PENDING);
    assertThat(items.findAll().getFirst().getState())
        .isEqualTo(EventRefundItemState.REFUND_PENDING);
    worker.sendRefunds();
    assertThat(refunds.findAll()).hasSize(1);
    assertThat(refunds.findById(refundId).orElseThrow().getState())
        .isEqualTo(RefundState.SUCCEEDED);
    assertThat(refundAttempts.findAll()).hasSize(2);
    for (var attempt : refundAttempts.findAll()) {
      assertThat(attempt.getRefundId()).isEqualTo(refundId);
      assertThat(attempt.getFinishedAt()).isNotNull();
    }
    verifyNoInteractions(refundClient);
  }

  @Test
  void settlesFailedRefundManuallyOnce() {
    UUID orderId = seedConfirmedOrder(UUID.randomUUID(), PaymentSagaState.COMPENSATING);
    var charge = charges.findAll().getFirst();
    var refund =
        tx.execute(
            s ->
                refundService.ensureRefund(
                    charge, RefundReason.TICKET_ISSUANCE_FAILED, Instant.now()));
    var failedAttempt = RefundAttempt.started(refund.getId(), "manual-request", Instant.now());
    failedAttempt.setFinishedAt(Instant.now());
    failedAttempt.setOutcome("95");
    refundAttempts.save(failedAttempt);
    jdbc.update("update payment_schema.tbl_refunds set state='FAILED' where id=?", refund.getId());
    assertThat(refunds.findById(refund.getId()).orElseThrow().getState())
        .isEqualTo(RefundState.FAILED);

    var view =
        refundService.settleRefundManually(
            refund.getId(), new ManualSettlementRequest("BANK-123"), "admin-1");
    assertThat(view.state()).isEqualTo(RefundState.SUCCEEDED);
    assertThat(view.settledBy()).isEqualTo("admin-1");
    assertThat(view.externalReference()).isEqualTo("BANK-123");
    assertThat(view.completedAt()).isNotNull();
    var replay =
        refundService.settleRefundManually(
            refund.getId(), new ManualSettlementRequest("BANK-123"), "admin-1");
    // PostgreSQL lưu timestamp tới microsecond, còn view đầu được tạo trước commit.
    assertThat(replay).usingRecursiveComparison().ignoringFields("completedAt").isEqualTo(view);
    assertThatThrownBy(
            () ->
                refundService.settleRefundManually(
                    refund.getId(), new ManualSettlementRequest("BANK-456"), "admin-1"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.REFUND_ALREADY_SUCCEEDED));
    var stored = refunds.findById(refund.getId()).orElseThrow();
    assertThat(stored.getState()).isEqualTo(RefundState.SUCCEEDED);
    assertThat(stored.getSettledBy()).isEqualTo("admin-1");
    assertThat(stored.getExternalReference()).isEqualTo("BANK-123");
    assertThat(stored.getCompletedAt()).isEqualTo(replay.completedAt());
    assertThat(sagas.findById(orderId).orElseThrow().getState())
        .isEqualTo(PaymentSagaState.COMPENSATED);
    assertThat(refundAttempts.count()).isEqualTo(1);
  }

  @Test
  void rejectsManualSettlementWhileAttemptInFlight() {
    seedConfirmedOrder(UUID.randomUUID(), PaymentSagaState.COMPENSATING);
    var charge = charges.findAll().getFirst();
    var refund =
        tx.execute(
            s ->
                refundService.ensureRefund(
                    charge, RefundReason.TICKET_ISSUANCE_FAILED, Instant.now()));
    assertThatThrownBy(
            () ->
                refundService.settleRefundManually(
                    refund.getId(), new ManualSettlementRequest("BANK-123"), "admin-1"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.REFUND_NOT_FAILED));
    assertThat(refunds.findById(refund.getId()).orElseThrow().getState())
        .isEqualTo(RefundState.PENDING);
    refundAttempts.save(RefundAttempt.started(refund.getId(), "in-flight-request", Instant.now()));
    jdbc.update(
        "update payment_schema.tbl_refunds set state='PROCESSING' where id=?", refund.getId());
    assertThat(refunds.findById(refund.getId()).orElseThrow().getState())
        .isEqualTo(RefundState.PROCESSING);
    assertThat(refundAttempts.findAll().getFirst().getFinishedAt()).isNull();
    assertThatThrownBy(
            () ->
                refundService.settleRefundManually(
                    refund.getId(), new ManualSettlementRequest("BANK-123"), "admin-1"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.REFUND_ATTEMPT_IN_FLIGHT));
    assertThat(refunds.findById(refund.getId()).orElseThrow().getState())
        .isEqualTo(RefundState.PROCESSING);
  }

  @Test
  void keepsDuplicateRefundAwayFromSaga() {
    UUID orderId = seedConfirmedOrder(UUID.randomUUID(), PaymentSagaState.COMPLETED);
    var primaryCharge = charges.findAll().getFirst();
    UUID extraChargeId = UUID.randomUUID();
    jdbc.update(
        "insert into"
            + " payment_schema.tbl_charges(id,attempt_id,order_id,provider,merchant_account,provider_charge_id,amount,currency,charged_at)"
            + " values (?,?,?,'VNPAY','test',?,100000,'VND',?)",
        extraChargeId,
        primaryCharge.getAttemptId(),
        orderId,
        UUID.randomUUID().toString(),
        Timestamp.from(Instant.now()));
    var extraCharge = charges.findById(extraChargeId).orElseThrow();
    var refund =
        tx.execute(
            s ->
                refundService.ensureRefund(
                    extraCharge, RefundReason.DUPLICATE_PAYMENT, Instant.now()));
    worker.sendRefunds();
    assertThat(refunds.findById(refund.getId()).orElseThrow().getState())
        .isEqualTo(RefundState.SUCCEEDED);
    var saga = sagas.findById(orderId).orElseThrow();
    assertThat(saga.getState()).isEqualTo(PaymentSagaState.COMPLETED);
    assertThat(saga.getChargeId()).isEqualTo(primaryCharge.getId());
  }

  @Test
  void selectsConfirmedOrdersForEventCancellationRun() {
    UUID eventId = UUID.randomUUID();
    var orderIds =
        List.of(
            seedConfirmedOrder(eventId, PaymentSagaState.COMPLETED),
            seedConfirmedOrder(eventId, PaymentSagaState.COMPLETED),
            seedConfirmedOrder(eventId, PaymentSagaState.COMPLETED));
    UUID otherOrderId = seedConfirmedOrder(UUID.randomUUID(), PaymentSagaState.COMPLETED);
    refundService.recordEventCancellation(
        cancellationEnvelope(eventId, UUID.randomUUID(), UUID.randomUUID()));
    assertThat(runs.findAll()).hasSize(1);
    UUID runId = runs.findAll().getFirst().getId();
    refundService.selectEventRefundOrders(runId, 2);
    assertThat(items.count()).isEqualTo(2);
    assertThat(runs.findById(runId).orElseThrow().getSelectionComplete()).isFalse();
    refundService.selectEventRefundOrders(runId, 2);
    assertThat(items.findAll()).hasSize(3);
    assertThat(refunds.findAll()).hasSize(3);
    for (var item : items.findAll()) {
      assertThat(item.getId().getOrderId()).isIn(orderIds);
      assertThat(item.getId().getRunId()).isEqualTo(runId);
      assertThat(item.getState()).isEqualTo(EventRefundItemState.REFUND_PENDING);
      var refund = refunds.findById(item.getRefundId()).orElseThrow();
      assertThat(refund.getReason()).isEqualTo(RefundReason.EVENT_CANCELLED);
      assertThat(refund.getState()).isEqualTo(RefundState.PENDING);
      assertThat(refund.getOrderId()).isEqualTo(item.getId().getOrderId());
      assertThat(refund.getChargeId()).isEqualTo(item.getConfirmedChargeId());
      assertThat(sagas.findById(refund.getOrderId()).orElseThrow().getState())
          .isEqualTo(PaymentSagaState.COMPENSATING);
    }
    var run = runs.findById(runId).orElseThrow();
    assertThat(run.getSelectionComplete()).isTrue();
    assertThat(run.getSelectedOrderCount()).isEqualTo(3);
    assertThat(sagas.findById(otherOrderId).orElseThrow().getState())
        .isEqualTo(PaymentSagaState.COMPLETED);
  }

  @Test
  void completesRunAfterAllItemsRefunded() {
    UUID eventId = UUID.randomUUID();
    seedConfirmedOrder(eventId, PaymentSagaState.COMPLETED);
    seedConfirmedOrder(eventId, PaymentSagaState.COMPLETED);
    refundService.recordEventCancellation(
        cancellationEnvelope(eventId, UUID.randomUUID(), UUID.randomUUID()));
    UUID runId = runs.findAll().getFirst().getId();
    refundService.selectEventRefundOrders(runId, 3);
    assertThat(runs.findById(runId).orElseThrow().getSelectionComplete()).isTrue();

    refundService.sendRefund(refunds.findAll().getFirst().getId());
    assertThat(items.countByIdRunIdAndState(runId, EventRefundItemState.REFUNDED)).isEqualTo(1);
    assertThat(items.countByIdRunIdAndState(runId, EventRefundItemState.REFUND_PENDING))
        .isEqualTo(1);
    assertThat(runs.findById(runId).orElseThrow().getCompletedAt()).isNull();
    worker.sendRefunds();
    assertThat(runs.findById(runId).orElseThrow().getCompletedAt()).isNotNull();
    assertThat(items.findAll()).hasSize(2);
    for (var item : items.findAll()) {
      assertThat(item.getState()).isEqualTo(EventRefundItemState.REFUNDED);
      assertThat(sagas.findById(item.getId().getOrderId()).orElseThrow().getState())
          .isEqualTo(PaymentSagaState.COMPENSATED);
    }
    verifyNoInteractions(refundClient);
  }

  @Test
  void ignoresDuplicateEventCancellationMessage() {
    UUID eventId = UUID.randomUUID();
    UUID cancellationId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    var envelope = cancellationEnvelope(eventId, cancellationId, messageId);
    refundService.recordEventCancellation(envelope);
    refundService.recordEventCancellation(envelope);
    assertThat(runs.count()).isEqualTo(1);
    assertThat(runs.findAll().getFirst().getSourceCancellationId()).isEqualTo(cancellationId);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from payment_schema.tbl_event_finance_snapshots", Long.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select cancellation_id from payment_schema.tbl_event_finance_snapshots where"
                    + " event_id=?",
                UUID.class,
                eventId))
        .isEqualTo(cancellationId);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from payment_schema.tbl_inbox_messages", Long.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from payment_schema.tbl_inbox_messages where"
                    + " consumer_name='payment.event-cancelled.v1' and message_id=?",
                Long.class,
                messageId))
        .isEqualTo(1);
  }

  @Test
  void reportsEventRefundProgress() {
    UUID eventId = UUID.randomUUID();
    for (int i = 0; i < 3; i++) seedConfirmedOrder(eventId, PaymentSagaState.COMPLETED);
    refundService.recordEventCancellation(
        cancellationEnvelope(eventId, UUID.randomUUID(), UUID.randomUUID()));
    UUID runId = runs.findAll().getFirst().getId();
    refundService.selectEventRefundOrders(runId, 4);
    var selected = refunds.findAll();
    UUID succeededId = selected.get(0).getId();
    UUID failedId = selected.get(1).getId();
    refundService.sendRefund(succeededId);
    jdbc.update("update payment_schema.tbl_refunds set state='FAILED' where id=?", failedId);
    jdbc.update(
        "update payment_schema.tbl_event_refund_items set state='NEEDS_ATTENTION' where refund_id=?",
        failedId);

    var progress = refundService.getEventRefundProgress(eventId);
    assertThat(progress.pendingCount()).isEqualTo(1);
    assertThat(progress.refundedCount()).isEqualTo(1);
    assertThat(progress.needsAttentionCount()).isEqualTo(1);
    assertThat(progress.selectedOrderCount()).isEqualTo(3);
    assertThat(progress.selectionComplete()).isTrue();
    assertThat(progress.completedAt()).isNull();
    assertThat(refunds.findById(selected.get(2).getId()).orElseThrow().getState())
        .isEqualTo(RefundState.PENDING);
    assertThatThrownBy(() -> refundService.getEventRefundProgress(UUID.randomUUID()))
        .isInstanceOfSatisfying(
            PaymentException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.RESOURCE_NOT_FOUND));
  }

  private UUID seedConfirmedOrder(UUID eventId, PaymentSagaState sagaState) {
    UUID orderId = UUID.randomUUID();
    UUID attemptId = UUID.randomUUID();
    UUID chargeId = UUID.randomUUID();
    Instant now = Instant.now();
    try {
      var node = (ObjectNode) json.readTree(PaymentContractFixtures.resource("purchase.json"));
      node.put("orderId", orderId.toString())
          .put("eventId", eventId.toString())
          .put("sourceOrderVersion", "1")
          .put("subtotal", "100000")
          .put("discount", "0")
          .put("total", "100000")
          .put("frozenAt", now.minusSeconds(1).toString())
          .put("expiresAt", now.plusSeconds(600).toString());
      ((ObjectNode) node.withArray("items").get(0))
          .put("unitPrice", "100000")
          .put("lineSubtotal", "100000");
      var purchase = codec.fromTree(node, PurchaseSnapshot.class);
      jdbc.update(
          "insert into"
              + " payment_schema.tbl_order_payment_snapshots(order_id,event_id,buyer_subject,expected_amount,currency,expires_at,source_order_version,purchase_snapshot,snapshot_hash,received_at)"
              + " values (?,?,'buyer-1',100000,'VND',?,1,?::jsonb,?,?)",
          orderId,
          eventId,
          Timestamp.from(purchase.expiresAt()),
          codec.write(purchase),
          orderId.toString(),
          Timestamp.from(now));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
    jdbc.update(
        "insert into"
            + " payment_schema.tbl_payment_attempts(id,order_id,provider,merchant_account,expected_amount,currency,started_at,finished_at,state,request_key,request_hash,payment_url)"
            + " values"
            + " (?,?,'VNPAY','test',100000,'VND',?,?,'SUCCEEDED',?,?,'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_CreateDate=20261102123045')",
        attemptId,
        orderId,
        Timestamp.from(now.minusSeconds(1)),
        Timestamp.from(now),
        UUID.randomUUID(),
        attemptId.toString());
    jdbc.update(
        "insert into"
            + " payment_schema.tbl_charges(id,attempt_id,order_id,provider,merchant_account,provider_charge_id,amount,currency,charged_at,recorded_at)"
            + " values (?,?,?,'VNPAY','test',?,100000,'VND',?,?)",
        chargeId,
        attemptId,
        orderId,
        chargeId.toString(),
        Timestamp.from(now),
        Timestamp.from(now));
    jdbc.update(
        "insert into"
            + " payment_schema.tbl_payment_confirmations(order_id,charge_id,amount,currency,confirmed_at)"
            + " values (?,?,100000,'VND',?)",
        orderId,
        chargeId,
        Timestamp.from(now));
    jdbc.update(
        "insert into"
            + " payment_schema.tbl_payment_sagas(order_id,charge_id,state,command_id,next_attempt_at,created_at,updated_at)"
            + " values (?,?,?,?,?,?,?)",
        orderId,
        chargeId,
        sagaState.name(),
        UUID.randomUUID(),
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(now));
    return orderId;
  }

  private Envelope<EventCancellation> cancellationEnvelope(
      UUID eventId, UUID cancellationId, UUID messageId) {
    try {
      var node =
          (ObjectNode) json.readTree(PaymentContractFixtures.resource("EventCancellation.json"));
      node.put("eventId", eventId.toString()).put("cancellationId", cancellationId.toString());
      ((ObjectNode) node.get("sales"))
          .put("eventId", eventId.toString())
          .put("eventStatus", "CANCELLED");
      ((ObjectNode) node.get("access"))
          .put("eventId", eventId.toString())
          .put("eventStatus", "CANCELLED");
      var cancellation = codec.fromTree(node, EventCancellation.class);
      return new Envelope<>(
          messageId,
          "event.cancelled.v1",
          1,
          eventId,
          "1",
          Instant.now(),
          UUID.randomUUID(),
          null,
          null,
          cancellation);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
