package com.flashticket.booking_service.web.error;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.booking_service.config.BookingJsonConfig;
import com.flashticket.booking_service.exception.BusinessErrorCode;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.observability.BookingContext;
import com.flashticket.booking_service.support.LogCapture;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    classes = HttpErrorHandlingTests.ProbeApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext
class HttpErrorHandlingTests {
  @Autowired MockMvc mvc;
  @Autowired JsonMapper mapper;
  @Autowired Environment environment;

  @ParameterizedTest
  @CsvSource({
    "ORDER_NOT_FOUND,404",
    "CONFIG_NOT_READY,503",
    "SEAT_UNAVAILABLE,409",
    "INSUFFICIENT_QUANTITY,409",
    "PURCHASE_LIMIT_EXCEEDED,409",
    "ORDER_EXPIRED,409",
    "ORDER_NOT_PAYABLE,409",
    "PAYMENT_IN_PROGRESS,409",
    "PRICE_FROZEN,409",
    "IDEMPOTENCY_CONFLICT,409",
    "PROMOTION_NOT_FOUND,422",
    "PROMOTION_OUTSIDE_WINDOW,422",
    "PROMOTION_EXHAUSTED,422",
    "PROMOTION_ALREADY_USED,422",
    "ORDER_ALREADY_HAS_PROMOTION,422",
    "NON_POSITIVE_TOTAL,422"
  })
  void mvcFailuresUseProblemContract(String code, int status) throws Exception {
    var result =
        mvc.perform(get("/error-probe/business/" + code))
            .andExpect(status().is(status))
            .andExpect(jsonPath("$.code").value(code))
            .andReturn();
    JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.size()).isEqualTo(4);
    assertThat(body.get("violations").isArray()).isTrue();
    assertThat(body.get("correlationId").asString())
        .isEqualTo(result.getResponse().getHeader("X-Correlation-Id"));
    assertThat(BookingContext.parseUuid(result.getResponse().getHeader("X-Request-Id")))
        .isNotNull();
    assertThat(MDC.get("correlationId")).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{",
        "{\"email\":\"a@b.test\",\"quantity\":\"2\"}",
        "{\"email\":\"a@b.test\",\"quantity\":2,\"SENTINEL-unknown@example.test\":true}"
      })
  void invalidJsonDoesNotEchoInput(String body) throws Exception {
    var result =
        mvc.perform(
                post("/error-probe/input").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("SENTINEL", "unknown@example");
  }

  @Test
  void inputAndReturnValidationAreDifferent() throws Exception {
    mvc.perform(
            post("/error-probe/input")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"SENTINEL-bad-email\",\"quantity\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.violations").isNotEmpty());
    mvc.perform(get("/error-probe/return"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    mvc.perform(get("/error-probe/constraint"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
  }

  @Test
  void malformedParametersAndFrameworkStatusesArePreserved() throws Exception {
    mvc.perform(get("/error-probe/uuid").param("id", "not-uuid"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/error-probe/uuid")).andExpect(status().isBadRequest());
    mvc.perform(get("/error-probe/query").param("count", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mvc.perform(get("/error-probe/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
    mvc.perform(post("/error-probe/get"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().exists("Allow"))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    mvc.perform(get("/error-probe/get").accept(MediaType.APPLICATION_XML))
        .andExpect(status().isNotAcceptable())
        .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    mvc.perform(post("/error-probe/input").contentType(MediaType.TEXT_PLAIN).content("secret"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1-1-1-1-1",
        "not-a-uuid",
        "SENTINEL\r\ninjected",
        "00000000-0000-4000-8000-000000000021,00000000-0000-4000-8000-000000000022"
      })
  void malformedCorrelationIsReplaced(String header) throws Exception {
    var result =
        mvc.perform(
                get("/error-probe/business/ORDER_EXPIRED")
                    .header("X-Correlation-Id", header)
                    .header("X-Request-Id", "client-id"))
            .andReturn();
    assertThat(BookingContext.parseUuid(result.getResponse().getHeader("X-Correlation-Id")))
        .isNotNull();
    assertThat(result.getResponse().getHeader("X-Request-Id")).isNotEqualTo("client-id");
    assertThat(result.getResponse().getContentAsString()).doesNotContain("SENTINEL");
  }

  @Test
  void correlationDoesNotClaimOrderOwnershipAndVerifiedBindingUpdatesHeader() throws Exception {
    String correlation = UUID.randomUUID().toString();
    try (var logs = new LogCapture()) {
      mvc.perform(
              get("/error-probe/business/ORDER_EXPIRED").header("X-Correlation-Id", correlation))
          .andExpect(header().string("X-Correlation-Id", correlation))
          .andExpect(jsonPath("$.correlationId").value(correlation));
      var failure =
          logs.events().stream()
              .filter(e -> e.getMessage().equals("Booking operation rejected."))
              .findFirst()
              .orElseThrow();
      assertThat(failure.getMDCPropertyMap()).doesNotContainKey("orderId");
    }
    String order = UUID.randomUUID().toString();
    mvc.perform(get("/error-probe/bind/" + order))
        .andExpect(header().string("X-Correlation-Id", order))
        .andExpect(jsonPath("$.correlationId").value(order));
  }

  @Test
  void errorDispatchKeepsIdsAndLogsOnce() throws Exception {
    UUID correlation = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    var failure = new IllegalStateException("SENTINEL-message");
    var request = new org.springframework.mock.web.MockHttpServletRequest();
    request.setAttribute(BookingContext.CORRELATION_ATTRIBUTE, correlation);
    request.setAttribute(BookingContext.REQUEST_ATTRIBUTE, requestId);
    request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, failure);
    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
    var responses = new ProblemResponses(mapper);
    try (var logs = new LogCapture()) {
      try (var ignored =
          BookingContext.open(
              Map.of("correlationId", correlation.toString(), "requestId", requestId.toString()))) {
        responses.response(
            request,
            org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
            com.flashticket.booking_service.exception.ErrorClassifier.classify(failure),
            List.of());
        var controller = new BookingErrorController(responses);
        controller.error(request, new org.springframework.mock.web.MockHttpServletResponse());
      }
      assertThat(
              logs.events().stream()
                  .filter(e -> e.getMessage().equals("Booking operation failed."))
                  .count())
          .isEqualTo(1);
    }
    mvc.perform(
            get("/error")
                .with(
                    r -> {
                      r.setDispatcherType(DispatcherType.ERROR);
                      r.setAttribute(BookingContext.CORRELATION_ATTRIBUTE, correlation);
                      r.setAttribute(BookingContext.REQUEST_ATTRIBUTE, requestId);
                      r.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
                      return r;
                    }))
        .andExpect(status().isNotFound())
        .andExpect(header().string("X-Request-Id", requestId.toString()))
        .andExpect(jsonPath("$.correlationId").value(correlation.toString()));
  }

  @Test
  void committedResponseIsNotWrittenAgain() throws Exception {
    var result = mvc.perform(get("/error-probe/committed")).andReturn();
    assertThat(result.getResponse().getContentAsString()).isEqualTo("already committed");
    mvc.perform(get("/error-probe/disconnect")).andExpect(content().string(""));
  }

  @Test
  void actualServletErrorDispatchUsesProblem() throws Exception {
    int port = environment.getRequiredProperty("local.server.port", Integer.class);
    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(
              HttpRequest.newBuilder(
                      URI.create("http://127.0.0.1:" + port + "/error-probe/send-error"))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(503);
      JsonNode body = mapper.readTree(response.body());
      assertThat(body.size()).isEqualTo(4);
      assertThat(body.get("correlationId").asString())
          .isEqualTo(response.headers().firstValue("X-Correlation-Id").orElseThrow());
      assertThat(response.body()).doesNotContain("SENTINEL-container");
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @Import({
    BookingJsonConfig.class,
    CorrelationFilter.class,
    ProblemResponses.class,
    GlobalExceptionHandler.class,
    BookingErrorController.class,
    ProbeController.class
  })
  static class ProbeApplication {
    @Bean
    SecurityFilterChain probeSecurity(HttpSecurity http) throws Exception {
      return http.csrf(c -> c.disable())
          .authorizeHttpRequests(a -> a.anyRequest().permitAll())
          .build();
    }

    @Bean
    Filter sendErrorProbe() {
      return (request, response, chain) -> {
        if (((jakarta.servlet.http.HttpServletRequest) request)
            .getRequestURI()
            .equals("/error-probe/send-error")) {
          ((HttpServletResponse) response).sendError(503, "SENTINEL-container");
        } else chain.doFilter(request, response);
      };
    }
  }

  record ProbeInput(@NotBlank @Email String email, @Min(1) int quantity) {}

  @RestController
  static class ProbeController {
    @GetMapping(value = "/error-probe/business/{code}", produces = "application/json")
    String business(@PathVariable BusinessErrorCode code) {
      throw new BusinessException(code);
    }

    @PostMapping(value = "/error-probe/input", consumes = "application/json")
    ProbeInput input(@Valid @RequestBody ProbeInput input) {
      return input;
    }

    @GetMapping("/error-probe/uuid")
    String uuid(@RequestParam UUID id) {
      return id.toString();
    }

    @GetMapping("/error-probe/query")
    String query(@RequestParam @Min(1) int count) {
      return "ok";
    }

    @GetMapping(value = "/error-probe/get", produces = "application/json")
    Map<String, String> get() {
      return Map.of("value", "ok");
    }

    @GetMapping("/error-probe/return")
    @NotBlank
    String invalidReturn() {
      return "";
    }

    @GetMapping("/error-probe/constraint")
    String constraint() {
      throw new DataIntegrityViolationException("SENTINEL-SQL-email");
    }

    @GetMapping("/error-probe/bind/{id}")
    String bind(@PathVariable UUID id) {
      BookingContext.bindOrder(id);
      throw new BusinessException(BusinessErrorCode.ORDER_EXPIRED);
    }

    @GetMapping("/error-probe/committed")
    void committed(HttpServletResponse response) throws IOException {
      response.getWriter().write("already committed");
      response.flushBuffer();
      throw new IllegalStateException("SENTINEL-committed");
    }

    @GetMapping("/error-probe/disconnect")
    void disconnected() throws SocketException {
      throw new SocketException("Broken pipe");
    }
  }
}
