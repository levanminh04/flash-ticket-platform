package com.flashticket.assistant_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flashticket.assistant_service.AssistantWorkspace;
import com.flashticket.assistant_service.client.AssistantApiClient;
import com.flashticket.assistant_service.dto.*;
import com.flashticket.assistant_service.exception.AssistantProblem;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

class AssistantConversationServiceTests {
  private final Instant now = Instant.parse("2026-11-18T00:00:00Z");
  private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
  private final Jwt jwt = Jwt.withTokenValue("fixture-token").header("alg", "RS256").issuer("https://issuer.test").subject("buyer").expiresAt(now.plusSeconds(300)).build();
  private final RedisConversationStore store = mock(RedisConversationStore.class);
  private final AssistantApiClient api = mock(AssistantApiClient.class);
  private final ChatModel model = mock(ChatModel.class);
  private final Validator validator = mock(Validator.class);

  @Test
  void commitsOnlySuccessfulWholeTurn() {
    try (var executor = Executors.newSingleThreadExecutor()) {
      var service = new AssistantConversationService(store, api, model, executor, clock, validator);
      var state = new ConversationState(); state.setConversationId(UUID.randomUUID()); state.setVersion(UUID.randomUUID()); state.setContextJson("[]"); state.setMessages(List.of());
      var request = new ChatRequest(); request.setConversationId(state.getConversationId()); request.setVersion(state.getVersion()); request.setRequestId(UUID.randomUUID()); request.setMessage("Tôi có đơn nào?");
      when(store.beginTurn(anyString(), any(), anyString(), any())).thenReturn(state);
      when(store.isLeaseCurrent(anyString(), any(), any())).thenReturn(true);
      var order = new OrderReadView(); order.setOrderId(UUID.randomUUID());
      var page = new OrderReadPage(); page.setItems(List.of(order));
      when(api.getMyOrders(jwt, 0, "")).thenReturn(page);
      var tool = ToolExecutionRequest.builder().id("orders-1").name("getMyOrders").arguments("{\"page\":0,\"state\":\"\"}").build();
      var toolCall = dev.langchain4j.model.chat.response.ChatResponse.builder().aiMessage(AiMessage.from(tool)).build();
      when(model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenReturn(toolCall,
          dev.langchain4j.model.chat.response.ChatResponse.builder().aiMessage(AiMessage.from("Kết quả tra đơn")).build());
      when(store.commitTurn(anyString(), any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(4));
      var response = service.sendMessage(jwt, request, AssistantWorkspace.BUYER); assertEquals("Kết quả tra đơn", response.getMessage()); assertNotEquals(state.getVersion(), response.getVersion());
      var committed = ArgumentCaptor.forClass(ConversationState.class);
      verify(store).commitTurn(anyString(), eq(state.getConversationId()), eq(state.getVersion()), any(), committed.capture());
      var next = committed.getValue();
      assertEquals(List.of("user", "assistant"), next.getMessages().stream().map(TranscriptMessage::getRole).toList());
      assertEquals(List.of(order), response.getOrders()); assertSame(response, next.getLastResponse());
      assertNull(next.getMessages().getFirst().getOrders()); assertEquals(response.getOrders(), next.getMessages().getLast().getOrders());
      next.setExpiresAtEpochMillis(now.plusSeconds(86400).toEpochMilli());
      when(store.read(anyString())).thenReturn(next);
      assertEquals(response.getOrders(), service.getConversation(jwt, AssistantWorkspace.BUYER).getMessages().getLast().getOrders());
      assertTrue(next.getContextJson().contains("Kết quả tra đơn")); assertEquals(request.getRequestId(), next.getLastRequestId());
      assertTrue(ChatMessageDeserializer.messagesFromJson(next.getContextJson()).contains(AiMessage.from("Kết quả tra đơn")));
      assertFalse(next.getContextJson().contains("\"orders\":"));
      clearInvocations(store);
      when(model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenReturn(toolCall).thenThrow(new IllegalStateException("secret-provider-body"));
      assertEquals("MODEL_TURN_FAILED", assertThrows(AssistantProblem.class, () -> service.sendMessage(jwt, request, AssistantWorkspace.BUYER)).getCode());
      verify(store, never()).commitTurn(anyString(), any(), any(), any(), any()); verify(store).release(anyString(), any());
    }
  }

  @Test
  void separatesBuyerAndOrganizerConversationsForSameJwt() {
    String buyerKey = "cfeb594e411920ba4993b662b327c7ff7925455634a9919635f03e0f24eace33";
    String organizerKey = "7002ea9b1f9c192f445df537f16b980f4912c425e9515f58ea8fac678b25f3bb";
    try (var executor = Executors.newSingleThreadExecutor()) {
      var service = new AssistantConversationService(store, api, model, executor, clock, validator);
      var buyer = new ConversationState();
      buyer.setConversationId(UUID.randomUUID());
      buyer.setMessages(List.of());
      var organizer = new ConversationState();
      organizer.setConversationId(UUID.randomUUID());
      organizer.setVersion(UUID.randomUUID());
      organizer.setMessages(List.of());
      when(store.read(buyerKey)).thenReturn(buyer);
      when(store.read(organizerKey)).thenThrow(new AssistantProblem(HttpStatus.GONE, "CONVERSATION_EXPIRED", "fixture"))
          .thenReturn(organizer);
      when(store.open(organizerKey)).thenReturn(organizer);
      assertEquals(buyer.getConversationId(), service.openConversation(jwt, AssistantWorkspace.BUYER).getConversationId());
      assertEquals(organizer.getConversationId(), service.openConversation(jwt, AssistantWorkspace.ORGANIZER).getConversationId());
      assertEquals(organizer.getConversationId(), service.getConversation(jwt, AssistantWorkspace.ORGANIZER).getConversationId());
      var reset = new ResetConversationRequest();
      reset.setConversationId(organizer.getConversationId());
      reset.setVersion(organizer.getVersion());
      when(store.reset(organizerKey, reset)).thenReturn(organizer);
      service.resetConversation(jwt, reset, AssistantWorkspace.ORGANIZER);
      verify(store).reset(organizerKey, reset);
      verify(store, never()).reset(eq(buyerKey), any());
      assertEquals(buyer.getConversationId(), service.getConversation(jwt, AssistantWorkspace.BUYER).getConversationId());
    }
  }

  @Test
  void separatesAdminFromBuyerAndOrganizerForSameJwt() {
    String buyerKey = "cfeb594e411920ba4993b662b327c7ff7925455634a9919635f03e0f24eace33";
    String organizerKey = "7002ea9b1f9c192f445df537f16b980f4912c425e9515f58ea8fac678b25f3bb";
    String adminKey = "0cd54319a8994b8aa68de740555c466facbf9f1679edb91168617f4e8140fcc6";
    try (var executor = Executors.newSingleThreadExecutor()) {
      var service = new AssistantConversationService(store, api, model, executor, clock, validator);
      var buyer = new ConversationState();
      buyer.setConversationId(UUID.randomUUID());
      buyer.setMessages(List.of());
      var organizer = new ConversationState();
      organizer.setConversationId(UUID.randomUUID());
      organizer.setMessages(List.of());
      var admin = new ConversationState();
      admin.setConversationId(UUID.randomUUID());
      admin.setVersion(UUID.randomUUID());
      admin.setMessages(List.of());
      when(store.read(buyerKey)).thenReturn(buyer);
      when(store.read(organizerKey)).thenReturn(organizer);
      when(store.read(adminKey)).thenThrow(new AssistantProblem(HttpStatus.GONE, "CONVERSATION_EXPIRED", "fixture"))
          .thenReturn(admin);
      when(store.open(adminKey)).thenReturn(admin);
      assertEquals(buyer.getConversationId(), service.openConversation(jwt, AssistantWorkspace.BUYER).getConversationId());
      assertEquals(organizer.getConversationId(), service.openConversation(jwt, AssistantWorkspace.ORGANIZER).getConversationId());
      assertEquals(admin.getConversationId(), service.openConversation(jwt, AssistantWorkspace.ADMIN).getConversationId());
      assertEquals(admin.getConversationId(), service.getConversation(jwt, AssistantWorkspace.ADMIN).getConversationId());
      var reset = new ResetConversationRequest();
      reset.setConversationId(admin.getConversationId());
      reset.setVersion(admin.getVersion());
      when(store.reset(adminKey, reset)).thenReturn(admin);
      service.resetConversation(jwt, reset, AssistantWorkspace.ADMIN);
      verify(store).reset(adminKey, reset);
      verify(store, never()).reset(eq(buyerKey), any());
      verify(store, never()).reset(eq(organizerKey), any());
      assertEquals(buyer.getConversationId(), service.getConversation(jwt, AssistantWorkspace.BUYER).getConversationId());
      assertEquals(organizer.getConversationId(), service.getConversation(jwt, AssistantWorkspace.ORGANIZER).getConversationId());
    }
  }

  @Test
  void rejectsLateResultAfterResetOrDeadline() {
    try (var executor = Executors.newSingleThreadExecutor()) {
      var state = new ConversationState(); state.setConversationId(UUID.randomUUID()); state.setVersion(UUID.randomUUID()); state.setContextJson("[]"); state.setMessages(List.of());
      var request = new ChatRequest(); request.setConversationId(state.getConversationId()); request.setVersion(state.getVersion()); request.setRequestId(UUID.randomUUID()); request.setMessage("Hello");
      when(store.beginTurn(anyString(), any(), anyString(), any())).thenReturn(state);
      when(store.isLeaseCurrent(anyString(), any(), any())).thenReturn(true);
      var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
      when(model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenAnswer(invocation -> {
        entered.countDown(); assertTrue(finish.await(5, TimeUnit.SECONDS));
        return dev.langchain4j.model.chat.response.ChatResponse.builder().aiMessage(AiMessage.from("Late answer")).build();
      });
      when(store.commitTurn(anyString(), any(), any(), any(), any())).thenThrow(new AssistantProblem(HttpStatus.CONFLICT, "LEASE_LOST", "Reset won"));
      var service = new AssistantConversationService(store, api, model, executor, clock, validator);
      try (var callers = Executors.newSingleThreadExecutor()) {
        var result = callers.submit(() -> service.sendMessage(jwt, request, AssistantWorkspace.BUYER));
        assertTrue(assertDoesNotThrow(() -> entered.await(5, TimeUnit.SECONDS)));
        var reset = new ResetConversationRequest(); reset.setConversationId(state.getConversationId()); reset.setVersion(state.getVersion());
        var fresh = new ConversationState(); fresh.setConversationId(UUID.randomUUID()); fresh.setVersion(UUID.randomUUID()); fresh.setMessages(List.of()); fresh.setExpiresAtEpochMillis(now.plusSeconds(86400).toEpochMilli());
        when(store.reset(anyString(), eq(reset))).thenReturn(fresh);
        assertEquals(fresh.getConversationId(), service.resetConversation(jwt, reset, AssistantWorkspace.BUYER).getConversationId());
        finish.countDown();
        var failure = assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
        assertEquals("LEASE_LOST", ((AssistantProblem) failure.getCause()).getCode());
      } finally { finish.countDown(); }
      clearInvocations(store);
      var movingClock = mock(Clock.class);
      when(movingClock.instant()).thenReturn(now, now, now.plusSeconds(46));
      var lateService = new AssistantConversationService(store, api, model, executor, movingClock, validator);
      assertEquals("TURN_DEADLINE_EXCEEDED", assertThrows(AssistantProblem.class, () -> lateService.sendMessage(jwt, request, AssistantWorkspace.BUYER)).getCode());
      verify(store, never()).commitTurn(anyString(), any(), any(), any(), any());
    }
  }

  @Test
  void replaysLatestRequestWithoutModelAndRejectsConflictingPayload() {
    try (var executor = Executors.newSingleThreadExecutor()) {
      var service = new AssistantConversationService(store, api, model, executor, clock, validator);
      var request = new ChatRequest(); request.setConversationId(UUID.randomUUID()); request.setVersion(UUID.randomUUID()); request.setRequestId(UUID.randomUUID()); request.setMessage("Hello");
      String hash = ReflectionTestUtils.invokeMethod(service, "requestHash", request);
      var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
      var state = mapper.readValue("{\"messages\":[{\"role\":\"assistant\"}],\"lastResponse\":{\"message\":\"Saved\"}}", ConversationState.class);
      state.setLastRequestId(request.getRequestId()); state.setLastRequestHash(hash);
      when(store.beginTurn(anyString(), any(), anyString(), any())).thenReturn(state);
      assertNull(state.getMessages().getFirst().getOrders()); assertNull(service.sendMessage(jwt, request, AssistantWorkspace.BUYER).getOrders());
      var order = new OrderReadView(); order.setOrderId(UUID.randomUUID());
      for (List<OrderReadView> orders : List.of(List.<OrderReadView>of(), List.of(order))) {
        state.getLastResponse().setOrders(orders); state.getMessages().getFirst().setOrders(orders);
        var restored = mapper.readValue(mapper.writeValueAsBytes(state), ConversationState.class);
        when(store.beginTurn(anyString(), any(), anyString(), any())).thenReturn(restored);
        var replay = service.sendMessage(jwt, request, AssistantWorkspace.BUYER);
        assertSame(restored.getLastResponse(), replay); assertEquals("Saved", replay.getMessage());
        assertEquals(orders.stream().map(OrderReadView::getOrderId).toList(), replay.getOrders().stream().map(OrderReadView::getOrderId).toList());
        assertEquals(replay.getOrders().stream().map(OrderReadView::getOrderId).toList(), restored.getMessages().getFirst().getOrders().stream().map(OrderReadView::getOrderId).toList());
      }
      verifyNoInteractions(model, api); verify(store, never()).release(anyString(), any());
      request.setMessage("different"); assertEquals("REQUEST_ID_CONFLICT", assertThrows(AssistantProblem.class, () -> service.sendMessage(jwt, request, AssistantWorkspace.BUYER)).getCode());
      verify(store, never()).commitTurn(anyString(), any(), any(), any(), any());
    }
  }

  @Test
  void boundsMemoryByBytesAndPreservesToolPairs() {
    try (var executor = Executors.newSingleThreadExecutor()) {
      var service = new AssistantConversationService(store, api, model, executor, clock, validator);
      var tool = ToolExecutionRequest.builder().id("call-1").name("getMyOrders").arguments("{\"page\":0,\"state\":\"\"}").build();
      List<ChatMessage> context = new ArrayList<>(); context.add(SystemMessage.from("Policy"));
      for (int index = 0; index < 20; index++) { context.add(UserMessage.from("旧".repeat(3000))); context.add(AiMessage.from("答".repeat(3000))); }
      context.add(UserMessage.from("Current")); context.add(AiMessage.from(tool)); context.add(ToolExecutionResultMessage.from(tool, "[]")); context.add(AiMessage.from("Done"));
      String json = ReflectionTestUtils.invokeMethod(service, "boundedContext", context);
      assertTrue(json.getBytes(StandardCharsets.UTF_8).length <= 98304);
      var restored = ChatMessageDeserializer.messagesFromJson(json); assertTrue(restored.size() <= 30); assertInstanceOf(SystemMessage.class, restored.getFirst());
      assertTrue(restored.contains(ToolExecutionResultMessage.from(tool, "[]"))); assertTrue(restored.contains(AiMessage.from(tool)));
      assertThrows(AssistantProblem.class, () -> ReflectionTestUtils.invokeMethod(service, "boundedContext", List.of(UserMessage.from("Q"), ToolExecutionResultMessage.from(tool, "[]"), AiMessage.from("A"))));
      assertThrows(AssistantProblem.class, () -> ReflectionTestUtils.invokeMethod(service, "boundedContext", List.of(UserMessage.from("巨".repeat(40000)), AiMessage.from("A"))));
    }
  }

  @Test
  void isolatesOwnersAndRedactsRecognizedSecrets() {
    try (var executor = Executors.newSingleThreadExecutor()) {
      var service = new AssistantConversationService(store, api, model, executor, clock, validator);
      var otherIssuer = Jwt.withTokenValue("other-token").header("alg", "RS256").issuer("https://other.test").subject("buyer").expiresAt(now.plusSeconds(300)).build();
      String owner = ReflectionTestUtils.invokeMethod(service, "ownerKey", jwt, AssistantWorkspace.BUYER);
      String other = ReflectionTestUtils.invokeMethod(service, "ownerKey", otherIssuer, AssistantWorkspace.BUYER);
      assertNotEquals(owner, other); assertTrue(owner.matches("[a-f0-9]{64}")); assertFalse(owner.contains("buyer"));
      String redacted = ReflectionTestUtils.invokeMethod(service, "redactSensitiveText", " mail@example.test Bearer abcd-token abcdefgh.ijklmnop.qrstuvwx ");
      assertEquals("[REDACTED] [REDACTED] [REDACTED]", redacted);
      assertThrows(AssistantProblem.class, () -> service.getConversation(null, AssistantWorkspace.BUYER));
      assertThrows(AssistantProblem.class, () -> ReflectionTestUtils.invokeMethod(service, "boundedContext", List.of(UserMessage.from("Q"), AiMessage.from("mail@example.test"))));
      verifyNoInteractions(store, model);
    }
  }
}
