package com.flashticket.user_service.messaging.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mongodb.client.*;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

class IdentityUserRegistrarTests {
  @SuppressWarnings("unchecked")
  @Test
  void emitsAtomicInsertOnlyWriteCompatibleWithClosedValidator() {
    var template = mock(MongoTemplate.class);
    MongoCollection<Document> collection = mock(MongoCollection.class);
    when(template.getCollection("tbl_users")).thenReturn(collection);
    when(collection.withWriteConcern(any())).thenReturn(collection);
    when(collection.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
        .thenReturn(UpdateResult.acknowledged(0, 0L, null));
    var event =
        new UserCreatedV1(
            1,
            UUID.randomUUID(),
            UserCreatedV1.TYPE,
            Instant.parse("2026-10-26T12:00:00Z"),
            "realm",
            "subject",
            null);
    new IdentityUserRegistrar(template).ensureRegistered(event);
    var query = ArgumentCaptor.forClass(Bson.class);
    var update = ArgumentCaptor.forClass(Bson.class);
    var options = ArgumentCaptor.forClass(UpdateOptions.class);
    verify(collection).updateOne(query.capture(), update.capture(), options.capture());
    assertThat(query.getValue()).isEqualTo(new Document("identitySubject", "subject"));
    Document changed = (Document) update.getValue();
    assertThat(changed.keySet()).containsExactly("$setOnInsert");
    Document inserted = changed.get("$setOnInsert", Document.class);
    assertThat(inserted.keySet())
        .containsExactlyInAnyOrder("_id", "identitySubject", "createdAt", "updatedAt", "version");
    assertThat(inserted.getInteger("version")).isZero();
    assertThat(UUID.fromString(inserted.getString("_id"))).isNotNull();
    assertThat(options.getValue().isUpsert()).isTrue();
  }
}
