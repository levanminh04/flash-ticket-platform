package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.flashticket.event_service.config.EventJsonConfig;
import com.flashticket.event_service.integration.*;
import com.flashticket.event_service.web.EventProblem;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import tools.jackson.databind.json.JsonMapper;

class SummaryHttpTests {
  static HttpServer server;
  static ExecutorService executor;
  static final UUID EVENT = UUID.randomUUID();
  static final AtomicReference<String> finance = new AtomicReference<>(),
      sales = new AtomicReference<>();
  static final AtomicReference<String> authorization = new AtomicReference<>(),
      correlation = new AtomicReference<>();
  static final AtomicInteger status = new AtomicInteger(200),
      requests = new AtomicInteger(),
      delay = new AtomicInteger();
  static JsonMapper json;

  @BeforeAll
  static void start() throws Exception {
    var builder = JsonMapper.builder();
    new org.springframework.boot.test.context.runner.ApplicationContextRunner()
        .withUserConfiguration(EventJsonConfig.class)
        .run(
            context ->
                context
                    .getBean(
                        org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
                            .class)
                    .customize(builder));
    json = builder.build();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    executor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    server.createContext(
        "/internal/v1/events/",
        exchange -> {
          requests.incrementAndGet();
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          correlation.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
          try {
            Thread.sleep(delay.get());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          byte[] bytes =
              (exchange.getRequestURI().getPath().endsWith("financial-summary")
                      ? finance.get()
                      : sales.get())
                  .getBytes(StandardCharsets.UTF_8);
          try {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
          } catch (java.io.IOException expectedOnTimeout) {
            /* Client closed the timed-out request. */
          } finally {
            exchange.close();
          }
        });
    server.start();
  }

  @AfterAll
  static void stop() {
    server.stop(0);
    executor.close();
  }

  @BeforeEach
  void reset() {
    status.set(200);
    delay.set(0);
    requests.set(0);
    MDC.clear();
    finance.set(financial(Instant.now().toString()));
    sales.set(
        "{\"schemaVersion\":1,\"eventId\":\""
            + EVENT
            + "\",\"organizerSubject\":\"owner\",\"asOf\":\""
            + Instant.now()
            + "\",\"purchasedTicketCount\":\"12\"}");
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  static String financial(String at) {
    return """
        {"schemaVersion":1,"eventId":"%s","organizerSubject":"owner","asOf":"%s",
         "hasCollectedOrders":true,"currency":"VND","validGross":"100000","validRefunds":"20000","feeAmount":"8000","netAmount":"72000"}
        """
        .formatted(EVENT, at);
  }

  static HttpEventSummaries client(boolean enabled, Duration timeout) {
    URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    return new HttpEventSummaries(
        new EventReadProperties(
            enabled,
            base,
            base,
            Duration.ofSeconds(1),
            timeout,
            Duration.ofSeconds(30),
            Duration.ofSeconds(5)),
        mock(DiscoveryClient.class),
        () -> "isolated-machine-token",
        json);
  }

  @Test
  void readsStrictSummariesAndPropagatesMachineAuthenticationAndCorrelation() {
    String id = UUID.randomUUID().toString();
    MDC.put("correlationId", id);
    var client = client(true, Duration.ofSeconds(2));
    assertThat(client.financial(EVENT, "owner", true).netAmount()).isEqualTo("72000");
    assertThat(client.sales(EVENT, "owner").purchasedTicketCount()).isEqualTo("12");
    assertThat(authorization.get()).isEqualTo("Bearer isolated-machine-token");
    assertThat(correlation.get()).isEqualTo(id);
  }

  @Test
  void disabledContractNeverCallsCounterparty() {
    assertThatThrownBy(() -> client(false, Duration.ofSeconds(1)).financial(EVENT, "owner", true))
        .isInstanceOfSatisfying(
            EventProblem.class,
            p -> assertThat(p.code()).isEqualTo("DEPENDENCY_CONTRACT_NOT_READY"));
    assertThat(requests.get()).isZero();
  }

  @ParameterizedTest
  @ValueSource(ints = {302, 401, 403, 404, 500, 503})
  void unavailableSourceDoesNotBecomeZeroOrSuccessfulCancellation(int code) {
    status.set(code);
    assertThatThrownBy(() -> client(true, Duration.ofSeconds(1)).financial(EVENT, "owner", true))
        .isInstanceOfSatisfying(
            EventProblem.class,
            p -> {
              assertThat(p.status()).isEqualTo(503);
              assertThat(p.code()).isEqualTo("UPSTREAM_UNAVAILABLE");
            });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "fraction",
        "number",
        "boolean",
        "unknown",
        "scope",
        "currency",
        "negative",
        "net",
        "null",
        "future",
        "missing"
      })
  void rejectsInvalidWirePayloadBeforeUsingIt(String fault) {
    String body = finance.get();
    body =
        switch (fault) {
          case "fraction" -> body.replace("\"100000\"", "\"100000.0\"");
          case "number" -> body.replace("\"100000\"", "100000");
          case "boolean" ->
              body.replace("\"hasCollectedOrders\":true", "\"hasCollectedOrders\":\"true\"");
          case "unknown" ->
              body.replace("\"schemaVersion\":1", "\"extra\":true,\"schemaVersion\":1");
          case "scope" -> body.replace(EVENT.toString(), UUID.randomUUID().toString());
          case "currency" -> body.replace("VND", "USD");
          case "negative" -> body.replace("\"20000\"", "\"-20000\"");
          case "net" -> body.replace("\"72000\"", "\"71000\"");
          case "null" -> body.replace("\"hasCollectedOrders\":true", "\"hasCollectedOrders\":null");
          case "future" -> financial(Instant.now().plusSeconds(60).toString());
          default -> body.replace("\"hasCollectedOrders\":true,", "");
        };
    finance.set(body);
    assertThatThrownBy(() -> client(true, Duration.ofSeconds(1)).financial(EVENT, "owner", true))
        .isInstanceOfSatisfying(EventProblem.class, p -> assertThat(p.status()).isEqualTo(503));
  }

  @Test
  void oldSummaryCanBeDisplayedWithAsOfButCannotClassifyCancellation() {
    finance.set(financial(Instant.now().minusSeconds(120).toString()));
    var client = client(true, Duration.ofSeconds(1));
    assertThat(client.financial(EVENT, "owner", false).hasCollectedOrders()).isTrue();
    assertThatThrownBy(() -> client.financial(EVENT, "owner", true))
        .isInstanceOfSatisfying(
            EventProblem.class, p -> assertThat(p.code()).isEqualTo("UPSTREAM_DATA_STALE"));
  }

  @Test
  void timeoutIsBoundedAndDoesNotExposeRemoteBody() {
    delay.set(400);
    assertThatThrownBy(() -> client(true, Duration.ofMillis(100)).financial(EVENT, "owner", true))
        .isInstanceOfSatisfying(
            EventProblem.class, p -> assertThat(p.code()).isEqualTo("UPSTREAM_UNAVAILABLE"));
  }

  @Test
  void statisticsRejectsCounterOverflowAndWrongOwner() {
    sales.set(sales.get().replace("\"12\"", "\"9223372036854775808\""));
    assertThatThrownBy(() -> client(true, Duration.ofSeconds(1)).sales(EVENT, "owner"))
        .isInstanceOf(EventProblem.class);
    assertThatThrownBy(() -> client(true, Duration.ofSeconds(1)).financial(EVENT, "foreign", false))
        .isInstanceOfSatisfying(
            EventProblem.class, p -> assertThat(p.code()).isEqualTo("UPSTREAM_SCOPE_MISMATCH"));
  }
}
