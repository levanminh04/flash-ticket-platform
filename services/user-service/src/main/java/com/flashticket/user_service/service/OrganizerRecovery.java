package com.flashticket.user_service.service;

import com.flashticket.user_service.domain.UserModels.ApplicationStatus;
import com.flashticket.user_service.observability.UserTelemetry;
import com.flashticket.user_service.persistence.UserStore;
import com.flashticket.user_service.web.UserException;
import com.mongodb.MongoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "user.recovery.enabled", havingValue = "true", matchIfMissing = true)
public final class OrganizerRecovery {
  private final UserStore store;
  private final OrganizerService organizers;
  private final UserTelemetry telemetry;
  private final int batchSize;
  private String projectionCursor;

  public OrganizerRecovery(
      UserStore store,
      OrganizerService organizers,
      UserTelemetry telemetry,
      @Value("${user.recovery.batch-size:20}") int batchSize) {
    if (batchSize < 1 || batchSize > 100)
      throw new IllegalArgumentException("Recovery batch size must be 1..100");
    this.store = store;
    this.organizers = organizers;
    this.telemetry = telemetry;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${user.recovery.delay:30s}")
  public void recover() {
    try {
      for (var pending : store.pendingGrants(batchSize)) {
        try {
          organizers.finishGrant(pending);
        } catch (UserException | MongoException | DataAccessException ex) {
          telemetry.decision(
              "role-grant-recovery", "RETRY", "PENDING", "PENDING", "GRANT_RETRY_PENDING");
        }
      }
      var active = store.applications(null, ApplicationStatus.ACTIVE, projectionCursor, batchSize);
      for (var application : active) organizers.projectSafely(application);
      // Walk every page; rescanning only the first batch would permanently starve later profiles.
      projectionCursor = active.size() == batchSize ? active.getLast().id() : null;
    } catch (MongoException | DataAccessException ex) {
      telemetry.decision(
          "organizer-recovery", "RETRY", "UNKNOWN", "UNKNOWN", "STORAGE_UNAVAILABLE");
    }
  }
}
