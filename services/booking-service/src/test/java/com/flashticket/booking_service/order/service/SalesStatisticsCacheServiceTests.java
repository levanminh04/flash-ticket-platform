package com.flashticket.booking_service.order.service;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flashticket.booking_service.config.BookingChartReplicaHealth;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SalesStatisticsCacheServiceTests {
  private static final String PREFIX = "test:booking:sales-statistics:";
  private static final Duration TTL = Duration.ofSeconds(30);
  private static final LocalDate FROM = LocalDate.parse("2026-11-01");
  private static final LocalDate TO = LocalDate.parse("2026-11-09");
  @Mock OrderService orderService;
  @Mock ObjectProvider<TransactionTemplate> chartTransactionProvider;
  @Mock ObjectProvider<TransactionTemplate> reportTransactionProvider;
  @Mock ObjectProvider<BookingChartReplicaHealth> replicaHealthProvider;
  @Mock BookingChartReplicaHealth replicaHealth;
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
            orderService,
            new RedisService(stringRedisTemplate),
            jsonMapper,
            chartTransactionProvider,
            reportTransactionProvider,
            replicaHealthProvider,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    ReflectionTestUtils.setField(cacheService, "cacheTtl", TTL);
    ReflectionTestUtils.setField(cacheService, "keyPrefix", PREFIX);
  }

  @Test
  void organizerReportsRespectReplicaFlagScopeAndCache() {
    var manager = mock(PlatformTransactionManager.class);
    var status = new SimpleTransactionStatus();
    var transaction = new TransactionTemplate(manager);
    transaction.setName("bookingSalesReportRead");
    transaction.setReadOnly(true);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    when(manager.getTransaction(transaction)).thenReturn(status);
    when(replicaHealth.tryBeginRead()).thenReturn(true);
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(orderService.getSalesStatisticsSummary("owner", FROM, TO)).thenReturn(summary);
    when(orderService.getSalesStatisticsChart("owner", FROM, TO, SalesGrouping.DAY))
        .thenReturn(chart);
    when(orderService.getEventSalesStatistics(
            "owner", FROM, TO, 0, 10, "soldRevenue", Sort.Direction.DESC))
        .thenReturn(page);
    new org.springframework.boot.test.context.runner.ApplicationContextRunner()
        .withPropertyValues(
            "booking.sales-organizer-report-replica.enabled=true",
            "booking.sales-statistics-cache.key-prefix=" + PREFIX)
        .run(
            context -> {
              var beans = context.getBeanFactory();
              beans.setConversionService(
                  org.springframework.boot.convert.ApplicationConversionService
                      .getSharedInstance());
              beans.registerSingleton("orderService", orderService);
              beans.registerSingleton("redisService", new RedisService(stringRedisTemplate));
              beans.registerSingleton("jsonMapper", jsonMapper);
              beans.registerSingleton("bookingSalesReportReadTransactionTemplate", transaction);
              beans.registerSingleton("bookingChartReplicaHealth", replicaHealth);
              beans.registerSingleton(
                  "metrics", new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
              var service = beans.createBean(SalesStatisticsCacheService.class);
              assertThat(service.getSalesStatisticsSummary("owner", FROM, TO)).isSameAs(summary);
              assertThat(service.getSalesStatisticsChart("owner", FROM, TO, SalesGrouping.DAY))
                  .isSameAs(chart);
              assertThat(
                      service.getEventSalesStatistics(
                          "owner", FROM, TO, 0, 10, "soldRevenue", Sort.Direction.DESC))
                  .isSameAs(page);
              verify(manager, times(3)).commit(status);
              verify(replicaHealth, times(3)).finishRead();
              clearInvocations(manager, replicaHealth, orderService);
              when(orderService.getSalesStatisticsSummary(null, FROM, TO)).thenReturn(summary);
              when(orderService.getEventSalesStatistics(
                      null, FROM, TO, 0, 10, "soldRevenue", Sort.Direction.DESC))
                  .thenReturn(page);
              service.getSalesStatisticsSummary(null, FROM, TO);
              service.getEventSalesStatistics(
                  null, FROM, TO, 0, 10, "soldRevenue", Sort.Direction.DESC);
              verifyNoInteractions(manager, replicaHealth);
              String scope =
                  Base64.getUrlEncoder()
                      .withoutPadding()
                      .encodeToString("owner".getBytes(StandardCharsets.UTF_8));
              when(values.get(PREFIX + "summary:organizer:" + scope + ":" + FROM + ":" + TO))
                  .thenReturn(jsonMapper.writeValueAsString(summary));
              assertThat(service.getSalesStatisticsSummary("owner", FROM, TO))
                  .usingRecursiveComparison()
                  .isEqualTo(summary);
              verifyNoInteractions(manager, replicaHealth);
              ReflectionTestUtils.setField(service, "cacheTtl", Duration.ZERO);
              assertThat(service.getSalesStatisticsSummary("owner", FROM, TO)).isSameAs(summary);
              verify(manager).commit(status);
            });
  }

  @Test
  void eventReportUsesReplicaOnlyAfterControllerScopeResolution() {
    var manager = mock(PlatformTransactionManager.class);
    var status = new SimpleTransactionStatus();
    var transaction = new TransactionTemplate(manager);
    transaction.setName("bookingSalesReportRead");
    when(manager.getTransaction(transaction)).thenReturn(status);
    when(replicaHealth.tryBeginRead()).thenReturn(true);
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(orderService.getEventSalesStatistics("owner", EVENT, FROM, TO)).thenReturn(detail);
    new org.springframework.boot.test.context.runner.ApplicationContextRunner()
        .withPropertyValues("booking.sales-event-report-replica.enabled=true")
        .run(
            context -> {
              var beans = context.getBeanFactory();
              beans.setConversionService(
                  org.springframework.boot.convert.ApplicationConversionService
                      .getSharedInstance());
              beans.registerSingleton("orderService", orderService);
              beans.registerSingleton("redisService", new RedisService(stringRedisTemplate));
              beans.registerSingleton("jsonMapper", jsonMapper);
              beans.registerSingleton("bookingSalesReportReadTransactionTemplate", transaction);
              beans.registerSingleton("bookingChartReplicaHealth", replicaHealth);
              beans.registerSingleton(
                  "metrics", new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
              var service = beans.createBean(SalesStatisticsCacheService.class);
              assertThat(service.getEventSalesStatistics("owner", EVENT, FROM, TO))
                  .isSameAs(detail);
              verify(manager).commit(status);
              verify(orderService).getEventSalesStatistics("owner", EVENT, FROM, TO);
              verify(replicaHealth).finishRead();
              clearInvocations(manager, replicaHealth, orderService);
              ReflectionTestUtils.setField(service, "cacheTtl", Duration.ZERO);
              service.getEventSalesStatistics("owner", EVENT, FROM, TO);
              verify(manager).commit(status);
              clearInvocations(manager, replicaHealth, orderService);
              TransactionSynchronizationManager.initSynchronization();
              try {
                service.getEventSalesStatistics("owner", EVENT, FROM, TO);
                verifyNoInteractions(manager, replicaHealth);
              } finally {
                TransactionSynchronizationManager.clearSynchronization();
              }
            });
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
                      : key.startsWith(PREFIX + "chart:v2:")
                          ? chart
                          : key.contains(":events:") ? page : detail;
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
    verifyNoInteractions(orderService, chartTransactionProvider);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void routesOnlySystemChartLoadsThroughReadTransaction() {
    var manager = mock(PlatformTransactionManager.class);
    var status = new SimpleTransactionStatus();
    var transaction = new TransactionTemplate(manager);
    transaction.setName("bookingAdminChartRead");
    transaction.setReadOnly(true);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    when(manager.getTransaction(transaction)).thenReturn(status);
    when(chartTransactionProvider.getIfAvailable())
        .thenReturn(transaction, transaction, transaction, null);
    when(replicaHealthProvider.getIfAvailable()).thenReturn(replicaHealth);
    when(replicaHealth.tryBeginRead()).thenReturn(true);
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(orderService.getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY)).thenReturn(chart);
    when(orderService.getSalesStatisticsChart("owner", FROM, TO, SalesGrouping.DAY))
        .thenReturn(chart);
    String key = PREFIX + "chart:v2:system:" + FROM + ":" + TO + ":DAY";

    assertThat(cacheService.getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY))
        .isSameAs(chart);
    var sequence = inOrder(values, replicaHealth, manager, orderService);
    sequence.verify(values).get(key);
    sequence.verify(replicaHealth).tryBeginRead();
    sequence.verify(manager).getTransaction(transaction);
    sequence.verify(orderService).getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY);
    sequence.verify(manager).commit(status);
    sequence.verify(replicaHealth).finishRead();
    sequence.verify(values).set(eq(key), anyString(), eq(TTL));

    clearInvocations(values, manager, orderService, chartTransactionProvider);
    when(values.get(key)).thenThrow(new RedisConnectionFailureException("test-only"));
    assertThat(cacheService.getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY))
        .isSameAs(chart);
    verify(manager).getTransaction(transaction);
    verify(manager).commit(status);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));

    clearInvocations(values, manager, chartTransactionProvider);
    ReflectionTestUtils.setField(cacheService, "cacheTtl", Duration.ZERO);
    assertThat(cacheService.getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY))
        .isSameAs(chart);
    verify(manager).getTransaction(transaction);
    verify(manager).commit(status);
    verifyNoInteractions(values);

    clearInvocations(manager, chartTransactionProvider, orderService);
    assertThat(cacheService.getSalesStatisticsChart("owner", FROM, TO, SalesGrouping.DAY))
        .isSameAs(chart);
    verifyNoInteractions(chartTransactionProvider);
    assertThat(cacheService.getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY))
        .isSameAs(chart);
    verify(chartTransactionProvider).getIfAvailable();
    verify(orderService).getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY);
    verify(orderService).getSalesStatisticsChart("owner", FROM, TO, SalesGrouping.DAY);
    verifyNoInteractions(manager, values);
  }

  @Test
  void bypassesReaderInsideExistingTransaction() {
    ReflectionTestUtils.setField(cacheService, "cacheTtl", Duration.ZERO);
    when(orderService.getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY)).thenReturn(chart);

    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      assertThat(cacheService.getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY))
          .isSameAs(chart);
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    TransactionSynchronizationManager.initSynchronization();
    try {
      assertThat(cacheService.getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY))
          .isSameAs(chart);
      assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
    verifyNoInteractions(chartTransactionProvider, replicaHealthProvider);
    verify(orderService, times(2)).getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY);
  }

  @Test
  void selectsWriterWhenReplicaIsUnavailableOrBusy() {
    ReflectionTestUtils.setField(cacheService, "cacheTtl", Duration.ZERO);
    var manager = mock(PlatformTransactionManager.class);
    when(chartTransactionProvider.getIfAvailable()).thenReturn(new TransactionTemplate(manager));
    when(replicaHealthProvider.getIfAvailable()).thenReturn(null, replicaHealth);
    when(orderService.getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY)).thenReturn(chart);

    assertThat(cacheService.getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY))
        .isSameAs(chart);
    assertThat(cacheService.getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY))
        .isSameAs(chart);
    verifyNoInteractions(manager);
    verify(orderService, times(2)).getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY);
    verify(replicaHealth).tryBeginRead();
    verifyNoMoreInteractions(replicaHealth);
  }

  @Test
  void propagatesReaderFailureWithoutRetryOrCaching() {
    var manager = mock(PlatformTransactionManager.class);
    var status = new SimpleTransactionStatus();
    var transaction = new TransactionTemplate(manager);
    var readFailure = new DataAccessResourceFailureException("test reader query failed");
    var beginFailure = new CannotCreateTransactionException("test reader transaction failed");
    when(chartTransactionProvider.getIfAvailable()).thenReturn(transaction);
    when(replicaHealthProvider.getIfAvailable()).thenReturn(replicaHealth);
    when(replicaHealth.tryBeginRead()).thenReturn(true);
    when(manager.getTransaction(transaction)).thenReturn(status).thenThrow(beginFailure);
    when(orderService.getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY))
        .thenThrow(readFailure);
    when(stringRedisTemplate.opsForValue()).thenReturn(values);

    assertThatThrownBy(
            () -> cacheService.getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY))
        .isSameAs(readFailure);
    assertThatThrownBy(
            () -> cacheService.getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY))
        .isSameAs(beginFailure);
    verify(orderService).getSalesStatisticsChart(null, FROM, TO, SalesGrouping.DAY);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    var sequence = inOrder(replicaHealth, manager);
    sequence.verify(replicaHealth).tryBeginRead();
    sequence.verify(manager).getTransaction(transaction);
    sequence.verify(manager).rollback(status);
    sequence.verify(replicaHealth).invalidate();
    sequence.verify(replicaHealth).finishRead();
    sequence.verify(replicaHealth).tryBeginRead();
    sequence.verify(manager).getTransaction(transaction);
    sequence.verify(replicaHealth).invalidate();
    sequence.verify(replicaHealth).finishRead();
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
  void ignoresLegacyChartCacheAfterPeriodLimit() {
    var from = LocalDate.parse("2024-01-01");
    var to = LocalDate.parse("2025-01-01");
    String legacyKey = PREFIX + "chart:system:" + from + ":" + to + ":DAY";
    String currentKey = PREFIX + "chart:v2:system:" + from + ":" + to + ":DAY";
    var cached = Map.of(legacyKey, jsonMapper.writeValueAsString(chart));
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenAnswer(call -> cached.get(call.getArgument(0)));
    when(orderService.getSalesStatisticsChart(null, from, to, SalesGrouping.DAY))
        .thenThrow(new BusinessException(ErrorCode.INVALID_DATE_RANGE));

    assertThatThrownBy(
            () -> cacheService.getSalesStatisticsChart(null, from, to, SalesGrouping.DAY))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));
    verify(values).get(currentKey);
    verifyNoMoreInteractions(values);
    verify(stringRedisTemplate, never()).delete(anyString());
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
