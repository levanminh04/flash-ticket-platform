package com.flashticket.payment_service.web;

import static org.assertj.core.api.Assertions.*;

import jakarta.servlet.ServletException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.*;

class PaymentCorrelationFilterTest {
  @Test
  void prefersOrderIdAndRestoresPreviousMdcAfterFailure() throws Exception {
    var id = UUID.randomUUID();
    var request = new MockHttpServletRequest("GET", "/api/v1/orders/" + id + "/payment");
    request.addHeader("X-Correlation-Id", UUID.randomUUID().toString());
    var response = new MockHttpServletResponse();
    MDC.put("correlationId", "previous");
    try {
      assertThatThrownBy(
              () ->
                  new PaymentCorrelationFilter()
                      .doFilter(
                          request,
                          response,
                          (req, res) -> {
                            assertThat(MDC.get("correlationId")).isEqualTo(id.toString());
                            throw new ServletException("test");
                          }))
          .isInstanceOf(ServletException.class);
      assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(id.toString());
      assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
      assertThat(MDC.get("correlationId")).isEqualTo("previous");
    } finally {
      MDC.remove("correlationId");
    }
  }

  @Test
  void acceptsOnlyCanonicalHeaderAndGeneratesSafeFallback() {
    var request = new MockHttpServletRequest("GET", "/unknown");
    request.addHeader("X-Correlation-Id", "bad input");
    var id = PaymentCorrelationFilter.correlationId(request);
    assertThat(PaymentCorrelationFilter.correlationId(request)).isEqualTo(id);
    var other = new MockHttpServletRequest("GET", "/unknown");
    other.addHeader("X-Correlation-Id", id.toString());
    assertThat(PaymentCorrelationFilter.correlationId(other)).isEqualTo(id);
    assertThatThrownBy(() -> PaymentCorrelationFilter.parseUuid("1-1-1-1-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
