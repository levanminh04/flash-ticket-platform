package com.flashticket.user_service.service;

import com.flashticket.user_service.domain.UserModels.Application;
import com.flashticket.user_service.domain.UserModels.ApplicationStatus;
import com.flashticket.user_service.identity.KeycloakRoles;
import com.flashticket.user_service.observability.UserTelemetry;
import com.flashticket.user_service.persistence.UserStore;
import com.flashticket.user_service.web.UserException;
import com.mongodb.MongoException;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public final class OrganizerService {
  private final UserStore store;
  private final KeycloakRoles roles;
  private final UserTelemetry telemetry;
  private final Duration leaseDuration;
  private final Duration waitTimeout;

  public OrganizerService(
      UserStore store,
      KeycloakRoles roles,
      UserTelemetry telemetry,
      @Value("${user.role-grant.lease-duration:60s}") Duration leaseDuration,
      @Value("${user.role-grant.wait-timeout:5s}") Duration waitTimeout) {
    if (leaseDuration.isNegative() || leaseDuration.isZero() || waitTimeout.isNegative())
      throw new IllegalArgumentException("Invalid role grant lease or wait timeout");
    this.store = store;
    this.roles = roles;
    this.telemetry = telemetry;
    this.leaseDuration = leaseDuration;
    this.waitTimeout = waitTimeout;
  }

  public Application approve(String id, String admin) {
    Application claimed = store.claim(id, admin);
    Application current = claimed == null ? store.application(id) : claimed;
    if (current.status() == ApplicationStatus.ACTIVE) {
      projectSafely(current);
      return current;
    }
    if (current.status() != ApplicationStatus.PENDING || current.roleGrant() == null)
      throw conflict();
    // The initial atomic claim already owns the attempt. Retries and workers
    // must acquire an expired/released attempt instead of touching a live one.
    Application result = claimed == null ? finishGrant(current) : executeGrant(claimed);
    return awaitDecision(result);
  }

  public Application reject(String id, String admin, String reason) {
    Application rejected = store.reject(id, admin, reason);
    if (rejected != null) {
      telemetry.decision("organizer-reject", "SUCCESS", "PENDING", "REJECTED", "ADMIN_REJECTED");
      return rejected;
    }
    Application current = store.application(id);
    if (current.status() == ApplicationStatus.REJECTED && reason.equals(current.rejectionReason()))
      return current;
    throw conflict();
  }

  public Application finishGrant(Application application) {
    Application acquired = store.retryGrant(application, leaseDuration);
    return acquired == null ? store.application(application.id()) : executeGrant(acquired);
  }

  private Application executeGrant(Application application) {
    String original = MDC.get("correlationId");
    MDC.put("correlationId", application.roleGrant().operationId());
    try {
      String admin = application.roleGrant().requestedBySubject();
      if (admin == null || admin.isBlank()) {
        telemetry.decision("role-grant", "REJECTED", "PENDING", "PENDING", "GRANT_ACTOR_MISSING");
        throw new UserException(
            503,
            "ROLE_GRANT_ACTOR_MISSING",
            "Grant requires operator reconciliation of the original admin");
      }
      telemetry.decision("role-grant", "RETRY", "PENDING", "PENDING", "GRANT_REQUESTED");
      try {
        roles.ensureOrganizer(application.identitySubject());
      } catch (RuntimeException ex) {
        store.grantUnknown(application);
        Application latest = store.application(application.id());
        if (latest.status() == ApplicationStatus.ACTIVE) {
          projectSafely(latest);
          return latest;
        }
        telemetry.decision("role-grant", "UNKNOWN", "PENDING", "PENDING", "KEYCLOAK_UNCONFIRMED");
        throw new UserException(
            503, "ROLE_GRANT_UNAVAILABLE", "Organizer role could not be confirmed; retry is safe");
      }
      Application active = store.activate(application);
      if (active == null) active = store.application(application.id());
      if (active.status() != ApplicationStatus.ACTIVE) return active;
      telemetry.decision("role-grant", "SUCCESS", "PENDING", "ACTIVE", "KEYCLOAK_CONFIRMED");
      projectSafely(active);
      return active;
    } finally {
      if (original == null) MDC.remove("correlationId");
      else MDC.put("correlationId", original);
    }
  }

  private Application awaitDecision(Application current) {
    long started = System.nanoTime();
    while (current.status() == ApplicationStatus.PENDING
        && System.nanoTime() - started < waitTimeout.toNanos()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw unavailable();
      }
      current = store.application(current.id());
    }
    if (current.status() != ApplicationStatus.ACTIVE) throw unavailable();
    projectSafely(current);
    return current;
  }

  private static UserException unavailable() {
    return new UserException(
        503, "ROLE_GRANT_UNAVAILABLE", "Organizer role could not be confirmed; retry is safe");
  }

  public void projectSafely(Application active) {
    try {
      store.project(active);
      telemetry.projectionAge(active.decidedAt());
    } catch (MongoException | DataAccessException ex) {
      // Application remains the durable source. Do not undo an already confirmed role.
      telemetry.decision(
          "organizer-projection", "RETRY", "ACTIVE", "ACTIVE", "PROJECTION_UNAVAILABLE");
    }
  }

  private static UserException conflict() {
    return new UserException(
        409, "APPLICATION_STATE_CONFLICT", "Application cannot accept this decision");
  }
}
