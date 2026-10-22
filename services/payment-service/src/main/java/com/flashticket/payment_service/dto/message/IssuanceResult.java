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
public record IssuanceResult(
@NotNull UUID orderId,
@NotNull UUID chargeId,
@NotNull UUID issuanceId,
@NotNull IssuanceState state,
@NotNull @Min(0) Integer ticketCount,
String failureCode) {
@AssertTrue @JsonIgnore public boolean isResultConsistent(){return state==IssuanceState.COMPLETED ? ticketCount!=null && ticketCount>0 && failureCode==null:state==IssuanceState.FAILED && Integer.valueOf(0).equals(ticketCount) && failureCode!=null && !failureCode.isBlank();}
}
