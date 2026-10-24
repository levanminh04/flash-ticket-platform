package com.flashticket.booking_service.client.config;

import com.flashticket.booking_service.observability.BookingContext;
import feign.RequestInterceptor;
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

/** Opt in through @FeignClient(configuration = InternalFeignConfig.class), never component scan. */
public class InternalFeignConfig {
  @Bean
  public RequestInterceptor internalCorrelationInterceptor() {
    return template -> {
      template.removeHeader("X-Correlation-Id");
      var correlation = BookingContext.correlationId();
      if (correlation != null) template.header("X-Correlation-Id", correlation.toString());
    };
  }

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

    OAuth2AuthorizedClientManager machineManager =
        ignored ->
            delegate.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId("internal")
                    .principal("booking-service")
                    .build());
    return new OAuth2AccessTokenInterceptor("internal", machineManager);
  }

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
