import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Group,
  Layer,
  Stage,
} from "react-konva";
import { PublicSeatMap, PublicSeatSector, PublicSeat } from "../../../types/api";
import { SectorRenderer, SharedSector } from "../shared/SectorRenderer";
import { SeatRenderer } from "../shared/SeatRenderer";

interface BuyerSeatMapCanvasProps {
  seatMap: PublicSeatMap;
  zoom: number;
  resetViewKey?: number;
  selectedSeatIds: string[];
  selectedSectorId?: string | null;
  getSeatColor?: (sector: PublicSeatSector, seat: PublicSeat) => string;
  onZoomChange: (zoom: number) => void;
  onFitZoom?: (zoom: number) => void;
  onSeatClick: (sector: PublicSeatSector, seat: PublicSeat) => void;
  onSectorClick?: (sector: PublicSeatSector) => void;
}

interface DecorationItem {
  id: string;
  label?: string;
  colorCode?: string;
  shape?: Record<string, unknown>;
}

const DISABLED_SEAT_STATUSES = new Set(["RESERVED", "SOLD", "BLOCKED", "LOCKED"]);
const MIN_ZOOM = 0.1;
const MAX_ZOOM = 3;
const WHEEL_ZOOM_STEP = 1.08;

function asNumber(value: unknown): number | null {
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

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function getSectorBounds(mapData: Record<string, unknown> = {}) {
  const bounds = mapData.bounds as Record<string, unknown> | undefined;
  const x = asNumber(bounds?.x ?? mapData.x) ?? 0;
  const y = asNumber(bounds?.y ?? mapData.y) ?? 0;
  const width = Math.max(asNumber(bounds?.width ?? mapData.width) ?? 180, 24);
  const height = Math.max(asNumber(bounds?.height ?? mapData.height) ?? 120, 24);

  return { x, y, width, height };
}

function extendBoundsWithShape(
  acc: { minX: number; minY: number; maxX: number; maxY: number },
  mapData: Record<string, unknown> = {},
) {
  const bounds = getSectorBounds(mapData);
  acc.minX = Math.min(acc.minX, bounds.x);
  acc.minY = Math.min(acc.minY, bounds.y);
  acc.maxX = Math.max(acc.maxX, bounds.x + bounds.width);
  acc.maxY = Math.max(acc.maxY, bounds.y + bounds.height);

  const rawPoints = mapData.points;
  if (Array.isArray(rawPoints)) {
    for (let i = 0; i < rawPoints.length; i += 2) {
      const px = asNumber(rawPoints[i]);
      const py = asNumber(rawPoints[i + 1]);
      if (px !== null && py !== null) {
        acc.minX = Math.min(acc.minX, px);
        acc.minY = Math.min(acc.minY, py);
        acc.maxX = Math.max(acc.maxX, px);
        acc.maxY = Math.max(acc.maxY, py);
      }
    }
  }
}

function getSeatRadius(sector: PublicSeatSector) {
  return Math.max(
    asNumber((sector.mapData?.seatLayout as Record<string, unknown> | undefined)?.seatRadius) ?? 6,
    4,
  );
}

function getSeatPosition(seat: PublicSeat) {
  return {
    x: asNumber(seat.coordX) ?? 0,
    y: asNumber(seat.coordY) ?? 0,
  };
}

function normalizeSeatStatus(status?: string) {
  if (status === "HELD") return "RESERVED";
  if (status === "PURCHASED") return "SOLD";
  if (status === "UNAVAILABLE") return "BLOCKED";
  return status;
}

export default function BuyerSeatMapCanvas({
  seatMap,
  zoom,
  resetViewKey = 0,
  selectedSeatIds,
  selectedSectorId,
  getSeatColor,
  onZoomChange,
  onFitZoom,
  onSeatClick,
  onSectorClick,
}: BuyerSeatMapCanvasProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [viewportSize, setViewportSize] = useState({ width: 960, height: 620 });
  const [panOffset, setPanOffset] = useState({ x: 0, y: 0 });
  const wheelRafRef = useRef<number | null>(null);
  const pendingWheelRef = useRef<{
    nextScale: number;
    nextPanX: number;
    nextPanY: number;
  } | null>(null);
  const lastWheelZoomRef = useRef<number | null>(null);

  const onSeatClickRef = useRef(onSeatClick);
  onSeatClickRef.current = onSeatClick;
  const onSectorClickRef = useRef(onSectorClick);
  onSectorClickRef.current = onSectorClick;
  const onZoomChangeRef = useRef(onZoomChange);
  onZoomChangeRef.current = onZoomChange;

  const selectedSeatIdSet = useMemo(() => new Set(selectedSeatIds), [selectedSeatIds]);

  const decorations = useMemo(
    () =>
      Array.isArray(seatMap.mapConfig?.decorations)
        ? (seatMap.mapConfig.decorations as DecorationItem[])
        : [],
    [seatMap.mapConfig?.decorations],
  );

  const decorationSectors = useMemo<SharedSector[]>(
    () =>
      [...decorations]
        .sort((left, right) => {
          const isFrame = (item: DecorationItem) => {
            const shape = item.shape ?? {};
            if (shape.isVenueShape === true && shape.venueVariant === "venue_frame") {
              return true;
            }
            const shapeType = String(shape.shapeType ?? "");
            const cornerRadius = asNumber(shape.cornerRadius) ?? 0;
            const fill = String(shape.fill ?? "").toLowerCase();
            const label = (item.label ?? "").trim();
            return (
              shapeType === "roundedRect" &&
              cornerRadius >= 80 &&
              (fill === "#f6ebe4" || /^venue\s*shape$/i.test(label))
            );
          };
          return Number(isFrame(right)) - Number(isFrame(left));
        })
        .map((decoration) => ({
          id: decoration.id,
          name: decoration.label,
          colorCode: decoration.colorCode,
          mapData: decoration.shape,
        })),
    [decorations],
  );

  const sectorEntries = useMemo(
    () =>
      (seatMap.sectors ?? []).map((sector) => {
        const bounds = getSectorBounds(sector.mapData);
        const seatRadius = getSeatRadius(sector);
        const colorCode = sector.colorCode || "#16a34a";
        const isStandingSector = String(sector.sectorType || "").toUpperCase() === "STANDING";
        const hasSeats = (sector.seatsData?.length ?? 0) > 0;
        const sharedSector: SharedSector = {
          id: sector.id,
          name: sector.name,
          sectorType: sector.sectorType,
          colorCode,
          bounds,
          mapData: sector.mapData,
        };

        return {
          sector,
          sharedSector,
          seatRadius,
          colorCode,
          isStandingSector,
          hasSeats,
        };
      }),
    [seatMap.sectors],
  );

  const contentBounds = useMemo(() => {
    const acc = {
      minX: Infinity,
      minY: Infinity,
      maxX: -Infinity,
      maxY: -Infinity,
    };

    for (const decoration of decorations) {
      extendBoundsWithShape(acc, decoration.shape);
    }

    for (const sector of seatMap.sectors ?? []) {
      extendBoundsWithShape(acc, sector.mapData);
      const seatPad = Math.max(getSeatRadius(sector) * 2, 14);
      for (const seat of sector.seatsData ?? []) {
        const pos = getSeatPosition(seat);
        acc.minX = Math.min(acc.minX, pos.x - seatPad);
        acc.minY = Math.min(acc.minY, pos.y - seatPad);
        acc.maxX = Math.max(acc.maxX, pos.x + seatPad);
        acc.maxY = Math.max(acc.maxY, pos.y + seatPad);
      }
    }

    if (!Number.isFinite(acc.minX) || !Number.isFinite(acc.minY)) {
      const fallbackWidth = seatMap.backgroundWidth && seatMap.backgroundWidth > 0 ? seatMap.backgroundWidth : 800;
      const fallbackHeight = seatMap.backgroundHeight && seatMap.backgroundHeight > 0 ? seatMap.backgroundHeight : 600;
      return {
        minX: 0,
        minY: 0,
        width: fallbackWidth,
        height: fallbackHeight,
      };
    }

    return {
      minX: acc.minX,
      minY: acc.minY,
      width: Math.max(acc.maxX - acc.minX, 120),
      height: Math.max(acc.maxY - acc.minY, 120),
    };
  }, [decorations, seatMap.backgroundHeight, seatMap.backgroundWidth, seatMap.sectors]);

  useEffect(() => {
    const element = containerRef.current;
    if (!element) {
      return;
    }

    const observer = new ResizeObserver((entries) => {
      const nextWidth = entries[0]?.contentRect.width ?? element.clientWidth;
      const nextHeight = entries[0]?.contentRect.height ?? element.clientHeight;
      setViewportSize({
        width: Math.max(1, nextWidth),
        height: Math.max(1, nextHeight),
      });
    });

    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(() => () => {
    if (wheelRafRef.current !== null) {
      window.cancelAnimationFrame(wheelRafRef.current);
    }
  }, []);

  const horizontalPadding = 36;
  const topPadding = viewportSize.width <= 900 ? 20 : 84;
  const bottomPadding = 28;
  const availableWidth = Math.max(viewportSize.width - horizontalPadding * 2, 180);
  const availableHeight = Math.max(viewportSize.height - topPadding - bottomPadding, 180);

  const fitZoom = clamp(
    Math.min(availableWidth / contentBounds.width, availableHeight / contentBounds.height),
    MIN_ZOOM,
    MAX_ZOOM,
  );
  const effectiveZoom = zoom > 0 ? zoom : fitZoom;
  const prevEffectiveZoomRef = useRef(effectiveZoom);

  const getCenteredOrigin = useCallback(
    (scale: number) => ({
      x:
        horizontalPadding +
        (availableWidth - contentBounds.width * scale) / 2 -
        contentBounds.minX * scale,
      y:
        topPadding +
        (availableHeight - contentBounds.height * scale) / 2 -
        contentBounds.minY * scale,
    }),
    [availableHeight, availableWidth, contentBounds.height, contentBounds.minX, contentBounds.minY, contentBounds.width, topPadding],
  );

  const baseOrigin = useMemo(
    () => getCenteredOrigin(effectiveZoom),
    [effectiveZoom, getCenteredOrigin],
  );

  useEffect(() => {
    if (zoom <= 0) {
      prevEffectiveZoomRef.current = fitZoom;
      lastWheelZoomRef.current = null;
      setPanOffset({ x: 0, y: 0 });
      return;
    }

    const prevScale = prevEffectiveZoomRef.current;
    if (Math.abs(effectiveZoom - prevScale) > 1e-6) {
      if (lastWheelZoomRef.current !== null && Math.abs(lastWheelZoomRef.current - effectiveZoom) < 1e-6) {
        lastWheelZoomRef.current = null;
      } else if (prevScale > 0) {
        const ratio = effectiveZoom / prevScale;
        setPanOffset((prev) => ({
          x: prev.x * ratio,
          y: prev.y * ratio,
        }));
      }
      prevEffectiveZoomRef.current = effectiveZoom;
    }
  }, [effectiveZoom, fitZoom, zoom, resetViewKey]);

  useEffect(() => {
    onFitZoom?.(fitZoom);
  }, [fitZoom, onFitZoom]);

  const stageTransform = useMemo(
    () => ({
      x: baseOrigin.x + panOffset.x,
      y: baseOrigin.y + panOffset.y,
      scaleX: effectiveZoom,
      scaleY: effectiveZoom,
    }),
    [baseOrigin.x, baseOrigin.y, effectiveZoom, panOffset.x, panOffset.y],
  );

  const handleWheel = (event: any) => {
    event.evt.preventDefault();
    const stage = event.target.getStage();
    const pointer = stage?.getPointerPosition();

    const baseScale = pendingWheelRef.current?.nextScale ?? stage?.scaleX() ?? effectiveZoom;
    const baseScaleOrigin = getCenteredOrigin(baseScale);
    const currentWorldX = pendingWheelRef.current
      ? baseScaleOrigin.x + pendingWheelRef.current.nextPanX
      : (stage?.x() ?? baseOrigin.x + panOffset.x);
    const currentWorldY = pendingWheelRef.current
      ? baseScaleOrigin.y + pendingWheelRef.current.nextPanY
      : (stage?.y() ?? baseOrigin.y + panOffset.y);

    const direction = event.evt.deltaY > 0 ? -1 : 1;
    const nextScale = clamp(
      direction > 0 ? baseScale * WHEEL_ZOOM_STEP : baseScale / WHEEL_ZOOM_STEP,
      MIN_ZOOM,
      MAX_ZOOM,
    );

    if (Math.abs(nextScale - baseScale) < 1e-6) {
      return;
    }

    const nextBase = getCenteredOrigin(nextScale);
    let nextPanX = panOffset.x;
    let nextPanY = panOffset.y;

    if (pointer) {
      const worldPointX = (pointer.x - currentWorldX) / baseScale;
      const worldPointY = (pointer.y - currentWorldY) / baseScale;
      nextPanX = pointer.x - worldPointX * nextScale - nextBase.x;
      nextPanY = pointer.y - worldPointY * nextScale - nextBase.y;
    }

    pendingWheelRef.current = { nextScale, nextPanX, nextPanY };

    if (wheelRafRef.current === null) {
      wheelRafRef.current = window.requestAnimationFrame(() => {
        wheelRafRef.current = null;
        const pending = pendingWheelRef.current;
        pendingWheelRef.current = null;
        if (!pending) return;
        lastWheelZoomRef.current = pending.nextScale;
        setPanOffset({ x: pending.nextPanX, y: pending.nextPanY });
        onZoomChangeRef.current(pending.nextScale);
      });
    }
  };

  const seatLookup = useMemo(() => {
    const map = new Map<string, { sector: PublicSeatSector; seat: PublicSeat }>();
    for (const sector of seatMap.sectors ?? []) {
      for (const seat of sector.seatsData ?? []) {
        map.set(seat.id, { sector, seat });
      }
    }
    return map;
  }, [seatMap.sectors]);

  const seatColorById = useMemo(() => {
    const map = new Map<string, string>();
    for (const { sector, colorCode } of sectorEntries) {
      for (const seat of sector.seatsData ?? []) {
        map.set(seat.id, getSeatColor?.(sector, seat) ?? seat.colorCode ?? colorCode);
      }
    }
    return map;
  }, [sectorEntries, getSeatColor]);

  const handleSeatPointerActivate = useCallback(
    (event: any) => {
      const seatNode = event.target?.findAncestor?.(
        (node: any) => node?.name?.() === "seat-node",
        true,
      );
      const seatId = seatNode?.id?.();
      if (!seatId) return;
      const match = seatLookup.get(seatId);
      if (!match) return;
      event.cancelBubble = true;
      onSeatClickRef.current(match.sector, match.seat);
    },
    [seatLookup],
  );

  const handleSeatLayerMouseMove = useCallback((event: any) => {
    const container = event.target?.getStage?.()?.container?.();
    if (!container) return;
    const seatNode = event.target?.findAncestor?.(
      (node: any) => node?.name?.() === "seat-node",
      true,
    );
    if (seatNode) {
      container.style.cursor = "pointer";
      return;
    }
    if (container.style.cursor === "pointer") {
      container.style.cursor = "";
    }
  }, []);

  const handleSeatLayerMouseLeave = useCallback((event: any) => {
    const container = event.target?.getStage?.()?.container?.();
    if (container) {
      container.style.cursor = "";
    }
  }, []);

  return (
    <div
      ref={containerRef}
      className="buyer-seat-map-canvas"
    >
      <Stage
        width={viewportSize.width}
        height={viewportSize.height}
        x={stageTransform.x}
        y={stageTransform.y}
        scaleX={stageTransform.scaleX}
        scaleY={stageTransform.scaleY}
        draggable
        onWheel={handleWheel}
        onDragStart={(event) => {
          if (event.target !== event.target.getStage()) {
            return;
          }
          const container = event.target.getStage()?.container();
          if (container) {
            container.style.cursor = "grabbing";
          }
        }}
        onDragEnd={(event) => {
          if (event.target !== event.target.getStage()) {
            return;
          }
          const stage = event.target.getStage();
          const container = stage?.container();
          if (container) {
            container.style.cursor = "";
          }
          if (!stage) {
            return;
          }
          setPanOffset({
            x: stage.x() - baseOrigin.x,
            y: stage.y() - baseOrigin.y,
          });
        }}
      >
        <Layer listening={false}>
          <Group listening={false}>
            {decorationSectors.map((decorationSector) => (
              <SectorRenderer
                key={decorationSector.id}
                sector={decorationSector}
                listening={false}
                showDepth={false}
                showGuides={false}
              />
            ))}
          </Group>
        </Layer>
        <Layer
          onClick={handleSeatPointerActivate}
          onTap={handleSeatPointerActivate}
          onMouseMove={handleSeatLayerMouseMove}
          onMouseLeave={handleSeatLayerMouseLeave}
        >
          <Group>
            {sectorEntries.map(({ sector, sharedSector, seatRadius, colorCode, isStandingSector, hasSeats }) => {
              const canSelectSector = Boolean(onSectorClick) && isStandingSector;

              return (
                <Group key={sector.id}>
                  <SectorRenderer
                    sector={sharedSector}
                    selected={selectedSectorId === sector.id}
                    listening={canSelectSector}
                    onClick={
                      canSelectSector
                        ? (event) => {
                            event.cancelBubble = true;
                            onSectorClickRef.current?.(sector);
                          }
                        : undefined
                    }
                    onTap={
                      canSelectSector
                        ? (event) => {
                            event.cancelBubble = true;
                            onSectorClickRef.current?.(sector);
                          }
                        : undefined
                    }
                    onMouseEnter={
                      canSelectSector
                        ? (event) => {
                            const container = event.target.getStage()?.container();
                            if (container) {
                              container.style.cursor = "pointer";
                            }
                          }
                        : undefined
                    }
                    onMouseLeave={
                      canSelectSector
                        ? (event) => {
                            const container = event.target.getStage()?.container();
                            if (container) {
                              container.style.cursor = "";
                            }
                          }
                        : undefined
                    }
                    showDepth
                    showLabel={!hasSeats}
                    dimmedForSeats={hasSeats}
                  />

                  {(sector.seatsData ?? []).map((seat) => {
                    const isSelected = selectedSeatIdSet.has(seat.id);
                    const rawStatus = String(seat.inventoryStatus || "").toUpperCase();
                    const disabled =
                      seat.isActive === false ||
                      DISABLED_SEAT_STATUSES.has(rawStatus) ||
                      seat.inventoryStatus !== "AVAILABLE";
                    const x = asNumber(seat.coordX) ?? 0;
                    const y = asNumber(seat.coordY) ?? 0;

                    return (
                      <SeatRenderer
                        key={seat.id}
                        id={seat.id}
                        x={x}
                        y={y}
                        radius={seatRadius}
                        status={normalizeSeatStatus(seat.inventoryStatus)}
                        selected={isSelected}
                        disabled={disabled}
                        listening={!disabled}
                        color={seatColorById.get(seat.id) ?? colorCode}
                        variant="icon"
                        memoizeEventHandlers
                      />
                    );
                  })}
                </Group>
              );
            })}
          </Group>
        </Layer>
      </Stage>
    </div>
  );
}
