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
public record AcceptPayment(
@NotNull UUID orderId,
@NotNull UUID chargeId,
@NotNull UUID attemptId,
@NotNull @WireMoney(positive=true) String amount,
@NotNull @Pattern(regexp="VND") String currency,
@NotNull @WireVersion String sourceOrderVersion) {

}
