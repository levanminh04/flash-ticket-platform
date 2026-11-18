package com.flashticket.assistant_service.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.assistant_service.config.AssistantSecurityConfiguration;
import com.flashticket.assistant_service.dto.*;
import com.flashticket.assistant_service.exception.*;
import com.flashticket.assistant_service.service.AssistantConversationService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
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

  @Test
  void validatesRequestsAndUsesJwtIdentity() {
    var token = Jwt.withTokenValue("fixture").header("alg", "RS256").issuer("https://issuer.test").subject("real-buyer").expiresAt(Instant.now().plusSeconds(300)).build();
    var auth = jwt().jwt(token).authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    var request = new ChatRequest(); request.setConversationId(UUID.randomUUID()); request.setVersion(UUID.randomUUID()); request.setRequestId(UUID.randomUUID()); request.setMessage("Hello");
    assertDoesNotThrow(() -> mvc.perform(post("/api/v1/assistant/messages").with(auth).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")));
    var captured = ArgumentCaptor.forClass(Jwt.class); verify(service).sendMessage(captured.capture(), any()); assertEquals("real-buyer", captured.getValue().getSubject());
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
      doThrow(new AssistantProblem(status, "SAFE_CODE", "Thông báo an toàn")).when(service).getConversation(any());
      var result = assertDoesNotThrow(() -> mvc.perform(get("/api/v1/assistant/conversations/current").with(auth)).andExpect(status().is(status.value())).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.code").value("SAFE_CODE")).andReturn());
      assertFalse(new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8).contains("fixture-token"));
    }
    doThrow(new IllegalStateException("Bearer private-secret mail@example.test")).when(service).getConversation(any());
    var result = assertDoesNotThrow(() -> mvc.perform(get("/api/v1/assistant/conversations/current").with(auth)).andExpect(status().isInternalServerError()).andReturn());
    assertFalse(new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8).contains("private-secret"));
    assertDoesNotThrow(() -> mvc.perform(post("/api/v1/assistant/messages").with(auth).contentType(MediaType.APPLICATION_JSON).content("{broken secret}")).andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store")));
  }
}
