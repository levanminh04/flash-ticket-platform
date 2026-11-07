package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.Refund;
import com.flashticket.payment_service.entity.enums.RefundState;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
  Optional<Refund> findByChargeId(UUID chargeId);

  List<Refund> findAllByOrderIdOrderByCreatedAtAscIdAsc(UUID orderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Refund r where r.id = :id")
  Optional<Refund> findForUpdateById(@Param("id") UUID id);

  @Query("select r.id from Refund r where r.state = :state order by r.createdAt, r.id")
  List<UUID> findIdsByState(@Param("state") RefundState state, Pageable pageable);

  Page<Refund> findAllByState(RefundState state, Pageable pageable);

  @Query(
      """
      select r.id from Refund r
      where r.state = com.flashticket.payment_service.entity.enums.RefundState.PROCESSING
        and exists (select a.id from RefundAttempt a where a.refundId = r.id
          and a.finishedAt is null and a.startedAt <= :startedBefore)
      order by r.createdAt, r.id
      """)
  List<UUID> findIdsToPoll(@Param("startedBefore") Instant startedBefore, Pageable pageable);

  @Query(
      """
      select coalesce(sum(r.amount), 0)
      from Refund r, PaymentConfirmation c, OrderPaymentSnapshot s
      where r.chargeId = c.chargeId and c.orderId = s.orderId and s.eventId = :eventId
        and r.state = com.flashticket.payment_service.entity.enums.RefundState.SUCCEEDED
      """)
  BigDecimal sumSucceededConfirmedAmountByEventId(@Param("eventId") UUID eventId);

  @Query(
      """
      select count(r) from Refund r, OrderPaymentSnapshot s
      where r.orderId = s.orderId and s.eventId = :eventId
        and r.state <> com.flashticket.payment_service.entity.enums.RefundState.SUCCEEDED
      """)
  long countUnfinishedByEventId(@Param("eventId") UUID eventId);
}
