package com.flashticket.payment_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.context.annotation.Import(com.flashticket.payment_service.config.PaymentReadRepositoryTestConfiguration.class)
@SpringBootTest
class PaymentServiceApplicationTests {

  @Test
  void contextLoads() {}
}
