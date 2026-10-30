package com.flashticket.user_service.persistence;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

import com.flashticket.user_service.domain.UserModels.*;
import com.flashticket.user_service.web.UserException;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Uses explicit BSON documents: B12 closed validators reject Spring's synthetic _class field. */
@Component
public final class UserStore {
  private final MongoTemplate mongo;
  private final Clock clock;
  private final String prefix;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentionally shares Spring's thread-safe MongoTemplate; no template accessor is exposed.")
  public UserStore(
      MongoTemplate mongo,
      Clock clock,
      @Value("${user.mongodb.collection-prefix:tbl_}") String prefix) {
    if (!prefix.matches("[A-Za-z0-9_]*"))
      throw new IllegalArgumentException("Invalid collection prefix");
    this.mongo = mongo;
    this.clock = clock;
    this.prefix = prefix;
  }

  private MongoCollection<Document> collection(String name) {
    return mongo.getCollection(prefix + name).withWriteConcern(WriteConcern.JOURNALED);
  }

  private Date now() {
    return Date.from(clock.instant());
  }

  private static String id() {
    return UUID.randomUUID().toString();
  }

  private Document metadata() {
    Date time = now();
    return new Document("_id", id())
        .append("createdAt", time)
        .append("updatedAt", time)
        .append("version", 0L);
  }

  public UserProfile ensureUser(String subject, String displayName) {
    Document initial = metadata().append("identitySubject", subject);
    if (displayName != null && !displayName.isBlank()) initial.append("displayName", displayName);
    try {
      collection("users")
          .updateOne(
              eq("identitySubject", subject),
              new Document("$setOnInsert", initial),
              new UpdateOptions().upsert(true));
    } catch (MongoException ex) {
      if (!duplicate(ex)) throw ex;
    }
    return user(subject);
  }

  public UserProfile user(String subject) {
    Document d = collection("users").find(eq("identitySubject", subject)).first();
    if (d == null) throw new UserException(404, "USER_NOT_FOUND", "User not found");
    return new UserProfile(
        d.getString("_id"), d.getString("identitySubject"), d.getString("displayName"));
  }

  public UserProfile updateDisplayName(String subject, String name) {
    collection("users")
        .updateOne(
            eq("identitySubject", subject),
            combine(set("displayName", name), set("updatedAt", now()), inc("version", 1L)));
    return user(subject);
  }

  public Application apply(String subject, String name, String description) {
    Document d =
        metadata()
            .append("identitySubject", subject)
            .append("organizationName", name)
            .append("shortDescription", description)
            .append("status", "PENDING");
    try {
      collection("organizer_applications").insertOne(d);
    } catch (MongoException ex) {
      if (duplicate(ex))
        throw new UserException(
            409, "APPLICATION_ALREADY_EXISTS", "An application already exists for this account");
      throw ex;
    }
    return application(d);
  }

  public Application application(String applicationId) {
    Document d = collection("organizer_applications").find(eq("_id", applicationId)).first();
    if (d == null) throw new UserException(404, "APPLICATION_NOT_FOUND", "Application not found");
    return application(d);
  }

  public List<Application> applications(
      String subject, ApplicationStatus status, String after, int limit) {
    List<Bson> filters = new ArrayList<>();
    if (subject != null) filters.add(eq("identitySubject", subject));
    if (status != null) filters.add(eq("status", status.name()));
    if (after != null) filters.add(gt("_id", after));
    return collection("organizer_applications")
        .find(filters.isEmpty() ? new Document() : and(filters))
        .sort(Sorts.ascending("_id"))
        .limit(limit)
        .map(UserStore::application)
        .into(new ArrayList<>());
  }

  public Application claim(String applicationId, String admin) {
    Document grant =
        new Document("operationId", id())
            .append("requestedBySubject", admin)
            .append("status", "REQUESTED")
            .append("attemptedAt", now());
    return changed(
        and(eq("_id", applicationId), eq("status", "PENDING"), exists("roleGrant", false)),
        combine(set("roleGrant", grant), set("updatedAt", now()), inc("version", 1L)));
  }

  public Application reject(String applicationId, String admin, String reason) {
    return changed(
        and(eq("_id", applicationId), eq("status", "PENDING"), exists("roleGrant", false)),
        combine(
            set("status", "REJECTED"),
            set("rejectionReason", reason),
            set("decidedBySubject", admin),
            set("decidedAt", now()),
            set("updatedAt", now()),
            inc("version", 1L)));
  }

  public boolean touchGrant(Application a) {
    return collection("organizer_applications")
            .updateOne(
                grantFilter(a),
                combine(
                    set("roleGrant.attemptedAt", now()),
                    set("updatedAt", now()),
                    inc("version", 1L)))
            .getMatchedCount()
        == 1;
  }

  public void grantUnknown(Application a) {
    collection("organizer_applications")
        .updateOne(
            grantFilter(a),
            combine(
                set("roleGrant.status", "UNKNOWN"),
                set("roleGrant.attemptedAt", now()),
                set("updatedAt", now()),
                inc("version", 1L)));
  }

