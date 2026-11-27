package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.flashticket.event_service.api.EventCommands;
import com.flashticket.event_service.api.EventViews;
import com.flashticket.event_service.application.EventService;
import com.flashticket.event_service.domain.ContractRules;
import com.flashticket.event_service.domain.EventData;
import com.flashticket.event_service.messaging.OutboxStore;
import com.flashticket.event_service.observability.EventAudit;
import com.flashticket.event_service.persistence.EventRepository;
import com.flashticket.event_service.web.EventProblem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShortDescriptionTests {
  private static EventData draftEvent(UUID id, String shortDescription) {
    var now = Instant.parse("2026-11-21T17:00:00Z");
    return new EventData(
        id,
        "organizer-1",
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Venue A",
        "Mega Concert",
        shortDescription,
        "Full description",
        "QUANTITY",
        "DRAFT",
        now.plusSeconds(3600),
        now.plusSeconds(7200),
        now,
        now.plusSeconds(3600),
        4,
        null,
        null,
        null,
        1L,
        0L,
        now,
        now,
        List.of(),
        List.of(
            new EventData.TicketType(
                UUID.randomUUID(), "Standard", new BigDecimal("100000"), 10L, "#CC9900")),
        List.of(),
        null);
  }

  @Test
  void contractRulesAllowEmptyAndEnforce300CharacterLimit() {
    assertThat(ContractRules.shortDescription(null)).isEmpty();
    assertThat(ContractRules.shortDescription("   ")).isEmpty();
    assertThat(ContractRules.shortDescription("  Gala Concert 2026  "))
        .isEqualTo("Gala Concert 2026");

    String max300 = "a".repeat(300);
    assertThat(ContractRules.shortDescription("  " + max300 + "  ")).hasSize(300);

    String tooLong = "a".repeat(301);
    assertThatThrownBy(() -> ContractRules.shortDescription(tooLong))
        .isInstanceOf(EventProblem.class)
        .satisfies(ex -> assertThat(((EventProblem) ex).field()).isEqualTo("shortDescription"));
  }

  @Test
  void draftAllowsEmptyShortDescriptionButSubmitRequiresIt() {
    var repo = mock(EventRepository.class);
    var outbox = mock(OutboxStore.class);
    var audit = mock(EventAudit.class);
    var service = new EventService(repo, outbox, audit);

    UUID id = UUID.randomUUID();
    var emptyDraft = draftEvent(id, "");
    when(repo.find(id, true)).thenReturn(Optional.of(emptyDraft));

    assertThatThrownBy(() -> service.submit(id, "organizer-1", "0"))
        .isInstanceOf(EventProblem.class)
        .satisfies(ex -> assertThat(((EventProblem) ex).field()).isEqualTo("shortDescription"));
    verify(repo, never()).transition(any(), any(), any(), any());

    var completeDraft = draftEvent(id, "Đêm nhạc hoành tráng tại Hà Nội");
    when(repo.find(id, true)).thenReturn(Optional.of(completeDraft));
    when(repo.find(id, false)).thenReturn(Optional.of(completeDraft));

    var submitted = service.submit(id, "organizer-1", "0");
    assertThat(submitted.shortDescription()).isEqualTo("Đêm nhạc hoành tráng tại Hà Nội");
    verify(repo).transition(completeDraft, "PENDING_APPROVAL", null, null);
  }

  @Test
  void viewsExposeShortDescriptionAcrossOwnerPublicAndCompactContracts() {
    var event = draftEvent(UUID.randomUUID(), "Tóm tắt 2 dòng cho sự kiện");
    assertThat(EventViews.owner(event).shortDescription()).isEqualTo("Tóm tắt 2 dòng cho sự kiện");
    assertThat(EventViews.publicView(event).shortDescription())
        .isEqualTo("Tóm tắt 2 dòng cho sự kiện");
    assertThat(EventViews.compactPublicView(event).shortDescription())
        .isEqualTo("Tóm tắt 2 dòng cho sự kiện");
  }

  @Test
  void draftCommandAllowsOmittedOrNullShortDescription() {
    var cmd =
        new EventCommands.Draft(
            "Title",
            "Desc",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "QUANTITY",
            "2027-01-01T12:00:00Z",
            "2027-01-01T15:00:00Z",
            "2026-12-01T00:00:00Z",
            "2027-01-01T14:00:00Z",
            4,
            List.of(),
            List.of(),
            List.of());
    var config = ContractRules.draft(cmd, null);
    assertThat(config.shortDescription()).isEmpty();
  }
}
