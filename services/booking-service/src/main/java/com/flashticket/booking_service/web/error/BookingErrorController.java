package com.flashticket.booking_service.web.error;

import com.flashticket.booking_service.dto.response.Problem;
import com.flashticket.booking_service.exception.ErrorClassifier;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public final class BookingErrorController implements ErrorController {
  private final ProblemResponses responses;

  @RequestMapping("${server.error.path:/error}")
  public ResponseEntity<Problem> error(HttpServletRequest request, HttpServletResponse response) {
    Object supplied = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    int status = supplied instanceof Integer value && value >= 400 && value <= 599 ? value : 500;
    Throwable exception =
        request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable error
            ? error
            : null;
    String code =
        switch (status) {
          case 400 -> "INVALID_REQUEST";
          case 401 -> "UNAUTHENTICATED";
          case 403 -> "ACCESS_DENIED";
          case 404 -> "ROUTE_NOT_FOUND";
          case 405 -> "METHOD_NOT_ALLOWED";
          case 406 -> "NOT_ACCEPTABLE";
          case 415 -> "UNSUPPORTED_MEDIA_TYPE";
          default -> "INTERNAL_ERROR";
        };
    var entity =
        responses.response(
            request,
            HttpStatusCode.valueOf(status),
            ErrorClassifier.of(code, exception),
            List.of());
    return response.isCommitted() ? null : entity;
  }
}
