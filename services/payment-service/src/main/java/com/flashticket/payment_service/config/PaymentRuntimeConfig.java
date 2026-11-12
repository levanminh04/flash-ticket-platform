package com.flashticket.payment_service.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
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

  @Value("${payment.booking.discovery-enabled:false}")
  private boolean bookingDiscoveryEnabled;

  @Value("${payment.booking.connect-timeout:PT3S}")
  private Duration connectTimeout;

  @Value("${payment.booking.read-timeout:PT5S}")
  private Duration readTimeout;

  @Value("${vnpay.api-url}")
  private String vnpayApiUrl;

  @Value("${payment.vnpay.connect-timeout:PT3S}")
  private Duration vnpayConnectTimeout;

  @Value("${payment.vnpay.read-timeout:PT10S}")
  private Duration vnpayReadTimeout;

  @Bean
  public RestClient vnpayRestClient(RestClient.Builder builder) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(vnpayConnectTimeout).build());
    factory.setReadTimeout(vnpayReadTimeout);
    return builder.clone().baseUrl(vnpayApiUrl).requestFactory(factory).build();
  }

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
      RestClient.Builder builder,
      OAuth2AuthorizedClientManager paymentAuthorizedClientManager,
      LoadBalancerClient loadBalancerClient) {
    var interceptor = new OAuth2ClientHttpRequestInterceptor(paymentAuthorizedClientManager);
    interceptor.setPrincipalResolver(new RequestAttributePrincipalResolver());
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(connectTimeout).build());
    factory.setReadTimeout(readTimeout);
    // Clone để interceptor Booking không lan sang client VNPay dùng cùng builder.
    var bookingBuilder =
        builder
            .clone()
            .baseUrl(bookingDiscoveryEnabled ? "http://booking-service" : bookingBaseUrl)
            .requestFactory(factory);
    if (bookingDiscoveryEnabled) {
      // Host logic được resolve qua Eureka, không gửi tới Kubernetes Service DNS.
      bookingBuilder.requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient));
    }
    return bookingBuilder.requestInterceptor(interceptor).build();
  }
}
