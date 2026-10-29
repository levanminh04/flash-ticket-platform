package com.flashticket.payment_service.exception;

import com.flashticket.payment_service.dto.response.Problem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.util.DisconnectedClientHelper;

/** Bắt exception tập trung, trả JSON lỗi cho client và ghi log phục vụ tra cứu. */
@Slf4j
@RestControllerAdvice
public final class GlobalExceptionHandler {
  /** Trả lỗi nghiệp vụ theo code, message và HTTP status đã khai báo. */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<Problem> handleBusiness(
      BusinessException exception, HttpServletRequest request) {
    ErrorCode code = exception.getErrorCode();
    return problem(request, code.status(), code, exception, List.of());
  }

  /** Tách lỗi validation đầu vào (400) với lỗi dữ liệu trả về của server (500). */
  @ExceptionHandler({BindException.class, HandlerMethodValidationException.class})
  public ResponseEntity<Problem> handleValidation(Exception exception, HttpServletRequest request) {
    boolean invalidReturn =
        exception instanceof HandlerMethodValidationException validation
            && validation.isForReturnValue();
    ErrorCode code = invalidReturn ? ErrorCode.INTERNAL_ERROR : ErrorCode.VALIDATION_FAILED;
    return problem(
        request, code.status(), code, exception, invalidReturn ? List.of() : violations(exception));
  }

  /** Xử lý lỗi security, request và lỗi bất ngờ; giữ HTTP headers cần thiết của Spring. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Problem> handleException(
      Exception exception, HttpServletRequest request, HttpServletResponse response) {
    if (DisconnectedClientHelper.isClientDisconnectedException(exception)) {
      log.debug("HTTP client disconnected.");
      return null;
    }
    ErrorCode code = ErrorCode.from(exception);
    HttpStatusCode status = code.status();
    HttpHeaders headers = new HttpHeaders();
    if (exception instanceof AuthenticationException) {
      code = ErrorCode.UNAUTHENTICATED;
      status = code.status();
    } else if (exception instanceof AccessDeniedException) {
      code = ErrorCode.ACCESS_DENIED;
      status = code.status();
    } else if (exception instanceof HttpMessageNotReadableException
        || exception instanceof TypeMismatchException) {
      code = ErrorCode.INVALID_REQUEST;
      status = code.status();
    } else if (exception instanceof ErrorResponse frameworkError) {
      status = frameworkError.getStatusCode();
      code = ErrorCode.fromStatus(status);
      headers.putAll(frameworkError.getHeaders());
    }
    var entity = problem(request, status, code, exception, List.of());
    if (response.isCommitted()) return null;
    headers.putAll(entity.getHeaders());
    return new ResponseEntity<>(entity.getBody(), headers, status);
  }

  /** Tạo response lỗi kèm correlation ID và ghi lý do an toàn vào log local. */
  private static ResponseEntity<Problem> problem(
      HttpServletRequest request,
      HttpStatusCode status,
      ErrorCode code,
      Throwable exception,
      List<Problem.Violation> violations) {
    UUID correlation = UUID.randomUUID();
    try {
      String supplied = request.getHeader("X-Correlation-Id");
      if (supplied != null
          && supplied.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
          && Collections.list(request.getHeaders("X-Correlation-Id")).size() == 1)
        correlation = UUID.fromString(supplied);
    } catch (RequestRejectedException ignored) {
      // Header truy vết sai không được làm mất response của lỗi đang xử lý.
    }
    boolean rejected = status.is4xxClientError();
    String operation =
        switch (code) {
          case UNAUTHENTICATED -> "security.authenticate";
          case ACCESS_DENIED -> "security.authorize";
          default -> "http.request";
        };
    String method =
        switch (request.getMethod()) {
          case "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE" ->
              request.getMethod();
          default -> "OTHER";
        };
    // Giới hạn MDC trong lần ghi lỗi để correlation header/body/log luôn khớp.
    String previousCorrelation = MDC.get("correlationId");
    MDC.put("correlationId", correlation.toString());
    try {
      var entry =
          log.atLevel(rejected ? Level.INFO : Level.ERROR)
              .addKeyValue(
                  "eventName", rejected ? "payment.request.rejected" : "payment.request.failed")
              .addKeyValue("operation", operation)
              .addKeyValue("outcome", rejected ? "REJECTED" : "FAILED")
              .addKeyValue("httpMethod", method)
              .addKeyValue("httpStatus", status.value())
              .addKeyValue("errorCode", code.name())
              .addKeyValue("errorType", exception.getClass().getName());
      if (!rejected) entry = entry.setCause(exception);
      entry.log(rejected ? "HTTP request rejected: {}" : "HTTP request failed: {}", code.message());
    } finally {
      if (previousCorrelation == null) MDC.remove("correlationId");
      else MDC.put("correlationId", previousCorrelation);
    }
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Correlation-Id", correlation.toString())
        .body(new Problem(code.name(), code.message(), correlation, List.copyOf(violations)));
  }

  /** Gom tối đa 50 lỗi validation thành field và mã lỗi, không đưa giá trị input vào response. */
  private static List<Problem.Violation> violations(Exception exception) {
    var result = new ArrayList<Problem.Violation>();
    if (exception instanceof BindException binding) {
      for (var error : binding.getAllErrors()) {
        if (result.size() == 50) break;
        String field =
            error instanceof FieldError fieldError ? safeField(fieldError.getField()) : "request";
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

  /** Chỉ giữ tên field gốc, tránh lộ key Map hoặc đường dẫn do client tạo. */
  private static String safeField(String path) {
    String root = path.split("[.\\[]", 2)[0];
    return root.matches("[A-Za-z_$][A-Za-z0-9_$]{0,127}") ? root : "request";
  }

  /** Đổi tên constraint validation thành mã lỗi ngắn, ổn định cho client. */
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
