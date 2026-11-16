import { Eye, EyeOff, Maximize2, Minus, Plus } from "lucide-react";
import { useState } from "react";
import SeatMapEditorCanvas from "./SeatMapEditorCanvas";
import {
  SeatMapEditorDocument,
  SeatMapEditorTool,
  SeatMapEditorViewport,
} from "./seatMapEditorTypes";

interface SeatMapCanvasShellProps {
  document: SeatMapEditorDocument;
  viewport: SeatMapEditorViewport;
  referenceImageVisible: boolean;
  showGrid?: boolean;
  externalResetViewKey?: number;
  activeTool?: SeatMapEditorTool;
  selectedShapeIds: string[];
  selectedSeatId: string | null;
  selectedSeatIds: string[];
  canRedo: boolean;
  canUndo: boolean;
  onClearSeatSelection: () => void;
  onMoveSeat: (shapeId: string, seatId: string, nextX: number, nextY: number) => void;
  onMoveSeatBlock: (shapeId: string, deltaX: number, deltaY: number) => void;
  onResizeSeatBlock: (
    shapeId: string,
    previousBounds: { x: number; y: number; width: number; height: number },
    nextBounds: { x: number; y: number; width: number; height: number },
  ) => void;
  onResizeShape: (shapeId: string, nextBounds: { x: number; y: number; width: number; height: number }) => void;
  onSelectSeat: (shapeId: string, seatId: string | null, additive?: boolean) => void;
  onSelectSeatsInShape?: (shapeId: string, seatIds: string[], additive?: boolean) => void;
  onCreateSeatRowsFromBounds?: (bounds: { x: number; y: number; width: number; height: number }) => void;
  onCloneSelectedShape?: () => void;
  onSelectShape: (shapeId: string | null, additive?: boolean) => void;
  onSelectShapes: (shapeIds: string[]) => void;
  onTransformPolygon: (
    shapeId: string,
    nextBounds: { x: number; y: number; width: number; height: number },
    scaleX: number,
    scaleY: number,
  ) => void;
  onTranslateShape: (shapeId: string, deltaX: number, deltaY: number) => void;
  onTranslateShapes: (shapeIds: string[], deltaX: number, deltaY: number) => void;
  onViewportChange: (viewport: SeatMapEditorViewport) => void;
  onDeleteSelected: () => void;
  onRedo: () => void;
  onSetActiveTool?: (tool: SeatMapEditorTool) => void;
  onToggleReference: () => void;
  onUndo: () => void;
}

