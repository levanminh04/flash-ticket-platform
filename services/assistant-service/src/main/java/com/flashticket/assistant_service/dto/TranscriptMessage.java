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
public class TranscriptMessage {
  private UUID id;
  private String role;
  private String text;
  private Instant createdAt;
  private PurchaseDraft draft;
  private List<EventView> events;
}
