package com.flashticket.user_service.exception;

import static org.springframework.http.HttpStatus.*;

import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

/** Mã lỗi kỹ thuật, message an toàn và HTTP status của User. */
public enum ErrorCode {
  VALIDATION_FAILED(BAD_REQUEST, "Dữ liệu yêu cầu không hợp lệ."),
  INVALID_REQUEST(BAD_REQUEST, "Nội dung yêu cầu không hợp lệ."),
  UNAUTHENTICATED(UNAUTHORIZED, "Bạn cần xác thực để thực hiện yêu cầu này."),
  ACCESS_DENIED(FORBIDDEN, "Bạn không có quyền thực hiện yêu cầu này."),
  ROUTE_NOT_FOUND(NOT_FOUND, "Không tìm thấy tài nguyên được yêu cầu."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức yêu cầu không được hỗ trợ."),
  NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Định dạng phản hồi được yêu cầu không được hỗ trợ."),
  UNSUPPORTED_MEDIA_TYPE(
      HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Định dạng nội dung yêu cầu không được hỗ trợ."),
  DATA_CONFLICT(CONFLICT, "Dữ liệu bị trùng hoặc xung đột."),
  CONCURRENT_MODIFICATION(CONFLICT, "Dữ liệu đã thay đổi trong lúc xử lý yêu cầu."),
  DATASTORE_UNAVAILABLE(SERVICE_UNAVAILABLE, "Hệ thống lưu trữ tạm thời chưa sẵn sàng."),
  DATASTORE_TIMEOUT(SERVICE_UNAVAILABLE, "Hệ thống lưu trữ phản hồi quá thời gian cho phép."),
  MESSAGE_BROKER_UNAVAILABLE(
      SERVICE_UNAVAILABLE, "Hệ thống trao đổi thông điệp tạm thời chưa sẵn sàng."),
  INVALID_MESSAGE(INTERNAL_SERVER_ERROR, "Thông điệp không hợp lệ."),
  INTERNAL_ERROR(INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi khi xử lý yêu cầu.");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  public HttpStatus status() {
    return status;
  }

  public String message() {
    return message;
  }

  public static ErrorCode from(Throwable exception) {
    Throwable error = unwrap(exception);
    return switch (error) {
      case BusinessException business -> business.getErrorCode();
      case AuthenticationException ignored -> UNAUTHENTICATED;
      case AccessDeniedException ignored -> ACCESS_DENIED;
      case DuplicateKeyException ignored -> DATA_CONFLICT;
      case OptimisticLockingFailureException ignored -> CONCURRENT_MODIFICATION;
      case QueryTimeoutException ignored -> DATASTORE_TIMEOUT;
      case MongoExecutionTimeoutException ignored -> DATASTORE_TIMEOUT;
      case MongoSocketException ignored -> DATASTORE_UNAVAILABLE;
      case MongoTimeoutException ignored -> DATASTORE_UNAVAILABLE;
      case DataAccessResourceFailureException ignored -> DATASTORE_UNAVAILABLE;
      case AmqpConnectException ignored -> MESSAGE_BROKER_UNAVAILABLE;
      case org.springframework.amqp.support.converter.MessageConversionException ignored ->
          INVALID_MESSAGE;
      case org.springframework.messaging.converter.MessageConversionException ignored ->
          INVALID_MESSAGE;
      case org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException
              ignored ->
          INVALID_MESSAGE;
      case org.springframework.messaging.handler.annotation.support
                  .MethodArgumentTypeMismatchException
              ignored ->
          INVALID_MESSAGE;
      case ClassCastException ignored when exception instanceof ListenerExecutionFailedException ->
          INVALID_MESSAGE;
      case NoSuchMethodException ignored
          when exception instanceof ListenerExecutionFailedException ->
          INVALID_MESSAGE;
      case null, default -> INTERNAL_ERROR;
    };
  }

  private static Throwable unwrap(Throwable error) {
    for (int depth = 0; error != null && depth < 16; depth++) {
      if (!(error instanceof CompletionException
          || error instanceof ExecutionException
          || error instanceof InvocationTargetException
          || error instanceof ListenerExecutionFailedException)) return error;
      error = error.getCause();
    }
    return null;
  }

  public static ErrorCode fromStatus(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> INVALID_REQUEST;
      case 401 -> UNAUTHENTICATED;
      case 403 -> ACCESS_DENIED;
      case 404 -> ROUTE_NOT_FOUND;
      case 405 -> METHOD_NOT_ALLOWED;
      case 406 -> NOT_ACCEPTABLE;
      case 415 -> UNSUPPORTED_MEDIA_TYPE;
      default -> INTERNAL_ERROR;
    };
  }
}
