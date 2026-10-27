package com.flashticket.user_service.messaging.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flashticket.user_service.config.IdentityMessagingConfig;
import com.flashticket.user_service.config.UserJsonConfig;
import com.flashticket.user_service.document.UserDocument;
import com.flashticket.user_service.user.repository.UserRepository;
import java.nio.file.*;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.*;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.resilience.annotation.RetryAnnotationBeanPostProcessor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class IdentityUserCreatedListenerTests {
  IdentityUserRegistrar registrar = mock(IdentityUserRegistrar.class);
  JsonMapper mapper;

  @BeforeEach
  void setup() {
    var builder = JsonMapper.builder().findAndAddModules();
    new UserJsonConfig().userJsonMapperCustomizer().customize(builder);
    mapper = builder.build();
  }

  Message message(String json) {
    var props = new MessageProperties();
    props.setDeliveryTag(7);
    props.setReceivedRoutingKey("identity.user-created.v1");
    props.setHeader("__TypeId__", "untrusted.payload.Class");
    return new Message(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), props);
  }

  String fixture() throws Exception {
    return Files.readString(Path.of("../../docs/contracts/identity/user-created-v1.example.json"));
  }

  IdentityUserCreatedListener listener() {
    IdentityUserCreatedListener listener = new IdentityUserCreatedListener(mapper, registrar);
    ReflectionTestUtils.setField(listener, "realmId", "realm-id-from-keycloak");
    return listener;
  }

  @Test
  void commitsBeforeAckAndIgnoresSenderTypeHeader() throws Exception {
    String json = fixture();
    for (String payload :
        new String[] {
          json,
          json.replace(",\n  \"displayName\": \"Nguyễn Văn An\"", ""),
          json.replace("\"displayName\": \"Nguyễn Văn An\"", "\"displayName\": null"),
          json.replace("2026-10-26T12:00:00Z", "2026-10-26T19:00:00+07:00"),
          json.replace("9fe8679b-759a-4529-bc49-e522c59da03b", "n+hnm3WaRSm8SeUixZ2gOw==")
        }) {
      listener().onMessage(message(payload));
    }
    var events = org.mockito.ArgumentCaptor.forClass(UserCreatedV1.class);
    verify(registrar, times(5)).ensureRegistered(events.capture());
    var accepted = events.getAllValues();
    assertThat(accepted.get(1).displayName()).isNull();
    assertThat(accepted.get(2).displayName()).isNull();
    assertThat(accepted.get(3).occurredAt()).isEqualTo(accepted.getFirst().occurredAt());
    assertThat(accepted.get(4).eventId()).isEqualTo(accepted.getFirst().eventId());
  }

  @Test
  void poisonMessagesGoStraightToDlq() throws Exception {
    for (String json :
        new String[] {
          fixture().replace("identitySubject", "identitySubjet"),
          fixture().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
          fixture().replace("realm-id-from-keycloak", "foreign"),
          fixture().replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\""),
          fixture().replace("user-id-from-keycloak", " "),
          fixture()
              .replace("\"eventId\": \"9fe8679b-759a-4529-bc49-e522c59da03b\"", "\"eventId\": 7"),
          fixture().replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"extra\": true"),
          "{"
        })
      assertThatThrownBy(() -> listener().onMessage(message(json)))
          .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    verifyNoInteractions(registrar);
  }

  @Test
  void retriesTransientWriteButNeverAcknowledgesExhaustion() throws Exception {
    assertThat(IdentityMessagingConfig.class.isAnnotationPresent(EnableResilientMethods.class))
        .isTrue();
    var repository = mock(UserRepository.class);
    when(repository.findByIdentitySubject(anyString()))
        .thenThrow(new TransientDataAccessResourceException("temporary"));
    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(
          "identityUserRegistrar",
          IdentityUserRegistrar.class,
          () -> new IdentityUserRegistrar(repository));
      context.registerBean(ObjectMapper.class, () -> mapper);
      context.registerBean(
          SimpleRabbitListenerContainerFactoryConfigurer.class,
          () -> mock(SimpleRabbitListenerContainerFactoryConfigurer.class));
      context.registerBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class));
      context
          .getEnvironment()
          .getPropertySources()
          .addFirst(
              new MapPropertySource(
                  "test", Map.of("KEYCLOAK_IDENTITY_REALM_ID", "realm-id-from-keycloak")));
      context.register(IdentityMessagingConfig.class);
      context.refresh();
      var actualRegistrar = context.getBean(IdentityUserRegistrar.class);
      assertThat(AopUtils.isAopProxy(actualRegistrar)).isTrue();
      var listener = context.getBean(IdentityUserCreatedListener.class);
      assertThatThrownBy(() -> listener.onMessage(message(fixture())))
          .isInstanceOf(AmqpRejectAndDontRequeueException.class);
      verify(repository, times(3)).findByIdentitySubject(anyString());

      reset(repository);
      when(repository.findByIdentitySubject(anyString()))
          .thenThrow(new IllegalArgumentException("permanent"));
      assertThatThrownBy(() -> listener.onMessage(message(fixture())))
          .isInstanceOf(AmqpRejectAndDontRequeueException.class);
      verify(repository, times(1)).findByIdentitySubject(anyString());
    }
  }

  @Test
  void writeCanRecoverBeforeAck() throws Exception {
    var repository = mock(UserRepository.class);
    when(repository.findByIdentitySubject(anyString()))
        .thenThrow(new TransientDataAccessResourceException("temporary"))
        .thenReturn(Optional.of(new UserDocument()));
    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(RetryAnnotationBeanPostProcessor.class);
      context.registerBean(
          IdentityUserRegistrar.class, () -> new IdentityUserRegistrar(repository));
      context.refresh();
      var listener =
          new IdentityUserCreatedListener(mapper, context.getBean(IdentityUserRegistrar.class));
      ReflectionTestUtils.setField(listener, "realmId", "realm-id-from-keycloak");
      listener.onMessage(message(fixture()));
      verify(repository, times(2)).findByIdentitySubject(anyString());
    }
  }
}
