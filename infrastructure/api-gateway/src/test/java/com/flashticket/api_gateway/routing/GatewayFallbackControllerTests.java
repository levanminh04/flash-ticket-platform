package com.flashticket.api_gateway.routing;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

class GatewayFallbackControllerTests {

  private final WebTestClient client =
      WebTestClient.bindToController(new GatewayFallbackController()).build();

  @Test
  void returnsOnlyTechnicalServiceUnavailableForGet() {
    client
        .get()
        .uri("/fallback/event")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectBody()
        .isEmpty();
  }

  @Test
  void returnsOnlyTechnicalServiceUnavailableForMutationForwards() {
    client
        .post()
        .uri("/fallback/payment")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectBody()
        .isEmpty();
  }
}
