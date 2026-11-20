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
public class OrganizerEventView {
  private UUID eventId;
  private String title;
  private String status;
  private String mode;
  private Instant startsAt;
  private Instant endsAt;
  private Instant saleStartsAt;
  private Instant saleEndsAt;
  private Instant approvedAt;
}
