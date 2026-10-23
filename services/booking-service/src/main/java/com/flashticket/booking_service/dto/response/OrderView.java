package com.flashticket.booking_service.dto.response;

import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.OrderState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class OrderView {
  @NotNull private final UUID orderId;
  @NotNull private final UUID eventId;
  @NotNull private final OrderState state;
  @NotBlank @Email private final String customerEmail;
  @NotNull private final Currency currency;
  @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") private final String subtotal;
  @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") private final String discount;
  @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") private final String total;
  @NotNull private final Instant expiresAt;
  private final Instant paymentFrozenAt;
  @NotBlank
  @Pattern(regexp = "^[1-9][0-9]{0,18}$")
  @DecimalMax("9223372036854775807")
  private final String sourceConfigVersion;
  @NotNull @Valid private final List<@NotNull @Valid OrderItemView> items;
}
