package com.flashticket.assistant_service.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.flashticket.assistant_service.AssistantWorkspace;
import com.flashticket.assistant_service.config.AssistantConfiguration;
import com.flashticket.assistant_service.dto.EventSearchRequest;
import com.flashticket.assistant_service.exception.AssistantProblem;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class BuyerApiClientTests {
  private HttpServer server;
  private AssistantApiClient client;
  private final UUID eventId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private volatile int status = 200;
  private volatile String body;
  private volatile String method;
  private volatile String path;
  private volatile String query;
  private volatile String authorization;
  private volatile int requests;
  private boolean gzip;

  @BeforeEach
  void setUp() {
    server = assertDoesNotThrow(() -> HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
    server.createContext("/", this::handleFixture); server.start();
    client = new AssistantApiClient(new AssistantConfiguration().buyerRestClient(), JsonMapper.builder().build());
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
    var order = client.getMyOrder(other, orderId);
    assertEquals(orderId, order.getOrderId()); assertEquals("Bearer other-current-token", authorization);
    assertEquals("GET", method); assertEquals("/api/v1/orders/" + orderId, path);
    var orderJson = JsonMapper.builder().build().writeValueAsString(order);
    assertFalse(orderJson.contains("private@example.test")); assertFalse(orderJson.contains("\"qr\""));
    status = 404;
    var missing = assertThrows(AssistantProblem.class, () -> client.getMyOrder(jwt, orderId));
    assertEquals(404, missing.getStatus().value()); assertFalse(missing.getSafeDetail().contains("private@example.test"));
    status = 200;
    body = "{\"items\":[],\"page\":0,\"size\":5}"; client.getMyOrders(jwt, 0, ""); assertEquals("/api/v1/orders", path);
    assertEquals("GET", method); assertEquals("Bearer current-token", authorization); assertEquals("page=0&size=5", query);
    client.getMyOrders(jwt, 2, "COMPLETED"); assertEquals("page=2&size=5&state=COMPLETED", query);
    body = "{\"items\":[],\"nextCursor\":null}";
    var search = new EventSearchRequest();
    for (String cursor : new String[] {null, "", " \t"}) {
      search.setCursor(cursor);
      client.searchEvents(jwt, search);
      assertEquals("size=5&includePast=false", query);
    }
    String cursor = "MjAyNi0xMS0xOFQwMDowMDowMFp8MTExMTExMTEtMTExMS00MTExLTgxMTEtMTExMTExMTExMTEx";
    search.setCursor(cursor);
    client.searchEvents(jwt, search);
    assertEquals("size=5&includePast=false&cursor=" + cursor, query);
    int before = requests;
    status = 302; assertEquals("DOWNSTREAM_UNAVAILABLE", assertThrows(AssistantProblem.class, () -> client.getEvent(jwt, eventId)).getCode()); assertEquals(before + 1, requests);
    ReflectionTestUtils.setField(client, "eventBaseUrl", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/forbidden"));
    assertEquals("DOWNSTREAM_CONFIGURATION_INVALID", assertThrows(AssistantProblem.class, () -> client.getEvent(jwt, eventId)).getCode()); assertEquals(before + 1, requests);
  }

  @Test
  void boundsResponseAndMapsFailuresWithoutLeakingBody() {
    var jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("buyer").expiresAt(Instant.now().plusSeconds(300)).build();
    var logger = (Logger) LoggerFactory.getLogger(AssistantApiClient.class);
    var logs = new ListAppender<ILoggingEvent>();
    logs.start();
    logger.addAppender(logs);
    try {
      body = "private@example.test Bearer hidden-secret";
      for (int code : new int[] {401, 403, 404, 429, 500}) {
        status = code;
        var problem = assertThrows(AssistantProblem.class, () -> client.getEvent(jwt, eventId));
        assertEquals(code == 500 ? 503 : code, problem.getStatus().value());
        if (code == 500) assertEquals("DOWNSTREAM_UNAVAILABLE", problem.getCode());
        assertFalse(problem.getSafeDetail().contains("hidden-secret")); assertFalse(problem.getMessage().contains("private@"));
      }

      var builder = RestClient.builder();
      var transport = MockRestServiceServer.bindTo(builder).build();
      URI origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
      transport.expect(requestTo(origin + "/api/v1/events/" + eventId))
          .andRespond(withException(new SocketTimeoutException("fixture-sensitive-timeout-message")));
      var timeoutClient = new AssistantApiClient(builder.build(), JsonMapper.builder().build());
      ReflectionTestUtils.setField(timeoutClient, "eventBaseUrl", origin);
      ReflectionTestUtils.setField(timeoutClient, "bookingBaseUrl", origin);
      var timeout = assertThrows(AssistantProblem.class, () -> timeoutClient.getEvent(jwt, eventId));
      assertEquals(503, timeout.getStatus().value());
      assertEquals("DOWNSTREAM_UNAVAILABLE", timeout.getCode());
      transport.verify();

      assertEquals(2, logs.list.size());
      assertTrue(logs.list.getFirst().getFormattedMessage().matches(
          "Assistant API call failed: operation=getEvent reasonCode=DOWNSTREAM_UNAVAILABLE downstreamStatus=500 durationMs=[0-9]+"));
      assertTrue(logs.list.getLast().getFormattedMessage().matches(
          "Assistant API call failed: operation=getEvent reasonCode=DOWNSTREAM_UNAVAILABLE errorType=ResourceAccessException causeType=SocketTimeoutException durationMs=[0-9]+"));
      status = 400;
      String cursorError = "{\"code\":\"INVALID_CURSOR\",\"message\":\"private@example.test Bearer hidden-secret\"}";
      String[] errorBodies = {
          cursorError,
          "{\"code\":\"INVALID_REQUEST\"}",
          "{\"code\":\"private@example.test\"}",
          cursorError + " trailing",
          cursorError + " ".repeat(2048)
      };
      String[] expectedCodes = {" downstreamCode=INVALID_CURSOR", " downstreamCode=INVALID_REQUEST", "", "", ""};
      for (int i = 0; i < errorBodies.length; i++) {
        body = errorBodies[i];
        var rejection = assertThrows(AssistantProblem.class, () -> client.searchEvents(jwt, new EventSearchRequest()));
        assertEquals(503, rejection.getStatus().value());
        assertEquals("DOWNSTREAM_UNAVAILABLE", rejection.getCode());
        assertFalse(rejection.getSafeDetail().contains("hidden-secret"));
        assertEquals(3 + i, logs.list.size());
        assertTrue(logs.list.getLast().getFormattedMessage().matches(
            "Assistant API call failed: operation=searchEvents reasonCode=DOWNSTREAM_UNAVAILABLE downstreamStatus=400"
                + expectedCodes[i] + " durationMs=[0-9]+"));
      }
      body = cursorError;
      assertEquals(503, assertThrows(AssistantProblem.class, () -> client.getEvent(jwt, eventId)).getStatus().value());
      assertEquals(3 + errorBodies.length, logs.list.size());
      assertTrue(logs.list.getLast().getFormattedMessage().matches(
          "Assistant API call failed: operation=getEvent reasonCode=DOWNSTREAM_UNAVAILABLE downstreamStatus=400 durationMs=[0-9]+"));
      for (ILoggingEvent log : logs.list) {
        assertEquals(Level.WARN, log.getLevel());
        assertNull(log.getThrowableProxy());
      }
    } finally {
      logger.detachAppender(logs);
      logs.stop();
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

  @Test
  void mapsInvalidDateRangeOnlyForBookingSalesReports() {
    var jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("fixture-user")
        .expiresAt(Instant.now().plusSeconds(300)).build();
    var from = LocalDate.parse("2026-11-18");
    var to = LocalDate.parse("2026-11-01");
    String rangeError = "{\"code\":\"INVALID_DATE_RANGE\",\"message\":\"private@example.test Bearer hidden-secret\"}";
    status = 400;
    body = rangeError;
    var admin = assertThrows(AssistantProblem.class, () -> client.getPlatformSalesSummary(jwt, from, to));
    var organizer = assertThrows(AssistantProblem.class, () -> client.getOrganizerSalesSummary(jwt, from, to));
    for (var rejection : new AssistantProblem[] {admin, organizer}) {
      assertEquals(400, rejection.getStatus().value());
      assertEquals("INVALID_DATE_RANGE", rejection.getCode());
      assertFalse(rejection.getSafeDetail().contains("hidden-secret"));
    }
    assertEquals("DOWNSTREAM_UNAVAILABLE", assertThrows(AssistantProblem.class,
        () -> client.searchEvents(jwt, new EventSearchRequest())).getCode());
    assertEquals("DOWNSTREAM_UNAVAILABLE", assertThrows(AssistantProblem.class,
        () -> client.getMyOrder(jwt, orderId)).getCode());
    for (String invalidBody : new String[] {"{\"code\":\"INVALID_REQUEST\"}", rangeError + " {}", rangeError + " ".repeat(2048)}) {
      body = invalidBody;
      assertEquals("DOWNSTREAM_UNAVAILABLE", assertThrows(AssistantProblem.class,
          () -> client.getPlatformSalesSummary(jwt, from, to)).getCode());
    }
    body = rangeError;
    status = 500;
    assertEquals("DOWNSTREAM_UNAVAILABLE", assertThrows(AssistantProblem.class,
        () -> client.getPlatformSalesSummary(jwt, from, to)).getCode());
  }

  @Test
  void readsSalesReportsOnlyForCurrentOrganizer() {
    var jwt = Jwt.withTokenValue("organizer-token").header("alg", "RS256").subject("organizer-1")
        .expiresAt(Instant.now().plusSeconds(300)).build();
    LocalDate from = LocalDate.parse("2026-11-02");
    LocalDate to = LocalDate.parse("2026-11-08");
    body = """
        {"soldRevenue":"9007199254740993","soldTicketCount":4,"organizerSubject":"organizer-1",
         "from":"2026-11-02","to":"2026-11-08","currency":"VND","byState":[]}
        """;
    var summary = client.getOrganizerSalesSummary(jwt, from, to);
    assertEquals("9007199254740993", summary.getSoldRevenue());
    assertEquals(from, summary.getFrom());
    assertEquals("GET", method);
    assertEquals("Bearer organizer-token", authorization);
    assertEquals("/api/v1/organizers/organizer-1/sales/summary", path);
    assertEquals("from=2026-11-02&to=2026-11-08", query);
    assertFalse(JsonMapper.builder().build().writeValueAsString(summary).contains("organizerSubject"));

    body = """
        {"items":[{"eventId":"%s","eventTitle":"Workshop","soldRevenue":"120000"}],"currency":"VND"}
        """.formatted(eventId);
    var topEvents = client.getOrganizerTopEvents(jwt, null, null);
    assertEquals(eventId, topEvents.getItems().getFirst().getEventId());
    assertEquals("/api/v1/organizers/organizer-1/events/sales-statistics", path);
    assertEquals("page=0&size=5&sortBy=soldRevenue&direction=DESC", query);

    body = """
        {"groupBy":"WEEK","items":[{"periodStart":"2026-11-02","periodEnd":"2026-11-08","soldTicketCount":2}]}
        """;
    var trend = client.getOrganizerSalesTrend(jwt, from, to, "WEEK");
    assertEquals(2L, trend.getItems().getFirst().getSoldTicketCount());
    assertEquals("/api/v1/organizers/organizer-1/sales/chart", path);
    assertEquals("from=2026-11-02&to=2026-11-08&groupBy=WEEK", query);

    var ticketTypes = new java.util.StringJoiner(",");
    for (int quantity = 1; quantity <= 11; quantity++) {
      ticketTypes.add("""
          {"ticketTypeName":"Hạng %d","soldQuantity":%d}
          """.formatted(quantity, quantity));
    }
    body = """
        {"eventId":"%s","soldTicketCount":66,"byTicketType":[%s]}
        """.formatted(eventId, ticketTypes);
    var eventSales = client.getOrganizerEventSales(jwt, eventId, null, to);
    assertEquals("/api/v1/organizer/events/" + eventId + "/sales-summary", path);
    assertEquals("to=2026-11-08", query);
    assertEquals(66L, eventSales.getSoldTicketCount());
    assertEquals(10, eventSales.getByTicketType().size());
    assertEquals("Hạng 11", eventSales.getByTicketType().getFirst().getTicketTypeName());
    assertEquals("Hạng 2", eventSales.getByTicketType().getLast().getTicketTypeName());
    assertTrue(eventSales.isTicketTypesTruncated());
    status = 404;
    var missing = assertThrows(AssistantProblem.class, () -> client.getOrganizerEventSales(jwt, eventId, null, null));
    assertEquals(404, missing.getStatus().value());
  }

  @Test
  void readsOnlyAllowedAdminReports() {
    var jwt = Jwt.withTokenValue("admin-token").header("alg", "RS256").subject("admin")
        .expiresAt(Instant.now().plusSeconds(300)).build();
    LocalDate from = LocalDate.parse("2026-11-02");
    LocalDate to = LocalDate.parse("2026-11-08");
    body = """
        {"soldRevenue":"9007199254740993","soldTicketCount":4,"customerEmail":"private@example.test"}
        """;
    var summary = client.getPlatformSalesSummary(jwt, from, to);
    assertEquals("9007199254740993", summary.getSoldRevenue());
    assertFalse(JsonMapper.builder().build().writeValueAsString(summary).contains("customerEmail"));
    assertEquals("/api/v1/admin/sales/summary", path);
    assertEquals("from=2026-11-02&to=2026-11-08", query);
    assertEquals("GET", method);
    assertEquals("Bearer admin-token", authorization);

    body = "{\"groupBy\":\"WEEK\",\"items\":[]}";
    assertEquals("WEEK", client.getPlatformSalesTrend(jwt, from, to, "WEEK").getGroupBy());
    assertEquals("/api/v1/admin/sales/chart", path);
    assertEquals("from=2026-11-02&to=2026-11-08&groupBy=WEEK", query);

    body = """
        {"items":[{"eventId":"%s","soldRevenue":"120000"}]}
        """.formatted(eventId);
    assertEquals(eventId, client.getPlatformTopEvents(jwt, null, null).getItems().getFirst().getEventId());
    assertEquals("/api/v1/admin/events/sales-statistics", path);
    assertEquals("page=0&size=5&sortBy=soldRevenue&direction=DESC", query);

    body = """
        {"eventId":"%s","byTicketType":[%s{"soldQuantity":11}]}
        """.formatted(eventId, "{\"soldQuantity\":1},".repeat(10));
    var eventSales = client.getAdminEventSales(jwt, eventId, null, to);
    assertEquals("/api/v1/events/" + eventId + "/sales-statistics", path);
    assertEquals("to=2026-11-08", query);
    assertTrue(eventSales.isTicketTypesTruncated());
    assertEquals(10, eventSales.getByTicketType().size());
    assertEquals(11L, eventSales.getByTicketType().getFirst().getSoldQuantity());

    body = "{\"soldRevenue\":\"120000\",\"organizerSubject\":\"private\"}";
    var organizerSales = client.getAdminOrganizerSalesSummary(jwt, orderId, from, null);
    assertEquals("120000", organizerSales.getSoldRevenue());
    assertFalse(JsonMapper.builder().build().writeValueAsString(organizerSales).contains("organizerSubject"));
    assertEquals("/api/v1/organizers/" + orderId + "/sales/summary", path);
    assertEquals("from=2026-11-02", query);

    body = """
        {"items":[{"eventId":"%s","title":"Workshop","status":"PENDING_APPROVAL",
        "organizerSubject":"private","description":"private"}],"nextCursor":"next_1"}
        """.formatted(eventId);
    var pending = client.listAdminEventsPendingReview(jwt, null);
    assertEquals("/api/v1/admin/events", path);
    assertEquals("status=PENDING_APPROVAL&size=5", query);
    assertEquals(eventId, pending.getItems().getFirst().getEventId());
    assertFalse(JsonMapper.builder().build().writeValueAsString(pending).contains("private"));
    client.listAdminEventsPendingReview(jwt, pending.getNextCursor());
    assertEquals("status=PENDING_APPROVAL&size=5&cursor=next_1", query);
    assertEquals(7, requests);

    URI adminReport = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/admin/sales/summary");
    URI otherOrigin = URI.create("http://localhost:" + server.getAddress().getPort() + "/api/v1/admin/sales/summary");
    assertEquals("DOWNSTREAM_PATH_NOT_ALLOWED", assertThrows(AssistantProblem.class, () ->
        ReflectionTestUtils.invokeMethod(client, "get", "fixture", adminReport, jwt, Object.class,
            AssistantWorkspace.BUYER)).getCode());
    assertEquals("DOWNSTREAM_PATH_NOT_ALLOWED", assertThrows(AssistantProblem.class, () ->
        ReflectionTestUtils.invokeMethod(client, "get", "fixture", otherOrigin, jwt, Object.class,
            AssistantWorkspace.ADMIN)).getCode());
    assertEquals(7, requests);
  }

  @Test
  void readsOnlyOwnedEventViewsAndConsumesCursor() {
    var jwt = Jwt.withTokenValue("organizer-token").header("alg", "RS256").subject("organizer-1")
        .expiresAt(Instant.now().plusSeconds(300)).build();
    body = """
        {"items":[{"eventId":"%s","title":"Workshop","status":"PENDING_APPROVAL","rowVersion":"17"}],
         "nextCursor":"cursor_2"}
        """.formatted(eventId);
    var events = client.listOrganizerEvents(jwt, "PENDING_APPROVAL", "");
    assertEquals("GET", method);
    assertEquals("Bearer organizer-token", authorization);
    assertEquals("/api/v1/organizer/events", path);
    assertEquals("size=5&status=PENDING_APPROVAL", query);
    assertEquals("cursor_2", events.getNextCursor());
    assertEquals(eventId, events.getItems().getFirst().getEventId());
    client.listOrganizerEvents(jwt, null, events.getNextCursor());
    assertEquals("size=5&cursor=cursor_2", query);

    body = """
        {"eventId":"%s","title":"Workshop","status":"PUBLISHED","saleStartsAt":"2026-11-20T09:00:00Z",
         "description":"private-data","seatMap":{"version":"17"},"feeRate":"0.02"}
        """.formatted(eventId);
    var detail = client.getOrganizerEvent(jwt, eventId);
    assertEquals("/api/v1/organizer/events/" + eventId, path);
    assertEquals(Instant.parse("2026-11-20T09:00:00Z"), detail.getSaleStartsAt());
    String projected = JsonMapper.builder().build().writeValueAsString(detail);
    assertFalse(projected.contains("private-data"));
    assertFalse(projected.contains("seatMap"));
    assertFalse(projected.contains("feeRate"));
    status = 404;
    var missing = assertThrows(AssistantProblem.class, () -> client.getOrganizerEvent(jwt, eventId));
    assertEquals(404, missing.getStatus().value());
  }

  private void handleFixture(HttpExchange exchange) throws IOException {
    requests++; method = exchange.getRequestMethod(); path = exchange.getRequestURI().getPath();
    query = exchange.getRequestURI().getRawQuery(); authorization = exchange.getRequestHeaders().getFirst("Authorization");
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
