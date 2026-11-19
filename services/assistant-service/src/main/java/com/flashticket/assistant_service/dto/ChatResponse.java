package com.flashticket.assistant_service.dto;

import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatResponse {
  private UUID conversationId;
  private UUID version;
  private UUID requestId;
  private String message;
  private PurchaseDraft draft;
  private List<EventView> events;
  private List<OrderReadView> orders;
}
