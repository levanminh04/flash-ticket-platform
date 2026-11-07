package com.flashticket.api_gateway.routing;

import java.net.InetAddress;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/** Route nằm trong application.yaml; class này chỉ cung cấp hai bean mà các route tham chiếu. */
@Configuration(proxyBeanMethods = false)
public class GatewayRouteConfiguration {

  private static final int REPLENISH_RATE = 10;
  private static final int BURST_CAPACITY = 20;
  private static final int REQUESTED_TOKENS = 1;

  @Bean
  RedisRateLimiter redisRateLimiter() {
    return new RedisRateLimiter(REPLENISH_RATE, BURST_CAPACITY, REQUESTED_TOKENS);
  }

  /**
   * Khóa rate limit là địa chỉ kết nối trực tiếp; không tin header forwarding do client gửi. Xử lý
   * an toàn khi không có hoặc không phân giải được địa chỉ.
   */
  @Bean
  KeyResolver remoteAddressKeyResolver() {
    return exchange ->
        Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
            .flatMap(
                address -> {
                  InetAddress resolvedAddress = address.getAddress();
                  return Mono.justOrEmpty(resolvedAddress)
                      .map(InetAddress::getHostAddress)
                      .switchIfEmpty(Mono.justOrEmpty(address.getHostString()));
                })
            .filter(key -> !key.isBlank())
            .defaultIfEmpty("unknown");
  }
}
