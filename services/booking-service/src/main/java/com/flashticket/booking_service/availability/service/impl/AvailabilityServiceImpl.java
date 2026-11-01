package com.flashticket.booking_service.availability.service.impl;

import com.flashticket.booking_service.availability.repository.EventSalesSnapshotRepository;
import com.flashticket.booking_service.availability.repository.PurchaseLimitRepository;
import com.flashticket.booking_service.availability.repository.SeatInventoryRepository;
import com.flashticket.booking_service.availability.repository.SectorInventoryRepository;
import com.flashticket.booking_service.availability.repository.TicketTypeInventoryRepository;
import com.flashticket.booking_service.availability.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AvailabilityServiceImpl implements AvailabilityService {
  private final EventSalesSnapshotRepository eventSalesSnapshotRepository;
  private final TicketTypeInventoryRepository ticketTypeInventoryRepository;
  private final SectorInventoryRepository sectorInventoryRepository;
  private final SeatInventoryRepository seatInventoryRepository;
  private final PurchaseLimitRepository purchaseLimitRepository;
}
