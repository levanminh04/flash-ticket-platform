package com.flashticket.booking_service.exception;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.booking_service.config.BookingJsonConfig;
import com.flashticket.booking_service.support.LogCapture;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.net.SocketException;
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

  @Test
  void successfulRequestsDoNotNeedAnErrorTrackingFilter() throws Exception {
    mvc.perform(get("/error-probe/get"))
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist("X-Correlation-Id"))
        .andExpect(header().doesNotExist("X-Request-Id"));
  }

  @Test
  void rejectionLogExplainsTheFailureAndCarriesRcaContext() throws Exception {
    String correlation = UUID.randomUUID().toString();
    try (var logs = new LogCapture()) {
      mvc.perform(
              get("/error-probe/business/ORDER_EXPIRED").header("X-Correlation-Id", correlation))
          .andExpect(status().isConflict());
      var entry =
          logs.events().stream()
              .filter(
                  event ->
                      event.getKeyValuePairs() != null
                          && event.getKeyValuePairs().stream()
                              .anyMatch(pair -> pair.key.equals("errorCode")))
              .findFirst()
              .orElseThrow();
      assertThat(entry.getFormattedMessage()).contains(ErrorCode.ORDER_EXPIRED.message());
      assertThat(entry.getKeyValuePairs())
          .anySatisfy(
              pair -> {
                assertThat(pair.key).isEqualTo("correlationId");
                assertThat(pair.value).isEqualTo(correlation);
              });
      assertThat(entry.getKeyValuePairs())
          .anySatisfy(
              pair -> {
                assertThat(pair.key).isEqualTo("httpStatus");
                assertThat(pair.value).isEqualTo(409);
              });
      assertThat(entry.getKeyValuePairs())
          .anySatisfy(
              pair -> {
                assertThat(pair.key).isEqualTo("httpMethod");
                assertThat(pair.value).isEqualTo("GET");
              });
    }
  }

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
    assertThat(UUID.fromString(result.getResponse().getHeader("X-Correlation-Id"))).isNotNull();
    assertThat(result.getResponse().getHeader("X-Request-Id")).isNotEqualTo("client-id");
    assertThat(result.getResponse().getContentAsString()).doesNotContain("SENTINEL");
  }

  @Test
  void nestedValidationReportsOnlyRootFieldsWithoutEchoingMapKeys() throws Exception {
    mvc.perform(
            post("/error-probe/nested")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"items\":[{\"email\":\"bad\",\"quantity\":1}],"
                        + "\"labels\":{\"SENTINEL-private@example.test\":\"\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(
            jsonPath(
                "$.violations[*].field",
                org.hamcrest.Matchers.containsInAnyOrder("items", "labels")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SENTINEL"))));
  }

  @Test
  void committedResponseIsNotWrittenAgain() throws Exception {
    var result = mvc.perform(get("/error-probe/committed")).andReturn();
    assertThat(result.getResponse().getContentAsString()).isEqualTo("already committed");
    mvc.perform(get("/error-probe/disconnect")).andExpect(content().string(""));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @Import({BookingJsonConfig.class, GlobalExceptionHandler.class, ProbeController.class})
  static class ProbeApplication {
    @Bean
    SecurityFilterChain probeSecurity(HttpSecurity http) throws Exception {
      return http.csrf(c -> c.disable())
          .authorizeHttpRequests(a -> a.anyRequest().permitAll())
          .build();
    }
  }

  record ProbeInput(@NotBlank @Email String email, @Min(1) int quantity) {}

  record NestedInput(List<@Valid ProbeInput> items, Map<String, @NotBlank String> labels) {}

  @RestController
  static class ProbeController {
    @GetMapping(value = "/error-probe/business/{code}", produces = "application/json")
    String business(@PathVariable ErrorCode code) {
      throw new BusinessException(code);
    }

    @PostMapping(value = "/error-probe/input", consumes = "application/json")
    ProbeInput input(@Valid @RequestBody ProbeInput input) {
      return input;
    }

    @PostMapping("/error-probe/nested")
    NestedInput nested(@Valid @RequestBody NestedInput input) {
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
