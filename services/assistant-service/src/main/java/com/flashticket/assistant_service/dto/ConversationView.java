package com.flashticket.assistant_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConversationView {
  private UUID conversationId;
  private UUID version;
  private UUID lastRequestId;
  private Instant expiresAt;
  private String notice;
  private List<TranscriptMessage> messages;
}
