package com.flashticket.ticket_service.dto.response;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record Problem(String code, String message, UUID correlationId, List<Violation> violations) {
  public Problem {
    violations = List.copyOf(violations);
  }

  public record Violation(String field, String code) {}
}
