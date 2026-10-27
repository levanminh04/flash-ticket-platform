package com.flashticket.user_service.config;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.user_service.dto.request.OrganizerApplicationRequest;
import com.flashticket.user_service.messaging.UserRabbitErrorHandler;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.FileSystemResource;

class RuntimeConfigurationTests {
  ApplicationContextRunner configuredRunner() {
    return new ApplicationContextRunner()
        .withInitializer(
            context -> {
              try {
                var file =
                    Path.of(System.getProperty("basedir", "."))
                        .resolve(
                            "../../infrastructure/config-server/src/main/resources/config/user-service.yaml");
                for (var source :
                    new YamlPropertySourceLoader().load("user", new FileSystemResource(file)))
                  context.getEnvironment().getPropertySources().addLast(source);
              } catch (java.io.IOException error) {
                throw new IllegalStateException(error);
              }
            })
        .withPropertyValues(
            "RABBITMQ_HOST=broker.example.test",
            "RABBITMQ_PASSWORD=test-only",
            "RABBITMQ_PORT=5672",
            "RABBITMQ_USERNAME=user-test",
            "MONGO_URI=mongodb://127.0.0.1:1/user_db",
            "KEYCLOAK_ISSUER_URI=https://issuer.example.test/realms/user",
            "USER_SERVICE_PORT=8085");
  }

  @Test
  void runtimePropertiesBindWithoutAdminApiCredentials() {
    configuredRunner()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var env = context.getEnvironment();
              assertThat(env.getProperty("server.port", Integer.class)).isEqualTo(8085);
              assertThat(env.getProperty("spring.mongodb.uri"))
                  .isEqualTo("mongodb://127.0.0.1:1/user_db");
              assertThat(env.getProperty("spring.data.mongodb.auto-index-creation", Boolean.class))
                  .isFalse();
              assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.audiences[0]"))
                  .isEqualTo("flash-ticket-api");
              assertThat(
                      env.getProperty(
                          "spring.security.oauth2.client.registration.user-management.client-id"))
                  .isNull();
            });
  }

  @Test
  void rabbitTemplateUsesConfiguredJsonWithoutDeclaringTopology() {
    configuredRunner()
        .withConfiguration(
            AutoConfigurations.of(JacksonAutoConfiguration.class, RabbitAutoConfiguration.class))
        .withUserConfiguration(UserJsonConfig.class, RabbitMqConfig.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(MessageConverter.class);
              var converter = context.getBean(MessageConverter.class);
              var template = context.getBean(RabbitTemplate.class);
              assertThat(template.getMessageConverter()).isSameAs(converter);
              assertThat(template.getExchange()).isEmpty();
              assertThat(context.getBeansOfType(Queue.class)).isEmpty();
              assertThat(context.getBeansOfType(Exchange.class)).isEmpty();
              assertThat(context.getBeansOfType(Binding.class)).isEmpty();
              assertThat(context.getBeansOfType(RabbitAdmin.class)).isEmpty();
              var endpoint = new SimpleRabbitListenerEndpoint();
              endpoint.setId("test-only");
              endpoint.setQueueNames("user.identity.q");
              endpoint.setAutoStartup(false);
              endpoint.setMessageListener(message -> {});
              var container =
                  context
                      .getBean(SimpleRabbitListenerContainerFactory.class)
                      .createListenerContainer(endpoint);
              try {
                assertThat(container.getErrorHandler()).isInstanceOf(UserRabbitErrorHandler.class);
                assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
              } finally {
                container.destroy();
              }
              var identityFactory =
                  new IdentityMessagingConfig()
                      .identityRabbitListenerContainerFactory(
                          context.getBean(SimpleRabbitListenerContainerFactoryConfigurer.class),
                          context.getBean(CachingConnectionFactory.class));
              var identityContainer = identityFactory.createListenerContainer(endpoint);
              try {
                assertThat(identityContainer.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.AUTO);
              } finally {
                identityContainer.destroy();
              }
              var props = context.getBean(RabbitProperties.class);
              assertThat(props.getListener().getSimple().getPrefetch()).isEqualTo(20);
              assertThat(
                      context
                          .getBean(CachingConnectionFactory.class)
                          .getRabbitConnectionFactory()
                          .getConnectionTimeout())
                  .isEqualTo(2000);
              var request = new OrganizerApplicationRequest("Tổ chức", "Mô tả");
              var message = converter.toMessage(request, new MessageProperties());
              message
                  .getMessageProperties()
                  .setInferredArgumentType(OrganizerApplicationRequest.class);
              assertThat(message.getMessageProperties().getContentType())
                  .isEqualTo("application/json");
              assertThat(converter.fromMessage(message)).isEqualTo(request);
            });
  }
}
