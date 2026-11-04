package com.flashticket.booking_service.redis.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisService {
  private final StringRedisTemplate stringRedisTemplate;

  public String get(String key) {
    return stringRedisTemplate.opsForValue().get(key);
  }

  public void set(String key, String value, Duration ttl) {
    stringRedisTemplate.opsForValue().set(key, value, ttl);
  }

  public void set(String key, String value) {
    stringRedisTemplate.opsForValue().set(key, value);
  }

  public boolean setIfAbsent(String key, String value) {
    return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value));
  }

  public void clear(String key) {
    stringRedisTemplate.delete(key);
  }
}
