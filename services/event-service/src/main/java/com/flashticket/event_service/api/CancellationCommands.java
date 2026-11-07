package com.flashticket.event_service.api;

import jakarta.validation.constraints.NotBlank;

public final class CancellationCommands {
  private CancellationCommands() {}

  public record Request(@NotBlank String rowVersion, @NotBlank String reason) {}

  public record Reject(@NotBlank String rowVersion, String reason) {}
}
