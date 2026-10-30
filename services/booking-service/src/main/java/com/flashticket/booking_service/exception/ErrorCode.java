package com.flashticket.booking_service.exception;

import static org.springframework.http.HttpStatus.*;

import feign.FeignException;
import feign.RetryableException;
import java.lang.reflect.InvocationTargetException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;

/** Danh mục mã lỗi, message an toàn và HTTP status dùng chung trong Booking. */
public enum ErrorCode {
  ISSUANCE_RESULT_NOT_READY(CONFLICT, "Đơn hàng chưa sẵn sàng nhận kết quả phát vé."),
  ISSUANCE_RESULT_CONFLICT(CONFLICT, "Kết quả phát vé không khớp đơn hàng đã nhận thanh toán."),
  PAYMENT_SNAPSHOT_MISMATCH(CONFLICT, "Thông tin thanh toán không khớp bản chốt tiền."),
  RESERVATION_DATA_CONFLICT(CONFLICT, "Dữ liệu giữ chỗ không nhất quán."),
  ORDER_NOT_FOUND(NOT_FOUND, "Không tìm thấy đơn hàng."),
  ORDER_NOT_CANCELLABLE(CONFLICT, "Đơn hàng không ở trạng thái có thể hủy."),
  CONFIG_NOT_READY(SERVICE_UNAVAILABLE, "Cấu hình bán vé chưa sẵn sàng."),
  SALES_CONFIGURATION_CONFLICT(CONFLICT, "Cấu hình bán vé mâu thuẫn với dữ liệu đã lưu."),
  MESSAGE_ID_CONFLICT(CONFLICT, "Mã thông điệp đã được dùng với nội dung khác."),
  SEAT_UNAVAILABLE(CONFLICT, "Ghế đã chọn hiện không còn khả dụng."),
  INSUFFICIENT_QUANTITY(CONFLICT, "Số lượng vé còn lại không đủ."),
  PURCHASE_LIMIT_EXCEEDED(CONFLICT, "Yêu cầu vượt quá giới hạn mua vé."),
  EVENT_NOT_ON_SALE(CONFLICT, "Sự kiện chưa mở bán hoặc đã đóng bán."),
  INVALID_SELECTION(UNPROCESSABLE_ENTITY, "Lựa chọn vé hoặc ghế không hợp lệ."),
  ORDER_AMOUNT_EXCEEDED(UNPROCESSABLE_ENTITY, "Tổng tiền của đơn hàng vượt giới hạn."),
  ORDER_EXPIRED(CONFLICT, "Đơn hàng đã hết thời hạn giữ chỗ."),
  ORDER_NOT_PAYABLE(CONFLICT, "Đơn hàng không ở trạng thái có thể thanh toán."),
  PAYMENT_IN_PROGRESS(CONFLICT, "Đơn hàng đang có yêu cầu thanh toán được xử lý."),
  PRICE_FROZEN(CONFLICT, "Thông tin giá của đơn hàng đã được cố định."),
  IDEMPOTENCY_CONFLICT(CONFLICT, "Khóa yêu cầu đã được dùng với nội dung khác."),
  PROMOTION_NOT_FOUND(UNPROCESSABLE_ENTITY, "Không tìm thấy mã khuyến mãi phù hợp."),
  PROMOTION_OUTSIDE_WINDOW(UNPROCESSABLE_ENTITY, "Mã khuyến mãi chưa có hiệu lực hoặc đã hết hạn."),
  PROMOTION_EXHAUSTED(UNPROCESSABLE_ENTITY, "Mã khuyến mãi đã hết lượt sử dụng."),
  PROMOTION_ALREADY_USED(UNPROCESSABLE_ENTITY, "Bạn đã sử dụng mã khuyến mãi này."),
  ORDER_ALREADY_HAS_PROMOTION(UNPROCESSABLE_ENTITY, "Đơn hàng đã có mã khuyến mãi."),
  NON_POSITIVE_TOTAL(UNPROCESSABLE_ENTITY, "Tổng tiền thanh toán phải lớn hơn không."),
  VALIDATION_FAILED(BAD_REQUEST, "Dữ liệu yêu cầu không hợp lệ."),
  INVALID_REQUEST(BAD_REQUEST, "Nội dung yêu cầu không hợp lệ."),
  UNAUTHENTICATED(UNAUTHORIZED, "Bạn cần xác thực để thực hiện yêu cầu này."),
  ACCESS_DENIED(FORBIDDEN, "Bạn không có quyền thực hiện yêu cầu này."),
  ROUTE_NOT_FOUND(NOT_FOUND, "Không tìm thấy tài nguyên được yêu cầu."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức yêu cầu không được hỗ trợ."),
  NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Định dạng phản hồi được yêu cầu không được hỗ trợ."),
  UNSUPPORTED_MEDIA_TYPE(
      HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Định dạng nội dung yêu cầu không được hỗ trợ."),
  CONCURRENT_MODIFICATION(CONFLICT, "Dữ liệu đã thay đổi trong lúc xử lý yêu cầu."),
  RESOURCE_BUSY(SERVICE_UNAVAILABLE, "Tài nguyên đang bận, vui lòng thử lại sau."),
  DATASTORE_UNAVAILABLE(SERVICE_UNAVAILABLE, "Hệ thống lưu trữ tạm thời chưa sẵn sàng."),
  DATASTORE_TIMEOUT(SERVICE_UNAVAILABLE, "Hệ thống lưu trữ phản hồi quá thời gian cho phép."),
  MESSAGE_BROKER_UNAVAILABLE(
      SERVICE_UNAVAILABLE, "Hệ thống trao đổi thông điệp tạm thời chưa sẵn sàng."),
  INTERNAL_AUTH_UNAVAILABLE(SERVICE_UNAVAILABLE, "Kết nối dịch vụ nội bộ tạm thời chưa sẵn sàng."),
  DEPENDENCY_TIMEOUT(SERVICE_UNAVAILABLE, "Dịch vụ phụ thuộc phản hồi quá thời gian cho phép."),
  DEPENDENCY_UNAVAILABLE(SERVICE_UNAVAILABLE, "Dịch vụ phụ thuộc tạm thời chưa sẵn sàng."),
  DEPENDENCY_FAILURE(SERVICE_UNAVAILABLE, "Không thể hoàn tất yêu cầu tới dịch vụ phụ thuộc."),
  INVALID_MESSAGE(INTERNAL_SERVER_ERROR, "Thông điệp không hợp lệ."),
  INVALID_MONEY_VALUE(INTERNAL_SERVER_ERROR, "Giá trị tiền không hợp lệ."),
  INVALID_VERSION(INTERNAL_SERVER_ERROR, "Phiên bản dữ liệu không hợp lệ."),
  INVALID_RESERVATION_DURATION(INTERNAL_SERVER_ERROR, "Thời gian giữ chỗ không hợp lệ."),
  INVALID_MESSAGE_PAYLOAD(INTERNAL_SERVER_ERROR, "Nội dung thông điệp không hợp lệ."),
  INVALID_MESSAGE_ENVELOPE(INTERNAL_SERVER_ERROR, "Thông tin bao thông điệp không hợp lệ."),
  INVALID_MESSAGE_SCHEMA(INTERNAL_SERVER_ERROR, "Phiên bản schema thông điệp không hợp lệ."),
  INVALID_MESSAGE_TYPE(INTERNAL_SERVER_ERROR, "Loại thông điệp không khớp nội dung."),
  MESSAGE_PUBLISH_FAILED(INTERNAL_SERVER_ERROR, "Không thể xác nhận việc gửi thông điệp."),
  INTERNAL_ERROR(INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi khi xử lý yêu cầu.");

  private final HttpStatus status;
  private final String message;

  /** Gắn HTTP status và message cố định cho mỗi mã lỗi. */
  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  /** Trả HTTP status tương ứng với mã lỗi. */
  public HttpStatus status() {
    return status;
  }

  /** Trả message an toàn cho client, không lấy nội dung từ exception kỹ thuật. */
  public String message() {
    return message;
  }

  /** Đổi lỗi nghiệp vụ hoặc lỗi thư viện thành mã lỗi thống nhất của Booking. */
  public static ErrorCode from(Throwable exception) {
    Throwable error = unwrap(exception);
    ErrorCode code =
        switch (error) {
          case BusinessException business -> business.getErrorCode();
          case OptimisticLockingFailureException ignored -> ErrorCode.CONCURRENT_MODIFICATION;
          case jakarta.persistence.OptimisticLockException ignored ->
              ErrorCode.CONCURRENT_MODIFICATION;
          case PessimisticLockingFailureException ignored -> ErrorCode.RESOURCE_BUSY;
          case QueryTimeoutException ignored -> ErrorCode.DATASTORE_TIMEOUT;
          case DataAccessResourceFailureException ignored -> ErrorCode.DATASTORE_UNAVAILABLE;
          case AmqpConnectException ignored -> ErrorCode.MESSAGE_BROKER_UNAVAILABLE;
          case OAuth2AuthorizationException ignored -> ErrorCode.INTERNAL_AUTH_UNAVAILABLE;
          case RetryableException ignored -> networkCode(error);
          case FeignException ignored -> ErrorCode.DEPENDENCY_FAILURE;
          case org.springframework.amqp.support.converter.MessageConversionException ignored ->
              ErrorCode.INVALID_MESSAGE;
          case org.springframework.messaging.converter.MessageConversionException ignored ->
              ErrorCode.INVALID_MESSAGE;
          case org.springframework.messaging.handler.annotation.support
                      .MethodArgumentNotValidException
                  ignored ->
              ErrorCode.INVALID_MESSAGE;
          case org.springframework.messaging.handler.annotation.support
                      .MethodArgumentTypeMismatchException
                  ignored ->
              ErrorCode.INVALID_MESSAGE;
          case ClassCastException ignored
              when exception instanceof ListenerExecutionFailedException ->
              ErrorCode.INVALID_MESSAGE;
          case NoSuchMethodException ignored
              when exception instanceof ListenerExecutionFailedException ->
              ErrorCode.INVALID_MESSAGE;
          case null, default -> ErrorCode.INTERNAL_ERROR;
        };
    return code;
  }

  /** Bỏ wrapper kỹ thuật để nhận diện lỗi bên trong; giới hạn duyệt 16 lớp. */
  private static Throwable unwrap(Throwable error) {
    for (int depth = 0; error != null && depth < 16; depth++) {
      if (!(error instanceof CompletionException
          || error instanceof ExecutionException
          || error instanceof InvocationTargetException
          || error instanceof AmqpRejectAndDontRequeueException
          || error instanceof ListenerExecutionFailedException)) return error;
      error = error.getCause();
    }
    return null;
  }

  /** Phân biệt lỗi gọi HTTP do timeout, mất kết nối hoặc nguyên nhân khác. */
  private static ErrorCode networkCode(Throwable error) {
    for (int depth = 0; error != null && depth < 16; depth++, error = error.getCause()) {
      if (error instanceof SocketTimeoutException || error instanceof HttpTimeoutException)
        return ErrorCode.DEPENDENCY_TIMEOUT;
      if (error instanceof ConnectException || error instanceof UnknownHostException)
        return ErrorCode.DEPENDENCY_UNAVAILABLE;
    }
    return ErrorCode.DEPENDENCY_FAILURE;
  }

  /** Chọn mã lỗi cho HTTP status do Spring cung cấp. */
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
