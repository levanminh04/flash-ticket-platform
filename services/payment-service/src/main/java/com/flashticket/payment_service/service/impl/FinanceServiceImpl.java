package com.flashticket.payment_service.service.impl;

import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.FinanceConfiguration;
import com.flashticket.payment_service.dto.request.ReconciliationRequest;
import com.flashticket.payment_service.dto.response.EventFinancialSummary;
import com.flashticket.payment_service.dto.response.EventReconciliationView;
import com.flashticket.payment_service.entity.EventFinanceSnapshot;
import com.flashticket.payment_service.entity.EventPayout;
import com.flashticket.payment_service.exception.PaymentErrorCode;
import com.flashticket.payment_service.exception.PaymentException;
import com.flashticket.payment_service.mapper.MoneyMapper;
import com.flashticket.payment_service.repository.*;
import com.flashticket.payment_service.service.FinanceService;
import com.flashticket.payment_service.service.MessagingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceServiceImpl implements FinanceService {
  private final EventFinanceSnapshotRepository finance;
  private final EventPayoutRepository payouts;
  private final PaymentConfirmationRepository confirmations;
  private final RefundRepository refunds;
  private final PaymentAttemptRepository attempts;
  private final ChargeRepository charges;
  private final MessagingService messaging;
  private final ContractCodec codec;
  private final MoneyMapper money;

  @Override
  @Transactional
  public void recordFinanceConfiguration(Envelope<FinanceConfiguration> incomingEnvelope) {
    var data = incomingEnvelope.data();
    var eventId = data.eventId();
    // Khóa marker trước inbox để tuần tự theo sự kiện.
    var stored = finance.findForUpdateByEventId(eventId).orElse(null);
    if (!messaging.recordInbox(
        "payment.finance-configured.v1", incomingEnvelope, codec.toTree(data))) return;
    var feeRate = new BigDecimal(data.feeRate());
    var version = Long.parseLong(data.sourceFeeVersion());
    if (stored == null) {
      finance.save(
          EventFinanceSnapshot.configured(
              eventId,
              data.organizerSubject(),
              data.eventEndsAt(),
              feeRate,
              version,
              Instant.now()));
      return;
    }
    if (stored.getFeeRate() != null) {
      if (version <= stored.getSourceFeeVersion()) return;
      var payout = payouts.findById(eventId).orElse(null);
      if (payout != null && payout.getReconciledAt() != null) {
        log.warn(
            "Finance configuration ignored after reconciliation: eventId={}, version={}",
            eventId,
            version);
        return;
      }
    }
    stored.setOrganizerSubject(data.organizerSubject());
    stored.setEventEndsAt(data.eventEndsAt());
    stored.setFeeRate(feeRate);
    stored.setSourceFeeVersion(version);
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public EventFinancialSummary getFinancialSummary(UUID eventId) {
    var stored = finance.findById(eventId).orElse(null);
    if (stored == null || stored.getFeeRate() == null)
      throw new PaymentException(PaymentErrorCode.RESOURCE_NOT_FOUND);
    var view = buildReconciliation(stored, payouts.findById(eventId).orElse(null));
    return new EventFinancialSummary(
        1,
        eventId,
        stored.getOrganizerSubject(),
        view.asOf(),
        charges.existsByEventId(eventId),
        "VND",
        view.validGross(),
        view.validRefunds(),
        view.feeAmount(),
        view.netAmount());
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public EventReconciliationView getReconciliation(
      UUID eventId, String actorSubject, boolean admin) {
    var stored = finance.findById(eventId).orElse(null);
    if (stored == null || stored.getFeeRate() == null)
      throw new PaymentException(PaymentErrorCode.RESOURCE_NOT_FOUND);
    if (!admin && !stored.getOrganizerSubject().equals(actorSubject))
      throw new PaymentException(PaymentErrorCode.RESOURCE_NOT_FOUND);
    return buildReconciliation(stored, payouts.findById(eventId).orElse(null));
  }

  @Override
  @Transactional
  public EventReconciliationView confirmReconciliation(
      UUID eventId, ReconciliationRequest request, String adminSubject) {
    if (adminSubject == null || adminSubject.isBlank())
      throw new PaymentException(PaymentErrorCode.AUTHENTICATION_REQUIRED);
    var stored = lockConfiguredFinance(eventId);
    var payout = payouts.findForUpdateByEventId(eventId).orElse(null);
    if (payout != null && payout.getPaidAt() != null)
      throw new PaymentException(PaymentErrorCode.PAYOUT_ALREADY_PAID);
    var ledger = buildReconciliation(stored, payout);
    requireReconcilable(ledger);
    if (payout == null) payout = EventPayout.forEvent(stored);
    payout.setValidGross(new BigDecimal(ledger.validGross()));
    payout.setValidRefunds(new BigDecimal(ledger.validRefunds()));
    payout.setFeeAmount(new BigDecimal(ledger.feeAmount()));
    payout.setNetAmount(new BigDecimal(ledger.netAmount()));
    payout.setPaymentPendingCount(0L);
    payout.setRefundPendingCount(0L);
    payout.setAsOf(ledger.asOf());
    payout.setFeeRate(stored.getFeeRate());
    payout.setSourceFeeVersion(stored.getSourceFeeVersion());
    payout.setEventEndsAt(stored.getEventEndsAt());
    payout.setReconciledBy(adminSubject);
    payout.setReconciledAt(Instant.now());
    payout.setReconciliationEvidence(request.evidence());
    var saved = payouts.save(payout);
    log.info("Event reconciled: eventId={}, adminSubject={}", eventId, adminSubject);
    return buildReconciliation(stored, saved);
  }

  @Override
  @Transactional
  public EventReconciliationView markEventPaid(UUID eventId, String adminSubject) {
    if (adminSubject == null || adminSubject.isBlank())
      throw new PaymentException(PaymentErrorCode.AUTHENTICATION_REQUIRED);
    var stored = lockConfiguredFinance(eventId);
    var payout = payouts.findForUpdateByEventId(eventId).orElse(null);
    if (payout == null || payout.getReconciledAt() == null)
      throw new PaymentException(PaymentErrorCode.RECONCILIATION_REQUIRED);
    if (payout.getPaidAt() != null)
      throw new PaymentException(PaymentErrorCode.PAYOUT_ALREADY_PAID);
    var ledger = buildReconciliation(stored, payout);
    requireReconcilable(ledger);
    // Số liệu đổi sau khi chốt thì phải đối soát lại.
    if (new BigDecimal(ledger.validGross()).compareTo(payout.getValidGross()) != 0
        || new BigDecimal(ledger.validRefunds()).compareTo(payout.getValidRefunds()) != 0
        || new BigDecimal(ledger.feeAmount()).compareTo(payout.getFeeAmount()) != 0
        || new BigDecimal(ledger.netAmount()).compareTo(payout.getNetAmount()) != 0)
      throw new PaymentException(PaymentErrorCode.RECONCILIATION_STALE);
    payout.setPaidBy(adminSubject);
    payout.setPaidAt(Instant.now());
    log.info("Event payout marked paid: eventId={}, adminSubject={}", eventId, adminSubject);
    return buildReconciliation(stored, payout);
  }

  private EventReconciliationView buildReconciliation(
      EventFinanceSnapshot finance, EventPayout payout) {
    var now = Instant.now();
    var eventId = finance.getEventId();
    var gross = confirmations.sumAmountByEventId(eventId);
    var refunded = refunds.sumSucceededConfirmedAmountByEventId(eventId);
    var paymentPending = attempts.countUnfinishedByEventId(eventId);
    var refundPending = refunds.countUnfinishedByEventId(eventId);
    // Phí tính một lần trên toàn sự kiện, GOV-140.
    var fee =
        gross.subtract(refunded).multiply(finance.getFeeRate()).setScale(0, RoundingMode.HALF_UP);
    var net = gross.subtract(refunded).subtract(fee);
    var cancelled = finance.getCancellationId() != null;
    var eligible =
        !cancelled
            && !now.isBefore(finance.getEventEndsAt())
            && paymentPending == 0
            && refundPending == 0
            && (payout == null || payout.getPaidAt() == null);
    return new EventReconciliationView(
        eventId,
        finance.getOrganizerSubject(),
        "VND",
        money.toWire(gross),
        money.toWire(refunded),
        money.toWire(fee),
        money.toWire(net),
        finance.getFeeRate().toPlainString(),
        finance.getSourceFeeVersion(),
        finance.getEventEndsAt(),
        cancelled,
        paymentPending,
        refundPending,
        eligible,
        payout == null ? null : payout.getReconciledBy(),
        payout == null ? null : payout.getReconciledAt(),
        payout == null ? null : payout.getReconciliationEvidence(),
        payout == null ? null : payout.getPaidBy(),
        payout == null ? null : payout.getPaidAt(),
        now);
  }

  private void requireReconcilable(EventReconciliationView ledger) {
    if (ledger.cancelled()) throw new PaymentException(PaymentErrorCode.EVENT_CANCELLED);
    if (Instant.now().isBefore(ledger.eventEndsAt()))
      throw new PaymentException(PaymentErrorCode.EVENT_NOT_ENDED);
    if (ledger.paymentPendingCount() > 0)
      throw new PaymentException(PaymentErrorCode.PAYMENT_PENDING);
    if (ledger.refundPendingCount() > 0)
      throw new PaymentException(PaymentErrorCode.REFUND_PENDING);
  }

  private EventFinanceSnapshot lockConfiguredFinance(UUID eventId) {
    var stored = finance.findForUpdateByEventId(eventId).orElse(null);
    if (stored == null || stored.getFeeRate() == null)
      throw new PaymentException(PaymentErrorCode.FINANCE_NOT_CONFIGURED);
    return stored;
  }
}
