package com.flashticket.booking_service.entity;

import com.flashticket.booking_service.dto.snapshot.SelectionSnapshot;
import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.SalesMode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tbl_order_items", schema = "booking_schema")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "ticket_type_id", nullable = false)
  private UUID ticketTypeId;

  @Column(name = "sector_id")
  private UUID sectorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "sales_mode", nullable = false)
  private SalesMode salesMode;

  @Column(name = "ticket_type_name", nullable = false)
  private String ticketTypeName;

  @Column(name = "quantity", nullable = false)
  private Integer quantity;

  @Column(name = "unit_price", nullable = false, precision = 19, scale = 0)
  private BigDecimal unitPrice;

  @Column(name = "line_subtotal", nullable = false, precision = 19, scale = 0)
  private BigDecimal lineSubtotal;

  @Enumerated(EnumType.STRING)
  @Column(name = "currency", nullable = false)
  private Currency currency;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "selection_snapshot", nullable = false, columnDefinition = "jsonb")
  private SelectionSnapshot selectionSnapshot;

  @OneToMany(mappedBy = "orderItem", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
  private List<OrderItemSeat> seatAssignments = new ArrayList<>();

  public void addSeatAssignment(OrderItemSeat assignment) {
    assignment = Objects.requireNonNull(assignment, "assignment");
    seatAssignments.add(assignment);
    assignment.setOrderItem(this);
  }
}
