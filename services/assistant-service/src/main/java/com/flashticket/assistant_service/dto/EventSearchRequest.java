package com.flashticket.assistant_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
  @JsonProperty(required = false) @Size(max = 300) private String q;
  @JsonProperty(required = false) @Size(max = 200) private String venue;
  @JsonProperty(required = false) @Size(max = 512) private String cursor;
  @JsonProperty(required = false) private UUID categoryId;
  @JsonProperty(required = false) private LocalDate from;
  @JsonProperty(required = false) private LocalDate to;
  @JsonProperty(required = false) @DecimalMin("0") private BigDecimal maxPrice;
}
