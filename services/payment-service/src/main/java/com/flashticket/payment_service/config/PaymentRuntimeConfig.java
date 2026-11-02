package com.flashticket.payment_service.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.web.client.RequestAttributePrincipalResolver;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class PaymentRuntimeConfig {
  @Value("${payment.booking.base-url:http://localhost:8082}")
  private String bookingBaseUrl;

  @Value("${payment.booking.connect-timeout:PT3S}")
  private Duration connectTimeout;

  @Value("${payment.booking.read-timeout:PT5S}")
  private Duration readTimeout;

  @Bean
  public TransactionTemplate paymentTransactionTemplate(
      PlatformTransactionManager transactionManager) {
    var template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template;
  }

  @Bean
  public OAuth2AuthorizedClientManager paymentAuthorizedClientManager(
      ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
    var manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
    manager.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
    return manager;
  }

  @Bean
  public RestClient bookingRestClient(
      RestClient.Builder builder, OAuth2AuthorizedClientManager paymentAuthorizedClientManager) {
    var interceptor = new OAuth2ClientHttpRequestInterceptor(paymentAuthorizedClientManager);
    interceptor.setPrincipalResolver(new RequestAttributePrincipalResolver());
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(connectTimeout).build());
    factory.setReadTimeout(readTimeout);
    return builder
        .baseUrl(bookingBaseUrl)
        .requestFactory(factory)
        .requestInterceptor(interceptor)
        .build();
  }
}
