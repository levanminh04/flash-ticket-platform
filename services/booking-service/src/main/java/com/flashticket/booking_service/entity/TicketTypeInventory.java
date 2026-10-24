package com.flashticket.booking_service.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_ticket_type_inventory", schema = "booking_schema")
@Getter
@Setter
@NoArgsConstructor
public class TicketTypeInventory {
  @Id
  @Column(name = "ticket_type_id", nullable = false)
  private UUID ticketTypeId;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

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
