import { createClientId } from "../components/seat-map/shared/idUtils";
import axiosClient from "../lib/axiosClient";
import { buildDefaultSeatLayoutConfig } from "../components/seat-map/editor/seatMapEditorUtils";
import { SeatMapEditorDocument, SeatMapEditorShape } from "../components/seat-map/editor/seatMapEditorTypes";
import { OrganizerPublicTicketType, OrganizerSeatMap } from "./organizerWorkspaceService";
import { OrganizerSummary } from "./organizerDashboardService";

type Geometry = { schemaVersion: number; shape: Record<string, unknown>; seatLayout: SeatMapEditorShape["seatLayout"]; editorVisible: boolean; editorLocked: boolean };
type DecorationWire = {
  id: string;
  label?: string;
  colorCode?: string | null;
  shape?: Record<string, unknown>;
};

type WireMap = {
  schemaVersion: number; layoutId: string; name: string; canvasWidth: number; canvasHeight: number;
  backgroundImageReference: string | null; layoutData: { schemaVersion: number; decorations: DecorationWire[] };
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
  if (!map) return { backgroundWidth: 1280, backgroundHeight: 860, sectors: [] };
  const ticketColorById = new Map(
    ticketTypes.map((type) => [type.ticketTypeId, type.colorCode ?? undefined]),
  );
  const sectorItems = map.sectors.map(sector => ({
    id: sector.sectorId, name: sector.name, code: sector.code, sectorType: sector.kind,
    totalCapacity: Number(sector.configuredCapacity), displayOrder: sector.displayOrder,
    colorCode: sector.colorCode ?? undefined,
    mapData: {
      ...sector.geometry.shape,
      presetType:
        sector.kind === "SEATED" && sector.seats.length > 0
          ? sector.geometry.seatLayout?.mode === "table"
            ? "round_table"
            : "seated_grid"
          : undefined,
      outlineMode: sector.geometry.seatLayout?.outlineMode,
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
      colorCode: seat.ticketTypeId ? ticketColorById.get(seat.ticketTypeId) : undefined,
      isActive: !seat.isHidden,
    })),
  }));

  const decorationItems = (map.layoutData?.decorations ?? []).map((dec, index) => {
    const rawShape = (dec.shape ?? {}) as Record<string, unknown>;
    const cornerRadiusVal = Number(rawShape.cornerRadius ?? 0);
    const fillLower = typeof rawShape.fill === "string" ? rawShape.fill.toLowerCase() : "";
    const isVenueFrame =
      (rawShape.isVenueShape === true && rawShape.venueVariant === "venue_frame") ||
      (rawShape.shapeType === "roundedRect" &&
        cornerRadiusVal >= 80 &&
        (fillLower === "#f6ebe4" || String(dec.colorCode ?? "").toLowerCase() === "#e5b89b"));
    return {
      id: dec.id || createClientId(),
      name: dec.label || (isVenueFrame ? "Venue Shape" : "STAGE"),
      code: typeof rawShape.code === "string" ? rawShape.code : dec.label || (isVenueFrame ? "VENUE" : "STAGE"),
      sectorType: "STANDING",
      totalCapacity: 0,
      displayOrder: map.sectors.length + index,
      colorCode: dec.colorCode ?? "#1e293b",
      mapData: {
        ...rawShape,
        decorative: true,
        hideSubtitle: true,
        ...(isVenueFrame ? { isVenueShape: true, venueVariant: "venue_frame" } : {}),
      },
      seatsData: [],
    };
  });

  return {
    layoutId: map.layoutId, backgroundWidth: map.canvasWidth, backgroundHeight: map.canvasHeight,
    backgroundImageUrl: map.backgroundImageReference ?? undefined,
    sectors: [...sectorItems, ...decorationItems],
  };
}

function isDecorativeShape(shape: SeatMapEditorShape) {
  return (
    shape.mapData?.decorative === true ||
    shape.shapeType === "stage" ||
    shape.shapeType === "foh"
  );
}

function wireShape(shape: SeatMapEditorShape) {
  const { shapeType, bounds, mapData } = shape;
  const extraMeta: Record<string, unknown> = {};
  if (mapData?.stylePreset) extraMeta.stylePreset = mapData.stylePreset;
  if (mapData?.fill) extraMeta.fill = mapData.fill;
  if (mapData?.subtitle) extraMeta.subtitle = mapData.subtitle;
  if (mapData?.cornerRadius !== undefined) extraMeta.cornerRadius = mapData.cornerRadius;
  if (mapData?.labelPosition) extraMeta.labelPosition = mapData.labelPosition;
  if (mapData?.labelWidth) extraMeta.labelWidth = mapData.labelWidth;
  if (mapData?.labelRotation !== undefined) extraMeta.labelRotation = mapData.labelRotation;
  if (mapData?.decorative) extraMeta.decorative = true;
  if (mapData?.hideSubtitle) extraMeta.hideSubtitle = true;

  if (shapeType === "polygon") return {
    ...extraMeta,
    shapeType, bounds,
    points: shape.points.map((coordinate, index) => coordinate + (index % 2 ? bounds.y : bounds.x)),
  };
  if (shapeType === "path") return { ...extraMeta, shapeType, bounds, pathData: mapData?.pathData };
  if (shapeType === "fan" || shapeType === "ringSection") {
    const { cx, cy, outerRadius, innerRadius, startAngle, endAngle } = mapData ?? {};
    return { ...extraMeta, shapeType, bounds, cx, cy, outerRadius, innerRadius, startAngle, endAngle };
  }
  return { ...extraMeta, shapeType, bounds };
}

export async function saveSeatMapOwner(event: Owner, document: SeatMapEditorDocument) {
  const sellableShapes = document.shapes.filter(shape => !isDecorativeShape(shape));
  const decorativeShapes = document.shapes.filter(shape => isDecorativeShape(shape));

  const map: WireMap = {
    schemaVersion: 1, layoutId: event.seatMap?.layoutId ?? createClientId(), name: event.seatMap?.name ?? event.title,
    canvasWidth: Math.ceil(document.width), canvasHeight: Math.ceil(document.height),
    backgroundImageReference: document.referenceImageUrl || null,
    layoutData: {
      schemaVersion: 1,
      decorations: decorativeShapes.map(shape => ({
        id: shape.id,
        label:
          shape.mapData?.isVenueShape === true && shape.mapData?.venueVariant === "venue_frame"
            ? (shape.label ?? "")
            : (shape.label ?? "").trim() || shape.name,
        colorCode: shape.color,
        shape: wireShape(shape),
      })),
    },
    sectors: sellableShapes.map((shape, displayOrder) => ({
      sectorId: shape.id, name: shape.name, code: shape.code ?? null, kind: shape.sectorType,
      configuredCapacity: String(shape.sectorType === "STANDING" ? Math.max(shape.totalCapacity ?? 100, 1) : Math.max(shape.seats.filter(seat => !seat.hidden).length, 1)),
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

export async function uploadSeatMapBackground(eventId: string, file: File) {
  const body = new FormData();
  body.append("file", file);
  const { data } = await axiosClient.post<{ backgroundImageReference: string }>(
    `${path(eventId)}/seat-map/background-image`,
    body,
    { headers: { "Content-Type": undefined } },
  );
  return data.backgroundImageReference;
}
