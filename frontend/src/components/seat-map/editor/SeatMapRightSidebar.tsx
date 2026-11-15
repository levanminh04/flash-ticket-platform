import { ChangeEvent, RefObject, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  AlignCenter,
  AlignLeft,
  AlignRight,
  Armchair,
  ChevronDown,
  ChevronRight,
  Eye,
  EyeOff,
  FlipHorizontal2,
  FlipVertical2,
  Layers3,
  Lock,
  LockOpen,
  RotateCcw,
  Ticket,
  Trash2,
} from "lucide-react";
import {
  SeatMapEditorDocument,
  SeatMapEditorOutlineMode,
  SeatMapEditorRowAlignment,
  SeatMapEditorRowLabelPosition,
  SeatMapEditorSeat,
  SeatMapEditorSeatLayoutConfig,
  SeatMapEditorSectorType,
  SeatMapEditorShape,
  SeatMapEditorTool,
} from "./seatMapEditorTypes";
import { OrganizerPublicTicketType } from "../../../services/organizerWorkspaceService";

interface SeatDropdownOptions {
  rows: Array<{ value: string; hidden?: boolean }>;
  seatNumbers: Array<{ value: string; hidden?: boolean }>;
}

/**
 * Controlled number input that strips leading zeros (e.g. "00" -> "0") and
 * restores a valid formatted value on blur without fighting mid-edit typing.
 */
function NumericInput({
  value,
  min,
  max,
  className,
  id,
  onCommit,
}: {
  value: number;
  min?: number;
  max?: number;
  className?: string;
  id?: string;
  onCommit: (next: number) => void;
}) {
  const [draft, setDraft] = useState(() => String(value));
  const [focused, setFocused] = useState(false);

  useEffect(() => {
    if (!focused) {
      setDraft(String(value));
    }
  }, [value, focused]);

  const clampValue = (num: number) => {
    let result = num;
    if (min !== undefined) result = Math.max(min, result);
    if (max !== undefined) result = Math.min(max, result);
    return result;
  };

  return (
    <input
      id={id}
      type="number"
      min={min}
      max={max}
      className={className}
      value={focused ? draft : String(value)}
      onFocus={() => {
        setFocused(true);
        setDraft(String(value));
      }}
      onChange={(e) => {
        const raw = e.target.value;
        if (raw === "" || raw === "-") {
          setDraft(raw);
          return;
        }
        const normalized = /^-?0\d+$/.test(raw) ? String(Number(raw)) : raw;
        const parsed = Number(normalized);
        if (!Number.isFinite(parsed)) {
          return;
        }
        setDraft(normalized);
        onCommit(clampValue(parsed));
      }}
      onBlur={() => {
        setFocused(false);
        const parsed = Number(draft);
        if (draft === "" || draft === "-" || !Number.isFinite(parsed)) {
          setDraft(String(value));
          return;
        }
        const clamped = clampValue(parsed);
        setDraft(String(clamped));
        onCommit(clamped);
      }}
      onKeyDown={(e) => {
        if (e.key === "Enter") {
          e.currentTarget.blur();
        }
      }}
    />
  );
}

interface SeatMapRightSidebarProps {
  assignmentEnabled?: boolean;
  activeTool?: SeatMapEditorTool;
  document: SeatMapEditorDocument;
  selectedShape: SeatMapEditorShape | null;
  selectedSeat: SeatMapEditorSeat | null;
  selectedSeats: SeatMapEditorSeat[];
  selectedShapeIds: string[];
  editingShapeId: string | null;
  seatLayoutInputs: Record<string, string>;
  seatDropdownOptions: SeatDropdownOptions;
  ticketTypes: OrganizerPublicTicketType[];
  importFileInputRef: RefObject<HTMLInputElement>;
  onImportJson: (event: ChangeEvent<HTMLInputElement>) => void;
  onImportJsonClick: () => void;
  onExportJson: () => void;
  onSetActiveTool: (tool: SeatMapEditorTool) => void;
  onAddTrapezoid: () => void;
  onAddDiamond?: () => void;
  onAddHexagon?: () => void;
  onAddCircle?: () => void;
  onAddEllipse?: () => void;
  onAddRoundedBlock: () => void;
  onAddSeatedBlock?: () => void;
  onAddRoundTable?: () => void;
  onAddConcertOvalTemplate: () => void;
  onAddFestivalTStageTemplate?: () => void;
  onAddSlicedRingArc?: () => void;
  onAddMainStage?: () => void;
  onAddStage?: () => void;
  onAddFoh?: () => void;
  onAddVipLeftCurved: () => void;
  onAddVipRightCurved: () => void;
  onAddBottomRingSection: () => void;
  onAddFanSection: () => void;
  onAddLeftSideRing?: () => void;
  onAddRightSideRing?: () => void;
  onSelectShape: (shapeId: string | null, additive?: boolean) => void;
  onToggleShapeVisibility: (shapeId: string) => void;
  onToggleShapeLocked: (shapeId: string) => void;
  onRemoveShape: (shapeId: string) => void;
  onCloneShape?: (shapeId: string) => void;
  onFlipShapeH?: (shapeId: string) => void;
  onFlipShapeV?: (shapeId: string) => void;
  onResizeShape?: (
    shapeId: string,
    nextBounds: { x: number; y: number; width: number; height: number },
  ) => void;
  onSetEditingShapeId: (shapeId: string | null) => void;
  onShapeMetaChange: (
    shapeId: string,
    patch: {
      name?: string;
      color?: string;
      ticketTypeId?: string;
      ticketTypeIds?: string[];
      sectorType?: SeatMapEditorSectorType;
      totalCapacity?: number;
    },
  ) => void;
  onSeatLayoutInputChange: (shapeId: string, key: string) => (event: ChangeEvent<HTMLInputElement>) => void;
  onSeatLayoutInputBlur: (shapeId: string, key: string) => void;
  onUpdateSeatLayoutAndGenerate?: (
    shapeId: string,
    patch: Partial<SeatMapEditorSeatLayoutConfig>,
  ) => void;
  onGenerateSeats: (shapeId: string) => void;
  onRestoreHiddenSeats: (shapeId: string) => void;
  onClearSeats: (shapeId: string) => void;
  onUpdateSeat: (shapeId: string, seatId: string, patch: Partial<SeatMapEditorSeat>) => void;
  onAssignTicketTypeToSeats: (
    shapeId: string,
    scope: "seat" | "selected" | "all",
    value: string,
    ticketTypeId: string,
  ) => void;
  onHideSeat: (shapeId: string, seatId: string) => void;
  onHideSelectedSeats: (shapeId: string, seatIds: string[]) => void;
  onRestoreSeat: (shapeId: string, seatId: string) => void;
  onRestoreSelectedSeats: (shapeId: string, seatIds: string[]) => void;
  onClearDraft: () => void;
  canClearDraft: boolean;
  onCreateSeatTypeForShape?: (shapeId: string) => void;
  onUpdateShape?: (shapeId: string, updater: (shape: SeatMapEditorShape) => SeatMapEditorShape) => void;
}

