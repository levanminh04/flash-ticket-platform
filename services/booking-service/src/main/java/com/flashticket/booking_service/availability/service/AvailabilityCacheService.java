package com.flashticket.booking_service.availability.service;

import com.flashticket.booking_service.availability.event.AvailabilityChangedEvent;
import com.flashticket.booking_service.dto.response.Availability;
import com.flashticket.booking_service.redis.service.RedisService;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityCacheService {
  private final AvailabilityService availabilityService;
  private final RedisService redisService;
  private final JsonMapper jsonMapper;

  @Value("${booking.availability-cache.ttl:PT15M}")
  private Duration cacheTtl;

  @Value("${booking.availability-cache.key-prefix:flash-ticket:dev:booking:availability:v2:}")
  private String keyPrefix;

  public Availability getAvailability(UUID eventId) {
    if (cacheTtl.isZero() || cacheTtl.isNegative())
      return availabilityService.getAvailability(eventId);

    String generationKey = keyPrefix + eventId + ":generation";
    String dataKey = null;
    try {
      String generation = redisService.get(generationKey);
      if (generation == null) {
        // Request khác có thể vừa khởi tạo hoặc listener vừa đổi generation.
        redisService.setIfAbsent(generationKey, UUID.randomUUID().toString());
        generation = redisService.get(generationKey);
      }
      if (generation != null) {
        dataKey = keyPrefix + eventId + ":data:" + generation;
        String cachedJson = redisService.get(dataKey);
        if (cachedJson != null) {
          try {
            Availability cachedAvailability = jsonMapper.readValue(cachedJson, Availability.class);
            if (cachedAvailability != null) return cachedAvailability;
          } catch (JacksonException exception) {
            log.warn(
                "Availability cache operation=decode eventId={} errorType={}",
                eventId,
                exception.getClass().getSimpleName());
          }
          // Chỉ xóa JSON lỗi của generation đang đọc; không xóa metadata của event.
          redisService.clear(dataKey);
        }
      }
    } catch (DataAccessException exception) {
      // Redis đọc/xóa lỗi thì dùng DB và tránh chờ thêm một lần ghi Redis.
      dataKey = null;
      log.warn(
          "Availability cache operation=read eventId={} errorType={}",
          eventId,
          exception.getClass().getSimpleName());
    }

    // Gọi qua proxy để transaction DB kết thúc trước khi ghi cache; không catch lỗi DB.
    Availability availability = availabilityService.getAvailability(eventId);
    if (dataKey != null) {
      try {
        // Giữ generation lấy trước khi đọc DB để reader cũ không nạp lại cache mới.
        redisService.set(dataKey, jsonMapper.writeValueAsString(availability), cacheTtl);
      } catch (DataAccessException | JacksonException exception) {
        log.warn(
            "Availability cache operation=write eventId={} errorType={}",
            eventId,
            exception.getClass().getSimpleName());
      }
    }
    return availability;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
  public void invalidateAvailabilityCache(AvailabilityChangedEvent event) {
    try {
      // Inventory đã commit; đổi namespace để GET mới không dùng JSON của reader cũ.
      redisService.set(
          keyPrefix + event.getEventId() + ":generation", UUID.randomUUID().toString());
    } catch (DataAccessException exception) {
      // DB đã commit; lỗi Redis không thể rollback inventory, TTL là dự phòng hiện tại.
      log.warn(
          "Availability cache operation=invalidate eventId={} errorType={}",
          event.getEventId(),
          exception.getClass().getSimpleName());
    }
  }
}
