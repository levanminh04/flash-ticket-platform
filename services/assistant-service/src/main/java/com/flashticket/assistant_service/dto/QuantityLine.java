package com.flashticket.assistant_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QuantityLine {
  @NotNull private UUID ticketTypeId;
  @NotNull @Min(1) @Max(99) private Integer quantity;
}
