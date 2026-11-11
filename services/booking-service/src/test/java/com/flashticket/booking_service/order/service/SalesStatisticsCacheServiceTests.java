package com.flashticket.booking_service.order.service;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flashticket.booking_service.dto.response.*;
import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.EventState;
import com.flashticket.booking_service.enums.OrderState;
import com.flashticket.booking_service.enums.SalesGrouping;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.redis.service.RedisService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SalesStatisticsCacheServiceTests {
  private static final String PREFIX = "test:booking:sales-statistics:";
  private static final Duration TTL = Duration.ofSeconds(30);
  private static final LocalDate FROM = LocalDate.parse("2026-11-01");
  private static final LocalDate TO = LocalDate.parse("2026-11-09");
  @Mock OrderService orderService;
  @Mock StringRedisTemplate stringRedisTemplate;
  @Mock ValueOperations<String, String> values;
  JsonMapper jsonMapper;
  SalesStatisticsCacheService cacheService;
  final SalesStatisticsSummaryView summary =
      new SalesStatisticsSummaryView(
          FROM,
          TO,
          null,
          "Asia/Ho_Chi_Minh",
          NOW,
          Currency.VND,
          1,
          2,
          "200",
          "20",
          "180",
          List.of(new SalesSummaryView.StateSales(OrderState.COMPLETED, 1, 2, "200", "20", "180")));
  final SalesStatisticsChartView chart =
      new SalesStatisticsChartView(
          FROM,
          TO,
          null,
          "Asia/Ho_Chi_Minh",
          NOW,
          Currency.VND,
          SalesGrouping.DAY,
          List.of(new SalesStatisticsChartView.PeriodSales(FROM, FROM, 1, 2, "180")));
  final EventSalesStatisticsPage page =
      new EventSalesStatisticsPage(
          FROM,
          TO,
          null,
          "Asia/Ho_Chi_Minh",
          NOW,
          Currency.VND,
          List.of(
              new EventSalesStatisticsPage.EventSales(
                  EVENT, "Concert", "owner", EventState.PUBLISHED, 1, 2, "180")),
          0,
          10,
          1,
          1);
  final SalesSummaryView detail =
      new SalesSummaryView(
          EVENT,
          "Concert",
          FROM,
          TO,
          "Asia/Ho_Chi_Minh",
          NOW,
          Currency.VND,
          1,
          2,
          "200",
          "20",
          "180",
          summary.getByState(),
          List.of(new SalesSummaryView.TicketTypeSales(TICKET, "VIP", 2, "200")));

  @BeforeEach
  void setUp() {
    jsonMapper = jsonMapper();
    cacheService =
        new SalesStatisticsCacheService(
            orderService, new RedisService(stringRedisTemplate), jsonMapper);
    ReflectionTestUtils.setField(cacheService, "cacheTtl", TTL);
    ReflectionTestUtils.setField(cacheService, "keyPrefix", PREFIX);
  }

  @Test
  void returnsCachedSalesReportsWithoutAggregatingDatabase() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(values.get(anyString()))
        .thenAnswer(
            call -> {
              String key = call.getArgument(0);
              Object report =
                  key.contains(":summary:")
                      ? summary
                      : key.contains(":chart:") ? chart : key.contains(":events:") ? page : detail;
              return jsonMapper.writeValueAsString(report);
            });
    assertThat(cacheService.getSalesStatisticsSummary(null, FROM, TO))
        .usingRecursiveComparison()
        .isEqualTo(summary);
    assertThat(cacheService.getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY))
        .usingRecursiveComparison()
        .isEqualTo(chart);
    assertThat(
            cacheService.getEventSalesStatistics(
                null, FROM, TO, 0, 10, "soldRevenue", Sort.Direction.DESC))
        .usingRecursiveComparison()
        .isEqualTo(page);
    assertThat(cacheService.getEventSalesStatistics("owner", EVENT, FROM, TO))
        .usingRecursiveComparison()
        .isEqualTo(detail);
    verifyNoInteractions(orderService);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void separatesCacheKeysByScopeAndReportFilters() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    String owner = "owner:with/colon";
    cacheService.getSalesStatisticsSummary(null, FROM, TO);
    cacheService.getSalesStatisticsSummary(owner, FROM, TO);
    cacheService.getSalesStatisticsSummary(owner, null, null);
    cacheService.getSalesStatisticsSummary(owner, FROM.minusDays(1), TO);
    for (var grouping : SalesGrouping.values())
      cacheService.getSalesStatisticsChart(owner, FROM, TO, grouping);
    cacheService.getEventSalesStatistics(
        owner, FROM, TO, 0, 10, "soldRevenue", Sort.Direction.DESC);
    cacheService.getEventSalesStatistics(
        owner, FROM, TO, 1, 10, "soldRevenue", Sort.Direction.DESC);
    cacheService.getEventSalesStatistics(
        owner, FROM, TO, 0, 20, "soldRevenue", Sort.Direction.DESC);
    cacheService.getEventSalesStatistics(
        owner, FROM, TO, 0, 10, "soldOrderCount", Sort.Direction.ASC);
    cacheService.getEventSalesStatistics(owner, EVENT, FROM, TO);
    var keys = ArgumentCaptor.forClass(String.class);
    verify(values, times(12)).get(keys.capture());
    assertThat(keys.getAllValues()).doesNotHaveDuplicates();
    assertThat(keys.getAllValues()).allSatisfy(key -> assertThat(key).doesNotContain(owner));
    assertThat(keys.getAllValues())
        .anySatisfy(
            key ->
                assertThat(key)
                    .contains(
                        Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(owner.getBytes(StandardCharsets.UTF_8))));
  }

  @Test
  void fallsBackWhenSalesCacheIsUnavailableOrCorrupt() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(orderService.getSalesStatisticsSummary(null, FROM, TO)).thenReturn(summary);
    when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("test-only"));
    assertThat(cacheService.getSalesStatisticsSummary(null, FROM, TO)).isSameAs(summary);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    reset(values);
    when(values.get(anyString())).thenReturn("{broken");
    assertThat(cacheService.getSalesStatisticsSummary(null, FROM, TO)).isSameAs(summary);
    verify(stringRedisTemplate).delete(anyString());
    verify(values).set(anyString(), anyString(), eq(TTL));
    reset(values);
    doThrow(new RedisConnectionFailureException("test-only"))
        .when(values)
        .set(anyString(), anyString(), any(Duration.class));
    assertThat(cacheService.getSalesStatisticsSummary(null, FROM, TO)).isSameAs(summary);
    reset(values);
    when(values.get(anyString())).thenReturn("null");
    when(stringRedisTemplate.delete(anyString()))
        .thenThrow(new RedisConnectionFailureException("test-only"));
    assertThat(cacheService.getSalesStatisticsSummary(null, FROM, TO)).isSameAs(summary);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    for (var ttl : List.of(Duration.ZERO, Duration.ofSeconds(-1))) {
      ReflectionTestUtils.setField(cacheService, "cacheTtl", ttl);
      assertThat(cacheService.getSalesStatisticsSummary(null, FROM, TO)).isSameAs(summary);
    }
  }

  @Test
  void doesNotCacheSalesBusinessErrors() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(orderService.getSalesStatisticsSummary(null, FROM, TO))
        .thenThrow(new BusinessException(ErrorCode.INVALID_DATE_RANGE));
    when(orderService.getEventSalesStatistics("owner", EVENT, FROM, TO))
        .thenThrow(new BusinessException(ErrorCode.EVENT_NOT_FOUND));
    assertThatThrownBy(() -> cacheService.getSalesStatisticsSummary(null, FROM, TO))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> cacheService.getEventSalesStatistics("owner", EVENT, FROM, TO))
        .isInstanceOf(BusinessException.class);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void cachesValidEmptySalesReports() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    var empty =
        new SalesStatisticsChartView(
            FROM,
            TO,
            "empty",
            "Asia/Ho_Chi_Minh",
            NOW,
            Currency.VND,
            SalesGrouping.DAY,
            List.of(new SalesStatisticsChartView.PeriodSales(FROM, FROM, 0, 0, "0")));
    when(orderService.getSalesStatisticsChart("empty", FROM, TO, SalesGrouping.DAY))
        .thenReturn(empty);
    Map<String, String> cached = new HashMap<>();
    when(values.get(anyString())).thenAnswer(call -> cached.get(call.getArgument(0)));
    doAnswer(
            call -> {
              cached.put(call.getArgument(0), call.getArgument(1));
              return null;
            })
        .when(values)
        .set(anyString(), anyString(), eq(TTL));
    cacheService.getSalesStatisticsChart("empty", FROM, TO, SalesGrouping.DAY);
    assertThat(cacheService.getSalesStatisticsChart("empty", FROM, TO, SalesGrouping.DAY))
        .usingRecursiveComparison()
        .isEqualTo(empty);
    verify(orderService, times(1)).getSalesStatisticsChart("empty", FROM, TO, SalesGrouping.DAY);
  }
}
