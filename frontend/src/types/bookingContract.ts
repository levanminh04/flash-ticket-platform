export type OrderSelection =
  | { mode: "QUANTITY"; items: { ticketTypeId: string; quantity: number }[] }
  | { mode: "SEAT_MAP"; kind: "STANDING"; sectorId: string; items: { ticketTypeId: string; quantity: number }[] }
  | { mode: "SEAT_MAP"; kind: "SEATED"; sectorId: string; seatIds: string[] };

export interface CreateOrderRequest {
  eventId: string;
  customerEmail: string;
  selection: OrderSelection;
}

export interface Availability {
  eventId: string;
  sourceConfigVersion: string;
  asOf: string;
  ticketTypes: { ticketTypeId: string; available: string }[];
  sectors: { sectorId: string; kind: "STANDING" | "SEATED"; available: string }[];
  seats: { seatId: string; state: "AVAILABLE" | "HELD" | "PURCHASED" | "UNAVAILABLE" }[];
}

export interface OrderView {
  orderId: string;
  eventId: string;
  state: "PENDING_PAYMENT" | "ISSUING" | "COMPLETED" | "CANCELLED" | "EXPIRED" | "ISSUANCE_FAILED";
  customerEmail: string;
  currency: "VND";
  subtotal: string;
  discount: string;
  total: string;
  expiresAt: string;
  paymentFrozenAt: string | null;
  sourceConfigVersion: string;
  items: {
    orderItemId: string; ticketTypeId: string; ticketTypeName: string;
    quantity: number; unitPrice: string; lineSubtotal: string; sectorId: string | null;
    seats: { seatId: string; seatLabel: string; rowName: string; seatNumber: string }[];
  }[];
}
