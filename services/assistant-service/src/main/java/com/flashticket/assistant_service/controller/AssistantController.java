package com.flashticket.assistant_service.controller;

import com.flashticket.assistant_service.dto.ChatRequest;
import com.flashticket.assistant_service.dto.ChatResponse;
import com.flashticket.assistant_service.dto.ConversationView;
import com.flashticket.assistant_service.dto.ResetConversationRequest;
import com.flashticket.assistant_service.exception.AssistantProblem;
import com.flashticket.assistant_service.service.AssistantConversationService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping({"/api/v1/assistant", "/api/v1/assistant/{workspace:organizer}"})
public class AssistantController {
  private final AssistantConversationService conversationService;
  private final ThreadPoolTaskExecutor assistantSseExecutor;

  public AssistantController(AssistantConversationService conversationService,
      @Qualifier("assistantSseExecutor") ThreadPoolTaskExecutor assistantSseExecutor) {
    this.conversationService = conversationService;
    this.assistantSseExecutor = assistantSseExecutor;
  }

  @PostMapping("/conversations/current")
  public ResponseEntity<ConversationView> openConversation(@AuthenticationPrincipal Jwt jwt,
      @PathVariable(name = "workspace", required = false) String workspace) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(conversationService.openConversation(jwt, workspace != null));
  }

  @GetMapping("/conversations/current")
  public ResponseEntity<ConversationView> getConversation(@AuthenticationPrincipal Jwt jwt,
      @PathVariable(name = "workspace", required = false) String workspace) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(conversationService.getConversation(jwt, workspace != null));
  }

  @PostMapping("/messages")
  public ResponseEntity<ChatResponse> sendMessage(@AuthenticationPrincipal Jwt jwt,
      @PathVariable(name = "workspace", required = false) String workspace,
      @Valid @RequestBody ChatRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(conversationService.sendMessage(jwt, request, workspace != null));
  }

  @PostMapping(value = "/messages/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> streamMessage(@AuthenticationPrincipal Jwt jwt,
      @PathVariable(name = "workspace", required = false) String workspace,
      @Valid @RequestBody ChatRequest request) {
    SseEmitter emitter = new SseEmitter(50_000L);
    try {
      assistantSseExecutor.execute(() -> writeMessageStream(emitter, jwt, request, workspace != null));
    } catch (RejectedExecutionException error) {
      throw new AssistantProblem(HttpStatus.SERVICE_UNAVAILABLE, "ASSISTANT_CAPACITY_EXCEEDED",
          "Trợ lý đang bận. Vui lòng thử lại sau.");
    }
    return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).cacheControl(CacheControl.noStore()).body(emitter);
  }

  private void writeMessageStream(SseEmitter emitter, Jwt jwt, ChatRequest request, boolean organizer) {
    try {
      emitter.send(SseEmitter.event().name("processing")
          .data(Map.of("requestId", request.getRequestId()), MediaType.APPLICATION_JSON));
      SseEmitter.SseEventBuilder terminal;
      try {
        ChatResponse response = conversationService.sendMessage(jwt, request, organizer);
        terminal = SseEmitter.event().name("result").data(Map.of("requestId", response.getRequestId(),
            "conversationId", response.getConversationId(), "version", response.getVersion()), MediaType.APPLICATION_JSON);
      } catch (RuntimeException error) {
        String code = error instanceof AssistantProblem problem ? problem.getCode() : "INTERNAL_ERROR";
        terminal = SseEmitter.event().name("turn_error")
            .data(Map.of("requestId", request.getRequestId(), "code", code), MediaType.APPLICATION_JSON);
      }
      emitter.send(terminal);
      emitter.complete();
    } catch (IOException | IllegalStateException error) {
      // Dừng ghi khi stream đóng; mất kết nối không có nghĩa lượt đã rollback.
    }
  }

  @PostMapping("/conversations/reset")
  public ResponseEntity<ConversationView> resetConversation(@AuthenticationPrincipal Jwt jwt,
      @PathVariable(name = "workspace", required = false) String workspace,
      @Valid @RequestBody ResetConversationRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(conversationService.resetConversation(jwt, request, workspace != null));
  }
}
