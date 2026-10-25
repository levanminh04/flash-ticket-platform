package com.flashticket.payment_service.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.payment_service.dto.response.PaymentView;
import com.flashticket.payment_service.service.payment.PaymentQueryService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class PaymentQueryControllerTest {
  @Test
  void delegatesAuthenticatedSubjectAndOptionalRequestWithoutChangingDto() {
    var service = mock(PaymentQueryService.class);
    var controller = new PaymentQueryController(service);
    var order = UUID.randomUUID();
    var request = UUID.randomUUID();
    var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject("buyer").build();
    var expected = new PaymentView(order, null, null, null, List.of());
    when(service.getPayment(order, request, "buyer")).thenReturn(expected);
    assertThat(controller.getPayment(order, request, jwt)).isSameAs(expected);
    verify(service).getPayment(order, request, "buyer");
    when(service.getPayment(order, null, "buyer")).thenReturn(expected);
    assertThat(controller.getPayment(order, null, jwt)).isSameAs(expected);
  }
}
