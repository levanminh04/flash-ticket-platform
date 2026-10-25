package com.flashticket.payment_service.exception;

import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.response.*;
import com.flashticket.payment_service.web.*;
import jakarta.servlet.http.HttpServletRequest;
import java.beans.PropertyEditorSupport;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public final class PaymentExceptionHandler {
  private final PaymentProblemFactory problems;

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
  ResponseEntity<Problem> payment(PaymentException e, HttpServletRequest request) {
    return ResponseEntity.status(e.status())
        .body(problems.problem(request, e.errorCode(), List.of()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<Problem> invalid(
      MethodArgumentTypeMismatchException e, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            problems.problem(
                request,
                PaymentErrorCode.INVALID_REQUEST,
                List.of(new Violation(e.getName(), "INVALID_UUID"))));
  }

  @ExceptionHandler(ContractValidationException.class)
  ResponseEntity<Problem> contract(ContractValidationException e, HttpServletRequest request) {
    return ResponseEntity.unprocessableContent()
        .body(
            problems.problem(
                request,
                PaymentErrorCode.INVALID_REQUEST,
                List.of(new Violation(e.field(), e.code()))));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Problem> unexpected(Exception e, HttpServletRequest request) {
    log.error(
        "Payment request failed correlationId={} exceptionType={}",
        PaymentCorrelationFilter.correlationId(request),
        e.getClass().getSimpleName());
    return ResponseEntity.internalServerError()
        .body(problems.problem(request, PaymentErrorCode.INTERNAL_ERROR, List.of()));
  }
}