const VENUE_SHAPE_VARIANTS: Array<{
  id: string;
  label: string;
  shapeType: SeatMapEditorShape["shapeType"];
  defaultCornerRadius: number;
  defaultFill: string;
  defaultColor: string;
  defaultLabel: string;
}> = [
  {
    id: "venue_frame",
    label: "Khung Venue",
    shapeType: "roundedRect",
    defaultCornerRadius: 160,
    defaultFill: "#f6ebe4",
    defaultColor: "#e5b89b",
    defaultLabel: "",
  },
  {
    id: "stage_rect",
    label: "Sân khấu ngang",
    shapeType: "stage",
    defaultCornerRadius: 16,
    defaultFill: "#1e293b",
    defaultColor: "#1e293b",
    defaultLabel: "SÂN KHẤU",
  },
  {
    id: "stage_t",
    label: "Sân khấu chữ T",
    shapeType: "stage",
    defaultCornerRadius: 12,
    defaultFill: "#1e293b",
    defaultColor: "#1e293b",
    defaultLabel: "SÂN KHẤU",
  },
  {
    id: "stage_arc",
    label: "Sân khấu vòm",
    shapeType: "stage",
    defaultCornerRadius: 0,
    defaultFill: "#1e293b",
    defaultColor: "#1e293b",
    defaultLabel: "SÂN KHẤU",
  },
  {
    id: "stage_trapezoid",
    label: "Hình thang",
    shapeType: "stage",
    defaultCornerRadius: 0,
    defaultFill: "#1e293b",
    defaultColor: "#1e293b",
    defaultLabel: "SÂN KHẤU",
  },
  {
    id: "stage_circle",
    label: "Tròn 360°",
    shapeType: "ellipse",
    defaultCornerRadius: 0,
    defaultFill: "#1e293b",
    defaultColor: "#1e293b",
    defaultLabel: "SÂN KHẤU",
  },
];

function isDecorativeShape(shape: SeatMapEditorShape) {
  return (
    shape.mapData?.decorative === true ||
    shape.shapeType === "stage" ||
    shape.shapeType === "foh"
  );
}

function isRoundTableShape(shape: SeatMapEditorShape) {
  return (
    shape.seatLayout?.mode === "table" ||
    shape.mapData?.presetKind === "round_table"
  );
}

