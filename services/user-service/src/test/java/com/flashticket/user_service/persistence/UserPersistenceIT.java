package com.flashticket.user_service.persistence;

import static com.flashticket.user_service.support.UserFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.user_service.config.MongoConfig;
import com.flashticket.user_service.document.*;
import com.flashticket.user_service.follow.repository.UserFollowRepository;
import com.flashticket.user_service.organizer.repository.*;
import com.flashticket.user_service.user.repository.UserRepository;
import com.mongodb.MongoWriteException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.*;

@DataMongoTest
@Import(MongoConfig.class)
@ActiveProfiles("integration")
@Testcontainers
class UserPersistenceIT {
  static final Path ROOT =
      Path.of(System.getProperty("basedir", "."))
          .toAbsolutePath()
          .normalize()
          .getParent()
          .getParent();
  static final String IMAGE =
      "mongo:8.0@sha256:98605bfa1bb2a15dd82109e1d78ad31527a9a744909fab4606076fa71a0ae515";

  @Container
  static final GenericContainer<?> MONGO =
      new GenericContainer<>(DockerImageName.parse(IMAGE))
          .withExposedPorts(27017)
          .withEnv("MONGO_INITDB_ROOT_USERNAME", "user_admin")
          .withEnv("MONGO_INITDB_ROOT_PASSWORD", "test-only-root")
          .withEnv("MONGO_INITDB_DATABASE", "user_db")
          .withEnv("USER_DB_VIEWER_PASSWORD", "test-only-viewer")
          .withCopyFileToContainer(
              MountableFile.forHostPath(ROOT.resolve("docs/architecture/data/mongodb/schema.cjs")),
              "/opt/flash-ticket/schema.cjs")
          .withCopyFileToContainer(
              MountableFile.forHostPath(ROOT.resolve("deploy/mongodb/10-user.js")),
              "/docker-entrypoint-initdb.d/10-user.js")
          .withCopyFileToContainer(
              MountableFile.forHostPath(ROOT.resolve("deploy/mongodb/99-collection-prefix.js")),
              "/docker-entrypoint-initdb.d/99-collection-prefix.js")
          .waitingFor(Wait.forListeningPort())
          .withStartupTimeout(Duration.ofMinutes(2));

