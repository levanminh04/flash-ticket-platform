package com.flashticket.payment_service.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "tbl_event_refund_runs", schema = "payment_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventRefundRun {
  @Setter
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Setter
  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Setter
  @Column(name = "source_cancellation_id", nullable = false)
  private UUID sourceCancellationId;

  @Setter
  @Column(name = "selection_cursor", nullable = true)
  private String selectionCursor;

  @Setter
  @Column(name = "selection_complete", nullable = false)
  private Boolean selectionComplete;

  @Setter
  @Column(name = "selected_order_count", nullable = false)
  private Long selectedOrderCount;

  @Setter
  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Setter
  @Column(name = "completed_at", nullable = true)
  private Instant completedAt;

  @Setter
  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @OneToMany(mappedBy = "run", fetch = FetchType.LAZY)
  private List<EventRefundItem> items = new ArrayList<>();
}
