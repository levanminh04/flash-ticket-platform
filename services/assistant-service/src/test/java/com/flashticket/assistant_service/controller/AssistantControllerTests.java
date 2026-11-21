package com.flashticket.assistant_service.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.assistant_service.AssistantWorkspace;
import com.flashticket.assistant_service.config.AssistantSecurityConfiguration;
import com.flashticket.assistant_service.dto.*;
import com.flashticket.assistant_service.exception.*;
import com.flashticket.assistant_service.service.AssistantConversationService;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(AssistantController.class)
@Import({AssistantSecurityConfiguration.class, AssistantExceptionHandler.class})
class AssistantControllerTests {
  @Autowired MockMvc mvc;
  @Autowired JsonMapper mapper;
  @MockitoBean AssistantConversationService service;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean(name = "assistantSseExecutor") ThreadPoolTaskExecutor sseExecutor;
  @MockitoBean(name = "assistantSseTurnExecutor") ThreadPoolTaskExecutor sseTurnExecutor;

  @Test
  void streamsOptInProgressBeforeSingleTerminal() throws Exception {
    var auth = jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    var request = new ChatRequest();
    request.setConversationId(UUID.randomUUID());
    request.setVersion(UUID.randomUUID());
    request.setRequestId(UUID.randomUUID());
    request.setMessage("Gợi ý sự kiện");
    var response = new ChatResponse();
    response.setRequestId(request.getRequestId());
    response.setConversationId(request.getConversationId());
    response.setVersion(UUID.randomUUID());
    Answer<Void> runTask = invocation -> {
      invocation.getArgument(0, Runnable.class).run();
      return null;
    };
    doAnswer(runTask).when(sseExecutor).execute(any(Runnable.class));
    doAnswer(runTask).when(sseTurnExecutor).execute(any(Runnable.class));
    List<String> operations = List.of("SEARCH_EVENTS", "READ_EVENT", "CHECK_AVAILABILITY", "READ_ORDERS",
        "PREPARE_DRAFT", "READ_EVENT", "SEARCH_EVENTS");
    Answer<ChatResponse> runTurn = invocation -> {
      Consumer<String> observer = invocation.getArgument(3);
      for (String operation : operations) observer.accept(operation);
      return response;
    };
    when(service.sendMessage(any(Jwt.class), any(ChatRequest.class), eq(AssistantWorkspace.BUYER), any())).thenAnswer(runTurn);
    when(service.sendMessage(any(Jwt.class), any(ChatRequest.class), eq(AssistantWorkspace.BUYER))).thenReturn(response);

    var pending = mvc.perform(post("/api/v1/assistant/messages/stream").param("progress", "tool-v1").with(auth)
        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).content(mapper.writeValueAsBytes(request)))
        .andExpect(request().asyncStarted()).andReturn();
    String[] frames = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn()
        .getResponse().getContentAsString().split("\n\n", -1);
    String processingPrefix = "event:processing\ndata:";
    String progressPrefix = "event:tool_progress\ndata:";
    String resultPrefix = "event:result\ndata:";
    assertEquals(9, frames.length);
    assertTrue(frames[0].startsWith(processingPrefix));
    assertEquals(mapper.valueToTree(Map.of("requestId", request.getRequestId())),
        mapper.readTree(frames[0].substring(processingPrefix.length())));
    for (int index = 0; index < 6; index++) {
      assertTrue(frames[index + 1].startsWith(progressPrefix));
      assertEquals(mapper.valueToTree(Map.of("requestId", request.getRequestId(), "operation", operations.get(index))),
          mapper.readTree(frames[index + 1].substring(progressPrefix.length())));
    }
    assertTrue(frames[7].startsWith(resultPrefix));
    assertEquals(mapper.valueToTree(Map.of("requestId", response.getRequestId(),
            "conversationId", response.getConversationId(), "version", response.getVersion())),
        mapper.readTree(frames[7].substring(resultPrefix.length())));
    assertEquals("", frames[8]);

