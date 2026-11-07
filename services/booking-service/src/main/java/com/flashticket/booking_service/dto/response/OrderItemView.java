package com.flashticket.booking_service.dto.response;

import com.flashticket.booking_service.dto.snapshot.SeatSelectionSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class OrderItemView {
  @NotNull private final UUID orderItemId;
  @NotNull private final UUID ticketTypeId;
  @NotBlank private final String ticketTypeName;
  @Positive private final int quantity;

  @NotBlank
  @Pattern(regexp = "^[1-9][0-9]{0,18}$")
  private final String unitPrice;

  @NotBlank
  @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$")
  private final String lineSubtotal;

  private final UUID sectorId;
  @NotNull @Valid private final List<@NotNull @Valid SeatSelectionSnapshot> seats;
}
