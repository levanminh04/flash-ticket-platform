package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.flashticket.event_service.api.EventController;
import com.flashticket.event_service.api.EventViews;
import com.flashticket.event_service.application.EventService;
import com.flashticket.event_service.domain.EventData;
import com.flashticket.event_service.messaging.OutboxStore;
import com.flashticket.event_service.observability.EventAudit;
import com.flashticket.event_service.persistence.EventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HomeDiscoveryTests {

  private static EventViews.HomeDiscovery sampleDiscovery(long total) {
    var catId = UUID.randomUUID();
    var card =
        new EventViews.HomeCard(
            UUID.randomUUID(),
            "org-1",
            "Live Concert",
            catId,
            Instant.parse("2026-12-01T12:00:00Z"),
            Instant.parse("2026-12-01T15:00:00Z"),
            "Stadium",
            "https://cdn.example/banner.jpg",
            "500000",
            "VND");
    return new EventViews.HomeDiscovery(
        total,
        List.of(card),
        List.of(card),
        List.of(new EventViews.HomeCategorySection(catId, "Music", total, List.of(card))),
        List.of(new EventViews.HomeOrganizerCount("org-1", total)));
  }

  private static EventData approvedEvent(UUID id, String actor) {
    var now = Instant.parse("2026-11-21T17:00:00Z");
    return new EventData(
        id,
        actor,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Venue A",
        "Approved Concert",
        "Short",
        "Full",
        "QUANTITY",
        "APPROVED",
        now.plusSeconds(3600),
        now.plusSeconds(7200),
        now,
        now.plusSeconds(3600),
        4,
        new BigDecimal("0.05"),
        "admin-1",
        now,
        1L,
        2L,
        now,
        now,
        List.of(),
        List.of(
            new EventData.TicketType(
                UUID.randomUUID(), "GA", new BigDecimal("200000"), 100L, "#CC9900")),
        List.of(),
        null);
  }

  @Test
  void cachesHomeDiscoveryWithinTtlAndEvictsOnPublish() {
    var repo = mock(EventRepository.class);
    var outbox = mock(OutboxStore.class);
    var audit = mock(EventAudit.class);
    var first = sampleDiscovery(10);
    var second = sampleDiscovery(11);
    when(repo.homeDiscovery()).thenReturn(first, second);

    var service = new EventService(repo, outbox, audit);
    var controller = new EventController(service);

    assertThat(controller.homeDiscovery()).isEqualTo(first);
    assertThat(controller.homeDiscovery()).isEqualTo(first);
    verify(repo, times(1)).homeDiscovery();

    UUID id = UUID.randomUUID();
    var approved = approvedEvent(id, "org-1");
    var published =
        new EventData(
            approved.id(),
            approved.organizer(),
            approved.categoryId(),
            approved.venueId(),
            approved.venueName(),
            approved.title(),
            approved.shortDescription(),
            approved.description(),
            approved.mode(),
            "PUBLISHED",
            approved.startsAt(),
            approved.endsAt(),
            approved.saleStartsAt(),
            approved.saleEndsAt(),
            approved.purchaseLimit(),
            approved.feeRate(),
            approved.approvedBy(),
            approved.approvedAt(),
            approved.configVersion(),
            3L,
            approved.createdAt(),
            approved.updatedAt(),
            approved.images(),
            approved.ticketTypes(),
            approved.promotions(),
            null);
    when(repo.find(id, true)).thenReturn(Optional.of(approved));
    when(repo.find(id, false)).thenReturn(Optional.of(published));

    service.publish(id, "org-1", "2");
    verify(outbox, times(3)).append(any(), any(), any());

    assertThat(controller.homeDiscovery()).isEqualTo(second);
    verify(repo, times(2)).homeDiscovery();
  }
}
