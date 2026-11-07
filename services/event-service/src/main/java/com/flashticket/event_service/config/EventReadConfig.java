package com.flashticket.event_service.config;

import com.flashticket.event_service.integration.*;
import com.flashticket.event_service.web.EventProblem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventReadProperties.class)
public class EventReadConfig {
  @Bean
  InternalAccessToken internalAccessToken(
      ObjectProvider<ClientRegistrationRepository> registrations,
      ObjectProvider<OAuth2AuthorizedClientService> clients) {
    return () -> {
      var repository = registrations.getIfAvailable();
      var service = clients.getIfAvailable();
      if (repository == null || service == null)
        throw new EventProblem(503, "INTERNAL_AUTH_UNAVAILABLE", null);
      var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(repository, service);
      manager.setAuthorizedClientProvider(
          OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
      try {
        var client =
            manager.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId("internal")
                    .principal("event-service")
                    .build());
        if (client == null) throw new EventProblem(503, "INTERNAL_AUTH_UNAVAILABLE", null);
        return client.getAccessToken().getTokenValue();
      } catch (org.springframework.security.oauth2.core.OAuth2AuthorizationException
          | IllegalArgumentException exception) {
        throw new EventProblem(503, "INTERNAL_AUTH_UNAVAILABLE", null);
      }
    };
  }
}
