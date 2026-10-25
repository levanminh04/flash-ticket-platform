package com.flashticket.api_gateway.routing;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;

@SpringBootTest(properties = "flashticket.gateway.vnpay-callbacks-enabled=true")
class VnpayCallbackRoutesTest {
  @Autowired
  @Qualifier("vnpayCallbackRoutes")
  RouteLocator routes;

  @Autowired
  @Qualifier("vnpayCallbackSecurity")
  SecurityWebFilterChain security;

  @Test
  void routesOnlyTheTwoExactGetsWithNoRetryOrFallback() {
    var all = routes.getRoutes().collectList().block(Duration.ofSeconds(5));
    assertThat(all).hasSize(2);
    for (var route : all) {
      assertThat(route.getUri().toString()).isEqualTo("lb://payment-service");
      assertThat(route.getFilters()).isEmpty();
      var path =
          route.getId().equals("vnpay-ipn")
              ? "/api/v1/payments/vnpay/ipn"
              : "/api/v1/payments/vnpay/return";
      var get = MockServerWebExchange.from(MockServerHttpRequest.get(path + "?vnp_Test=a%2Bb"));
      var post = MockServerWebExchange.from(MockServerHttpRequest.post(path));
      var wrong = MockServerWebExchange.from(MockServerHttpRequest.get(path + "/extra"));
      assertThat(Mono.from(route.getPredicate().apply(get)).block()).isTrue();
      assertThat(Mono.from(route.getPredicate().apply(post)).block()).isFalse();
      assertThat(Mono.from(route.getPredicate().apply(wrong)).block()).isFalse();
    }
  }

  @Test
  void allowsAnonymousProviderGetButNotPostOrUnrelatedPaths() {
    var get = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/payments/vnpay/ipn"));
    assertThat(security.matches(get).block()).isTrue();
    assertThat(reaches(HttpMethod.GET)).isTrue();
    assertThat(reaches(HttpMethod.POST)).isFalse();
    assertThat(
            security
                .matches(
                    MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/v1/orders/123/payment")))
                .block())
        .isFalse();
  }

  boolean reaches(HttpMethod method) {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.method(method, "/api/v1/payments/vnpay/ipn"));
    var reached = new AtomicBoolean();
    new DefaultWebFilterChain(
            e -> {
              reached.set(true);
              return Mono.empty();
            },
            security.getWebFilters().collectList().block())
        .filter(exchange)
        .block();
    return reached.get();
  }
}
