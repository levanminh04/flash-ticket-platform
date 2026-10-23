package com.flashticket.booking_service.entity;

import com.flashticket.booking_service.enums.SeatState;
import com.flashticket.booking_service.enums.SectorType;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_seat_inventory", schema = "booking_schema")
@Getter
@Setter
@NoArgsConstructor
public class SeatInventory {
  @Id
  @Column(name = "seat_id", nullable = false)
  private UUID seatId;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "sector_id", nullable = false)
  private UUID sectorId;

  @Column(name = "ticket_type_id")
  private UUID ticketTypeId;

  @Column(name = "is_hidden", nullable = false)
  private Boolean isHidden;

  @Enumerated(EnumType.STRING)
  @Column(name = "sector_type", nullable = false)
  private SectorType sectorType;

  @Column(name = "seat_label", nullable = false)
  private String seatLabel;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private SeatState state;

  @Column(name = "current_reservation_id")
  private UUID currentReservationId;

  @Column(name = "reservation_generation", nullable = false)
  private Long reservationGeneration;

  @Column(name = "source_config_version", nullable = false)
  private Long sourceConfigVersion;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;
}
