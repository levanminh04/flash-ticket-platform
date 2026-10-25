package com.flashticket.user_service.document;

import com.flashticket.user_service.enums.RoleGrantStatus;
import java.time.Instant;

public record RoleGrant(
    String operationId, RoleGrantStatus status, Instant attemptedAt, Instant confirmedAt) {}
