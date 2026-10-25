package com.flashticket.api_gateway.routing;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class VnpayCallbacksDisabledTest {
  @Autowired
  @Qualifier("vnpayCallbackRoutes")
  RouteLocator routes;

  @Autowired ApplicationContext context;

  @Test
  void defaultHasNoCallbackRoutesOrPublicCallbackChain() {
    assertThat(routes.getRoutes().collectList().block()).isEmpty();
    assertThat(context.containsBean("vnpayCallbackSecurity")).isFalse();
  }
}
