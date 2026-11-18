package com.flashticket.assistant_service.dto;

import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConversationState {
  private UUID conversationId;
  private UUID version;
  private UUID lastRequestId;
  private long expiresAtEpochMillis;
  private String contextJson;
  private String lastRequestHash;
  private List<TranscriptMessage> messages;
  private ChatResponse lastResponse;
}
