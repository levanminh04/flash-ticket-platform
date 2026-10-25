package com.flashticket.booking_service.client.config;

import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.security.OAuth2AccessTokenInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/** Cấu hình Feign nội bộ; chỉ bật qua thuộc tính configuration của @FeignClient. */
public class InternalFeignConfig {
  /** Chuyển correlation UUID hợp lệ trong MDC, thay giá trị caller tự gán. */
  @Bean
  public RequestInterceptor internalCorrelationInterceptor() {
    return template -> {
      template.removeHeader("X-Correlation-Id");
      String correlation = MDC.get("correlationId");
      if (correlation != null && correlation.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
        template.header("X-Correlation-Id", correlation);
    };
  }

  /** Tự lấy và gắn Bearer token client_credentials bằng danh tính của Booking. */
  @Bean
  public OAuth2AccessTokenInterceptor internalOAuth2AccessTokenInterceptor(
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientService authorizedClients,
      @Value("${spring.cloud.openfeign.client.config.default.connectTimeout}")
          int connectTimeoutMillis,
      @Value("${spring.cloud.openfeign.client.config.default.readTimeout}") int readTimeoutMillis) {
    var delegate =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
    delegate.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials(
                builder ->
                    builder.accessTokenResponseClient(
                        tokenResponseClient(connectTimeoutMillis, readTimeoutMillis)))
            .build());

    // Cache token theo Booking, không theo user đang gửi request.
    OAuth2AuthorizedClientManager machineManager =
        ignored ->
            delegate.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId("internal")
                    .principal("booking-service")
                    .build());
    return new OAuth2AccessTokenInterceptor("internal", machineManager);
  }

  /** Tạo HTTP client lấy token với timeout hữu hạn và bộ xử lý lỗi OAuth2. */
  private static RestClientClientCredentialsTokenResponseClient tokenResponseClient(
      int connectTimeoutMillis, int readTimeoutMillis) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeoutMillis);
    factory.setReadTimeout(readTimeoutMillis);

    var client = new RestClientClientCredentialsTokenResponseClient();
    client.setRestClient(
        RestClient.builder()
            .requestFactory(factory)
            .configureMessageConverters(
                converters -> {
                  converters.addCustomConverter(new FormHttpMessageConverter());
                  converters.addCustomConverter(
                      new OAuth2AccessTokenResponseHttpMessageConverter());
                })
            .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
            .build());
    return client;
  }
}
