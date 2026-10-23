package com.flashticket.booking_service.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_order_item_seats", schema = "booking_schema")
@Getter
@Setter
@NoArgsConstructor
public class OrderItemSeat {
  @EmbeddedId private OrderItemSeatId id;

  @MapsId("orderItemId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_item_id", nullable = false)
  private OrderItem orderItem;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "sector_id", nullable = false)
  private UUID sectorId;
}
