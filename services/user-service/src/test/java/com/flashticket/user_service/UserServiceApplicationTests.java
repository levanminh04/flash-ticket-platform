package com.flashticket.user_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(LocalTestInfrastructure.class)
class UserServiceApplicationTests {

  @Test
  void contextLoads() {}
}
