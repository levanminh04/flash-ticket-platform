package com.flashticket.user_service.domain;

import java.time.Instant;
import java.util.List;

public final class UserModels {
  private UserModels() {}

  public enum ApplicationStatus {
    PENDING,
    ACTIVE,
    REJECTED
  }

  public record UserProfile(
      String userId,
      String identitySubject,
      String displayName,
      String email,
      String dateOfBirth,
      String address,
      String phoneNumber,
      String avatarUrl) {}

  public record RoleGrant(
      String operationId, String requestedBySubject, String status, Instant attemptedAt) {}

  public record Application(
      String id,
      String identitySubject,
      String organizationName,
      String shortDescription,
      String contactEmail,
      String contactPhone,
      String websiteUrl,
      String socialPageUrl,
      String headquartersAddress,
      String taxCode,
      ApplicationStatus status,
      String rejectionReason,
      String decidedBySubject,
      Instant decidedAt,
      Instant createdAt,
      long version,
      RoleGrant roleGrant) {
    public Application(
        String id,
        String identitySubject,
        String organizationName,
        String shortDescription,
        ApplicationStatus status,
        String rejectionReason,
        String decidedBySubject,
        Instant decidedAt,
        Instant createdAt,
        long version,
        RoleGrant roleGrant) {
      this(
          id,
          identitySubject,
          organizationName,
          shortDescription,
          null,
          null,
          null,
          null,
          null,
          null,
          status,
          rejectionReason,
          decidedBySubject,
          decidedAt,
          createdAt,
          version,
          roleGrant);
    }
  }

  public record ApplicationView(
      String id,
      String identitySubject,
      String organizationName,
      String shortDescription,
      String contactEmail,
      String contactPhone,
      String websiteUrl,
      String socialPageUrl,
      String headquartersAddress,
      String taxCode,
      ApplicationStatus status,
      String rejectionReason,
      String decidedBySubject,
      Instant decidedAt,
      Instant createdAt,
      String version) {
    public static ApplicationView of(Application a) {
      return new ApplicationView(
          a.id(),
          a.identitySubject(),
          a.organizationName(),
          a.shortDescription(),
          a.contactEmail(),
          a.contactPhone(),
          a.websiteUrl(),
          a.socialPageUrl(),
          a.headquartersAddress(),
          a.taxCode(),
          a.status(),
          a.rejectionReason(),
          a.decidedBySubject(),
          a.decidedAt(),
          a.createdAt(),
          Long.toString(a.version()));
    }
  }

  @com.fasterxml.jackson.annotation.JsonInclude(
      com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public record PublicOrganizer(
      String organizerSubject,
      String organizationName,
      String shortDescription,
      long followerCount,
      String logoUrl,
      String bannerUrl) {}

  public record FollowState(String organizerSubject, boolean following, long followerCount) {}

  public record Page<T>(List<T> items, String nextCursor) {
    public Page {
      items = List.copyOf(items);
    }
  }
}
