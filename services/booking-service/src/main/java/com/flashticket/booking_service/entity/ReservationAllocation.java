package com.flashticket.booking_service.entity;

import com.flashticket.booking_service.enums.ReservationState;
import com.flashticket.booking_service.enums.ResourceKind;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_reservation_allocations", schema = "booking_schema")
@Getter
@Setter
@NoArgsConstructor
public class ReservationAllocation {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reservation_id", nullable = false)
  private Reservation reservation;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_item_id", nullable = false)
  private OrderItem orderItem;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Enumerated(EnumType.STRING)
  @Column(name = "resource_kind", nullable = false)
  private ResourceKind resourceKind;

  @Column(name = "ticket_type_id")
  private UUID ticketTypeId;

  @Column(name = "sector_id")
  private UUID sectorId;

  @Column(name = "seat_id")
  private UUID seatId;

  @Column(name = "quantity", nullable = false)
  private Integer quantity;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private ReservationState state;
}
