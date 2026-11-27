package com.flashticket.payment_service.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.payment_service.config.PaymentDataSourceConfig;
import com.flashticket.payment_service.config.PaymentReportReplicaHealth;
import com.flashticket.payment_service.config.PaymentRuntimeConfig;
import com.flashticket.payment_service.dto.response.*;
import com.flashticket.payment_service.entity.EventFinanceSnapshot;
import com.flashticket.payment_service.entity.enums.RefundState;
import com.flashticket.payment_service.exception.PaymentException;
import com.flashticket.payment_service.repository.EventFinanceSnapshotRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentReportReadServiceTests {
  FinanceService financeService;
  RefundService refundService;
  EventFinanceSnapshotRepository finance;
  PaymentReportReplicaHealth health;
  PlatformTransactionManager manager;
  TransactionTemplate template;
  SimpleMeterRegistry metrics;
  PaymentReportReadService reports;

  @BeforeEach
  void setUp() {
    financeService = mock(FinanceService.class);
    refundService = mock(RefundService.class);
    finance = mock(EventFinanceSnapshotRepository.class);
    health = mock(PaymentReportReplicaHealth.class);
    manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    template = new TransactionTemplate(manager);
    template.setName("paymentReportRead");
    template.setReadOnly(true);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    template.setTimeout(10);
    metrics = new SimpleMeterRegistry();
    reports =
        new PaymentReportReadService(
            financeService,
            refundService,
            finance,
            new StaticListableBeanFactory(Map.of("report", template))
                .getBeanProvider(TransactionTemplate.class),
            new StaticListableBeanFactory(Map.of("health", health))
                .getBeanProvider(PaymentReportReplicaHealth.class),
            metrics);
    ReflectionTestUtils.setField(reports, "enabled", true);
    when(health.tryBeginRead()).thenReturn(true);
  }

  @AfterEach
  void clearTransactionContext() {
    TransactionSynchronizationManager.clear();
    metrics.close();
  }

  @Test
  void reportMethodsDelegateArgumentsAndUseSharedReaderTransaction() {
    var id = UUID.randomUUID();
    var page = new PageResponse<RefundAdminView>(List.of(), 2, 7, 0, 0);
    var progress =
        new EventRefundProgressView(id, UUID.randomUUID(), true, 0, 0, 0, 0, null, Instant.EPOCH);
    var reconciliation = mock(EventReconciliationView.class);
    when(refundService.getRefunds(RefundState.FAILED, 2, 7)).thenReturn(page);
    when(refundService.getEventRefundProgress(id)).thenReturn(progress);
    when(financeService.getReconciliation(id, "admin", true)).thenReturn(reconciliation);
    assertThat(reports.getRefunds(RefundState.FAILED, 2, 7)).isSameAs(page);
    assertThat(reports.getEventRefundProgress(id)).isSameAs(progress);
    assertThat(reports.getReconciliation(id, "admin", true)).isSameAs(reconciliation);
    var definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(manager, times(3)).getTransaction(definitions.capture());
    for (var definition : definitions.getAllValues()) {
      assertThat(definition.getName()).isEqualTo("paymentReportRead");
      assertThat(definition.isReadOnly()).isTrue();
      assertThat(definition.getIsolationLevel())
          .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
      assertThat(definition.getPropagationBehavior())
          .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
      assertThat(definition.getTimeout()).isEqualTo(10);
    }
    for (String operation : List.of("refunds", "refund_progress", "reconciliation")) {
      assertThat(
              metrics
                  .get("payment.report.source")
                  .tags("report", operation, "source", "reader")
                  .counter()
                  .count())
          .isEqualTo(1);
    }
    verify(health, times(3)).finishRead();
    verifyNoInteractions(finance);
    verify(refundService).getRefunds(RefundState.FAILED, 2, 7);
    verify(refundService).getEventRefundProgress(id);
    verify(financeService).getReconciliation(id, "admin", true);
    verifyNoMoreInteractions(refundService, financeService);
  }

  @Test
  void disabledIneligibleAndBusyReportsChooseWriterBeforeAttempt() {
    var empty = new StaticListableBeanFactory();
    for (String condition : List.of("disabled", "no-template", "no-health", "ineligible")) {
      setUp();
      if (condition.equals("disabled")) ReflectionTestUtils.setField(reports, "enabled", false);
      if (condition.equals("ineligible")) when(health.tryBeginRead()).thenReturn(false);
      if (condition.equals("no-template") || condition.equals("no-health")) {
        reports =
            new PaymentReportReadService(
                financeService,
                refundService,
                finance,
                condition.equals("no-template")
                    ? empty.getBeanProvider(TransactionTemplate.class)
                    : new StaticListableBeanFactory(Map.of("report", template))
                        .getBeanProvider(TransactionTemplate.class),
                empty.getBeanProvider(PaymentReportReplicaHealth.class),
                metrics);
        ReflectionTestUtils.setField(reports, "enabled", true);
      }
      var failure = new IllegalStateException("writer failure");
      when(refundService.getRefunds(RefundState.FAILED, 0, 20)).thenThrow(failure);
      assertThatThrownBy(() -> reports.getRefunds(RefundState.FAILED, 0, 20)).isSameAs(failure);
      verify(refundService).getRefunds(RefundState.FAILED, 0, 20);
      verifyNoInteractions(manager);
      verify(health, never()).invalidate();
      verify(health, never()).finishRead();
    }
  }

  @Test
  void ambientTransactionsAndSynchronizationKeepCallerSource() {
    for (boolean actual : new boolean[] {false, true}) {
      TransactionSynchronizationManager.setActualTransactionActive(actual);
      if (!actual) TransactionSynchronizationManager.initSynchronization();
      reports.getRefunds(RefundState.FAILED, 0, 20);
      TransactionSynchronizationManager.clear();
    }
    verify(refundService, times(2)).getRefunds(RefundState.FAILED, 0, 20);
    verifyNoInteractions(manager, health);
    assertThat(metrics.find("payment.report.source").counter()).isNull();
  }

  @Test
  void readerBeginQueryAndCommitFailuresReleaseWithoutWriterRetry() {
    for (String phase : List.of("begin", "query", "commit", "metric")) {
      setUp();
      var failure = new IllegalStateException(phase);
      if (phase.equals("begin")) when(manager.getTransaction(any())).thenThrow(failure);
      if (phase.equals("query"))
        when(refundService.getRefunds(RefundState.FAILED, 0, 20)).thenThrow(failure);
      if (phase.equals("commit")) doThrow(failure).when(manager).commit(any());
      if (phase.equals("metric")) {
        var brokenMetrics = mock(MeterRegistry.class);
        when(brokenMetrics.counter(
                "payment.report.source", "report", "refunds", "source", "reader"))
            .thenThrow(failure);
        reports =
            new PaymentReportReadService(
                financeService,
                refundService,
                finance,
                new StaticListableBeanFactory(Map.of("report", template))
                    .getBeanProvider(TransactionTemplate.class),
                new StaticListableBeanFactory(Map.of("health", health))
                    .getBeanProvider(PaymentReportReplicaHealth.class),
                brokenMetrics);
        ReflectionTestUtils.setField(reports, "enabled", true);
      }
      assertThatThrownBy(() -> reports.getRefunds(RefundState.FAILED, 0, 20)).isSameAs(failure);
      verify(health).invalidate();
      verify(health).finishRead();
      verify(refundService, times(phase.equals("begin") || phase.equals("metric") ? 0 : 1))
          .getRefunds(RefundState.FAILED, 0, 20);
      if (phase.equals("commit")) {
        var order = inOrder(manager, health);
        order.verify(manager).commit(any());
        order.verify(health).invalidate();
        order.verify(health).finishRead();
      }
    }
  }

  @Test
  void reconciliationChecksWriterOwnerBeforeClaimingReader() {
    UUID id = UUID.randomUUID();
    var stored = mock(EventFinanceSnapshot.class);
    when(stored.getFeeRate()).thenReturn(new BigDecimal("0.075"));
    when(stored.getOrganizerSubject()).thenReturn("new-owner");
    when(finance.findById(id)).thenReturn(Optional.of(stored));
    assertThatThrownBy(() -> reports.getReconciliation(id, "old-owner", false))
        .isInstanceOf(PaymentException.class);
    verifyNoInteractions(health, financeService);
    var failure = new IllegalStateException("reader still has old owner");
    when(financeService.getReconciliation(id, "new-owner", false)).thenThrow(failure);
    assertThatThrownBy(() -> reports.getReconciliation(id, "new-owner", false)).isSameAs(failure);
    var order = inOrder(finance, health, financeService);
    order.verify(finance, times(2)).findById(id);
    order.verify(health).tryBeginRead();
    order.verify(financeService).getReconciliation(id, "new-owner", false);
    order.verify(health).invalidate();
    order.verify(health).finishRead();
    when(finance.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> reports.getReconciliation(id, "new-owner", false))
        .isInstanceOf(PaymentException.class);
    when(finance.findById(id)).thenReturn(Optional.of(stored));
    when(stored.getFeeRate()).thenReturn(null);
    assertThatThrownBy(() -> reports.getReconciliation(id, "new-owner", false))
        .isInstanceOf(PaymentException.class);
    clearInvocations(finance);
    reports.getReconciliation(id, "admin", true);
    ReflectionTestUtils.setField(reports, "enabled", false);
    reports.getReconciliation(id, "any", false);
    ReflectionTestUtils.setField(reports, "enabled", true);
    TransactionSynchronizationManager.setActualTransactionActive(true);
    reports.getReconciliation(id, "any", false);
    verifyNoInteractions(finance);
  }

  @Test
  void writerOnlyConfigurationNeedsNoReaderSettings() throws Exception {
    new ApplicationContextRunner()
        .withInitializer(
            context ->
                context
                    .getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
        .withUserConfiguration(PaymentDataSourceConfig.class)
        .withPropertyValues(
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/not-used",
            "spring.datasource.username=fixture",
            "spring.datasource.password=fixture")
        .run(
            context -> {
              assertThat(context)
                  .hasNotFailed()
                  .hasBean("paymentWriterDataSource")
                  .hasBean("dbHealthContributor")
                  .doesNotHaveBean("paymentReportReaderDataSource")
                  .doesNotHaveBean("paymentReportReplicaHealth")
                  .doesNotHaveBean("paymentReportReadTransactionTemplate")
                  .doesNotHaveBean("paymentReportReplicaScheduler");
              assertThat(
                      ReflectionTestUtils.getField(
                          context.getBean("dbHealthContributor"), "dataSource"))
                  .isSameAs(context.getBean("paymentWriterDataSource"));
            });
    var config = new PaymentDataSourceConfig();
    assertThatThrownBy(
            () ->
                ReflectionTestUtils.invokeMethod(
                    config, "paymentReportReadTransactionTemplate", manager, 0))
        .isInstanceOf(IllegalArgumentException.class);
    TransactionTemplate read =
        ReflectionTestUtils.invokeMethod(
            config, "paymentReportReadTransactionTemplate", manager, 10);
    assertThat(read.getName()).isEqualTo("paymentReportRead");
    assertThat(read.isReadOnly()).isTrue();
    assertThat(
            new PaymentRuntimeConfig().paymentTransactionTemplate(manager).getPropagationBehavior())
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }
}
