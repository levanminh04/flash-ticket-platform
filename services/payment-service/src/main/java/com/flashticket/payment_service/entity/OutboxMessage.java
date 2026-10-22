package com.flashticket.payment_service.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "tbl_outbox_messages", schema = "payment_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessage {
  @Setter
  @Id
  @Column(name = "message_id", nullable = false)
  private UUID messageId;

  @Setter
  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Setter
  @Column(name = "aggregate_version", nullable = false)
  private Long aggregateVersion;

  @Setter
  @Column(
      name = "message_type",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String messageType;

  @Setter
  @Column(name = "schema_version", nullable = false)
  private Integer schemaVersion;

  @Setter
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private JsonNode payload;

  @Setter
  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Setter
  @Column(name = "correlation_id", nullable = false)
  private UUID correlationId;

  @Setter
  @Column(name = "causation_id", nullable = true)
  private UUID causationId;

  @Setter
  @Column(name = "traceparent", nullable = true)
  private String traceparent;

  @Setter
  @Column(name = "published_at", nullable = true)
  private Instant publishedAt;

  @Setter
  @Column(name = "attempts", nullable = false)
  private Integer attempts;

  @Setter
  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Setter
  @Column(name = "last_error_code", nullable = true)
  private String lastErrorCode;
}
