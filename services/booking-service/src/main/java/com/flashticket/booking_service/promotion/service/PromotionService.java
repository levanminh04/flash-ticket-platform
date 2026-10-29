package com.flashticket.booking_service.promotion.service;

import com.flashticket.booking_service.dto.request.ApplyPromotionRequest;
import com.flashticket.booking_service.dto.response.OrderView;
import java.util.UUID;

public interface PromotionService {
  OrderView applyPromotionToOrder(String buyerSubject, UUID orderId, ApplyPromotionRequest request);
}
