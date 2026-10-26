package com.flashticket.user_service.messaging.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flashticket.user_service.config.UserJsonConfig;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.core.retry.*;
import org.springframework.dao.*;
import tools.jackson.databind.json.JsonMapper;

class IdentityUserCreatedListenerTests {
  IdentityUserRegistrar registrar = mock(IdentityUserRegistrar.class);
  Channel channel = mock(Channel.class);
  JsonMapper mapper;
  RetryTemplate retry;

  @BeforeEach
  void setup() {
    var builder = JsonMapper.builder().findAndAddModules();
    new UserJsonConfig().userJsonMapperCustomizer().customize(builder);
    mapper = builder.build();
    retry =
        new RetryTemplate(
            RetryPolicy.builder()
                .maxRetries(2)
                .delay(Duration.ZERO)
                .includes(TransientDataAccessException.class)
                .build());
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
    return new IdentityUserCreatedListener(mapper, registrar, "realm-id-from-keycloak", retry);
  }

  @Test
  void commitsBeforeAckAndIgnoresSenderTypeHeader() throws Exception {
    listener().onMessage(message(fixture()), channel);
    var order = inOrder(registrar, channel);
    order.verify(registrar).ensureRegistered(any(UserCreatedV1.class));
    order.verify(channel).basicAck(7, false);
    verify(channel, never()).basicReject(anyLong(), anyBoolean());
  }

  @Test
  void poisonMessagesGoStraightToDlq() throws Exception {
    for (String json :
        new String[] {
          fixture().replace("identitySubject", "identitySubjet"),
          fixture().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
          fixture().replace("realm-id-from-keycloak", "foreign"),
          fixture().replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\""),
          fixture().replace("\"displayName\": \"Nguyễn Văn An\"", "\"displayName\": null"),
          fixture()
              .replace("\"eventId\": \"9fe8679b-759a-4529-bc49-e522c59da03b\"", "\"eventId\": 7"),
          fixture().replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"extra\": true"),
          "{"
        }) listener().onMessage(message(json), channel);
    verifyNoInteractions(registrar);
    verify(channel, times(8)).basicReject(7, false);
    verify(channel, never()).basicAck(anyLong(), anyBoolean());
  }

  @Test
  void retriesTransientWriteButNeverAcknowledgesExhaustion() throws Exception {
    doThrow(new TransientDataAccessResourceException("temporary"))
        .when(registrar)
        .ensureRegistered(any());
    listener().onMessage(message(fixture()), channel);
    verify(registrar, times(3)).ensureRegistered(any());
    verify(channel).basicReject(7, false);
    verify(channel, never()).basicAck(anyLong(), anyBoolean());
  }

  @Test
  void writeCanRecoverBeforeAck() throws Exception {
    doThrow(new TransientDataAccessResourceException("temporary"))
        .doNothing()
        .when(registrar)
        .ensureRegistered(any());
    listener().onMessage(message(fixture()), channel);
    verify(registrar, times(2)).ensureRegistered(any());
    verify(channel).basicAck(7, false);
  }

  @Test
  void ackFailureIsNotRetriedOrRejectedWithStaleTag() throws Exception {
    doThrow(new IOException("connection lost")).when(channel).basicAck(7, false);
    assertThatThrownBy(() -> listener().onMessage(message(fixture()), channel))
        .isInstanceOf(IOException.class);
    verify(registrar).ensureRegistered(any());
    verify(channel, never()).basicReject(anyLong(), anyBoolean());
    verify(channel).abort();
  }
}
