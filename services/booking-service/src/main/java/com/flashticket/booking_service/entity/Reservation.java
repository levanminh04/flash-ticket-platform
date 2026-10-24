package com.flashticket.booking_service.entity;

import com.flashticket.booking_service.enums.ReservationState;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_reservations", schema = "booking_schema")
@Getter
@Setter
@NoArgsConstructor
public class Reservation {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, unique = true)
  private Order order;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "buyer_subject", nullable = false)
  private String buyerSubject;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private ReservationState state;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "reservation", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
  private List<ReservationAllocation> allocations = new ArrayList<>();

  public void addAllocation(ReservationAllocation allocation) {
    allocation = Objects.requireNonNull(allocation, "allocation");
    allocations.add(allocation);
    allocation.setReservation(this);
  }
}
