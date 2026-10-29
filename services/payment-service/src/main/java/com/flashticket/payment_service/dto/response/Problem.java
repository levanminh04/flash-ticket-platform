package com.flashticket.payment_service.dto.response;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class Problem {
  @NotBlank private final String code;
  @NotBlank private final String message;
  @NotNull private final UUID correlationId;
  @NotNull @Valid private final List<@NotNull Violation> violations;

  @Getter
  @AllArgsConstructor
  public static final class Violation {
    @NotBlank private final String field;
    @NotBlank private final String code;
  }
}
