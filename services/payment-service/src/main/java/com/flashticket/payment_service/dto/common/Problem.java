package com.flashticket.payment_service.dto.common;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.dto.payment.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

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
