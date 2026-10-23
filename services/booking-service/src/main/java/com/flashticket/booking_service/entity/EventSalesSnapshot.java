package com.flashticket.booking_service.entity;

import com.flashticket.booking_service.dto.snapshot.SalesConfiguration;
import com.flashticket.booking_service.enums.EventState;
import com.flashticket.booking_service.enums.SalesMode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tbl_event_sales_snapshots", schema = "booking_schema")
@Getter
@Setter
@NoArgsConstructor
public class EventSalesSnapshot {
  @Id
  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "organizer_subject", nullable = false)
  private String organizerSubject;

  @Enumerated(EnumType.STRING)
  @Column(name = "sales_mode", nullable = false)
  private SalesMode salesMode;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_status", nullable = false)
  private EventState eventStatus;

  @Column(name = "sale_starts_at", nullable = false)
  private Instant saleStartsAt;

  @Column(name = "sale_ends_at", nullable = false)
  private Instant saleEndsAt;

  @Column(name = "purchase_limit", nullable = false)
  private Integer purchaseLimit;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "configuration_snapshot", nullable = false, columnDefinition = "jsonb")
  private SalesConfiguration configurationSnapshot;

  @Column(name = "source_config_version", nullable = false)
  private Long sourceConfigVersion;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;
}
