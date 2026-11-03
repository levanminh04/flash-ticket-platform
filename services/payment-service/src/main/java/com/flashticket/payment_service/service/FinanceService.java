package com.flashticket.payment_service.service;

import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.FinanceConfiguration;
import com.flashticket.payment_service.dto.request.ReconciliationRequest;
import com.flashticket.payment_service.dto.response.EventFinancialSummary;
import com.flashticket.payment_service.dto.response.EventReconciliationView;
import java.util.UUID;

public interface FinanceService {
  void recordFinanceConfiguration(Envelope<FinanceConfiguration> incomingEnvelope);

  EventFinancialSummary getFinancialSummary(UUID eventId);

  EventReconciliationView getReconciliation(UUID eventId, String actorSubject, boolean admin);

  EventReconciliationView confirmReconciliation(
      UUID eventId, ReconciliationRequest request, String adminSubject);

  EventReconciliationView markEventPaid(UUID eventId, String adminSubject);
}
