package com.flashticket.assistant_service.controller;

import com.flashticket.assistant_service.AssistantWorkspace;
import com.flashticket.assistant_service.dto.ChatRequest;
import com.flashticket.assistant_service.dto.ChatResponse;
import com.flashticket.assistant_service.dto.ConversationView;
import com.flashticket.assistant_service.dto.ResetConversationRequest;
import com.flashticket.assistant_service.exception.AssistantProblem;
import com.flashticket.assistant_service.service.AssistantConversationService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
@RequestMapping({"/api/v1/assistant", "/api/v1/assistant/{workspace:organizer|admin}"})
public class AssistantController {
  private final AssistantConversationService conversationService;
  private final ThreadPoolTaskExecutor assistantSseExecutor;
  private final ThreadPoolTaskExecutor assistantSseTurnExecutor;

  public AssistantController(AssistantConversationService conversationService,
      @Qualifier("assistantSseExecutor") ThreadPoolTaskExecutor assistantSseExecutor,
      @Qualifier("assistantSseTurnExecutor") ThreadPoolTaskExecutor assistantSseTurnExecutor) {
    this.conversationService = conversationService;
    this.assistantSseExecutor = assistantSseExecutor;
    this.assistantSseTurnExecutor = assistantSseTurnExecutor;
  }

  @PostMapping("/conversations/current")
  public ResponseEntity<ConversationView> openConversation(@AuthenticationPrincipal Jwt jwt,
      @PathVariable(name = "workspace", required = false) String workspace) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(conversationService.openConversation(jwt, AssistantWorkspace.fromPath(workspace)));
  }

  @GetMapping("/conversations/current")
  public ResponseEntity<ConversationView> getConversation(@AuthenticationPrincipal Jwt jwt,
      @PathVariable(name = "workspace", required = false) String workspace) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(conversationService.getConversation(jwt, AssistantWorkspace.fromPath(workspace)));
  }

  @PostMapping("/messages")
  public ResponseEntity<ChatResponse> sendMessage(@AuthenticationPrincipal Jwt jwt,
      @PathVariable(name = "workspace", required = false) String workspace,
      @Valid @RequestBody ChatRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(conversationService.sendMessage(jwt, request, AssistantWorkspace.fromPath(workspace)));
  }

  @PostMapping(value = "/messages/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> streamMessage(@AuthenticationPrincipal Jwt jwt,
      @PathVariable(name = "workspace", required = false) String workspace,
      @Valid @RequestBody ChatRequest request,
      @RequestParam(name = "progress", required = false) String progress) {
    AssistantWorkspace selectedWorkspace = AssistantWorkspace.fromPath(workspace);
    SseEmitter emitter = new SseEmitter(50_000L);
    try {
      assistantSseExecutor.execute(() -> {
        if ("tool-v1".equals(progress)) {
          writeProgressMessageStream(emitter, jwt, request, selectedWorkspace);
        } else {
          writeMessageStream(emitter, jwt, request, selectedWorkspace);
        }
      });
    } catch (RejectedExecutionException error) {
      throw new AssistantProblem(HttpStatus.SERVICE_UNAVAILABLE, "ASSISTANT_CAPACITY_EXCEEDED",
          "Trợ lý đang bận. Vui lòng thử lại sau.");
    }
    return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).cacheControl(CacheControl.noStore()).body(emitter);
  }

  private void writeMessageStream(SseEmitter emitter, Jwt jwt, ChatRequest request, AssistantWorkspace workspace) {
    try {
      emitter.send(SseEmitter.event().name("processing")
          .data(Map.of("requestId", request.getRequestId()), MediaType.APPLICATION_JSON));
      var terminal = runMessageTurn(jwt, request, workspace, null);
      emitter.send(SseEmitter.event().name(terminal.getKey()).data(terminal.getValue(), MediaType.APPLICATION_JSON));
      emitter.complete();
    } catch (IOException | IllegalStateException error) {
      // Dừng ghi khi stream đóng; mất kết nối không có nghĩa lượt đã rollback.
    }
  }

  private void writeProgressMessageStream(SseEmitter emitter, Jwt jwt, ChatRequest request, AssistantWorkspace workspace) {
    long startedAt = System.nanoTime();
    var mailbox = new ArrayBlockingQueue<Map.Entry<String, Map<String, ?>>>(7);
    var acceptingProgress = new AtomicBoolean(true);
    var progressCount = new AtomicInteger();
    try {
      emitter.send(SseEmitter.event().name("processing")
          .data(Map.of("requestId", request.getRequestId()), MediaType.APPLICATION_JSON));
      try {
        assistantSseTurnExecutor.execute(() -> {
          var terminal = runMessageTurn(jwt, request, workspace, operation -> {
            // Tối đa 6 progress cho cả lượt; luôn chừa một ô cho terminal.
            if (acceptingProgress.get() && progressCount.getAndIncrement() < 6) {
              mailbox.offer(Map.entry("tool_progress", Map.of("requestId", request.getRequestId(), "operation", operation)));
            }
          });
          mailbox.offer(terminal);
        });
      } catch (RejectedExecutionException error) {
        mailbox.offer(Map.entry("turn_error", Map.of("requestId", request.getRequestId(), "code", "ASSISTANT_CAPACITY_EXCEEDED")));
      }
      while (true) {
        long remaining = TimeUnit.SECONDS.toNanos(48) - (System.nanoTime() - startedAt);
        if (remaining <= 0) break;
        var frame = mailbox.poll(remaining, TimeUnit.NANOSECONDS);
        if (frame == null) break;
        emitter.send(SseEmitter.event().name(frame.getKey()).data(frame.getValue(), MediaType.APPLICATION_JSON));
        if (!"tool_progress".equals(frame.getKey())) break;
      }
      emitter.complete();
    } catch (IOException | IllegalStateException error) {
      // Mất stream không đồng nghĩa rollback; để MVC dọn kết nối lỗi.
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      emitter.complete();
    } finally {
      acceptingProgress.set(false);
    }
  }

  private Map.Entry<String, Map<String, ?>> runMessageTurn(Jwt jwt, ChatRequest request, AssistantWorkspace workspace,
      Consumer<String> onToolProgress) {
    try {
      ChatResponse response = onToolProgress == null ? conversationService.sendMessage(jwt, request, workspace)
          : conversationService.sendMessage(jwt, request, workspace, onToolProgress);
      return Map.entry("result", Map.of("requestId", response.getRequestId(),
          "conversationId", response.getConversationId(), "version", response.getVersion()));
    } catch (RuntimeException error) {
      String code = error instanceof AssistantProblem problem ? problem.getCode() : "INTERNAL_ERROR";
      return Map.entry("turn_error", Map.of("requestId", request.getRequestId(), "code", code));
    }
  }

  @PostMapping("/conversations/reset")
  public ResponseEntity<ConversationView> resetConversation(@AuthenticationPrincipal Jwt jwt,
      @PathVariable(name = "workspace", required = false) String workspace,
      @Valid @RequestBody ResetConversationRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(conversationService.resetConversation(jwt, request, AssistantWorkspace.fromPath(workspace)));
  }
}
