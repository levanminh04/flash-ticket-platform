package com.flashticket.assistant_service.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.assistant_service.config.AssistantSecurityConfiguration;
import com.flashticket.assistant_service.controller.AssistantController;
import com.flashticket.assistant_service.dto.ChatRequest;
import com.flashticket.assistant_service.dto.ChatResponse;
import com.flashticket.assistant_service.exception.AssistantExceptionHandler;
import com.flashticket.assistant_service.service.AssistantConversationService;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AssistantController.class)
@Import({AssistantSecurityConfiguration.class, AssistantExceptionHandler.class})
class AssistantSecurityTests {
  @Autowired MockMvc mvc;
  @MockitoBean AssistantConversationService service;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean(name = "assistantSseExecutor") ThreadPoolTaskExecutor sseExecutor;

  @Test
  void requiresBuyerForMessageStream() throws Exception {
    var organizer = jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"));
    var streamRequest = post("/api/v1/assistant/messages/stream").contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_PROBLEM_JSON).content("{}");
    mvc.perform(streamRequest).andExpect(status().isUnauthorized()).andExpect(request().asyncNotStarted())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    mvc.perform(streamRequest.with(organizer)).andExpect(status().isForbidden()).andExpect(request().asyncNotStarted())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    verify(sseExecutor, never()).execute(any(Runnable.class));
    verifyNoInteractions(service);
  }

  @Test
  void requiresOrganizerForOrganizerMessageStream() throws Exception {
    var organizer = jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"));
    var buyer = jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    var dualRole = jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"),
        new SimpleGrantedAuthority("ROLE_BUYER"));
    UUID conversationId = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    UUID version = UUID.randomUUID();
    String input = """
        {"conversationId":"%s","version":"%s","requestId":"%s","message":"Tổng doanh thu?"}
        """.formatted(conversationId, version, requestId);
    var request = post("/api/v1/assistant/organizer/messages/stream").contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.TEXT_EVENT_STREAM).content(input);
    mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(request().asyncNotStarted());
    mvc.perform(request.with(buyer)).andExpect(status().isForbidden()).andExpect(request().asyncNotStarted());
    verify(sseExecutor, never()).execute(any(Runnable.class));
    verifyNoInteractions(service);

    var response = new ChatResponse();
    response.setConversationId(conversationId);
    response.setRequestId(requestId);
    response.setVersion(UUID.randomUUID());
    when(service.sendMessage(any(Jwt.class), any(ChatRequest.class), eq(true))).thenReturn(response);
    var pending = mvc.perform(post("/api/v1/assistant/organizer/messages/stream").with(organizer)
            .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).content(input))
        .andExpect(request().asyncStarted()).andReturn();
    var task = ArgumentCaptor.forClass(Runnable.class);
    verify(sseExecutor).execute(task.capture());
    task.getValue().run();
    String stream = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)).andReturn()
        .getResponse().getContentAsString();
    assertTrue(stream.startsWith("event:processing"));
    assertTrue(stream.contains("event:result"));
    assertTrue(stream.contains(requestId.toString()));
    verify(service).sendMessage(any(Jwt.class), any(ChatRequest.class), eq(true));

    mvc.perform(get("/api/v1/assistant/organizer/conversations/current").with(dualRole)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/assistant/conversations/current").with(dualRole)).andExpect(status().isOk());
    verify(service).getConversation(any(Jwt.class), eq(true));
    verify(service).getConversation(any(Jwt.class), eq(false));
  }

  @Test
  void requiresBuyerAndValidJwt() {
    assertDoesNotThrow(() -> mvc.perform(get("/api/v1/assistant/conversations/current")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store")));
    byte[] key = "fixture-only-hmac-key-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var realDecoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256")).build();
    realDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("https://issuer.test"));
    when(decoder.decode(anyString())).thenAnswer(invocation -> realDecoder.decode(invocation.getArgument(0)));
    for (String scenario : List.of("buyer", "organizer", "wrong-issuer", "expired", "bad-signature")) {
      var claims = new JWTClaimsSet.Builder().issuer(scenario.equals("wrong-issuer") ? "https://wrong.test" : "https://issuer.test").subject("buyer")
          .issueTime(Date.from(Instant.now().minusSeconds(600))).expirationTime(Date.from(Instant.now().plusSeconds(scenario.equals("expired") ? -300 : 300)))
          .claim("realm_access", Map.of("roles", List.of(scenario.equals("organizer") ? "ORGANIZER" : "BUYER"))).build();
      var signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      assertDoesNotThrow(() -> signed.sign(new MACSigner(scenario.equals("bad-signature") ? new byte[32] : key)));
      int expected = scenario.equals("buyer") ? 200 : scenario.equals("organizer") ? 403 : 401;
      assertDoesNotThrow(() -> mvc.perform(get("/api/v1/assistant/conversations/current").header("Authorization", "Bearer " + signed.serialize())).andExpect(status().is(expected)));
    }
    verify(service, times(1)).getConversation(any(), eq(false));
  }

  @Test
  void deniesOperationalPathsAndKeepsProbePolicy() {
    var buyer = jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    for (String path : List.of("/internal/v1/orders", "/actuator/env", "/actuator/health", "/unknown")) {
      assertDoesNotThrow(() -> mvc.perform(get(path).with(buyer)).andExpect(status().isForbidden()).andExpect(header().string("Cache-Control", "no-store")));
    }
    // Slice không mount Actuator: 404 chứng minh đã qua security, không coi là health runtime.
    for (String path : List.of("/actuator/health/liveness", "/actuator/health/readiness")) {
      var result = assertDoesNotThrow(() -> mvc.perform(get(path)).andReturn());
      assertNotEquals(401, result.getResponse().getStatus()); assertNotEquals(403, result.getResponse().getStatus());
      assertDoesNotThrow(() -> mvc.perform(post(path).with(buyer)).andExpect(status().isForbidden()));
    }
    verifyNoInteractions(service);
  }
}
