package com.flashticket.booking_service.promotion.service.impl;

import com.flashticket.booking_service.promotion.repository.PromotionInventoryRepository;
import com.flashticket.booking_service.promotion.repository.PromotionUsageRepository;
import com.flashticket.booking_service.promotion.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {
  private final PromotionInventoryRepository promotionInventoryRepository;
  private final PromotionUsageRepository promotionUsageRepository;
}
