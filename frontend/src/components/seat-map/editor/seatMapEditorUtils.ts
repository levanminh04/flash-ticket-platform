import {
  OrganizerSeatMap,
  OrganizerSeatMapPublishPayload,
  OrganizerSeatSector,
} from "../../../services/organizerWorkspaceService";
import {
  SeatMapEditorBounds,
  SeatMapEditorDocument,
  SeatMapEditorSeat,
  SeatMapEditorSeatLayoutConfig,
  SeatMapEditorSectorType,
  SeatMapEditorShape,
  SeatMapEditorShapeType,
} from "./seatMapEditorTypes";
import { generateRingSeats } from "../shared/seatGenerationUtils";
import { createClientId } from "../shared/idUtils";

const DEFAULT_CANVAS_WIDTH = 1280;
const DEFAULT_CANVAS_HEIGHT = 720;
const DEFAULT_SECTOR_COLORS = ["#14b8a6"];
const DEFAULT_SEAT_RADIUS = 6;
const DEFAULT_ROW_START_CHAR_CODE = "A".charCodeAt(0);
const DRAFT_VERSION = 1;
const SECTOR_TYPES: SeatMapEditorSectorType[] = ["SEATED", "STANDING"];
const GENERATED_SEAT_SECTOR_TYPE: SeatMapEditorSectorType = "SEATED";

export function createEditorDraftKey(eventId: string) {
  return `seat-map-editor-draft:${eventId}`;
}

export function asNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }

  if (typeof value === "string") {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }

  return null;
}

function buildBoundsFromAbsolutePoints(points: number[]): SeatMapEditorBounds | null {
  if (points.length < 6 || points.length % 2 !== 0) {
    return null;
  }

  const xs: number[] = [];
  const ys: number[] = [];

  for (let index = 0; index < points.length; index += 2) {
    xs.push(points[index]);
    ys.push(points[index + 1]);
  }

  const minX = Math.min(...xs);
  const minY = Math.min(...ys);
  const maxX = Math.max(...xs);
  const maxY = Math.max(...ys);

  return {
    x: minX,
    y: minY,
    width: Math.max(maxX - minX, 1),
    height: Math.max(maxY - minY, 1),
  };
}

function normalizePolygonPoints(points: number[], bounds: SeatMapEditorBounds): number[] {
  if (points.length < 6) {
    return [];
  }

  return points.map((point, index) => (index % 2 === 0 ? point - bounds.x : point - bounds.y));
}

function normalizeBounds(mapData?: Record<string, unknown>): SeatMapEditorBounds | null {
  if (!mapData) {
    return null;
  }

  const bounds = mapData.bounds as Record<string, unknown> | undefined;
  const x = asNumber(bounds?.x ?? mapData.x ?? mapData.positionX);
  const y = asNumber(bounds?.y ?? mapData.y ?? mapData.positionY);
  const width = asNumber(bounds?.width ?? mapData.width ?? mapData.sizeX);
  const height = asNumber(bounds?.height ?? mapData.height ?? mapData.sizeY);

  if (x === null || y === null || width === null || height === null) {
    return null;
  }

  return {
    x,
    y,
    width: Math.max(width, 1),
    height: Math.max(height, 1),
  };
}

function normalizeRawPoints(mapData?: Record<string, unknown>): number[] {
  if (!mapData) {
    return [];
  }

  const sourcePoints = Array.isArray(mapData.points) ? mapData.points : [];
  return sourcePoints
    .flatMap((point) => {
      if (Array.isArray(point) && point.length >= 2) {
        const x = asNumber(point[0]);
        const y = asNumber(point[1]);
        return x !== null && y !== null ? [x, y] : [];
      }

      if (point && typeof point === "object") {
        const x = asNumber((point as Record<string, unknown>).x);
        const y = asNumber((point as Record<string, unknown>).y);
        return x !== null && y !== null ? [x, y] : [];
      }

      if (typeof point === "number") {
        return [point];
      }

      return [];
    })
    .filter((value) => Number.isFinite(value));
}

function inferShapeType(
  mapData?: Record<string, unknown>,
  points?: number[],
): SeatMapEditorShapeType {
  const rawShapeType = typeof mapData?.shapeType === "string" ? mapData.shapeType : "";
  if (
    rawShapeType === "path" ||
    rawShapeType === "ringSection" ||
    rawShapeType === "fan" ||
    rawShapeType === "roundedRect" ||
    rawShapeType === "stage" ||
    rawShapeType === "foh"
  ) {
    return rawShapeType;
  }
  if (rawShapeType === "polygon") {
    return "polygon";
  }
  if (rawShapeType === "rect") {
    return "rectangle";
  }
  if (rawShapeType === "circle") {
    return "circle";
  }
  if (rawShapeType === "ellipse") {
    return "ellipse";
  }

  return points && points.length >= 6 ? "polygon" : "rectangle";
}

function normalizeSectorType(value: unknown, seatCount = 0): SeatMapEditorSectorType {
  return typeof value === "string" && SECTOR_TYPES.includes(value as SeatMapEditorSectorType)
    ? (value as SeatMapEditorSectorType)
    : seatCount > 0
      ? "SEATED"
      : "STANDING";
}

function normalizeStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }

  return value.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
}

function buildFallbackBounds(
  sectorIndex: number,
  seatCount: number,
  canvasWidth: number,
  canvasHeight: number,
): SeatMapEditorBounds {
  const column = sectorIndex % 3;
  const row = Math.floor(sectorIndex / 3);
  const width = Math.max(Math.round(canvasWidth * 0.22), 200);
  const height = Math.max(Math.round(canvasHeight * 0.18), 130);
  const gapX = 32;
  const gapY = 28;

  return {
    x: 48 + column * (width + gapX),
    y: 56 + row * (height + gapY),
    width,
    height: Math.max(height, 96 + seatCount * 2),
  };
}

export function buildDefaultSeatLayoutConfig(
  bounds: SeatMapEditorBounds,
  seatCount = 0,
): SeatMapEditorSeatLayoutConfig {
  const suggestedRows = seatCount > 0 ? Math.max(1, Math.ceil(Math.sqrt(seatCount / 1.6))) : 4;
  const rows = Math.max(1, suggestedRows);
  const seatsPerRow = seatCount > 0 ? Math.max(1, Math.ceil(seatCount / rows)) : 6;

  return {
    rows,
    seatsPerRow,
    gapX: Math.max(18, Math.round(bounds.width / Math.max(seatsPerRow + 1, 3))),
    gapY: Math.max(18, Math.round(bounds.height / Math.max(rows + 2, 4))),
    paddingX: Math.max(16, Math.round(bounds.width * 0.08)),
    paddingY: Math.max(24, Math.round(bounds.height * 0.14)),
    offsetX: 0,
    offsetY: 0,
    mode: "grid",
    seatRadius: DEFAULT_SEAT_RADIUS,
    rowStartCharCode: DEFAULT_ROW_START_CHAR_CODE,
    seatStartNumber: 1,
    rotation: 0,
    curve: 0,
    stretch: 0,
    pathBasedLayout: false,
    rowLabelPosition: "left",
    rowAlignment: "center",
    outlineMode: "auto",
  };
}

function normalizeSeatLayoutConfig(
  rawLayout: unknown,
  bounds: SeatMapEditorBounds,
  seatCount: number,
): SeatMapEditorSeatLayoutConfig {
  const fallback = buildDefaultSeatLayoutConfig(bounds, seatCount);
  const layout = rawLayout && typeof rawLayout === "object" ? (rawLayout as Record<string, unknown>) : {};

  return {
    mode:
      layout.mode === "arc" || layout.mode === "fan" || layout.mode === "table"
        ? layout.mode
        : fallback.mode,
    rows: Math.max(1, Math.round(asNumber(layout.rows) ?? fallback.rows)),
    seatsPerRow: Math.max(1, Math.round(asNumber(layout.seatsPerRow) ?? fallback.seatsPerRow)),
    gapX: Math.max(8, asNumber(layout.gapX) ?? fallback.gapX),
    gapY: Math.max(8, asNumber(layout.gapY) ?? fallback.gapY),
    paddingX: Math.max(4, asNumber(layout.paddingX) ?? fallback.paddingX),
    paddingY: Math.max(4, asNumber(layout.paddingY) ?? fallback.paddingY),
    offsetX: asNumber(layout.offsetX) ?? fallback.offsetX,
    offsetY: asNumber(layout.offsetY) ?? fallback.offsetY,
    seatRadius: Math.max(4, asNumber(layout.seatRadius) ?? fallback.seatRadius),
    rowStartCharCode: Math.round(asNumber(layout.rowStartCharCode) ?? fallback.rowStartCharCode),
    seatStartNumber: Math.max(1, Math.round(asNumber(layout.seatStartNumber) ?? fallback.seatStartNumber)),
    rotation: Math.max(-180, Math.min(180, Math.round(asNumber(layout.rotation) ?? fallback.rotation ?? 0))),
    curve: Math.max(-100, Math.min(100, Math.round(asNumber(layout.curve) ?? fallback.curve ?? 0))),
    stretch: Math.max(-100, Math.min(100, Math.round(asNumber(layout.stretch) ?? fallback.stretch ?? 0))),
    pathBasedLayout: Boolean(layout.pathBasedLayout ?? fallback.pathBasedLayout ?? false),
    rowLabelPosition:
      layout.rowLabelPosition === "none" ||
      layout.rowLabelPosition === "left" ||
      layout.rowLabelPosition === "right" ||
      layout.rowLabelPosition === "both"
        ? layout.rowLabelPosition
        : (fallback.rowLabelPosition ?? "left"),
    rowAlignment:
      layout.rowAlignment === "left" ||
      layout.rowAlignment === "center" ||
      layout.rowAlignment === "right"
        ? layout.rowAlignment
        : (fallback.rowAlignment ?? "center"),
    outlineMode:
      layout.outlineMode === "auto" ||
      layout.outlineMode === "full" ||
      layout.outlineMode === "none"
        ? layout.outlineMode
        : (fallback.outlineMode ?? "auto"),
  };
}