export default function SeatMapRightSidebar({
  assignmentEnabled = false,
  activeTool = "select",
  document,
  selectedShape,
  selectedSeat,
  selectedSeats,
  selectedShapeIds,
  editingShapeId,
  seatLayoutInputs,
  ticketTypes,
  importFileInputRef,
  onImportJson,
  onSetActiveTool,
  onSelectShape,
  onToggleShapeVisibility,
  onToggleShapeLocked,
  onRemoveShape,
  onFlipShapeH,
  onFlipShapeV,
  onResizeShape,
  onUpdateShape,
  onSetEditingShapeId,
  onShapeMetaChange,
  onSeatLayoutInputChange,
  onSeatLayoutInputBlur,
  onUpdateSeatLayoutAndGenerate,
  onGenerateSeats,
  onRestoreHiddenSeats,
  onUpdateSeat,
  onAssignTicketTypeToSeats,
  onHideSeat,
  onHideSelectedSeats,
  onRestoreSeat,
  onRestoreSelectedSeats,
  onCreateSeatTypeForShape,
}: SeatMapRightSidebarProps) {
  const { t } = useTranslation();
  const [layersExpanded, setLayersExpanded] = useState(true);
  const [bulkTicketTypeId, setBulkTicketTypeId] = useState<string>("");

  const stats = useMemo(() => {
    let sectorCount = 0;
    let totalSeats = 0;
    let standingCount = 0;
    for (const shape of document.shapes) {
      if (isDecorativeShape(shape)) continue;
      sectorCount += 1;
      if (shape.sectorType === "STANDING") {
        standingCount += Number(shape.totalCapacity || 0);
      } else {
        totalSeats += shape.seats.filter((s) => !s.hidden).length;
      }
    }
    return { sectorCount, totalSeats, standingCount };
  }, [document.shapes]);

  const isDecorative = selectedShape ? isDecorativeShape(selectedShape) : false;
  const isRoundTable = selectedShape ? isRoundTableShape(selectedShape) : false;
  const hiddenSeatsCount = selectedShape
    ? selectedShape.seats.filter((seat) => seat.hidden).length
    : 0;

  const currentLayout = selectedShape?.seatLayout;
  const rotationVal = Number(currentLayout?.rotation ?? 0);
  const curveVal = Number(currentLayout?.curve ?? 0);
  const stretchVal = Number(currentLayout?.stretch ?? 0);
  const rowAlignment: SeatMapEditorRowAlignment = currentLayout?.rowAlignment ?? "center";
  const rowLabelPos: SeatMapEditorRowLabelPosition = currentLayout?.rowLabelPosition ?? "left";
  const outlineMode: SeatMapEditorOutlineMode = currentLayout?.outlineMode ?? "auto";

  const selectedSeatIds = selectedSeats.map((s) => s.id);
  const hasHiddenInSelection = selectedSeats.some((s) => s.hidden);

  return (
    <aside className="seat-map-editor-sidebar smp-inspector" aria-label="Thuộc tính phân khu">
      <input
        ref={importFileInputRef}
        type="file"
        accept=".json,application/json"
        style={{ display: "none" }}
        onChange={onImportJson}
      />

      {!selectedShape ? (
        <>
          <div className="smp-inspector-section">
            <div className="smp-inspector-heading">TỔNG QUAN SƠ ĐỒ</div>
            <div className="smp-kpi-grid">
              <div className="smp-kpi-box">
                <span>Phân khu</span>
                <strong>{stats.sectorCount}</strong>
              </div>
              <div className="smp-kpi-box">
                <span>Ghế ngồi</span>
                <strong>{stats.totalSeats}</strong>
              </div>
              <div className="smp-kpi-box">
                <span>Đứng</span>
                <strong>{stats.standingCount}</strong>
              </div>
            </div>
            <p className="smp-inspector-hint">
              Chọn <strong>Hàng ghế</strong> hoặc <strong>Hình khối</strong> trên thanh công cụ phía trên để vẽ, hoặc bấm chọn một phân khu để tinh chỉnh.
            </p>
          </div>
        </>
      ) : selectedShape.mapData?.isVenueShape === true ? (
        <>
          <div className="smp-inspector-section">
            <div className="smp-inspector-heading">
              <span>VENUE SHAPE</span>
              <span className="smp-inspector-badge">KHUNG / SÂN KHẤU</span>
            </div>

            <div className="smp-prop-block">
              <span className="smp-prop-label">Kiểu hình</span>
              <div className="smp-action-grid-2" style={{ marginTop: 6 }}>
                {VENUE_SHAPE_VARIANTS.map((variant) => {
                  const activeVariant = String(selectedShape.mapData?.venueVariant ?? "venue_frame");
                  const isActive = activeVariant === variant.id;
                  return (
                    <button
                      key={variant.id}
                      type="button"
                      className={`smp-outline-btn ${isActive ? "is-active" : ""}`}
                      style={
                        isActive
                          ? {
                              borderColor: "#1688f0",
                              background: "#eff6ff",
                              color: "#0f62fe",
                              fontWeight: 600,
                            }
                          : undefined
                      }
                      onClick={() => {
                        onUpdateShape?.(selectedShape.id, (current) => {
                          const prevLabel = (current.label ?? "").trim();
                          const shouldSwapLabel =
                            prevLabel === "" || prevLabel === "SÂN KHẤU" || prevLabel === "MAIN STAGE";
                          const nextBounds =
                            variant.id === "stage_t" && current.bounds.height < 180
                              ? { ...current.bounds, height: 260 }
                              : variant.id === "stage_circle"
                                ? {
                                    ...current.bounds,
                                    width: Math.min(current.bounds.width, 360),
                                    height: Math.min(current.bounds.width, 360),
                                  }
                                : current.bounds;
                          return {
                            ...current,
                            name:
                              variant.id === "venue_frame"
                                ? "Venue Shape"
                                : shouldSwapLabel || current.name === "Venue Shape"
                                  ? variant.defaultLabel
                                  : current.name,
                            shapeType: variant.shapeType,
                            color: variant.defaultColor,
                            label: shouldSwapLabel ? variant.defaultLabel : current.label,
                            bounds: nextBounds,
                            mapData: {
                              ...(current.mapData ?? {}),
                              decorative: true,
                              isVenueShape: true,
                              venueVariant: variant.id,
                              cornerRadius: variant.defaultCornerRadius,
                              fill: variant.defaultFill,
                            },
                          };
                        });
                      }}
                    >
                      <span>{variant.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="smp-prop-row">
              <span className="smp-prop-label">Vị trí</span>
              <div className="smp-dual-inputs">
                <label className="smp-mini-field">
                  <span>X</span>
                  <NumericInput
                    value={Math.round(selectedShape.bounds.x)}
                    onCommit={(nextX) =>
                      onUpdateShape?.(selectedShape.id, (current) => ({
                        ...current,
                        bounds: { ...current.bounds, x: nextX },
                      }))
                    }
                  />
                </label>
                <label className="smp-mini-field">
                  <span>Y</span>
                  <NumericInput
                    value={Math.round(selectedShape.bounds.y)}
                    onCommit={(nextY) =>
                      onUpdateShape?.(selectedShape.id, (current) => ({
                        ...current,
                        bounds: { ...current.bounds, y: nextY },
                      }))
                    }
                  />
                </label>
              </div>
            </div>

            <div className="smp-prop-row">
              <span className="smp-prop-label">Kích thước</span>
              <div className="smp-dual-inputs">
                <label className="smp-mini-field">
                  <span>Rộng</span>
                  <NumericInput
                    min={40}
                    value={Math.round(selectedShape.bounds.width)}
                    onCommit={(nextW) =>
                      onUpdateShape?.(selectedShape.id, (current) => ({
                        ...current,
                        bounds: { ...current.bounds, width: nextW },
                      }))
                    }
                  />
                </label>
                <label className="smp-mini-field">
                  <span>Cao</span>
                  <NumericInput
                    min={40}
                    value={Math.round(selectedShape.bounds.height)}
                    onCommit={(nextH) =>
                      onUpdateShape?.(selectedShape.id, (current) => ({
                        ...current,
                        bounds: { ...current.bounds, height: nextH },
                      }))
                    }
                  />
                </label>
              </div>
            </div>

            {(() => {
              const activeVariant = String(selectedShape.mapData?.venueVariant ?? "venue_frame");
              const supportsRadius = activeVariant === "venue_frame" || activeVariant === "stage_rect";
              if (!supportsRadius) return null;
              const maxRadius = Math.max(
                10,
                Math.floor(Math.min(selectedShape.bounds.width, selectedShape.bounds.height) / 2),
              );
              const currentRadius = Math.min(
                maxRadius,
                Math.max(0, Number(selectedShape.mapData?.cornerRadius ?? 160)),
              );
              return (
                <div className="smp-prop-row">
                  <span className="smp-prop-label">Bo góc</span>
                  <div className="smp-slider-control">
                    <input
                      type="range"
                      min={0}
                      max={maxRadius}
                      step={1}
                      value={currentRadius}
                      onChange={(e) => {
                        const r = Number(e.target.value);
                        onUpdateShape?.(selectedShape.id, (current) => ({
                          ...current,
                          mapData: { ...(current.mapData ?? {}), cornerRadius: r },
                        }));
                      }}
                    />
                    <NumericInput
                      min={0}
                      max={maxRadius}
                      className="smp-slider-num"
                      value={Math.round(currentRadius)}
                      onCommit={(r) =>
                        onUpdateShape?.(selectedShape.id, (current) => ({
                          ...current,
                          mapData: { ...(current.mapData ?? {}), cornerRadius: r },
                        }))
                      }
                    />
                  </div>
                </div>
              );
            })()}

            <div className="smp-prop-row">
              <label className="smp-prop-label" htmlFor="smp-venue-label">
                Nhãn chữ
              </label>
              <input
                id="smp-venue-label"
                type="text"
                className="smp-prop-input"
                placeholder="Tên nhãn"
                value={selectedShape.label ?? ""}
                onChange={(e) =>
                  onUpdateShape?.(selectedShape.id, (current) => ({
                    ...current,
                    label: e.target.value,
                  }))
                }
              />
            </div>

            <div className="smp-prop-row">
              <span className="smp-prop-label">Màu sắc</span>
              <input
                type="color"
                className="smp-color-native"
                value={
                  typeof selectedShape.mapData?.fill === "string"
                    ? selectedShape.mapData.fill
                    : selectedShape.color
                }
                onChange={(e) => {
                  const nextColor = e.target.value;
                  onUpdateShape?.(selectedShape.id, (current) => ({
                    ...current,
                    color: nextColor,
                    mapData: { ...(current.mapData ?? {}), fill: nextColor },
                  }));
                }}
              />
            </div>

            <div className="smp-btn-stack" style={{ marginTop: 8 }}>
              <button
                type="button"
                className="smp-outline-btn is-danger"
                onClick={() => onRemoveShape(selectedShape.id)}
              >
                <Trash2 size={14} />
                <span>Xóa Venue Shape</span>
              </button>
            </div>
          </div>
        </>
      ) : (
        <>
          {/* SECTION: PHÂN KHU */}
          <div className="smp-inspector-section">
            <div className="smp-inspector-heading">
              <span>{isDecorative ? "HẠNG MỤC SÂN KHẤU" : "PHÂN KHU"}</span>
              <span className="smp-inspector-badge">
                {selectedShape.sectorType === "STANDING"
                  ? "ĐỨNG"
                  : isDecorative
                    ? "KỸ THUẬT"
                    : `${selectedShape.seats.filter((s) => !s.hidden).length} GHẾ`}
              </span>
            </div>

            <div className="smp-prop-row">
              <label className="smp-prop-label" htmlFor="smp-shape-name">
                Tên khu
              </label>
              <input
                id="smp-shape-name"
                type="text"
                className="smp-prop-input"
                value={selectedShape.name}
                onChange={(e) => onShapeMetaChange(selectedShape.id, { name: e.target.value })}
              />
            </div>

            {!isDecorative ? (
              <div className="smp-prop-row">
                <span className="smp-prop-label">Loại khu</span>
                <div className="smp-seg-group">
                  <button
                    type="button"
                    className={`smp-seg-btn ${selectedShape.sectorType !== "STANDING" ? "is-active" : ""}`}
                    onClick={() => onShapeMetaChange(selectedShape.id, { sectorType: "SEATED" })}
                  >
                    Ngồi
                  </button>
                  <button
                    type="button"
                    className={`smp-seg-btn ${selectedShape.sectorType === "STANDING" ? "is-active" : ""}`}
                    onClick={() => onShapeMetaChange(selectedShape.id, { sectorType: "STANDING" })}
                  >
                    Đứng
                  </button>
                </div>
              </div>
            ) : null}

            {selectedShape.sectorType === "STANDING" && !isDecorative ? (
              <div className="smp-prop-row">
                <label className="smp-prop-label" htmlFor="smp-standing-cap">
                  Sức chứa
                </label>
                <NumericInput
                  id="smp-standing-cap"
                  min={1}
                  className="smp-prop-input"
                  value={selectedShape.totalCapacity ?? 100}
                  onCommit={(nextCap) =>
                    onShapeMetaChange(selectedShape.id, {
                      totalCapacity: nextCap,
                    })
                  }
                />
              </div>
            ) : null}

            {onResizeShape && selectedShape.bounds?.width && selectedShape.bounds?.height ? (
              <div className="smp-prop-row">
                <span className="smp-prop-label">Kích thước</span>
                <div className="smp-dual-inputs">
                  <label className="smp-mini-field">
                    <span>W</span>
                    <NumericInput
                      min={40}
                      value={Math.round(selectedShape.bounds.width)}
                      onCommit={(nextW) =>
                        onResizeShape(selectedShape.id, {
                          x: selectedShape.bounds.x,
                          y: selectedShape.bounds.y,
                          width: nextW,
                          height: selectedShape.bounds.height || 100,
                        })
                      }
                    />
                  </label>
                  <label className="smp-mini-field">
                    <span>H</span>
                    <NumericInput
                      min={40}
                      value={Math.round(selectedShape.bounds.height)}
                      onCommit={(nextH) =>
                        onResizeShape(selectedShape.id, {
                          x: selectedShape.bounds.x,
                          y: selectedShape.bounds.y,
                          width: selectedShape.bounds.width || 100,
                          height: nextH,
                        })
                      }
                    />
                  </label>
                </div>
              </div>
            ) : null}

            {!isDecorative && selectedShape.sectorType !== "STANDING" && !isRoundTable ? (
              <div className="smp-prop-row">
                <span className="smp-prop-label">Khung khu</span>
                <div className="smp-seg-group">
                  <button
                    type="button"
                    className={`smp-seg-btn ${outlineMode !== "none" ? "is-active" : ""}`}
                    onClick={() =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, { outlineMode: "auto" })
                    }
                  >
                    Có viền
                  </button>
                  <button
                    type="button"
                    className={`smp-seg-btn ${outlineMode === "none" ? "is-active" : ""}`}
                    onClick={() =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, { outlineMode: "none" })
                    }
                  >
                    Chỉ ghế
                  </button>
                </div>
              </div>
            ) : null}

            {!isDecorative && selectedShape.sectorType !== "STANDING" ? (
              <div className="smp-prop-row">
                <span className="smp-prop-label">Sửa ghế</span>
                <button
                  type="button"
                  className={`smp-outline-btn smp-inline-toggle ${activeTool === "editSeats" ? "is-active" : ""}`}
                  onClick={() =>
                    onSetActiveTool(activeTool === "editSeats" ? "select" : "editSeats")
                  }
                >
                  <Armchair size={14} />
                  <span>{activeTool === "editSeats" ? "Thoát sửa ghế" : "Mở chế độ sửa ghế"}</span>
                </button>
              </div>
            ) : null}
          </div>

          {/* SECTION: HÀNG GHẾ & ĐÁNH SỐ */}
          {!isDecorative && selectedShape.sectorType !== "STANDING" ? (
            <div className="smp-inspector-section">
              <div className="smp-inspector-heading">
                <span>{isRoundTable ? "GHẾ QUANH BÀN" : "HÀNG GHẾ & ĐÁNH SỐ"}</span>
              </div>

              {isRoundTable ? (
                <div className="smp-prop-row">
                  <label className="smp-prop-label" htmlFor="smp-table-seats">
                    Số ghế
                  </label>
                  <input
                    id="smp-table-seats"
                    type="number"
                    min={2}
                    max={24}
                    className="smp-prop-input"
                    value={seatLayoutInputs.seatsPerRow ?? String(currentLayout?.seatsPerRow ?? 8)}
                    onChange={onSeatLayoutInputChange(selectedShape.id, "seatsPerRow")}
                    onBlur={() => onSeatLayoutInputBlur(selectedShape.id, "seatsPerRow")}
                  />
                </div>
              ) : (
                <>
                  <div className="smp-prop-row">
                    <span className="smp-prop-label">Hàng × Ghế</span>
                    <div className="smp-dual-inputs">
                      <label className="smp-mini-field">
                        <span>H</span>
                        <input
                          type="number"
                          min={1}
                          value={seatLayoutInputs.rows ?? String(currentLayout?.rows ?? 6)}
                          onChange={onSeatLayoutInputChange(selectedShape.id, "rows")}
                          onBlur={() => onSeatLayoutInputBlur(selectedShape.id, "rows")}
                        />
                      </label>
                      <label className="smp-mini-field">
                        <span>G</span>
                        <input
                          type="number"
                          min={1}
                          value={seatLayoutInputs.seatsPerRow ?? String(currentLayout?.seatsPerRow ?? 12)}
                          onChange={onSeatLayoutInputChange(selectedShape.id, "seatsPerRow")}
                          onBlur={() => onSeatLayoutInputBlur(selectedShape.id, "seatsPerRow")}
                        />
                      </label>
                    </div>
                  </div>

                  <div className="smp-prop-row">
                    <span className="smp-prop-label">Bắt đầu</span>
                    <div className="smp-dual-inputs">
                      <label className="smp-mini-field">
                        <span>Hàng</span>
                        <input
                          type="text"
                          maxLength={1}
                          value={String.fromCharCode(currentLayout?.rowStartCharCode ?? 65)}
                          onChange={(e) => {
                            const char = (e.target.value || "A").toUpperCase().charCodeAt(0);
                            onUpdateSeatLayoutAndGenerate?.(selectedShape.id, {
                              rowStartCharCode: Math.min(90, Math.max(65, char)),
                            });
                          }}
                        />
                      </label>
                      <label className="smp-mini-field">
                        <span>Số</span>
                        <input
                          type="number"
                          min={1}
                          value={
                            seatLayoutInputs.seatStartNumber ??
                            String(currentLayout?.seatStartNumber ?? 1)
                          }
                          onChange={onSeatLayoutInputChange(selectedShape.id, "seatStartNumber")}
                          onBlur={() => onSeatLayoutInputBlur(selectedShape.id, "seatStartNumber")}
                        />
                      </label>
                    </div>
                  </div>

                  <div className="smp-prop-row">
                    <span className="smp-prop-label">Nhãn hàng</span>
                    <div className="smp-seg-group">
                      {(
                        [
                          { id: "left", label: "Trái" },
                          { id: "right", label: "Phải" },
                          { id: "both", label: "2 bên" },
                          { id: "none", label: "Ẩn" },
                        ] as Array<{ id: SeatMapEditorRowLabelPosition; label: string }>
                      ).map((opt) => (
                        <button
                          key={opt.id}
                          type="button"
                          className={`smp-seg-btn ${rowLabelPos === opt.id ? "is-active" : ""}`}
                          onClick={() =>
                            onUpdateSeatLayoutAndGenerate?.(selectedShape.id, {
                              rowLabelPosition: opt.id,
                            })
                          }
                        >
                          {opt.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </>
              )}

              <div className="smp-btn-stack">
                <button
                  type="button"
                  className="smp-primary-btn"
                  onClick={() => onGenerateSeats(selectedShape.id)}
                >
                  Sinh lại ghế
                </button>
                {hiddenSeatsCount > 0 ? (
                  <button
                    type="button"
                    className="smp-outline-btn"
                    onClick={() => onRestoreHiddenSeats(selectedShape.id)}
                  >
                    <RotateCcw size={14} />
                    <span>Khôi phục {hiddenSeatsCount} ghế đã ẩn</span>
                  </button>
                ) : null}
              </div>
            </div>
          ) : null}

          {/* SECTION: CĂN HÀNG & BIẾN HÌNH (Seatmap.pro style) */}
          {!isDecorative && selectedShape.sectorType !== "STANDING" && !isRoundTable ? (
            <div className="smp-inspector-section">
              <div className="smp-inspector-heading">CĂN HÀNG & BIẾN HÌNH</div>

              <div className="smp-prop-row">
                <span className="smp-prop-label">Căn hàng</span>
                <div className="smp-seg-group">
                  <button
                    type="button"
                    className={`smp-seg-btn ${rowAlignment === "left" ? "is-active" : ""}`}
                    onClick={() =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, { rowAlignment: "left" })
                    }
                    title="Căn trái"
                  >
                    <AlignLeft size={14} />
                  </button>
                  <button
                    type="button"
                    className={`smp-seg-btn ${rowAlignment === "center" ? "is-active" : ""}`}
                    onClick={() =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, { rowAlignment: "center" })
                    }
                    title="Căn giữa"
                  >
                    <AlignCenter size={14} />
                  </button>
                  <button
                    type="button"
                    className={`smp-seg-btn ${rowAlignment === "right" ? "is-active" : ""}`}
                    onClick={() =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, { rowAlignment: "right" })
                    }
                    title="Căn phải"
                  >
                    <AlignRight size={14} />
                  </button>
                </div>
              </div>

              <div className="smp-prop-row">
                <span className="smp-prop-label" title="Path-based Layout (Path-based Seat Arrangement)">
                  Path-based
                </span>
                <div className="smp-seg-group">
                  <button
                    type="button"
                    className={`smp-seg-btn ${!currentLayout?.pathBasedLayout ? "is-active" : ""}`}
                    onClick={() =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, {
                        pathBasedLayout: false,
                      })
                    }
                    title="Path-based: OFF"
                  >
                    OFF
                  </button>
                  <button
                    type="button"
                    className={`smp-seg-btn ${currentLayout?.pathBasedLayout ? "is-active" : ""}`}
                    onClick={() =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, {
                        pathBasedLayout: true,
                      })
                    }
                    title="Path-based: ON"
                  >
                    ON
                  </button>
                </div>
              </div>

              {(onFlipShapeH || onFlipShapeV) ? (
                <div className="smp-prop-row">
                  <span className="smp-prop-label">Lật</span>
                  <div className="smp-seg-group">
                    {onFlipShapeH ? (
                      <button
                        type="button"
                        className="smp-seg-btn"
                        onClick={() => onFlipShapeH(selectedShape.id)}
                        title="Lật ngang"
                      >
                        <FlipHorizontal2 size={14} />
                      </button>
                    ) : null}
                    {onFlipShapeV ? (
                      <button
                        type="button"
                        className="smp-seg-btn"
                        onClick={() => onFlipShapeV(selectedShape.id)}
                        title="Lật dọc"
                      >
                        <FlipVertical2 size={14} />
                      </button>
                    ) : null}
                  </div>
                </div>
              ) : null}

              <div className="smp-prop-row">
                <span className="smp-prop-label">Xoay</span>
                <div className="smp-slider-control">
                  <input
                    type="range"
                    min={-180}
                    max={180}
                    step={1}
                    value={rotationVal}
                    onChange={(e) =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, {
                        rotation: Number(e.target.value),
                      })
                    }
                  />
                  <NumericInput
                    min={-180}
                    max={180}
                    className="smp-slider-num"
                    value={rotationVal}
                    onCommit={(nextRot) =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, {
                        rotation: nextRot,
                      })
                    }
                  />
                </div>
              </div>

              <div className="smp-prop-row">
                <span className="smp-prop-label">Độ cong</span>
                <div className="smp-slider-control">
                  <input
                    type="range"
                    min={-100}
                    max={100}
                    step={1}
                    value={curveVal}
                    onChange={(e) =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, {
                        curve: Number(e.target.value),
                      })
                    }
                  />
                  <NumericInput
                    min={-100}
                    max={100}
                    className="smp-slider-num"
                    value={curveVal}
                    onCommit={(nextCurve) =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, {
                        curve: nextCurve,
                      })
                    }
                  />
                </div>
              </div>

              <div className="smp-prop-row">
                <span className="smp-prop-label">Giãn quạt</span>
                <div className="smp-slider-control">
                  <input
                    type="range"
                    min={-100}
                    max={100}
                    step={1}
                    value={stretchVal}
                    onChange={(e) =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, {
                        stretch: Number(e.target.value),
                      })
                    }
                  />
                  <NumericInput
                    min={-100}
                    max={100}
                    className="smp-slider-num"
                    value={stretchVal}
                    onCommit={(nextStretch) =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, {
                        stretch: nextStretch,
                      })
                    }
                  />
                </div>
              </div>

              <div className="smp-prop-row">
                <span className="smp-prop-label">Giãn ghế</span>
                <div className="smp-slider-control">
                  <input
                    type="range"
                    min={-8}
                    max={26}
                    step={1}
                    value={Math.min(26, Math.max(-8, Math.round((currentLayout?.gapX ?? 22) - 22)))}
                    onChange={(e) =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, {
                        gapX: Math.min(48, Math.max(14, 22 + Number(e.target.value))),
                      })
                    }
                  />
                  <NumericInput
                    min={-8}
                    max={26}
                    className="smp-slider-num"
                    value={Math.round((currentLayout?.gapX ?? 22) - 22)}
                    onCommit={(delta) =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, {
                        gapX: 22 + delta,
                      })
                    }
                  />
                </div>
              </div>

              <div className="smp-prop-row">
                <span className="smp-prop-label">Giãn hàng</span>
                <div className="smp-slider-control">
                  <input
                    type="range"
                    min={-8}
                    max={26}
                    step={1}
                    value={Math.min(26, Math.max(-8, Math.round((currentLayout?.gapY ?? 22) - 22)))}
                    onChange={(e) =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, {
                        gapY: Math.min(48, Math.max(14, 22 + Number(e.target.value))),
                      })
                    }
                  />
                  <NumericInput
                    min={-8}
                    max={26}
                    className="smp-slider-num"
                    value={Math.round((currentLayout?.gapY ?? 22) - 22)}
                    onCommit={(delta) =>
                      onUpdateSeatLayoutAndGenerate?.(selectedShape.id, {
                        gapY: 22 + delta,
                      })
                    }
                  />
                </div>
              </div>
            </div>
          ) : null}

          {/* SECTION: GHẾ ĐANG CHỌN */}
          {(selectedSeat || selectedSeats.length > 0) && !isDecorative ? (
            <div className="smp-inspector-section">
              <div className="smp-inspector-heading">
                <span>
                  {selectedSeats.length > 1
                    ? `ĐANG CHỌN ${selectedSeats.length} GHẾ`
                    : selectedSeat
                      ? `GHẾ ${selectedSeat.rowName || ""}${selectedSeat.seatNumber || selectedSeat.label}`
                      : "GHẾ ĐANG CHỌN"}
                </span>
              </div>

              {selectedSeat && selectedSeats.length <= 1 ? (
                <div className="smp-prop-row">
                  <span className="smp-prop-label">Nhãn ghế</span>
                  <div className="smp-dual-inputs">
                    <label className="smp-mini-field">
                      <span>Hàng</span>
                      <input
                        type="text"
                        value={selectedSeat.rowName || ""}
                        onChange={(e) =>
                          onUpdateSeat(selectedShape.id, selectedSeat.id, {
                            rowName: e.target.value.toUpperCase(),
                          })
                        }
                      />
                    </label>
                    <label className="smp-mini-field">
                      <span>Số</span>
                      <input
                        type="text"
                        value={selectedSeat.seatNumber || ""}
                        onChange={(e) =>
                          onUpdateSeat(selectedShape.id, selectedSeat.id, {
                            seatNumber: e.target.value,
                          })
                        }
                      />
                    </label>
                  </div>
                </div>
              ) : null}

              <div className="smp-btn-stack">
                {selectedSeats.length > 1 ? (
                  <>
                    <button
                      type="button"
                      className="smp-outline-btn"
                      onClick={() => onHideSelectedSeats(selectedShape.id, selectedSeatIds)}
                    >
                      <EyeOff size={14} />
                      <span>Ẩn {selectedSeats.length} ghế đã chọn</span>
                    </button>
                    {hasHiddenInSelection ? (
                      <button
                        type="button"
                        className="smp-outline-btn"
                        onClick={() => onRestoreSelectedSeats(selectedShape.id, selectedSeatIds)}
                      >
                        <Eye size={14} />
                        <span>Hiện lại ghế đã ẩn</span>
                      </button>
                    ) : null}
                  </>
                ) : selectedSeat ? (
                  <button
                    type="button"
                    className="smp-outline-btn"
                    onClick={() =>
                      selectedSeat.hidden
                        ? onRestoreSeat(selectedShape.id, selectedSeat.id)
                        : onHideSeat(selectedShape.id, selectedSeat.id)
                    }
                  >
                    {selectedSeat.hidden ? <Eye size={14} /> : <EyeOff size={14} />}
                    <span>{selectedSeat.hidden ? "Hiện lại ghế này" : "Ẩn ghế này (lối đi)"}</span>
                  </button>
                ) : null}
              </div>
            </div>
          ) : null}

          {/* SECTION: GÁN LOẠI VÉ */}
          {assignmentEnabled && !isDecorative ? (
            (() => {
              const shapeTicketTypes = ticketTypes.filter(
                (tt) => (tt.sectorId ?? tt.eventSectorId) === selectedShape.id,
              );
              const activeBulkTicketTypeId = shapeTicketTypes.some((tt) => tt.id === bulkTicketTypeId)
                ? bulkTicketTypeId
                : "";
              return (
            <div className="smp-inspector-section">
              <div className="smp-inspector-heading">
                <span>GÁN LOẠI VÉ</span>
                {onCreateSeatTypeForShape ? (
                  <button
                    type="button"
                    className="smp-link-btn"
                    onClick={() => onCreateSeatTypeForShape(selectedShape.id)}
                  >
                    + Tạo loại vé
                  </button>
                ) : null}
              </div>

              <div className="smp-prop-row">
                <label className="smp-prop-label" htmlFor="smp-ticket-type">
                  Loại vé
                </label>
                <select
                  id="smp-ticket-type"
                  className="smp-prop-select"
                  value={selectedShape.ticketTypeId || ""}
                  onChange={(e) =>
                    onShapeMetaChange(selectedShape.id, {
                      ticketTypeId: e.target.value,
                      ticketTypeIds: e.target.value ? [e.target.value] : [],
                    })
                  }
                >
                  <option value="">-- Chưa gán loại vé --</option>
                  {shapeTicketTypes.map((tt) => (
                    <option key={tt.id} value={tt.id}>
                      {tt.name} ({Number(tt.price || 0).toLocaleString("vi-VN")}đ)
                    </option>
                  ))}
                </select>
              </div>

              {selectedShape.sectorType !== "STANDING" && selectedSeats.length > 0 ? (
                <div className="smp-prop-row">
                  <span className="smp-prop-label">Cho ghế chọn</span>
                  <div className="smp-dual-inputs">
                    <select
                      className="smp-prop-select"
                      value={activeBulkTicketTypeId}
                      onChange={(e) => setBulkTicketTypeId(e.target.value)}
                    >
                      <option value="">Chọn vé...</option>
                      {shapeTicketTypes.map((tt) => (
                        <option key={tt.id} value={tt.id}>
                          {tt.name} ({Number(tt.price || 0).toLocaleString("vi-VN")}đ)
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      className="smp-outline-btn"
                      disabled={!activeBulkTicketTypeId}
                      onClick={() =>
                        onAssignTicketTypeToSeats(
                          selectedShape.id,
                          selectedSeats.length > 1 ? "selected" : "seat",
                          selectedSeats[0]?.id || "",
                          activeBulkTicketTypeId,
                        )
                      }
                    >
                      <Ticket size={13} />
                      <span>Gán</span>
                    </button>
                  </div>
                </div>
              ) : null}
            </div>
              );
            })()
          ) : null}
        </>
      )}

      {/* SECTION: DANH SÁCH PHÂN KHU (LAYERS) */}
      <div className="smp-inspector-section smp-layers-section">
        <button
          type="button"
          className="smp-layers-toggle"
          onClick={() => setLayersExpanded((prev) => !prev)}
        >
          <span className="smp-layers-toggle-left">
            <Layers3 size={14} />
            <span>DANH SÁCH PHÂN KHU ({document.shapes.length})</span>
          </span>
          {layersExpanded ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
        </button>

        {layersExpanded ? (
          <div className="smp-layers-list">
            {document.shapes.length === 0 ? (
              <div className="smp-layers-empty">
                {t("seatMap.noLayers", "Chưa có phân khu nào.")}
              </div>
            ) : (
              document.shapes.map((shape) => {
                const isSelected = selectedShapeIds.includes(shape.id);
                const isEditingName = editingShapeId === shape.id;
                const countLabel = isDecorativeShape(shape)
                  ? "SK"
                  : shape.sectorType === "STANDING"
                    ? `${shape.totalCapacity ?? 0} GA`
                    : `${shape.seats.filter((s) => !s.hidden).length}g`;
                return (
                  <div
                    key={shape.id}
                    className={`smp-layer-item ${isSelected ? "is-selected" : ""}`}
                    onClick={(e) => onSelectShape(shape.id, e.shiftKey)}
                  >
                    <span className="smp-layer-dot" style={{ background: shape.color }} />
                    {isEditingName ? (
                      <input
                        type="text"
                        className="smp-layer-name-input"
                        value={shape.name}
                        autoFocus
                        onClick={(e) => e.stopPropagation()}
                        onChange={(e) => onShapeMetaChange(shape.id, { name: e.target.value })}
                        onBlur={() => onSetEditingShapeId(null)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter" || e.key === "Escape") {
                            onSetEditingShapeId(null);
                          }
                        }}
                      />
                    ) : (
                      <span
                        className="smp-layer-name"
                        onDoubleClick={(e) => {
                          e.stopPropagation();
                          onSetEditingShapeId(shape.id);
                        }}
                      >
                        {shape.name}
                      </span>
                    )}
                    <span className="smp-layer-count">{countLabel}</span>
                    <div className="smp-layer-actions" onClick={(e) => e.stopPropagation()}>
                      <button
                        type="button"
                        onClick={() => onToggleShapeLocked(shape.id)}
                        title={shape.locked ? "Mở khóa" : "Khóa vị trí"}
                      >
                        {shape.locked ? <Lock size={13} /> : <LockOpen size={13} />}
                      </button>
                      <button
                        type="button"
                        onClick={() => onToggleShapeVisibility(shape.id)}
                        title={shape.visible === false ? "Hiện" : "Ẩn"}
                      >
                        {shape.visible === false ? <EyeOff size={13} /> : <Eye size={13} />}
                      </button>
                      <button
                        type="button"
                        onClick={() => onRemoveShape(shape.id)}
                        title="Xóa"
                      >
                        <Trash2 size={13} />
                      </button>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        ) : null}
      </div>
    </aside>
  );
}
