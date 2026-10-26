package com.flashticket.payment_service.service.start;

import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;

/** A future real Booking client must run outside the Payment transaction. */
public interface BookingPaymentGuard {
  /** Null means payable; infrastructure failure must throw and remain retryable. */
  String rejection(PurchaseSnapshot purchase);
}
