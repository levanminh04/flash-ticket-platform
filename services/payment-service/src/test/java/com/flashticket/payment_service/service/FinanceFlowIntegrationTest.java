package com.flashticket.payment_service.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.reset;

import com.flashticket.payment_service.client.BookingPurchaseClient;
import com.flashticket.payment_service.client.VNPayRefundClient;
import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.FinanceConfiguration;
import com.flashticket.payment_service.dto.request.ReconciliationRequest;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.payment_service.entity.enums.PaymentSagaState;
import com.flashticket.payment_service.entity.enums.RefundReason;
import com.flashticket.payment_service.exception.PaymentErrorCode;
import com.flashticket.payment_service.exception.PaymentException;
import com.flashticket.payment_service.repository.ChargeRepository;
import com.flashticket.payment_service.repository.EventPayoutRepository;
import com.flashticket.payment_service.repository.RefundRepository;
import com.flashticket.payment_service.validation.PaymentContractFixtures;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class FinanceFlowIntegrationTest {
  @MockitoBean BookingPurchaseClient bookingClient;
  @MockitoBean VNPayRefundClient refundClient;
  @Autowired FinanceService financeService;
  @Autowired RefundService refundService;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate tx;
  @Autowired RefundRepository refunds;
  @Autowired ChargeRepository charges;
  @Autowired EventPayoutRepository payouts;
  @Autowired ContractCodec codec;
  @Autowired JsonMapper json;

  @BeforeEach
  void setUp() {
    jdbc.execute(
        "TRUNCATE payment_schema.tbl_order_payment_snapshots,"
            + " payment_schema.tbl_event_finance_snapshots, payment_schema.tbl_event_refund_runs,"
            + " payment_schema.tbl_event_payouts,"
            + " payment_schema.tbl_outbox_messages, payment_schema.tbl_inbox_messages CASCADE");
    reset(refundClient);
  }

  private UUID seedConfirmedOrder(UUID eventId, String amount) {
    UUID orderId = UUID.randomUUID();
    UUID attemptId = UUID.randomUUID();
    UUID chargeId = UUID.randomUUID();
    Instant now = Instant.now();
    try {
      var node = (ObjectNode) json.readTree(PaymentContractFixtures.resource("purchase.json"));
      node.put("orderId", orderId.toString())
          .put("eventId", eventId.toString())
          .put("sourceOrderVersion", "1")
          .put("subtotal", amount)
          .put("discount", "0")
          .put("total", amount)
          .put("frozenAt", now.minusSeconds(1).toString())
          .put("expiresAt", now.plusSeconds(600).toString());
      ((ObjectNode) node.withArray("items").get(0))
          .put("unitPrice", amount)
          .put("lineSubtotal", amount);
      var purchase = codec.fromTree(node, PurchaseSnapshot.class);
      jdbc.update(
          "insert into"
              + " payment_schema.tbl_order_payment_snapshots(order_id,event_id,buyer_subject,expected_amount,currency,expires_at,source_order_version,purchase_snapshot,snapshot_hash,received_at)"
              + " values (?,?,'buyer-1',?,'VND',?,1,?::jsonb,?,?)",
          orderId,
          eventId,
          new BigDecimal(amount),
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
            + " (?,?,'VNPAY','test',?,'VND',?,?,'SUCCEEDED',?,?,'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_CreateDate=20261102123045')",
        attemptId,
        orderId,
        new BigDecimal(amount),
        Timestamp.from(now.minusSeconds(1)),
        Timestamp.from(now),
        UUID.randomUUID(),
        attemptId.toString());
    jdbc.update(
        "insert into"
            + " payment_schema.tbl_charges(id,attempt_id,order_id,provider,merchant_account,provider_charge_id,amount,currency,charged_at,recorded_at)"
            + " values (?,?,?,'VNPAY','test',?,?,'VND',?,?)",
        chargeId,
        attemptId,
        orderId,
        chargeId.toString(),
        new BigDecimal(amount),
        Timestamp.from(now),
        Timestamp.from(now));
    jdbc.update(
        "insert into"
            + " payment_schema.tbl_payment_confirmations(order_id,charge_id,amount,currency,confirmed_at)"
            + " values (?,?,?,'VND',?)",
        orderId,
        chargeId,
        new BigDecimal(amount),
        Timestamp.from(now));
    jdbc.update(
        "insert into"
            + " payment_schema.tbl_payment_sagas(order_id,charge_id,state,command_id,next_attempt_at,created_at,updated_at)"
            + " values (?,?,?,?,?,?,?)",
        orderId,
        chargeId,
        PaymentSagaState.COMPLETED.name(),
        UUID.randomUUID(),
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(now));
    return orderId;
  }

  private Envelope<FinanceConfiguration> financeEnvelope(
      UUID eventId,
      String organizerSubject,
      Instant eventEndsAt,
      String feeRate,
      String sourceFeeVersion,
      UUID messageId) {
    return new Envelope<>(
        messageId,
        "event.finance-configured.v1",
        1,
        eventId,
        sourceFeeVersion,
        Instant.now(),
        UUID.randomUUID(),
        null,
        null,
        new FinanceConfiguration(
            eventId, organizerSubject, eventEndsAt, feeRate, sourceFeeVersion, "VND"));
  }

  @Test
  void recordsFinanceConfigurationOnceAndKeepsCancellationMarker() {
    UUID eventId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    var envelope =
        financeEnvelope(
            eventId, "organizer-1", Instant.now().minusSeconds(60), "0.075", "1", messageId);
    financeService.recordFinanceConfiguration(envelope);
    financeService.recordFinanceConfiguration(envelope);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from payment_schema.tbl_event_finance_snapshots", Long.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select fee_rate from payment_schema.tbl_event_finance_snapshots where event_id=?",
                BigDecimal.class,
                eventId))
        .isEqualByComparingTo("0.075");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from payment_schema.tbl_inbox_messages where consumer_name='payment.finance-configured.v1' and message_id=?",
                Long.class,
                messageId))
        .isEqualTo(1);
    UUID cancelledEventId = UUID.randomUUID();
    UUID cancellationId = UUID.randomUUID();
    jdbc.update(
        "insert into payment_schema.tbl_event_finance_snapshots(event_id,cancellation_id,cancelled_at,received_at) values (?,?,now(),now())",
        cancelledEventId,
        cancellationId);
    financeService.recordFinanceConfiguration(
        financeEnvelope(
            cancelledEventId,
            "organizer-1",
            Instant.now().minusSeconds(60),
            "0.07",
            "1",
            UUID.randomUUID()));
    assertThat(
            jdbc.queryForObject(
                "select fee_rate from payment_schema.tbl_event_finance_snapshots where event_id=?",
                BigDecimal.class,
                cancelledEventId))
        .isEqualByComparingTo("0.07");
    assertThat(
            jdbc.queryForObject(
                "select cancellation_id from payment_schema.tbl_event_finance_snapshots where event_id=?",
                UUID.class,
                cancelledEventId))
        .isEqualTo(cancellationId);
  }

  @Test
  void ignoresOlderFinanceVersionAndFrozenFee() {
    UUID eventId = UUID.randomUUID();
    Instant endedAt = Instant.now().minusSeconds(60);
    financeService.recordFinanceConfiguration(
        financeEnvelope(eventId, "organizer-1", endedAt, "0.05", "5", UUID.randomUUID()));
    financeService.recordFinanceConfiguration(
        financeEnvelope(eventId, "organizer-1", endedAt, "0.03", "3", UUID.randomUUID()));
    var older = financeService.getReconciliation(eventId, "admin-1", true);
    assertThat(new BigDecimal(older.feeRate())).isEqualByComparingTo("0.05");
    assertThat(older.sourceFeeVersion()).isEqualTo(5L);
    financeService.recordFinanceConfiguration(
        financeEnvelope(eventId, "organizer-1", endedAt, "0.06", "6", UUID.randomUUID()));
    var newer = financeService.getReconciliation(eventId, "admin-1", true);
    assertThat(new BigDecimal(newer.feeRate())).isEqualByComparingTo("0.06");
    assertThat(newer.sourceFeeVersion()).isEqualTo(6L);
    financeService.confirmReconciliation(
        eventId, new ReconciliationRequest("VNPAY-REPORT-1"), "admin-1");
    financeService.recordFinanceConfiguration(
        financeEnvelope(eventId, "organizer-1", endedAt, "0.07", "7", UUID.randomUUID()));
    var frozen = financeService.getReconciliation(eventId, "admin-1", true);
    assertThat(new BigDecimal(frozen.feeRate())).isEqualByComparingTo("0.06");
    assertThat(frozen.sourceFeeVersion()).isEqualTo(6L);
  }

  @Test
  void summarizesValidRevenueAfterRefundsWithHalfUpFee() {
    UUID eventId = UUID.randomUUID();
    financeService.recordFinanceConfiguration(
        financeEnvelope(
            eventId,
            "organizer-1",
            Instant.now().minusSeconds(60),
            "0.075",
            "1",
            UUID.randomUUID()));
    UUID firstOrderId = seedConfirmedOrder(eventId, "100000");
    UUID secondOrderId = seedConfirmedOrder(eventId, "33333");
    var primaryCharge =
        charges.findAll().stream()
            .filter(c -> c.getOrderId().equals(firstOrderId))
            .findFirst()
            .orElseThrow();
    jdbc.update(
        "insert into payment_schema.tbl_charges(id,attempt_id,order_id,provider,merchant_account,provider_charge_id,amount,currency,charged_at) values (?,?,?,'VNPAY','test',?,100000,'VND',?)",
        UUID.randomUUID(),
        primaryCharge.getAttemptId(),
        firstOrderId,
        UUID.randomUUID().toString(),
        Timestamp.from(Instant.now()));
    assertThat(financeService.getFinancialSummary(eventId).validGross()).isEqualTo("133333");
    var refundedCharge =
        charges.findAll().stream()
            .filter(c -> c.getOrderId().equals(secondOrderId))
            .findFirst()
            .orElseThrow();
    // ensureRefund cần transaction để ghi hoàn tiền cùng saga.
    var refund =
        tx.execute(
            s ->
                refundService.ensureRefund(
                    refundedCharge, RefundReason.EVENT_CANCELLED, Instant.now()));
    jdbc.update(
        "update payment_schema.tbl_refunds set state='SUCCEEDED', completed_at=now() where id=?",
        refund.getId());
    assertThat(refunds.findById(refund.getId()).orElseThrow().getCompletedAt()).isNotNull();
    var summary = financeService.getFinancialSummary(eventId);
    assertThat(summary.validGross()).isEqualTo("133333");
    assertThat(summary.validRefunds()).isEqualTo("33333");
    assertThat(summary.feeAmount()).isEqualTo("7500");
    assertThat(summary.netAmount()).isEqualTo("92500");
    assertThat(summary.hasCollectedOrders()).isTrue();
    assertThat(summary.organizerSubject()).isEqualTo("organizer-1");
    assertThat(summary.schemaVersion()).isEqualTo(1);
    UUID roundingEventId = UUID.randomUUID();
    financeService.recordFinanceConfiguration(
        financeEnvelope(
            roundingEventId,
            "organizer-1",
            Instant.now().minusSeconds(60),
            "0.07",
            "1",
            UUID.randomUUID()));
    seedConfirmedOrder(roundingEventId, "50");
    var rounded = financeService.getFinancialSummary(roundingEventId);
    assertThat(rounded.validGross()).isEqualTo("50");
    assertThat(rounded.feeAmount()).isEqualTo("4");
    assertThat(rounded.netAmount()).isEqualTo("46");
  }

  @Test
  void returnsNotFoundWithoutFinanceConfiguration() {
    UUID missingEventId = UUID.randomUUID();
    UUID markerEventId = UUID.randomUUID();
    jdbc.update(
        "insert into payment_schema.tbl_event_finance_snapshots(event_id,cancellation_id,cancelled_at,received_at) values (?,?,now(),now())",
        markerEventId,
        UUID.randomUUID());
    for (UUID eventId : List.of(missingEventId, markerEventId)) {
      assertThatThrownBy(() -> financeService.getFinancialSummary(eventId))
          .isInstanceOfSatisfying(
              PaymentException.class,
              ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.RESOURCE_NOT_FOUND));
      assertThatThrownBy(() -> financeService.getReconciliation(eventId, "admin-1", true))
          .isInstanceOfSatisfying(
              PaymentException.class,
              ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.RESOURCE_NOT_FOUND));
    }
  }

  @Test
  void rejectsReconciliationWhilePendingOrNotEnded() {
    var request = new ReconciliationRequest("VNPAY-REPORT-1");
    assertThatThrownBy(
            () -> financeService.confirmReconciliation(UUID.randomUUID(), request, "admin-1"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.FINANCE_NOT_CONFIGURED));
    UUID futureEventId = UUID.randomUUID();
    financeService.recordFinanceConfiguration(
        financeEnvelope(
            futureEventId,
            "organizer-1",
            Instant.now().plusSeconds(3600),
            "0.07",
            "1",
            UUID.randomUUID()));
    assertThatThrownBy(
            () -> financeService.confirmReconciliation(futureEventId, request, "admin-1"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.EVENT_NOT_ENDED));
    UUID pendingEventId = UUID.randomUUID();
    financeService.recordFinanceConfiguration(
        financeEnvelope(
            pendingEventId,
            "organizer-1",
            Instant.now().minusSeconds(60),
            "0.07",
            "1",
            UUID.randomUUID()));
    UUID pendingOrderId = seedConfirmedOrder(pendingEventId, "100000");
    UUID pendingAttemptId = UUID.randomUUID();
    jdbc.update(
        "insert into payment_schema.tbl_payment_attempts(id,order_id,provider,merchant_account,expected_amount,currency,started_at,state,request_key,request_hash) values (?,?,'VNPAY','test',100000,'VND',now(),'PENDING',?,?)",
        pendingAttemptId,
        pendingOrderId,
        UUID.randomUUID(),
        pendingAttemptId.toString());
    assertThatThrownBy(
            () -> financeService.confirmReconciliation(pendingEventId, request, "admin-1"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAYMENT_PENDING));
    jdbc.update(
        "update payment_schema.tbl_payment_attempts set state='FAILED', finished_at=now() where id=?",
        pendingAttemptId);
    UUID refundEventId = UUID.randomUUID();
    financeService.recordFinanceConfiguration(
        financeEnvelope(
            refundEventId,
            "organizer-1",
            Instant.now().minusSeconds(60),
            "0.07",
            "1",
            UUID.randomUUID()));
    UUID refundOrderId = seedConfirmedOrder(refundEventId, "100000");
    var charge =
        charges.findAll().stream()
            .filter(c -> c.getOrderId().equals(refundOrderId))
            .findFirst()
            .orElseThrow();
    var refund =
        tx.execute(
            s -> refundService.ensureRefund(charge, RefundReason.EVENT_CANCELLED, Instant.now()));
    jdbc.update("update payment_schema.tbl_refunds set state='FAILED' where id=?", refund.getId());
    assertThatThrownBy(
            () -> financeService.confirmReconciliation(refundEventId, request, "admin-1"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.REFUND_PENDING));
    UUID cancelledEventId = UUID.randomUUID();
    financeService.recordFinanceConfiguration(
        financeEnvelope(
            cancelledEventId,
            "organizer-1",
            Instant.now().minusSeconds(60),
            "0.07",
            "1",
            UUID.randomUUID()));
    jdbc.update(
        "update payment_schema.tbl_event_finance_snapshots set cancellation_id=?, cancelled_at=now() where event_id=?",
        UUID.randomUUID(),
        cancelledEventId);
    assertThatThrownBy(
            () -> financeService.confirmReconciliation(cancelledEventId, request, "admin-1"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.EVENT_CANCELLED));
  }

  @Test
  void marksEventPaidOnceAfterReconciliation() {
    UUID eventId = UUID.randomUUID();
    financeService.recordFinanceConfiguration(
        financeEnvelope(
            eventId,
            "organizer-1",
            Instant.now().minusSeconds(60),
            "0.075",
            "1",
            UUID.randomUUID()));
    seedConfirmedOrder(eventId, "100000");
    assertThatThrownBy(() -> financeService.markEventPaid(eventId, "admin-1"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.RECONCILIATION_REQUIRED));
    var request = new ReconciliationRequest("VNPAY-REPORT-1");
    var reconciled = financeService.confirmReconciliation(eventId, request, "admin-1");
    assertThat(reconciled.reconciledBy()).isEqualTo("admin-1");
    assertThat(reconciled.reconciledAt()).isNotNull();
    assertThat(reconciled.reconciliationEvidence()).isEqualTo("VNPAY-REPORT-1");
    var paid = financeService.markEventPaid(eventId, "admin-1");
    assertThat(paid.paidBy()).isEqualTo("admin-1");
    assertThat(paid.paidAt()).isNotNull();
    assertThat(paid.paidAt()).isAfterOrEqualTo(paid.reconciledAt());
    assertThatThrownBy(() -> financeService.markEventPaid(eventId, "admin-1"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAYOUT_ALREADY_PAID));
    assertThatThrownBy(() -> financeService.confirmReconciliation(eventId, request, "admin-1"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.PAYOUT_ALREADY_PAID));
    assertThat(payouts.count()).isEqualTo(1);
    var stored = payouts.findById(eventId).orElseThrow();
    assertThat(stored.getValidGross()).isEqualByComparingTo(paid.validGross());
    assertThat(stored.getValidRefunds()).isEqualByComparingTo(paid.validRefunds());
    assertThat(stored.getFeeAmount()).isEqualByComparingTo(paid.feeAmount());
    assertThat(stored.getNetAmount()).isEqualByComparingTo(paid.netAmount());
    assertThat(paid.validGross()).isEqualTo("100000");
    assertThat(paid.validRefunds()).isEqualTo("0");
    assertThat(paid.feeAmount()).isEqualTo("7500");
    assertThat(paid.netAmount()).isEqualTo("92500");
    assertThat(stored.getPaidBy()).isEqualTo("admin-1");
    // PostgreSQL lưu timestamp tới microsecond.
    assertThat(stored.getPaidAt()).isCloseTo(paid.paidAt(), within(1, ChronoUnit.MICROS));
  }

  @Test
  void requiresNewReconciliationWhenLedgerChanges() {
    UUID eventId = UUID.randomUUID();
    financeService.recordFinanceConfiguration(
        financeEnvelope(
            eventId,
            "organizer-1",
            Instant.now().minusSeconds(60),
            "0.07",
            "1",
            UUID.randomUUID()));
    seedConfirmedOrder(eventId, "100000");
    var request = new ReconciliationRequest("VNPAY-REPORT-1");
    financeService.confirmReconciliation(eventId, request, "admin-1");
    seedConfirmedOrder(eventId, "33333");
    assertThatThrownBy(() -> financeService.markEventPaid(eventId, "admin-1"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.RECONCILIATION_STALE));
    var refreshed =
        financeService.confirmReconciliation(
            eventId, new ReconciliationRequest("VNPAY-REPORT-2"), "admin-1");
    assertThat(refreshed.validGross()).isEqualTo("133333");
    assertThat(refreshed.reconciliationEvidence()).isEqualTo("VNPAY-REPORT-2");
    var paid = financeService.markEventPaid(eventId, "admin-1");
    assertThat(paid.paidAt()).isNotNull();
    assertThat(paid.validGross()).isEqualTo("133333");
    assertThat(paid.netAmount()).isEqualTo("124000");
  }

  @Test
  void restrictsReconciliationReportToOwnerOrAdmin() {
    UUID eventId = UUID.randomUUID();
    financeService.recordFinanceConfiguration(
        financeEnvelope(
            eventId,
            "organizer-1",
            Instant.now().minusSeconds(60),
            "0.07",
            "1",
            UUID.randomUUID()));
    assertThat(financeService.getReconciliation(eventId, "organizer-1", false).eventId())
        .isEqualTo(eventId);
    assertThatThrownBy(() -> financeService.getReconciliation(eventId, "organizer-2", false))
        .isInstanceOfSatisfying(
            PaymentException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(PaymentErrorCode.RESOURCE_NOT_FOUND));
    assertThat(financeService.getReconciliation(eventId, "admin-1", true).eventId())
        .isEqualTo(eventId);
  }
}
