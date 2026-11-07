package com.flashticket.user_service.identity;

/** Returns only after both BUYER and ORGANIZER are confirmed at the identity source. */
@FunctionalInterface
public interface KeycloakRoles {
  void ensureOrganizer(String subject);
}
