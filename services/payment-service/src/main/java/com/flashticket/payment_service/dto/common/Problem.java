package com.flashticket.payment_service.dto.common;
import java.util.*;
import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.dto.payment.*;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.entity.enums.*;
public record Problem(
@NotBlank String code,
@NotBlank String message,
@NotNull UUID correlationId,
@NotNull List<@Valid Violation> violations) {
public Problem {if (violations != null) violations = List.copyOf(violations);}

}
