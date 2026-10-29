package com.flashticket.event_service.application;

import com.flashticket.event_service.api.*;
import com.flashticket.event_service.domain.*;
import com.flashticket.event_service.integration.EventReadProperties;
import com.flashticket.event_service.messaging.*;
import com.flashticket.event_service.observability.EventAudit;
import com.flashticket.event_service.persistence.*;
import com.flashticket.event_service.web.EventProblem;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CancellationTransactions {
  private final EventRepository events;
  private final CancellationRepository requests;
  private final OutboxStore outbox;
  private final EventAudit audit;
  private final EventReadProperties properties;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentionally shares Spring-managed beans; no dependency accessor is exposed.")
  public CancellationTransactions(
      EventRepository events,
      CancellationRepository requests,
      OutboxStore outbox,
      EventAudit audit,
      EventReadProperties properties) {
    this.events = events;
    this.requests = requests;
    this.outbox = outbox;
    this.audit = audit;
    this.properties = properties;
  }

  @Transactional
  public EventViews.Owner cancel(UUID id, String actor, String expected, boolean owner) {
    return cancel(id, actor, expected, owner, null);
  }

  @Transactional
  public EventViews.Owner cancel(
      UUID id, String actor, String expected, boolean owner, Instant summaryAsOf) {
    var event = load(id, actor, owner);
    if ("CANCELLED".equals(event.status())) return EventViews.owner(event);
    version(event, expected);
    Instant decision = beforeStart(event);
    if (owner) summaryFresh(summaryAsOf, decision);
    if (owner && requests.pending(id)) throw EventProblem.conflict("CANCELLATION_REQUEST_PENDING");
    cancelEvent(event, actor, decision);
    if (!owner) requests.approvePending(id, actor, decision);
    return EventViews.owner(events.find(id, false).orElseThrow(EventProblem::missing));
  }

  @Transactional
  public CancellationRepository.Request request(
      UUID id, String actor, CancellationCommands.Request input, Instant summaryAsOf) {
    var event = load(id, actor, true);
    version(event, input.rowVersion());
    if ("CANCELLED".equals(event.status())) throw EventProblem.conflict("EVENT_ALREADY_CANCELLED");
    Instant at = beforeStart(event);
    summaryFresh(summaryAsOf, at);
    if (requests.pending(id)) throw EventProblem.conflict("CANCELLATION_REQUEST_PENDING");
    UUID requestId = UUID.randomUUID();
    requests.create(requestId, id, actor, ContractRules.text(input.reason(), "reason"), at);
    requests.touch(id, event.rowVersion());
    audit.committed(
        "request-cancellation",
        event.status(),
        events.find(id, false).orElseThrow(EventProblem::missing),
        actor);
    return requests.find(requestId, false);
  }

  @Transactional
  public CancellationRepository.Request review(
      UUID requestId, String admin, String expected, boolean approve, String reason) {
    var initial = requests.find(requestId, false);
    var event = load(initial.eventId(), admin, false);
    var request = requests.find(requestId, true);
    if (approve && "APPROVED".equals(request.status()) && "CANCELLED".equals(event.status()))
      return request;
    if (!"PENDING".equals(request.status()))
      throw EventProblem.conflict("INVALID_CANCELLATION_REQUEST_STATE");
    version(event, expected);
    if ("CANCELLED".equals(event.status())) throw EventProblem.conflict("EVENT_ALREADY_CANCELLED");
    Instant at = approve ? beforeStart(event) : requests.now();
    String normalizedReason = reason == null ? null : ContractRules.text(reason, "reason");
    requests.review(requestId, approve ? "APPROVED" : "REJECTED", admin, normalizedReason, at);
    if (approve) cancelEvent(event, admin, at);
    else {
      requests.touch(event.id(), event.rowVersion());
      audit.committed(
          "reject-cancellation",
          event.status(),
          events.find(event.id(), false).orElseThrow(EventProblem::missing),
          admin);
    }
    return requests.find(requestId, false);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public EventViews.Page<CancellationRepository.Request> history(
      UUID eventId, String actor, boolean owner, String status, int size, String cursor) {
    if (eventId != null) {
      var event = events.find(eventId, false).orElseThrow(EventProblem::missing);
      if (owner && !event.organizer().equals(actor)) throw EventProblem.missing();
    } else if (owner) throw EventProblem.missing();
    return requests.list(eventId, status, size, cursor);
  }

  private void cancelEvent(EventData event, String actor, Instant at) {
    events.transition(event, "CANCELLED", null, null);
    var next = events.find(event.id(), false).orElseThrow(EventProblem::missing);
    outbox.append(
        next,
        EventSnapshots.CANCELLED,
        new EventSnapshots.Cancellation(
            next.id(),
            UUID.randomUUID(),
            Long.toString(next.configVersion()),
            at,
            actor,
            EventSnapshots.sales(next),
            EventSnapshots.access(next)));
    audit.committed("cancel-event", event.status(), next, actor);
  }

  private EventData load(UUID id, String actor, boolean owner) {
    ContractRules.text(actor, "actor");
    var event = events.find(id, true).orElseThrow(EventProblem::missing);
    if (owner && !event.organizer().equals(actor)) throw EventProblem.missing();
    return event;
  }

  private Instant beforeStart(EventData event) {
    Instant at = requests.now();
    if (!at.isBefore(event.startsAt())) throw EventProblem.conflict("EVENT_ALREADY_STARTED");
    return at;
  }

  private void summaryFresh(Instant observed, Instant decision) {
    if (observed == null || observed.isBefore(decision.minus(properties.cancellationMaxAge())))
      throw new EventProblem(503, "UPSTREAM_DATA_STALE", null);
    if (observed.isAfter(decision.plus(properties.clockSkew())))
      throw new EventProblem(503, "UPSTREAM_INVALID_RESPONSE", null);
  }

  private static void version(EventData event, String expected) {
    if (event.rowVersion() != ContractRules.unsigned(expected, "rowVersion", true))
      throw EventProblem.conflict("STALE_EVENT_VERSION");
  }
}
