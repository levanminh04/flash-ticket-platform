package com.flashticket.payment_service.dto.response;

import java.util.List;

public record PageResponse<T>(
    List<T> items, int page, int size, long totalElements, int totalPages) {
  public PageResponse {
    if (items != null) items = List.copyOf(items);
  }

  public List<T> items() {
    return items == null ? null : List.copyOf(items);
  }
}
