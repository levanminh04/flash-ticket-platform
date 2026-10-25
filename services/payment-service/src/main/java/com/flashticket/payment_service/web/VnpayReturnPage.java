package com.flashticket.payment_service.web;

/** Fixed presentation only; Return never proves or changes payment state. */
public final class VnpayReturnPage {
  private VnpayReturnPage() {}

  public static String render(boolean authentic) {
    var text =
        authentic ? "Đang kiểm tra kết quả thanh toán" : "Không xác minh được thông tin trả về";
    return "<!doctype html><html lang=\"vi\"><meta charset=\"utf-8\"><title>Thanh toán</title><p>"
        + text
        + "</p></html>";
  }
}
