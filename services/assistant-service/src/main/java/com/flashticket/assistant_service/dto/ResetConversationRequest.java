package com.flashticket.assistant_service.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResetConversationRequest {
  @NotNull private UUID conversationId;
  @NotNull private UUID version;
}
