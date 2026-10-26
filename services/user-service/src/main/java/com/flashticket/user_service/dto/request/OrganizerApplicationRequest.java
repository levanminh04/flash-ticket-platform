package com.flashticket.user_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OrganizerApplicationRequest(
    @NotBlank String organizationName, @NotBlank String shortDescription) {}
