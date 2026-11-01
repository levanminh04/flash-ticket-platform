package com.flashticket.booking_service.client;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.booking_service.client.config.InternalFeignConfig;
import com.flashticket.booking_service.config.BookingJsonConfig;
import com.flashticket.booking_service.exception.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import feign.RequestInterceptor;
import feign.RetryableException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import tools.jackson.databind.json.JsonMapper;

class InternalFeignConfigTests {
  private HttpServer server;
  private ExecutorService executor;
  private final AtomicInteger tokenCalls = new AtomicInteger();
  private final AtomicInteger resourceCalls = new AtomicInteger();
  private final List<List<String>> resourceAuthorizations = new CopyOnWriteArrayList<>();
  private final List<List<String>> resourceCorrelations = new CopyOnWriteArrayList<>();
  private final List<String> tokenAuthorizations = new CopyOnWriteArrayList<>();
  private final List<String> tokenBodies = new CopyOnWriteArrayList<>();
  private final List<String> resourceBodies = new CopyOnWriteArrayList<>();
  private volatile int tokenStatus = 200;
  private volatile int resourceStatus = 200;
  private volatile String tokenResponse;
  private volatile String resourceResponse = "ok";
  private volatile CountDownLatch tokenGate = new CountDownLatch(0);
  private volatile CountDownLatch resourceGate = new CountDownLatch(0);

