package com.flashticket.user_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RejectOrganizerApplicationRequest(@NotBlank String reason) {}
