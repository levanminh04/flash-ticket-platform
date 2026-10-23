package com.flashticket.payment_service.dto.response;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record Problem(
    @NotBlank String code,
    @NotBlank String message,
    @NotNull UUID correlationId,
    @NotNull List<@Valid Violation> violations) {
  public Problem {
    if (violations != null) violations = List.copyOf(violations);
  }

  public List<Violation> violations() {
    return violations == null ? null : List.copyOf(violations);
  }
}
