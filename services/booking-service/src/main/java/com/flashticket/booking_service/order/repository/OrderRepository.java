package com.flashticket.booking_service.order.repository;

import com.flashticket.booking_service.entity.Order;
import com.flashticket.booking_service.enums.OrderState;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {
  @Query(
      """
      select o.id from Order o
      where o.lifecycleState = :state and o.expiresAt <= :cutoff
        and (:afterOrderId is null or o.id > :afterOrderId)
      order by o.id asc
      """)
  List<UUID> findExpiredOrderIds(
      @Param("state") OrderState state,
      @Param("cutoff") Instant cutoff,
      @Param("afterOrderId") UUID afterOrderId,
      Pageable pageable);

  @Query("select o.eventId from Order o where o.id = :orderId")
  Optional<UUID> findEventIdById(@Param("orderId") UUID orderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Order> findLockedById(UUID id);

  @Query("select o.eventId from Order o where o.id = :orderId and o.buyerSubject = :buyerSubject")
  Optional<UUID> findEventIdByIdAndBuyerSubject(
      @Param("orderId") UUID orderId, @Param("buyerSubject") String buyerSubject);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Order> findLockedByIdAndBuyerSubject(UUID id, String buyerSubject);

  // Lấy giờ thực sau khi chờ khóa để kiểm tra thời hạn bán vé.
  @Query(value = "select clock_timestamp()", nativeQuery = true)
  Instant currentDatabaseTime();

  // Chỉ giới hạn chờ khóa trong transaction tạo đơn hiện tại.
  @Query(value = "select set_config('lock_timeout', :timeout, true)", nativeQuery = true)
  String configureTransactionLockTimeout(@Param("timeout") String timeout);
}
