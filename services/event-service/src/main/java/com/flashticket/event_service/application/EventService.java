package com.flashticket.event_service.application;

import com.flashticket.event_service.api.EventCommands;
import com.flashticket.event_service.api.EventViews;
import com.flashticket.event_service.domain.ContractRules;
import com.flashticket.event_service.domain.EventData;
import com.flashticket.event_service.messaging.EventSnapshots;
import com.flashticket.event_service.messaging.OutboxStore;
import com.flashticket.event_service.observability.EventAudit;
import com.flashticket.event_service.persistence.EventRepository;
import com.flashticket.event_service.web.EventProblem;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
  private static final Duration HOME_DISCOVERY_TTL = Duration.ofSeconds(30);

  private record CachedHomeDiscovery(EventViews.HomeDiscovery data, Instant expiresAt) {}

  private final EventRepository repository;
  private final OutboxStore outbox;
  private final EventAudit audit;
  private volatile CachedHomeDiscovery homeDiscoveryCache;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentionally shares Spring-managed infrastructure; no dependency accessor is exposed.")
  public EventService(EventRepository repository, OutboxStore outbox, EventAudit audit) {
    this.repository = repository;
    this.outbox = outbox;
    this.audit = audit;
  }

  @Transactional
  public EventViews.Owner create(String actor, EventCommands.Draft input) {
    var draft = ContractRules.draft(input, null);
    repository.references(draft);
    UUID id = UUID.randomUUID();
    repository.insert(id, actor, draft);
    var result = load(id, false);
    audit.committed("save-draft", null, result, actor);
    return EventViews.owner(result);
  }

  @Transactional
  public EventViews.Owner save(UUID id, String actor, EventCommands.Save input) {
    var event = owned(id, actor, true);
    state(event, "DRAFT");
    version(event, input.rowVersion());
    var draft = ContractRules.draft(input.draft(), event);
    repository.references(draft);
    repository.update(event, draft);
    var result = load(id, false);
    audit.committed("save-draft", event.status(), result, actor);
    return EventViews.owner(result);
  }

  @Transactional
  public EventViews.Owner submit(UUID id, String actor, String expected) {
    var event = owned(id, actor, true);
    state(event, "DRAFT");
    version(event, expected);
    if (event.ticketTypes().isEmpty()) throw EventProblem.invalid("ticketTypes");
    if (event.venueId() == null) throw EventProblem.invalid("venueId");
    if (event.shortDescription() == null || event.shortDescription().isBlank())
      throw EventProblem.invalid("shortDescription");
    if ("SEAT_MAP".equals(event.mode()))
      com.flashticket.event_service.domain.SeatMapRules.submittable(event);
    repository.transition(event, "PENDING_APPROVAL", null, null);
    var next = load(id, false);
    audit.committed("submit", event.status(), next, actor);
    return EventViews.owner(next);
  }

  @Transactional
  public EventViews.Owner returnToDraft(UUID id, String admin, String expected) {
    var event = load(id, true);
    state(event, "PENDING_APPROVAL");
    version(event, expected);
    ContractRules.text(admin, "admin");
    repository.transition(event, "DRAFT", null, null);
    var next = load(id, false);
    audit.committed("return-to-draft", event.status(), next, admin);
    return EventViews.owner(next);
  }

  @Transactional
  public EventViews.Owner approve(UUID id, String admin, EventCommands.Approval input) {
    var event = load(id, true);
    state(event, "PENDING_APPROVAL");
    version(event, input.rowVersion());
    repository.transition(
        event, "APPROVED", ContractRules.fee(input.feeRate()), ContractRules.text(admin, "admin"));
    var next = load(id, false);
    audit.committed("approve", event.status(), next, admin);
    return EventViews.owner(next);
  }

  @Transactional
  public EventViews.Owner publish(UUID id, String actor, String expected) {
    var event = owned(id, actor, true);
    if ("PUBLISHED".equals(event.status())) return EventViews.owner(event);
    state(event, "APPROVED");
    version(event, expected);
    repository.transition(event, "PUBLISHED", null, null);
    var next = load(id, false);
    // Defer delivery until publication while preserving the fee version fixed at approval.
    outbox.append(event, EventSnapshots.FINANCE, EventSnapshots.finance(event));
    outbox.append(next, EventSnapshots.SALES, EventSnapshots.sales(next));
    outbox.append(next, EventSnapshots.ACCESS, EventSnapshots.access(next));
    audit.committed("publish", event.status(), next, actor);
    homeDiscoveryCache = null;
    return EventViews.owner(next);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
  public EventViews.HomeDiscovery homeDiscovery() {
    Instant now = Instant.now();
    CachedHomeDiscovery cached = homeDiscoveryCache;
    if (cached != null && now.isBefore(cached.expiresAt())) {
      return cached.data();
    }
    synchronized (this) {
      cached = homeDiscoveryCache;
      if (cached != null && now.isBefore(cached.expiresAt())) {
        return cached.data();
      }
      EventViews.HomeDiscovery fresh = repository.homeDiscovery();
      homeDiscoveryCache = new CachedHomeDiscovery(fresh, now.plus(HOME_DISCOVERY_TTL));
      return fresh;
    }
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public EventViews.Owner readOwned(UUID id, String actor) {
    return EventViews.owner(owned(id, actor, false));
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public EventViews.Owner readAdmin(UUID id) {
    return EventViews.owner(load(id, false));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
  public EventViews.Public readPublic(UUID id) {
    var event = repository.find(id, false).orElseThrow(EventProblem::missing);
    if (!"PUBLISHED".equals(event.status())) throw EventProblem.missing();
    supportedMode(event);
    return EventViews.publicView(event);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
  public EventViews.Page<EventViews.Summary> listOwner(
      String actor, String status, int size, String cursor) {
    return page(actor, status, null, null, size, cursor, false, EventViews::summary, false);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
  public EventViews.Page<EventViews.Summary> listAdmin(String status, int size, String cursor) {
    return page(null, status, null, null, size, cursor, false, EventViews::summary, false);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
  public EventViews.Page<EventViews.Public> listPublic(
      String query, UUID category, int size, String cursor) {
    return listPublic(
        query, category, null, null, null, null, false, size, cursor, EventViews::publicView, true);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
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
      boolean compact) {
    return listPublic(
        query, category, from, to, maxPrice, venue, includePast, size, cursor, compact, !compact);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
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
    if (compact) {
      return listPublic(
          query,
          category,
          from,
          to,
          maxPrice,
          venue,
          includePast,
          size,
          cursor,
          EventViews::compactPublicView,
          false);
    }
    return listPublic(
        query,
        category,
        from,
        to,
        maxPrice,
        venue,
        includePast,
        size,
        cursor,
        EventViews::publicView,
        includeSeatMap);
  }

  private <T> EventViews.Page<T> listPublic(
      String query,
      UUID category,
      LocalDate from,
      LocalDate to,
      BigDecimal maxPrice,
      String venue,
      boolean includePast,
      int size,
      String cursor,
      Function<EventData, T> mapper,
      boolean includeSeatMap) {
    if (from != null && to != null && from.isAfter(to)) {
      throw EventProblem.invalid("dateRange");
    }
    if (maxPrice != null && maxPrice.signum() < 0) {
      throw EventProblem.invalid("maxPrice");
    }
    return page(
        null,
        null,
        query,
        category,
        from,
        to,
        maxPrice,
        venue,
        includePast,
        size,
        cursor,
        true,
        mapper,
        true,
        includeSeatMap);
  }

  @Transactional(readOnly = true, timeout = 5)
  public List<EventViews.Lookup> categories() {
    return repository.categories();
  }

  @Transactional(readOnly = true, timeout = 5)
  public List<EventViews.Venue> venues() {
    return repository.venues();
  }

  private <T> EventViews.Page<T> page(
      String actor,
      String status,
      String query,
      UUID category,
      int size,
      String cursor,
      boolean published,
      Function<EventData, T> mapper,
      boolean children) {
    return page(
        actor, status, query, category, null, null, null, null, true, size, cursor, published,
        mapper, children, true);
  }

  private <T> EventViews.Page<T> page(
      String actor,
      String status,
      String query,
      UUID category,
      LocalDate from,
      LocalDate to,
      BigDecimal maxPrice,
      String venue,
      boolean includePast,
      int size,
      String cursor,
      boolean published,
      Function<EventData, T> mapper,
      boolean children,
      boolean includeSeatMap) {
    if (size < 1 || size > 100) throw new EventProblem(400, "INVALID_PAGE_SIZE", "size");
    if (status != null
        && !Set.of("DRAFT", "PENDING_APPROVAL", "APPROVED", "PUBLISHED", "CANCELLED")
            .contains(status)) throw new EventProblem(400, "INVALID_EVENT_STATUS", "status");
    Instant before = null;
    UUID id = null;
    if (cursor != null) {
      try {
        var fields =
            new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                .split("\\|", -1);
        if (fields.length != 2) throw new IllegalArgumentException();
        before = Instant.parse(fields[0]);
        id = UUID.fromString(fields[1]);
      } catch (IllegalArgumentException | java.time.DateTimeException ex) {
        throw new EventProblem(400, "INVALID_CURSOR", "cursor");
      }
    }
    var rows =
        repository.list(
            actor,
            status,
            query,
            category,
            from,
            to,
            maxPrice,
            venue,
            includePast,
            before,
            id,
            size + 1,
            published);
    boolean more = rows.size() > size;
    var selected = rows.subList(0, Math.min(size, rows.size()));
    String next = null;
    if (more) {
      var last = selected.getLast();
      Instant cursorTime = published ? last.startsAt() : last.createdAt();
      next =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString((cursorTime + "|" + last.id()).getBytes(StandardCharsets.UTF_8));
    }
    var values = children ? repository.withChildren(selected, includeSeatMap) : selected;
    return new EventViews.Page<>(values.stream().map(mapper).toList(), next);
  }

  private EventData owned(UUID id, String actor, boolean lock) {
    var event = repository.find(id, lock).orElseThrow(EventProblem::missing);
    if (!event.organizer().equals(actor)) throw EventProblem.missing();
    supportedMode(event);
    return event;
  }

  private EventData load(UUID id, boolean lock) {
    var event = repository.find(id, lock).orElseThrow(EventProblem::missing);
    supportedMode(event);
    return event;
  }

  private static void supportedMode(EventData event) {
    if (!Set.of("QUANTITY", "SEAT_MAP").contains(event.mode()))
      throw new EventProblem(422, "SALES_MODE_NOT_IMPLEMENTED", "mode");
  }

  private static void version(EventData event, String expected) {
    if (event.rowVersion() != ContractRules.unsigned(expected, "rowVersion", true))
      throw EventProblem.conflict("STALE_EVENT_VERSION");
  }

  private static void state(EventData event, String required) {
    if (!required.equals(event.status())) throw EventProblem.conflict("INVALID_EVENT_STATE");
  }
}
