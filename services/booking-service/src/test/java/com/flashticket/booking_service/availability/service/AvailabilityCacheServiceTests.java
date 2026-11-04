package com.flashticket.booking_service.availability.service;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flashticket.booking_service.availability.event.AvailabilityChangedEvent;
import com.flashticket.booking_service.dto.response.Availability;
import com.flashticket.booking_service.enums.SeatState;
import com.flashticket.booking_service.enums.SectorType;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.redis.service.RedisService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AvailabilityCacheServiceTests {
  @Mock AvailabilityService availabilityService;
  @Mock StringRedisTemplate stringRedisTemplate;
  @Mock ValueOperations<String, String> values;
  JsonMapper jsonMapper;
  RedisService redisService;
  AvailabilityCacheService cacheService;
  private static final String PREFIX = "test:booking:availability:v2:";
  private static final Duration TTL = Duration.ofMinutes(15);

  @BeforeEach
  void setUp() {
    jsonMapper = jsonMapper();
    redisService = new RedisService(stringRedisTemplate);
    cacheService = new AvailabilityCacheService(availabilityService, redisService, jsonMapper);
    ReflectionTestUtils.setField(cacheService, "cacheTtl", TTL);
    ReflectionTestUtils.setField(cacheService, "keyPrefix", PREFIX);
  }

  @Test
  void returnsCachedAvailabilityWithoutLoadingDatabase() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    var expected =
        new Availability(
            EVENT,
            "3",
            NOW,
            List.of(new Availability.TicketTypeAvailability(TICKET, "0")),
            List.of(new Availability.SectorAvailability(SECTOR, SectorType.SEATED, "0")),
            List.of(new Availability.SeatAvailability(SEAT, SeatState.HELD)));
    when(values.get(PREFIX + EVENT + ":generation")).thenReturn("G1");
    when(values.get(PREFIX + EVENT + ":data:G1"))
        .thenReturn(jsonMapper.writeValueAsString(expected));

    var actual = cacheService.getAvailability(EVENT);

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    verifyNoInteractions(availabilityService);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    verify(values, never()).set(anyString(), anyString());
    verify(stringRedisTemplate, never()).delete(anyString());
  }

  @Test
  void loadsAndCachesAvailabilityOnMiss() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    for (UUID eventId : List.of(EVENT, UUID.randomUUID())) {
      var expected =
          new Availability(
              eventId,
              "2",
              Instant.now(),
              List.of(new Availability.TicketTypeAvailability(TICKET, "8")),
              List.of(),
              List.of());
      when(values.get(PREFIX + eventId + ":generation")).thenReturn("G1");
      when(availabilityService.getAvailability(eventId)).thenReturn(expected);

      assertThat(cacheService.getAvailability(eventId)).isSameAs(expected);

      var json = ArgumentCaptor.forClass(String.class);
      verify(values).set(eq(PREFIX + eventId + ":data:G1"), json.capture(), eq(TTL));
      assertThat(jsonMapper.readValue(json.getValue(), Availability.class))
          .usingRecursiveComparison()
          .isEqualTo(expected);
      verify(availabilityService).getAvailability(eventId);
    }
  }

  @Test
  void usesConfiguredTtlWithoutRefreshingOnHit() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    var expected = new Availability(EVENT, "1", NOW, List.of(), List.of(), List.of());
    var json = jsonMapper.writeValueAsString(expected);
    when(values.get(PREFIX + EVENT + ":generation")).thenReturn("G1");
    when(values.get(PREFIX + EVENT + ":data:G1")).thenReturn(null, json, json);
    when(availabilityService.getAvailability(EVENT)).thenReturn(expected);

    cacheService.getAvailability(EVENT);
    assertThat(cacheService.getAvailability(EVENT).getAsOf()).isEqualTo(NOW);
    assertThat(cacheService.getAvailability(EVENT).getAsOf()).isEqualTo(NOW);

    verify(values).set(PREFIX + EVENT + ":data:G1", json, TTL);
    verify(availabilityService).getAvailability(EVENT);
    verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
    verify(values, never()).getAndExpire(anyString(), any(Duration.class));
  }

  @Test
  void fallsBackToDatabaseWhenRedisReadFails() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(values.get(PREFIX + EVENT + ":generation"))
        .thenThrow(new RedisConnectionFailureException("test-only"));
    var expected = new Availability(EVENT, "1", NOW, List.of(), List.of(), List.of());
    when(availabilityService.getAvailability(EVENT)).thenReturn(expected);

    assertThat(cacheService.getAvailability(EVENT)).isSameAs(expected);

    verify(availabilityService).getAvailability(EVENT);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    verify(values, never()).setIfAbsent(anyString(), anyString());
  }

  @Test
  void returnsDatabaseResponseWhenCacheWriteFails() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(values.get(PREFIX + EVENT + ":generation")).thenReturn("G1");
    var expected = new Availability(EVENT, "1", NOW, List.of(), List.of(), List.of());
    when(availabilityService.getAvailability(EVENT)).thenReturn(expected);
    doThrow(new RedisConnectionFailureException("test-only"))
        .when(values)
        .set(eq(PREFIX + EVENT + ":data:G1"), anyString(), eq(TTL));

    assertThat(cacheService.getAvailability(EVENT)).isSameAs(expected);

    var failingMapper = spy(jsonMapper);
    var serializationFailure =
        catchThrowableOfType(
            () -> jsonMapper.readValue("{", Availability.class), JacksonException.class);
    doThrow(serializationFailure).when(failingMapper).writeValueAsString(expected);
    cacheService = new AvailabilityCacheService(availabilityService, redisService, failingMapper);
    ReflectionTestUtils.setField(cacheService, "cacheTtl", TTL);
    ReflectionTestUtils.setField(cacheService, "keyPrefix", PREFIX);
    clearInvocations(values);

    assertThat(cacheService.getAvailability(EVENT)).isSameAs(expected);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void reloadsAvailabilityWhenCachedJsonIsInvalid() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(values.get(PREFIX + EVENT + ":generation")).thenReturn("G1");
    var expected = new Availability(EVENT, "1", NOW, List.of(), List.of(), List.of());
    when(availabilityService.getAvailability(EVENT)).thenReturn(expected);
    for (String invalidJson : List.of("{", "null")) {
      when(values.get(PREFIX + EVENT + ":data:G1")).thenReturn(invalidJson);
      clearInvocations(values, stringRedisTemplate);

      assertThat(cacheService.getAvailability(EVENT)).isSameAs(expected);

      verify(stringRedisTemplate).delete(PREFIX + EVENT + ":data:G1");
      verify(values).set(PREFIX + EVENT + ":data:G1", jsonMapper.writeValueAsString(expected), TTL);
      verify(stringRedisTemplate, never()).delete(PREFIX + EVENT + ":generation");
    }
    when(stringRedisTemplate.delete(PREFIX + EVENT + ":data:G1"))
        .thenThrow(new RedisConnectionFailureException("test-only"));
    clearInvocations(values);

    assertThat(cacheService.getAvailability(EVENT)).isSameAs(expected);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void propagatesSourceErrorsWithoutCachingThem() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    when(values.get(PREFIX + EVENT + ":generation")).thenReturn("G1");
    for (RuntimeException failure :
        List.of(
            new BusinessException(ErrorCode.CONFIG_NOT_READY),
            new BusinessException(ErrorCode.EVENT_NOT_ON_SALE),
            new IllegalStateException("test-only source error"))) {
      doThrow(failure).when(availabilityService).getAvailability(EVENT);

      assertThatThrownBy(() -> cacheService.getAvailability(EVENT)).isSameAs(failure);
    }
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void bypassesRedisWhenCacheTtlIsNotPositive() {
    var expected = new Availability(EVENT, "1", NOW, List.of(), List.of(), List.of());
    when(availabilityService.getAvailability(EVENT)).thenReturn(expected);
    for (Duration ttl : List.of(Duration.ZERO, Duration.ofSeconds(-1))) {
      ReflectionTestUtils.setField(cacheService, "cacheTtl", ttl);
      assertThat(cacheService.getAvailability(EVENT)).isSameAs(expected);
    }
    verifyNoInteractions(stringRedisTemplate, values);
  }

  @Test
  void initializesGenerationWithoutReusingOldDataKeys() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    var expected = new Availability(EVENT, "1", NOW, List.of(), List.of(), List.of());
    when(availabilityService.getAvailability(EVENT)).thenReturn(expected);
    // Listener/request khác thắng SET NX; phải dùng generation thực tế G2, không dùng UUID tự tạo.
    when(values.get(PREFIX + EVENT + ":generation")).thenReturn(null, "G2", null, null);
    when(values.setIfAbsent(eq(PREFIX + EVENT + ":generation"), anyString())).thenReturn(false);

    assertThat(cacheService.getAvailability(EVENT)).isSameAs(expected);
    verify(values).set(PREFIX + EVENT + ":data:G2", jsonMapper.writeValueAsString(expected), TTL);
    var generations = ArgumentCaptor.forClass(String.class);
    verify(values).setIfAbsent(eq(PREFIX + EVENT + ":generation"), generations.capture());
    assertThatCode(() -> UUID.fromString(generations.getValue())).doesNotThrowAnyException();

    clearInvocations(values);
    assertThat(cacheService.getAvailability(EVENT)).isSameAs(expected);
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    verify(values).setIfAbsent(eq(PREFIX + EVENT + ":generation"), generations.capture());
    assertThat(generations.getAllValues()).doesNotHaveDuplicates();
  }

  @Test
  void staleReaderCannotPopulateTheNewGeneration() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    var redis = new ConcurrentHashMap<String, String>();
    String generationKey = PREFIX + EVENT + ":generation";
    redis.put(generationKey, "G1");
    when(values.get(anyString())).thenAnswer(invocation -> redis.get(invocation.getArgument(0)));
    doAnswer(
            invocation -> {
              redis.put(invocation.getArgument(0), invocation.getArgument(1));
              return null;
            })
        .when(values)
        .set(anyString(), anyString());
    doAnswer(
            invocation -> {
              redis.put(invocation.getArgument(0), invocation.getArgument(1));
              return null;
            })
        .when(values)
        .set(anyString(), anyString(), eq(TTL));
    var readerAtDatabase = new CountDownLatch(1);
    var writerCommitted = new CountDownLatch(1);
    var previous =
        new Availability(
            EVENT,
            "1",
            NOW,
            List.of(),
            List.of(),
            List.of(new Availability.SeatAvailability(SEAT, SeatState.AVAILABLE)));
    var current =
        new Availability(
            EVENT,
            "1",
            NOW.plusSeconds(1),
            List.of(),
            List.of(),
            List.of(new Availability.SeatAvailability(SEAT, SeatState.HELD)));
    when(availabilityService.getAvailability(EVENT))
        .thenAnswer(
            invocation -> {
              readerAtDatabase.countDown();
              assertThat(writerCommitted.await(10, TimeUnit.SECONDS)).isTrue();
              return previous;
            });
    var executor = Executors.newSingleThreadExecutor();
    try {
      var previousRead = executor.submit(() -> cacheService.getAvailability(EVENT));
      assertThat(readerAtDatabase.await(10, TimeUnit.SECONDS)).isTrue();
      cacheService.invalidateAvailabilityCache(new AvailabilityChangedEvent(EVENT));
      String generationAfterCommit = redis.get(generationKey);
      assertThat(generationAfterCommit).isNotEqualTo("G1");
      writerCommitted.countDown();
      assertThat(previousRead.get(10, TimeUnit.SECONDS)).isSameAs(previous);
      assertThat(redis).containsKey(PREFIX + EVENT + ":data:G1");
      assertThat(redis).doesNotContainKey(PREFIX + EVENT + ":data:" + generationAfterCommit);

      when(availabilityService.getAvailability(EVENT)).thenReturn(current);
      assertThat(cacheService.getAvailability(EVENT)).isSameAs(current);
      assertThat(cacheService.getAvailability(EVENT)).usingRecursiveComparison().isEqualTo(current);
      verify(availabilityService, times(2)).getAvailability(EVENT);
    } catch (Exception exception) {
      throw new AssertionError(exception);
    } finally {
      writerCommitted.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void invalidatesOnlyTheAffectedEventAndHandlesRedisFailure() {
    when(stringRedisTemplate.opsForValue()).thenReturn(values);
    cacheService.invalidateAvailabilityCache(new AvailabilityChangedEvent(EVENT));
    cacheService.invalidateAvailabilityCache(new AvailabilityChangedEvent(EVENT));
    var generations = ArgumentCaptor.forClass(String.class);
    verify(values, times(2)).set(eq(PREFIX + EVENT + ":generation"), generations.capture());
    assertThat(generations.getAllValues()).doesNotHaveDuplicates();
    for (String generation : generations.getAllValues())
      assertThatCode(() -> UUID.fromString(generation)).doesNotThrowAnyException();
    verifyNoInteractions(availabilityService);
    verify(stringRedisTemplate, never()).delete(anyString());
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    doThrow(new RedisConnectionFailureException("test-only"))
        .when(values)
        .set(eq(PREFIX + EVENT + ":generation"), anyString());
    assertThatCode(
            () -> cacheService.invalidateAvailabilityCache(new AvailabilityChangedEvent(EVENT)))
        .doesNotThrowAnyException();
    verify(values, times(3)).set(eq(PREFIX + EVENT + ":generation"), anyString());
    verifyNoMoreInteractions(values);
  }
}
