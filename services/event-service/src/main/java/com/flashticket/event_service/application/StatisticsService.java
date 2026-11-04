package com.flashticket.event_service.application;

import com.flashticket.event_service.domain.ContractRules;
import com.flashticket.event_service.integration.EventSummaries;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StatisticsService {
  private final EventService events;
  private final EventSummaries summaries;

  public StatisticsService(EventService events, EventSummaries summaries) {
    this.events = events;
    this.summaries = summaries;
  }

  public record Statistics(
      UUID eventId,
      Instant asOf,
      Instant salesAsOf,
      Instant financeAsOf,
      String purchasedTicketCount,
      String currency,
      String validGross,
      String validRefunds,
      String feeAmount,
      String netAmount) {}

  public Statistics read(UUID id, String actor) {
    var event = events.readOwned(id, actor);
    // Both owner transactions have finished before any external HTTP call starts.
    var sales = summaries.sales(id, event.organizerSubject());
    var finance = summaries.financial(id, event.organizerSubject(), false);
    Instant salesAt = ContractRules.instant(sales.asOf(), "asOf"),
        financeAt = ContractRules.instant(finance.asOf(), "asOf");
    return new Statistics(
        id,
        salesAt.isBefore(financeAt) ? salesAt : financeAt,
        salesAt,
        financeAt,
        sales.purchasedTicketCount(),
        finance.currency(),
        finance.validGross(),
        finance.validRefunds(),
        finance.feeAmount(),
        finance.netAmount());
  }
}
