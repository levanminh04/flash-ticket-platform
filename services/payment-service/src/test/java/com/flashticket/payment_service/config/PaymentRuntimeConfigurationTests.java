package com.flashticket.payment_service.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.flashticket.payment_service.PaymentServiceApplication;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.payment_service.finance.repository.*;
import com.flashticket.payment_service.payment.repository.*;
import com.flashticket.payment_service.refund.repository.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import tools.jackson.databind.json.JsonMapper;

class PaymentRuntimeConfigurationTests {
  @Test
  void loadsPaymentRuntimeProperties() throws Exception {
    var path =
        Path.of(System.getProperty("basedir", "."))
            .resolve(
                "../../infrastructure/config-server/src/main/resources/config/payment-service.yaml");
    var sources = new YamlPropertySourceLoader().load("payment", new FileSystemResource(path));
    var runner = new ApplicationContextRunner();
    for (var source : sources)
      runner =
          runner.withInitializer(
              context -> context.getEnvironment().getPropertySources().addLast(source));
    runner
        .withPropertyValues("KEYCLOAK_ISSUER_URI=https://issuer.example.test")
        .run(
            context -> {
              var env = context.getEnvironment();
              assertThat(env.getProperty("server.port", Integer.class)).isEqualTo(8083);
              assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
              assertThat(env.getProperty("spring.jpa.properties.hibernate.default_schema"))
                  .isEqualTo("payment_schema");
              assertThat(env.getProperty("spring.jpa.properties.hibernate.type.json_format_mapper"))
                  .isEqualTo("jackson3");
              assertThat(env.getProperty("spring.datasource.hikari.connection-init-sql"))
                  .isEqualTo("SET ROLE payment_owner");
              assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                  .isEqualTo("https://issuer.example.test");
              assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"))
                  .isNull();
              assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.audiences"))
                  .isNull();
            });
  }

  @Test
  void usesBootJsonMapperForAmqpConverter() throws Exception {
    var path =
        Path.of(System.getProperty("basedir", "."))
            .resolve(
                "../../infrastructure/config-server/src/main/resources/config/payment-service.yaml");
    var sources = new YamlPropertySourceLoader().load("payment", new FileSystemResource(path));
    var runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    JacksonAutoConfiguration.class, RabbitAutoConfiguration.class))
            .withUserConfiguration(PaymentJsonConfig.class, RabbitMqConfig.class);
    for (var source : sources)
      runner =
          runner.withInitializer(
              context -> context.getEnvironment().getPropertySources().addLast(source));
    runner
        .withPropertyValues("RABBITMQ_HOST=broker.example.test", "RABBITMQ_PASSWORD=test-only")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var converter = context.getBean(MessageConverter.class);
              var template = context.getBean(RabbitTemplate.class);
              assertThat(template.getMessageConverter()).isSameAs(converter);
              assertThat(context.getBeansOfType(Queue.class)).isEmpty();
              assertThat(context.getBeansOfType(Exchange.class)).isEmpty();
              assertThat(context.getBeansOfType(Binding.class)).isEmpty();
              var mapper = context.getBean(JsonMapper.class);
              var snapshot =
                  mapper.readValue(
                      Path.of("src/test/resources/fixtures/payment/purchase-quantity.json")
                          .toFile(),
                      PurchaseSnapshot.class);
              var message = converter.toMessage(snapshot, new MessageProperties());
              message.getMessageProperties().setInferredArgumentType(PurchaseSnapshot.class);
              tools.jackson.databind.JsonNode roundtrip =
                  mapper.valueToTree(converter.fromMessage(message));
              tools.jackson.databind.JsonNode expected = mapper.valueToTree(snapshot);
              assertThat(roundtrip).isEqualTo(expected);
              assertThat(template.getExchange()).isEqualTo("business.ex");
              assertThat(template.isMandatoryFor(message)).isTrue();
              var connection = context.getBean(CachingConnectionFactory.class);
              assertThat(connection.isPublisherConfirms()).isTrue();
              assertThat(connection.isPublisherReturns()).isTrue();
              var listener = context.getBean(RabbitProperties.class).getListener().getSimple();
              assertThat(listener.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
              assertThat(listener.getPrefetch()).isEqualTo(20);
            });
  }

  @Test
  void hasNoBusinessListenerOrWorkerBeans() {
    new WebApplicationContextRunner()
        .withUserConfiguration(PaymentServiceApplication.class)
        .withPropertyValues(
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration")
        .withBean(
            OrderPaymentSnapshotRepository.class, () -> mock(OrderPaymentSnapshotRepository.class))
        .withBean(PaymentAttemptRepository.class, () -> mock(PaymentAttemptRepository.class))
        .withBean(ChargeRepository.class, () -> mock(ChargeRepository.class))
        .withBean(
            PaymentConfirmationRepository.class, () -> mock(PaymentConfirmationRepository.class))
        .withBean(RefundRepository.class, () -> mock(RefundRepository.class))
        .withBean(RefundAttemptRepository.class, () -> mock(RefundAttemptRepository.class))
        .withBean(EventRefundRunRepository.class, () -> mock(EventRefundRunRepository.class))
        .withBean(EventRefundItemRepository.class, () -> mock(EventRefundItemRepository.class))
        .withBean(EventPayoutRepository.class, () -> mock(EventPayoutRepository.class))
        .withBean(
            EventFinanceSnapshotRepository.class, () -> mock(EventFinanceSnapshotRepository.class))
        .withBean(JwtDecoder.class, () -> mock(JwtDecoder.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              for (Object bean : context.getBeansOfType(Object.class).values()) {
                if (!bean.getClass().getPackageName().startsWith("com.flashticket.payment_service"))
                  continue;
                for (var method : bean.getClass().getDeclaredMethods()) {
                  assertThat(method.isAnnotationPresent(RabbitListener.class)).isFalse();
                  assertThat(method.isAnnotationPresent(Scheduled.class)).isFalse();
                }
              }
            });
  }
}
