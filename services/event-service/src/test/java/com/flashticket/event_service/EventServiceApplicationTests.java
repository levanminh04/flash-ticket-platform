package com.flashticket.event_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.data.jpa.repositories.enabled=false",
      "management.health.db.enabled=false"
    })
@org.springframework.context.annotation.Import(IsolatedInfrastructure.class)
class EventServiceApplicationTests {
  @org.springframework.test.context.bean.override.mockito.MockitoBean(
      name = "eventWriterDataSource")
  com.zaxxer.hikari.HikariDataSource writer;

  @Test
  void contextLoads() {}
}
