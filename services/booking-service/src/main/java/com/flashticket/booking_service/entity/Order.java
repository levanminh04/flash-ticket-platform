package com.flashticket.booking_service.entity;

import com.flashticket.booking_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.OrderState;
import com.flashticket.booking_service.enums.SalesMode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_orders", schema = "booking_schema")
@Getter
@Setter
@NoArgsConstructor
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "buyer_subject", nullable = false)
  private String buyerSubject;

  @Enumerated(EnumType.STRING)
  @Column(name = "sales_mode", nullable = false)
  private SalesMode salesMode;

  @Column(name = "sector_id")
  private UUID sectorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "lifecycle_state", nullable = false)
  private OrderState lifecycleState;

  @Column(name = "customer_email", nullable = false)
  private String customerEmail;

  @Column(name = "payment_frozen_at")
  private Instant paymentFrozenAt;

  @Column(name = "payment_snapshot_version")
  private Long paymentSnapshotVersion;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "frozen_purchase_snapshot", columnDefinition = "jsonb")
  private PurchaseSnapshot frozenPurchaseSnapshot;

  @Column(name = "accepted_charge_id")
  private UUID acceptedChargeId;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "subtotal", nullable = false, precision = 19, scale = 0)
  private BigDecimal subtotal;

  @Column(name = "discount", nullable = false, precision = 19, scale = 0)
  private BigDecimal discount;

  @Column(name = "total", nullable = false, precision = 19, scale = 0)
  private BigDecimal total;

  @Enumerated(EnumType.STRING)
  @Column(name = "currency", nullable = false)
  private Currency currency;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "source_config_version", nullable = false)
  private Long sourceConfigVersion;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
  private List<OrderItem> items = new ArrayList<>();

  public void addItem(OrderItem item) {
    item = Objects.requireNonNull(item, "item");
    items.add(item);
    item.setOrder(this);
  }
}