  @BeforeEach
  void startServer() throws IOException {
    SecurityContextHolder.clearContext();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.createContext(
        "/token",
        exchange -> {
          int sequence = tokenCalls.incrementAndGet();
          tokenAuthorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
          tokenBodies.add(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          awaitGate(tokenGate);
          respond(
              exchange,
              tokenStatus,
              tokenResponse == null
                  ? "{\"access_token\":\"machine-"
                      + sequence
                      + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}"
                  : tokenResponse);
        });
    server.createContext(
        "/probe",
        exchange -> {
          resourceCalls.incrementAndGet();
          var authorizations = exchange.getRequestHeaders().get("Authorization");
          resourceAuthorizations.add(
              authorizations == null ? List.of() : List.copyOf(authorizations));
          var correlations = exchange.getRequestHeaders().get("X-Correlation-Id");
          resourceCorrelations.add(correlations == null ? List.of() : List.copyOf(correlations));
          resourceBodies.add(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          awaitGate(resourceGate);
          respond(exchange, resourceStatus, resourceResponse);
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    SecurityContextHolder.clearContext();
    tokenGate.countDown();
    resourceGate.countDown();
    server.stop(0);
    executor.shutdownNow();
  }

  private ApplicationContextRunner runner() {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration.class,
                HttpMessageConvertersAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                FeignAutoConfiguration.class))
        .withUserConfiguration(BookingJsonConfig.class, ProbeConfiguration.class)
        .withInitializer(
            context -> {
              try {
                var path =
                    Path.of(System.getProperty("basedir", "."))
                        .resolve(
                            "../../infrastructure/config-server/src/main/resources/config/booking-service.yaml");
                for (var source :
                    new YamlPropertySourceLoader()
                        .load("booking-feign", new FileSystemResource(path))) {
                  context.getEnvironment().getPropertySources().addLast(source);
                }
              } catch (IOException exception) {
                throw new IllegalStateException(exception);
              }
            })
        .withPropertyValues(
            "INTERNAL_SERVICE_CLIENT_SECRET=test-only",
            "KEYCLOAK_TOKEN_URI=" + baseUrl() + "/token",
            "probe.url=" + baseUrl());
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (exchange;
        var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  private static void awaitGate(CountDownLatch gate) throws IOException {
    try {
      gate.await(500, TimeUnit.MILLISECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException(exception);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableFeignClients(
      clients = {
        InternalProbeClient.class,
        SecondInternalProbeClient.class,
        ExternalProbeClient.class
      })
  static class ProbeConfiguration {}

  @FeignClient(
      name = "internal-probe",
      url = "${probe.url}",
      configuration = InternalFeignConfig.class)
  interface InternalProbeClient {
    @GetMapping("/probe")
    String probe();

    @GetMapping("/probe")
    String withAuthorization(@RequestHeader("Authorization") String authorization);

    @GetMapping("/probe")
    String withCorrelation(@RequestHeader("X-Correlation-Id") String correlation);

    @PostMapping("/probe")
    String postProbe();

    @PostMapping("/probe")
    ProbePayload roundTrip(@RequestBody ProbePayload payload);

    @GetMapping("/probe")
    ProbePayload payload();
  }

  record ProbePayload(int quantity, String amount) {}

  @Test
  void internalClientsPropagateOnlyCurrentCorrelation() {
    UUID correlation = UUID.randomUUID();
    runner()
        .run(
            context -> {
              try (var ignored = MDC.putCloseable("correlationId", correlation.toString())) {
                context
                    .getBean(InternalProbeClient.class)
                    .withCorrelation("untrusted-caller-value");
                context.getBean(SecondInternalProbeClient.class).probe();
              }
              assertThat(resourceCorrelations)
                  .containsExactly(
                      List.of(correlation.toString()), List.of(correlation.toString()));
              assertThat(tokenCalls).hasValue(1);
            });
  }

  @Test
  void externalClientDoesNotReceiveCorrelation() {
    runner()
        .run(
            context -> {
              try (var ignored = MDC.putCloseable("correlationId", UUID.randomUUID().toString())) {
                context.getBean(ExternalProbeClient.class).probe();
              }
              context.getBean(InternalProbeClient.class).withCorrelation("untrusted-caller-value");
              assertThat(resourceCorrelations).containsExactly(List.of(), List.of());
            });
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void upstreamFailuresRetainEvidenceWithoutBecomingBuyerAuthErrors(boolean tokenFailure) {
    if (tokenFailure) {
      tokenStatus = 400;
      tokenResponse = "{\"error\":\"invalid_client\",\"error_description\":\"SENTINEL-secret\"}";
    } else {
      resourceStatus = 401;
      resourceResponse = "SENTINEL-response-body";
    }
    runner()
        .run(
            context -> {
              Throwable error = catchThrowable(context.getBean(InternalProbeClient.class)::probe);
              var failure = ErrorCode.from(error);
              assertThat(failure.name())
                  .isEqualTo(tokenFailure ? "INTERNAL_AUTH_UNAVAILABLE" : "DEPENDENCY_FAILURE");
              assertThat(failure.message()).doesNotContain("SENTINEL");
            });
  }

  @FeignClient(
      name = "second-internal-probe",
      url = "${probe.url}",
      configuration = InternalFeignConfig.class)
  interface SecondInternalProbeClient {
    @GetMapping("/probe")
    String probe();
  }

  @FeignClient(name = "external-probe", url = "${probe.url}")
  interface ExternalProbeClient {
    @GetMapping("/probe")
    String probe();
  }

  @Test
  void internalClientObtainsAndAttachesMachineToken() {
    runner()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(tokenCalls).hasValue(0);
              assertThat(context.getBean(InternalProbeClient.class).probe()).isEqualTo("ok");
              assertThat(resourceAuthorizations).containsExactly(List.of("Bearer machine-1"));
              String basic =
                  "Basic "
                      + Base64.getEncoder()
                          .encodeToString(
                              "internal-service:test-only".getBytes(StandardCharsets.UTF_8));
              assertThat(tokenAuthorizations).containsExactly(basic);
              assertThat(tokenBodies).containsExactly("grant_type=client_credentials");
            });
  }

  @Test
  void machineTokenReplacesCallerAuthorization() {
    runner()
        .run(
            context -> {
              context.getBean(InternalProbeClient.class).withAuthorization("Bearer buyer-token");
              assertThat(resourceAuthorizations).containsExactly(List.of("Bearer machine-1"));
            });
  }

  @Test
  void buyerAndBackgroundCallsShareTheApplicationToken() {
    runner()
        .run(
            context -> {
              var client = context.getBean(InternalProbeClient.class);
              for (String buyer : List.of("buyer-a", "buyer-b")) {
                SecurityContextHolder.getContext()
                    .setAuthentication(
                        new UsernamePasswordAuthenticationToken(buyer, "unused", List.of()));
                client.probe();
              }
              SecurityContextHolder.clearContext();
              client.probe();
              assertThat(resourceCalls).hasValue(3);
              assertThat(tokenCalls).hasValue(1);
              OAuth2AuthorizedClient stored =
                  context
                      .getBean(OAuth2AuthorizedClientService.class)
                      .loadAuthorizedClient("internal", "booking-service");
              assertThat(stored).isNotNull();
              assertThat(stored.getPrincipalName()).isEqualTo("booking-service");
            });
  }

  @Test
  void internalClientsShareTheApplicationToken() {
    runner()
        .run(
            context -> {
              context.getBean(InternalProbeClient.class).probe();
              context.getBean(SecondInternalProbeClient.class).probe();
              assertThat(resourceCalls).hasValue(2);
              assertThat(tokenCalls).hasValue(1);
            });
  }

  @Test
  void expiredCachedTokenIsRenewed() {
    runner()
        .run(
            context -> {
              var client = context.getBean(InternalProbeClient.class);
              client.probe();
              var store = context.getBean(OAuth2AuthorizedClientService.class);
              OAuth2AuthorizedClient stored =
                  store.loadAuthorizedClient("internal", "booking-service");
              var expired =
                  new OAuth2AccessToken(
                      OAuth2AccessToken.TokenType.BEARER,
                      "expired",
                      Instant.now().minusSeconds(120),
                      Instant.now().minusSeconds(60));
              store.saveAuthorizedClient(
                  new OAuth2AuthorizedClient(
                      stored.getClientRegistration(), "booking-service", expired),
                  new UsernamePasswordAuthenticationToken("booking-service", "unused", List.of()));
              client.probe();
              assertThat(tokenCalls).hasValue(2);
              assertThat(resourceAuthorizations)
                  .containsExactly(List.of("Bearer machine-1"), List.of("Bearer machine-2"));
            });
  }

  @Test
  void externalClientDoesNotReceiveTheMachineToken() {
    runner()
        .run(
            context -> {
              assertThat(context.getBean(ExternalProbeClient.class).probe()).isEqualTo("ok");
              assertThat(tokenCalls).hasValue(0);
              assertThat(resourceAuthorizations).containsExactly(List.of());
              assertThat(context).doesNotHaveBean(RequestInterceptor.class);
            });
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 500})
  void tokenEndpointFailurePreventsResourceCall(int status) {
    tokenStatus = status;
    tokenResponse = "{\"error\":\"invalid_client\"}";
    runner()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var client = context.getBean(InternalProbeClient.class);
              assertThatThrownBy(client::probe).isInstanceOf(RuntimeException.class);
              assertThat(tokenCalls).hasValue(1);
              assertThat(resourceCalls).hasValue(0);
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"token_type\":\"Bearer\"}", "not-json"})
  void invalidTokenResponsePreventsResourceCall(String body) {
    tokenResponse = body;
    runner()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var client = context.getBean(InternalProbeClient.class);
              assertThatThrownBy(client::probe).isInstanceOf(RuntimeException.class);
              assertThat(tokenCalls).hasValue(1);
              assertThat(resourceCalls).hasValue(0);
            });
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 500})
  void resourceErrorsAreNotRetried(int status) {
    resourceStatus = status;
    runner()
        .run(
            context -> {
              var client = context.getBean(InternalProbeClient.class);
              assertThatThrownBy(client::postProbe)
                  .isInstanceOfSatisfying(
                      FeignException.class,
                      exception -> assertThat(exception.status()).isEqualTo(status));
              assertThat(tokenCalls).hasValue(1);
              assertThat(resourceCalls).hasValue(1);
            });
  }

