export type SeatMapEditorShapeType =
  | "rectangle"
  | "rect"
  | "roundedRect"
  | "polygon"
  | "path"
  | "ringSection"
  | "fan"
  | "stage"
  | "foh"
  | "circle"
  | "ellipse";
export type SeatMapEditorSectorType = "SEATED" | "STANDING";
export type SeatMapEditorSeatLayoutMode = "grid" | "arc" | "fan" | "table";
export type SeatMapEditorTool =
  | "select"
  | "seatRows"
  | "editSeats"
  | "rectangle"
  | "polygon";
export type SeatMapEditorRowLabelPosition = "none" | "left" | "right" | "both";
export type SeatMapEditorRowAlignment = "left" | "center" | "right";
export type SeatMapEditorOutlineMode = "auto" | "full" | "none";

export interface SeatMapEditorBounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface SeatMapEditorSeatLayoutConfig {
  mode?: SeatMapEditorSeatLayoutMode;
  rows: number;
  seatsPerRow: number;
  gapX: number;
  gapY: number;
  paddingX: number;
  paddingY: number;
  offsetX: number;
  offsetY: number;
  seatRadius: number;
  rowStartCharCode: number;
  seatStartNumber: number;
  rotation?: number;
  curve?: number;
  stretch?: number;
  pathBasedLayout?: boolean;
  rowLabelPosition?: SeatMapEditorRowLabelPosition;
  rowAlignment?: SeatMapEditorRowAlignment;
  outlineMode?: SeatMapEditorOutlineMode;
}

export interface SeatMapEditorSeat {
  id: string;
  label: string;
  rowName?: string;
  seatNumber?: string;
  ticketTypeId?: string;
  colorCode?: string;
  seatType?: string; // Standard, VIP, etc
  x: number;
  y: number;
  inventoryStatus?: string;
  hidden?: boolean;
  manualAdjusted?: boolean;
}

export interface SeatMapEditorShape {
  id: string;
  sectorId: string;
  name: string;
  code?: string;
  ticketTypeId?: string;
  ticketTypeName?: string;
  ticketTypeIds?: string[];
  ticketTypeNames?: string[];
  sectorType: SeatMapEditorSectorType;
  totalCapacity?: number;
  shapeType: SeatMapEditorShapeType;
  mapData?: Record<string, unknown>;
  color: string;
  bounds: SeatMapEditorBounds;
  points: number[];
  label: string;
  seatCount: number;
  seats: SeatMapEditorSeat[];
  seatLayout: SeatMapEditorSeatLayoutConfig;
  visible: boolean;
  locked: boolean;
  zIndex: number;
}

export interface SeatMapEditorViewport {
  scale: number;
  x: number;
  y: number;
}

export interface SeatMapEditorDocument {
  width: number;
  height: number;
  referenceImageUrl?: string;
  shapes: SeatMapEditorShape[];
}
