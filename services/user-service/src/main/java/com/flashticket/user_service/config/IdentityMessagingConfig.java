package com.flashticket.user_service.config;

import com.flashticket.user_service.messaging.identity.*;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.MongoWriteConcernException;
import java.time.Duration;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.retry.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "user.identity.enabled", matchIfMissing = true)
public class IdentityMessagingConfig {
  @Bean(initMethod = "verifyIdentityIndex")
  IdentityUserRegistrar identityUserRegistrar(MongoTemplate template) {
    return new IdentityUserRegistrar(template);
  }

  @Bean
  IdentityUserCreatedListener identityUserCreatedListener(
      JsonMapper mapper,
      IdentityUserRegistrar registrar,
      @Value("${KEYCLOAK_IDENTITY_REALM_ID}") String realmId) {
    RetryPolicy policy =
        RetryPolicy.builder()
            .maxRetries(2)
            .delay(Duration.ofSeconds(1))
            .multiplier(5)
            .maxDelay(Duration.ofSeconds(5))
            .includes(
                TransientDataAccessException.class,
                DataAccessResourceFailureException.class,
                MongoSocketException.class,
                MongoTimeoutException.class,
                MongoWriteConcernException.class)
            .build();
    return new IdentityUserCreatedListener(mapper, registrar, realmId, new RetryTemplate(policy));
  }

  @Bean
  SimpleRabbitListenerContainerFactory identityRabbitListenerContainerFactory(
      SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connection) {
    var factory = new SimpleRabbitListenerContainerFactory();
    configurer.configure(factory, connection);
    factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
    factory.setPrefetchCount(1);
    factory.setConcurrentConsumers(1);
    factory.setMaxConcurrentConsumers(1);
    factory.setDefaultRequeueRejected(false);
    return factory;
  }
}
