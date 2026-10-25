package com.flashticket.payment_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnpayAck(
    @JsonProperty("RspCode") String code, @JsonProperty("Message") String message) {}
