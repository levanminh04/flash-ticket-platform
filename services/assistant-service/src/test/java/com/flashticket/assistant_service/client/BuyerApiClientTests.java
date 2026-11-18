package com.flashticket.assistant_service.client;

import static org.junit.jupiter.api.Assertions.*;

import com.flashticket.assistant_service.config.AssistantConfiguration;
import com.flashticket.assistant_service.dto.EventSearchRequest;
import com.flashticket.assistant_service.exception.AssistantProblem;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class BuyerApiClientTests {
  private HttpServer server;
  private BuyerApiClient client;
  private final UUID eventId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private volatile int status = 200;
  private volatile String body;
  private volatile String method;
  private volatile String path;
  private volatile String authorization;
  private volatile int requests;
  private boolean gzip;

  @BeforeEach
  void setUp() {
    server = assertDoesNotThrow(() -> HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
    server.createContext("/", this::handleFixture); server.start();
    client = new BuyerApiClient(new AssistantConfiguration().buyerRestClient(), JsonMapper.builder().build());
    URI origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    ReflectionTestUtils.setField(client, "eventBaseUrl", origin); ReflectionTestUtils.setField(client, "bookingBaseUrl", origin);
    body = "{\"eventId\":\"" + eventId + "\",\"title\":\"Show\",\"mode\":\"QUANTITY\",\"ticketTypes\":[],\"description\":\"buyer@example.test\",\"customerEmail\":\"private@example.test\"}";
  }

  @AfterEach
  void tearDown() { if (server != null) server.stop(0); }

  @Test
  void relaysCurrentJwtOnlyToAllowedGetEndpoints() {
    var jwt = Jwt.withTokenValue("current-token").header("alg", "RS256").subject("buyer").expiresAt(Instant.now().plusSeconds(300)).build();
    var event = client.getEvent(jwt, eventId);
    assertEquals("GET", method); assertEquals("/api/v1/events/" + eventId, path); assertEquals("Bearer current-token", authorization);
    assertEquals("[REDACTED]", event.getDescription()); assertTrue(event.getTicketTypes().isEmpty());
    var projected = JsonMapper.builder().build().writeValueAsString(event); assertFalse(projected.contains("customerEmail"));
    body = "{\"orderId\":\"" + orderId + "\",\"eventId\":\"" + eventId + "\",\"state\":\"COMPLETED\",\"customerEmail\":\"private@example.test\",\"qr\":\"secret\"}";
    var other = Jwt.withTokenValue("other-current-token").header("alg", "RS256").subject("other").expiresAt(Instant.now().plusSeconds(300)).build();
    assertEquals(orderId, client.getMyOrder(other, orderId).getOrderId()); assertEquals("Bearer other-current-token", authorization);
    body = "{\"items\":[],\"page\":0,\"size\":5}"; client.getMyOrders(jwt, 0, ""); assertEquals("/api/v1/orders", path);
    body = "{\"items\":[],\"nextCursor\":null}"; client.searchEvents(jwt, new EventSearchRequest());
    int before = requests;
    status = 302; assertEquals("DOWNSTREAM_UNAVAILABLE", assertThrows(AssistantProblem.class, () -> client.getEvent(jwt, eventId)).getCode()); assertEquals(before + 1, requests);
    ReflectionTestUtils.setField(client, "eventBaseUrl", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/forbidden"));
    assertEquals("DOWNSTREAM_CONFIGURATION_INVALID", assertThrows(AssistantProblem.class, () -> client.getEvent(jwt, eventId)).getCode()); assertEquals(before + 1, requests);
  }

  @Test
  void boundsResponseAndMapsFailuresWithoutLeakingBody() {
    var jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("buyer").expiresAt(Instant.now().plusSeconds(300)).build();
    body = "private@example.test Bearer hidden-secret";
    for (int code : new int[] {401, 403, 404, 429, 500}) {
      status = code;
      var problem = assertThrows(AssistantProblem.class, () -> client.getEvent(jwt, eventId));
      assertEquals(code == 500 ? 503 : code, problem.getStatus().value());
      assertFalse(problem.getSafeDetail().contains("hidden-secret")); assertFalse(problem.getMessage().contains("private@"));
    }
    status = 200; body = "not-json";
    assertEquals("INVALID_DOWNSTREAM_RESPONSE", assertThrows(AssistantProblem.class, () -> client.getEvent(jwt, eventId)).getCode());
    body = "x".repeat(262145);
    assertEquals("DOWNSTREAM_BODY_TOO_LARGE", assertThrows(AssistantProblem.class, () -> client.getEvent(jwt, eventId)).getCode());
    gzip = true;
    assertEquals("DOWNSTREAM_BODY_TOO_LARGE", assertThrows(AssistantProblem.class, () -> client.getEvent(jwt, eventId)).getCode());
    var expired = Jwt.withTokenValue("expired").header("alg", "RS256").subject("buyer").expiresAt(Instant.now().minusSeconds(1)).build();
    int before = requests;
    assertEquals("UNAUTHENTICATED", assertThrows(AssistantProblem.class, () -> client.getEvent(expired, eventId)).getCode()); assertEquals(before, requests);
  }

  private void handleFixture(HttpExchange exchange) throws IOException {
    requests++; method = exchange.getRequestMethod(); path = exchange.getRequestURI().getPath(); authorization = exchange.getRequestHeaders().getFirst("Authorization");
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    if (gzip) {
      var output = new java.io.ByteArrayOutputStream();
      try (var zipped = new java.util.zip.GZIPOutputStream(output)) { zipped.write(bytes); }
      bytes = output.toByteArray(); exchange.getResponseHeaders().set("Content-Encoding", "gzip");
    }
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.getResponseHeaders().set("Location", "/redirect-target");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) { output.write(bytes); }
    exchange.close();
  }
}
