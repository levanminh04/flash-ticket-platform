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
  @org.springframework.test.context.bean.override.mockito.MockitoBean(name = "writeDs")
  com.zaxxer.hikari.HikariDataSource writer;

  @org.springframework.test.context.bean.override.mockito.MockitoBean(name = "readDs")
  com.zaxxer.hikari.HikariDataSource readDs;

  @org.springframework.test.context.bean.override.mockito.MockitoBean(name = "dataSource")
  javax.sql.DataSource dataSource;

  @Test
  void contextLoads() {}
}
