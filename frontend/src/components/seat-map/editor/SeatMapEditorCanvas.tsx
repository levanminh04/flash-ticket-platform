import { useEffect, useMemo, useRef, useState } from "react";
import {
  Ellipse,
  Group,
  Image as KonvaImage,
  Layer,
  Line,
  Path,
  Rect,
  Stage,
  Text,
  Transformer,
} from "react-konva";
import {
  SeatMapEditorDocument,
  SeatMapEditorShape,
  SeatMapEditorTool,
  SeatMapEditorViewport,
} from "./seatMapEditorTypes";
import { EDITOR_GRID_CELL_SIZE, estimateSeatGridFromBounds } from "./seatMapEditorUtils";
import { SectorRenderer } from "../shared/SectorRenderer";
import { SeatRenderer } from "../shared/SeatRenderer";
import {
  asNumber,
  createFanSectorPath,
  createRingSectorPath,
  BoundsLike,
  polarToCartesian,
} from "../shared/sectorPathUtils";

const MIN_SCALE = 0.05;
const MAX_SCALE = 4;
const ZOOM_RATIO = 1.08;
const DEFAULT_VIEWPORT_WIDTH = 960;
const DEFAULT_VIEWPORT_HEIGHT = 640;

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function useHtmlImage(src?: string) {
  const [image, setImage] = useState<HTMLImageElement | null>(null);

  useEffect(() => {
    if (!src) {
      setImage(null);
      return;
    }

    const nextImage = new window.Image();
    if (!src.startsWith("data:") && !src.startsWith("blob:")) {
      nextImage.crossOrigin = "anonymous";
    }
    nextImage.src = src;

    const handleLoad = () => setImage(nextImage);
    nextImage.addEventListener("load", handleLoad);

    return () => {
      nextImage.removeEventListener("load", handleLoad);
    };
  }, [src]);

  return image;
}

export interface SeatMapEditorCanvasProps {
  document: SeatMapEditorDocument;
  viewport: SeatMapEditorViewport;
  referenceImageVisible: boolean;
  showGrid?: boolean;
  activeTool?: SeatMapEditorTool;
  selectedShapeIds: string[];
  selectedSeatId: string | null;
  selectedSeatIds: string[];
  onClearSeatSelection: () => void;
  onSelectShape: (shapeId: string | null, additive?: boolean) => void;
  onSelectSeat: (shapeId: string, seatId: string | null, additive?: boolean) => void;
  onSelectSeatsInShape?: (shapeId: string, seatIds: string[], additive?: boolean) => void;
  onCreateSeatRowsFromBounds?: (bounds: { x: number; y: number; width: number; height: number }) => void;
  onSetActiveTool?: (tool: SeatMapEditorTool) => void;
  onViewportChange: (viewport: SeatMapEditorViewport) => void;
  onResizeShape: (
    shapeId: string,
    nextBounds: { x: number; y: number; width: number; height: number },
    nextPoints?: number[],
  ) => void;
  onSelectShapes: (shapeIds: string[]) => void;
  onTransformPolygon: (
    shapeId: string,
    nextBounds: { x: number; y: number; width: number; height: number },
    scaleX: number,
    scaleY: number,
  ) => void;
  onTranslateShape: (shapeId: string, deltaX: number, deltaY: number) => void;
  onTranslateShapes: (shapeIds: string[], deltaX: number, deltaY: number) => void;
  onMoveSeat: (shapeId: string, seatId: string, nextX: number, nextY: number) => void;
  onMoveSeatBlock: (shapeId: string, deltaX: number, deltaY: number) => void;
  resetViewKey?: number;
  onResizeSeatBlock: (
    shapeId: string,
    previousBounds: { x: number; y: number; width: number; height: number },
    nextBounds: { x: number; y: number; width: number; height: number },
  ) => void;
}

