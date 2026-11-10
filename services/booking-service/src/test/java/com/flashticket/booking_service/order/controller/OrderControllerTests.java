package com.flashticket.booking_service.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.booking_service.config.BookingJsonConfig;
import com.flashticket.booking_service.config.SecurityConfig;
import com.flashticket.booking_service.dto.response.OrderPage;
import com.flashticket.booking_service.dto.response.OrderView;
import com.flashticket.booking_service.dto.response.SalesSummaryView;
import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.OrderState;
import com.flashticket.booking_service.enums.SalesGrouping;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.exception.GlobalExceptionHandler;
import com.flashticket.booking_service.order.service.BuyerOrdersCacheService;
import com.flashticket.booking_service.order.service.OrderService;
import com.flashticket.booking_service.order.service.SalesStatisticsCacheService;
import com.flashticket.booking_service.order.service.SalesSummaryCacheService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest({
  OrderController.class,
  InternalOrderController.class,
  OrganizerSalesController.class,
  AdminSalesController.class,
  SalesStatisticsController.class
})
@Import({SecurityConfig.class, BookingJsonConfig.class, GlobalExceptionHandler.class})
class OrderControllerTests {
  @MockitoBean BuyerOrdersCacheService buyerOrdersCacheService;
  @MockitoBean SalesStatisticsCacheService salesStatisticsCacheService;

