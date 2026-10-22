package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.payment.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.constraints.*;
import java.util.*;

public record OrderCompleted(
    @NotNull UUID orderId,
    @NotNull UUID chargeId,
    @NotNull UUID issuanceId,
    @NotNull @Positive Integer ticketCount) {}
