package com.flashticket.payment_service.dto.snapshot;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.enums.*;
import com.flashticket.payment_service.enums.Currency;
import com.flashticket.payment_service.validation.ContractRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class PurchaseSnapshot {
  @NotNull
  @Min(1)
  @Max(1)
  private final Integer schemaVersion;

  @NotNull private final UUID orderId;
  @NotNull private final UUID eventId;
  @NotBlank private final String buyerSubject;

  @NotBlank
  @Email
  @Size(max = 254)
  private final String customerEmail;

  @NotBlank
  @Pattern(regexp = "^[1-9][0-9]{0,18}$")
  @DecimalMax("9223372036854775807")
  private final String sourceConfigVersion;

  @NotBlank
  @Pattern(regexp = "^[1-9][0-9]{0,18}$")
  @DecimalMax("9223372036854775807")
  private final String sourceOrderVersion;

  @NotNull private final Instant frozenAt;
  @NotNull private final Instant expiresAt;
  @NotNull private final Currency currency;
  @NotBlank private final String eventTitle;
  @NotNull private final Instant eventStartsAt;
  @NotNull private final Instant eventEndsAt;
  private final String venueName;
  @NotNull private final SalesMode mode;
  private final UUID sectorId;

  @NotBlank
  @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$")
  private final String subtotal;

  @NotBlank
  @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$")
  private final String discount;

  @NotBlank
  @Pattern(regexp = "^[1-9][0-9]{0,18}$")
  private final String total;

  @Valid private final AppliedPromotionSnapshot promotion;
  @NotNull @Valid private final List<@NotNull Item> items;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public PurchaseSnapshot(
      @JsonProperty(value = "schemaVersion", required = true) Integer schemaVersion,
      @JsonProperty(value = "orderId", required = true) UUID orderId,
      @JsonProperty(value = "eventId", required = true) UUID eventId,
      @JsonProperty(value = "buyerSubject", required = true) String buyerSubject,
      @JsonProperty(value = "customerEmail", required = true) String customerEmail,
      @JsonProperty(value = "sourceConfigVersion", required = true) String sourceConfigVersion,
      @JsonProperty(value = "sourceOrderVersion", required = true) String sourceOrderVersion,
      @JsonProperty(value = "frozenAt", required = true) Instant frozenAt,
      @JsonProperty(value = "expiresAt", required = true) Instant expiresAt,
      @JsonProperty(value = "currency", required = true) Currency currency,
      @JsonProperty(value = "eventTitle", required = true) String eventTitle,
      @JsonProperty(value = "eventStartsAt", required = true) Instant eventStartsAt,
      @JsonProperty(value = "eventEndsAt", required = true) Instant eventEndsAt,
      @JsonProperty(value = "venueName", required = true) String venueName,
      @JsonProperty(value = "mode", required = true) SalesMode mode,
      @JsonProperty(value = "sectorId", required = true) UUID sectorId,
      @JsonProperty(value = "subtotal", required = true) String subtotal,
      @JsonProperty(value = "discount", required = true) String discount,
      @JsonProperty(value = "total", required = true) String total,
      @JsonProperty(value = "promotion", required = true)
          PurchaseSnapshot.AppliedPromotionSnapshot promotion,
      @JsonProperty(value = "items", required = true) List<PurchaseSnapshot.Item> items) {
    this.schemaVersion = schemaVersion;
    this.orderId = orderId;
    this.eventId = eventId;
    this.buyerSubject = buyerSubject;
    this.customerEmail = customerEmail;
    this.sourceConfigVersion = sourceConfigVersion;
    this.sourceOrderVersion = sourceOrderVersion;
    this.frozenAt = frozenAt;
    this.expiresAt = expiresAt;
    this.currency = currency;
    this.eventTitle = eventTitle;
    this.eventStartsAt = eventStartsAt;
    this.eventEndsAt = eventEndsAt;
    this.venueName = venueName;
    this.mode = mode;
    this.sectorId = sectorId;
    this.subtotal = subtotal;
    this.discount = discount;
    this.total = total;
    this.promotion = promotion;
    this.items = items == null ? null : Collections.unmodifiableList(new ArrayList<>(items));
  }

  @AssertTrue
  @JsonIgnore
  public boolean isValidSnapshot() {
    return ContractRules.validPurchaseSnapshot(this);
  }

  @Getter
  public static final class AppliedPromotionSnapshot {
    @NotNull private final UUID promotionId;
    @NotBlank private final String code;

    @NotBlank
    @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$")
    private final String discount;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public AppliedPromotionSnapshot(
        @JsonProperty(value = "promotionId", required = true) UUID promotionId,
        @JsonProperty(value = "code", required = true) String code,
        @JsonProperty(value = "discount", required = true) String discount) {
      this.promotionId = promotionId;
      this.code = code;
      this.discount = discount;
    }
  }

  @Getter
  public static final class Item {
    @NotNull private final UUID orderItemId;
    @NotNull private final UUID ticketTypeId;
    @NotBlank private final String ticketTypeName;
    @NotNull @Positive private final Integer quantity;

    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private final String unitPrice;

    @NotBlank
    @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$")
    private final String lineSubtotal;

    private final UUID sectorId;
    @NotNull @Valid private final List<@NotNull SeatSelectionSnapshot> seats;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Item(
        @JsonProperty(value = "orderItemId", required = true) UUID orderItemId,
        @JsonProperty(value = "ticketTypeId", required = true) UUID ticketTypeId,
        @JsonProperty(value = "ticketTypeName", required = true) String ticketTypeName,
        @JsonProperty(value = "quantity", required = true) Integer quantity,
        @JsonProperty(value = "unitPrice", required = true) String unitPrice,
        @JsonProperty(value = "lineSubtotal", required = true) String lineSubtotal,
        @JsonProperty(value = "sectorId", required = true) UUID sectorId,
        @JsonProperty(value = "seats", required = true) List<SeatSelectionSnapshot> seats) {
      this.orderItemId = orderItemId;
      this.ticketTypeId = ticketTypeId;
      this.ticketTypeName = ticketTypeName;
      this.quantity = quantity;
      this.unitPrice = unitPrice;
      this.lineSubtotal = lineSubtotal;
      this.sectorId = sectorId;
      this.seats = seats == null ? null : Collections.unmodifiableList(new ArrayList<>(seats));
    }
  }
}
