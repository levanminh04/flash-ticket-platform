package com.flashticket.payment_service.service.vnpay;

import com.flashticket.payment_service.dto.response.VnpayAck;
import com.flashticket.payment_service.gateway.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Orchestrates provider callbacks; the durable handler must commit before returning. */
@Service
@ConditionalOnProperty(
    prefix = "flashticket.payment.vnpay",
    name = "callbacks-enabled",
    havingValue = "true")
public final class VnpayCallbackService {
  private final PaymentGateway gateway;
  private final VerifiedPaymentHandler handler;

  public VnpayCallbackService(PaymentGateway gateway, VerifiedPaymentHandler handler) {
    this.gateway = gateway;
    this.handler = handler;
  }

  public VnpayAck handleNotification(String rawQuery) {
    String code;
    try {
      var notification = gateway.verifyNotification(rawQuery);
      var outcome = handler.handle(notification);
      if (outcome == null) throw new GatewayException(GatewayException.Code.UNAVAILABLE);
      code = outcome == VerifiedPaymentHandler.Outcome.ALREADY_RECORDED ? "02" : "00";
    } catch (GatewayException ex) {
      code = ex.code().ackCode();
    } catch (RuntimeException ex) {
      code = "99";
    }
    return new VnpayAck(code, message(code));
  }

  public boolean isReturnAuthentic(String rawQuery) {
    try {
      gateway.verifyNotification(rawQuery);
      return true;
    } catch (RuntimeException ex) {
      return false;
    }
  }

  private static String message(String code) {
    return switch (code) {
      case "00" -> "Confirm Success";
      case "02" -> "Order already confirmed";
      case "01" -> "Order not found";
      case "04" -> "Invalid amount";
      case "97" -> "Invalid signature";
      default -> "Unable to process notification";
    };
  }
}
