package com.flashticket.user_service.dto.response;

import jakarta.validation.constraints.NotBlank;

public record MeView(@NotBlank String identitySubject, String displayName) {}
