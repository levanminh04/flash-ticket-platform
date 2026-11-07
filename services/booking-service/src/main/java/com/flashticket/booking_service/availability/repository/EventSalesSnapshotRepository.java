package com.flashticket.booking_service.availability.repository;

import com.flashticket.booking_service.entity.EventSalesSnapshot;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EventSalesSnapshotRepository extends JpaRepository<EventSalesSnapshot, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<EventSalesSnapshot> findByEventId(UUID eventId);

  // Nhiều đơn được đọc cùng lúc; consumer cập nhật phải chờ các đơn kết thúc.
  @Lock(LockModeType.PESSIMISTIC_READ)
  Optional<EventSalesSnapshot> findForBookingByEventId(UUID eventId);
}
