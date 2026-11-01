package com.flashticket.booking_service.entity;

import com.flashticket.booking_service.enums.SectorType;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_sector_inventory", schema = "booking_schema")
@Getter
@Setter
@NoArgsConstructor
public class SectorInventory {
  @Id
  @Column(name = "sector_id", nullable = false)
  private UUID sectorId;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Enumerated(EnumType.STRING)
  @Column(name = "sector_type", nullable = false)
  private SectorType sectorType;

  @Column(name = "configured_capacity", nullable = false)
  private Long configuredCapacity;

  @Column(name = "held", nullable = false)
  private Long held;

  @Column(name = "purchased", nullable = false)
  private Long purchased;

  @Column(name = "source_config_version", nullable = false)
  private Long sourceConfigVersion;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;
}
