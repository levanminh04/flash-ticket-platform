import { createClientId } from "../components/seat-map/shared/idUtils";
import axiosClient from "../lib/axiosClient";
import { buildDefaultSeatLayoutConfig } from "../components/seat-map/editor/seatMapEditorUtils";
import { SeatMapEditorDocument, SeatMapEditorShape } from "../components/seat-map/editor/seatMapEditorTypes";
import { OrganizerPublicTicketType, OrganizerSeatMap } from "./organizerWorkspaceService";
import { OrganizerSummary } from "./organizerDashboardService";

type Geometry = { schemaVersion: number; shape: Record<string, unknown>; seatLayout: SeatMapEditorShape["seatLayout"]; editorVisible: boolean; editorLocked: boolean };
type WireMap = {
  schemaVersion: number; layoutId: string; name: string; canvasWidth: number; canvasHeight: number;
  backgroundImageReference: string | null; layoutData: { schemaVersion: number; decorations: object[] };
  sectors: {
    sectorId: string; name: string; code: string | null; kind: string; configuredCapacity: string;
    displayOrder: number; colorCode: string | null; geometry: Geometry;
    seats: { seatId: string; seatLabel: string; rowName: string; seatNumber: string; coordX: number; coordY: number; ticketTypeId: string | null; isHidden: boolean }[];
  }[];
};
type Owner = OrganizerSummary & {
  seatMap: WireMap | null;
  ticketTypes: { ticketTypeId: string; name: string; sectorId: string; unitPrice: string; currency: string; colorCode: string | null }[];
};

function path(id: string) {
  return `/api/v1/organizer/events/${encodeURIComponent(id)}`;
}

export async function readSeatMapOwner(id: string) {
  return (await axiosClient.get<Owner>(path(id))).data;
}

export function editorTickets(event: Owner): OrganizerPublicTicketType[] {
  return event.ticketTypes.map(type => ({
    id: type.ticketTypeId, eventSectorId: type.sectorId, sectorId: type.sectorId, name: type.name,
    price: Number(type.unitPrice), currency: type.currency, colorCode: type.colorCode ?? undefined,
  }));
}

export function editorSeatMap({ seatMap: map, ticketTypes }: Owner): OrganizerSeatMap {
  if (!map) return { backgroundWidth: 1200, backgroundHeight: 800, sectors: [] };
  return {
    layoutId: map.layoutId, backgroundWidth: map.canvasWidth, backgroundHeight: map.canvasHeight,
    backgroundImageUrl: map.backgroundImageReference ?? undefined,
    sectors: map.sectors.map(sector => ({
      id: sector.sectorId, name: sector.name, code: sector.code, sectorType: sector.kind,
      totalCapacity: Number(sector.configuredCapacity), displayOrder: sector.displayOrder,
      colorCode: sector.colorCode ?? undefined,
      mapData: {
        ...sector.geometry.shape,
        code: sector.code,
        seatLayout: sector.geometry.seatLayout,
        visible: sector.geometry.editorVisible,
        locked: sector.geometry.editorLocked,
        ticketTypeIds: sector.kind === "STANDING"
          ? ticketTypes.filter(type => type.sectorId === sector.sectorId).map(type => type.ticketTypeId)
          : [],
      },
      seatsData: sector.seats.map(seat => ({
        id: seat.seatId, seatLabel: seat.seatLabel, rowName: seat.rowName, seatNumber: seat.seatNumber,
        coordX: seat.coordX, coordY: seat.coordY, ticketTypeId: seat.ticketTypeId ?? undefined,
        isActive: !seat.isHidden,
      })),
    })),
  };
}

function wireShape(shape: SeatMapEditorShape) {
  const { shapeType, bounds, mapData } = shape;
  if (shapeType === "polygon") return {
    shapeType, bounds,
    points: shape.points.map((coordinate, index) => coordinate + (index % 2 ? bounds.y : bounds.x)),
  };
  if (shapeType === "path") return { shapeType, bounds, pathData: mapData?.pathData };
  if (shapeType === "fan" || shapeType === "ringSection") {
    const { cx, cy, outerRadius, innerRadius, startAngle, endAngle } = mapData ?? {};
    return { shapeType, bounds, cx, cy, outerRadius, innerRadius, startAngle, endAngle };
  }
  return { shapeType, bounds };
}

export async function saveSeatMapOwner(event: Owner, document: SeatMapEditorDocument) {
  const map: WireMap = {
    schemaVersion: 1, layoutId: event.seatMap?.layoutId ?? createClientId(), name: event.seatMap?.name ?? event.title,
    canvasWidth: Math.ceil(document.width), canvasHeight: Math.ceil(document.height),
    backgroundImageReference: document.referenceImageUrl || null,
    layoutData: event.seatMap?.layoutData ?? { schemaVersion: 1, decorations: [] },
    sectors: document.shapes.map((shape, displayOrder) => ({
      sectorId: shape.id, name: shape.name, code: shape.code ?? null, kind: shape.sectorType,
      configuredCapacity: String(shape.sectorType === "STANDING" ? shape.totalCapacity ?? 0 : shape.seats.filter(seat => !seat.hidden).length),
      displayOrder, colorCode: shape.color,
      geometry: { schemaVersion: 1, shape: wireShape(shape), seatLayout: { ...buildDefaultSeatLayoutConfig(shape.bounds, shape.seats.length), ...shape.seatLayout, mode: shape.seatLayout.mode ?? "grid" }, editorVisible: shape.visible, editorLocked: shape.locked },
      seats: shape.sectorType === "STANDING" ? [] : shape.seats.map((seat, index) => ({
        seatId: seat.id, seatLabel: seat.label, rowName: seat.rowName || "A", seatNumber: seat.seatNumber || String(index + 1),
        coordX: Number(seat.x.toFixed(2)), coordY: Number(seat.y.toFixed(2)),
        ticketTypeId: seat.ticketTypeId || null, isHidden: Boolean(seat.hidden),
      })),
    })),
  };
  await axiosClient.put<WireMap>(`${path(event.eventId)}/seat-map`, map, { params: { rowVersion: event.rowVersion } });
  return readSeatMapOwner(event.eventId);

}
