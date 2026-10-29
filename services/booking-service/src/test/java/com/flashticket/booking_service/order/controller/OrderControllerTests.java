package com.flashticket.booking_service.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.booking_service.config.BookingJsonConfig;
import com.flashticket.booking_service.config.SecurityConfig;
import com.flashticket.booking_service.dto.response.OrderView;
import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.OrderState;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.exception.GlobalExceptionHandler;
import com.flashticket.booking_service.order.service.OrderService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, BookingJsonConfig.class, GlobalExceptionHandler.class})
class OrderControllerTests {
  @Autowired MockMvc mvc;
  @Autowired JsonMapper objectMapper;
  @MockitoBean OrderService orderService;

  @Test
  void createsOrderUsingAuthenticatedSubjectAndIdempotencyKey() throws Exception {
    UUID key = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID eventId = UUID.fromString("11111111-1111-4111-8111-111111111111");
    OrderView response =
        new OrderView(
            orderId,
            eventId,
            OrderState.PENDING_PAYMENT,
            "buyer@example.com",
            Currency.VND,
            "100",
            "0",
            "100",
            Instant.parse("2026-10-28T12:15:00Z"),
            null,
            "1",
            List.of());
    when(orderService.createOrder(eq("buyer-subject"), eq(key), any())).thenReturn(response);

    mvc.perform(
            post("/api/v1/orders")
                .with(
                    jwt()
                        .jwt(token -> token.subject("buyer-subject"))
                        .authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                {"eventId":"11111111-1111-4111-8111-111111111111",
                 "customerEmail":"buyer@example.com",
                 "selection":{"mode":"QUANTITY","items":[
                   {"ticketTypeId":"22222222-2222-4222-8222-222222222222","quantity":1}]}}
                """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/orders/" + orderId))
        .andExpect(jsonPath("$.orderId").value(orderId.toString()))
        .andExpect(jsonPath("$.state").value("PENDING_PAYMENT"));
    verify(orderService).createOrder(eq("buyer-subject"), eq(key), any());
  }

  @Test
  void rejectsMissingIdempotencyKey() throws Exception {
    mvc.perform(
            post("/api/v1/orders")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(orderService);
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", " "})
  void rejectsInvalidIdempotencyKey(String key) throws Exception {
    mvc.perform(
            post("/api/v1/orders")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(orderService);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"eventId\":\"11111111-1111-4111-8111-111111111111\",\"customerEmail\":\"wrong\",\"selection\":{\"mode\":\"QUANTITY\",\"items\":[{\"ticketTypeId\":\"22222222-2222-4222-8222-222222222222\",\"quantity\":1}]}}",
        "{\"eventId\":\"11111111-1111-4111-8111-111111111111\",\"customerEmail\":\"buyer@example.com\",\"selection\":{\"mode\":\"QUANTITY\",\"items\":[{\"ticketTypeId\":\"22222222-2222-4222-8222-222222222222\",\"quantity\":0}]}}",
        "{\"eventId\":\"11111111-1111-4111-8111-111111111111\",\"customerEmail\":\"buyer@example.com\",\"buyerSubject\":\"another-user\",\"selection\":{\"mode\":\"QUANTITY\",\"items\":[{\"ticketTypeId\":\"22222222-2222-4222-8222-222222222222\",\"quantity\":1}]}}",
        "{\"eventId\":\"11111111-1111-4111-8111-111111111111\",\"customerEmail\":\"buyer@example.com\",\"selection\":{\"mode\":\"QUANTITY\",\"items\":[]}}"
      })
  void rejectsInvalidCreateOrderBody(String body) throws Exception {
    mvc.perform(
            post("/api/v1/orders")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(orderService);
  }

  @Test
  void requiresAuthenticationAndBuyerRole() throws Exception {
    mvc.perform(post("/api/v1/orders")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/orders")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
        .andExpect(status().isForbidden());
    verifyNoInteractions(orderService);
  }

  @Test
  void returnsBusinessProblemFromOrderService() throws Exception {
    when(orderService.createOrder(any(), any(), any()))
        .thenThrow(new BusinessException(ErrorCode.SEAT_UNAVAILABLE));
    mvc.perform(
            post("/api/v1/orders")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                {"eventId":"11111111-1111-4111-8111-111111111111",
                 "customerEmail":"buyer@example.com",
                 "selection":{"mode":"QUANTITY","items":[
                   {"ticketTypeId":"22222222-2222-4222-8222-222222222222","quantity":1}]}}
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));
  }
}
