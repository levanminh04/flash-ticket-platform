package com.flashticket.user_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.flashticket.user_service.identity.IdentityConsumer;
import com.flashticket.user_service.observability.UserTelemetry;
import com.flashticket.user_service.persistence.UserStore;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClients;
import com.rabbitmq.client.Channel;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class IdentityConsumerTests {
  private static final String ENVELOPE =
      """
                    {"messageId":"00000000-0000-4000-8000-000000000001","messageType":"identity.user-created.v1",
                     "schemaVersion":1,"aggregateId":"00000000-0000-4000-8000-000000000002","aggregateVersion":"1",
                     "occurredAt":"2026-10-25T00:00:00Z","correlationId":"00000000-0000-4000-8000-000000000003",
                     "causationId":null,"traceparent":null,"data":{"identitySubject":"buyer-identity","displayName":"Display name"}}
                    """;

  private Message message(String json) {
    var properties = new MessageProperties();
    properties.setDeliveryTag(7);
    properties.setReceivedRoutingKey("identity.user-created.v1");
    return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
  }

  @Test
  void duplicateCreationIsIdempotentAndAckHappensAfterDatabaseWrite() throws Exception {
    var server = new MongoServer(new MemoryBackend());
    try (var client = MongoClients.create(server.bindAndGetConnectionString());
        var validation = Validation.buildDefaultValidatorFactory()) {
      var mongo = new MongoTemplate(client, "identity_test");
      mongo
          .getCollection("tbl_users")
          .createIndex(
              new Document("identitySubject", 1),
              new com.mongodb.client.model.IndexOptions().unique(true));
      var store = new UserStore(mongo, Clock.systemUTC(), "tbl_");
      var consumer =
          new IdentityConsumer(
              JsonMapper.builder()
                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                  .build(),
              validation.getValidator(),
              store,
              new UserTelemetry(new SimpleMeterRegistry(), Clock.systemUTC()),
              65536);
      Channel channel = mock(Channel.class);
      doAnswer(
              invocation -> {
                assertThat(mongo.getCollection("tbl_users").countDocuments()).isEqualTo(1);
                return null;
              })
          .when(channel)
          .basicAck(7, false);
      consumer.receive(message(ENVELOPE), channel);
      consumer.receive(message(ENVELOPE), channel);
      assertThat(mongo.getCollection("tbl_users").find().first())
          .doesNotContainKeys("email", "roles", "_class");
      verify(channel, times(2)).basicAck(7, false);
      verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
      consumer.receive(
          message(ENVELOPE.replace("\"schemaVersion\":1", "\"schemaVersion\":2")), channel);
      verify(channel).basicReject(7, false);
      assertThat(mongo.getCollection("tbl_users").countDocuments()).isEqualTo(1);
    } finally {
      server.shutdownNow();
    }
  }

  @Test
  void storageFailureIsRequeuedWithoutAcknowledgement() throws Exception {
    var unavailable = mock(UserStore.class);
    when(unavailable.ensureUser(anyString(), anyString()))
        .thenThrow(new MongoException("unavailable"));
    try (var validation = Validation.buildDefaultValidatorFactory()) {
      var consumer =
          new IdentityConsumer(
              JsonMapper.builder().build(),
              validation.getValidator(),
              unavailable,
              new UserTelemetry(new SimpleMeterRegistry(), Clock.systemUTC()),
              65536);
      Channel channel = mock(Channel.class);
      consumer.receive(message(ENVELOPE), channel);
      verify(channel).basicNack(7, false, true);
      verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
  }
}
