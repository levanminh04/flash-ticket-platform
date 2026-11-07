package com.flashticket.payment_service.dto.response;

import lombok.Builder;

@Builder
public record ApiResponse<T>(T data, boolean success, String message) {
  public static <T> ApiResponse<T> success(T data, String message) {
    return ApiResponse.<T>builder().data(data).success(true).message(message).build();
  }

  public static ApiResponse<Problem> failure(Problem problem) {
    return ApiResponse.<Problem>builder()
        .data(problem)
        .success(false)
        .message(problem.message())
        .build();
  }
}