  @Test
  void systemSalesReportsRequireAdmin() throws Exception {
    for (String path :
        List.of(
            "/api/v1/admin/sales/summary",
            "/api/v1/admin/sales/chart",
            "/api/v1/admin/events/sales-statistics")) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
      mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
          .andExpect(status().isForbidden());
      mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
          .andExpect(status().isOk());
    }
    verify(salesStatisticsCacheService).getSalesStatisticsSummary(null, null, null);
    verify(salesStatisticsCacheService)
        .getSalesStatisticsChart(null, null, null, SalesGrouping.DAY);
    verify(salesStatisticsCacheService)
        .getEventSalesStatistics(null, null, null, 0, 10, "soldRevenue", Sort.Direction.DESC);
  }

  @Test
  void organizerSalesReportsAllowAdminOrOwner() throws Exception {
    for (String suffix : List.of("/sales/summary", "/sales/chart", "/events/sales-statistics")) {
      String path = "/api/v1/organizers/owner" + suffix;
      mvc.perform(
              get(path)
                  .with(
                      jwt()
                          .jwt(t -> t.subject("owner"))
                          .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
          .andExpect(status().isOk());
      mvc.perform(
              get(path)
                  .with(
                      jwt()
                          .jwt(t -> t.subject("admin"))
                          .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
          .andExpect(status().isOk());
    }
    org.mockito.Mockito.verify(salesStatisticsCacheService, org.mockito.Mockito.times(2))
        .getSalesStatisticsSummary("owner", null, null);
  }

  @Test
  void organizerSalesReportsRejectOtherOrganizersBeforeCache() throws Exception {
    for (String suffix : List.of("/sales/summary", "/sales/chart", "/events/sales-statistics")) {
      mvc.perform(
              get("/api/v1/organizers/owner" + suffix)
                  .with(
                      jwt()
                          .jwt(t -> t.subject("other"))
                          .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
          .andExpect(status().isForbidden());
      mvc.perform(
              get("/api/v1/organizers/owner" + suffix)
                  .with(
                      jwt()
                          .jwt(t -> t.subject("owner"))
                          .authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(salesStatisticsCacheService, orderService);
  }

  @Test
  void eventSalesStatisticsChecksOwnerBeforeCache() throws Exception {
    UUID event = UUID.randomUUID();
    when(orderService.getSalesEventOrganizerSubject(event, "owner", false)).thenReturn("owner");
    mvc.perform(
            get("/api/v1/events/" + event + "/sales-statistics")
                .with(
                    jwt()
                        .jwt(t -> t.subject("owner"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
        .andExpect(status().isOk());
    var calls = org.mockito.Mockito.inOrder(orderService, salesStatisticsCacheService);
    calls.verify(orderService).getSalesEventOrganizerSubject(event, "owner", false);
    calls.verify(salesStatisticsCacheService).getEventSalesStatistics("owner", event, null, null);
    when(orderService.getSalesEventOrganizerSubject(event, "admin", true)).thenReturn("owner");
    mvc.perform(
            get("/api/v1/events/" + event + "/sales-statistics")
                .with(
                    jwt()
                        .jwt(t -> t.subject("admin"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isOk());
    org.mockito.Mockito.clearInvocations(salesStatisticsCacheService);
    when(orderService.getSalesEventOrganizerSubject(event, "other", false))
        .thenThrow(new BusinessException(ErrorCode.EVENT_NOT_FOUND));
    mvc.perform(
            get("/api/v1/events/" + event + "/sales-statistics")
                .with(
                    jwt()
                        .jwt(t -> t.subject("other"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    verifyNoInteractions(salesStatisticsCacheService);
  }

  @Test
  void salesReportsBindDatesGroupingAndPagination() throws Exception {
    LocalDate from = LocalDate.parse("2026-10-01");
    LocalDate to = LocalDate.parse("2026-10-20");
    for (String base : List.of("/api/v1/admin", "/api/v1/organizers/owner")) {
      mvc.perform(
              get(base + "/sales/summary")
                  .param("from", from.toString())
                  .param("to", to.toString())
                  .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
          .andExpect(status().isOk());
      mvc.perform(
              get(base + "/sales/chart")
                  .param("from", from.toString())
                  .param("to", to.toString())
                  .param("groupBy", "WEEK")
                  .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
          .andExpect(status().isOk());
      mvc.perform(
              get(base + "/events/sales-statistics")
                  .param("from", from.toString())
                  .param("to", to.toString())
                  .param("page", "2")
                  .param("size", "5")
                  .param("sortBy", "soldTicketCount")
                  .param("direction", "ASC")
                  .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
          .andExpect(status().isOk());
      String owner = base.endsWith("admin") ? null : "owner";
      verify(salesStatisticsCacheService).getSalesStatisticsSummary(owner, from, to);
      verify(salesStatisticsCacheService)
          .getSalesStatisticsChart(owner, from, to, SalesGrouping.WEEK);
      verify(salesStatisticsCacheService)
          .getEventSalesStatistics(owner, from, to, 2, 5, "soldTicketCount", Sort.Direction.ASC);
    }
    UUID event = UUID.randomUUID();
    when(orderService.getSalesEventOrganizerSubject(event, "user", true)).thenReturn("owner");
    mvc.perform(
            get("/api/v1/events/" + event + "/sales-statistics")
                .param("from", from.toString())
                .param("to", to.toString())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isOk());
    verify(salesStatisticsCacheService).getEventSalesStatistics("owner", event, from, to);
  }

  @Test
  void salesReportsRejectMalformedParameters() throws Exception {
    for (String path :
        List.of(
            "/api/v1/admin/sales/summary?from=bad",
            "/api/v1/admin/sales/chart?groupBy=YEAR",
            "/api/v1/admin/events/sales-statistics?direction=INVALID",
            "/api/v1/admin/events/sales-statistics?page=x",
            "/api/v1/events/not-a-uuid/sales-statistics")) {
      mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(salesStatisticsCacheService, orderService);
    when(salesStatisticsCacheService.getSalesStatisticsSummary(null, null, null))
        .thenThrow(new BusinessException(ErrorCode.INVALID_DATE_RANGE));
    mvc.perform(
            get("/api/v1/admin/sales/summary")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
  }

  @Autowired MockMvc mvc;
  @Autowired JsonMapper objectMapper;
  @MockitoBean OrderService orderService;
  @MockitoBean SalesSummaryCacheService salesSummaryCacheService;
  @MockitoBean com.flashticket.booking_service.promotion.service.PromotionService promotionService;

  @Test
  void cancelsOrderUsingAuthenticatedBuyer() throws Exception {
    UUID id = UUID.randomUUID();
    when(orderService.cancelOrder("buyer", id))
        .thenReturn(
            new OrderView(
                id,
                UUID.randomUUID(),
                OrderState.CANCELLED,
                "buyer@example.com",
                Currency.VND,
                "100",
                "0",
                "100",
                Instant.now().plusSeconds(900),
                null,
                "1",
                List.of()));
    mvc.perform(
            post("/api/v1/orders/" + id + "/cancel")
                .with(
                    jwt()
                        .jwt(t -> t.subject("buyer"))
                        .authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("CANCELLED"));
    verify(orderService).cancelOrder("buyer", id);
    for (ErrorCode code :
        List.of(
            ErrorCode.ORDER_NOT_FOUND,
            ErrorCode.ORDER_EXPIRED,
            ErrorCode.ORDER_NOT_CANCELLABLE,
            ErrorCode.RESOURCE_BUSY)) {
      when(orderService.cancelOrder("buyer", id)).thenThrow(new BusinessException(code));
      mvc.perform(
              post("/api/v1/orders/" + id + "/cancel")
                  .with(
                      jwt()
                          .jwt(t -> t.subject("buyer"))
                          .authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().is(code.status().value()))
          .andExpect(jsonPath("$.code").value(code.name()));
      org.mockito.Mockito.reset(orderService);
    }
  }

  @Test
  void requiresBuyerAuthenticationForCancellation() throws Exception {
    String path = "/api/v1/orders/" + UUID.randomUUID() + "/cancel";
    mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post(path)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(orderService);
  }

  @Test
  void rejectsInvalidCancellationCommand() throws Exception {
    for (String body : List.of("", "null", "[]", "{", "{\"buyerSubject\":\"other\"}"))
      mvc.perform(
              post("/api/v1/orders/" + UUID.randomUUID() + "/cancel")
                  .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/orders/not-a-uuid/cancel")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(orderService);
  }

  @Test
  void appliesPromotionUsingAuthenticatedBuyer() throws Exception {
    UUID id = UUID.randomUUID();
    when(promotionService.applyPromotionToOrder(eq("buyer"), eq(id), any()))
        .thenReturn(
            new OrderView(
                id,
                UUID.randomUUID(),
                OrderState.PENDING_PAYMENT,
                "buyer@example.com",
                Currency.VND,
                "200",
                "20",
                "180",
                Instant.now().plusSeconds(900),
                null,
                "1",
                List.of()));
    mvc.perform(
            post("/api/v1/orders/" + id + "/promotion")
                .with(
                    jwt()
                        .jwt(t -> t.subject("buyer"))
                        .authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"SAVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value("180"));
    verify(promotionService).applyPromotionToOrder(eq("buyer"), eq(id), any());
  }

  @Test
  void rejectsInvalidPromotionRequest() throws Exception {
    for (String body : List.of("{}", "{\"code\":\" \"}"))
      mvc.perform(
              post("/api/v1/orders/" + UUID.randomUUID() + "/promotion")
                  .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    verifyNoInteractions(promotionService);
  }

  @Test
  void rejectsUnauthorizedPromotionRequest() throws Exception {
    String path = "/api/v1/orders/" + UUID.randomUUID() + "/promotion";
    mvc.perform(post(path)).andExpect(status().isUnauthorized());
    mvc.perform(post(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
        .andExpect(status().isForbidden());
    verifyNoInteractions(promotionService);
  }

  @Test
  void preparesSnapshotForInternalCaller() throws Exception {
    UUID id = UUID.randomUUID();
    when(orderService.preparePurchaseSnapshot("buyer", id))
        .thenReturn(com.flashticket.booking_service.support.BookingFixtures.purchase());
    mvc.perform(
            post("/internal/v1/orders/" + id + "/purchase-snapshot")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"buyerSubject\":\"buyer\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value("200"));
    verify(orderService).preparePurchaseSnapshot("buyer", id);
  }

  @Test
  void rejectsInvalidSnapshotRequest() throws Exception {
    for (String body : List.of("{}", "{\"buyerSubject\":\"\"}"))
      mvc.perform(
              post("/internal/v1/orders/" + UUID.randomUUID() + "/purchase-snapshot")
                  .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    verifyNoInteractions(orderService);
  }

  @Test
  void rejectsBuyerOnInternalSnapshotRoute() throws Exception {
    mvc.perform(
            post("/internal/v1/orders/" + UUID.randomUUID() + "/purchase-snapshot")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
        .andExpect(status().isForbidden());
    verifyNoInteractions(orderService);
  }

  @Test
  void doesNotExposeSeparateFreezeOrSnapshotGet() throws Exception {
    String path = "/internal/v1/orders/" + UUID.randomUUID();
    mvc.perform(
            post(path + "/freeze-payment")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))))
        .andExpect(status().isNotFound());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    path + "/purchase-snapshot")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))))
        .andExpect(status().isMethodNotAllowed());
    verifyNoInteractions(orderService);
  }

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

  @Test
  void getOrderReturnsOrderViewForAuthenticatedBuyer() throws Exception {
    UUID id = UUID.randomUUID();
    UUID eventId = UUID.fromString("11111111-1111-4111-8111-111111111111");
    when(orderService.getOrder("buyer", id))
        .thenReturn(
            new OrderView(
                id,
                eventId,
                OrderState.PENDING_PAYMENT,
                "buyer@example.com",
                Currency.VND,
                "200",
                "20",
                "180",
                Instant.parse("2026-10-28T12:15:00Z"),
                null,
                "1",
                List.of()));
    mvc.perform(
            get("/api/v1/orders/" + id)
                .with(
                    jwt()
                        .jwt(t -> t.subject("buyer"))
                        .authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value(id.toString()))
        .andExpect(jsonPath("$.eventId").value(eventId.toString()))
        .andExpect(jsonPath("$.state").value("PENDING_PAYMENT"))
        .andExpect(jsonPath("$.total").value("180"))
        .andExpect(jsonPath("$.status").doesNotExist());
    verify(orderService).getOrder("buyer", id);
  }

  @Test
  void getOrderReturnsNotFoundForOtherBuyersOrMissingOrder() throws Exception {
    UUID id = UUID.randomUUID();
    when(orderService.getOrder("other-buyer", id))
        .thenThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    mvc.perform(
            get("/api/v1/orders/" + id)
                .with(
                    jwt()
                        .jwt(t -> t.subject("other-buyer"))
                        .authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    verify(orderService).getOrder("other-buyer", id);
  }

  @Test
  void getOrderRejectsInvalidOrderId() throws Exception {
    mvc.perform(
            get("/api/v1/orders/not-a-uuid")
                .with(
                    jwt()
                        .jwt(t -> t.subject("buyer"))
                        .authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(orderService);
  }

  @Test
  void getOrderRequiresAuthentication() throws Exception {
    UUID id = UUID.randomUUID();
    mvc.perform(get("/api/v1/orders/" + id)).andExpect(status().isUnauthorized());
  }

  @Test
  void getOrdersReturnsPageForAuthenticatedBuyer() throws Exception {
    UUID id = UUID.randomUUID();
    var order =
        new OrderView(
            id,
            UUID.randomUUID(),
            OrderState.COMPLETED,
            "buyer@example.com",
            Currency.VND,
            "200",
            "20",
            "180",
            Instant.parse("2026-11-08T10:15:00Z"),
            Instant.parse("2026-11-08T10:05:00Z"),
            "1",
            List.of());
    when(buyerOrdersCacheService.getOrders("buyer", OrderState.COMPLETED, 2, 5))
        .thenReturn(new OrderPage(List.of(order), 2, 5, 11, 3));
    when(buyerOrdersCacheService.getOrders("buyer", null, 0, 10))
        .thenReturn(new OrderPage(List.of(), 0, 10, 0, 0));

    mvc.perform(
            get("/api/v1/orders")
                .param("state", "COMPLETED")
                .param("page", "2")
                .param("size", "5")
                .with(
                    jwt()
                        .jwt(t -> t.subject("buyer"))
                        .authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].orderId").value(id.toString()))
        .andExpect(jsonPath("$.items[0].state").value("COMPLETED"))
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.size").value(5))
        .andExpect(jsonPath("$.totalElements").value(11))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
    // Không truyền tham số => trang 0, size 10, không lọc state.
    mvc.perform(
            get("/api/v1/orders")
                .with(
                    jwt()
                        .jwt(t -> t.subject("buyer"))
                        .authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    verify(buyerOrdersCacheService).getOrders("buyer", OrderState.COMPLETED, 2, 5);
    verify(buyerOrdersCacheService).getOrders("buyer", null, 0, 10);
    verifyNoInteractions(orderService);
  }

  @Test
  void getOrdersRejectsInvalidParamsAndNonBuyer() throws Exception {
    mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/v1/orders")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
        .andExpect(status().isForbidden());
    for (String[] param :
        List.of(
            new String[] {"state", "PAID"}, new String[] {"page", "x"}, new String[] {"size", "x"}))
      mvc.perform(
              get("/api/v1/orders")
                  .param(param[0], param[1])
                  .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    verifyNoInteractions(orderService);
    verifyNoInteractions(buyerOrdersCacheService);
    // page/size ngoài giới hạn do service từ chối qua cache facade.
    when(buyerOrdersCacheService.getOrders("buyer", null, 0, 500))
        .thenThrow(new BusinessException(ErrorCode.INVALID_REQUEST));
    mvc.perform(
            get("/api/v1/orders")
                .param("size", "500")
                .with(
                    jwt()
                        .jwt(t -> t.subject("buyer"))
                        .authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void getSalesSummaryUsesOrganizerSubjectAndDates() throws Exception {
    UUID eventId = UUID.randomUUID();
    var from = LocalDate.parse("2026-11-01");
    var to = LocalDate.parse("2026-11-09");
    when(salesSummaryCacheService.getSalesSummary("organizer", eventId, from, to))
        .thenReturn(
            new SalesSummaryView(
                eventId,
                "Concert A",
                from,
                to,
                "Asia/Ho_Chi_Minh",
                Instant.parse("2026-11-09T08:00:00Z"),
                Currency.VND,
                1,
                2,
                "200",
                "20",
                "180",
                List.of(
                    new SalesSummaryView.StateSales(
                        OrderState.COMPLETED, 1, 2, "200", "20", "180")),
                List.of()));

    mvc.perform(
            get("/api/v1/organizer/events/" + eventId + "/sales-summary")
                .param("from", "2026-11-01")
                .param("to", "2026-11-09")
                .with(
                    jwt()
                        .jwt(t -> t.subject("organizer"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eventId").value(eventId.toString()))
        .andExpect(jsonPath("$.from").value("2026-11-01"))
        .andExpect(jsonPath("$.to").value("2026-11-09"))
        .andExpect(jsonPath("$.soldRevenue").value("180"))
        .andExpect(jsonPath("$.byState[0].state").value("COMPLETED"))
        .andExpect(jsonPath("$.byState[0].ticketCount").value(2));
    verify(salesSummaryCacheService).getSalesSummary("organizer", eventId, from, to);
  }

  @Test
  void getSalesSummaryRequiresOrganizerAndValidDates() throws Exception {
    String path = "/api/v1/organizer/events/" + UUID.randomUUID() + "/sales-summary";
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
        .andExpect(status().isForbidden());
    for (String date : List.of("09-11-2026", "2026-13-01", "x"))
      mvc.perform(
              get(path)
                  .param("from", date)
                  .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mvc.perform(
            get("/api/v1/organizer/events/not-a-uuid/sales-summary")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(salesSummaryCacheService);
    // Lỗi nghiệp vụ từ service giữ nguyên status/code.
    UUID eventId = UUID.randomUUID();
    for (ErrorCode code : List.of(ErrorCode.EVENT_NOT_FOUND, ErrorCode.INVALID_DATE_RANGE)) {
      when(salesSummaryCacheService.getSalesSummary("organizer", eventId, null, null))
          .thenThrow(new BusinessException(code));
      mvc.perform(
              get("/api/v1/organizer/events/" + eventId + "/sales-summary")
                  .with(
                      jwt()
                          .jwt(t -> t.subject("organizer"))
                          .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
          .andExpect(status().is(code.status().value()))
          .andExpect(jsonPath("$.code").value(code.name()));
      org.mockito.Mockito.reset(salesSummaryCacheService);
    }
  }
}
