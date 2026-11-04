package com.flashticket.ticket_service.entity;

import com.flashticket.ticket_service.dto.snapshot.*;
import com.flashticket.ticket_service.enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "tbl_event_access_snapshots", schema = "ticket_schema")
@Getter
@Setter
@NoArgsConstructor
public class EventAccessSnapshot {
  @Id
  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "organizer_subject", nullable = false, columnDefinition = "nonempty_text")
  private String organizerSubject;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_status", nullable = false, columnDefinition = "nonempty_text")
  private EventState eventStatus;

  @Column(name = "event_starts_at", nullable = false)
  private Instant eventStartsAt;

  @Column(name = "event_ends_at", nullable = false)
  private Instant eventEndsAt;

  @Column(name = "source_config_version", nullable = false)
  private Long sourceConfigVersion;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;
}
