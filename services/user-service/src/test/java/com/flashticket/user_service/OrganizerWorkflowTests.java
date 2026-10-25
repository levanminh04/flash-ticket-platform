package com.flashticket.user_service;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.user_service.domain.UserModels.Application;
import com.flashticket.user_service.domain.UserModels.ApplicationStatus;
import com.flashticket.user_service.identity.KeycloakRoles;
import com.flashticket.user_service.observability.UserTelemetry;
import com.flashticket.user_service.persistence.UserStore;
import com.flashticket.user_service.service.OrganizerRecovery;
import com.flashticket.user_service.service.OrganizerService;
import com.flashticket.user_service.web.UserException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.IndexOptions;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class OrganizerWorkflowTests {
  private static MongoServer server;
  private static MongoClient client;
  private MongoTemplate mongo;
  private UserStore store;
  private UserTelemetry telemetry;

  @BeforeAll
  static void start() {
    server = new MongoServer(new MemoryBackend());
    client = MongoClients.create(server.bindAndGetConnectionString());
  }

  @AfterAll
  static void stop() {
    client.close();
    server.shutdownNow();
  }

  @BeforeEach
  void setup() {
    mongo = new MongoTemplate(client, "workflow_test");
    mongo.getDb().drop();
    mongo
        .getCollection("tbl_organizer_applications")
        .createIndex(new Document("identitySubject", 1), new IndexOptions().unique(true));
    mongo
        .getCollection("tbl_organizer_profiles")
        .createIndex(new Document("identitySubject", 1), new IndexOptions().unique(true));
    mongo
        .getCollection("tbl_organizer_profiles")
        .createIndex(new Document("sourceApplicationId", 1), new IndexOptions().unique(true));
    store = new UserStore(mongo, Clock.systemUTC(), "tbl_");
    telemetry = new UserTelemetry(new SimpleMeterRegistry(), Clock.systemUTC());
  }

  private OrganizerService service(KeycloakRoles roles) {
    return new OrganizerService(store, roles, telemetry);
  }

  @Test
  void crashAfterKeycloakGrantRecoversWithOriginalAdminAndRebuildsProfile() {
    var application = store.apply("buyer", "Original", "Description");
    AtomicBoolean granted = new AtomicBoolean();
    var uncertain =
        service(
            subject -> {
              granted.set(true);
              throw new IllegalStateException("connection lost");
            });
    assertThatThrownBy(() -> uncertain.approve(application.id(), "admin-original"))
        .isInstanceOf(UserException.class);
    var pending = store.application(application.id());
    assertThat(pending.status()).isEqualTo(ApplicationStatus.PENDING);
    assertThat(pending.decidedBySubject()).isNull();
    assertThat(pending.roleGrant().requestedBySubject()).isEqualTo("admin-original");
    var restarted =
        service(
            subject -> {
              assertThat(subject).isEqualTo("buyer");
              assertThat(granted).isTrue();
            });
    var active = restarted.approve(application.id(), "admin-retry");
    assertThat(active.status()).isEqualTo(ApplicationStatus.ACTIVE);
    assertThat(active.decidedBySubject()).isEqualTo("admin-original");
    assertThat(store.publicOrganizer("buyer").organizationName()).isEqualTo("Original");
    mongo.getCollection("tbl_organizer_profiles").deleteMany(new Document());
    new OrganizerRecovery(store, restarted, telemetry, 2).recover();
    assertThat(store.publicOrganizer("buyer").shortDescription()).isEqualTo("Description");
  }

  @Test
  void rejectCannotWinAfterApprovalHasClaimedApplication() throws Exception {
    var application = store.apply("buyer", "Organization", "Description");
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    var service =
        service(
            subject -> {
              entered.countDown();
              try {
                if (!release.await(10, TimeUnit.SECONDS))
                  throw new IllegalStateException("test timeout");
              } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
              }
            });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<Application> approved =
          executor.submit(() -> service.approve(application.id(), "admin-1"));
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> service.reject(application.id(), "admin-2", "Rejected"))
            .isInstanceOfSatisfying(
                UserException.class, e -> assertThat(e.status()).isEqualTo(409));
      } finally {
        release.countDown();
      }
      assertThat(approved.get(10, TimeUnit.SECONDS).status()).isEqualTo(ApplicationStatus.ACTIVE);
    }
  }

  @Test
  void rejectedApplicationCannotBeResubmittedOrApproved() {
    var application = store.apply("buyer", "Organization", "Description");
    var service = service(subject -> fail("Rejected application must not reach Keycloak"));
    assertThat(service.reject(application.id(), "admin", "Incomplete").status())
        .isEqualTo(ApplicationStatus.REJECTED);
    assertThatThrownBy(() -> store.apply("buyer", "Another", "Another"))
        .isInstanceOf(UserException.class);
    assertThatThrownBy(() -> service.approve(application.id(), "admin"))
        .isInstanceOf(UserException.class);
    assertThatThrownBy(() -> store.publicOrganizer("buyer")).isInstanceOf(UserException.class);
  }

  @Test
  void recoveryWalksBeyondFirstPageOfActiveApplications() {
    var service = service(subject -> {});
    for (int i = 0; i < 5; i++)
      service.approve(store.apply("buyer-" + i, "Org " + i, "Description").id(), "admin");
    mongo.getCollection("tbl_organizer_profiles").deleteMany(new Document());
    var recovery = new OrganizerRecovery(store, service, telemetry, 2);
    recovery.recover();
    recovery.recover();
    recovery.recover();
    assertThat(mongo.getCollection("tbl_organizer_profiles").countDocuments()).isEqualTo(5);
  }

  @Test
  void olderProjectionCannotOverwriteNewerVersion() {
    var a =
        service(subject -> {})
            .approve(store.apply("buyer", "Current name", "Current description").id(), "admin");
    var stale =
        new Application(
            a.id(),
            a.identitySubject(),
            "Stale name",
            "Stale description",
            a.status(),
            null,
            a.decidedBySubject(),
            a.decidedAt(),
            a.createdAt(),
            a.version() - 1,
            a.roleGrant());
    store.project(stale);
    assertThat(store.publicOrganizer("buyer").organizationName()).isEqualTo("Current name");
  }
}
