package com.flashticket.user_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.flashticket.user_service.persistence.UserStore;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class UserStoreWriteConcernTests {
  @Test
  void writesRequestJournalAcknowledgementBeforeExternalSideEffectsCanStart() {
    var server = new MongoServer(new MemoryBackend());
    var concern = new AtomicReference<BsonDocument>();
    var settings =
        MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(server.bindAndGetConnectionString()))
            .addCommandListener(
                new CommandListener() {
                  @Override
                  public void commandStarted(CommandStartedEvent event) {
                    if (event.getCommandName().equals("insert"))
                      concern.set(event.getCommand().getDocument("writeConcern"));
                  }
                })
            .build();
    try (var client = MongoClients.create(settings)) {
      new UserStore(new MongoTemplate(client, "journal_test"), Clock.systemUTC(), "tbl_")
          .apply("buyer", "Organization", "Description");
      assertThat(concern.get()).isNotNull();
      assertThat(concern.get().getBoolean("j").getValue()).isTrue();
    } finally {
      server.shutdownNow();
    }
  }
}
