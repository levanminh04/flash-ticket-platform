package com.flashticket.assistant_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatRequest {
  @NotNull private UUID conversationId;
  @NotNull private UUID version;
  @NotNull private UUID requestId;
  @NotBlank @Size(max = 2000) private String message;
}
