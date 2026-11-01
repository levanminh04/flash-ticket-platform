package com.flashticket.booking_service.availability.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.booking_service.availability.service.AvailabilityService;
import com.flashticket.booking_service.config.BookingJsonConfig;
import com.flashticket.booking_service.config.SecurityConfig;
import com.flashticket.booking_service.dto.response.Availability;
import com.flashticket.booking_service.enums.SeatState;
import com.flashticket.booking_service.enums.SectorType;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AvailabilityController.class)
@Import({SecurityConfig.class, BookingJsonConfig.class, GlobalExceptionHandler.class})
class AvailabilityControllerTests {
  @Autowired MockMvc mvc;
  @MockitoBean AvailabilityService availabilityService;
  @MockitoBean JwtDecoder jwtDecoder;

  @Test
  void returnsPublicAvailabilityWithoutJwt() throws Exception {
    var eventId = UUID.randomUUID();
    var typeId = UUID.randomUUID();
    var sectorId = UUID.randomUUID();
    var seatId = UUID.randomUUID();
    var asOf = Instant.parse("2026-11-01T07:00:00Z");
    for (boolean seated : List.of(false, true)) {
      when(availabilityService.getAvailability(eventId))
          .thenReturn(
              new Availability(
                  eventId,
                  "3",
                  asOf,
                  List.of(new Availability.TicketTypeAvailability(typeId, "0")),
                  seated
                      ? List.of(
                          new Availability.SectorAvailability(sectorId, SectorType.SEATED, "0"))
                      : List.of(),
                  seated
                      ? List.of(new Availability.SeatAvailability(seatId, SeatState.HELD))
                      : List.of()));
      var result =
          mvc.perform(get("/api/v1/events/" + eventId + "/availability"))
              .andExpect(status().isOk())
              .andExpect(header().string("Cache-Control", "no-store"))
              .andExpect(jsonPath("$.eventId").value(eventId.toString()))
              .andExpect(jsonPath("$.sourceConfigVersion").value("3"))
              .andExpect(jsonPath("$.asOf").value(asOf.toString()))
              .andExpect(jsonPath("$.ticketTypes[0].ticketTypeId").value(typeId.toString()))
              .andExpect(jsonPath("$.ticketTypes[0].available").value("0"))
              .andExpect(jsonPath("$.buyerSubject").doesNotExist())
              .andExpect(jsonPath("$.reservationId").doesNotExist())
              .andExpect(jsonPath("$.configurationSnapshot").doesNotExist())
              .andExpect(jsonPath("$.promotions").doesNotExist());
      if (seated)
        result
            .andExpect(jsonPath("$.sectors[0].kind").value("SEATED"))
            .andExpect(jsonPath("$.sectors[0].available").value("0"))
            .andExpect(jsonPath("$.seats[0].seatId").value(seatId.toString()))
            .andExpect(jsonPath("$.seats[0].state").value("HELD"))
            .andExpect(jsonPath("$.seats[0].currentReservationId").doesNotExist());
      else
        result.andExpect(jsonPath("$.sectors").isEmpty()).andExpect(jsonPath("$.seats").isEmpty());
    }
    verify(availabilityService, org.mockito.Mockito.times(2)).getAvailability(eventId);
  }

  @Test
  void rejectsMalformedEventId() throws Exception {
    mvc.perform(get("/api/v1/events/not-a-uuid/availability"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    verifyNoInteractions(availabilityService);
  }

  @Test
  void returnsConfigNotReadyProblem() throws Exception {
    var eventId = UUID.randomUUID();
    when(availabilityService.getAvailability(eventId))
        .thenThrow(new BusinessException(ErrorCode.CONFIG_NOT_READY));
    mvc.perform(get("/api/v1/events/" + eventId + "/availability"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("CONFIG_NOT_READY"));
  }

  @Test
  void returnsEventNotOnSaleProblem() throws Exception {
    var eventId = UUID.randomUUID();
    when(availabilityService.getAvailability(eventId))
        .thenThrow(new BusinessException(ErrorCode.EVENT_NOT_ON_SALE));
    mvc.perform(get("/api/v1/events/" + eventId + "/availability"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVENT_NOT_ON_SALE"));
  }

  @Test
  void rejectsInvalidBearerToken() throws Exception {
    when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid test token"));
    mvc.perform(
            get("/api/v1/events/" + UUID.randomUUID() + "/availability")
                .header("Authorization", "Bearer invalid-token"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(availabilityService);
  }
}