function formatRowName(rowIndex: number, rowStartCharCode: number) {
  let current = (rowStartCharCode - 65) + rowIndex;
  let rowName = "";

  while (current >= 0) {
    rowName = String.fromCharCode(65 + (current % 26)) + rowName;
    current = Math.floor(current / 26) - 1;
  }

  return rowName;
}

function buildSeatLabel(rowName: string, seatNumber: string) {
  return `${rowName}${seatNumber}`;
}

function translatePathData(pathData: string, deltaX: number, deltaY: number) {
  let coordinateIndex = 0;
  return pathData.replace(/-?\d*\.?\d+(?:e[-+]?\d+)?/gi, (match) => {
    const value = Number(match);
    if (!Number.isFinite(value)) {
      return match;
    }
    const translated = value + (coordinateIndex % 2 === 0 ? deltaX : deltaY);
    coordinateIndex += 1;
    return Number(translated.toFixed(3)).toString();
  });
}

function translateMapData(mapData: Record<string, unknown> | undefined, deltaX: number, deltaY: number) {
  if (!mapData) {
    return mapData;
  }

  const nextMapData: Record<string, unknown> = { ...mapData };
  const cx = asNumber(nextMapData.cx);
  const cy = asNumber(nextMapData.cy);
  if (cx !== null) nextMapData.cx = cx + deltaX;
  if (cy !== null) nextMapData.cy = cy + deltaY;

  if (typeof nextMapData.pathData === "string") {
    nextMapData.pathData = translatePathData(nextMapData.pathData, deltaX, deltaY);
  }

  if (nextMapData.labelPosition && typeof nextMapData.labelPosition === "object") {
    const labelPosition = nextMapData.labelPosition as Record<string, unknown>;
    const x = asNumber(labelPosition.x);
    const y = asNumber(labelPosition.y);
    nextMapData.labelPosition = {
      ...labelPosition,
      x: x !== null ? x + deltaX : labelPosition.x,
      y: y !== null ? y + deltaY : labelPosition.y,
    };
  }

  if (nextMapData.bounds && typeof nextMapData.bounds === "object") {
    const bounds = nextMapData.bounds as Record<string, unknown>;
    const x = asNumber(bounds.x);
    const y = asNumber(bounds.y);
    nextMapData.bounds = {
      ...bounds,
      x: x !== null ? x + deltaX : bounds.x,
      y: y !== null ? y + deltaY : bounds.y,
    };
  }

  if (Array.isArray(nextMapData.transformedOutlinePoints)) {
    nextMapData.transformedOutlinePoints = nextMapData.transformedOutlinePoints.map((value, index) => {
      const numeric = asNumber(value);
      if (numeric === null) return value;
      return Number((numeric + (index % 2 === 0 ? deltaX : deltaY)).toFixed(2));
    });
  }

  return nextMapData;
}

function buildRelativePolygonPoints(shape: SeatMapEditorShape) {
  const relativePoints: Array<{ x: number; y: number }> = [];
  for (let index = 0; index < shape.points.length; index += 2) {
    relativePoints.push({
      x: shape.points[index],
      y: shape.points[index + 1],
    });
  }
  return relativePoints;
}

function isPointInsideEllipse(
  localX: number,
  localY: number,
  bounds: SeatMapEditorBounds,
  radius: number,
) {
  const centerX = bounds.width / 2;
  const centerY = bounds.height / 2;
  const radiusX = Math.max(bounds.width / 2 - radius, 1);
  const radiusY = Math.max(bounds.height / 2 - radius, 1);

  const normalizedCenter =
    ((localX - centerX) * (localX - centerX)) / (radiusX * radiusX) +
    ((localY - centerY) * (localY - centerY)) / (radiusY * radiusY);

  if (normalizedCenter > 1) {
    return false;
  }

  const cardinalChecks: Array<[number, number]> = [
    [localX - radius, localY],
    [localX + radius, localY],
    [localX, localY - radius],
    [localX, localY + radius],
  ];

  return cardinalChecks.every(([x, y]) => {
    const normalized =
      ((x - centerX) * (x - centerX)) / (radiusX * radiusX) +
      ((y - centerY) * (y - centerY)) / (radiusY * radiusY);
    return normalized <= 1;
  });
}

function normalizeSeats(
  sector: OrganizerSeatSector,
  fallbackBounds: SeatMapEditorBounds,
): SeatMapEditorSeat[] {
  const seats = sector.seatsData ?? [];
  return seats
    .map((seat, index) => {
      const x = asNumber(seat.coordX) ?? fallbackBounds.x + 24 + (index % 8) * 22;
      const y = asNumber(seat.coordY) ?? fallbackBounds.y + 48 + Math.floor(index / 8) * 22;

      return {
        id: seat.id,
        label: seat.seatLabel || seat.seatNumber || seat.id,
        rowName: seat.rowName,
        seatNumber: seat.seatNumber,
        ticketTypeId: seat.ticketTypeId ?? undefined,
        colorCode: seat.colorCode ?? undefined,
        x,
        y,
        inventoryStatus: seat.inventoryStatus,
        hidden: seat.isActive === false,
        manualAdjusted: false,
      };
    })
    .filter((seat) => Number.isFinite(seat.x) && Number.isFinite(seat.y));
}

function buildShape(
  sector: OrganizerSeatSector,
  sectorIndex: number,
  canvasWidth: number,
  canvasHeight: number,
): SeatMapEditorShape {
  const mapData = sector.mapData ?? {};
  const rawPoints = normalizeRawPoints(mapData);
  const inferredShapeType = inferShapeType(mapData, rawPoints);
  const normalizedBounds =
    normalizeBounds(mapData) ??
    (inferredShapeType === "polygon" ? buildBoundsFromAbsolutePoints(rawPoints) : null);
  const fallbackBounds =
    normalizedBounds ??
    buildFallbackBounds(sectorIndex, sector.seatsData?.length ?? 0, canvasWidth, canvasHeight);

  const points =
    inferredShapeType === "polygon" ? normalizePolygonPoints(rawPoints, fallbackBounds) : [];
  const color = sector.colorCode || DEFAULT_SECTOR_COLORS[sectorIndex % DEFAULT_SECTOR_COLORS.length];
  const seats = normalizeSeats(sector, fallbackBounds);
  const visible = sector.isActive !== false && mapData.visible !== false;
  const locked = mapData.locked === true;
  const zIndex = asNumber(mapData.zIndex) ?? sectorIndex;
  const totalCapacity = asNumber(sector.totalCapacity ?? mapData.totalCapacity) ?? undefined;
  const ticketTypeIds = normalizeStringArray(mapData.ticketTypeIds);
  const ticketTypeNames = normalizeStringArray(mapData.ticketTypeNames);

  const isVenueFrame =
    mapData.isVenueShape === true && mapData.venueVariant === "venue_frame";

  return {
    id: sector.id,
    sectorId: sector.id,
    name: sector.name,
    code: typeof mapData.code === "string" ? mapData.code : undefined,
    ticketTypeId: typeof mapData.ticketTypeId === "string" ? mapData.ticketTypeId : ticketTypeIds[0],
    ticketTypeName: typeof mapData.ticketTypeName === "string" ? mapData.ticketTypeName : undefined,
    ticketTypeIds,
    ticketTypeNames,
    sectorType: normalizeSectorType(sector.sectorType ?? mapData.sectorType, seats.length),
    totalCapacity,
    shapeType: inferredShapeType,
    mapData,
    color,
    bounds: fallbackBounds,
    points,
    label: isVenueFrame && /^venue\s*shape$/i.test(sector.name) ? "" : sector.name,
    seatCount: seats.filter((seat) => !seat.hidden).length,
    seats,
    seatLayout: normalizeSeatLayoutConfig(mapData.seatLayout, fallbackBounds, seats.length),
    visible,
    locked,
    zIndex,
  };
}

export function sortShapesByZIndex(shapes: SeatMapEditorShape[]) {
  return [...shapes].sort((left, right) => left.zIndex - right.zIndex);
}

function isDeletedMapData(mapData?: Record<string, unknown>) {
  return mapData?.deleted === true;
}

export function buildSeatMapEditorDocument(seatMap: OrganizerSeatMap | null): SeatMapEditorDocument | null {
  if (!seatMap) {
    return null;
  }

  const width = Math.max(asNumber(seatMap.backgroundWidth) ?? DEFAULT_CANVAS_WIDTH, 640);
  const height = Math.max(asNumber(seatMap.backgroundHeight) ?? DEFAULT_CANVAS_HEIGHT, 420);
  const shapes = sortShapesByZIndex(
    (seatMap.sectors ?? [])
      .filter((sector) => !isDeletedMapData(sector.mapData))
      .map((sector, index) => buildShape(sector, index, width, height)),
  );

  return {
    width,
    height,
    referenceImageUrl: seatMap.backgroundImageUrl,
    shapes,
  };
}

export function getVisibleSeatCount(seats: SeatMapEditorSeat[]) {
  return seats.filter((seat) => !seat.hidden).length;
}

export function translateShape(shape: SeatMapEditorShape, deltaX: number, deltaY: number): SeatMapEditorShape {
  return {
    ...shape,
    mapData: translateMapData(shape.mapData, deltaX, deltaY),
    bounds: {
      ...shape.bounds,
      x: shape.bounds.x + deltaX,
      y: shape.bounds.y + deltaY,
    },
    seats: shape.seats.map((seat) => ({
      ...seat,
      x: seat.x + deltaX,
      y: seat.y + deltaY,
    })),
  };
}

export function scalePolygonPoints(points: number[], scaleX: number, scaleY: number): number[] {
  return points.map((point, index) => (index % 2 === 0 ? point * scaleX : point * scaleY));
}

