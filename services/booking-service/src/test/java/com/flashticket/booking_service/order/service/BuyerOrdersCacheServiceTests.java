package com.flashticket.booking_service.order.service;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flashticket.booking_service.dto.response.OrderItemView;
import com.flashticket.booking_service.dto.response.OrderPage;
import com.flashticket.booking_service.dto.response.OrderView;
import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.OrderState;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.order.event.BuyerOrdersChangedEvent;
import com.flashticket.booking_service.redis.service.RedisService;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class BuyerOrdersCacheServiceTests {
  private static final String PREFIX = "test:buyer-orders:";
  private static final String BUYER_KEY = PREFIX + "YnV5ZXI";
  private static final Duration TTL = Duration.ofHours(24);
  @Mock OrderService orderService;
  @Mock StringRedisTemplate stringRedisTemplate;
  @Mock ValueOperations<String, String> values;
  JsonMapper jsonMapper;
  BuyerOrdersCacheService cacheService;
  final OrderPage orderPage =
      new OrderPage(
          List.of(
              new OrderView(
                  ORDER,
                  EVENT,
                  OrderState.COMPLETED,
                  "buyer@example.com",
                  Currency.VND,
                  "200",
                  "20",
                  "180",
                  NOW.plusSeconds(900),
                  NOW,
                  "1",
                  List.of(
                      new OrderItemView(
                          MESSAGE, TICKET, "VIP", 2, "100", "200", SECTOR, List.of(seat()))))),
          0,
          10,
          1,
          1);

  @BeforeEach
  void setUp() {
    jsonMapper = jsonMapper();
    cacheService =
        new BuyerOrdersCacheService(
            orderService, new RedisService(stringRedisTemplate), jsonMapper);
    ReflectionTestUtils.setField(cacheService, "cacheTtl", TTL);
    ReflectionTestUtils.setField(cacheService, "keyPrefix", PREFIX);
  }

  @Test
  void returnsCachedBuyerOrdersWithoutQueryingDatabase() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(values.get(BUYER_KEY + ":generation")).thenReturn("g1");
    when(values.get(BUYER_KEY + ":data:g1:state:COMPLETED:page:0:size:10"))
        .thenReturn(jsonMapper.writeValueAsString(orderPage));
    assertThat(cacheService.getOrders("buyer", OrderState.COMPLETED, 0, 10))
        .usingRecursiveComparison()
        .isEqualTo(orderPage);
    verifyNoInteractions(orderService);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void separatesBuyerStateAndPaginationKeys() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(values.get(anyString()))
        .thenAnswer(call -> ((String) call.getArgument(0)).endsWith(":generation") ? "g1" : null);
    when(orderService.getOrders(anyString(), nullable(OrderState.class), anyInt(), anyInt()))
        .thenReturn(orderPage);
    cacheService.getOrders("buyer", null, 0, 10);
    cacheService.getOrders("buyer", OrderState.COMPLETED, 0, 10);
    cacheService.getOrders("buyer", null, 1, 10);
    cacheService.getOrders("buyer", null, 0, 20);
    cacheService.getOrders("buyer:other", null, 0, 10);
    cacheService.getOrders("buyer", null, 0, 10);
    var keys = ArgumentCaptor.forClass(String.class);
    verify(values, times(6)).set(keys.capture(), anyString(), eq(TTL));
    assertThat(keys.getAllValues().subList(0, 5)).doesNotHaveDuplicates();
    assertThat(keys.getAllValues().get(5)).isEqualTo(keys.getAllValues().get(0));
    assertThat(keys.getAllValues())
        .allSatisfy(key -> assertThat(key).doesNotContain("buyer:other"));
  }

  @Test
  void makesPreviousBuyerPagesUnreachableAfterInvalidation() {
    Map<String, String> redis = new HashMap<>();
    redis.put(BUYER_KEY + ":generation", "old");
    String otherKey = PREFIX + "b3RoZXI";
    redis.put(otherKey + ":generation", "other-generation");
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenAnswer(call -> redis.get(call.getArgument(0)));
    doAnswer(
            call -> {
              redis.put(call.getArgument(0), call.getArgument(1));
              return null;
            })
        .when(values)
        .set(anyString(), anyString(), any(Duration.class));
    doAnswer(
            call -> {
              redis.put(call.getArgument(0), call.getArgument(1));
              return null;
            })
        .when(values)
        .set(anyString(), anyString());
    when(orderService.getOrders(anyString(), nullable(OrderState.class), anyInt(), anyInt()))
        .thenReturn(orderPage);
    cacheService.getOrders("buyer", null, 0, 10);
    cacheService.getOrders("buyer", OrderState.COMPLETED, 1, 5);
    cacheService.getOrders("other", null, 0, 10);
    clearInvocations(orderService);
    cacheService.invalidateBuyerOrdersCache(new BuyerOrdersChangedEvent("buyer"));
    assertThat(redis.get(BUYER_KEY + ":generation")).isNotEqualTo("old");
    assertThat(redis.get(otherKey + ":generation")).isEqualTo("other-generation");
    cacheService.getOrders("buyer", null, 0, 10);
    cacheService.getOrders("buyer", OrderState.COMPLETED, 1, 5);
    cacheService.getOrders("other", null, 0, 10);
    verify(orderService).getOrders("buyer", null, 0, 10);
    verify(orderService).getOrders("buyer", OrderState.COMPLETED, 1, 5);
    verifyNoMoreInteractions(orderService);
    assertThat(redis).containsKey(BUYER_KEY + ":data:old:state:ALL:page:0:size:10");
    verify(stringRedisTemplate, never()).delete(anyString());
    verify(stringRedisTemplate, never()).keys(anyString());
  }

  @Test
  void keepsStaleReadersInTheirOriginalGeneration() {
    Map<String, String> redis = new HashMap<>();
    redis.put(BUYER_KEY + ":generation", "old");
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenAnswer(call -> redis.get(call.getArgument(0)));
    doAnswer(
            call -> {
              redis.put(call.getArgument(0), call.getArgument(1));
              return null;
            })
        .when(values)
        .set(anyString(), anyString(), any(Duration.class));
    doAnswer(
            call -> {
              redis.put(call.getArgument(0), call.getArgument(1));
              return null;
            })
        .when(values)
        .set(anyString(), anyString());
    var fresh = new OrderPage(List.of(), 0, 10, 0, 0);
    when(orderService.getOrders("buyer", null, 0, 10))
        .thenAnswer(
            call -> {
              cacheService.invalidateBuyerOrdersCache(new BuyerOrdersChangedEvent("buyer"));
              return orderPage;
            })
        .thenReturn(fresh);
    assertThat(cacheService.getOrders("buyer", null, 0, 10)).isSameAs(orderPage);
    String newGeneration = redis.get(BUYER_KEY + ":generation");
    assertThat(redis).containsKey(BUYER_KEY + ":data:old:state:ALL:page:0:size:10");
    assertThat(redis)
        .doesNotContainKey(BUYER_KEY + ":data:" + newGeneration + ":state:ALL:page:0:size:10");
    assertThat(cacheService.getOrders("buyer", null, 0, 10)).isSameAs(fresh);
    assertThat(cacheService.getOrders("buyer", null, 0, 10))
        .usingRecursiveComparison()
        .isEqualTo(fresh);
    verify(orderService, times(2)).getOrders("buyer", null, 0, 10);
  }

  @Test
  void fallsBackWhenBuyerOrdersCacheFailsOrIsDisabled() {
    when(orderService.getOrders("buyer", null, 0, 10)).thenReturn(orderPage);
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("test-only"));
    assertThat(cacheService.getOrders("buyer", null, 0, 10)).isSameAs(orderPage);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    reset(values);
    when(values.get(BUYER_KEY + ":generation")).thenReturn("g1");
    when(values.get(BUYER_KEY + ":data:g1:state:ALL:page:0:size:10")).thenReturn("{broken");
    assertThat(cacheService.getOrders("buyer", null, 0, 10)).isSameAs(orderPage);
    verify(stringRedisTemplate).delete(BUYER_KEY + ":data:g1:state:ALL:page:0:size:10");
    doThrow(new RedisConnectionFailureException("test-only"))
        .when(values)
        .set(anyString(), anyString(), any(Duration.class));
    assertThat(cacheService.getOrders("buyer", null, 0, 10)).isSameAs(orderPage);
    reset(values);
    when(values.get(BUYER_KEY + ":generation")).thenReturn("g1");
    when(values.get(BUYER_KEY + ":data:g1:state:ALL:page:0:size:10")).thenReturn("null");
    when(stringRedisTemplate.delete(anyString()))
        .thenThrow(new RedisConnectionFailureException("test-only"));
    assertThat(cacheService.getOrders("buyer", null, 0, 10)).isSameAs(orderPage);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    reset(values);
    when(values.get(BUYER_KEY + ":generation")).thenReturn(null, "initialized-by-other-request");
    assertThat(cacheService.getOrders("buyer", null, 0, 10)).isSameAs(orderPage);
    verify(values).setIfAbsent(eq(BUYER_KEY + ":generation"), anyString());
    verify(values)
        .set(
            eq(BUYER_KEY + ":data:initialized-by-other-request:state:ALL:page:0:size:10"),
            anyString(),
            eq(TTL));
    doThrow(new RedisConnectionFailureException("test-only"))
        .when(values)
        .set(anyString(), anyString());
    assertThatCode(
            () -> cacheService.invalidateBuyerOrdersCache(new BuyerOrdersChangedEvent("buyer")))
        .doesNotThrowAnyException();
    reset(stringRedisTemplate, values);
    for (var ttl : List.of(Duration.ZERO, Duration.ofSeconds(-1))) {
      ReflectionTestUtils.setField(cacheService, "cacheTtl", ttl);
      assertThat(cacheService.getOrders("buyer", null, 0, 10)).isSameAs(orderPage);
    }
    verifyNoInteractions(stringRedisTemplate, values);
  }

  @Test
  void cachesValidEmptyPagesWithConfiguredTtl() {
    Map<String, String> redis = new HashMap<>();
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenAnswer(call -> redis.get(call.getArgument(0)));
    when(values.setIfAbsent(anyString(), anyString()))
        .thenAnswer(call -> redis.putIfAbsent(call.getArgument(0), call.getArgument(1)) == null);
    doAnswer(
            call -> {
              redis.put(call.getArgument(0), call.getArgument(1));
              return null;
            })
        .when(values)
        .set(anyString(), anyString(), any(Duration.class));
    var empty = new OrderPage(List.of(), 0, 10, 0, 0);
    when(orderService.getOrders("buyer", null, 0, 10)).thenReturn(empty);
    cacheService.getOrders("buyer", null, 0, 10);
    assertThat(cacheService.getOrders("buyer", null, 0, 10))
        .usingRecursiveComparison()
        .isEqualTo(empty);
    verify(orderService).getOrders("buyer", null, 0, 10);
    verify(values).set(anyString(), anyString(), eq(Duration.ofHours(24)));
    when(orderService.getOrders("buyer", null, 0, 500))
        .thenThrow(new BusinessException(ErrorCode.INVALID_REQUEST));
    assertThatThrownBy(() -> cacheService.getOrders("buyer", null, 0, 500))
        .isInstanceOf(BusinessException.class);
    when(orderService.getOrders(null, null, 0, 10))
        .thenThrow(new BusinessException(ErrorCode.UNAUTHENTICATED));
    assertThatThrownBy(() -> cacheService.getOrders(null, null, 0, 10))
        .isInstanceOf(BusinessException.class);
    verify(values, times(1)).set(anyString(), anyString(), any(Duration.class));
  }
}
