package com.flashticket.booking_service.dto.response;

import com.flashticket.booking_service.enums.SeatState;
import com.flashticket.booking_service.enums.SectorType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class Availability {
  @NotNull private final UUID eventId;
  @NotNull
  @Pattern(regexp = "^[1-9][0-9]{0,18}$")
  @DecimalMax("9223372036854775807")
  private final String sourceConfigVersion;
  @NotNull private final Instant asOf;
  @NotNull @Valid private final List<@NotNull @Valid TicketTypeAvailability> ticketTypes;
  @NotNull @Valid private final List<@NotNull @Valid SectorAvailability> sectors;
  @NotNull @Valid private final List<@NotNull @Valid SeatAvailability> seats;

  @Getter
  @AllArgsConstructor
  public static final class TicketTypeAvailability {
    @NotNull private final UUID ticketTypeId;
    @NotNull
    @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$")
    @DecimalMax("9223372036854775807")
    private final String available;
  }

  @Getter
  @AllArgsConstructor
  public static final class SectorAvailability {
    @NotNull private final UUID sectorId;
    @NotNull private final SectorType kind;
    @NotNull
    @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$")
    @DecimalMax("9223372036854775807")
    private final String available;
  }

  @Getter
  @AllArgsConstructor
  public static final class SeatAvailability {
    @NotNull private final UUID seatId;
    @NotNull private final SeatState state;
  }
}
