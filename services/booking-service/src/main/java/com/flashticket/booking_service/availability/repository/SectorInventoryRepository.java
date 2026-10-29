package com.flashticket.booking_service.availability.repository;

import com.flashticket.booking_service.entity.SectorInventory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface SectorInventoryRepository extends JpaRepository<SectorInventory, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<SectorInventory> findLockedBySectorId(UUID sectorId);
}