  public Application activate(Application a) {
    return changed(
        grantFilter(a),
        combine(
            set("status", "ACTIVE"),
            set("roleGrant.status", "CONFIRMED"),
            set("roleGrant.confirmedAt", now()),
            set("decidedBySubject", a.roleGrant().requestedBySubject()),
            set("decidedAt", now()),
            set("updatedAt", now()),
            inc("version", 1L)));
  }

  public List<Application> pendingGrants(int limit) {
    return collection("organizer_applications")
        .find(and(eq("status", "PENDING"), exists("roleGrant", true)))
        .sort(Sorts.ascending("roleGrant.attemptedAt", "_id"))
        .limit(limit)
        .map(UserStore::application)
        .into(new ArrayList<>());
  }

  /** Monotonic projection. A concurrent newer version wins via the unique source index. */
  public void project(Application a) {
    if (a.status() != ApplicationStatus.ACTIVE)
      throw new IllegalArgumentException("Only ACTIVE applications may be projected");
    Bson filter = and(eq("sourceApplicationId", a.id()), lte("sourceVersion", a.version()));
    Bson update =
        combine(
            setOnInsert("_id", a.id()),
            set("identitySubject", a.identitySubject()),
            set("sourceApplicationId", a.id()),
            set("sourceVersion", a.version()),
            set("organizationName", a.organizationName()),
            set("shortDescription", a.shortDescription()),
            set("status", "ACTIVE"),
            set("refreshedAt", now()));
    try {
      collection("organizer_profiles").updateOne(filter, update, new UpdateOptions().upsert(true));
    } catch (MongoException ex) {
      if (!duplicate(ex)) throw ex;
    }
  }

  public PublicOrganizer publicOrganizer(String subject) {
    Document p =
        collection("organizer_profiles")
            .find(and(eq("identitySubject", subject), eq("status", "ACTIVE")))
            .first();
    if (p == null) throw new UserException(404, "ORGANIZER_NOT_FOUND", "Organizer not found");
    return new PublicOrganizer(
        subject,
        p.getString("organizationName"),
        p.getString("shortDescription"),
        followerCount(subject),
        p.getString("logoUrl"),
        p.getString("bannerUrl"));
  }

  public PublicOrganizer updateBranding(String subject, boolean logo, String url) {
    var result =
        collection("organizer_profiles")
            .updateOne(
                and(eq("identitySubject", subject), eq("status", "ACTIVE")),
                combine(set(logo ? "logoUrl" : "bannerUrl", url), set("refreshedAt", now())));
    if (result.getMatchedCount() != 1)
      throw new UserException(404, "ORGANIZER_NOT_FOUND", "Organizer not found");
    return publicOrganizer(subject);
  }

  public FollowState follow(String buyer, String organizer) {
    try {
      collection("user_follows")
          .insertOne(
              new Document("_id", id())
                  .append("buyerSubject", buyer)
                  .append("organizerSubject", organizer)
                  .append("createdAt", now()));
    } catch (MongoException ex) {
      if (!duplicate(ex)) throw ex;
    }
    return new FollowState(organizer, true, followerCount(organizer));
  }

  public FollowState unfollow(String buyer, String organizer) {
    collection("user_follows")
        .deleteOne(and(eq("buyerSubject", buyer), eq("organizerSubject", organizer)));
    return new FollowState(organizer, false, followerCount(organizer));
  }

  public FollowState followState(String buyer, String organizer) {
    boolean following =
        collection("user_follows")
                .find(and(eq("buyerSubject", buyer), eq("organizerSubject", organizer)))
                .first()
            != null;
    return new FollowState(organizer, following, followerCount(organizer));
  }

  private long followerCount(String organizer) {
    return collection("user_follows").countDocuments(eq("organizerSubject", organizer));
  }

  private Bson grantFilter(Application a) {
    return and(
        eq("_id", a.id()),
        eq("status", "PENDING"),
        eq("roleGrant.operationId", a.roleGrant().operationId()));
  }

  private Application changed(Bson filter, Bson update) {
    Document d =
        collection("organizer_applications")
            .findOneAndUpdate(
                filter, update, new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
    return d == null ? null : application(d);
  }

  private static boolean duplicate(MongoException ex) {
    return ex.getCode() == 11000;
  }

  private static Instant instant(Document d, String key) {
    Date value = d.getDate(key);
    return value == null ? null : value.toInstant();
  }

  private static Application application(Document d) {
    Document g = d.get("roleGrant", Document.class);
    RoleGrant grant =
        g == null
            ? null
            : new RoleGrant(
                g.getString("operationId"),
                g.getString("requestedBySubject"),
                g.getString("status"),
                instant(g, "attemptedAt"));
    return new Application(
        d.getString("_id"),
        d.getString("identitySubject"),
        d.getString("organizationName"),
        d.getString("shortDescription"),
        ApplicationStatus.valueOf(d.getString("status")),
        d.getString("rejectionReason"),
        d.getString("decidedBySubject"),
        instant(d, "decidedAt"),
        instant(d, "createdAt"),
        ((Number) d.get("version")).longValue(),
        grant);
  }
}
