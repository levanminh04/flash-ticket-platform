package com.flashticket.ticket_service.exception;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.*;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.dao.*;
import org.springframework.http.*;

/** Safe technical errors for Ticket's scaffold boundaries. */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu không hợp lệ."),
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Nội dung yêu cầu không hợp lệ."),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Bạn cần xác thực để thực hiện yêu cầu này."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện yêu cầu này."),
  ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên được yêu cầu."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức yêu cầu không được hỗ trợ."),
  NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Định dạng phản hồi không được hỗ trợ."),
  UNSUPPORTED_MEDIA_TYPE(
      HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Định dạng nội dung không được hỗ trợ."),
  CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Dữ liệu đã thay đổi trong lúc xử lý yêu cầu."),
  DATA_CONFLICT(HttpStatus.CONFLICT, "Dữ liệu xung đột với ràng buộc lưu trữ."),
  RESOURCE_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "Tài nguyên đang bận, vui lòng thử lại sau."),
  DATASTORE_TIMEOUT(
      HttpStatus.SERVICE_UNAVAILABLE, "Hệ thống lưu trữ phản hồi quá thời gian cho phép."),
  DATASTORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Hệ thống lưu trữ tạm thời chưa sẵn sàng."),
  MESSAGE_BROKER_UNAVAILABLE(
      HttpStatus.SERVICE_UNAVAILABLE, "Hệ thống trao đổi thông điệp tạm thời chưa sẵn sàng."),
  INVALID_MESSAGE(HttpStatus.INTERNAL_SERVER_ERROR, "Thông điệp không hợp lệ."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi khi xử lý yêu cầu.");

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
      case OptimisticLockingFailureException ignored -> CONCURRENT_MODIFICATION;
      case jakarta.persistence.OptimisticLockException ignored -> CONCURRENT_MODIFICATION;
      case PessimisticLockingFailureException ignored -> RESOURCE_BUSY;
      case QueryTimeoutException ignored -> DATASTORE_TIMEOUT;
      case DataAccessResourceFailureException ignored -> DATASTORE_UNAVAILABLE;
      case DataIntegrityViolationException ignored -> DATA_CONFLICT;
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
