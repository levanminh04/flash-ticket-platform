package com.flashticket.user_service.dto.response;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record Problem(
    @NotBlank String code,
    @NotBlank String message,
    @NotNull UUID correlationId,
    @NotNull @Valid List<@NotNull @Valid Violation> violations) {
  public Problem {
    violations = List.copyOf(violations);
  }

  public record Violation(@NotBlank String field, @NotBlank String code) {}
}
