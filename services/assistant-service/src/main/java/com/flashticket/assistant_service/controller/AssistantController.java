package com.flashticket.assistant_service.controller;

import com.flashticket.assistant_service.dto.ChatRequest;
import com.flashticket.assistant_service.dto.ChatResponse;
import com.flashticket.assistant_service.dto.ConversationView;
import com.flashticket.assistant_service.dto.ResetConversationRequest;
import com.flashticket.assistant_service.service.AssistantConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantController {
  private final AssistantConversationService conversationService;

  @PostMapping("/conversations/current")
  public ResponseEntity<ConversationView> openConversation(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(conversationService.openConversation(jwt));
  }

  @GetMapping("/conversations/current")
  public ResponseEntity<ConversationView> getConversation(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(conversationService.getConversation(jwt));
  }

  @PostMapping("/messages")
  public ResponseEntity<ChatResponse> sendMessage(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChatRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(conversationService.sendMessage(jwt, request));
  }

  @PostMapping("/conversations/reset")
  public ResponseEntity<ConversationView> resetConversation(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ResetConversationRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(conversationService.resetConversation(jwt, request));
  }
}
