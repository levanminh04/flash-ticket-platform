package com.flashticket.payment_service.exception;

import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.response.*;
import com.flashticket.payment_service.web.*;
import jakarta.servlet.http.HttpServletRequest;
import java.beans.PropertyEditorSupport;
import java.util.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class PaymentExceptionHandler {
  PaymentProblemFactory problems;

  @InitBinder
  void strictIds(WebDataBinder binder) {
    binder.registerCustomEditor(
        UUID.class,
        new PropertyEditorSupport() {
          @Override
          public void setAsText(String text) {
            setValue(PaymentCorrelationFilter.parseUuid(text));
          }
        });
  }

  @ExceptionHandler(PaymentException.class)
  ResponseEntity<ApiResponse<Problem>> payment(PaymentException e, HttpServletRequest request) {
    return ResponseEntity.status(e.status()).body(problem(request, e.errorCode(), List.of()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiResponse<Problem>> invalid(
      MethodArgumentTypeMismatchException e, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            problem(
                request,
                PaymentErrorCode.INVALID_REQUEST,
                List.of(new Violation(e.getName(), "INVALID_UUID"))));
  }

  @ExceptionHandler(ContractValidationException.class)
  ResponseEntity<ApiResponse<Problem>> contract(
      ContractValidationException e, HttpServletRequest request) {
    return ResponseEntity.unprocessableContent()
        .body(
            problem(
                request,
                PaymentErrorCode.INVALID_REQUEST,
                List.of(new Violation(e.field(), e.code()))));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Problem>> unexpected(Exception e, HttpServletRequest request) {
    log.error(
        "Payment request failed correlationId={} exceptionType={}",
        PaymentCorrelationFilter.correlationId(request),
        e.getClass().getSimpleName());
    return ResponseEntity.internalServerError()
        .body(problem(request, PaymentErrorCode.INTERNAL_ERROR, List.of()));
  }

  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  ResponseEntity<ApiResponse<Problem>> denied(HttpServletRequest request) {
    return ResponseEntity.status(403)
        .body(problem(request, PaymentErrorCode.ACCESS_DENIED, List.of()));
  }

  @ExceptionHandler({
    org.springframework.web.bind.MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  ResponseEntity<ApiResponse<Problem>> invalidBody(HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(problem(request, PaymentErrorCode.INVALID_REQUEST, List.of()));
  }

  private ApiResponse<Problem> problem(
      HttpServletRequest request, PaymentErrorCode code, List<Violation> violations) {
    return ApiResponse.failure(problems.problem(request, code, violations));
  }
}
