package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "tbl_event_refund_runs", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor
public class EventRefundRun {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "source_cancellation_id", nullable = false)
  private UUID sourceCancellationId;

  @Column(name = "selection_cursor", nullable = true)
  private String selectionCursor;

  @Column(name = "selection_complete", nullable = false)
  private Boolean selectionComplete;

  @Column(name = "selected_order_count", nullable = false)
  private Long selectedOrderCount;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at", nullable = true)
  private Instant completedAt;

  // Version null trên row mới để Spring Data nhận biết INSERT.
  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;
}
