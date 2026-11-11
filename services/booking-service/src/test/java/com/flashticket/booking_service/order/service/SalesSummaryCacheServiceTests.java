package com.flashticket.booking_service.order.service;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flashticket.booking_service.dto.response.SalesSummaryView;
import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.OrderState;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.redis.service.RedisService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
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
class SalesSummaryCacheServiceTests {
  private static final String PREFIX = "test:booking:sales-summary:v1:";
  private static final Duration TTL = Duration.ofSeconds(30);
  private static final LocalDate FROM = LocalDate.parse("2026-11-01");
  private static final LocalDate TO = LocalDate.parse("2026-11-09");

  @Mock OrderService orderService;
  @Mock StringRedisTemplate stringRedisTemplate;
  @Mock ValueOperations<String, String> values;
  JsonMapper jsonMapper;
  SalesSummaryCacheService cacheService;

  final SalesSummaryView summary =
      new SalesSummaryView(
          EVENT,
          "Concert A",
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
          List.of(new SalesSummaryView.StateSales(OrderState.COMPLETED, 1, 2, "200", "20", "180")),
          List.of(new SalesSummaryView.TicketTypeSales(TICKET, "VIP", 2, "200")));

  @BeforeEach
  void setUp() {
    jsonMapper = jsonMapper();
    cacheService =
        new SalesSummaryCacheService(
            orderService, new RedisService(stringRedisTemplate), jsonMapper);
    ReflectionTestUtils.setField(cacheService, "cacheTtl", TTL);
    ReflectionTestUtils.setField(cacheService, "keyPrefix", PREFIX);
  }

  @Test
  void returnsCachedSummaryWithoutQueryingDatabase() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(values.get(PREFIX + EVENT + ":organizer:2026-11-01:2026-11-09"))
        .thenReturn(jsonMapper.writeValueAsString(summary));

    var actual = cacheService.getSalesSummary("organizer", EVENT, FROM, TO);

    assertThat(actual).usingRecursiveComparison().isEqualTo(summary);
    verifyNoInteractions(orderService);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void cachesSummaryPerOrganizerAndRange() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(orderService.getSalesSummary("organizer", EVENT, FROM, TO)).thenReturn(summary);
    when(orderService.getSalesSummary("other", EVENT, null, null)).thenReturn(summary);

    assertThat(cacheService.getSalesSummary("organizer", EVENT, FROM, TO)).isSameAs(summary);
    assertThat(cacheService.getSalesSummary("other", EVENT, null, null)).isSameAs(summary);

    var json = ArgumentCaptor.forClass(String.class);
    verify(values)
        .set(eq(PREFIX + EVENT + ":organizer:2026-11-01:2026-11-09"), json.capture(), eq(TTL));
    // Không truyền from/to => key dùng "-" và tách riêng theo organizer.
    verify(values).set(eq(PREFIX + EVENT + ":other:-:-"), anyString(), eq(TTL));
    assertThat(jsonMapper.readValue(json.getValue(), SalesSummaryView.class))
        .usingRecursiveComparison()
        .isEqualTo(summary);
  }

  @Test
  void fallsBackToDatabaseWhenRedisFails() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("test-only"));
    when(orderService.getSalesSummary("organizer", EVENT, FROM, TO)).thenReturn(summary);

    assertThat(cacheService.getSalesSummary("organizer", EVENT, FROM, TO)).isSameAs(summary);

    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void doesNotCacheBusinessErrors() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(orderService.getSalesSummary("other", EVENT, FROM, TO))
        .thenThrow(new BusinessException(ErrorCode.EVENT_NOT_FOUND));

    assertThatThrownBy(() -> cacheService.getSalesSummary("other", EVENT, FROM, TO))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_FOUND));

    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
  }
}
