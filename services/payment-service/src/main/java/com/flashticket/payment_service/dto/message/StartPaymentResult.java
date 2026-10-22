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
public record StartPaymentResult(
@NotNull UUID requestId,
@NotNull UUID orderId,
@NotNull StartPaymentResultState state,
UUID attemptId,
String failureCode) {
@AssertTrue @JsonIgnore public boolean isResultConsistent(){return state==StartPaymentResultState.CREATED ? attemptId!=null && failureCode==null : state==StartPaymentResultState.REJECTED && attemptId==null && failureCode!=null && !failureCode.isBlank();}
}