export function scaleSeatsWithinBounds(
  seats: SeatMapEditorSeat[],
  previousBounds: SeatMapEditorBounds,
  nextBounds: SeatMapEditorBounds,
): SeatMapEditorSeat[] {
  const safeWidth = previousBounds.width || 1;
  const safeHeight = previousBounds.height || 1;

  return seats.map((seat) => {
    const relativeX = (seat.x - previousBounds.x) / safeWidth;
    const relativeY = (seat.y - previousBounds.y) / safeHeight;

    return {
      ...seat,
      x: nextBounds.x + relativeX * nextBounds.width,
      y: nextBounds.y + relativeY * nextBounds.height,
    };
  });
}

function rotatePointAroundCenter(
  x: number,
  y: number,
  centerX: number,
  centerY: number,
  rotationDeg: number,
) {
  if (!rotationDeg) {
    return { x, y };
  }
  const rad = (rotationDeg * Math.PI) / 180;
  const cos = Math.cos(rad);
  const sin = Math.sin(rad);
  const dx = x - centerX;
  const dy = y - centerY;
  return {
    x: centerX + dx * cos - dy * sin,
    y: centerY + dx * sin + dy * cos,
  };
}

function buildTransformedSectionOutlinePoints(
  rowGrid: Array<Array<{ x: number; y: number }>>,
  padding: number,
  rotationDeg: number,
): number[] | undefined {
  const nonEmptyRows = rowGrid.filter((row) => row.length > 0);
  if (nonEmptyRows.length === 0) {
    return undefined;
  }

  const rad = (rotationDeg * Math.PI) / 180;
  const fallbackDirX = Math.cos(rad);
  const fallbackDirY = Math.sin(rad);
  const fallbackNormX = -Math.sin(rad);
  const fallbackNormY = Math.cos(rad);

  const getRowTangent = (row: Array<{ x: number; y: number }>, idx: number) => {
    if (row.length < 2) {
      return { x: fallbackDirX, y: fallbackDirY };
    }
    const prev = row[Math.max(0, idx - 1)];
    const next = row[Math.min(row.length - 1, idx + 1)];
    const dx = next.x - prev.x;
    const dy = next.y - prev.y;
    const len = Math.hypot(dx, dy);
    return len > 1e-4 ? { x: dx / len, y: dy / len } : { x: fallbackDirX, y: fallbackDirY };
  };

  const getCrossRowNormal = (rowIdx: number, colIdx: number) => {
    if (nonEmptyRows.length < 2) {
      const t = getRowTangent(nonEmptyRows[rowIdx], colIdx);
      return { x: -t.y, y: t.x };
    }
    const rPrev = Math.max(0, rowIdx - 1);
    const rNext = Math.min(nonEmptyRows.length - 1, rowIdx + 1);
    const rowA = nonEmptyRows[rPrev];
    const rowB = nonEmptyRows[rNext];
    const idxA = Math.min(colIdx, rowA.length - 1);
    const idxB = Math.min(colIdx, rowB.length - 1);
    const dx = rowB[idxB].x - rowA[idxA].x;
    const dy = rowB[idxB].y - rowA[idxA].y;
    const len = Math.hypot(dx, dy);
    return len > 1e-4 ? { x: dx / len, y: dy / len } : { x: fallbackNormX, y: fallbackNormY };
  };

  const outlinePoints: Array<{ x: number; y: number }> = [];
  const firstRow = nonEmptyRows[0];
  const lastRowIdx = nonEmptyRows.length - 1;
  const lastRow = nonEmptyRows[lastRowIdx];

  // Top/inner edge (left to right along first row)
  firstRow.forEach((pt, idx) => {
    const t = getRowTangent(firstRow, idx);
    const n = getCrossRowNormal(0, idx);
    const sideOffset = idx === 0 ? -padding : idx === firstRow.length - 1 ? padding : 0;
    outlinePoints.push({
      x: pt.x - n.x * padding + t.x * sideOffset,
      y: pt.y - n.y * padding + t.y * sideOffset,
    });
  });

  // Right edge (top to bottom along rightmost seat of intermediate rows)
  for (let r = 1; r < lastRowIdx; r += 1) {
    const row = nonEmptyRows[r];
    const lastIdx = row.length - 1;
    const rightPt = row[lastIdx];
    const t = getRowTangent(row, lastIdx);
    outlinePoints.push({
      x: rightPt.x + t.x * padding,
      y: rightPt.y + t.y * padding,
    });
  }

  // Bottom/outer edge (right to left along last row)
  for (let idx = lastRow.length - 1; idx >= 0; idx -= 1) {
    const pt = lastRow[idx];
    const t = getRowTangent(lastRow, idx);
    const n = getCrossRowNormal(lastRowIdx, idx);
    const sideOffset = idx === lastRow.length - 1 ? padding : idx === 0 ? -padding : 0;
    outlinePoints.push({
      x: pt.x + n.x * padding + t.x * sideOffset,
      y: pt.y + n.y * padding + t.y * sideOffset,
    });
  }

  // Left edge (bottom to top along leftmost seat of intermediate rows)
  for (let r = lastRowIdx - 1; r >= 1; r -= 1) {
    const row = nonEmptyRows[r];
    const leftPt = row[0];
    const t = getRowTangent(row, 0);
    outlinePoints.push({
      x: leftPt.x - t.x * padding,
      y: leftPt.y - t.y * padding,
    });
  }

  const flattened: number[] = [];
  outlinePoints.forEach((pt) => {
    flattened.push(Number(pt.x.toFixed(2)), Number(pt.y.toFixed(2)));
  });
  return flattened.length >= 6 ? flattened : undefined;
}