    var legacy = mvc.perform(post("/api/v1/assistant/messages/stream").param("progress", "unknown").with(auth)
        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).content(mapper.writeValueAsBytes(request)))
        .andExpect(request().asyncStarted()).andReturn();
    String[] legacyFrames = mvc.perform(asyncDispatch(legacy)).andExpect(status().isOk()).andReturn()
        .getResponse().getContentAsString().split("\n\n", -1);
    assertArrayEquals(new String[]{frames[0], frames[7], ""}, legacyFrames);
    verify(sseTurnExecutor).execute(any(Runnable.class));
    verify(service).sendMessage(any(Jwt.class), any(ChatRequest.class), eq(AssistantWorkspace.BUYER), any());
    verify(service).sendMessage(any(Jwt.class), any(ChatRequest.class), eq(AssistantWorkspace.BUYER));
  }

  @Test
  void reportsProgressCoordinatorCapacityWithoutStartingTurn() throws Exception {
    var auth = jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    var request = new ChatRequest();
    request.setConversationId(UUID.randomUUID());
    request.setVersion(UUID.randomUUID());
    request.setRequestId(UUID.randomUUID());
    request.setMessage("Gợi ý sự kiện");
    doAnswer(invocation -> {
      invocation.getArgument(0, Runnable.class).run();
      return null;
    }).when(sseExecutor).execute(any(Runnable.class));
    doThrow(new RejectedExecutionException()).when(sseTurnExecutor).execute(any(Runnable.class));
    var pending = mvc.perform(post("/api/v1/assistant/messages/stream").param("progress", "tool-v1").with(auth)
        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).content(mapper.writeValueAsBytes(request)))
        .andExpect(request().asyncStarted()).andReturn();
    String[] frames = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn()
        .getResponse().getContentAsString().split("\n\n", -1);
    assertEquals(3, frames.length);
    String processingPrefix = "event:processing\ndata:";
    String errorPrefix = "event:turn_error\ndata:";
    assertTrue(frames[0].startsWith(processingPrefix));
    assertTrue(frames[1].startsWith(errorPrefix));
    assertEquals(mapper.valueToTree(Map.of("requestId", request.getRequestId())),
        mapper.readTree(frames[0].substring(processingPrefix.length())));
    assertEquals(mapper.valueToTree(Map.of("requestId", request.getRequestId(), "code", "ASSISTANT_CAPACITY_EXCEEDED")),
        mapper.readTree(frames[1].substring(errorPrefix.length())));
    assertEquals("", frames[2]);
    verifyNoInteractions(service);
  }

  @Test
  void streamsProcessingThenCommittedResult() throws Exception {
    var token = Jwt.withTokenValue("fixture").header("alg", "RS256").issuer("https://issuer.test")
        .subject("real-buyer").expiresAt(Instant.now().plusSeconds(300)).build();
    var auth = jwt().jwt(token).authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    var request = new ChatRequest();
    request.setConversationId(UUID.randomUUID());
    request.setVersion(UUID.randomUUID());
    request.setRequestId(UUID.randomUUID());
    request.setMessage("Hello");
    var response = new ChatResponse();
    response.setRequestId(request.getRequestId());
    response.setConversationId(request.getConversationId());
    response.setVersion(UUID.randomUUID());
    response.setMessage("Private fixture transcript");
    when(service.sendMessage(eq(token), any(ChatRequest.class), eq(AssistantWorkspace.BUYER))).thenReturn(response);
    doAnswer(invocation -> {
      invocation.getArgument(0, Runnable.class).run();
      return null;
    }).when(sseExecutor).execute(any(Runnable.class));

    var pending = mvc.perform(post("/api/v1/assistant/messages/stream").with(auth)
            .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_PROBLEM_JSON)
            .content(mapper.writeValueAsBytes(request)))
        .andExpect(request().asyncStarted()).andReturn();
    var completed = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(header().doesNotExist(HttpHeaders.CONTENT_LENGTH)).andReturn();
    String[] frames = completed.getResponse().getContentAsString().split("\n\n", -1);
    assertEquals(3, frames.length);
    String processingPrefix = "event:processing\ndata:";
    String resultPrefix = "event:result\ndata:";
    assertTrue(frames[0].startsWith(processingPrefix));
    assertTrue(frames[1].startsWith(resultPrefix));
    assertEquals(mapper.valueToTree(Map.of("requestId", request.getRequestId())),
        mapper.readTree(frames[0].substring(processingPrefix.length())));
    assertEquals(mapper.valueToTree(Map.of("requestId", response.getRequestId(),
            "conversationId", response.getConversationId(), "version", response.getVersion())),
        mapper.readTree(frames[1].substring(resultPrefix.length())));
    assertEquals("", frames[2]);
    var capturedRequest = ArgumentCaptor.forClass(ChatRequest.class);
    verify(sseExecutor).execute(any(Runnable.class));
    verify(service).sendMessage(eq(token), capturedRequest.capture(), eq(AssistantWorkspace.BUYER));
    assertEquals(mapper.valueToTree(request), mapper.valueToTree(capturedRequest.getValue()));
  }

  @Test
  void separatesHttpProblemsFromStreamErrors() throws Exception {
    var auth = jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    var streamRequest = post("/api/v1/assistant/messages/stream").with(auth).contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_PROBLEM_JSON);
    mvc.perform(streamRequest.content("{}")).andExpect(status().isBadRequest()).andExpect(request().asyncNotStarted())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    verify(sseExecutor, never()).execute(any(Runnable.class));
    verifyNoInteractions(service);

    var request = new ChatRequest();
    request.setConversationId(UUID.randomUUID());
    request.setVersion(UUID.randomUUID());
    request.setRequestId(UUID.randomUUID());
    request.setMessage("Hello");
    streamRequest.content(mapper.writeValueAsBytes(request));
    doThrow(new RejectedExecutionException()).when(sseExecutor).execute(any(Runnable.class));
    mvc.perform(streamRequest).andExpect(status().isServiceUnavailable()).andExpect(request().asyncNotStarted())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(jsonPath("$.code").value("ASSISTANT_CAPACITY_EXCEEDED"));
    verifyNoInteractions(service);

    doAnswer(invocation -> {
      invocation.getArgument(0, Runnable.class).run();
      return null;
    }).when(sseExecutor).execute(any(Runnable.class));
    var knownError = new AssistantProblem(HttpStatus.CONFLICT, "CONVERSATION_BUSY", "Private fixture detail");
    var unexpectedError = new IllegalStateException("Private fixture exception");
    doThrow(knownError, unexpectedError).when(service).sendMessage(any(Jwt.class), any(ChatRequest.class), eq(AssistantWorkspace.BUYER));
    for (String code : List.of("CONVERSATION_BUSY", "INTERNAL_ERROR")) {
      var pending = mvc.perform(streamRequest).andExpect(request().asyncStarted()).andReturn();
      var completed = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)).andReturn();
      String[] frames = completed.getResponse().getContentAsString().split("\n\n", -1);
      assertEquals(3, frames.length);
      String processingPrefix = "event:processing\ndata:";
      String errorPrefix = "event:turn_error\ndata:";
      assertTrue(frames[0].startsWith(processingPrefix));
      assertTrue(frames[1].startsWith(errorPrefix));
      assertEquals(mapper.valueToTree(Map.of("requestId", request.getRequestId())),
          mapper.readTree(frames[0].substring(processingPrefix.length())));
      assertEquals(mapper.valueToTree(Map.of("requestId", request.getRequestId(), "code", code)),
          mapper.readTree(frames[1].substring(errorPrefix.length())));
      assertEquals("", frames[2]);
    }
    verify(service, times(2)).sendMessage(any(Jwt.class), any(ChatRequest.class), eq(AssistantWorkspace.BUYER));
  }

  @Test
  void validatesRequestsAndUsesJwtIdentity() {
    var token = Jwt.withTokenValue("fixture").header("alg", "RS256").issuer("https://issuer.test").subject("real-buyer").expiresAt(Instant.now().plusSeconds(300)).build();
    var auth = jwt().jwt(token).authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    var request = new ChatRequest(); request.setConversationId(UUID.randomUUID()); request.setVersion(UUID.randomUUID()); request.setRequestId(UUID.randomUUID()); request.setMessage("Hello");
    assertDoesNotThrow(() -> mvc.perform(post("/api/v1/assistant/messages").with(auth).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")));
    var captured = ArgumentCaptor.forClass(Jwt.class); verify(service).sendMessage(captured.capture(), any(), eq(AssistantWorkspace.BUYER)); assertEquals("real-buyer", captured.getValue().getSubject());
    clearInvocations(service);
    for (String message : List.of(" ", "x".repeat(2001))) {
      request.setMessage(message);
      assertDoesNotThrow(() -> mvc.perform(post("/api/v1/assistant/messages").with(auth).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request))).andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store")));
    }
    request.setMessage("Hello"); request.setConversationId(null);
    assertDoesNotThrow(() -> mvc.perform(post("/api/v1/assistant/messages").with(auth).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request))).andExpect(status().isBadRequest()));
    verifyNoInteractions(service);
  }

  @Test
  void returnsSafeNoStoreProblems() {
    var auth = jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    for (var status : List.of(HttpStatus.CONFLICT, HttpStatus.GONE, HttpStatus.TOO_MANY_REQUESTS, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT)) {
      doThrow(new AssistantProblem(status, "SAFE_CODE", "Thông báo an toàn")).when(service).getConversation(any(), eq(AssistantWorkspace.BUYER));
      var result = assertDoesNotThrow(() -> mvc.perform(get("/api/v1/assistant/conversations/current").with(auth)).andExpect(status().is(status.value())).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.code").value("SAFE_CODE")).andReturn());
      assertFalse(new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8).contains("fixture-token"));
    }
    doThrow(new IllegalStateException("Bearer private-secret mail@example.test")).when(service).getConversation(any(), eq(AssistantWorkspace.BUYER));
    var result = assertDoesNotThrow(() -> mvc.perform(get("/api/v1/assistant/conversations/current").with(auth)).andExpect(status().isInternalServerError()).andReturn());
    assertFalse(new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8).contains("private-secret"));
    assertDoesNotThrow(() -> mvc.perform(post("/api/v1/assistant/messages").with(auth).contentType(MediaType.APPLICATION_JSON).content("{broken secret}")).andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store")));
  }
}
