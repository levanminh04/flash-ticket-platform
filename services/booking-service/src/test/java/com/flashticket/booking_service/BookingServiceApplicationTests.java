package com.flashticket.booking_service;

import com.flashticket.booking_service.availability.repository.EventSalesSnapshotRepository;
import com.flashticket.booking_service.availability.repository.PurchaseLimitRepository;
import com.flashticket.booking_service.availability.repository.SeatInventoryRepository;
import com.flashticket.booking_service.availability.repository.SectorInventoryRepository;
import com.flashticket.booking_service.availability.repository.TicketTypeInventoryRepository;
import com.flashticket.booking_service.messaging.repository.InboxMessageRepository;
import com.flashticket.booking_service.messaging.repository.OutboxMessageRepository;
import com.flashticket.booking_service.order.repository.IdempotencyRequestRepository;
import com.flashticket.booking_service.order.repository.OrderRepository;
import com.flashticket.booking_service.promotion.repository.PromotionInventoryRepository;
import com.flashticket.booking_service.promotion.repository.PromotionUsageRepository;
import com.flashticket.booking_service.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@MockitoBean(
    types = {
      EventSalesSnapshotRepository.class,
      PurchaseLimitRepository.class,
      SeatInventoryRepository.class,
      SectorInventoryRepository.class,
      TicketTypeInventoryRepository.class,
      InboxMessageRepository.class,
      OutboxMessageRepository.class,
      IdempotencyRequestRepository.class,
      OrderRepository.class,
      PromotionInventoryRepository.class,
      PromotionUsageRepository.class,
      ReservationRepository.class
    })
class BookingServiceApplicationTests {

  @Test
  void contextLoads() {}
}