export function generateSeatsForShape(
  shape: SeatMapEditorShape,
  layoutOverrides?: Partial<SeatMapEditorSeatLayoutConfig>,
): SeatMapEditorShape {
  const seatLayout: SeatMapEditorSeatLayoutConfig = {
    ...shape.seatLayout,
    ...layoutOverrides,
    rows: Math.max(1, Math.round(layoutOverrides?.rows ?? shape.seatLayout.rows)),
    seatsPerRow: Math.max(
      1,
      Math.round(layoutOverrides?.seatsPerRow ?? shape.seatLayout.seatsPerRow),
    ),
    gapX: Math.max(8, layoutOverrides?.gapX ?? shape.seatLayout.gapX),
    gapY: Math.max(8, layoutOverrides?.gapY ?? shape.seatLayout.gapY),
    paddingX: Math.max(4, layoutOverrides?.paddingX ?? shape.seatLayout.paddingX),
    paddingY: Math.max(4, layoutOverrides?.paddingY ?? shape.seatLayout.paddingY),
    offsetX: layoutOverrides?.offsetX ?? shape.seatLayout.offsetX,
    offsetY: layoutOverrides?.offsetY ?? shape.seatLayout.offsetY,
    mode: layoutOverrides?.mode ?? shape.seatLayout.mode ?? "grid",
    seatRadius: Math.max(4, layoutOverrides?.seatRadius ?? shape.seatLayout.seatRadius),
    rowStartCharCode: Math.round(
      layoutOverrides?.rowStartCharCode ?? shape.seatLayout.rowStartCharCode,
    ),
    seatStartNumber: Math.max(
      1,
      Math.round(layoutOverrides?.seatStartNumber ?? shape.seatLayout.seatStartNumber),
    ),
    rotation: Math.max(
      -180,
      Math.min(180, Math.round(layoutOverrides?.rotation ?? shape.seatLayout.rotation ?? 0)),
    ),
    curve: Math.max(
      -100,
      Math.min(100, Math.round(layoutOverrides?.curve ?? shape.seatLayout.curve ?? 0)),
    ),
    stretch: Math.max(
      -100,
      Math.min(100, Math.round(layoutOverrides?.stretch ?? shape.seatLayout.stretch ?? 0)),
    ),
    pathBasedLayout: Boolean(
      layoutOverrides?.pathBasedLayout ?? shape.seatLayout.pathBasedLayout ?? false,
    ),
    rowLabelPosition:
      layoutOverrides?.rowLabelPosition ?? shape.seatLayout.rowLabelPosition ?? "left",
    rowAlignment:
      layoutOverrides?.rowAlignment ?? shape.seatLayout.rowAlignment ?? "center",
    outlineMode:
      layoutOverrides?.outlineMode ?? shape.seatLayout.outlineMode ?? "auto",
  };

  const isSeatedGrid = shape.mapData?.presetType === "seated_grid";
  const isRectGridShape =
    isSeatedGrid ||
    shape.shapeType === "roundedRect" ||
    shape.shapeType === "rectangle" ||
    shape.shapeType === "rect";
  const padX = isSeatedGrid
    ? EDITOR_GRID_CELL_SIZE / 2
    : Math.min(Math.max(12, shape.bounds.width * 0.09), shape.bounds.width * 0.25);
  const padY = isSeatedGrid
    ? EDITOR_GRID_CELL_SIZE / 2
    : Math.min(Math.max(14, shape.bounds.height * 0.14), shape.bounds.height * 0.28);

  const shouldResizeForSpacing =
    isRectGridShape &&
    (layoutOverrides?.gapX !== undefined || layoutOverrides?.gapY !== undefined);
  const effectiveBounds = shouldResizeForSpacing
    ? {
        ...shape.bounds,
        width:
          layoutOverrides?.gapX !== undefined
            ? Math.max(
                40,
                Math.round(padX * 2 + Math.max(seatLayout.seatsPerRow - 1, 0) * seatLayout.gapX),
              )
            : shape.bounds.width,
        height:
          layoutOverrides?.gapY !== undefined
            ? Math.max(
                40,
                Math.round(padY * 2 + Math.max(seatLayout.rows - 1, 0) * seatLayout.gapY),
              )
            : shape.bounds.height,
      }
    : shape.bounds;

  const polygon =
    shape.shapeType === "polygon" && shape.points.length >= 6 ? buildRelativePolygonPoints(shape) : [];
  const existingSeatMap = new Map<string, SeatMapEditorSeat>(
    shape.seats.map((seat) => [`${seat.rowName ?? ""}::${seat.seatNumber ?? ""}`, seat] as const),
  );
  const nextSeats: SeatMapEditorSeat[] = [];
  const centerX = effectiveBounds.x + effectiveBounds.width / 2;
  const centerY = effectiveBounds.y + effectiveBounds.height / 2;
  const rotationDeg = seatLayout.rotation ?? 0;

  if (seatLayout.mode === "table") {
    const tableSeatsCount = Math.max(2, Math.min(24, seatLayout.seatsPerRow));
    const orbitRadius = Math.max(
      Math.min(shape.bounds.width, shape.bounds.height) / 2 - Math.max(seatLayout.seatRadius * 2.2, 14),
      22,
    );
    const rowName = formatRowName(0, seatLayout.rowStartCharCode);

    for (let seatIndex = 0; seatIndex < tableSeatsCount; seatIndex += 1) {
      const angleDeg = -90 + (seatIndex * 360) / tableSeatsCount + rotationDeg;
      const angleRad = (angleDeg * Math.PI) / 180;
      const x = centerX + Math.cos(angleRad) * orbitRadius + seatLayout.offsetX;
      const y = centerY + Math.sin(angleRad) * orbitRadius + seatLayout.offsetY;
      const seatNumber = String(seatLayout.seatStartNumber + seatIndex);
      const seatKey = `${rowName}::${seatNumber}`;
      const existingSeat = existingSeatMap.get(seatKey);

      nextSeats.push({
        id: existingSeat?.id ?? createClientId(),
        label: buildSeatLabel(rowName, seatNumber),
        rowName,
        seatNumber,
        ticketTypeId: existingSeat?.ticketTypeId,
        colorCode: existingSeat?.colorCode,
        seatType: existingSeat?.seatType,
        x,
        y,
        inventoryStatus: existingSeat?.inventoryStatus,
        hidden: existingSeat?.hidden ?? false,
        manualAdjusted: false,
      });
    }

    return {
      ...shape,
      sectorType: GENERATED_SEAT_SECTOR_TYPE,
      seatLayout: {
        ...seatLayout,
        rows: 1,
        seatsPerRow: tableSeatsCount,
      },
      seats: nextSeats,
      seatCount: getVisibleSeatCount(nextSeats),
    };
  }

  if (seatLayout.mode === "arc" || seatLayout.mode === "fan") {
    const mapData = shape.mapData ?? {};
    const cx = asNumber(mapData.cx) ?? centerX;
    const cy = asNumber(mapData.cy) ?? centerY;
    const rawStartAngle = asNumber(mapData.startAngle) ?? -50;
    const rawEndAngle = asNumber(mapData.endAngle) ?? 50;
    const angleSpan = rawEndAngle - rawStartAngle;
    const angleInset = Math.sign(angleSpan || 1) * Math.min(Math.abs(angleSpan) * 0.08, 4.5);
    const startAngle = seatLayout.seatsPerRow > 1 ? rawStartAngle + angleInset : (rawStartAngle + rawEndAngle) / 2;
    const endAngle = seatLayout.seatsPerRow > 1 ? rawEndAngle - angleInset : (rawStartAngle + rawEndAngle) / 2;

    const rawInner =
      seatLayout.mode === "fan"
        ? Math.max(asNumber(mapData.innerRadius) ?? 42, 24)
        : Math.max(asNumber(mapData.innerRadius) ?? Math.min(shape.bounds.width, shape.bounds.height) * 0.35, 24);
    const rawOuter = asNumber(mapData.outerRadius);
    let startRadius = rawInner;
    let rowSpacing = seatLayout.gapY;
    if (rawOuter !== null && rawOuter > rawInner + 16) {
      const radialMargin = Math.min(18, Math.max(8, (rawOuter - rawInner) * 0.14));
      const usableBand = Math.max(rawOuter - rawInner - radialMargin * 2, 0);
      startRadius = rawInner + radialMargin + (seatLayout.rows === 1 ? usableBand / 2 : 0);
      rowSpacing = seatLayout.rows > 1 ? usableBand / (seatLayout.rows - 1) : 0;
    }

    generateRingSeats({
      cx,
      cy,
      startRadius,
      rowSpacing,
      rows: seatLayout.rows,
      startAngle,
      endAngle,
      seatsPerRow: seatLayout.seatsPerRow,
    }).forEach((point) => {
      const rowName = formatRowName(point.rowIndex, seatLayout.rowStartCharCode);
      const seatNumber = String(seatLayout.seatStartNumber + point.seatIndex);
      const seatKey = `${rowName}::${seatNumber}`;
      const existingSeat = existingSeatMap.get(seatKey);
      const rotated = rotatePointAroundCenter(
        point.x + seatLayout.offsetX,
        point.y + seatLayout.offsetY,
        centerX,
        centerY,
        rotationDeg,
      );
      nextSeats.push({
        id: existingSeat?.id ?? createClientId(),
        label: buildSeatLabel(rowName, seatNumber),
        rowName,
        seatNumber,
        ticketTypeId: existingSeat?.ticketTypeId,
        colorCode: existingSeat?.colorCode,
        seatType: existingSeat?.seatType,
        x: rotated.x,
        y: rotated.y,
        inventoryStatus: existingSeat?.inventoryStatus,
        hidden: existingSeat?.hidden ?? false,
        manualAdjusted: false,
      });
    });

    return {
      ...shape,
      sectorType: GENERATED_SEAT_SECTOR_TYPE,
      seatLayout,
      seats: nextSeats,
      seatCount: getVisibleSeatCount(nextSeats),
    };
  }

  const usableHeight = Math.max(effectiveBounds.height - padY * 2, 1);
  const stretchRatio = (seatLayout.stretch ?? 0) / 100;
  const curveRatio = (seatLayout.curve ?? 0) / 100;
  const absCurveRatio = Math.abs(curveRatio);
  const rowAlignment = seatLayout.rowAlignment ?? "center";
  const rowGridForOutline: Array<Array<{ x: number; y: number }>> = [];

  // Concentric Horseshoe Geometry Engine:
  // Builds an innermost U-shaped base path (left tangential arm + rounded bottom arc + right tangential arm)
  // and offsets strictly OUTWARD (d >= 0) for subsequent rows so curves never self-intersect, cusp, or
  // converge toward a focal point. Seats are distributed along each row by exact arc-length parameterization.
  const sampleConcentricHorseshoePoint = (
    rowSpanX: number,
    outwardOffset: number,
    colProgress: number,
  ) => {
    const alpha = absCurveRatio * Math.PI; // 0..180 deg rounded bottom
    const halfAlpha = alpha / 2;
    const armLen = absCurveRatio * Math.max(rowSpanX * 0.24, usableHeight * 0.36);
    const minInnerRadius = Math.max(rowSpanX * 0.18, seatLayout.seatRadius * 6, 56);
    const innerArcTargetSpan = Math.max(
      rowSpanX - 2 * armLen * (1 - 0.35 * absCurveRatio),
      rowSpanX * 0.56,
    );
    const innerRadius = Math.max(innerArcTargetSpan / Math.max(alpha, 1e-4), minInnerRadius);

    // Strictly outward radius from inner U-curve: guarantees zero cusps and zero radial convergence
    const radius = innerRadius + Math.max(0, outwardOffset);
    const arcLen = radius * alpha;
    const totalPathLen = 2 * armLen + arcLen;
    const s = Math.max(0, Math.min(1, colProgress)) * totalPathLen;

    const sinHalf = Math.sin(halfAlpha);
    const cosHalf = Math.cos(halfAlpha);
    // Left & right arc-to-arm tangent joints (C1 continuous)
    const leftJointX = -radius * sinHalf;
    const leftJointY = radius * cosHalf;
    const rightJointX = radius * sinHalf;
    const rightJointY = radius * cosHalf;
    // Outward unit tangents along left and right arms
    const leftArmDirX = -cosHalf;
    const leftArmDirY = -sinHalf;
    const rightArmDirX = cosHalf;
    const rightArmDirY = -sinHalf;

    let x = 0;
    let y = 0;
    if (s < armLen) {
      const distFromJoint = armLen - s;
      x = leftJointX + leftArmDirX * distFromJoint;
      y = leftJointY + leftArmDirY * distFromJoint;
    } else if (s <= armLen + arcLen) {
      const thetaStart = Math.PI / 2 + halfAlpha;
      const theta = thetaStart - (s - armLen) / Math.max(radius, 1e-4);
      x = radius * Math.cos(theta);
      y = radius * Math.sin(theta);
    } else {
      const distFromJoint = s - (armLen + arcLen);
      x = rightJointX + rightArmDirX * distFromJoint;
      y = rightJointY + rightArmDirY * distFromJoint;
    }

    return {
      x,
      y: curveRatio < 0 ? -y : y,
    };
  };

  const horseshoeBandThickness = usableHeight * (1 - 0.22 * absCurveRatio);

  interface PendingSeatPoint {
    rowIndex: number;
    outlineRowIndex: number;
    outlineColIndex: number;
    rowName: string;
    seatNumber: string;
    px: number;
    py: number;
  }

  const pendingPoints: PendingSeatPoint[] = [];

  const isPathBasedLayout = Boolean(seatLayout.pathBasedLayout);
  const usableWidth = Math.max(effectiveBounds.width - padX * 2, 1);

  for (let rowIndex = 0; rowIndex < seatLayout.rows; rowIndex += 1) {
    const rowName = formatRowName(rowIndex, seatLayout.rowStartCharCode);
    const rowProgress = seatLayout.rows === 1 ? 0.5 : rowIndex / (seatLayout.rows - 1);
    const baseLocalY = padY + rowProgress * usableHeight + seatLayout.offsetY;

    let rowMinX = padX;
    let rowMaxX = Math.max(effectiveBounds.width - padX, padX);

    if (shape.shapeType === "polygon" && polygon.length >= 3) {
      const intersections: number[] = [];
      for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
        const p1 = polygon[j];
        const p2 = polygon[i];
        if ((p1.y <= baseLocalY && p2.y > baseLocalY) || (p2.y <= baseLocalY && p1.y > baseLocalY)) {
          const intersectX = p1.x + ((baseLocalY - p1.y) * (p2.x - p1.x)) / (p2.y - p1.y || 1e-6);
          intersections.push(intersectX);
        }
      }
      if (intersections.length >= 2) {
        const leftEdge = Math.min(...intersections);
        const rightEdge = Math.max(...intersections);
        const polyPad = Math.min(14, Math.max(6, (rightEdge - leftEdge) * 0.08));
        rowMinX = leftEdge + polyPad;
        rowMaxX = Math.max(rightEdge - polyPad, rowMinX);
      }
    }

    const baseSpanX = Math.max(rowMaxX - rowMinX, 0);

    for (let seatIndex = 0; seatIndex < seatLayout.seatsPerRow; seatIndex += 1) {
      const colProgress = seatLayout.seatsPerRow === 1 ? 0.5 : seatIndex / (seatLayout.seatsPerRow - 1);
      const effectiveRowProgress = isPathBasedLayout ? 1 - colProgress : rowProgress;
      const effectiveColProgress = isPathBasedLayout ? 1 - rowProgress : colProgress;

      let widthMultiplier = 1;
      if (stretchRatio > 0) {
        widthMultiplier = (1 - stretchRatio * 0.65) + effectiveRowProgress * (stretchRatio * 1.0);
      } else if (stretchRatio < 0) {
        const absStretch = Math.abs(stretchRatio);
        widthMultiplier = (1 + absStretch * 0.35) - effectiveRowProgress * (absStretch * 1.0);
      }

      const activeBaseSpanX = isPathBasedLayout ? usableWidth : baseSpanX;
      const stretchedSpanX = Math.max(activeBaseSpanX * widthMultiplier, 12);
      const spanDelta = activeBaseSpanX - stretchedSpanX;
      const activeMinX = isPathBasedLayout ? padX : rowMinX;
      const alignedMinX =
        rowAlignment === "left"
          ? activeMinX
          : rowAlignment === "right"
            ? activeMinX + spanDelta
            : activeMinX + spanDelta / 2;

      const flatLocalX = alignedMinX + effectiveColProgress * stretchedSpanX + seatLayout.offsetX;
      const flatLocalY = padY + effectiveRowProgress * usableHeight + seatLayout.offsetY;

      if (
        (shape.shapeType === "circle" || shape.shapeType === "ellipse") &&
        !isPointInsideEllipse(flatLocalX, flatLocalY, shape.bounds, seatLayout.seatRadius)
      ) {
        continue;
      }

      let px = flatLocalX;
      let py = flatLocalY;

      if (curveRatio !== 0) {
        const outwardRowOffset =
          curveRatio >= 0
            ? effectiveRowProgress * horseshoeBandThickness
            : (1 - effectiveRowProgress) * horseshoeBandThickness;
        const pt = sampleConcentricHorseshoePoint(
          stretchedSpanX,
          outwardRowOffset,
          effectiveColProgress,
        );
        px = pt.x;
        py = pt.y;
      }

      pendingPoints.push({
        rowIndex,
        outlineRowIndex: isPathBasedLayout ? seatLayout.seatsPerRow - 1 - seatIndex : rowIndex,
        outlineColIndex: isPathBasedLayout ? seatLayout.rows - 1 - rowIndex : seatIndex,
        rowName,
        seatNumber: String(seatLayout.seatStartNumber + seatIndex),
        px,
        py,
      });
    }
  }

  let shiftX = 0;
  let shiftY = 0;
  if (curveRatio !== 0 && pendingPoints.length > 0) {
    let minPx = Infinity;
    let maxPx = -Infinity;
    let minPy = Infinity;
    let maxPy = -Infinity;
    for (const pt of pendingPoints) {
      if (pt.px < minPx) minPx = pt.px;
      if (pt.px > maxPx) maxPx = pt.px;
      if (pt.py < minPy) minPy = pt.py;
      if (pt.py > maxPy) maxPy = pt.py;
    }
    shiftX = effectiveBounds.width / 2 - (minPx + maxPx) / 2 + seatLayout.offsetX;
    shiftY = effectiveBounds.height / 2 - (minPy + maxPy) / 2 + seatLayout.offsetY;
  }

  const rowPointsMap = new Map<number, Array<{ colIdx: number; x: number; y: number }>>();

  for (const pt of pendingPoints) {
    const localX = pt.px + shiftX;
    const localY = pt.py + shiftY;
    const rawAbsX = effectiveBounds.x + localX;
    const rawAbsY = effectiveBounds.y + localY;
    const rotated = rotatePointAroundCenter(rawAbsX, rawAbsY, centerX, centerY, rotationDeg);

    const rowList = rowPointsMap.get(pt.outlineRowIndex) ?? [];
    rowList.push({ colIdx: pt.outlineColIndex, x: rotated.x, y: rotated.y });
    rowPointsMap.set(pt.outlineRowIndex, rowList);

    const seatKey = `${pt.rowName}::${pt.seatNumber}`;
    const existingSeat = existingSeatMap.get(seatKey);
    nextSeats.push({
      id: existingSeat?.id ?? createClientId(),
      label: buildSeatLabel(pt.rowName, pt.seatNumber),
      rowName: pt.rowName,
      seatNumber: pt.seatNumber,
      ticketTypeId: existingSeat?.ticketTypeId,
      colorCode: existingSeat?.colorCode,
      seatType: existingSeat?.seatType,
      x: rotated.x,
      y: rotated.y,
      inventoryStatus: existingSeat?.inventoryStatus,
      hidden: existingSeat?.hidden ?? false,
      manualAdjusted: false,
    });
  }

  const outlineRowCount = isPathBasedLayout ? seatLayout.seatsPerRow : seatLayout.rows;
  for (let rIdx = 0; rIdx < outlineRowCount; rIdx += 1) {
    const rowPts = rowPointsMap.get(rIdx);
    if (rowPts && rowPts.length > 0) {
      rowPts.sort((a, b) => a.colIdx - b.colIdx);
      rowGridForOutline.push(rowPts.map(({ x, y }) => ({ x, y })));
    }
  }

  const hasParametricTransform =
    Math.abs(rotationDeg) > 0.5 ||
    Math.abs(seatLayout.curve ?? 0) > 0.5 ||
    Math.abs(seatLayout.stretch ?? 0) > 0.5;

  const transformedOutlinePoints =
    hasParametricTransform && seatLayout.outlineMode !== "full"
      ? buildTransformedSectionOutlinePoints(
          rowGridForOutline,
          Math.max(seatLayout.seatRadius * 2.4, 15),
          rotationDeg,
        )
      : undefined;

  const nextMapData: Record<string, unknown> = {
    ...(shape.mapData ?? {}),
    seatLayout,
    outlineMode: seatLayout.outlineMode ?? "auto",
  };
  if (shape.mapData?.bounds) {
    nextMapData.bounds = effectiveBounds;
  }

  if (transformedOutlinePoints) {
    nextMapData.transformedOutlinePoints = transformedOutlinePoints;
  } else {
    delete nextMapData.transformedOutlinePoints;
  }

  return {
    ...shape,
    bounds: effectiveBounds,
    sectorType: GENERATED_SEAT_SECTOR_TYPE,
    mapData: nextMapData,
    seatLayout,
    seats: nextSeats,
    seatCount: getVisibleSeatCount(nextSeats),
  };
}

