package com.flashticket.payment_service.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.web.PaymentProblemFactory;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class PaymentExceptionHandlerTest {
  @ParameterizedTest
  @CsvSource({
    "PAYMENT_NOT_READY, 404, Payment is not ready.",
    "RESOURCE_NOT_FOUND, 404, Resource was not found.",
    "AUTHENTICATION_REQUIRED, 401, A valid bearer token is required.",
    "ACCESS_DENIED, 403, Access is denied.",
    "INVALID_REQUEST, 400, Request parameters are invalid.",
    "PAYMENT_STATE_UNAVAILABLE, 503, Payment state is temporarily unavailable.",
    "INTERNAL_ERROR, 500, The request could not be processed."
  })
  void thrownPaymentErrorProducesStableHttpProblem(String code, int status, String message)
      throws Exception {
    var cause = new IllegalStateException("private provider credentials");
    var error = new PaymentException(PaymentErrorCode.valueOf(code), cause);
    assertThat(error.getCause()).isSameAs(cause);
    assertThat(error.errorCode()).isEqualTo(PaymentErrorCode.valueOf(code));
    var correlationId = UUID.randomUUID();
    MockMvcBuilders.standaloneSetup(new FailureController(error))
        .setControllerAdvice(new PaymentExceptionHandler(new PaymentProblemFactory()))
        .build()
        .perform(get("/failure").header("X-Correlation-Id", correlationId))
        .andExpect(status().is(status))
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.message").value(message))
        .andExpect(jsonPath("$.correlationId").value(correlationId.toString()))
        .andExpect(jsonPath("$.violations").isEmpty());
  }

  @Test
  void contractViolationsKeepUnprocessableStatusAndFieldDetails() throws Exception {
    MockMvcBuilders.standaloneSetup(
            new FailureController(new ContractValidationException("$.total", "TOTAL_MISMATCH")))
        .setControllerAdvice(new PaymentExceptionHandler(new PaymentProblemFactory()))
        .build()
        .perform(get("/failure"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.violations[0].field").value("$.total"))
        .andExpect(jsonPath("$.violations[0].code").value("TOTAL_MISMATCH"));
  }

  @Test
  void unexpectedExceptionsProduceSafeInternalError() throws Exception {
    MockMvcBuilders.standaloneSetup(
            new FailureController(new IllegalStateException("private provider credentials")))
        .setControllerAdvice(new PaymentExceptionHandler(new PaymentProblemFactory()))
        .build()
        .perform(get("/failure"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("The request could not be processed."));
  }

  @RestController
  static class FailureController {
    private final RuntimeException failure;

    FailureController(RuntimeException failure) {
      this.failure = failure;
    }

    @GetMapping("/failure")
    void fail() {
      throw failure;
    }
  }
}
