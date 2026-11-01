package com.flashticket.api_gateway.routing;

import com.flashticket.api_gateway.web.GatewayHttpHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

class GatewayFallbackControllerTests {

  private final WebTestClient client =
      WebTestClient.bindToController(new GatewayFallbackController(new GatewayHttpHandler()))
          .build();

  @Test
  void returnsOnlyTechnicalServiceUnavailableForGet() {
    client
        .get()
        .uri("/fallback/event")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("DEPENDENCY_UNAVAILABLE");
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
        .jsonPath("$.code")
        .isEqualTo("DEPENDENCY_UNAVAILABLE");
  }
}
