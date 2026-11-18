package com.flashticket.assistant_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EventSearchRequest {
  @Size(max = 300) private String q;
  @Size(max = 200) private String venue;
  @Size(max = 512) private String cursor;
  private UUID categoryId;
  private LocalDate from;
  private LocalDate to;
  @DecimalMin("0") private BigDecimal maxPrice;
}
