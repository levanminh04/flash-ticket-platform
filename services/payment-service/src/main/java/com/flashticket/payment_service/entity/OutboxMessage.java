package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "tbl_outbox_messages", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor
public class OutboxMessage {
  @Id
  @Column(name = "message_id", nullable = false)
  private UUID messageId;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "aggregate_version", nullable = false)
  private Long aggregateVersion;

  @Column(name = "message_type", nullable = false)
  private String messageType;

  @Column(name = "schema_version", nullable = false)
  private Integer schemaVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private JsonNode payload;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "correlation_id", nullable = false)
  private UUID correlationId;

  @Column(name = "causation_id", nullable = true)
  private UUID causationId;

  @Column(name = "traceparent", nullable = true)
  private String traceparent;

  @Column(name = "published_at", nullable = true)
  private Instant publishedAt;

  @Column(name = "attempts", nullable = false)
  private Integer attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error_code", nullable = true)
  private String lastErrorCode;
}
