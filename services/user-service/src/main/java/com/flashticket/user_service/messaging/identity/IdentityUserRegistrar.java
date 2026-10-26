package com.flashticket.user_service.messaging.identity;

import com.mongodb.MongoWriteException;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

/** An insert-only single-document operation; no cross-document transaction needed. */
public final class IdentityUserRegistrar {
  private final MongoCollection<Document> users;

  public IdentityUserRegistrar(MongoTemplate template) {
    this.users =
        template
            .getCollection("tbl_users")
            .withWriteConcern(WriteConcern.JOURNALED.withW(1).withWTimeout(5, TimeUnit.SECONDS));
  }

  public void verifyIdentityIndex() {
    for (Document index : users.listIndexes()) {
      Document keys = index.get("key", Document.class);
      if (Boolean.TRUE.equals(index.getBoolean("unique"))
          && keys != null
          && keys.size() == 1
          && keys.containsKey("identitySubject")) return;
    }
    throw new IllegalStateException(
        "Missing unique identitySubject index; identity listener cannot start");
  }

  public void ensureRegistered(UserCreatedV1 event) {
    Document filter = new Document("identitySubject", event.identitySubject());
    Document inserted =
        new Document("_id", UUID.randomUUID().toString())
            .append("identitySubject", event.identitySubject())
            .append("createdAt", Date.from(event.occurredAt()))
            .append("updatedAt", Date.from(event.occurredAt()))
            .append("version", 0);
    if (event.displayName() != null) inserted.append("displayName", event.displayName());
    try {
      var result =
          users.updateOne(
              filter, new Document("$setOnInsert", inserted), new UpdateOptions().upsert(true));
      if (!result.wasAcknowledged())
        throw new IllegalStateException("Unacknowledged identity write");
    } catch (MongoWriteException duplicate) {
      if (duplicate.getError().getCode() != 11000) throw duplicate;
      var details = duplicate.getError().getDetails();
      if (details == null
          || !details.containsKey("keyPattern")
          || !details.getDocument("keyPattern").containsKey("identitySubject")) throw duplicate;
      Document existing = users.find(filter).first();
      if (existing == null
          || !(existing.get("createdAt") instanceof Date)
          || !(existing.get("updatedAt") instanceof Date)
          || !(existing.get("version") instanceof Number)
          || existing.getString("_id") == null) throw duplicate;
      UUID.fromString(existing.getString("_id"));
    }
  }
}
