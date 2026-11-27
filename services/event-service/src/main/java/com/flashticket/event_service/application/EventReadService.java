package com.flashticket.event_service.application;

import com.flashticket.event_service.api.EventViews;
import com.flashticket.event_service.config.EventReplicaHealth;
import com.flashticket.event_service.persistence.CancellationRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EventReadService {
  private final EventService eventService;
  private final CancellationTransactions cancellationTransactions;
  private final ObjectProvider<TransactionTemplate> transactionProvider;
  private final ObjectProvider<EventReplicaHealth> healthProvider;
  private final MeterRegistry metrics;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Shares Spring-managed infrastructure without exposing it.")
  public EventReadService(
      EventService eventService,
      CancellationTransactions cancellationTransactions,
      @Qualifier("eventReplicaReadTransactionTemplate")
          ObjectProvider<TransactionTemplate> transactionProvider,
      @Qualifier("eventReplicaHealth") ObjectProvider<EventReplicaHealth> healthProvider,
      MeterRegistry metrics) {
    this.eventService = eventService;
    this.cancellationTransactions = cancellationTransactions;
    this.transactionProvider = transactionProvider;
    this.healthProvider = healthProvider;
    this.metrics = metrics;
  }

  public EventViews.Page<?> listPublic(
      String query,
      UUID category,
      LocalDate from,
      LocalDate to,
      BigDecimal maxPrice,
      String venue,
      boolean includePast,
      int size,
      String cursor,
      boolean compact,
      boolean includeSeatMap) {
    return readFromEligibleReplica(
        "public_list",
        () ->
            eventService.listPublic(
                query,
                category,
                from,
                to,
                maxPrice,
                venue,
                includePast,
                size,
                cursor,
                compact,
                includeSeatMap));
  }

  public EventViews.Public readPublic(UUID id) {
    return readFromEligibleReplica("public_detail", () -> eventService.readPublic(id));
  }

  public List<EventViews.Lookup> categories() {
    return readFromEligibleReplica("categories", () -> eventService.categories());
  }

  public List<EventViews.Venue> venues() {
    return readFromEligibleReplica("venues", () -> eventService.venues());
  }

  public EventViews.Page<EventViews.Summary> listOwner(
      String actor, String status, int size, String cursor) {
    return readFromEligibleReplica(
        "owner_list", () -> eventService.listOwner(actor, status, size, cursor));
  }

  public EventViews.Page<EventViews.Summary> listAdmin(String status, int size, String cursor) {
    return readFromEligibleReplica(
        "admin_list", () -> eventService.listAdmin(status, size, cursor));
  }

  public EventViews.Page<CancellationRepository.Request> cancellationHistory(
      UUID eventId, String actor, boolean owner, String status, int size, String cursor) {
    return readFromEligibleReplica(
        "cancellation_history",
        () -> cancellationTransactions.history(eventId, actor, owner, status, size, cursor));
  }

  private <T> T readFromEligibleReplica(String operation, Supplier<T> query) {
    // Không đổi nguồn trong transaction caller, kể cả chỉ mới có synchronization.
    if (TransactionSynchronizationManager.isActualTransactionActive()
        || TransactionSynchronizationManager.isSynchronizationActive()) return query.get();
    var template = transactionProvider.getIfAvailable();
    var health = healthProvider.getIfAvailable();
    if (template == null || health == null || !health.tryBeginRead()) {
      metrics.counter("event.read.source", "operation", operation, "source", "writer").increment();
      return query.get();
    }
    try {
      metrics.counter("event.read.source", "operation", operation, "source", "reader").increment();
      return template.execute(status -> query.get());
    } catch (RuntimeException failure) {
      // Đã thử reader thì propagate; không chạy query lần hai trên writer.
      health.invalidate();
      metrics.counter("event.read.replica.attempt.error", "operation", operation).increment();
      throw failure;
    } finally {
      // execute chỉ kết thúc sau commit/rollback; không trả permit trong callback.
      health.finishRead();
    }
  }
}