export function setMultipleSeatsHiddenState(
  shape: SeatMapEditorShape,
  seatIds: string[],
  hidden: boolean,
): SeatMapEditorShape {
  const idSet = new Set(seatIds);
  const nextSeats = shape.seats.map((seat) =>
    idSet.has(seat.id)
      ? {
          ...seat,
          hidden,
        }
      : seat,
  );

  return {
    ...shape,
    seats: nextSeats,
    seatCount: getVisibleSeatCount(nextSeats),
  };
}

export const EDITOR_GRID_CELL_SIZE = 22;

export function estimateSeatGridFromBounds(bounds: SeatMapEditorBounds): {
  rows: number;
  cols: number;
} {
  const cols = Math.max(2, Math.min(36, Math.round(Math.max(bounds.width, EDITOR_GRID_CELL_SIZE * 2) / EDITOR_GRID_CELL_SIZE)));
  const rows = Math.max(1, Math.min(26, Math.round(Math.max(bounds.height, EDITOR_GRID_CELL_SIZE) / EDITOR_GRID_CELL_SIZE)));
  return { rows, cols };
}

export function createSeatRowsShapeFromBounds(
  bounds: SeatMapEditorBounds,
  existingShapes: SeatMapEditorShape[],
): SeatMapEditorShape {
  const snappedX = Math.round(bounds.x / EDITOR_GRID_CELL_SIZE) * EDITOR_GRID_CELL_SIZE;
  const snappedY = Math.round(bounds.y / EDITOR_GRID_CELL_SIZE) * EDITOR_GRID_CELL_SIZE;
  const { rows, cols } = estimateSeatGridFromBounds(bounds);
  const normalizedBounds: SeatMapEditorBounds = {
    x: snappedX,
    y: snappedY,
    width: Math.max(cols * EDITOR_GRID_CELL_SIZE, EDITOR_GRID_CELL_SIZE * 2),
    height: Math.max(rows * EDITOR_GRID_CELL_SIZE, EDITOR_GRID_CELL_SIZE),
  };
  const seatedCount = existingShapes.filter((s) => s.sectorType === "SEATED").length;
  const name = `KHU GHẾ ${seatedCount + 1}`;
  const color = DEFAULT_SECTOR_COLORS[existingShapes.length % DEFAULT_SECTOR_COLORS.length];
  const baseLayout = buildDefaultSeatLayoutConfig(normalizedBounds, rows * cols);
  const seatLayout: SeatMapEditorSeatLayoutConfig = {
    ...baseLayout,
    rows,
    seatsPerRow: cols,
    gapX: EDITOR_GRID_CELL_SIZE,
    gapY: EDITOR_GRID_CELL_SIZE,
    paddingX: EDITOR_GRID_CELL_SIZE / 2,
    paddingY: EDITOR_GRID_CELL_SIZE / 2,
    seatRadius: 8.5,
    rowLabelPosition: "left",
    rowAlignment: "center",
    outlineMode: "auto",
  };

  const baseShape: SeatMapEditorShape = {
    id: createClientId(),
    sectorId: createClientId(),
    name,
    sectorType: "SEATED",
    totalCapacity: rows * cols,
    shapeType: "roundedRect",
    mapData: {
      shapeType: "roundedRect",
      presetType: "seated_grid",
      cornerRadius: 0,
      bounds: normalizedBounds,
      seatLayout,
    },
    color,
    bounds: normalizedBounds,
    points: [],
    label: name,
    seatCount: 0,
    seats: [],
    seatLayout,
    visible: true,
    locked: false,
    zIndex: existingShapes.length,
  };

  return generateSeatsForShape(baseShape, seatLayout);
}

