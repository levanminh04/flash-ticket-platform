package com.flashticket.user_service.support;

import com.flashticket.user_service.document.*;
import com.flashticket.user_service.enums.*;
import java.time.Instant;

public final class UserFixtures {
  public static final String ID = "00000000-0000-4000-8000-000000000001";
  public static final String SUBJECT = "idp|buyer-17";
  public static final Instant NOW = Instant.parse("2026-10-25T00:00:00.123Z");

  private UserFixtures() {}

  public static UserDocument user() {
    var value = new UserDocument();
    value.setId(ID);
    value.setIdentitySubject(SUBJECT);
    value.setDisplayName("Người mua");
    value.setCreatedAt(NOW);
    value.setUpdatedAt(NOW);
    value.setVersion(0L);
    return value;
  }

  public static OrganizerApplicationDocument pendingApplication() {
    var value = new OrganizerApplicationDocument();
    value.setId(ID);
    value.setIdentitySubject(SUBJECT);
    value.setOrganizationName("Tổ chức");
    value.setShortDescription("Mô tả");
    value.setStatus(OrganizerApplicationStatus.PENDING);
    value.setCreatedAt(NOW);
    value.setUpdatedAt(NOW);
    value.setVersion(0L);
    return value;
  }

  public static OrganizerApplicationDocument activeApplication() {
    var value = pendingApplication();
    value.setStatus(OrganizerApplicationStatus.ACTIVE);
    value.setDecidedBySubject("admin|1");
    value.setDecidedAt(NOW);
    value.setRoleGrant(new RoleGrant(ID, RoleGrantStatus.CONFIRMED, NOW, NOW));
    return value;
  }

  public static OrganizerApplicationDocument rejectedApplication() {
    var value = pendingApplication();
    value.setStatus(OrganizerApplicationStatus.REJECTED);
    value.setDecidedBySubject("admin|1");
    value.setDecidedAt(NOW);
    value.setRejectionReason("Chưa phù hợp");
    return value;
  }

  public static OrganizerProfileDocument profile() {
    var value = new OrganizerProfileDocument();
    value.setId(ID);
    value.setIdentitySubject(SUBJECT);
    value.setSourceApplicationId(ID);
    value.setSourceVersion(0L);
    value.setOrganizationName("Tổ chức");
    value.setShortDescription("Mô tả");
    value.setStatus(OrganizerProfileStatus.ACTIVE);
    value.setRefreshedAt(NOW);
    return value;
  }

  public static UserFollowDocument follow() {
    var value = new UserFollowDocument();
    value.setId(ID);
    value.setBuyerSubject(SUBJECT);
    value.setOrganizerSubject("idp|organizer-5");
    value.setCreatedAt(NOW);
    return value;
  }
}
