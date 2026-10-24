package com.flashticket.booking_service.exception;

public enum BusinessErrorCode {
  ORDER_NOT_FOUND("Không tìm thấy đơn hàng."),
  CONFIG_NOT_READY("Cấu hình bán vé chưa sẵn sàng."),
  SEAT_UNAVAILABLE("Ghế đã chọn hiện không còn khả dụng."),
  INSUFFICIENT_QUANTITY("Số lượng vé còn lại không đủ."),
  PURCHASE_LIMIT_EXCEEDED("Yêu cầu vượt quá giới hạn mua vé."),
  ORDER_EXPIRED("Đơn hàng đã hết thời hạn giữ chỗ."),
  ORDER_NOT_PAYABLE("Đơn hàng không ở trạng thái có thể thanh toán."),
  PAYMENT_IN_PROGRESS("Đơn hàng đang có yêu cầu thanh toán được xử lý."),
  PRICE_FROZEN("Thông tin giá của đơn hàng đã được cố định."),
  IDEMPOTENCY_CONFLICT("Khóa yêu cầu đã được dùng với nội dung khác."),
  PROMOTION_NOT_FOUND("Không tìm thấy mã khuyến mãi phù hợp."),
  PROMOTION_OUTSIDE_WINDOW("Mã khuyến mãi chưa có hiệu lực hoặc đã hết hạn."),
  PROMOTION_EXHAUSTED("Mã khuyến mãi đã hết lượt sử dụng."),
  PROMOTION_ALREADY_USED("Bạn đã sử dụng mã khuyến mãi này."),
  ORDER_ALREADY_HAS_PROMOTION("Đơn hàng đã có mã khuyến mãi."),
  NON_POSITIVE_TOTAL("Tổng tiền thanh toán phải lớn hơn không.");

  private final String message;

  BusinessErrorCode(String message) {
    this.message = message;
  }

  public String message() {
    return message;
  }
}
