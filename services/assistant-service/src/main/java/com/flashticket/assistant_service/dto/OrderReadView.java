package com.flashticket.assistant_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderReadView {
  private UUID orderId;
  private UUID eventId;
  private String eventTitle;
  private String state;
  private String currency;
  private String subtotal;
  private String discount;
  private String total;
  private Instant expiresAt;
}
