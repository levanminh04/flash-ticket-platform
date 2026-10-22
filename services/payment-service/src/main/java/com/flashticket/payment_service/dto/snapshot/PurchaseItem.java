package com.flashticket.payment_service.dto.snapshot;
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
public record PurchaseItem(
@NotNull UUID orderItemId,
@NotNull UUID ticketTypeId,
@NotBlank String ticketTypeName,
@NotNull @Positive Integer quantity,
@NotNull @WireMoney(positive=true) String unitPrice,
@NotNull @WireMoney(positive=true) String lineSubtotal,
UUID sectorId,
@NotNull List<@Valid PurchaseSeat> seats) {
public PurchaseItem {if (seats != null) seats = List.copyOf(seats);}

}
