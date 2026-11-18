package com.flashticket.assistant_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flashticket.assistant_service.client.BuyerApiClient;
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
  private final BuyerApiClient api = mock(BuyerApiClient.class);
  private final ChatModel model = mock(ChatModel.class);
  private final Validator validator = mock(Validator.class);

  @Test
  void commitsOnlySuccessfulWholeTurn() {
    try (var executor = Executors.newSingleThreadExecutor()) {
      var service = new AssistantConversationService(store, api, model, executor, clock, validator);
      var state = new ConversationState(); state.setConversationId(UUID.randomUUID()); state.setVersion(UUID.randomUUID()); state.setContextJson("[]"); state.setMessages(List.of());
      var request = new ChatRequest(); request.setConversationId(state.getConversationId()); request.setVersion(state.getVersion()); request.setRequestId(UUID.randomUUID()); request.setMessage("Có sự kiện nào?");
      when(store.beginTurn(anyString(), any(), anyString(), any())).thenReturn(state);
      when(store.isLeaseCurrent(anyString(), any(), any())).thenReturn(true);
      when(model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenReturn(dev.langchain4j.model.chat.response.ChatResponse.builder().aiMessage(AiMessage.from("Thông tin công khai")).build());
      when(store.commitTurn(anyString(), any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(4));
      var response = service.sendMessage(jwt, request); assertEquals("Thông tin công khai", response.getMessage()); assertNotEquals(state.getVersion(), response.getVersion());
      var committed = ArgumentCaptor.forClass(ConversationState.class);
      verify(store).commitTurn(anyString(), eq(state.getConversationId()), eq(state.getVersion()), any(), committed.capture());
      assertEquals(List.of("user", "assistant"), committed.getValue().getMessages().stream().map(TranscriptMessage::getRole).toList());
      assertTrue(committed.getValue().getContextJson().contains("Thông tin công khai")); assertEquals(request.getRequestId(), committed.getValue().getLastRequestId());
      clearInvocations(store);
      when(model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenThrow(new IllegalStateException("secret-provider-body"));
      assertEquals("MODEL_TURN_FAILED", assertThrows(AssistantProblem.class, () -> service.sendMessage(jwt, request)).getCode());
      verify(store, never()).commitTurn(anyString(), any(), any(), any(), any()); verify(store).release(anyString(), any());
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
        var result = callers.submit(() -> service.sendMessage(jwt, request));
        assertTrue(assertDoesNotThrow(() -> entered.await(5, TimeUnit.SECONDS)));
        var reset = new ResetConversationRequest(); reset.setConversationId(state.getConversationId()); reset.setVersion(state.getVersion());
        var fresh = new ConversationState(); fresh.setConversationId(UUID.randomUUID()); fresh.setVersion(UUID.randomUUID()); fresh.setMessages(List.of()); fresh.setExpiresAtEpochMillis(now.plusSeconds(86400).toEpochMilli());
        when(store.reset(anyString(), eq(reset))).thenReturn(fresh);
        assertEquals(fresh.getConversationId(), service.resetConversation(jwt, reset).getConversationId());
        finish.countDown();
        var failure = assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
        assertEquals("LEASE_LOST", ((AssistantProblem) failure.getCause()).getCode());
      } finally { finish.countDown(); }
      clearInvocations(store);
      var movingClock = mock(Clock.class);
      when(movingClock.instant()).thenReturn(now, now, now.plusSeconds(46));
      var lateService = new AssistantConversationService(store, api, model, executor, movingClock, validator);
      assertEquals("TURN_DEADLINE_EXCEEDED", assertThrows(AssistantProblem.class, () -> lateService.sendMessage(jwt, request)).getCode());
      verify(store, never()).commitTurn(anyString(), any(), any(), any(), any());
    }
  }

  @Test
  void replaysLatestRequestWithoutModelAndRejectsConflictingPayload() {
    try (var executor = Executors.newSingleThreadExecutor()) {
      var service = new AssistantConversationService(store, api, model, executor, clock, validator);
      var request = new ChatRequest(); request.setConversationId(UUID.randomUUID()); request.setVersion(UUID.randomUUID()); request.setRequestId(UUID.randomUUID()); request.setMessage("Hello");
      String hash = ReflectionTestUtils.invokeMethod(service, "requestHash", request);
      var state = new ConversationState(); state.setLastRequestId(request.getRequestId()); state.setLastRequestHash(hash); var saved = new ChatResponse(); saved.setMessage("Saved"); state.setLastResponse(saved);
      when(store.beginTurn(anyString(), any(), anyString(), any())).thenReturn(state);
      assertSame(saved, service.sendMessage(jwt, request)); verifyNoInteractions(model); verify(store, never()).release(anyString(), any());
      request.setMessage("different"); assertEquals("REQUEST_ID_CONFLICT", assertThrows(AssistantProblem.class, () -> service.sendMessage(jwt, request)).getCode());
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
      String owner = ReflectionTestUtils.invokeMethod(service, "ownerKey", jwt);
      String other = ReflectionTestUtils.invokeMethod(service, "ownerKey", otherIssuer);
      assertNotEquals(owner, other); assertTrue(owner.matches("[a-f0-9]{64}")); assertFalse(owner.contains("buyer"));
      String redacted = ReflectionTestUtils.invokeMethod(service, "redactSensitiveText", " mail@example.test Bearer abcd-token abcdefgh.ijklmnop.qrstuvwx ");
      assertEquals("[REDACTED] [REDACTED] [REDACTED]", redacted);
      assertThrows(AssistantProblem.class, () -> service.getConversation(null));
      assertThrows(AssistantProblem.class, () -> ReflectionTestUtils.invokeMethod(service, "boundedContext", List.of(UserMessage.from("Q"), AiMessage.from("mail@example.test"))));
      verifyNoInteractions(store, model);
    }
  }
}
