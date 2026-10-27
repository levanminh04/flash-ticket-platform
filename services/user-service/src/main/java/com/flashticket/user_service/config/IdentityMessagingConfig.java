package com.flashticket.user_service.config;

import com.flashticket.user_service.messaging.identity.*;
import com.flashticket.user_service.user.repository.UserRepository;
import com.mongodb.WriteConcern;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.resilience.annotation.EnableResilientMethods;
import tools.jackson.databind.ObjectMapper;

/** Ghép consumer identity với MongoDB, retry DB và listener container riêng của queue identity. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "user.identity.enabled", matchIfMissing = true)
@EnableResilientMethods
public class IdentityMessagingConfig {
  @Bean
  MongoClientSettingsBuilderCustomizer identityMongoTimeouts() {
    // Không để một thao tác mạng treo mãi, khiến listener không thể retry hoặc kết thúc delivery.
    return builder ->
        builder
            .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(2, TimeUnit.SECONDS))
            .applyToSocketSettings(
                socket ->
                    socket.connectTimeout(2, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS));
  }

  @Bean
  IdentityUserRegistrar identityUserRegistrar(MongoTemplate template) {
    // Template riêng để không thay write concern của các chức năng User khác.
    MongoTemplate identityTemplate =
        new MongoTemplate(template.getMongoDatabaseFactory(), template.getConverter());
    identityTemplate.setWriteConcern(
        WriteConcern.JOURNALED.withW(1).withWTimeout(5, TimeUnit.SECONDS));
    UserRepository identityRepository =
        new MongoRepositoryFactory(identityTemplate).getRepository(UserRepository.class);
    return new IdentityUserRegistrar(identityRepository);
  }

  @Bean
  IdentityUserCreatedListener identityUserCreatedListener(
      ObjectMapper objectMapper, IdentityUserRegistrar registrar) {
    return new IdentityUserCreatedListener(objectMapper, registrar);
  }

  @Bean
  SimpleRabbitListenerContainerFactory identityRabbitListenerContainerFactory(
      SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connection) {
    var factory = new SimpleRabbitListenerContainerFactory();
    configurer.configure(factory, connection);
    // Spring ACK sau khi listener xử lý thành công; mỗi consumer giữ tối đa một delivery chưa ACK.
    factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
    factory.setPrefetchCount(1);
    factory.setConcurrentConsumers(1);
    factory.setMaxConcurrentConsumers(1);
    // Tránh message lỗi bị trả về queue chính liên tục; dùng DLQ đã khai trong broker.
    factory.setDefaultRequeueRejected(false);
    return factory;
  }
}
