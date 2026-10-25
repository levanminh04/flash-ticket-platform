package com.flashticket.user_service.dto.response;

import jakarta.validation.constraints.*;

public record OrganizerPublicView(
    @NotBlank String organizerSubject,
    @NotBlank String organizationName,
    @NotBlank String shortDescription,
    @PositiveOrZero long followerCount) {}
