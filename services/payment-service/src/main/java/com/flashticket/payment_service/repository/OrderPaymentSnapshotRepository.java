package com.flashticket.payment_service.repository;
import com.flashticket.payment_service.entity.OrderPaymentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
public interface OrderPaymentSnapshotRepository extends JpaRepository<OrderPaymentSnapshot,UUID> {
  interface OrderPaymentAccess { UUID getOrderId(); String getBuyerSubject(); Instant getExpiresAt(); }
  @org.springframework.data.jpa.repository.Query("select s.orderId as orderId, s.buyerSubject as buyerSubject, s.expiresAt as expiresAt from OrderPaymentSnapshot s where s.orderId = :orderId")
  Optional<OrderPaymentAccess> findAccessByOrderId(@org.springframework.data.repository.query.Param("orderId") UUID orderId);
}
