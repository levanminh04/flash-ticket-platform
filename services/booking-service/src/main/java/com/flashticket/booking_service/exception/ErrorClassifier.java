package com.flashticket.booking_service.exception;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import feign.FeignException;
import feign.RetryableException;
import java.lang.reflect.InvocationTargetException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;

/** Classifies evidence; transport handlers decide status, disposition and retry. */
public final class ErrorClassifier {
  private ErrorClassifier() {}

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Retain the original Throwable as diagnostic evidence for boundary logging.")
  public record Failure(String code, String message, String errorType, Throwable exception) {}

  public static Failure classify(Throwable exception) {
    Throwable error = relevant(exception);
    String code =
        switch (error) {
          case BusinessException business -> business.getErrorCode().name();
          case OptimisticLockingFailureException ignored -> "CONCURRENT_MODIFICATION";
          case jakarta.persistence.OptimisticLockException ignored -> "CONCURRENT_MODIFICATION";
          case PessimisticLockingFailureException ignored -> "RESOURCE_BUSY";
          case QueryTimeoutException ignored -> "DATASTORE_TIMEOUT";
          case DataAccessResourceFailureException ignored -> "DATASTORE_UNAVAILABLE";
          case AmqpConnectException ignored -> "MESSAGE_BROKER_UNAVAILABLE";
          case OAuth2AuthorizationException ignored -> "INTERNAL_AUTH_UNAVAILABLE";
          case RetryableException ignored -> networkCode(error);
          case FeignException ignored -> "DEPENDENCY_FAILURE";
          case org.springframework.amqp.support.converter.MessageConversionException ignored ->
              "INVALID_MESSAGE";
          case org.springframework.messaging.converter.MessageConversionException ignored ->
              "INVALID_MESSAGE";
          case org.springframework.messaging.handler.annotation.support
                      .MethodArgumentNotValidException
                  ignored ->
              "INVALID_MESSAGE";
          case org.springframework.messaging.handler.annotation.support
                      .MethodArgumentTypeMismatchException
                  ignored ->
              "INVALID_MESSAGE";
          case ClassCastException ignored
              when exception instanceof ListenerExecutionFailedException ->
              "INVALID_MESSAGE";
          case NoSuchMethodException ignored
              when exception instanceof ListenerExecutionFailedException ->
              "INVALID_MESSAGE";
          case null, default -> "INTERNAL_ERROR";
        };
    return of(code, exception);
  }

  public static Failure of(String knownCode, Throwable exception) {
    String message = businessMessage(knownCode);
    if (message == null) {
      message =
          switch (knownCode == null ? "" : knownCode) {
            case "VALIDATION_FAILED" -> "Dữ liệu yêu cầu không hợp lệ.";
            case "INVALID_REQUEST" -> "Nội dung yêu cầu không hợp lệ.";
            case "INVALID_PARAMETER" -> "Tham số yêu cầu không hợp lệ.";
            case "UNAUTHENTICATED" -> "Bạn cần xác thực để thực hiện yêu cầu này.";
            case "ACCESS_DENIED" -> "Bạn không có quyền thực hiện yêu cầu này.";
            case "ROUTE_NOT_FOUND" -> "Không tìm thấy tài nguyên được yêu cầu.";
            case "METHOD_NOT_ALLOWED" -> "Phương thức yêu cầu không được hỗ trợ.";
            case "NOT_ACCEPTABLE" -> "Định dạng phản hồi được yêu cầu không được hỗ trợ.";
            case "UNSUPPORTED_MEDIA_TYPE" -> "Định dạng nội dung yêu cầu không được hỗ trợ.";
            case "CONCURRENT_MODIFICATION" -> "Dữ liệu đã thay đổi trong lúc xử lý yêu cầu.";
            case "RESOURCE_BUSY" -> "Tài nguyên đang bận, vui lòng thử lại sau.";
            case "DATASTORE_UNAVAILABLE" -> "Hệ thống lưu trữ tạm thời chưa sẵn sàng.";
            case "DATASTORE_TIMEOUT" -> "Hệ thống lưu trữ phản hồi quá thời gian cho phép.";
            case "MESSAGE_BROKER_UNAVAILABLE" ->
                "Hệ thống trao đổi thông điệp tạm thời chưa sẵn sàng.";
            case "INTERNAL_AUTH_UNAVAILABLE" -> "Kết nối dịch vụ nội bộ tạm thời chưa sẵn sàng.";
            case "DEPENDENCY_TIMEOUT" -> "Dịch vụ phụ thuộc phản hồi quá thời gian cho phép.";
            case "DEPENDENCY_UNAVAILABLE" -> "Dịch vụ phụ thuộc tạm thời chưa sẵn sàng.";
            case "DEPENDENCY_FAILURE" -> "Không thể hoàn tất yêu cầu tới dịch vụ phụ thuộc.";
            case "INVALID_MESSAGE" -> "Thông điệp không hợp lệ.";
            case "MESSAGE_PUBLISH_FAILED" -> "Không thể xác nhận việc gửi thông điệp.";
            default -> null;
          };
    }
    String code = knownCode;
    if (message == null) {
      code = "INTERNAL_ERROR";
      message = "Đã xảy ra lỗi khi xử lý yêu cầu.";
    }
    if ("INVALID_PARAMETER".equals(code)) code = "INVALID_REQUEST";
    Throwable type = relevant(exception);
    return new Failure(code, message, type == null ? code : type.getClass().getName(), exception);
  }

  private static String businessMessage(String code) {
    if (code == null) return null;
    for (BusinessErrorCode business : BusinessErrorCode.values()) {
      if (business.name().equals(code)) return business.message();
    }
    return null;
  }

  private static Throwable relevant(Throwable error) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (int depth = 0; error != null && depth < 16 && seen.add(error); depth++) {
      if (!(error instanceof CompletionException
          || error instanceof ExecutionException
          || error instanceof InvocationTargetException
          || error instanceof ListenerExecutionFailedException)) return error;
      error = error.getCause();
    }
    return null;
  }

  private static String networkCode(Throwable error) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (int depth = 0;
        error != null && depth < 16 && seen.add(error);
        depth++, error = error.getCause()) {
      if (error instanceof SocketTimeoutException || error instanceof HttpTimeoutException)
        return "DEPENDENCY_TIMEOUT";
      if (error instanceof ConnectException || error instanceof UnknownHostException)
        return "DEPENDENCY_UNAVAILABLE";
    }
    return "DEPENDENCY_FAILURE";
  }
}