  @Test
  void tokenReadTimeoutPreventsResourceCall() {
    tokenGate = new CountDownLatch(1);
    runner()
        .withPropertyValues("spring.cloud.openfeign.client.config.default.readTimeout=100")
        .run(
            context -> {
              var client = context.getBean(InternalProbeClient.class);
              assertThatThrownBy(client::probe)
                  .hasRootCauseInstanceOf(SocketTimeoutException.class);
              assertThat(tokenCalls).hasValue(1);
              assertThat(resourceCalls).hasValue(0);
            });
  }

  @Test
  void resourceReadTimeoutIsBounded() {
    resourceGate = new CountDownLatch(1);
    runner()
        .withPropertyValues("spring.cloud.openfeign.client.config.default.readTimeout=100")
        .run(
            context -> {
              var client = context.getBean(InternalProbeClient.class);
              assertThatThrownBy(client::probe)
                  .isInstanceOf(RetryableException.class)
                  .hasRootCauseInstanceOf(SocketTimeoutException.class);
              assertThat(tokenCalls).hasValue(1);
              assertThat(resourceCalls).hasValue(1);
            });
  }

  @Test
  void feignRoundTripsTheExistingJsonContract() {
    resourceResponse = "{\"quantity\":2,\"amount\":\"200\"}";
    runner()
        .run(
            context -> {
              var client = context.getBean(InternalProbeClient.class);
              assertThat(client.roundTrip(new ProbePayload(2, "200")))
                  .isEqualTo(new ProbePayload(2, "200"));
              var sent = context.getBean(JsonMapper.class).readTree(resourceBodies.getFirst());
              assertThat(sent.get("quantity").asInt()).isEqualTo(2);
              assertThat(sent.get("amount").isString()).isTrue();
              assertThat(sent.get("amount").asString()).isEqualTo("200");
            });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"quantity\":2,\"amount\":\"200\",\"unknown\":true}",
        "{\"quantity\":\"2\",\"amount\":\"200\"}",
        "{\"quantity\":2,\"amount\":200}"
      })
  void feignUsesTheExistingStrictJsonMapper(String body) {
    resourceResponse = body;
    runner()
        .run(
            context -> {
              var client = context.getBean(InternalProbeClient.class);
              assertThatThrownBy(client::payload).isInstanceOf(feign.codec.DecodeException.class);
              assertThat(resourceCalls).hasValue(1);
            });
  }
}
