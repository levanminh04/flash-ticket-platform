package com.flashticket.payment_service.validation;

import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.common.ContractViolations;
import com.flashticket.payment_service.dto.snapshot.PurchaseMode;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class PurchaseSnapshotValidator {
  private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999999999999");
  Validator validator;

  public PurchaseSnapshotValidator(Validator validator) {
    this.validator = Objects.requireNonNull(validator);
  }

  public void validate(PurchaseSnapshot purchase) {
    if (purchase == null) fail("$", "CONSTRAINT_VIOLATION");
    ContractViolations.firstPath(validator.validate(purchase))
        .ifPresent(path -> fail("$." + path, "CONSTRAINT_VIOLATION"));
    boolean quantity = purchase.mode() == PurchaseMode.QUANTITY;
    if (quantity != (purchase.sectorId() == null)) fail("$.sectorId", "INVALID_SELECTION");
    var itemIds = new HashSet<UUID>();
    var seatIds = new HashSet<UUID>();
    BigDecimal subtotal = BigDecimal.ZERO;
    long count = 0;
    Boolean seated = null;
    for (int i = 0; i < purchase.items().size(); i++) {
      var item = purchase.items().get(i);
      String path = "$.items[" + i + "]";
      if (!itemIds.add(item.orderItemId())) fail(path + ".orderItemId", "DUPLICATE_ID");
      var amount = new BigDecimal(item.unitPrice()).multiply(BigDecimal.valueOf(item.quantity()));
      bound(amount, path + ".lineSubtotal");
      if (amount.compareTo(new BigDecimal(item.lineSubtotal())) != 0)
        fail(path + ".lineSubtotal", "LINE_TOTAL_MISMATCH");
      subtotal = subtotal.add(amount);
      bound(subtotal, "$.subtotal");
      count += item.quantity();
      if (count > Integer.MAX_VALUE) fail("$.items", "TICKET_COUNT_OUT_OF_RANGE");
      if (!Objects.equals(item.sectorId(), purchase.sectorId()))
        fail(path + ".sectorId", "INVALID_SELECTION");
      boolean hasSeats = !item.seats().isEmpty();
      if (quantity && hasSeats) fail(path + ".seats", "INVALID_SELECTION");
      if (!quantity) {
        if (seated == null) seated = hasSeats;
        if (seated != hasSeats || hasSeats && item.seats().size() != item.quantity())
          fail(path + ".seats", "INVALID_SELECTION");
      }
      for (int j = 0; j < item.seats().size(); j++) {
        if (!seatIds.add(item.seats().get(j).seatId()))
          fail(path + ".seats[" + j + "].seatId", "DUPLICATE_ID");
      }
    }
    if (subtotal.compareTo(new BigDecimal(purchase.subtotal())) != 0)
      fail("$.subtotal", "SUBTOTAL_MISMATCH");
    var discount = new BigDecimal(purchase.discount());
    if (discount.compareTo(subtotal) > 0 || purchase.promotion() == null && discount.signum() != 0)
      fail("$.discount", "DISCOUNT_MISMATCH");
    if (purchase.promotion() != null
        && discount.compareTo(new BigDecimal(purchase.promotion().discount())) != 0)
      fail("$.promotion.discount", "DISCOUNT_MISMATCH");
    if (subtotal.subtract(discount).compareTo(new BigDecimal(purchase.total())) != 0)
      fail("$.total", "TOTAL_MISMATCH");
    if (!purchase.frozenAt().isBefore(purchase.expiresAt()))
      fail("$.frozenAt", "INVALID_TIME_RANGE");
    if (!purchase.eventStartsAt().isBefore(purchase.eventEndsAt()))
      fail("$.eventStartsAt", "INVALID_TIME_RANGE");
  }

  public int expectedTicketCount(PurchaseSnapshot purchase) {
    validate(purchase);
    return purchase.items().stream().mapToInt(i -> i.quantity()).sum();
  }

  private static void bound(BigDecimal amount, String path) {
    if (amount.compareTo(MAX_MONEY) > 0) fail(path, "AMOUNT_OUT_OF_RANGE");
  }

  private static void fail(String field, String code) {
    throw new ContractValidationException(field, code);
  }
}
