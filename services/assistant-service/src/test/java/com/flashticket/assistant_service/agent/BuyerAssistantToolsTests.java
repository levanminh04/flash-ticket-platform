package com.flashticket.assistant_service.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.assistant_service.client.BuyerApiClient;
import com.flashticket.assistant_service.dto.*;
import com.flashticket.assistant_service.exception.AssistantProblem;
import com.flashticket.assistant_service.service.RedisConversationStore;
import dev.langchain4j.agent.tool.Tool;
import jakarta.validation.Validation;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class BuyerAssistantToolsTests {
  @Test
  void exposesOnlyReadToolsAndCreatesVerifiedQuantityDraft() {
    var names = Arrays.stream(BuyerAssistantTools.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(Tool.class)).map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet());
    assertEquals(Set.of("searchEvents", "getEvent", "getAvailability", "getMyOrders", "getMyOrder", "prepareQuantityPurchase"), names);
    var api = mock(BuyerApiClient.class);
    var store = mock(RedisConversationStore.class);
    when(store.isLeaseCurrent(anyString(), any(), any())).thenReturn(true);
    var now = Instant.parse("2026-11-18T00:00:00Z");
    var jwt = Jwt.withTokenValue("fixture-token").header("alg", "RS256").subject("buyer").issuer("https://issuer.test").expiresAt(now.plusSeconds(300)).build();
    var eventId = UUID.randomUUID(); var ticketId = UUID.randomUUID();
    var event = new EventView(); event.setEventId(eventId); event.setTitle("Concert"); event.setMode("QUANTITY");
    var ticket = new EventView.TicketType(); ticket.setTicketTypeId(ticketId); ticket.setName("Standard"); ticket.setUnitPrice("125000.50"); ticket.setCurrency("VND");
    event.setTicketTypes(List.of(ticket));
    var stock = new AvailabilityReadView.TicketStock(); stock.setTicketTypeId(ticketId); stock.setAvailable("5");
    var availability = new AvailabilityReadView(); availability.setEventId(eventId); availability.setAsOf(now); availability.setTicketTypes(List.of(stock));
    when(api.getEvent(jwt, eventId)).thenReturn(event); when(api.getAvailability(jwt, eventId)).thenReturn(availability);
    var line = new QuantityLine(); line.setTicketTypeId(ticketId); line.setQuantity(2);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var tools = new BuyerAssistantTools(api, store, factory.getValidator(), Clock.fixed(now, ZoneOffset.UTC), jwt, "owner", UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(45));
      var draft = tools.prepareQuantityPurchase(eventId, List.of(line));
      assertEquals("250001.00", draft.getEstimatedTotal()); assertEquals(now.plusSeconds(600), draft.getExpiresAt());
      assertEquals(eventId, draft.getEventId()); assertNotNull(draft.getDraftId()); assertSame(draft, tools.getPurchaseDraft());
      assertThrows(AssistantProblem.class, () -> tools.prepareQuantityPurchase(eventId, List.of(line)));
      verify(api).getEvent(jwt, eventId); verify(api).getAvailability(jwt, eventId); verifyNoMoreInteractions(api);
    }
  }

  @Test
  void rejectsInvalidSelectionAndStaleExecution() {
    var api = mock(BuyerApiClient.class); var store = mock(RedisConversationStore.class);
    var now = Instant.parse("2026-11-18T00:00:00Z"); var clock = Clock.fixed(now, ZoneOffset.UTC);
    var jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("buyer").expiresAt(now.plusSeconds(300)).build();
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var tools = new BuyerAssistantTools(api, store, factory.getValidator(), clock, jwt, "owner", UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(45));
      assertEquals("LEASE_LOST", assertThrows(AssistantProblem.class, () -> tools.getEvent(UUID.randomUUID())).getCode());
      when(store.isLeaseCurrent(anyString(), any(), any())).thenReturn(true);
      var invalid = new QuantityLine(); invalid.setTicketTypeId(UUID.randomUUID()); invalid.setQuantity(0);
      assertEquals("INVALID_TOOL_INPUT", assertThrows(AssistantProblem.class, () -> tools.prepareQuantityPurchase(UUID.randomUUID(), List.of(invalid))).getCode());
      invalid.setQuantity(1);
      assertEquals("DUPLICATE_TICKET_TYPE", assertThrows(AssistantProblem.class, () -> tools.prepareQuantityPurchase(UUID.randomUUID(), List.of(invalid, invalid))).getCode());
      assertThrows(AssistantProblem.class, () -> tools.getMyOrders(-1, ""));
      assertThrows(AssistantProblem.class, () -> tools.getMyOrders(0, "ADMIN"));
      assertThrows(AssistantProblem.class, () -> tools.searchEvents(null));
      assertEquals("TOOL_EXECUTION_LIMIT", assertThrows(AssistantProblem.class, () -> tools.getEvent(UUID.randomUUID())).getCode());
      var expired = new BuyerAssistantTools(api, store, factory.getValidator(), Clock.offset(clock, Duration.ofSeconds(301)), jwt, "owner", UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(500));
      assertEquals("UNAUTHENTICATED", assertThrows(AssistantProblem.class, () -> expired.getEvent(UUID.randomUUID())).getCode());
      var late = new BuyerAssistantTools(api, store, factory.getValidator(), clock, jwt, "owner", UUID.randomUUID(), UUID.randomUUID(), now);
      assertEquals("TURN_DEADLINE_EXCEEDED", assertThrows(AssistantProblem.class, () -> late.getEvent(UUID.randomUUID())).getCode());
      verifyNoInteractions(api);
    }
  }
}
