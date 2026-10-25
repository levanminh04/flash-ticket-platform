package com.flashticket.user_service.service;

import com.flashticket.user_service.domain.UserModels.Application;
import com.flashticket.user_service.domain.UserModels.ApplicationStatus;
import com.flashticket.user_service.identity.KeycloakRoles;
import com.flashticket.user_service.observability.UserTelemetry;
import com.flashticket.user_service.persistence.UserStore;
import com.flashticket.user_service.web.UserException;
import com.mongodb.MongoException;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class OrganizerService {
  private final UserStore store;
  private final KeycloakRoles roles;
  private final UserTelemetry telemetry;

  public OrganizerService(UserStore store, KeycloakRoles roles, UserTelemetry telemetry) {
    this.store = store;
    this.roles = roles;
    this.telemetry = telemetry;
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
    return finishGrant(current);
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
    String original = MDC.get("correlationId");
    MDC.put("correlationId", application.roleGrant().operationId());
    try {
      if (!store.touchGrant(application)) return store.application(application.id());
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
        telemetry.decision("role-grant", "UNKNOWN", "PENDING", "PENDING", "KEYCLOAK_UNCONFIRMED");
        throw new UserException(
            503, "ROLE_GRANT_UNAVAILABLE", "Organizer role could not be confirmed; retry is safe");
      }
      Application active = store.activate(application);
      if (active == null) active = store.application(application.id());
      if (active.status() != ApplicationStatus.ACTIVE) throw conflict();
      telemetry.decision("role-grant", "SUCCESS", "PENDING", "ACTIVE", "KEYCLOAK_CONFIRMED");
      projectSafely(active);
      return active;
    } finally {
      if (original == null) MDC.remove("correlationId");
      else MDC.put("correlationId", original);
    }
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
