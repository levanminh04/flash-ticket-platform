package com.flashticket.payment_service.dto.common;

import jakarta.validation.ConstraintViolation;
import java.util.Optional;
import java.util.Set;

/** Selects a stable error path while callers retain their own error codes and path prefixes. */
public final class ContractViolations {
  private ContractViolations() {}

  public static Optional<String> firstPath(Set<? extends ConstraintViolation<?>> violations) {
    return violations.stream()
        .map(violation -> violation.getPropertyPath().toString())
        .min(String::compareTo);
  }
}