function buildSmartCloneName(baseName: string, existingNames: Set<string>): string {
  const trimmed = baseName.trim();
  const trailingNumberMatch = trimmed.match(/^(.*?)(\d+)$/);
  if (trailingNumberMatch) {
    const prefix = trailingNumberMatch[1];
    let num = Number(trailingNumberMatch[2]) + 1;
    while (existingNames.has(`${prefix}${num}`)) {
      num += 1;
    }
    return `${prefix}${num}`;
  }

  const trailingLetterMatch = trimmed.match(/^(.*\s+)([A-Z])$/);
  if (trailingLetterMatch) {
    const prefix = trailingLetterMatch[1];
    let code = trailingLetterMatch[2].charCodeAt(0) + 1;
    while (code <= 90 && existingNames.has(`${prefix}${String.fromCharCode(code)}`)) {
      code += 1;
    }
    if (code <= 90) {
      return `${prefix}${String.fromCharCode(code)}`;
    }
  }

  let copyIdx = 2;
  while (existingNames.has(`${trimmed} (${copyIdx})`)) {
    copyIdx += 1;
  }
  return `${trimmed} (${copyIdx})`;
}

export function cloneShape(
  shape: SeatMapEditorShape,
  existingShapes: SeatMapEditorShape[],
  canvasWidth: number,
  canvasHeight: number,
): SeatMapEditorShape {
  const existingNames = new Set(existingShapes.map((item) => item.name.trim()));
  const nextName = buildSmartCloneName(shape.name, existingNames);
  const deltaX = shape.bounds.x + shape.bounds.width + 44 <= canvasWidth ? 36 : -36;
  const deltaY = shape.bounds.y + shape.bounds.height + 44 <= canvasHeight ? 36 : -36;
  const translated = translateShape(shape, deltaX, deltaY);

  const nextMapData = translated.mapData ? { ...translated.mapData } : undefined;
  if (nextMapData) {
    delete nextMapData.ticketTypeId;
    delete nextMapData.ticketTypeIds;
    delete nextMapData.ticketTypeName;
    delete nextMapData.ticketTypeNames;
  }

  return {
    ...translated,
    id: createClientId(),
    sectorId: createClientId(),
    name: nextName,
    label: nextName,
    ticketTypeId: undefined,
    ticketTypeName: undefined,
    ticketTypeIds: [],
    ticketTypeNames: [],
    mapData: nextMapData,
    zIndex: existingShapes.length,
    seats: translated.seats.map((seat) => ({
      ...seat,
      id: createClientId(),
      ticketTypeId: undefined,
      colorCode: undefined,
      inventoryStatus: undefined,
    })),
  };
}

export function flipShapeHorizontally(shape: SeatMapEditorShape): SeatMapEditorShape {
  const centerX = shape.bounds.x + shape.bounds.width / 2;
  const nextPoints =
    shape.shapeType === "polygon" && shape.points.length >= 6
      ? shape.points.map((coord, idx) => (idx % 2 === 0 ? shape.bounds.width - coord : coord))
      : shape.points;

  const nextRotation = -(shape.seatLayout.rotation ?? 0);
  const nextMapData: Record<string, unknown> = {
    ...(shape.mapData ?? {}),
  };

  if (Array.isArray(nextMapData.transformedOutlinePoints)) {
    nextMapData.transformedOutlinePoints = nextMapData.transformedOutlinePoints.map((val, idx) => {
      const num = asNumber(val);
      if (num === null) return val;
      return idx % 2 === 0 ? Number((centerX * 2 - num).toFixed(2)) : num;
    });
  }

  const nextSeats = shape.seats.map((seat) => ({
    ...seat,
    x: Number((centerX * 2 - seat.x).toFixed(2)),
  }));

  return {
    ...shape,
    points: nextPoints,
    mapData: nextMapData,
    seatLayout: {
      ...shape.seatLayout,
      rotation: nextRotation,
    },
    seats: nextSeats,
  };
}

export function flipShapeVertically(shape: SeatMapEditorShape): SeatMapEditorShape {
  const centerY = shape.bounds.y + shape.bounds.height / 2;
  const nextPoints =
    shape.shapeType === "polygon" && shape.points.length >= 6
      ? shape.points.map((coord, idx) => (idx % 2 === 1 ? shape.bounds.height - coord : coord))
      : shape.points;

  const nextCurve = -(shape.seatLayout.curve ?? 0);
  const nextStretch = -(shape.seatLayout.stretch ?? 0);
  const nextMapData: Record<string, unknown> = {
    ...(shape.mapData ?? {}),
  };

  if (Array.isArray(nextMapData.transformedOutlinePoints)) {
    nextMapData.transformedOutlinePoints = nextMapData.transformedOutlinePoints.map((val, idx) => {
      const num = asNumber(val);
      if (num === null) return val;
      return idx % 2 === 1 ? Number((centerY * 2 - num).toFixed(2)) : num;
    });
  }

  const nextSeats = shape.seats.map((seat) => ({
    ...seat,
    y: Number((centerY * 2 - seat.y).toFixed(2)),
  }));

  return {
    ...shape,
    points: nextPoints,
    mapData: nextMapData,
    seatLayout: {
      ...shape.seatLayout,
      curve: nextCurve,
      stretch: nextStretch,
    },
    seats: nextSeats,
  };
}

export function moveSeatWithinShape(
  shape: SeatMapEditorShape,
  seatId: string,
  nextX: number,
  nextY: number,
): SeatMapEditorShape {
  const nextSeats = shape.seats.map((seat) =>
    seat.id === seatId
      ? {
          ...seat,
          x: nextX,
          y: nextY,
          manualAdjusted: true,
        }
      : seat,
  );

  return {
    ...shape,
    seats: nextSeats,
    seatCount: getVisibleSeatCount(nextSeats),
  };
}

export function setSeatHiddenState(
  shape: SeatMapEditorShape,
  seatId: string,
  hidden: boolean,
): SeatMapEditorShape {
  const nextSeats = shape.seats.map((seat) =>
    seat.id === seatId
      ? {
          ...seat,
          hidden,
        }
      : seat,
  );

  return {
    ...shape,
    seats: nextSeats,
    seatCount: getVisibleSeatCount(nextSeats),
  };
}

export function restoreHiddenSeats(shape: SeatMapEditorShape): SeatMapEditorShape {
  const nextSeats = shape.seats.map((seat) => ({
    ...seat,
    hidden: false,
  }));

  return {
    ...shape,
    seats: nextSeats,
    seatCount: getVisibleSeatCount(nextSeats),
  };
}

export function createRectangleShape(
  index: number,
  document: SeatMapEditorDocument,
): SeatMapEditorShape {
  const width = Math.max(Math.round(document.width * 0.18), 180);
  const height = Math.max(Math.round(document.height * 0.16), 120);
  const bounds = {
    x: 80 + (index % 3) * 48,
    y: 80 + (index % 4) * 40,
    width,
    height,
  };

  return {
    id: createClientId(),
    sectorId: createClientId(),
    name: `Sector ${index + 1}`,
    sectorType: "STANDING",
    totalCapacity: 0,
    shapeType: "rectangle",
    mapData: { shapeType: "rectangle", bounds },
    color: DEFAULT_SECTOR_COLORS[index % DEFAULT_SECTOR_COLORS.length],
    bounds,
    points: [],
    label: `Sector ${index + 1}`,
    seatCount: 0,
    seats: [],
    seatLayout: buildDefaultSeatLayoutConfig(bounds),
    visible: true,
    locked: false,
    zIndex: index,
  };
}

function createPolygonPresetShape(
  index: number,
  document: SeatMapEditorDocument,
  namePrefix: string,
  pointsFactory: (width: number, height: number) => number[],
  offsetX = 120,
  offsetY = 120,
  stepX = 56,
  stepY = 48,
): SeatMapEditorShape {
  const width = Math.max(Math.round(document.width * 0.18), 180);
  const height = Math.max(Math.round(document.height * 0.16), 120);
  const bounds = {
    x: offsetX + (index % 3) * stepX,
    y: offsetY + (index % 4) * stepY,
    width,
    height,
  };

  const baseShape: SeatMapEditorShape = {
    id: createClientId(),
    sectorId: createClientId(),
    name: `${namePrefix} ${index + 1}`,
    sectorType: "SEATED",
    totalCapacity: 400,
    shapeType: "polygon",
    mapData: { shapeType: "polygon", bounds },
    color: DEFAULT_SECTOR_COLORS[index % DEFAULT_SECTOR_COLORS.length],
    bounds,
    points: pointsFactory(width, height),
    label: `${namePrefix} ${index + 1}`,
    seatCount: 0,
    seats: [],
    seatLayout: buildDefaultSeatLayoutConfig(bounds),
    visible: true,
    locked: false,
    zIndex: index,
  };

  return generateSeatsForShape(baseShape);
}

