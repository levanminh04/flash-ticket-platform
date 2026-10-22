package com.flashticket.payment_service.dto.message;
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
public record EventCancellation(
@NotNull UUID eventId,
@NotNull UUID cancellationId,
@NotNull @WireVersion String sourceConfigVersion,
@NotNull Instant cancelledAt,
@NotBlank String actorSubject,
@NotNull @Valid SalesConfiguration sales,
@NotNull @Valid AccessConfiguration access) {

}