  @DynamicPropertySource
  static void mongo(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.mongodb.uri",
        () ->
            "mongodb://user_admin:test-only-root@"
                + MONGO.getHost()
                + ":"
                + MONGO.getMappedPort(27017)
                + "/user_db?authSource=admin");
  }

  @Autowired MongoTemplate template;
  @Autowired UserRepository users;
  @Autowired OrganizerApplicationRepository applications;
  @Autowired OrganizerProfileRepository profiles;
  @Autowired UserFollowRepository follows;

  @BeforeEach
  void clearOnlyThisDisposableDatabase() {
    assertThat(MONGO.isRunning()).isTrue();
    assertThat(template.getDb().getName()).isEqualTo("user_db");
    for (String collection :
        List.of(
            "tbl_users",
            "tbl_organizer_applications",
            "tbl_organizer_profiles",
            "tbl_user_follows"))
      template.getDb().getCollection(collection).deleteMany(new Document());
  }

  @Test
  void roundTripsFourDocumentsWithoutExtraFields() {
    var user = user();
    user.setVersion(null);
    var application = activeApplication();
    application.setVersion(null);
    assertThat(users.save(user).getVersion()).isZero();
    assertThat(applications.save(application).getVersion()).isZero();
    profiles.save(profile());
    follows.save(follow());
    assertThat(users.findById(ID).orElseThrow().getIdentitySubject()).isEqualTo("idp|buyer-17");
    assertThat(applications.findById(ID).orElseThrow().getRoleGrant().confirmedAt()).isEqualTo(NOW);
    assertThat(profiles.findById(ID).orElseThrow().getSourceVersion()).isZero();
    assertThat(follows.findById(ID).orElseThrow().getBuyerSubject()).isEqualTo(SUBJECT);
    assertThat(template.getCollectionNames())
        .containsExactlyInAnyOrder(
            "tbl_users",
            "tbl_organizer_applications",
            "tbl_organizer_profiles",
            "tbl_user_follows");
    var raw = template.getDb().getCollection("tbl_users").find(new Document("_id", ID)).first();
    assertThat(raw.get("_id")).isInstanceOf(String.class);
    assertThat(raw.get("createdAt")).isEqualTo(Date.from(NOW));
    assertThat(raw).doesNotContainKey("_class");
    assertThat(template.getDb().getCollection("tbl_organizer_profiles").find().first())
        .doesNotContainKeys("version", "updatedAt", "_class");
    assertThat(template.getDb().getCollection("tbl_user_follows").find().first())
        .doesNotContainKeys("version", "updatedAt", "_class");
  }

  @Test
  void rejectsDuplicateIdentityAndFollowPair() {
    var first = user();
    first.setVersion(null);
    users.save(first);
    var duplicate = user();
    duplicate.setId(UUID.randomUUID().toString());
    duplicate.setVersion(null);
    assertThatThrownBy(() -> users.save(duplicate)).isInstanceOf(DuplicateKeyException.class);
    var app = pendingApplication();
    app.setVersion(null);
    applications.save(app);
    var duplicateApp = pendingApplication();
    duplicateApp.setId(UUID.randomUUID().toString());
    duplicateApp.setVersion(null);
    assertThatThrownBy(() -> applications.save(duplicateApp))
        .isInstanceOf(DuplicateKeyException.class);
    follows.save(follow());
    var duplicateFollow = follow();
    duplicateFollow.setId(UUID.randomUUID().toString());
    assertThatThrownBy(() -> follows.save(duplicateFollow))
        .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void enforcesApplicationBranchesAndOptionalFields() {
    var invalidUser = bson(user());
    invalidUser.put("displayName", null);
    rejects("tbl_users", invalidUser);
    var extra = bson(user());
    extra.put("_class", "not-allowed");
    rejects("tbl_users", extra);
    var pending = bson(pendingApplication());
    pending.put("decidedAt", Date.from(NOW));
    rejects("tbl_organizer_applications", pending);
    var active = bson(activeApplication());
    active.remove("roleGrant");
    rejects("tbl_organizer_applications", active);
    var noConfirmation = bson(activeApplication());
    noConfirmation.get("roleGrant", Document.class).remove("confirmedAt");
    rejects("tbl_organizer_applications", noConfirmation);
    var rejected = bson(rejectedApplication());
    rejected.remove("rejectionReason");
    rejects("tbl_organizer_applications", rejected);
    var rejectedGrant = bson(rejectedApplication());
    rejectedGrant.put("roleGrant", bson(activeApplication()).get("roleGrant"));
    rejects("tbl_organizer_applications", rejectedGrant);
    for (var valid : List.of(pendingApplication(), activeApplication(), rejectedApplication())) {
      var value = bson(valid);
      value.put("_id", UUID.randomUUID().toString());
      value.put("identitySubject", UUID.randomUUID().toString());
      template.getDb().getCollection("tbl_organizer_applications").insertOne(value);
    }
    assertThat(applications.count()).isEqualTo(3);
  }

  @Test
  void staleVersionCannotOverwriteUserOrApplication() {
    var user = user();
    user.setVersion(null);
    users.save(user);
    var first = users.findById(ID).orElseThrow();
    var stale = users.findById(ID).orElseThrow();
    first.setDisplayName("Updated");
    assertThat(users.save(first).getVersion()).isEqualTo(1L);
    stale.setDisplayName("Stale");
    assertThatThrownBy(() -> users.save(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
    var app = pendingApplication();
    app.setVersion(null);
    applications.save(app);
    var firstApp = applications.findById(ID).orElseThrow();
    var staleApp = applications.findById(ID).orElseThrow();
    firstApp.setShortDescription("Updated");
    assertThat(applications.save(firstApp).getVersion()).isEqualTo(1L);
    staleApp.setShortDescription("Stale");
    assertThatThrownBy(() -> applications.save(staleApp))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  @Test
  void projectionSourceVersionIsNotOptimisticVersion() {
    var saved = profiles.save(profile());
    saved.setShortDescription("Refreshed");
    profiles.save(saved);
    var actual = profiles.findById(ID).orElseThrow();
    assertThat(actual.getSourceVersion()).isZero();
    assertThat(actual.getShortDescription()).isEqualTo("Refreshed");
  }

  Document bson(Object value) {
    var result = new Document();
    template.getConverter().write(value, result);
    return result;
  }

  void rejects(String collection, Document value) {
    assertThatThrownBy(() -> template.getDb().getCollection(collection).insertOne(value))
        .isInstanceOfSatisfying(
            MongoWriteException.class,
            error -> assertThat(error.getError().getCode()).isEqualTo(121));
  }
}
