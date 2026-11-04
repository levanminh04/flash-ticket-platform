package com.flashticket.ticket_service.entity;

import com.flashticket.ticket_service.dto.snapshot.*;
import com.flashticket.ticket_service.enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "tbl_checkin_attempts", schema = "ticket_schema")
@Getter
@Setter
@NoArgsConstructor
public class CheckinAttempt {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "ticket_id", nullable = true)
  private UUID ticketId;

  @Column(name = "request_key", nullable = false)
  private UUID requestKey;

  @Column(name = "replay_of", nullable = true)
  private UUID replayOf;

  @Column(name = "actor_subject", nullable = false, columnDefinition = "nonempty_text")
  private String actorSubject;

  @Column(name = "requested_event_id", nullable = false)
  private UUID requestedEventId;

  @Column(name = "request_hash", nullable = false, columnDefinition = "nonempty_text")
  private String requestHash;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Column(name = "result_code", nullable = false, columnDefinition = "nonempty_text")
  private String resultCode;

  @Column(name = "succeeded", nullable = false)
  private Boolean succeeded;

  @Column(name = "correlation_id", nullable = false, columnDefinition = "nonempty_text")
  private String correlationId;
}
