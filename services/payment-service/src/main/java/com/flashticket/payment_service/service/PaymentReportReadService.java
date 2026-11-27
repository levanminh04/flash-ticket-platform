package com.flashticket.payment_service.service;

import com.flashticket.payment_service.config.PaymentReportReplicaHealth;
import com.flashticket.payment_service.dto.response.EventReconciliationView;
import com.flashticket.payment_service.dto.response.EventRefundProgressView;
import com.flashticket.payment_service.dto.response.PageResponse;
import com.flashticket.payment_service.dto.response.RefundAdminView;
import com.flashticket.payment_service.entity.enums.RefundState;
import com.flashticket.payment_service.exception.PaymentErrorCode;
import com.flashticket.payment_service.exception.PaymentException;
import com.flashticket.payment_service.repository.EventFinanceSnapshotRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentReportReadService {
  private final FinanceService financeService;
  private final RefundService refundService;
  private final EventFinanceSnapshotRepository finance;
  private final ObjectProvider<TransactionTemplate> reportTransactionProvider;
  private final ObjectProvider<PaymentReportReplicaHealth> replicaHealthProvider;
  private final MeterRegistry metrics;

  @Value("${payment.report-replica.enabled:false}")
  private boolean enabled;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Spring-managed service proxies and MeterRegistry are shared singleton dependencies.")
  public PaymentReportReadService(
      FinanceService financeService,
      RefundService refundService,
      EventFinanceSnapshotRepository finance,
      @Qualifier("paymentReportReadTransactionTemplate")
          ObjectProvider<TransactionTemplate> reportTransactionProvider,
      @Qualifier("paymentReportReplicaHealth")
          ObjectProvider<PaymentReportReplicaHealth> replicaHealthProvider,
      MeterRegistry metrics) {
    this.financeService = financeService;
    this.refundService = refundService;
    this.finance = finance;
    this.reportTransactionProvider = reportTransactionProvider;
    this.replicaHealthProvider = replicaHealthProvider;
    this.metrics = metrics;
  }

  public PageResponse<RefundAdminView> getRefunds(RefundState state, int page, int size) {
    return readFromEligibleReplica("refunds", () -> refundService.getRefunds(state, page, size));
  }

  public EventRefundProgressView getEventRefundProgress(UUID eventId) {
    return readFromEligibleReplica(
        "refund_progress", () -> refundService.getEventRefundProgress(eventId));
  }

  public EventReconciliationView getReconciliation(
      UUID eventId, String actorSubject, boolean admin) {
    if (enabled
        && !admin
        && !TransactionSynchronizationManager.isActualTransactionActive()
        && !TransactionSynchronizationManager.isSynchronizationActive()
        && reportTransactionProvider.getIfAvailable() != null) {
      // Finance configuration có thể đổi organizer. Kiểm writer trước marker/permit;
      // payload vẫn tự kiểm owner ở snapshot reader và có thể fail closed khi lag.
      var stored = finance.findById(eventId).orElse(null);
      if (stored == null
          || stored.getFeeRate() == null
          || !actorSubject.equals(stored.getOrganizerSubject())) {
        throw new PaymentException(PaymentErrorCode.RESOURCE_NOT_FOUND);
      }
    }
    return readFromEligibleReplica(
        "reconciliation", () -> financeService.getReconciliation(eventId, actorSubject, admin));
  }

  private <T> T readFromEligibleReplica(String operation, Supplier<T> query) {
    // Giữ nguồn của caller; không gắn nhãn writer khi chưa biết connection thật.
    if (TransactionSynchronizationManager.isActualTransactionActive()
        || TransactionSynchronizationManager.isSynchronizationActive()) return query.get();
    var template = enabled ? reportTransactionProvider.getIfAvailable() : null;
    var health = template == null ? null : replicaHealthProvider.getIfAvailable();
    if (health == null || !health.tryBeginRead()) {
      metrics.counter("payment.report.source", "report", operation, "source", "writer").increment();
      return query.get();
    }
    try {
      // Giữ slot qua cả begin/acquire/query/commit và lỗi metric, không chỉ callback.
      metrics.counter("payment.report.source", "report", operation, "source", "reader").increment();
      return template.execute(status -> query.get());
    } catch (RuntimeException failure) {
      health.invalidate();
      try {
        metrics.counter("payment.report.replica.attempt.error", "report", operation).increment();
      } catch (RuntimeException metricFailure) {
        failure.addSuppressed(metricFailure);
      }
      throw failure;
    } finally {
      health.finishRead();
    }
  }
}
