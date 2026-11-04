package com.flashticket.booking_service.dto.response;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** JSON lỗi trả cho client, gồm mã lỗi, message, ID tra cứu và lỗi validation. */
@Getter
@AllArgsConstructor
public final class Problem {
  @NotBlank private final String code;
  @NotBlank private final String message;
  @NotNull private final UUID correlationId;
  @NotNull @Valid private final List<@NotNull @Valid Violation> violations;

  /** Mô tả một field không hợp lệ bằng tên field và mã lỗi, không chứa giá trị input. */
  @Getter
  @AllArgsConstructor
  public static final class Violation {
    @NotBlank private final String field;
    @NotBlank private final String code;
  }
}
