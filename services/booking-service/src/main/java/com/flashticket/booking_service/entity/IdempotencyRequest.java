package com.flashticket.booking_service.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "tbl_idempotency_requests", schema = "booking_schema")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRequest {
  @EmbeddedId private IdempotencyRequestId id;

  @Column(name = "request_hash", nullable = false)
  private String requestHash;

  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Column(name = "response_status", nullable = false)
  private Integer responseStatus;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  // ID được gán trước; version null giúp save() INSERT thay vì ghi đè receipt.
  @Version
  @ColumnDefault("0")
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;
}