export function createPolygonShape(
  index: number,
  document: SeatMapEditorDocument,
): SeatMapEditorShape {
  return createPolygonPresetShape(index, document, "Polygon", (width, height) => [
    width * 0.08,
    height * 0.18,
    width * 0.84,
    0,
    width,
    height * 0.54,
    width * 0.74,
    height,
    0,
    height * 0.76,
  ]);
}

export function createCircleShape(
  index: number,
  document: SeatMapEditorDocument,
): SeatMapEditorShape {
  const size = Math.max(Math.round(Math.min(document.width, document.height) * 0.14), 140);
  const bounds = {
    x: 156 + (index % 3) * 52,
    y: 132 + (index % 4) * 44,
    width: size,
    height: size,
  };

  return {
    id: createClientId(),
    sectorId: createClientId(),
    name: `Circle ${index + 1}`,
    sectorType: "STANDING",
    totalCapacity: 0,
    shapeType: "circle",
    mapData: { shapeType: "circle", bounds },
    color: DEFAULT_SECTOR_COLORS[index % DEFAULT_SECTOR_COLORS.length],
    bounds,
    points: [],
    label: `Circle ${index + 1}`,
    seatCount: 0,
    seats: [],
    seatLayout: buildDefaultSeatLayoutConfig(bounds),
    visible: true,
    locked: false,
    zIndex: index,
  };
}

export function createEllipseShape(
  index: number,
  document: SeatMapEditorDocument,
): SeatMapEditorShape {
  const bounds = {
    x: 168 + (index % 3) * 50,
    y: 144 + (index % 4) * 42,
    width: Math.max(Math.round(document.width * 0.18), 180),
    height: Math.max(Math.round(document.height * 0.13), 110),
  };

  return {
    id: createClientId(),
    sectorId: createClientId(),
    name: `Ellipse ${index + 1}`,
    sectorType: "STANDING",
    totalCapacity: 0,
    shapeType: "ellipse",
    mapData: { shapeType: "ellipse", bounds },
    color: DEFAULT_SECTOR_COLORS[index % DEFAULT_SECTOR_COLORS.length],
    bounds,
    points: [],
    label: `Ellipse ${index + 1}`,
    seatCount: 0,
    seats: [],
    seatLayout: buildDefaultSeatLayoutConfig(bounds),
    visible: true,
    locked: false,
    zIndex: index,
  };
}

export function createTrapezoidShape(
  index: number,
  document: SeatMapEditorDocument,
): SeatMapEditorShape {
  return createPolygonPresetShape(index, document, "KHÁN ĐÀI VÁT", (width, height) => [
    width * 0.16,
    0,
    width * 0.84,
    0,
    width,
    height,
    0,
    height,
  ], 180, 210, 52, 44);
}

export function createDiamondShape(
  index: number,
  document: SeatMapEditorDocument,
): SeatMapEditorShape {
  return createPolygonPresetShape(index, document, "Diamond", (width, height) => [
    width * 0.5,
    0,
    width,
    height * 0.5,
    width * 0.5,
    height,
    0,
    height * 0.5,
  ], 140, 118, 50, 42);
}

export function createHexagonShape(
  index: number,
  document: SeatMapEditorDocument,
): SeatMapEditorShape {
  return createPolygonPresetShape(index, document, "Hexagon", (width, height) => [
    width * 0.22,
    0,
    width * 0.78,
    0,
    width,
    height * 0.5,
    width * 0.78,
    height,
    width * 0.22,
    height,
    0,
    height * 0.5,
  ], 148, 124, 46, 40);
}

export function serializeEditorDocument(document: SeatMapEditorDocument) {
  return JSON.stringify({
    version: DRAFT_VERSION,
    document,
  });
}

function normalizeParsedEditorDocument(parsed: unknown): SeatMapEditorDocument | null {
  try {
    const candidate = parsed as {
      version?: number;
      document?: SeatMapEditorDocument;
      seatMapEditorDocument?: SeatMapEditorDocument;
    };
    const documentSource =
      candidate.seatMapEditorDocument ?? candidate.document;

    if (!documentSource) {
      return null;
    }

    return {
      width: Math.max(asNumber(documentSource.width) ?? DEFAULT_CANVAS_WIDTH, 640),
      height: Math.max(asNumber(documentSource.height) ?? DEFAULT_CANVAS_HEIGHT, 420),
      referenceImageUrl: documentSource.referenceImageUrl,
      shapes: sortShapesByZIndex(
        (documentSource.shapes ?? []).map((shape, index) => {
          const bounds = {
            x: asNumber(shape.bounds?.x) ?? 0,
            y: asNumber(shape.bounds?.y) ?? 0,
            width: Math.max(asNumber(shape.bounds?.width) ?? 1, 1),
            height: Math.max(asNumber(shape.bounds?.height) ?? 1, 1),
          };
          const seats = Array.isArray(shape.seats)
            ? shape.seats.map((seat) => ({
                ...seat,
                x: asNumber(seat.x) ?? 0,
                y: asNumber(seat.y) ?? 0,
                hidden: seat.hidden === true,
                manualAdjusted: seat.manualAdjusted === true,
              }))
            : [];

          return {
            ...shape,
            sectorType: normalizeSectorType(shape.sectorType, seats.length),
            totalCapacity: asNumber(shape.totalCapacity ?? shape.mapData?.totalCapacity) ?? undefined,
            ticketTypeIds: Array.isArray(shape.ticketTypeIds)
              ? shape.ticketTypeIds.filter((item): item is string => typeof item === "string" && item.trim().length > 0)
              : normalizeStringArray(shape.mapData?.ticketTypeIds),
            ticketTypeNames: Array.isArray(shape.ticketTypeNames)
              ? shape.ticketTypeNames.filter((item): item is string => typeof item === "string" && item.trim().length > 0)
              : normalizeStringArray(shape.mapData?.ticketTypeNames),
            mapData: shape.mapData,
            visible: shape.visible !== false,
            locked: shape.locked === true,
            zIndex: asNumber(shape.zIndex) ?? index,
            bounds,
            points: Array.isArray(shape.points)
              ? shape.points.filter((point) => typeof point === "number")
              : [],
            seats,
            seatCount: getVisibleSeatCount(seats),
            seatLayout: normalizeSeatLayoutConfig(shape.seatLayout, bounds, seats.length),
          };
        }),
      ),
    };
  } catch {
    return null;
  }
}

export function deserializeEditorDocument(rawValue: string): SeatMapEditorDocument | null {
  try {
    return normalizeParsedEditorDocument(JSON.parse(rawValue));
  } catch {
    return null;
  }
}

export function exportEditorDocument(document: SeatMapEditorDocument) {
  return JSON.stringify(
    {
      version: DRAFT_VERSION,
      exportedAt: new Date().toISOString(),
      seatMapEditorDocument: document,
    },
    null,
    2,
  );
}

export function importEditorDocument(rawValue: string): SeatMapEditorDocument | null {
  try {
    return normalizeParsedEditorDocument(JSON.parse(rawValue));
  } catch {
    return null;
  }
}

export function buildSeatMapPublishPayload(
  document: SeatMapEditorDocument,
  options?: {
    name?: string;
    backgroundPublicId?: string;
    mapConfig?: Record<string, unknown>;
  },
): OrganizerSeatMapPublishPayload {
  return {
    name: options?.name,
    backgroundImageUrl: document.referenceImageUrl,
    backgroundPublicId: options?.backgroundPublicId,
    backgroundWidth: document.width,
    backgroundHeight: document.height,
    mapConfig: {
      ...(options?.mapConfig ?? {}),
      schemaVersion: 1,
      runtime: {
        renderMode: "vector-first",
        backgroundVisible: false,
      },
    },
    sectors: sortShapesByZIndex(document.shapes).map((shape, index) => {
      const sectorType = normalizeSectorType(shape.sectorType, shape.seats.length);
      const publishedSeats = sectorType === "SEATED" ? shape.seats : [];

      return {
      id: shape.id,
      name: shape.name,
      code: shape.code,
      sectorType,
      totalCapacity:
        sectorType === "STANDING"
          ? Math.max(0, Math.round(asNumber(shape.totalCapacity) ?? 0))
          : getVisibleSeatCount(shape.seats),
      colorCode: shape.color,
      visible: shape.visible,
      displayOrder: index,
      mapData: {
        ...(shape.mapData ?? {}),
        shapeType: shape.shapeType,
        bounds: shape.bounds,
        points:
          shape.shapeType === "polygon"
            ? shape.points.map((point, pointIndex) =>
                pointIndex % 2 === 0 ? point + shape.bounds.x : point + shape.bounds.y,
              )
            : [],
        label: shape.label,
        sectorType,
        totalCapacity:
          sectorType === "STANDING"
            ? Math.max(0, Math.round(asNumber(shape.totalCapacity) ?? 0))
            : getVisibleSeatCount(shape.seats),
        ticketTypeId:
          sectorType === "STANDING"
            ? shape.ticketTypeIds?.[0] ?? shape.ticketTypeId
            : undefined,
        ticketTypeName:
          sectorType === "STANDING"
            ? shape.ticketTypeNames?.[0] ?? shape.ticketTypeName
            : undefined,
        ticketTypeIds:
          sectorType === "STANDING"
            ? shape.ticketTypeIds ?? (shape.ticketTypeId ? [shape.ticketTypeId] : [])
            : undefined,
        ticketTypeNames: sectorType === "STANDING" ? shape.ticketTypeNames : undefined,
        zIndex: shape.zIndex,
        visible: shape.visible,
        locked: shape.locked,
        seatLayout: shape.seatLayout,
      },
      seats: publishedSeats.map((seat) => ({
        id: seat.id,
        rowName: seat.rowName,
        seatNumber: seat.seatNumber,
        seatLabel: seat.label,
        coordX: Number(seat.x.toFixed(2)),
        coordY: Number(seat.y.toFixed(2)),
        ticketTypeId: sectorType === "SEATED" ? seat.ticketTypeId : undefined,
        colorCode: sectorType === "SEATED" ? (seat.colorCode ?? undefined) : undefined,
        isActive: seat.hidden !== true,
        coordMetadata: {
          shapeId: shape.id,
          hidden: seat.hidden === true,
          manualAdjusted: seat.manualAdjusted === true,
          relativeX: Number(((seat.x - shape.bounds.x) / Math.max(shape.bounds.width, 1)).toFixed(4)),
          relativeY: Number(((seat.y - shape.bounds.y) / Math.max(shape.bounds.height, 1)).toFixed(4)),
          seatRadius: shape.seatLayout.seatRadius,
        },
      })),
      };
    }),
  };
}

