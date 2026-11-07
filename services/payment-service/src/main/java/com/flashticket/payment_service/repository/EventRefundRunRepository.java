package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.EventRefundRun;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRefundRunRepository extends JpaRepository<EventRefundRun, UUID> {
  Optional<EventRefundRun> findBySourceCancellationId(UUID cancellationId);

  Optional<EventRefundRun> findFirstByEventIdOrderByStartedAtDesc(UUID eventId);

  @Query(
      "select r.id from EventRefundRun r where r.selectionComplete = false order by r.startedAt,"
          + " r.id")
  List<UUID> findIdsToSelect(Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from EventRefundRun r where r.id = :id")
  Optional<EventRefundRun> findForUpdateById(@Param("id") UUID id);
}
