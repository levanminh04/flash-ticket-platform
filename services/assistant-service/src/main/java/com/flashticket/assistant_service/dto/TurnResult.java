package com.flashticket.assistant_service.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TurnResult {
  private String answer;
  private String contextJson;
  private PurchaseDraft draft;
  private List<EventView> events;
  private List<OrderReadView> orders;
}
