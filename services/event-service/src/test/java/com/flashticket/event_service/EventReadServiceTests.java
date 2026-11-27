package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flashticket.event_service.application.CancellationTransactions;
import com.flashticket.event_service.application.EventReadService;
import com.flashticket.event_service.application.EventService;
import com.flashticket.event_service.config.EventDataSourceConfig;
import com.flashticket.event_service.config.EventReplicaHealth;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class EventReadServiceTests {
  EventService service;
  CancellationTransactions cancellations;
  EventReplicaHealth health;
  PlatformTransactionManager manager;
  SimpleMeterRegistry metrics;
  TransactionTemplate template;
  EventReadService reads;

  @BeforeEach
  void setUp() {
    service = mock(EventService.class);
    cancellations = mock(CancellationTransactions.class);
    health = mock(EventReplicaHealth.class);
    manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    template = new TransactionTemplate(manager);
    template.setName("eventReplicaRead");
    template.setReadOnly(true);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    template.setTimeout(5);
    metrics = new SimpleMeterRegistry();
    when(health.tryBeginRead()).thenReturn(true);
    reads =
        new EventReadService(
            service,
            cancellations,
            new StaticListableBeanFactory(Map.of("template", template))
                .getBeanProvider(TransactionTemplate.class),
            new StaticListableBeanFactory(Map.of("health", health))
                .getBeanProvider(EventReplicaHealth.class),
            metrics);
  }

  @AfterEach
  void clearTransactionContext() {
    TransactionSynchronizationManager.clear();
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.clearSynchronization();
    metrics.close();
  }

  @Test
  void routesWhitelistedPayloadsWhenEligible() {
    UUID id = UUID.randomUUID();
    LocalDate from = LocalDate.of(2027, 1, 1);
    LocalDate to = from.plusDays(1);
    reads.listPublic(
        "concert", id, from, to, BigDecimal.TEN, "Venue", true, 7, "cursor", false, false);
    reads.readPublic(id);
    reads.categories();
    reads.venues();
    reads.listOwner("owner-a", "DRAFT", 8, "owner-cursor");
    reads.listAdmin("PENDING_APPROVAL", 9, null);
    reads.cancellationHistory(id, "owner-a", true, null, 10, "history-cursor");
    verify(service)
        .listPublic(
            "concert", id, from, to, BigDecimal.TEN, "Venue", true, 7, "cursor", false, false);
    verify(service).readPublic(id);
    verify(service).categories();
    verify(service).venues();
    verify(service).listOwner("owner-a", "DRAFT", 8, "owner-cursor");
    verify(service).listAdmin("PENDING_APPROVAL", 9, null);
    verify(cancellations).history(id, "owner-a", true, null, 10, "history-cursor");
    verify(manager, times(7)).getTransaction(template);
    verify(manager, times(7)).commit(any());
    verify(health, times(7)).finishRead();
    for (String operation :
        List.of(
            "public_list",
            "public_detail",
            "categories",
            "venues",
            "owner_list",
            "admin_list",
            "cancellation_history")) {
      assertThat(
              metrics
                  .get("event.read.source")
                  .tags("operation", operation, "source", "reader")
                  .counter()
                  .count())
          .isEqualTo(1);
    }
    verifyNoMoreInteractions(service, cancellations);
  }

  @Test
  void choosesWriterBeforeReadWhenDisabledIneligibleOrBusy() {
    var disabled =
        new EventReadService(
            service,
            cancellations,
            new StaticListableBeanFactory().getBeanProvider(TransactionTemplate.class),
            new StaticListableBeanFactory().getBeanProvider(EventReplicaHealth.class),
            metrics);
    disabled.categories();
    when(health.tryBeginRead()).thenReturn(false);
    reads.listOwner("owner-a", null, 20, null);
    reads.cancellationHistory(null, "admin", false, "PENDING", 20, null);
    var failure = new IllegalStateException("writer unavailable");
    when(service.venues()).thenThrow(failure);
    try {
      reads.venues();
      Assertions.fail("writer error must propagate");
    } catch (IllegalStateException actual) {
      assertThat(actual).isSameAs(failure);
    }
    verifyNoInteractions(manager);
    verify(health, never()).invalidate();
    verify(health, never()).finishRead();
    verify(service).categories();
    verify(service).listOwner("owner-a", null, 20, null);
    verify(cancellations).history(null, "admin", false, "PENDING", 20, null);
    assertThat(
            metrics
                .get("event.read.source")
                .tags("operation", "categories", "source", "writer")
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void preservesAmbientTransactionWithoutOpeningReader() {
    TransactionSynchronizationManager.setActualTransactionActive(true);
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
    TransactionSynchronizationManager.setCurrentTransactionName("caller");
    reads.categories();
    reads.listAdmin("PENDING_APPROVAL", 20, null);
    TransactionSynchronizationManager.clear();
    TransactionSynchronizationManager.initSynchronization();
    reads.cancellationHistory(null, "admin", false, "PENDING", 20, null);
    verifyNoInteractions(manager, health);
    assertThat(metrics.find("event.read.source").counters()).isEmpty();
    verify(service).categories();
    verify(service).listAdmin("PENDING_APPROVAL", 20, null);
    verify(cancellations).history(null, "admin", false, "PENDING", 20, null);
  }

  @Test
  void readerFailureInvalidatesAndReleasesWithoutWriterRetry() {
    for (String phase : List.of("begin", "query", "commit")) {
      clearInvocations(health, service, manager);
      var failure = new IllegalStateException(phase);
      when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
      when(service.categories()).thenReturn(List.of());
      doAnswer(
              invocation -> {
                verify(health, never()).finishRead();
                if (phase.equals("commit")) throw failure;
                return null;
              })
          .when(manager)
          .commit(any());
      if (phase.equals("begin")) when(manager.getTransaction(any())).thenThrow(failure);
      if (phase.equals("query")) when(service.categories()).thenThrow(failure);
      try {
        reads.categories();
        Assertions.fail("reader error must propagate");
      } catch (IllegalStateException actual) {
        assertThat(actual).isSameAs(failure);
      }
      verify(health).invalidate();
      verify(health).finishRead();
      verify(service, times(phase.equals("begin") ? 0 : 1)).categories();
      // Xóa cả stubbing lỗi để vòng sau không ném lỗi ngay lúc setup Mockito.
      reset(manager, service);
    }
    assertThat(
            metrics
                .get("event.read.replica.attempt.error")
                .tag("operation", "categories")
                .counter()
                .count())
        .isEqualTo(3);
  }

  @Test
  void writerOnlyContextNeedsNoReaderSettingsAndHealthUsesWriter() {
    new ApplicationContextRunner()
        .withUserConfiguration(EventDataSourceConfig.class)
        .withPropertyValues(
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/event_db",
            "spring.datasource.username=fixture",
            "spring.datasource.password=fixture")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .hasBean("eventWriterDataSource")
                  .hasBean("dataSource")
                  .hasBean("dbHealthContributor");
              assertThat(context)
                  .doesNotHaveBean("eventReaderDataSource")
                  .doesNotHaveBean("eventReplicaReadTransactionTemplate")
                  .doesNotHaveBean("eventReplicaHealth")
                  .doesNotHaveBean("eventReplicaScheduler");
              var writer = context.getBean("eventWriterDataSource", HikariDataSource.class);
              var indicator =
                  context.getBean("dbHealthContributor", DataSourceHealthIndicator.class);
              assertThat(ReflectionTestUtils.getField(indicator, "dataSource")).isSameAs(writer);
              assertThat(writer.isRunning()).isFalse();
            });
  }
}
