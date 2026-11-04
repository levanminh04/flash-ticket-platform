package com.flashticket.user_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flashticket.user_service.web.CorrelationFilter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationFilterTests {
  @Test
  void invalidIncomingHeaderCannotReachResponseAndPreviousMdcIsRestoredOnFailure() {
    var request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-Id", "bad\r\nInjected: header");
    var response = new MockHttpServletResponse();
    MDC.put("correlationId", "outer-context");
    try {
      assertThatThrownBy(
              () ->
                  new CorrelationFilter()
                      .doFilter(
                          request,
                          response,
                          (r, s) -> {
                            assertThat(MDC.get("correlationId"))
                                .isEqualTo(response.getHeader("X-Correlation-Id"));
                            throw new IllegalStateException("probe");
                          }))
          .isInstanceOf(IllegalStateException.class);
      assertThat(MDC.get("correlationId")).isEqualTo("outer-context");
      assertThat(UUID.fromString(response.getHeader("X-Correlation-Id"))).isNotNull();
      assertThat(response.getHeader("X-Correlation-Id")).doesNotContain("Injected", "\r", "\n");
    } finally {
      MDC.clear();
    }
  }
}
