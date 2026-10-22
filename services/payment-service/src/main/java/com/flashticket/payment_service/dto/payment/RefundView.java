package com.flashticket.payment_service.dto.payment;
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
public record RefundView(
@NotNull UUID refundId,
@NotNull UUID chargeId,
@NotNull @WireMoney String amount,
@NotNull RefundState state,
@NotNull RefundReason reason) {

}
