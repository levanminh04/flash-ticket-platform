package com.flashticket.payment_service.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.payment_service.dto.response.PaymentView;
import com.flashticket.payment_service.service.payment.PaymentService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class PaymentControllerTest {
  @Test
  void delegatesAuthenticatedSubjectAndOptionalRequestWithoutChangingDto() {
    var service = mock(PaymentService.class);
    var controller = new PaymentController(service);
    var order = UUID.randomUUID();
    var request = UUID.randomUUID();
    var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject("buyer").build();
    var expected = new PaymentView(order, null, null, null, List.of());
    when(service.getPayment(order, request, "buyer")).thenReturn(expected);
    assertThat(controller.getPayment(order, request, jwt).data()).isSameAs(expected);
    verify(service).getPayment(order, request, "buyer");
    when(service.getPayment(order, null, "buyer")).thenReturn(expected);
    assertThat(controller.getPayment(order, null, jwt).data()).isSameAs(expected);
  }
}
