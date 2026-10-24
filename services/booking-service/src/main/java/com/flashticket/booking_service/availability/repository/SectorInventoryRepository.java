package com.flashticket.booking_service.availability.repository;

import com.flashticket.booking_service.entity.SectorInventory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectorInventoryRepository extends JpaRepository<SectorInventory, UUID> {}
