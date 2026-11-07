package com.flashticket.booking_service.availability.repository;

import com.flashticket.booking_service.entity.SeatInventory;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface SeatInventoryRepository extends JpaRepository<SeatInventory, UUID> {
  // Khóa theo thứ tự ID để các đơn không chờ khóa ngược nhau.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<SeatInventory> findLockedBySeatIdInOrderBySeatIdAsc(Collection<UUID> seatIds);
}
