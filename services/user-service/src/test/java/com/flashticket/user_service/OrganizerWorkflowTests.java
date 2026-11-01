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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
    return new OrganizerService(
        store, roles, telemetry, Duration.ofSeconds(60), Duration.ofSeconds(1));
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
  void workerDoesNotGrantWhileApprovalOwnsFreshAttempt() throws Exception {
    var application = store.apply("buyer", "Organization", "Description");
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger grants = new AtomicInteger();
    var service =
        service(
            subject -> {
              if (grants.incrementAndGet() > 1) throw new IllegalStateException("concurrent grant");
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
      var approved = executor.submit(() -> service.approve(application.id(), "admin-original"));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        new OrganizerRecovery(store, service, telemetry, 20).recover();
        assertThat(grants).hasValue(1);
      } finally {
        release.countDown();
      }
      assertThat(approved.get(5, TimeUnit.SECONDS).status()).isEqualTo(ApplicationStatus.ACTIVE);
    }
  }

  @Test
  void simultaneousAdminsObserveSameCompletedDecisionWithoutDuplicateGrant() throws Exception {
    var application = store.apply("buyer", "Organization", "Description");
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger grants = new AtomicInteger();
    var service =
        service(
            subject -> {
              if (grants.incrementAndGet() > 1) throw new IllegalStateException("concurrent grant");
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
      var first = executor.submit(() -> service.approve(application.id(), "admin-original"));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      CountDownLatch secondStarted = new CountDownLatch(1);
      var second =
          executor.submit(
              () -> {
                secondStarted.countDown();
                return service.approve(application.id(), "admin-retry");
              });
      try {
        assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
        // Give the competing request time to read the PENDING application.
        Thread.sleep(150);
      } finally {
        release.countDown();
      }
      assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo(ApplicationStatus.ACTIVE);
      var retried = second.get(5, TimeUnit.SECONDS);
      assertThat(retried.status()).isEqualTo(ApplicationStatus.ACTIVE);
      assertThat(retried.decidedBySubject()).isEqualTo("admin-original");
      assertThat(grants).hasValue(1);
    }
  }

  @Test
  void expiredCrashIsRecoveredWithOriginalAdmin() {
    Instant start = Instant.parse("2026-11-01T00:00:00Z");
    store = new UserStore(mongo, Clock.fixed(start, ZoneOffset.UTC), "tbl_");
    var applied = store.apply("buyer", "Organization", "Description");
    var crashed = store.claim(applied.id(), "admin-original");
    var recoveredStore =
        new UserStore(mongo, Clock.fixed(start.plusSeconds(61), ZoneOffset.UTC), "tbl_");
    var recovered =
        new OrganizerService(
            recoveredStore,
            subject -> {},
            telemetry,
            Duration.ofSeconds(60),
            Duration.ofSeconds(1));
    new OrganizerRecovery(recoveredStore, recovered, telemetry, 20).recover();
    var active = store.application(applied.id());
    assertThat(active.status()).isEqualTo(ApplicationStatus.ACTIVE);
    assertThat(active.decidedBySubject()).isEqualTo("admin-original");
    assertThat(active.roleGrant().operationId()).isEqualTo(crashed.roleGrant().operationId());
    assertThat(store.publicOrganizer("buyer").organizationName()).isEqualTo("Organization");
  }

  @Test
  void expiredOwnerCannotActivateOrReleaseNewOwnersAttempt() {
    Instant start = Instant.parse("2026-11-01T00:00:00Z");
    store = new UserStore(mongo, Clock.fixed(start, ZoneOffset.UTC), "tbl_");
    var applied = store.apply("buyer", "Organization", "Description");
    var expired = store.claim(applied.id(), "admin-original");
    var recoveredStore =
        new UserStore(mongo, Clock.fixed(start.plusSeconds(61), ZoneOffset.UTC), "tbl_");
    var acquired = recoveredStore.retryGrant(expired, Duration.ofSeconds(60));
    assertThat(acquired).isNotNull();
    assertThat(recoveredStore.retryGrant(expired, Duration.ofSeconds(60))).isNull();
    assertThat(recoveredStore.retryGrant(acquired, Duration.ofSeconds(60))).isNull();
    assertThat(store.activate(expired)).isNull();
    store.grantUnknown(expired);
    var stillOwned = store.application(applied.id());
    assertThat(stillOwned.status()).isEqualTo(ApplicationStatus.PENDING);
    assertThat(stillOwned.roleGrant().status()).isEqualTo("REQUESTED");
    assertThat(stillOwned.version()).isEqualTo(acquired.version());
    assertThat(recoveredStore.activate(acquired).status()).isEqualTo(ApplicationStatus.ACTIVE);
  }

  @Test
  void freshBusyAttemptReturnsRetryableErrorWithoutReportingPendingAsSuccess() {
    var applied = store.apply("buyer", "Organization", "Description");
    var owned = store.claim(applied.id(), "admin-original");
    var service =
        new OrganizerService(
            store,
            subject -> fail("A live attempt must not be granted again"),
            telemetry,
            Duration.ofSeconds(60),
            Duration.ofMillis(100));
    assertThatThrownBy(() -> service.approve(applied.id(), "admin-retry"))
        .isInstanceOfSatisfying(
            UserException.class, error -> assertThat(error.status()).isEqualTo(503));
    var pending = store.application(applied.id());
    assertThat(pending.status()).isEqualTo(ApplicationStatus.PENDING);
    assertThat(pending.version()).isEqualTo(owned.version());
    assertThat(pending.roleGrant().requestedBySubject()).isEqualTo("admin-original");
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
