// Wire DTO của Event Service (dev/tuyen). Các ID dùng UUID, tiền là chuỗi VND.
export interface PublicEventContract {
  eventId: string;
  title: string;
  description: string;
  startsAt: string;
  endsAt: string;
  venueName: string;
  mode: "QUANTITY" | "SEAT_MAP";
  images: { imageId: string; objectReference: string; displayOrder: number }[];
  ticketTypes: {
    ticketTypeId: string; name: string; unitPrice: string; currency: string;
    sectorId: string | null; configuredQuantity: string | null; colorCode: string;
  }[];
  seatMap: EventSeatMapContract | null;
}

export interface EventSeatMapContract {
  schemaVersion: number;
  layoutId: string;
  name: string;
  canvasWidth: number;
  canvasHeight: number;
  backgroundImageReference: string | null;
  layoutData: { schemaVersion: number; decorations: Record<string, unknown>[] };
  sectors: {
    sectorId: string; name: string; code: string; kind: "STANDING" | "SEATED";
    configuredCapacity: string; displayOrder: number; colorCode: string;
    geometry: { schemaVersion: number; shape: Record<string, unknown>; seatLayout: Record<string, unknown> };
    seats: {
      seatId: string; seatLabel: string; rowName: string; seatNumber: string;
      coordX: number; coordY: number; ticketTypeId: string; isHidden: boolean;
    }[];
  }[];
}

export interface EventPage {
  content: import("./api").EventSummary[];
  nextCursor: string | null;
}