function getMapDataObject(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function normalizeAngle(angle: number) {
  return ((angle % 360) + 360) % 360;
}

function isAngleInSweep(angle: number, startAngle: number, endAngle: number) {
  const start = normalizeAngle(startAngle);
  const end = normalizeAngle(endAngle);
  const target = normalizeAngle(angle);

  if (end >= start) {
    return target >= start && target <= end;
  }

  return target >= start || target <= end;
}

function getBoundsFromCoordinates(points: Array<{ x: number; y: number }>): BoundsLike {
  const xs = points.map((point) => point.x);
  const ys = points.map((point) => point.y);
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

function getShapeSeatRadius(shape: SeatMapEditorDocument["shapes"][number]) {
  return shape.seatLayout?.seatRadius ?? 6;
}

function getRadialShapeConfig(shape: SeatMapEditorDocument["shapes"][number]) {
  const mapData = getMapDataObject(shape.mapData);
  const cx = asNumber(mapData.cx) ?? shape.bounds.x + shape.bounds.width / 2;
  const cy = asNumber(mapData.cy) ?? shape.bounds.y + shape.bounds.height / 2;
  const outerRadius = Math.max(asNumber(mapData.outerRadius) ?? Math.max(shape.bounds.width, shape.bounds.height) / 2, 1);
  const innerRadius = Math.max(asNumber(mapData.innerRadius) ?? (shape.shapeType === "ringSection" ? outerRadius * 0.72 : 0), 0);
  const startAngle = asNumber(mapData.startAngle) ?? -70;
  const endAngle = asNumber(mapData.endAngle) ?? 70;

  return { cx, cy, outerRadius, innerRadius, startAngle, endAngle };
}

function getRadialShapeBounds(shape: SeatMapEditorDocument["shapes"][number]): BoundsLike {
  const config = getRadialShapeConfig(shape);
  const sampleAngles = [config.startAngle, config.endAngle, -180, -90, 0, 90, 180]
    .filter((angle) => isAngleInSweep(angle, config.startAngle, config.endAngle));
  const points = sampleAngles.flatMap((angle) => [
    polarToCartesian(config.cx, config.cy, config.outerRadius, angle),
    polarToCartesian(config.cx, config.cy, config.innerRadius, angle),
  ]);

  return getBoundsFromCoordinates(points);
}

function getShapeSelectionBounds(shape: SeatMapEditorDocument["shapes"][number]): BoundsLike {
  if (shape.shapeType === "ringSection" || shape.shapeType === "fan") {
    return getRadialShapeBounds(shape);
  }

  return shape.bounds;
}

function rectsIntersect(a: BoundsLike, b: BoundsLike) {
  return (
    a.x <= b.x + b.width &&
    a.x + a.width >= b.x &&
    a.y <= b.y + b.height &&
    a.y + a.height >= b.y
  );
}

function getRadialPathData(shape: SeatMapEditorDocument["shapes"][number]) {
  if (shape.shapeType !== "ringSection" && shape.shapeType !== "fan") {
    return null;
  }

  const config = getRadialShapeConfig(shape);
  return shape.shapeType === "ringSection"
    ? createRingSectorPath(config)
    : createFanSectorPath(config);
}

interface RowLabelMarker {
  key: string;
  text: string;
  x: number;
  y: number;
  rotation: number;
}

function computeShapeRowLabels(shape: SeatMapEditorShape): RowLabelMarker[] {
  const pos = shape.seatLayout?.rowLabelPosition ?? "left";
  if (pos === "none" || shape.seatLayout?.mode === "table") {
    return [];
  }

  const labeledSeats = shape.seats.filter((seat) => Boolean(seat.rowName));
  if (labeledSeats.length === 0) {
    return [];
  }

  const rowsMap = new Map<string, typeof labeledSeats>();
  labeledSeats.forEach((seat) => {
    const rowKey = seat.rowName ?? "";
    const list = rowsMap.get(rowKey);
    if (list) {
      list.push(seat);
    } else {
      rowsMap.set(rowKey, [seat]);
    }
  });

  const markers: RowLabelMarker[] = [];
  const offsetDist = Math.max((shape.seatLayout?.seatRadius ?? 6) + 11, 16);
  const fallbackRotRad = ((shape.seatLayout?.rotation ?? 0) * Math.PI) / 180;

  rowsMap.forEach((rowSeats, rowName) => {
    if (rowSeats.length === 0 || rowSeats.every((seat) => seat.hidden)) return;
    const first = rowSeats[0];
    const last = rowSeats[rowSeats.length - 1];
    const dx = last.x - first.x;
    const dy = last.y - first.y;
    const dist = Math.hypot(dx, dy);
    const ux = dist > 1 ? dx / dist : Math.cos(fallbackRotRad);
    const uy = dist > 1 ? dy / dist : Math.sin(fallbackRotRad);
    const angleDeg = (Math.atan2(uy, ux) * 180) / Math.PI;
    const readableAngle =
      angleDeg > 90 ? angleDeg - 180 : angleDeg < -90 ? angleDeg + 180 : angleDeg;

    if (pos === "left" || pos === "both") {
      markers.push({
        key: `${shape.id}-${rowName}-L`,
        text: rowName,
        x: first.x - ux * offsetDist,
        y: first.y - uy * offsetDist,
        rotation: readableAngle,
      });
    }
    if (pos === "right" || pos === "both") {
      markers.push({
        key: `${shape.id}-${rowName}-R`,
        text: rowName,
        x: last.x + ux * offsetDist,
        y: last.y + uy * offsetDist,
        rotation: readableAngle,
      });
    }
  });

  return markers;
}

export default function SeatMapEditorCanvas({
  document,
  viewport,
  referenceImageVisible,
  activeTool = "select",
  selectedShapeIds,
  selectedSeatId,
  selectedSeatIds,
  onClearSeatSelection,
  onSelectShape,
  onSelectSeat,
  onSelectSeatsInShape,
  onCreateSeatRowsFromBounds,
  onSetActiveTool,
  onViewportChange,
  onResizeShape,
  onSelectShapes,
  onTransformPolygon,
  onTranslateShape,
  onTranslateShapes,
  onMoveSeat: _onMoveSeat,
  onMoveSeatBlock: _onMoveSeatBlock,
  resetViewKey = 0,
  showGrid = true,
  onResizeSeatBlock: _onResizeSeatBlock,
}: SeatMapEditorCanvasProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const transformerRef = useRef<any>(null);
  const nodeRefs = useRef<Record<string, any>>({});
  const dragOriginRef = useRef<Record<string, { x: number; y: number }>>({});
  const panStartRef = useRef<{
    pointerX: number;
    pointerY: number;
    viewportX: number;
    viewportY: number;
    moved: boolean;
  } | null>(null);
  const selectionStartRef = useRef<{ x: number; y: number } | null>(null);
  const drawRowsStartRef = useRef<{ x: number; y: number } | null>(null);
  const [viewportSize, setViewportSize] = useState({ width: 960, height: 640 });
  const [isPanning, setIsPanning] = useState(false);
  const [selectionBox, setSelectionBox] = useState<BoundsLike | null>(null);
  const [drawRowsBox, setDrawRowsBox] = useState<BoundsLike | null>(null);
  const lastFitKeyRef = useRef<string | null>(null);
  const referenceImage = useHtmlImage(document.referenceImageUrl);
  const stageOffset = useMemo(
    () => ({
      x: (viewportSize.width - DEFAULT_VIEWPORT_WIDTH) / 2,
      y: (viewportSize.height - DEFAULT_VIEWPORT_HEIGHT) / 2,
    }),
    [viewportSize.height, viewportSize.width],
  );

  useEffect(() => {
    const element = containerRef.current;
    if (!element) {
      return;
    }

    const observer = new ResizeObserver((entries) => {
      const nextWidth = entries[0]?.contentRect.width ?? element.clientWidth;
      const nextHeight = entries[0]?.contentRect.height ?? element.clientHeight;
      setViewportSize({
        width: Math.max(nextWidth, 320),
        height: Math.max(nextHeight, 480),
      });
    });

    observer.observe(element);

    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!document.width || !document.height || !viewportSize.width || !viewportSize.height) {
      return;
    }

    const fitKey = `${document.width}x${document.height}:${viewportSize.width}x${viewportSize.height}:${resetViewKey}`;
    if (lastFitKeyRef.current === fitKey) {
      return;
    }
    lastFitKeyRef.current = fitKey;

    const visibleShapes = document.shapes.filter((shape) => shape.visible !== false);
    const horizontalPadding = 48;
    const topPadding = 76;
    const bottomPadding = visibleShapes.length > 0 ? 86 : 28;
    const availableWidth = Math.max(viewportSize.width - horizontalPadding * 2, 320);
    const availableHeight = Math.max(viewportSize.height - topPadding - bottomPadding, 260);

    let contentMinX = 0;
    let contentMinY = 0;
    let contentMaxX = document.width;
    let contentMaxY = document.height;

    if (visibleShapes.length > 0) {
      contentMinX = Infinity;
      contentMinY = Infinity;
      contentMaxX = -Infinity;
      contentMaxY = -Infinity;
      for (const shape of visibleShapes) {
        const b = getShapeSelectionBounds(shape);
        contentMinX = Math.min(contentMinX, b.x);
        contentMinY = Math.min(contentMinY, b.y);
        contentMaxX = Math.max(contentMaxX, b.x + b.width);
        contentMaxY = Math.max(contentMaxY, b.y + b.height);
        const pad = Math.max((shape.seatLayout?.seatRadius ?? 7) * 2, 14);
        for (const seat of shape.seats) {
          if (seat.hidden) continue;
          contentMinX = Math.min(contentMinX, seat.x - pad);
          contentMinY = Math.min(contentMinY, seat.y - pad);
          contentMaxX = Math.max(contentMaxX, seat.x + pad);
          contentMaxY = Math.max(contentMaxY, seat.y + pad);
        }
      }
      if (!Number.isFinite(contentMinX) || !Number.isFinite(contentMinY)) {
        contentMinX = 0;
        contentMinY = 0;
        contentMaxX = document.width;
        contentMaxY = document.height;
      }
    }

    const contentWidth = Math.max(contentMaxX - contentMinX, 120);
    const contentHeight = Math.max(contentMaxY - contentMinY, 120);

    const nextScale = clamp(
      Math.min(availableWidth / contentWidth, availableHeight / contentHeight, 1.2),
      MIN_SCALE,
      MAX_SCALE,
    );

    const centeredX =
      horizontalPadding +
      (availableWidth - contentWidth * nextScale) / 2 -
      contentMinX * nextScale -
      stageOffset.x;
    const centeredY =
      topPadding +
      (availableHeight - contentHeight * nextScale) / 2 -
      contentMinY * nextScale -
      stageOffset.y;

    onViewportChange({
      scale: nextScale,
      x: centeredX,
      y: centeredY,
    });
  }, [
    document.height,
    document.shapes,
    document.width,
    onViewportChange,
    resetViewKey,
    stageOffset.x,
    stageOffset.y,
    viewportSize.height,
    viewportSize.width,
  ]);

  useEffect(() => {
    const transformer = transformerRef.current;
    if (!transformer) {
      return;
    }

    const selectedNodes = selectedShapeIds
      .map((shapeId) => {
        const targetShape = document.shapes.find((shape) => shape.id === shapeId);
        if (!targetShape || targetShape.locked) {
          return null;
        }

        return nodeRefs.current[shapeId];
      })
      .filter(Boolean);

    transformer.nodes(selectedNodes.length === 1 ? selectedNodes : []);
    transformer.getLayer()?.batchDraw();
  }, [selectedShapeIds, document.shapes]);

  const worldStyle = useMemo(
    () => ({
      x: viewport.x + stageOffset.x,
      y: viewport.y + stageOffset.y,
      scaleX: viewport.scale,
      scaleY: viewport.scale,
    }),
    [stageOffset.x, stageOffset.y, viewport],
  );

  const handleWheel = (event: any) => {
    event.evt.preventDefault();

    const stage = event.target.getStage();
    const pointer = stage?.getPointerPosition();
    if (!pointer) {
      return;
    }

    const oldScale = viewport.scale;
    const direction = event.evt.deltaY > 0 ? -1 : 1;
    const nextScale = clamp(
      direction > 0 ? oldScale * ZOOM_RATIO : oldScale / ZOOM_RATIO,
      MIN_SCALE,
      MAX_SCALE,
    );

    const mousePoint = {
      x: (pointer.x - worldStyle.x) / oldScale,
      y: (pointer.y - worldStyle.y) / oldScale,
    };

    onViewportChange({
      scale: nextScale,
      x: pointer.x - mousePoint.x * nextScale - stageOffset.x,
      y: pointer.y - mousePoint.y * nextScale - stageOffset.y,
    });
  };

  const getWorldPointer = (event: any) => {
    const stage = event.target.getStage();
    const pointer = stage?.getPointerPosition();
    if (!pointer) {
      return null;
    }

    return {
      x: (pointer.x - worldStyle.x) / viewport.scale,
      y: (pointer.y - worldStyle.y) / viewport.scale,
    };
  };

  const normalizeSelectionBox = (start: { x: number; y: number }, end: { x: number; y: number }): BoundsLike => ({
    x: Math.min(start.x, end.x),
    y: Math.min(start.y, end.y),
    width: Math.abs(end.x - start.x),
    height: Math.abs(end.y - start.y),
  });

  const handleMouseDown = (event: any) => {
    if (activeTool === "seatRows" && event.evt.button === 0) {
      const pointer = getWorldPointer(event);
      if (!pointer) {
        return;
      }
      event.evt.preventDefault();
      drawRowsStartRef.current = pointer;
      setDrawRowsBox({ x: pointer.x, y: pointer.y, width: 0, height: 0 });
      return;
    }

    if (event.evt.button === 2 || (activeTool === "editSeats" && event.evt.button === 0 && event.target.attrs?.name !== "seat-node")) {
      const pointer = getWorldPointer(event);
      if (!pointer) {
        return;
      }

      event.evt.preventDefault();
      selectionStartRef.current = pointer;
      setSelectionBox({ x: pointer.x, y: pointer.y, width: 0, height: 0 });
      return;
    }

    const targetName = event.target.attrs?.name;
    if (
      targetName === "shape-node" ||
      targetName === "seat-node" ||
      targetName === "seat-block-node" ||
      (typeof targetName === "string" && targetName.includes("anchor"))
    ) {
      return;
    }

    if (targetName === "seat-map-background") {
      const stage = event.target.getStage();
      const pointer = stage?.getPointerPosition();
      if (!pointer) {
        return;
      }

      event.evt.preventDefault();
      panStartRef.current = {
        pointerX: pointer.x,
        pointerY: pointer.y,
        viewportX: viewport.x,
        viewportY: viewport.y,
        moved: false,
      };
      setIsPanning(true);
    }
  };

  const handleMouseMove = (event: any) => {
    const drawRowsStart = drawRowsStartRef.current;
    if (drawRowsStart) {
      const pointer = getWorldPointer(event);
      if (!pointer) {
        return;
      }
      event.evt.preventDefault();
      const snappedStart = {
        x: Math.round(drawRowsStart.x / EDITOR_GRID_CELL_SIZE) * EDITOR_GRID_CELL_SIZE,
        y: Math.round(drawRowsStart.y / EDITOR_GRID_CELL_SIZE) * EDITOR_GRID_CELL_SIZE,
      };
      const snappedEnd = {
        x: Math.round(pointer.x / EDITOR_GRID_CELL_SIZE) * EDITOR_GRID_CELL_SIZE,
        y: Math.round(pointer.y / EDITOR_GRID_CELL_SIZE) * EDITOR_GRID_CELL_SIZE,
      };
      setDrawRowsBox(normalizeSelectionBox(snappedStart, snappedEnd));
      return;
    }

    const selectionStart = selectionStartRef.current;
    if (selectionStart) {
      const pointer = getWorldPointer(event);
      if (!pointer) {
        return;
      }

      event.evt.preventDefault();
      setSelectionBox(normalizeSelectionBox(selectionStart, pointer));
      return;
    }

    const panStart = panStartRef.current;
    if (!panStart) {
      return;
    }

    const stage = event.target.getStage();
    const pointer = stage?.getPointerPosition();
    if (!pointer) {
      return;
    }

    const deltaX = pointer.x - panStart.pointerX;
    const deltaY = pointer.y - panStart.pointerY;
    const moved = Math.abs(deltaX) > 3 || Math.abs(deltaY) > 3;
    panStartRef.current = {
      ...panStart,
      moved: panStart.moved || moved,
    };

    onViewportChange({
      ...viewport,
      x: panStart.viewportX + deltaX,
      y: panStart.viewportY + deltaY,
    });
  };

  const handleMouseUp = (event?: any) => {
    const drawRowsStart = drawRowsStartRef.current;
    if (drawRowsStart) {
      const finishedBox = drawRowsBox;
      drawRowsStartRef.current = null;
      setDrawRowsBox(null);
      if (finishedBox && finishedBox.width >= 40 && finishedBox.height >= 32) {
        onCreateSeatRowsFromBounds?.(finishedBox);
      }
      return;
    }

    const selectionStart = selectionStartRef.current;
    if (selectionStart && selectionBox) {
      const isSmallClick = selectionBox.width < 6 && selectionBox.height < 6;
      const additive = Boolean(event?.evt?.shiftKey || event?.evt?.ctrlKey || event?.evt?.metaKey);

      if (activeTool === "editSeats") {
        selectionStartRef.current = null;
        setSelectionBox(null);
        if (isSmallClick) {
          const pointer = getWorldPointer(event);
          const clickedInsideSeatedShape = pointer
            ? document.shapes.some(
                (shape) =>
                  shape.visible !== false &&
                  shape.seats.length > 0 &&
                  pointer.x >= shape.bounds.x &&
                  pointer.x <= shape.bounds.x + shape.bounds.width &&
                  pointer.y >= shape.bounds.y &&
                  pointer.y <= shape.bounds.y + shape.bounds.height,
              )
            : false;
          if (!clickedInsideSeatedShape) {
            onClearSeatSelection();
          }
          return;
        }

        const candidateShapes = selectedShapeIds.length
          ? document.shapes.filter((shape) => selectedShapeIds.includes(shape.id))
          : document.shapes.filter((shape) => shape.visible !== false && shape.seats.length > 0);

        for (const shape of candidateShapes) {
          const matchedSeatIds = shape.seats
            .filter(
              (seat) =>
                !seat.hidden &&
                seat.x >= selectionBox.x &&
                seat.x <= selectionBox.x + selectionBox.width &&
                seat.y >= selectionBox.y &&
                seat.y <= selectionBox.y + selectionBox.height,
            )
            .map((seat) => seat.id);

          if (matchedSeatIds.length > 0) {
            onSelectSeatsInShape?.(shape.id, matchedSeatIds, additive);
            return;
          }
        }
        return;
      }

      const selectedIds = document.shapes
        .filter((shape) => shape.visible !== false && shape.mapData?.decorative !== true)
        .filter((shape) => shape.shapeType !== "stage" && shape.shapeType !== "foh")
        .filter((shape) => rectsIntersect(selectionBox, getShapeSelectionBounds(shape)))
        .map((shape) => shape.id);

      selectionStartRef.current = null;
      setSelectionBox(null);
      onSelectShapes(selectedIds);
      return;
    }

    const panStart = panStartRef.current;
    if (!panStart) {
      return;
    }

    panStartRef.current = null;
    setIsPanning(false);

    if (!panStart.moved) {
      onClearSeatSelection();
      onSelectShape(null);
    }
  };

  const isAdditiveSelection = (nativeEvent: { ctrlKey?: boolean; metaKey?: boolean; shiftKey?: boolean }) =>
    Boolean(nativeEvent.ctrlKey || nativeEvent.metaKey || nativeEvent.shiftKey);
  const getShapeMoveTargetIds = (shapeId: string) =>
    selectedShapeIds.length > 1 && selectedShapeIds.includes(shapeId) ? selectedShapeIds : [shapeId];
  const orderedShapes = useMemo(() => {
    const regularShapes = document.shapes.filter(
      (shape) => shape.shapeType !== "stage" && shape.shapeType !== "foh" && shape.mapData?.decorative !== true,
    );
    const overlayShapes = document.shapes.filter(
      (shape) => shape.shapeType === "stage" || shape.shapeType === "foh" || shape.mapData?.decorative === true,
    );
    return [...regularShapes, ...overlayShapes];
  }, [document.shapes]);
  return (
    <div
      ref={containerRef}
      className={`organizer-seat-map-canvas-shell${isPanning ? " is-panning" : ""}`}
      style={{
        cursor: activeTool === "seatRows" || drawRowsBox ? "crosshair" : undefined,
      }}
      onContextMenu={(event) => event.preventDefault()}
    >
      <Stage
        width={viewportSize.width}
        height={viewportSize.height}
        onWheel={handleWheel}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onTouchStart={handleMouseDown}
        onTouchMove={handleMouseMove}
        onTouchEnd={handleMouseUp}
        onContextMenu={(event: any) => event.evt.preventDefault()}
      >
        <Layer>
          <Rect
            name="seat-map-background"
            x={0}
            y={0}
            width={viewportSize.width}
            height={viewportSize.height}
            fill="transparent"
            stroke="transparent"
            strokeWidth={0}
          />
        </Layer>

        <Layer>
          <Group {...worldStyle}>
            {showGrid ? (() => {
              const step = EDITOR_GRID_CELL_SIZE;
              const safeScale = Math.max(viewport.scale, 0.05);
              const minX = Math.floor((-worldStyle.x / safeScale) / step) * step - step * 2;
              const maxX = Math.ceil(((viewportSize.width - worldStyle.x) / safeScale) / step) * step + step * 2;
              const minY = Math.floor((-worldStyle.y / safeScale) / step) * step - step * 2;
              const maxY = Math.ceil(((viewportSize.height - worldStyle.y) / safeScale) / step) * step + step * 2;
              const lines: JSX.Element[] = [];
              const strokeWidth = 1 / safeScale;
              for (let gx = minX; gx <= maxX; gx += step) {
                lines.push(
                  <Line
                    key={`gx-${gx}`}
                    points={[gx, minY, gx, maxY]}
                    stroke="rgba(148, 163, 184, 0.26)"
                    strokeWidth={strokeWidth}
                    listening={false}
                  />,
                );
              }
              for (let gy = minY; gy <= maxY; gy += step) {
                lines.push(
                  <Line
                    key={`gy-${gy}`}
                    points={[minX, gy, maxX, gy]}
                    stroke="rgba(148, 163, 184, 0.26)"
                    strokeWidth={strokeWidth}
                    listening={false}
                  />,
                );
              }
              return lines;
            })() : null}
            {referenceImageVisible && referenceImage ? (
              <KonvaImage
                image={referenceImage}
                x={0}
                y={0}
                width={document.width}
                height={document.height}
                opacity={0.34}
                listening={false}
              />
            ) : null}

            {orderedShapes.map((shape) => {
              if (!shape.visible) {
                return null;
              }

              const isSelected = selectedShapeIds.includes(shape.id);
              const draggable =
                !shape.locked && activeTool !== "editSeats" && activeTool !== "seatRows";
              const showSeatsForShape = shape.seats.length > 0;
              const handleShapeDoubleClick = () => {
                onSelectShape(shape.id, false);
                if (shape.seats.length > 0) {
                  onSetActiveTool?.("editSeats");
                }
              };

              const handleShapeNodeClick = (event: any) => {
                const additive = isAdditiveSelection(event.evt as any);
                if (
                  activeTool === "editSeats" ||
                  (isSelected && selectedSeatIds.length > 0 && !additive)
                ) {
                  return;
                }
                onSelectShape(shape.id, additive);
              };

              return (
                <Group key={shape.id}>
                  <SectorRenderer
                    sector={{
                      id: shape.id,
                      name: shape.name,
                      label: shape.label,
                      sectorType: shape.sectorType,
                      totalCapacity: shape.totalCapacity,
                      seatCount: shape.seatCount,
                      shapeType: shape.shapeType,
                      color: shape.color,
                      bounds: shape.bounds,
                      points: shape.points,
                      mapData: shape.mapData,
                    }}
                    selected={isSelected}
                    draggable={false}
                    pointsAreRelative
                    showDepth
                    showLabel={!showSeatsForShape}
                    dimmedForSeats={showSeatsForShape}
                    listening={false}
                  />
                  {shape.shapeType === "polygon" && shape.points.length >= 6 ? (
                    <>
                      <Line
                        ref={(node) => {
                          nodeRefs.current[shape.id] = node;
                        }}
                        name="shape-node"
                        x={shape.bounds.x}
                        y={shape.bounds.y}
                        points={shape.points}
                        closed
                        draggable={draggable}
                        fill="rgba(0, 0, 0, 0.01)"
                        stroke="rgba(0, 0, 0, 0)"
                        strokeWidth={Math.max(isSelected ? 3 : 2, 12)}
                        onClick={handleShapeNodeClick}
                        onTap={handleShapeNodeClick}
                        onDblClick={handleShapeDoubleClick}
                        onDblTap={handleShapeDoubleClick}
                        onDragStart={() => {
                          dragOriginRef.current[shape.id] = {
                            x: shape.bounds.x,
                            y: shape.bounds.y,
                          };
                        }}
                        onDragEnd={(event) => {
                          const origin = dragOriginRef.current[shape.id];
                          if (!origin) {
                            return;
                          }
                          const nextX = event.target.x();
                          const nextY = event.target.y();
                          const targetShapeIds = getShapeMoveTargetIds(shape.id);
                          const deltaX = nextX - origin.x;
                          const deltaY = nextY - origin.y;
                          if (targetShapeIds.length > 1) {
                            onTranslateShapes(targetShapeIds, deltaX, deltaY);
                          } else {
                            onTranslateShape(shape.id, deltaX, deltaY);
                          }
                          event.target.position({ x: shape.bounds.x, y: shape.bounds.y });
                        }}
                        onTransformEnd={(event) => {
                          const node = event.target;
                          const scaleX = node.scaleX();
                          const scaleY = node.scaleY();
                          const nextBounds = {
                            x: node.x(),
                            y: node.y(),
                            width: Math.max(shape.bounds.width * scaleX, 24),
                            height: Math.max(shape.bounds.height * scaleY, 24),
                          };

                          onTransformPolygon(shape.id, nextBounds, scaleX, scaleY);
                          node.scaleX(1);
                          node.scaleY(1);
                          node.position({ x: nextBounds.x, y: nextBounds.y });
                        }}
                      />

                    </>
                  ) : shape.shapeType === "ringSection" || shape.shapeType === "fan" ? (
                    <Path
                      ref={(node) => {
                        nodeRefs.current[shape.id] = node;
                      }}
                      name="shape-node"
                      data={getRadialPathData(shape) ?? ""}
                      draggable={draggable}
                      fill="rgba(0, 0, 0, 0.01)"
                      stroke="rgba(0, 0, 0, 0)"
                      strokeWidth={Math.max(isSelected ? 3 : 2, 12)}
                      onClick={handleShapeNodeClick}
                      onTap={handleShapeNodeClick}
                      onDblClick={handleShapeDoubleClick}
                      onDblTap={handleShapeDoubleClick}
                      onDragStart={() => {
                        dragOriginRef.current[shape.id] = { x: 0, y: 0 };
                      }}
                      onDragEnd={(event) => {
                        const origin = dragOriginRef.current[shape.id];
                        if (!origin) {
                          return;
                        }
                        const nextX = event.target.x();
                        const nextY = event.target.y();
                        const targetShapeIds = getShapeMoveTargetIds(shape.id);
                        const deltaX = nextX - origin.x;
                        const deltaY = nextY - origin.y;
                        if (targetShapeIds.length > 1) {
                          onTranslateShapes(targetShapeIds, deltaX, deltaY);
                        } else {
                          onTranslateShape(shape.id, deltaX, deltaY);
                        }
                        event.target.position({ x: 0, y: 0 });
                      }}
                      onTransformEnd={(event) => {
                        const node = event.target as any;
                        node.scaleX(1);
                        node.scaleY(1);
                        node.position({ x: 0, y: 0 });
                      }}
                    />
                  ) : shape.shapeType === "circle" || shape.shapeType === "ellipse" ? (
                    <>
                      <Ellipse
                        ref={(node) => {
                          nodeRefs.current[shape.id] = node;
                        }}
                        name="shape-node"
                        x={shape.bounds.x + shape.bounds.width / 2}
                        y={shape.bounds.y + shape.bounds.height / 2}
                        radiusX={shape.bounds.width / 2}
                        radiusY={shape.bounds.height / 2}
                        draggable={draggable}
                        fill="rgba(0, 0, 0, 0.01)"
                        stroke="rgba(0, 0, 0, 0)"
                        strokeWidth={Math.max(isSelected ? 3 : 2, 12)}
                        onClick={handleShapeNodeClick}
                        onTap={handleShapeNodeClick}
                        onDblClick={handleShapeDoubleClick}
                        onDblTap={handleShapeDoubleClick}
                        onDragStart={() => {
                          dragOriginRef.current[shape.id] = {
                            x: shape.bounds.x + shape.bounds.width / 2,
                            y: shape.bounds.y + shape.bounds.height / 2,
                          };
                        }}
                        onDragEnd={(event) => {
                          const origin = dragOriginRef.current[shape.id];
                          if (!origin) {
                            return;
                          }
                          const nextX = event.target.x();
                          const nextY = event.target.y();
                          const targetShapeIds = getShapeMoveTargetIds(shape.id);
                          const deltaX = nextX - origin.x;
                          const deltaY = nextY - origin.y;
                          if (targetShapeIds.length > 1) {
                            onTranslateShapes(targetShapeIds, deltaX, deltaY);
                          } else {
                            onTranslateShape(shape.id, deltaX, deltaY);
                          }
                          event.target.position({
                            x: shape.bounds.x + shape.bounds.width / 2,
                            y: shape.bounds.y + shape.bounds.height / 2,
                          });
                        }}
                        onTransformEnd={(event) => {
                          const node = event.target as any;
                          const scaleX = node.scaleX();
                          const scaleY = node.scaleY();
                          const nextWidth = Math.max(shape.bounds.width * scaleX, 24);
                          const nextHeight =
                            shape.shapeType === "circle"
                              ? nextWidth
                              : Math.max(shape.bounds.height * scaleY, 24);
                          const nextBounds = {
                            x: node.x() - nextWidth / 2,
                            y: node.y() - nextHeight / 2,
                            width: nextWidth,
                            height: nextHeight,
                          };

                          onResizeShape(shape.id, nextBounds);
                          node.scaleX(1);
                          node.scaleY(1);
                          node.position({
                            x: nextBounds.x + nextBounds.width / 2,
                            y: nextBounds.y + nextBounds.height / 2,
                          });
                          node.radiusX(nextBounds.width / 2);
                          node.radiusY(nextBounds.height / 2);
                        }}
                      />

                    </>
                  ) : (
                    <>
                      <Rect
                        ref={(node) => {
                          nodeRefs.current[shape.id] = node;
                        }}
                        name="shape-node"
                        x={shape.bounds.x}
                        y={shape.bounds.y}
                        width={shape.bounds.width}
                        height={shape.bounds.height}
                        cornerRadius={20}
                        draggable={draggable}
                        fill="rgba(0, 0, 0, 0.01)"
                        stroke="rgba(0, 0, 0, 0)"
                        strokeWidth={Math.max(isSelected ? 3 : 2, 12)}
                        onClick={handleShapeNodeClick}
                        onTap={handleShapeNodeClick}
                        onDblClick={handleShapeDoubleClick}
                        onDblTap={handleShapeDoubleClick}
                        onDragStart={() => {
                          dragOriginRef.current[shape.id] = {
                            x: shape.bounds.x,
                            y: shape.bounds.y,
                          };
                        }}
                        onDragEnd={(event) => {
                          const origin = dragOriginRef.current[shape.id];
                          if (!origin) {
                            return;
                          }
                          const isGridLocked = shape.mapData?.presetType === "seated_grid";
                          const rawX = event.target.x();
                          const rawY = event.target.y();
                          const nextX = isGridLocked
                            ? Math.round(rawX / EDITOR_GRID_CELL_SIZE) * EDITOR_GRID_CELL_SIZE
                            : rawX;
                          const nextY = isGridLocked
                            ? Math.round(rawY / EDITOR_GRID_CELL_SIZE) * EDITOR_GRID_CELL_SIZE
                            : rawY;
                          const targetShapeIds = getShapeMoveTargetIds(shape.id);
                          const deltaX = nextX - origin.x;
                          const deltaY = nextY - origin.y;
                          if (targetShapeIds.length > 1) {
                            onTranslateShapes(targetShapeIds, deltaX, deltaY);
                          } else {
                            onTranslateShape(shape.id, deltaX, deltaY);
                          }
                          event.target.position({ x: shape.bounds.x, y: shape.bounds.y });
                        }}
                        onTransformEnd={(event) => {
                          const node = event.target;
                          const scaleX = node.scaleX();
                          const scaleY = node.scaleY();
                          const isGridLocked = shape.mapData?.presetType === "seated_grid";
                          const rawX = node.x();
                          const rawY = node.y();
                          const rawW = Math.max(node.width() * scaleX, 24);
                          const rawH = Math.max(node.height() * scaleY, 24);
                          const nextBounds = isGridLocked
                            ? {
                                x: Math.round(rawX / EDITOR_GRID_CELL_SIZE) * EDITOR_GRID_CELL_SIZE,
                                y: Math.round(rawY / EDITOR_GRID_CELL_SIZE) * EDITOR_GRID_CELL_SIZE,
                                width: Math.max(
                                  EDITOR_GRID_CELL_SIZE * 2,
                                  Math.round(rawW / EDITOR_GRID_CELL_SIZE) * EDITOR_GRID_CELL_SIZE,
                                ),
                                height: Math.max(
                                  EDITOR_GRID_CELL_SIZE,
                                  Math.round(rawH / EDITOR_GRID_CELL_SIZE) * EDITOR_GRID_CELL_SIZE,
                                ),
                              }
                            : {
                                x: rawX,
                                y: rawY,
                                width: rawW,
                                height: rawH,
                              };

                          onResizeShape(shape.id, nextBounds);
                          node.scaleX(1);
                          node.scaleY(1);
                          node.position({ x: nextBounds.x, y: nextBounds.y });
                          node.size({
                            width: nextBounds.width,
                            height: nextBounds.height,
                          });
                        }}
                      />

                    </>
                  )}

                  {showSeatsForShape && (isSelected || activeTool === "editSeats")
                    ? computeShapeRowLabels(shape).map((marker) => (
                        <Group
                          key={marker.key}
                          x={marker.x}
                          y={marker.y}
                          rotation={marker.rotation}
                          listening={false}
                        >
                          <Text
                            x={-16}
                            y={-5}
                            width={32}
                            align="center"
                            text={marker.text}
                            fontSize={10}
                            fontStyle="700"
                            fill="#334155"
                            listening={false}
                            shadowColor="rgba(255, 255, 255, 0.9)"
                            shadowBlur={2}
                          />
                        </Group>
                      ))
                    : null}

                  {showSeatsForShape
                    ? shape.seats.map((seat) => {
                        const isSeatSelected = selectedSeatId === seat.id || selectedSeatIds.includes(seat.id);
                        const isSeatHidden = seat.hidden === true;
                        const seatEditingEnabled = shape.locked || activeTool === "editSeats";

                        return (
                          <Group key={seat.id}>
                            <SeatRenderer
                              id={seat.id}
                              x={seat.x}
                              y={seat.y}
                              radius={getShapeSeatRadius(shape)}
                              status={seat.inventoryStatus}
                              selected={isSeatSelected}
                              hidden={isSeatHidden}
                              disabled={false}
                              color={seat.colorCode ?? shape.color}
                              editable={seatEditingEnabled}
                              draggable={false}
                              memoizeEventHandlers
                              onClick={(event) => {
                                if (!seatEditingEnabled) {
                                  return;
                                }
                                event.cancelBubble = true;
                                onSelectSeat(shape.id, seat.id, true);
                              }}
                              onTap={(event) => {
                                if (!seatEditingEnabled) {
                                  return;
                                }
                                event.cancelBubble = true;
                                onSelectSeat(shape.id, seat.id, true);
                              }}
                            />
                          </Group>
                        );
                      })
                    : null}
                </Group>
              );
            })}

            {selectionBox ? (
              <Rect
                x={selectionBox.x}
                y={selectionBox.y}
                width={selectionBox.width}
                height={selectionBox.height}
                fill="rgba(22, 136, 240, 0.14)"
                stroke="#1688f0"
                strokeWidth={1.5}
                dash={[6, 4]}
                listening={false}
              />
            ) : null}

            {drawRowsBox && drawRowsBox.width > 12 && drawRowsBox.height > 12 ? (() => {
              const gridEstimate = estimateSeatGridFromBounds(drawRowsBox);
              const sizeText = `${gridEstimate.cols}x${gridEstimate.rows}`;
              const labelFontSize = Math.max(
                14,
                Math.min(
                  Math.round(22 / Math.max(viewport.scale, 0.35)),
                  Math.floor(drawRowsBox.height * 0.55),
                  Math.floor(drawRowsBox.width * 0.28),
                ),
              );
              return (
                <Group listening={false}>
                  <Rect
                    x={drawRowsBox.x}
                    y={drawRowsBox.y}
                    width={drawRowsBox.width}
                    height={drawRowsBox.height}
                    fill="rgba(22, 136, 240, 0.12)"
                    stroke="rgba(22, 136, 240, 0.28)"
                    strokeWidth={1 / Math.max(viewport.scale, 0.35)}
                  />
                  <Text
                    x={drawRowsBox.x}
                    y={drawRowsBox.y}
                    width={drawRowsBox.width}
                    height={drawRowsBox.height}
                    align="center"
                    verticalAlign="middle"
                    text={sizeText}
                    fontSize={labelFontSize}
                    fontStyle="600"
                    fill="#111827"
                  />
                </Group>
              );
            })() : null}

            <Transformer
              ref={transformerRef}
              rotateEnabled={false}
              borderStroke="#1688f0"
              borderStrokeWidth={1.5}
              anchorStroke="#1688f0"
              anchorFill="#ffffff"
              anchorSize={8}
              ignoreStroke
              enabledAnchors={[
                "top-left",
                "top-center",
                "top-right",
                "middle-right",
                "bottom-right",
                "bottom-center",
                "bottom-left",
                "middle-left",
              ]}
              boundBoxFunc={(_, nextBox) => ({
                ...nextBox,
                width: Math.max(nextBox.width, 24),
                height: Math.max(nextBox.height, 24),
              })}
            />
          </Group>
        </Layer>
      </Stage>
    </div>
  );
}
