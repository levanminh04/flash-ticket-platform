package com.flashticket.payment_service.gateway.vnpay;

import com.flashticket.payment_service.gateway.*;
import java.math.*;
import java.time.*;
import java.time.format.*;
import java.util.UUID;

/** Shared exact-money, reference and provider-time conversions. */
final class VnpayWireFormat {
  static final DateTimeFormatter WIRE_TIME =
      DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);
  static final ZoneOffset WIRE_ZONE = ZoneOffset.ofHours(7);

  private VnpayWireFormat() {}

  static void validateMerchant(PaymentContext context, String merchantAccount) {
    if (!merchantAccount.equals(context.merchantAccount()))
      throw new GatewayException(GatewayException.Code.MERCHANT_MISMATCH);
  }

  static String reference(UUID id) {
    return id.toString().replace("-", "");
  }

  static String wireTime(Instant time) {
    return WIRE_TIME.format(time.atOffset(WIRE_ZONE));
  }

  static Instant parseTime(String value) {
    try {
      return LocalDateTime.parse(value, WIRE_TIME).toInstant(WIRE_ZONE);
    } catch (DateTimeException ex) {
      throw VnpayCodec.invalid();
    }
  }

  static String amountWire(BigDecimal amount) {
    try {
      var vnd = amount.toBigIntegerExact();
      var wire = vnd.multiply(BigInteger.valueOf(100));
      if (vnd.signum() <= 0 || wire.toString().length() > 12) throw new ArithmeticException();
      return wire.toString();
    } catch (ArithmeticException ex) {
      throw new GatewayException(GatewayException.Code.AMOUNT_MISMATCH);
    }
  }

  static BigDecimal amountVnd(String wire) {
    if (wire == null || !wire.matches("[0-9]{1,12}"))
      throw new GatewayException(GatewayException.Code.AMOUNT_MISMATCH);
    var value = new BigInteger(wire);
    if (value.signum() <= 0 || value.remainder(BigInteger.valueOf(100)).signum() != 0)
      throw new GatewayException(GatewayException.Code.AMOUNT_MISMATCH);
    return new BigDecimal(value.divide(BigInteger.valueOf(100)));
  }
}
