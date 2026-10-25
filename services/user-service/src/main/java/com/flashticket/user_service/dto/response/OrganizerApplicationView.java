package com.flashticket.user_service.dto.response;

import com.flashticket.user_service.enums.OrganizerApplicationStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;

public record OrganizerApplicationView(
    @NotBlank String applicationId,
    @NotBlank String identitySubject,
    @NotBlank String organizationName,
    @NotBlank String shortDescription,
    @NotNull OrganizerApplicationStatus status,
    String rejectionReason,
    @NotNull Instant createdAt,
    @NotNull Instant updatedAt) {}
