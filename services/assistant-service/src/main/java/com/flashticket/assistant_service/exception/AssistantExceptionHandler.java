package com.flashticket.assistant_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.json.JsonMapper;

@RestControllerAdvice
@RequiredArgsConstructor
public class AssistantExceptionHandler {
  private final JsonMapper jsonMapper;

  @ExceptionHandler(AssistantProblem.class)
  public ResponseEntity<ProblemDetail> handleAssistantProblem(AssistantProblem error) {
    return ResponseEntity.status(error.getStatus()).cacheControl(CacheControl.noStore())
        .body(problem(error.getStatus(), error.getCode(), error.getSafeDetail()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException error) {
    return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
        .body(problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Thông tin gửi lên không hợp lệ."));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException error) {
    return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
        .body(problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Không đọc được yêu cầu."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception error) {
    // Không đưa exception message hoặc rejected value vào response/log.
    return ResponseEntity.internalServerError().cacheControl(CacheControl.noStore())
        .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Không thể xử lý yêu cầu lúc này."));
  }

  private ProblemDetail problem(HttpStatus status, String code, String safeDetail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, safeDetail);
    problem.setTitle(code);
    problem.setProperty("code", code);
    return problem;
  }

  private void writeProblem(HttpServletResponse response, HttpStatus status, String code, String safeDetail) throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setHeader("Cache-Control", "no-store");
    if (status == HttpStatus.UNAUTHORIZED) response.setHeader("WWW-Authenticate", "Bearer");
    if (status == HttpStatus.FORBIDDEN) response.setHeader("WWW-Authenticate", "Bearer error=\"insufficient_scope\"");
    jsonMapper.writeValue(response.getOutputStream(), problem(status, code, safeDetail));
  }

  public void handleUnauthenticated(HttpServletRequest request, HttpServletResponse response, AuthenticationException error) throws IOException {
    writeProblem(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Vui lòng đăng nhập lại.");
  }

  public void handleAccessDenied(HttpServletRequest request, HttpServletResponse response, AccessDeniedException error) throws IOException {
    writeProblem(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền sử dụng chức năng này.");
  }
}
