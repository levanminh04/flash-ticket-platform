package com.flashticket.event_service.application;

import com.flashticket.event_service.api.*;
import com.flashticket.event_service.domain.*;
import com.flashticket.event_service.observability.EventAudit;
import com.flashticket.event_service.persistence.*;
import com.flashticket.event_service.web.EventProblem;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class SeatMapService {
  private final EventRepository events;
  private final SeatMapRepository maps;
  private final EventAudit audit;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentionally shares Spring-managed beans; no dependency accessor is exposed.")
  public SeatMapService(EventRepository events, SeatMapRepository maps, EventAudit audit) {
    this.events = events;
    this.maps = maps;
    this.audit = audit;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SeatMap read(UUID id, String actor) {
    var event = own(id, actor, false);
    seatMode(event);
    if (event.seatMap() == null) throw new EventProblem(404, "SEAT_MAP_NOT_CONFIGURED", null);
    return event.seatMap();
  }

  @Transactional
  public SeatMap save(UUID id, String actor, String version, SeatMap map) {
    var event = editing(id, actor, version);
    seatMode(event);
    SeatMapRules.validate(map, event.ticketTypes());
    maps.save(event, map);
    var next = events.find(id, false).orElseThrow(EventProblem::missing);
    audit.committed("save-seat-map", event.status(), next, actor);
    return next.seatMap();
  }

  @Transactional
  public EventViews.Owner types(UUID id, String actor, EventCommands.SeatTypes input) {
    var event = editing(id, actor, input.rowVersion());
    seatMode(event);
    if (event.seatMap() == null) throw EventProblem.invalid("seatMap");
    var existing = new HashSet<UUID>();
    event.ticketTypes().forEach(t -> existing.add(t.id()));
    var ids = new HashSet<UUID>();
    var types = new ArrayList<EventData.TicketType>();
    for (var type : input.ticketTypes()) {
      UUID typeId = type.ticketTypeId() == null ? UUID.randomUUID() : type.ticketTypeId();
      if (!ids.add(typeId) || (type.ticketTypeId() != null && !existing.contains(typeId)))
        throw EventProblem.invalid("ticketTypeId");
      if (!"VND".equals(type.currency())) throw EventProblem.invalid("currency");
      SeatMapRules.color(type.colorCode());
      types.add(
          new EventData.TicketType(
              typeId,
              ContractRules.text(type.name(), "name"),
              ContractRules.money(type.unitPrice(), "unitPrice"),
              null,
              type.colorCode(),
              type.sectorId()));
    }
    SeatMapRules.validate(event.seatMap(), types);
    maps.types(event, types);
    var next = events.find(id, false).orElseThrow(EventProblem::missing);
    audit.committed("save-seat-types", event.status(), next, actor);
    return EventViews.owner(next);
  }

  @Transactional
  public EventViews.Owner mode(UUID id, String actor, EventCommands.Mode input) {
    var event = editing(id, actor, input.rowVersion());
    if (!Set.of("QUANTITY", "SEAT_MAP").contains(input.mode())) throw EventProblem.invalid("mode");
    if (event.mode().equals(input.mode())) return EventViews.owner(event);
    if (!Boolean.TRUE.equals(input.confirmDeleteIncompatibleConfiguration()))
      throw EventProblem.conflict("MODE_CHANGE_CONFIRMATION_REQUIRED");
    maps.mode(event, input.mode());
    var next = events.find(id, false).orElseThrow(EventProblem::missing);
    audit.committed("change-sales-mode", event.status(), next, actor);
    return EventViews.owner(next);
  }

  private EventData editing(UUID id, String actor, String version) {
    var event = own(id, actor, true);
    if (!"DRAFT".equals(event.status())) throw EventProblem.conflict("INVALID_EVENT_STATE");
    if (event.rowVersion() != ContractRules.unsigned(version, "rowVersion", true))
      throw EventProblem.conflict("STALE_EVENT_VERSION");
    return event;
  }

  private EventData own(UUID id, String actor, boolean lock) {
    var event = events.find(id, lock).orElseThrow(EventProblem::missing);
    if (!event.organizer().equals(actor)) throw EventProblem.missing();
    return event;
  }

  private static void seatMode(EventData event) {
    if (!"SEAT_MAP".equals(event.mode())) throw EventProblem.conflict("INCOMPATIBLE_SALES_MODE");
  }
}
