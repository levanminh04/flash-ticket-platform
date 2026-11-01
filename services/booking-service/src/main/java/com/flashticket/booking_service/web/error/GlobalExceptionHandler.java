package com.flashticket.booking_service.web.error;

import com.flashticket.booking_service.dto.response.Problem;
import com.flashticket.booking_service.exception.ErrorClassifier;
import com.flashticket.booking_service.exception.ErrorClassifier.Failure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.DisconnectedClientHelper;

@RestControllerAdvice
@RequiredArgsConstructor
public final class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  private final ProblemResponses responses;

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Problem> handleApplicationException(
      Exception exception, HttpServletRequest request) {
    if (DisconnectedClientHelper.isClientDisconnectedException(exception)) {
      LoggerFactory.getLogger(GlobalExceptionHandler.class).debug("HTTP client disconnected.");
      return null;
    }
    Failure failure =
        exception instanceof AuthenticationException
            ? ErrorClassifier.of("UNAUTHENTICATED", exception)
            : exception instanceof AccessDeniedException
                ? ErrorClassifier.of("ACCESS_DENIED", exception)
                : ErrorClassifier.classify(exception);
    return responses.response(request, status(failure.code()), failure, List.of());
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var servlet = (ServletWebRequest) request;
    boolean returnValidation =
        exception instanceof HandlerMethodValidationException validation
            && validation.isForReturnValue();
    String code =
        returnValidation || status.is5xxServerError()
            ? "INTERNAL_ERROR"
            : switch (status.value()) {
              case 404 -> "ROUTE_NOT_FOUND";
              case 405 -> "METHOD_NOT_ALLOWED";
              case 406 -> "NOT_ACCEPTABLE";
              case 415 -> "UNSUPPORTED_MEDIA_TYPE";
              default -> "INVALID_REQUEST";
            };
    if (!returnValidation
        && (exception instanceof BindException
            || exception instanceof HandlerMethodValidationException)) code = "VALIDATION_FAILED";
    if (exception instanceof org.springframework.beans.TypeMismatchException
        || exception instanceof org.springframework.web.bind.MissingServletRequestParameterException
        || exception instanceof org.springframework.web.bind.ServletRequestBindingException)
      code = "INVALID_PARAMETER";
    Failure failure = ErrorClassifier.of(code, exception);
    var entity = responses.response(servlet.getRequest(), status, failure, violations(exception));
    var servletResponse = servlet.getResponse();
    if (servletResponse != null && servletResponse.isCommitted()) return null;
    var merged = new HttpHeaders();
    merged.putAll(headers);
    merged.putAll(entity.getHeaders());
    return new ResponseEntity<>(entity.getBody(), merged, status);
  }

  private static HttpStatus status(String code) {
    return switch (code) {
      case "ORDER_NOT_FOUND", "ROUTE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
      case "CONFIG_NOT_READY",
          "RESOURCE_BUSY",
          "DATASTORE_UNAVAILABLE",
          "DATASTORE_TIMEOUT",
          "MESSAGE_BROKER_UNAVAILABLE",
          "INTERNAL_AUTH_UNAVAILABLE",
          "DEPENDENCY_TIMEOUT",
          "DEPENDENCY_UNAVAILABLE",
          "DEPENDENCY_FAILURE" ->
          HttpStatus.SERVICE_UNAVAILABLE;
      case "PROMOTION_NOT_FOUND",
          "PROMOTION_OUTSIDE_WINDOW",
          "PROMOTION_EXHAUSTED",
          "PROMOTION_ALREADY_USED",
          "ORDER_ALREADY_HAS_PROMOTION",
          "NON_POSITIVE_TOTAL" ->
          HttpStatus.UNPROCESSABLE_ENTITY;
      case "SEAT_UNAVAILABLE",
          "INSUFFICIENT_QUANTITY",
          "PURCHASE_LIMIT_EXCEEDED",
          "ORDER_EXPIRED",
          "ORDER_NOT_PAYABLE",
          "PAYMENT_IN_PROGRESS",
          "PRICE_FROZEN",
          "IDEMPOTENCY_CONFLICT",
          "CONCURRENT_MODIFICATION" ->
          HttpStatus.CONFLICT;
      case "UNAUTHENTICATED" -> HttpStatus.UNAUTHORIZED;
      case "ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }

  private static List<Problem.Violation> violations(Exception exception) {
    var result = new ArrayList<Problem.Violation>();
    if (exception instanceof BindException binding) {
      for (var error : binding.getAllErrors()) {
        if (result.size() == 50) break;
        String field =
            error instanceof FieldError fieldError
                ? safeField(binding.getTarget(), fieldError.getField())
                : "request";
        result.add(new Problem.Violation(field, violationCode(error.getCode())));
      }
    } else if (exception instanceof HandlerMethodValidationException validation
        && !validation.isForReturnValue()) {
      for (var parameter : validation.getParameterValidationResults()) {
        for (var error : parameter.getResolvableErrors()) {
          if (result.size() == 50) break;
          String field = parameter.getMethodParameter().getParameterName();
          if (field == null || !field.matches("[A-Za-z_$][A-Za-z0-9_$]{0,127}")) field = "request";
          String[] codes = error.getCodes();
          result.add(
              new Problem.Violation(
                  field,
                  violationCode(
                      codes == null || codes.length == 0 ? null : codes[codes.length - 1])));
        }
      }
      if (!validation.getCrossParameterValidationResults().isEmpty() && result.size() < 50)
        result.add(new Problem.Violation("request", "INVALID_VALUE"));
    }
    return List.copyOf(result);
  }

  private static String safeField(Object target, String field) {
    if (target == null
        || field.length() > 128
        || !field.matches(
            "[A-Za-z_$][A-Za-z0-9_$]*(\\[[0-9]+\\])?(\\.[A-Za-z_$][A-Za-z0-9_$]*(\\[[0-9]+\\])?)*"))
      return "request";
    try {
      var wrapper = new BeanWrapperImpl(target);
      String prefix = "";
      for (String part : field.split("\\.")) {
        prefix += (prefix.isEmpty() ? "" : ".") + part;
        Class<?> type = wrapper.getPropertyType(prefix);
        if (type == null || Map.class.isAssignableFrom(type)) return "request";
      }
      return wrapper.isReadableProperty(field) ? field : "request";
    } catch (org.springframework.beans.BeansException ignored) {
      return "request";
    }
  }

  private static String violationCode(String code) {
    if (code == null) return "INVALID_VALUE";
    return switch (code) {
      case "NotNull", "NotBlank", "NotEmpty" -> "REQUIRED";
      case "Email", "Pattern" -> "INVALID_FORMAT";
      case "Min",
          "Max",
          "Size",
          "Positive",
          "PositiveOrZero",
          "Negative",
          "NegativeOrZero",
          "DecimalMin",
          "DecimalMax",
          "Digits" ->
          "OUT_OF_RANGE";
      default -> "INVALID_VALUE";
    };
  }
}
