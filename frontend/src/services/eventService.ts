import axiosClient from "../lib/axiosClient";
import { EventSummary, PublicSeatMap } from "../types/api";
import { EventPage, PublicEventContract } from "../types/eventContract";

function toEventSummary(event: PublicEventContract): EventSummary {
  const images = [...event.images].sort((a, b) => a.displayOrder - b.displayOrder);
  const ticketTypes = event.ticketTypes.map((type, displayOrder) => ({
    id: type.ticketTypeId, eventId: event.eventId, name: type.name,
    unitPrice: type.unitPrice, price: Number(type.unitPrice), currency: type.currency,
    sectorId: type.sectorId || undefined,
    inventoryMode: event.seatMap?.sectors.find(s => s.sectorId === type.sectorId)?.kind === "SEATED" ? "ASSIGNED_SEAT" : "QUANTITY",
    // Sức chứa cấu hình không phải số vé còn lại; Booking cấp availability riêng.
    quantityTotal: Number(type.configuredQuantity ?? 0), quantityAvailable: 0, quantityReserved: 0,
    maxPerOrder: Number.MAX_SAFE_INTEGER, seatSelectionEnabled: event.mode === "SEAT_MAP",
    displayOrder, colorCode: type.colorCode, status: "ACTIVE", isVisible: true,
  }));
  const seatMap: PublicSeatMap | null = event.seatMap ? {
    layoutId: event.seatMap.layoutId,
    backgroundImageUrl: event.seatMap.backgroundImageReference || undefined,
    backgroundWidth: event.seatMap.canvasWidth, backgroundHeight: event.seatMap.canvasHeight,
    mapConfig: event.seatMap.layoutData,
    sectors: event.seatMap.sectors.map(sector => ({
      id: sector.sectorId, name: sector.name, sectorType: sector.kind, colorCode: sector.colorCode,
      mapData: { ...sector.geometry.shape, code: sector.code, sectorType: sector.kind, seatLayout: sector.geometry.seatLayout },
      seatsData: sector.seats.filter(seat => !seat.isHidden).map(seat => ({
        id: seat.seatId, ticketTypeId: seat.ticketTypeId, seatLabel: seat.seatLabel,
        rowName: seat.rowName, seatNumber: seat.seatNumber, coordX: seat.coordX, coordY: seat.coordY,
        isActive: true,
      })),
    })),
  } : null;
  return {
    id: event.eventId, slug: event.eventId, title: event.title, description: event.description,
    startDatetime: event.startsAt, endDatetime: event.endsAt, venueName: event.venueName,
    mode: event.mode, seatMap, ticketTypes,
    // Endpoint này chỉ trả sự kiện PUBLISHED; DTO không có slug hay category.
    status: "PUBLISHED", isFeatured: false,
    bannerUrl: images[0]?.objectReference || "/event-placeholder.svg", thumbnailUrl: images[0]?.objectReference,
    images: images.map((image, index) => ({ type: index === 0 ? "BANNER" : "GALLERY", url: image.objectReference })),
    minPrice: ticketTypes.length ? Math.min(...ticketTypes.map(type => type.price)) : undefined,
    minPriceAmount: event.ticketTypes.reduce<string | undefined>((minimum, type) => minimum === undefined || BigInt(type.unitPrice) < BigInt(minimum) ? type.unitPrice : minimum, undefined),
  };
}

export const eventService = {
  getEvents: async (params: { search?: string; category?: string; size?: number; cursor?: string; [key: string]: unknown } = {}): Promise<EventPage> => {
    const response = await axiosClient.get<{ items: PublicEventContract[]; nextCursor: string | null }>("/api/v1/events", {
      params: { q: params.search || undefined, categoryId: params.category || undefined, size: params.size ?? 20, cursor: params.cursor || undefined },
    });
    return { content: response.data.items.map(toEventSummary), nextCursor: response.data.nextCursor };
  },
  getFeaturedEvents: async (limit = 8) => (await eventService.getEvents({ size: limit })).content,
  getEventDetails: async (eventId: string): Promise<EventSummary> => {
    const response = await axiosClient.get<PublicEventContract>(`/api/v1/events/${eventId}`);
    return toEventSummary(response.data);
  },
  getSeatMap: async (eventId: string): Promise<PublicSeatMap | null> => {
    return (await eventService.getEventDetails(eventId)).seatMap ?? null;
  },
};
