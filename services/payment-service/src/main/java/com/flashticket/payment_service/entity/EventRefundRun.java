package com.flashticket.payment_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_event_refund_runs", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @Setter(AccessLevel.NONE)
  @OneToMany(mappedBy = "run", fetch = FetchType.LAZY)
  private List<EventRefundItem> items = new ArrayList<>();
}
