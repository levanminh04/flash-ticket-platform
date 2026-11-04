package com.flashticket.event_service.application;

import com.flashticket.event_service.api.*;
import com.flashticket.event_service.domain.ContractRules;
import com.flashticket.event_service.integration.EventSummaries;
import com.flashticket.event_service.persistence.CancellationRepository;
import com.flashticket.event_service.web.EventProblem;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CancellationService {
  private final EventService events;
  private final EventSummaries summaries;
  private final CancellationTransactions transactions;

  public CancellationService(
      EventService events, EventSummaries summaries, CancellationTransactions transactions) {
    this.events = events;
    this.summaries = summaries;
    this.transactions = transactions;
  }

  public EventViews.Owner direct(UUID id, String actor, String expected) {
    var event = events.readOwned(id, actor);
    if ("CANCELLED".equals(event.status())) return event;
    version(event, expected);
    var summary = summaries.financial(id, actor, true);
    if (Boolean.TRUE.equals(summary.hasCollectedOrders()))
      throw EventProblem.conflict("CANCELLATION_REQUEST_REQUIRED");
    return transactions.cancel(
        id, actor, expected, true, ContractRules.instant(summary.asOf(), "asOf"));
  }

  public CancellationRepository.Request request(
      UUID id, String actor, CancellationCommands.Request input) {
    var event = events.readOwned(id, actor);
    if ("CANCELLED".equals(event.status())) throw EventProblem.conflict("EVENT_ALREADY_CANCELLED");
    version(event, input.rowVersion());
    var summary = summaries.financial(id, actor, true);
    if (!Boolean.TRUE.equals(summary.hasCollectedOrders()))
      throw EventProblem.conflict("DIRECT_CANCELLATION_AVAILABLE");
    return transactions.request(id, actor, input, ContractRules.instant(summary.asOf(), "asOf"));
  }

  private static void version(EventViews.Owner event, String expected) {
    if (Long.parseLong(event.rowVersion()) != ContractRules.unsigned(expected, "rowVersion", true))
      throw EventProblem.conflict("STALE_EVENT_VERSION");
  }
}
