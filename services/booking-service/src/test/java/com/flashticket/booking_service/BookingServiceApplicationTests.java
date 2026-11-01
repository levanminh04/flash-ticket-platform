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

@SpringBootTest(
    properties = {
      "spring.rabbitmq.host=${RABBITMQ_HOST}",
      "spring.rabbitmq.port=${RABBITMQ_PORT:5672}",
      "spring.rabbitmq.username=${RABBITMQ_USERNAME}",
      "spring.rabbitmq.password=${RABBITMQ_PASSWORD}",
      "spring.rabbitmq.connection-timeout=5s",
      "spring.rabbitmq.dynamic=false",
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "spring.rabbitmq.listener.direct.auto-startup=false",
      "booking.outbox.enabled=false"
    })
@org.springframework.context.annotation.Import(
    org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration.class)
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
  @org.springframework.beans.factory.annotation.Autowired
  org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

  @Test
  void contextLoads() {
    // Chỉ mở kết nối thật; không bật listener, scheduler hoặc declare queue live.
    try (var connection = rabbitTemplate.getConnectionFactory().createConnection()) {
      org.assertj.core.api.Assertions.assertThat(connection.isOpen()).isTrue();
    }
  }
}