function escapeXmlAttr(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

export function exportEditorDocumentToSvg(document: SeatMapEditorDocument, artworkOnly = false): string {
  const visibleShapes = sortShapesByZIndex(document.shapes).filter((shape) => shape.visible !== false);
  const serialized = exportEditorDocument(document).replace(/]]>/g, "]]]]><![CDATA[>");

  let minX = Number.POSITIVE_INFINITY;
  let minY = Number.POSITIVE_INFINITY;
  let maxX = Number.NEGATIVE_INFINITY;
  let maxY = Number.NEGATIVE_INFINITY;

  const includePoint = (px: number, py: number) => {
    if (!Number.isFinite(px) || !Number.isFinite(py)) {
      return;
    }
    minX = Math.min(minX, px);
    minY = Math.min(minY, py);
    maxX = Math.max(maxX, px);
    maxY = Math.max(maxY, py);
  };

  const shouldRenderShapeOutline = (shape: SeatMapEditorShape) => {
    const isDecorative = shape.mapData?.decorative === true || shape.mapData?.isVenueShape === true;
    if (isDecorative) {
      return true;
    }
    const isSeatedGrid = shape.mapData?.presetType === "seated_grid";
    const isRoundTable = shape.mapData?.presetType === "round_table" || shape.seatLayout?.mode === "table";
    const outlineMode = String(shape.mapData?.outlineMode ?? shape.seatLayout?.outlineMode ?? "auto");
    if (outlineMode === "none" || isSeatedGrid || (shape.seats.length > 0 && !isRoundTable)) {
      return false;
    }
    return true;
  };

  for (const shape of visibleShapes) {
    const { x, y, width: w, height: h } = shape.bounds;
    if (shouldRenderShapeOutline(shape)) {
      includePoint(x, y);
      includePoint(x + w, y + h);
      if (shape.shapeType === "polygon" && shape.points.length >= 6) {
        for (let i = 0; i < shape.points.length; i += 2) {
          includePoint(x + Number(shape.points[i]), y + Number(shape.points[i + 1]));
        }
      }
    }
    const r = shape.seatLayout?.seatRadius ?? 8;
    for (const seat of shape.seats) {
      if (seat.hidden === true) {
        continue;
      }
      includePoint(seat.x - r, seat.y - r);
      includePoint(seat.x + r, seat.y + r);
    }
  }

  const hasContent = Number.isFinite(minX) && Number.isFinite(minY) && Number.isFinite(maxX) && Number.isFinite(maxY);
  const padding = 48;
  const viewBoxX = !artworkOnly && hasContent ? Math.floor(minX - padding) : 0;
  const viewBoxY = !artworkOnly && hasContent ? Math.floor(minY - padding) : 0;
  const width = artworkOnly ? document.width : hasContent
    ? Math.max(400, Math.ceil(maxX - minX + padding * 2))
    : Math.max(400, Math.round(document.width || 1280));
  const height = artworkOnly ? document.height : hasContent
    ? Math.max(300, Math.ceil(maxY - minY + padding * 2))
    : Math.max(300, Math.round(document.height || 860));

  const shapeNodes = visibleShapes
    .map((shape) => {
      const { x, y, width: w, height: h } = shape.bounds;
      const isSeatedGrid = shape.mapData?.presetType === "seated_grid";
      const isVenueFrame =
        shape.mapData?.isVenueShape === true && shape.mapData?.venueVariant === "venue_frame";
      const isDecorative = shape.mapData?.decorative === true || shape.mapData?.isVenueShape === true;
      const neutralSvgSeatColor = "#94a3b8";
      const fillColor = isDecorative
        ? typeof shape.mapData?.fill === "string" && shape.mapData.fill.trim()
          ? shape.mapData.fill.trim()
          : shape.color || "#e2e8f0"
        : "#cbd5e1";
      const strokeColor = isDecorative ? shape.color || "#64748b" : "#cbd5e1";
      const cornerRadius = asNumber(shape.mapData?.cornerRadius) ?? (isVenueFrame ? 160 : 12);

      let outlineSvg = "";
      if (shouldRenderShapeOutline(shape)) {
        if (shape.shapeType === "circle" || shape.shapeType === "ellipse") {
          outlineSvg = `<ellipse cx="${(x + w / 2).toFixed(2)}" cy="${(y + h / 2).toFixed(2)}" rx="${(w / 2).toFixed(2)}" ry="${(h / 2).toFixed(2)}" fill="${escapeXmlAttr(fillColor)}" fill-opacity="${isSeatedGrid ? "0.06" : "0.28"}" stroke="${escapeXmlAttr(strokeColor)}" stroke-width="2" />`;
        } else if (shape.shapeType === "polygon" && shape.points.length >= 6) {
          const pts: string[] = [];
          for (let i = 0; i < shape.points.length; i += 2) {
            pts.push(`${(x + Number(shape.points[i])).toFixed(2)},${(y + Number(shape.points[i + 1])).toFixed(2)}`);
          }
          outlineSvg = `<polygon points="${pts.join(" ")}" fill="${escapeXmlAttr(fillColor)}" fill-opacity="${isSeatedGrid ? "0.06" : "0.28"}" stroke="${escapeXmlAttr(strokeColor)}" stroke-width="2" />`;
        } else {
          outlineSvg = `<rect x="${x.toFixed(2)}" y="${y.toFixed(2)}" width="${w.toFixed(2)}" height="${h.toFixed(2)}" rx="${cornerRadius}" ry="${cornerRadius}" fill="${escapeXmlAttr(fillColor)}" fill-opacity="${isSeatedGrid ? "0.06" : isVenueFrame ? "0.45" : "0.28"}" stroke="${escapeXmlAttr(strokeColor)}" stroke-width="2" />`;
        }
      }

      const seatsSvg = (artworkOnly ? [] : shape.seats)
        .filter((seat) => seat.hidden !== true)
        .map((seat) => {
          const r = shape.seatLayout?.seatRadius ?? 8;
          const seatFill = seat.ticketTypeId && seat.colorCode ? seat.colorCode : neutralSvgSeatColor;
          return `<circle cx="${seat.x.toFixed(2)}" cy="${seat.y.toFixed(2)}" r="${r}" fill="${escapeXmlAttr(seatFill)}" />`;
        })
        .join("");

      const labelText = (shape.label || "").trim();
      const labelFontSize =
        shape.mapData?.isVenueShape === true
          ? Math.round(Math.min(Math.max(Math.min(w, h) * 0.22, 18), 56))
          : 14;
      const labelSvg =
        labelText && !isSeatedGrid
          ? `<text x="${(x + w / 2).toFixed(2)}" y="${(y + h / 2).toFixed(2)}" text-anchor="middle" dominant-baseline="central" font-family="sans-serif" font-size="${labelFontSize}" font-weight="700" fill="#0f172a">${escapeXmlAttr(labelText)}</text>`
          : "";

      return `<g id="${escapeXmlAttr(shape.id)}" data-sector-name="${escapeXmlAttr(shape.name)}">${outlineSvg}${seatsSvg}${labelSvg}</g>`;
    })
    .join("\n  ");

  return [
    `<?xml version="1.0" encoding="UTF-8"?>`,
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${viewBoxX} ${viewBoxY} ${width} ${height}" width="${width}" height="${height}">`,
    artworkOnly ? "" : `  <metadata id="flash-ticket-seatmap-json"><![CDATA[${serialized}]]></metadata>`,
    `  <rect x="${viewBoxX}" y="${viewBoxY}" width="${width}" height="${height}" fill="#ffffff" />`,
    `  ${shapeNodes}`,
    `</svg>`,
  ].join("\n");
}

export function importEditorDocumentFromSvg(svgText: string): SeatMapEditorDocument | null {
  try {
    const parser = new DOMParser();
    const xmlDoc = parser.parseFromString(svgText, "image/svg+xml");
    const metadataNode = xmlDoc.querySelector("#flash-ticket-seatmap-json");
    const rawJson = metadataNode?.textContent?.trim();
    if (!rawJson) {
      return null;
    }
    const parsed = importEditorDocument(rawJson);
    if (!parsed) {
      return null;
    }
    return {
      ...parsed,
      shapes: parsed.shapes.map((shape) => {
        const isDecorative =
          shape.mapData?.decorative === true || shape.mapData?.isVenueShape === true;
        if (isDecorative) {
          return shape;
        }
        return {
          ...shape,
          color: "#94a3b8",
          seats: shape.seats.map((seat) => ({
            ...seat,
            colorCode: seat.ticketTypeId ? seat.colorCode : undefined,
          })),
        };
      }),
    };
  } catch {
    return null;
  }
}
