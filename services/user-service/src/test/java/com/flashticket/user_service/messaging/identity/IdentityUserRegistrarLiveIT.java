package com.flashticket.user_service.messaging.identity;

import static org.assertj.core.api.Assertions.*;

import com.mongodb.ConnectionString;
import com.mongodb.client.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;

/** Opt-in only: creates and deletes its own uniquely scoped test document on the configured DB. */
@EnabledIfEnvironmentVariable(named = "IDENTITY_LIVE_TEST", matches = "true")
class IdentityUserRegistrarLiveIT {
  @Test
  void actualValidatorAcceptsInsertAndConcurrentRedeliveriesDoNotOverwrite() throws Exception {
    String uri = System.getenv("MONGO_URI");
    var connection = new ConnectionString(uri);
    assertThat(connection.getDatabase()).isEqualTo("user_db");
    String subject = "identity-it-" + UUID.randomUUID();
    try (MongoClient client = MongoClients.create(uri)) {
      var template = new MongoTemplate(client, "user_db");
      var registrar = new IdentityUserRegistrar(template);
      registrar.verifyIdentityIndex();
      var collection = template.getCollection("tbl_users");
      var filter = new Document("identitySubject", subject);
      var event =
          new UserCreatedV1(
              1,
              UUID.randomUUID(),
              UserCreatedV1.TYPE,
              Instant.parse("2026-10-26T12:00:00Z"),
              "test-realm",
              subject,
              null);
      try {
        try (var pool = Executors.newFixedThreadPool(8)) {
          List<Callable<Void>> jobs = new ArrayList<>();
          for (int i = 0; i < 8; i++)
            jobs.add(
                () -> {
                  registrar.ensureRegistered(event);
                  return null;
                });
          for (var future : pool.invokeAll(jobs)) future.get(15, TimeUnit.SECONDS);
        }
        assertThat(collection.countDocuments(filter)).isEqualTo(1);
        Document original = collection.find(filter).first();
        assertThat(original).isNotNull();
        assertThat(original.containsKey("displayName")).isFalse();
        assertThat(original.containsKey("_class")).isFalse();
        assertThat(original.getInteger("version")).isZero();
        collection.updateOne(
            filter,
            new Document("$set", new Document("displayName", "Edited").append("version", 7)));
        registrar.ensureRegistered(event);
        Document redelivered = collection.find(filter).first();
        assertThat(redelivered.getString("_id")).isEqualTo(original.getString("_id"));
        assertThat(redelivered.getString("displayName")).isEqualTo("Edited");
        assertThat(redelivered.getInteger("version")).isEqualTo(7);
        assertThat(redelivered.getDate("createdAt")).isEqualTo(original.getDate("createdAt"));
      } finally {
        collection.deleteOne(filter);
      }
    }
  }
}
