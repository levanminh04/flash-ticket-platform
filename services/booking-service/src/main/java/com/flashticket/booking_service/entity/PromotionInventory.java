package com.flashticket.booking_service.entity;

import com.flashticket.booking_service.dto.snapshot.PromotionConfigurationSnapshot;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tbl_promotion_inventory", schema = "booking_schema")
@Getter
@Setter
@NoArgsConstructor
public class PromotionInventory {
  @Id
  @Column(name = "promotion_id", nullable = false)
  private UUID promotionId;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "configured_limit", nullable = false)
  private Long configuredLimit;

  @Column(name = "per_user_limit", nullable = false)
  private Integer perUserLimit;

  @Column(name = "held", nullable = false)
  private Long held;

  @Column(name = "used", nullable = false)
  private Long used;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "configuration_snapshot", nullable = false, columnDefinition = "jsonb")
  private PromotionConfigurationSnapshot configurationSnapshot;

  @Column(name = "source_config_version", nullable = false)
  private Long sourceConfigVersion;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;
}