export default function SeatMapCanvasShell({
  document,
  viewport,
  referenceImageVisible,
  showGrid = true,
  externalResetViewKey = 0,
  activeTool = "select",
  selectedShapeIds,
  selectedSeatId,
  selectedSeatIds,
  onClearSeatSelection,
  onMoveSeat,
  onMoveSeatBlock,
  onResizeSeatBlock,
  onResizeShape,
  onSelectSeat,
  onSelectSeatsInShape,
  onCreateSeatRowsFromBounds,
  onSelectShape,
  onSelectShapes,
  onTransformPolygon,
  onTranslateShape,
  onTranslateShapes,
  onViewportChange,
  onSetActiveTool,
  onToggleReference,
}: SeatMapCanvasShellProps) {
  const [resetViewKey, setResetViewKey] = useState(0);

  const handleResetView = () => {
    setResetViewKey((current) => current + 1);
  };

  const handleZoomStep = (delta: number) => {
    const oldScale = viewport.scale;
    const effectiveDelta =
      (delta < 0 && oldScale <= 0.3) || (delta > 0 && oldScale < 0.25)
        ? Math.sign(delta) * 0.05
        : delta;
    const nextScale = Math.min(4, Math.max(0.05, Number((oldScale + effectiveDelta).toFixed(2))));
    if (nextScale === oldScale) {
      return;
    }
    // Keep the visual center of the canvas (DEFAULT_VIEWPORT 960x640 / 2) anchored while zooming
    const centerAnchorX = 480;
    const centerAnchorY = 320;
    const worldCenterX = (centerAnchorX - viewport.x) / oldScale;
    const worldCenterY = (centerAnchorY - viewport.y) / oldScale;

    onViewportChange({
      scale: nextScale,
      x: centerAnchorX - worldCenterX * nextScale,
      y: centerAnchorY - worldCenterY * nextScale,
    });
  };

  const hasReferenceImage = Boolean(document.referenceImageUrl?.trim());
  const activeEditingShape =
    activeTool === "editSeats" && selectedShapeIds.length === 1
      ? document.shapes.find((shape) => shape.id === selectedShapeIds[0]) ?? null
      : null;

  return (
    <section className="seat-map-canvas-workspace">
      <div className="seat-map-canvas-card" style={{ position: "relative" }}>
        <div className="seat-map-canvas-grid" />

        {/* Active Edit Seats context pill (Seatmap.pro style) */}
        {activeTool === "editSeats" ? (
          <div className="smp-canvas-mode-pill">
            <span>
              {activeEditingShape
                ? `Đang sửa ghế: ${activeEditingShape.name}`
                : "Chế độ sửa ghế: Click hoặc kéo quét chọn ghế"}
            </span>
          </div>
        ) : null}

        {/* Hint pill when Seat Rows drawing mode is active */}
        {activeTool === "seatRows" ? (
          <div className="smp-canvas-mode-pill is-blue">
            <span>Kéo thả trên lưới để vẽ nhanh phân khu hàng ghế</span>
          </div>
        ) : null}

        <SeatMapEditorCanvas
          document={document}
          viewport={viewport}
          referenceImageVisible={referenceImageVisible}
          activeTool={activeTool}
          selectedShapeIds={selectedShapeIds}
          selectedSeatId={selectedSeatId}
          selectedSeatIds={selectedSeatIds}
          onClearSeatSelection={onClearSeatSelection}
          onMoveSeat={onMoveSeat}
          onMoveSeatBlock={onMoveSeatBlock}
          onResizeSeatBlock={onResizeSeatBlock}
          onResizeShape={onResizeShape}
          onSelectSeat={onSelectSeat}
          onSelectSeatsInShape={onSelectSeatsInShape}
          onCreateSeatRowsFromBounds={onCreateSeatRowsFromBounds}
          onSetActiveTool={onSetActiveTool}
          onSelectShape={onSelectShape}
          onSelectShapes={onSelectShapes}
          onTransformPolygon={onTransformPolygon}
          onTranslateShape={onTranslateShape}
          onTranslateShapes={onTranslateShapes}
          onViewportChange={onViewportChange}
          resetViewKey={resetViewKey + externalResetViewKey}
          showGrid={showGrid}
        />

        {/* Bottom-right floating canvas controls (Seatmap.pro style) */}
        <div className="smp-canvas-bottom-controls">
          {hasReferenceImage ? (
            <button
              type="button"
              className={`smp-canvas-circle-btn ${referenceImageVisible ? "is-active" : ""}`}
              onClick={onToggleReference}
              title={referenceImageVisible ? "Ẩn ảnh nền tham chiếu" : "Hiện ảnh nền tham chiếu"}
            >
              {referenceImageVisible ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          ) : null}

          <button
            type="button"
            className="smp-canvas-circle-btn"
            onClick={handleResetView}
            title="Căn vừa toàn bộ sơ đồ (Reset View)"
          >
            <Maximize2 size={16} />
          </button>

          <div className="smp-canvas-zoom-pill">
            <button
              type="button"
              onClick={() => handleZoomStep(-0.15)}
              title="Thu nhỏ"
            >
              <Minus size={14} />
            </button>
            <button
              type="button"
              className="smp-canvas-zoom-pct"
              onClick={handleResetView}
              title="Đặt lại góc nhìn"
            >
              {Math.round(viewport.scale * 100)}%
            </button>
            <button
              type="button"
              onClick={() => handleZoomStep(0.15)}
              title="Phóng to"
            >
              <Plus size={14} />
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}
