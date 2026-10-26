package com.flashticket.ticket_service;

import com.flashticket.ticket_service.access.repository.EventAccessSnapshotRepository;
import com.flashticket.ticket_service.checkin.repository.CheckinAttemptRepository;
import com.flashticket.ticket_service.delivery.repository.TicketDeliveryRepository;
import com.flashticket.ticket_service.issuance.repository.IssuanceRepository;
import com.flashticket.ticket_service.messaging.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@MockitoBean(
    types = {
      EventAccessSnapshotRepository.class,
      IssuanceRepository.class,
      TicketDeliveryRepository.class,
      CheckinAttemptRepository.class,
      InboxMessageRepository.class,
      OutboxMessageRepository.class,
      JwtDecoder.class
    })
class TicketServiceApplicationTests {
  @Test
  void contextLoads() {}
}
