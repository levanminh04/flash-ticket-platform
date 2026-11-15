import { memo, useMemo } from "react";
import { Ellipse, Group, Line, Path, Rect, Text } from "react-konva";
import {
  asNumber,
  BoundsLike,
  createFanSectorPath,
  createRingSectorPath,
  createRoundedRectPath,
  getBoundsFromPoints,
  polarToCartesian,
} from "./sectorPathUtils";
import { resolveSectorStylePreset } from "./sectorStylePresets";

export type SharedSectorShapeType =
  | "rect"
  | "rectangle"
  | "roundedRect"
  | "ellipse"
  | "polygon"
  | "path"
  | "ringSection"
  | "fan"
  | "stage"
  | "foh";

export interface SharedSector {
  id: string;
  name?: string;
  label?: string;
  colorCode?: string;
  color?: string;
  shapeType?: string;
  sectorType?: string;
  totalCapacity?: number;
  seatCount?: number;
  bounds?: BoundsLike;
  points?: number[];
  mapData?: Record<string, unknown>;
}

interface SectorRendererProps {
  sector: SharedSector;
  selected?: boolean;
  hovered?: boolean;
  draggable?: boolean;
  pointsAreRelative?: boolean;
  showDepth?: boolean;
  showLabel?: boolean;
  showGuides?: boolean;
  dimmedForSeats?: boolean;
  listening?: boolean;
  nodeRef?: (node: any) => void;
  onClick?: (event: any) => void;
  onTap?: (event: any) => void;
  onDragStart?: (event: any) => void;
  onDragEnd?: (event: any) => void;
  onTransformEnd?: (event: any) => void;
  onMouseEnter?: (event: any) => void;
  onMouseLeave?: (event: any) => void;
}

function getMapDataObject(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function parseHexRgb(hex: string): [number, number, number] | null {
  const clean = hex.trim().replace(/^#/, "");
  if (clean.length === 3) {
    const r = parseInt(clean[0] + clean[0], 16);
    const g = parseInt(clean[1] + clean[1], 16);
    const b = parseInt(clean[2] + clean[2], 16);
    return Number.isNaN(r) || Number.isNaN(g) || Number.isNaN(b) ? null : [r, g, b];
  }
  if (clean.length >= 6) {
    const r = parseInt(clean.slice(0, 2), 16);
    const g = parseInt(clean.slice(2, 4), 16);
    const b = parseInt(clean.slice(4, 6), 16);
    return Number.isNaN(r) || Number.isNaN(g) || Number.isNaN(b) ? null : [r, g, b];
  }
  return null;
}

function shadeColor(color: string, factor: number, alpha = 1): string {
  const rgb = parseHexRgb(color);
  if (!rgb) {
    return color;
  }
  const clampChannel = (v: number) => Math.max(0, Math.min(255, Math.round(v)));
  const r = clampChannel(rgb[0] * factor);
  const g = clampChannel(rgb[1] * factor);
  const b = clampChannel(rgb[2] * factor);
  return alpha < 1 ? `rgba(${r}, ${g}, ${b}, ${alpha})` : `rgb(${r}, ${g}, ${b})`;
}

function isLightFill(color: string): boolean {
  const rgb = parseHexRgb(color);
  if (!rgb) {
    return false;
  }
  const luminance = (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255;
  return luminance > 0.68;
}

function getBounds(sector: SharedSector): BoundsLike {
  const mapData = getMapDataObject(sector.mapData);
  const rawBounds = getMapDataObject(mapData.bounds);
  const x = asNumber(sector.bounds?.x ?? rawBounds.x ?? mapData.x) ?? 0;
  const y = asNumber(sector.bounds?.y ?? rawBounds.y ?? mapData.y) ?? 0;
  const width = Math.max(asNumber(sector.bounds?.width ?? rawBounds.width ?? mapData.width) ?? 180, 1);
  const height = Math.max(asNumber(sector.bounds?.height ?? rawBounds.height ?? mapData.height) ?? 120, 1);

  return { x, y, width, height };
}

function getPoints(sector: SharedSector, bounds: BoundsLike, pointsAreRelative?: boolean) {
  const mapData = getMapDataObject(sector.mapData);
  const rawPoints = Array.isArray(sector.points) && sector.points.length ? sector.points : mapData.points;

  if (!Array.isArray(rawPoints)) {
    return [];
  }

  const points = rawPoints
    .flatMap((point) => {
      if (typeof point === "number") {
        return [point];
      }
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
      return [];
    })
    .filter((point): point is number => typeof point === "number" && Number.isFinite(point));

  if (!pointsAreRelative) {
    return points;
  }

  return points.map((point, index) => (index % 2 === 0 ? point + bounds.x : point + bounds.y));
}

function normalizeShapeType(sector: SharedSector): SharedSectorShapeType {
  const mapData = getMapDataObject(sector.mapData);
  const raw = String(mapData.shapeType ?? sector.shapeType ?? "rect");

  if (raw === "rectangle") {
    return "rect";
  }
  if (raw === "circle") {
    return "ellipse";
  }
  if (
    raw === "rect" ||
    raw === "roundedRect" ||
    raw === "ellipse" ||
    raw === "polygon" ||
    raw === "path" ||
    raw === "ringSection" ||
    raw === "fan" ||
    raw === "stage" ||
    raw === "foh"
  ) {
    return raw;
  }

  return "rect";
}

function computePolygonCentroid(points: number[], fallbackBounds: BoundsLike): { x: number; y: number } {
  if (points.length < 6) {
    return {
      x: fallbackBounds.x + fallbackBounds.width / 2,
      y: fallbackBounds.y + fallbackBounds.height / 2,
    };
  }

  let signedArea = 0;
  let cx = 0;
  let cy = 0;
  const count = Math.floor(points.length / 2);

  for (let i = 0; i < count; i += 1) {
    const x0 = points[i * 2];
    const y0 = points[i * 2 + 1];
    const nextIndex = (i + 1) % count;
    const x1 = points[nextIndex * 2];
    const y1 = points[nextIndex * 2 + 1];
    const cross = x0 * y1 - x1 * y0;
    signedArea += cross;
    cx += (x0 + x1) * cross;
    cy += (y0 + y1) * cross;
  }

  if (Math.abs(signedArea) < 1e-5) {
    return {
      x: fallbackBounds.x + fallbackBounds.width / 2,
      y: fallbackBounds.y + fallbackBounds.height / 2,
    };
  }

  const areaFactor = 1 / (3 * signedArea);
  return {
    x: cx * areaFactor,
    y: cy * areaFactor,
  };
}

function computeReadableArcRotation(midAngle: number): number {
  let angle = ((midAngle % 360) + 360) % 360;
  if (angle > 180) {
    angle -= 360;
  }
  if (Math.abs(angle) < 14 || Math.abs(Math.abs(angle) - 180) < 14) {
    return 0;
  }
  if (angle > 95) {
    return angle - 180;
  }
  if (angle < -95) {
    return angle + 180;
  }
  if (Math.abs(angle) >= 72 && Math.abs(angle) <= 108) {
    return angle > 0 ? -90 : 90;
  }
  return angle * 0.55;
}

function getLabelConfig(
  sector: SharedSector,
  shapeType: SharedSectorShapeType,
  bounds: BoundsLike,
  points: number[],
) {
  const mapData = getMapDataObject(sector.mapData);
  const labelPosition = getMapDataObject(mapData.labelPosition);
  const explicitRotation = asNumber(mapData.labelRotation);

  if (shapeType === "ringSection" || shapeType === "fan") {
    const cx = asNumber(mapData.cx) ?? bounds.x + bounds.width / 2;
    const cy =
      asNumber(mapData.cy) ??
      (shapeType === "fan" ? bounds.y + bounds.height : bounds.y + bounds.height / 2);
    const outerRadius = Math.max(
      asNumber(mapData.outerRadius) ?? Math.max(bounds.width, bounds.height) / 2,
      1,
    );
    const innerRadius = Math.max(
      asNumber(mapData.innerRadius) ?? (shapeType === "ringSection" ? outerRadius * 0.72 : outerRadius * 0.25),
      0,
    );
    const startAngle = asNumber(mapData.startAngle) ?? -60;
    const endAngle = asNumber(mapData.endAngle) ?? 60;
    const midAngle = (startAngle + endAngle) / 2;
    const midRadius = (innerRadius + outerRadius) / 2;
    const arcCenter = polarToCartesian(cx, cy, midRadius, midAngle);
    const arcSpanRad = (Math.abs(endAngle - startAngle) * Math.PI) / 180;
    const approxArcLength = Math.max(midRadius * arcSpanRad, 56);
    const radialThickness = Math.max(outerRadius - innerRadius, 36);

    return {
      x: asNumber(labelPosition.x) ?? arcCenter.x,
      y: asNumber(labelPosition.y) ?? arcCenter.y,
      width: Math.max(asNumber(mapData.labelWidth) ?? Math.min(approxArcLength * 0.88, 180), 54),
      maxHeight: radialThickness,
      rotation: explicitRotation ?? computeReadableArcRotation(midAngle),
      align:
        mapData.labelAlign === "left" || mapData.labelAlign === "right"
          ? mapData.labelAlign
          : ("center" as const),
    };
  }

  if (shapeType === "polygon" && points.length >= 6) {
    const centroid = computePolygonCentroid(points, bounds);
    const defaultPolyWidth =
      mapData.isVenueShape === true ? bounds.width * 0.84 : Math.min(bounds.width * 0.84, 200);
    return {
      x: asNumber(labelPosition.x) ?? centroid.x,
      y: asNumber(labelPosition.y) ?? centroid.y,
      width: Math.max(asNumber(mapData.labelWidth) ?? defaultPolyWidth, 48),
      maxHeight: bounds.height,
      rotation: explicitRotation ?? 0,
      align:
        mapData.labelAlign === "left" || mapData.labelAlign === "right"
          ? mapData.labelAlign
          : ("center" as const),
    };
  }

  const x = asNumber(labelPosition.x) ?? bounds.x + bounds.width / 2;
  const y = asNumber(labelPosition.y) ?? bounds.y + bounds.height / 2;
  const labelText = (sector.label ?? sector.name ?? "").trim();
  const isStageLike =
    shapeType === "stage" ||
    mapData.stylePreset === "stage" ||
    String(mapData.venueVariant ?? "").startsWith("stage_") ||
    (mapData.decorative === true &&
      (/sân\s*khấu|stage/i.test(labelText) ||
        (/^venue\s*shape$/i.test(labelText) && String(mapData.fill ?? "").toLowerCase() === "#1e293b")));
  const defaultBoxWidth =
    mapData.isVenueShape === true || isStageLike
      ? bounds.width * 0.9
      : Math.min(bounds.width * 0.9, 220);

  return {
    x,
    y,
    width: Math.max(asNumber(mapData.labelWidth) ?? defaultBoxWidth, 44),
    maxHeight: bounds.height,
    rotation: explicitRotation ?? 0,
    align:
      mapData.labelAlign === "left" || mapData.labelAlign === "right"
        ? mapData.labelAlign
        : ("center" as const),
  };
}

function getPathData(sector: SharedSector, shapeType: SharedSectorShapeType, bounds: BoundsLike, points: number[]) {
  const mapData = getMapDataObject(sector.mapData);
  const pathData = typeof mapData.pathData === "string" ? mapData.pathData : undefined;

  if (shapeType === "path" && pathData) {
    return pathData;
  }

  if (shapeType === "ringSection") {
    const cx = asNumber(mapData.cx) ?? bounds.x + bounds.width / 2;
    const cy = asNumber(mapData.cy) ?? bounds.y + bounds.height / 2;
    const outerRadius = Math.max(asNumber(mapData.outerRadius) ?? Math.max(bounds.width, bounds.height) / 2, 1);
    const innerRadius = Math.max(asNumber(mapData.innerRadius) ?? outerRadius * 0.72, 0);
    const startAngle = asNumber(mapData.startAngle) ?? -70;
    const endAngle = asNumber(mapData.endAngle) ?? 70;

    return createRingSectorPath({ cx, cy, innerRadius, outerRadius, startAngle, endAngle });
  }

  if (shapeType === "fan") {
    const cx = asNumber(mapData.cx) ?? bounds.x + bounds.width / 2;
    const cy = asNumber(mapData.cy) ?? bounds.y + bounds.height;
    const outerRadius = Math.max(asNumber(mapData.outerRadius) ?? Math.max(bounds.width, bounds.height), 1);
    const innerRadius = Math.max(asNumber(mapData.innerRadius) ?? 0, 0);
    const startAngle = asNumber(mapData.startAngle) ?? -45;
    const endAngle = asNumber(mapData.endAngle) ?? 45;

    return createFanSectorPath({ cx, cy, innerRadius, outerRadius, startAngle, endAngle });
  }

  if (shapeType === "stage" || shapeType === "foh" || shapeType === "roundedRect") {
    const venueVariant = typeof mapData.venueVariant === "string" ? mapData.venueVariant : undefined;
    if (venueVariant === "stage_t") {
      const { x, y, width, height } = bounds;
      const topHeight = Math.max(24, height * 0.42);
      const stemWidth = Math.max(28, Math.min(width * 0.34, width - 24));
      const stemLeft = x + (width - stemWidth) / 2;
      const stemRight = stemLeft + stemWidth;
      return `M ${x} ${y} L ${x + width} ${y} L ${x + width} ${y + topHeight} L ${stemRight} ${y + topHeight} L ${stemRight} ${y + height} L ${stemLeft} ${y + height} L ${stemLeft} ${y + topHeight} L ${x} ${y + topHeight} Z`;
    }
    if (venueVariant === "stage_arc") {
      const { x, y, width, height } = bounds;
      const rx = Math.max(width / 2, 1);
      const ry = Math.max(height, 1);
      return `M ${x} ${y + height} A ${rx} ${ry} 0 0 1 ${x + width} ${y + height} Z`;
    }
    if (venueVariant === "stage_trapezoid") {
      const { x, y, width, height } = bounds;
      const inset = Math.min(width * 0.16, Math.max(16, width * 0.14));
      return `M ${x + inset} ${y} L ${x + width - inset} ${y} L ${x + width} ${y + height} L ${x} ${y + height} Z`;
    }
    if (pathData) {
      return pathData;
    }
    const cornerRadius = asNumber(mapData.cornerRadius) ?? (shapeType === "foh" ? 8 : 14);
    return createRoundedRectPath(bounds, cornerRadius);
  }

  if (shapeType === "polygon" && points.length >= 6) {
    return points.reduce((path, point, index) => {
      if (index % 2 !== 0) {
        return path;
      }
      const command = index === 0 ? "M" : "L";
      return `${path} ${command} ${point} ${points[index + 1]}`;
    }, "").trim() + " Z";
  }

  return undefined;
}

function getSectorSubtitle(sector: SharedSector, shapeType: SharedSectorShapeType): string | null {
  const mapData = getMapDataObject(sector.mapData);
  if (mapData.hideSubtitle === true || shapeType === "stage" || shapeType === "foh") {
    return null;
  }
  if (typeof mapData.subtitle === "string" && mapData.subtitle.trim()) {
    return mapData.subtitle.trim();
  }
  return null;
}

function SectorRendererBase({
  sector,
  selected = false,
  hovered = false,
  draggable = false,
  pointsAreRelative = false,
  showDepth = true,
  showLabel = true,
  showGuides = true,
  dimmedForSeats = false,
  listening = true,
  nodeRef,
  onClick,
  onTap,
  onDragStart,
  onDragEnd,
  onTransformEnd,
  onMouseEnter,
  onMouseLeave,
}: SectorRendererProps) {
  const bounds = getBounds(sector);
  const shapeType = normalizeShapeType(sector);
  const points = getPoints(sector, bounds, pointsAreRelative);
  const mapData = getMapDataObject(sector.mapData);
  const style = resolveSectorStylePreset(mapData.stylePreset ?? (shapeType === "stage" ? "stage" : shapeType === "foh" ? "foh" : undefined));
  const pathData = useMemo(
    () => getPathData(sector, shapeType, bounds, points),
    [bounds.height, bounds.width, bounds.x, bounds.y, points, sector, shapeType],
  );
  const labelConfig = useMemo(
    () => getLabelConfig(sector, shapeType, bounds, points),
    [bounds, points, sector, shapeType],
  );

  const baseColor =
    (typeof mapData.fill === "string" && mapData.fill.trim() ? mapData.fill.trim() : undefined) ??
    sector.colorCode ??
    sector.color ??
    style.fill;

  const rawLabelText = (sector.label ?? sector.name ?? "").trim();
  const cornerRadiusVal = asNumber(mapData.cornerRadius) ?? 0;
  const isDecorative = shapeType === "stage" || shapeType === "foh" || mapData.decorative === true;
  const isStageVariant =
    shapeType === "stage" ||
    mapData.stylePreset === "stage" ||
    String(mapData.venueVariant ?? "").startsWith("stage_") ||
    String(mapData.fill ?? "").toLowerCase() === "#1e293b";
  const isLegacyVenueFrame =
    isDecorative &&
    !isStageVariant &&
    shapeType === "roundedRect" &&
    cornerRadiusVal >= 80 &&
    (/^venue\s*shape$/i.test(rawLabelText) ||
      String(mapData.fill ?? "").toLowerCase() === "#f6ebe4" ||
      String(sector.colorCode ?? sector.color ?? "").toLowerCase() === "#e5b89b");
  const isVenueFrame =
    (mapData.isVenueShape === true && mapData.venueVariant === "venue_frame") ||
    isLegacyVenueFrame;
  const seatLayoutObj = getMapDataObject(mapData.seatLayout);
  const isRoundTable =
    mapData.presetType === "round_table" || seatLayoutObj.mode === "table";
  const outlineMode = String(mapData.outlineMode ?? seatLayoutObj.outlineMode ?? "auto");
  const isMinimalOutline = dimmedForSeats && outlineMode === "none" && !isDecorative;
  const transformedOutlinePoints = Array.isArray(mapData.transformedOutlinePoints)
    ? mapData.transformedOutlinePoints
        .map((val) => asNumber(val))
        .filter((val): val is number => val !== null)
    : [];
  const hasTransformedOutline = transformedOutlinePoints.length >= 6;

  const isSeatedGridPreset =
    (mapData.presetType === "seated_grid" ||
      (typeof seatLayoutObj.mode === "string" && seatLayoutObj.mode !== "table")) &&
    !isDecorative;
  const fillAlpha = isVenueFrame
    ? selected || hovered
      ? 0.5
      : 0.42
    : isSeatedGridPreset && dimmedForSeats
    ? selected || hovered
      ? 0.08
      : 0
    : isMinimalOutline
      ? selected || hovered
        ? 0.18
        : 0.04
      : dimmedForSeats && !isDecorative
        ? 0.34
        : isDecorative
          ? 0.96
          : 0.92;
  const fill = isVenueFrame
    ? shadeColor(baseColor, 1, fillAlpha)
    : shadeColor(baseColor, 1, fillAlpha);
  const depthFill = shadeColor(
    baseColor,
    0.48,
    isSeatedGridPreset || isMinimalOutline ? 0.02 : dimmedForSeats ? 0.35 : 0.95,
  );
  const stroke = selected
    ? "#1688f0"
    : hovered
      ? "#38bdf8"
      : isVenueFrame
        ? "#e5b89b"
      : isSeatedGridPreset && dimmedForSeats
        ? "transparent"
        : isMinimalOutline
          ? shadeColor(baseColor, 1.15, 0.22)
          : isDecorative
            ? style.stroke
            : shadeColor(baseColor, 1.25, 0.95);
  const strokeWidth = selected
    ? 2
    : hovered
      ? 1.8
      : isVenueFrame
        ? 2.2
      : isSeatedGridPreset && dimmedForSeats
        ? 1
        : isMinimalOutline
          ? 0.8
          : isDecorative
            ? 2
            : 1.5;

  const commonProps = {
    ref: nodeRef,
    name: "shape-node",
    draggable,
    listening,
    fill,
    stroke,
    strokeWidth,
    lineJoin: "round" as const,
    perfectDrawEnabled: false,
    shadowForStrokeEnabled: false,
    shadowColor: selected ? "#38bdf8" : style.shadowColor,
    shadowBlur: selected ? 14 : style.shadowBlur,
    shadowOpacity: selected ? 0.5 : style.shadowOpacity,
    shadowOffset: { x: style.shadowOffsetX, y: style.shadowOffsetY },
    opacity: hovered ? 0.98 : 1,
    onClick,
    onTap,
    onDragStart,
    onDragEnd,
    onTransformEnd,
    onMouseEnter,
    onMouseLeave,
  };

  const label =
    isVenueFrame && /^venue\s*shape$/i.test(rawLabelText)
      ? ""
      : isStageVariant && /^venue\s*shape$/i.test(rawLabelText)
        ? "SÂN KHẤU"
        : rawLabelText;
  const subtitle = getSectorSubtitle(sector, shapeType);
  const isVenueShape = mapData.isVenueShape === true || isVenueFrame;
  const isStageLike =
    isStageVariant ||
    (isDecorative && /sân\s*khấu|stage/i.test(label));
  const minDimension = Math.min(bounds.width, bounds.height, labelConfig.maxHeight ?? 120);
  const venueMinDimension = Math.min(bounds.width, bounds.height);
  const stageMaxForWidth = (labelConfig.width * 0.86) / Math.max(label.length * 0.82, 4);
  const adaptiveTitleSize =
    isStageLike
      ? Math.min(Math.max(venueMinDimension * 0.45, 22), 52, stageMaxForWidth)
      : shapeType === "foh"
        ? Math.min(Math.max(minDimension * 0.28, 10), 13)
        : isVenueShape
          ? Math.min(Math.max(venueMinDimension * 0.22, 18), 56)
          : Math.min(Math.max(minDimension * 0.18, 10.5), 15);
  const adaptiveSubtitleSize = Math.max(adaptiveTitleSize - 3, 8.5);
  const showSubtitleLine = Boolean(subtitle && minDimension >= 42 && !dimmedForSeats);
  const textPrimaryColor = !dimmedForSeats && isLightFill(baseColor) ? "#0f172a" : "#ffffff";
  const textSecondaryColor =
    !dimmedForSeats && isLightFill(baseColor) ? "rgba(15, 23, 42, 0.78)" : "rgba(248, 250, 252, 0.86)";
  const textShadowColor =
    !dimmedForSeats && isLightFill(baseColor) ? "rgba(255, 255, 255, 0.35)" : "rgba(2, 6, 23, 0.72)";
  const depthOffsetY = shapeType === "stage" ? 8 : 6;
  const tableRadius = Math.max(Math.min(bounds.width, bounds.height) / 2 - 26, 16);
  const shouldRenderLabel = (showLabel || isRoundTable) && Boolean(label);

  return (
    <Group>
      {/* Lớp đế 3D Bevel / Extrusion */}
      {showDepth && !isMinimalOutline && !isVenueFrame && !isSeatedGridPreset ? (
        isRoundTable ? (
          <Ellipse
            x={bounds.x + bounds.width / 2}
            y={bounds.y + bounds.height / 2 + 4}
            radiusX={tableRadius}
            radiusY={tableRadius}
            fill={shadeColor(baseColor, 0.42, 0.9)}
            listening={false}
          />
        ) : hasTransformedOutline ? (
          <Line
            points={transformedOutlinePoints}
            x={0}
            y={depthOffsetY}
            closed
            tension={0.08}
            fill={depthFill}
            stroke={shadeColor(baseColor, 0.32, 0.85)}
            strokeWidth={1}
            lineJoin="round"
            listening={false}
          />
        ) : shapeType === "ellipse" ? (
          <Ellipse
            x={bounds.x + bounds.width / 2}
            y={bounds.y + bounds.height / 2 + depthOffsetY}
            radiusX={bounds.width / 2}
            radiusY={bounds.height / 2}
            fill={depthFill}
            listening={false}
          />
        ) : pathData ? (
          <Path
            data={pathData}
            x={0}
            y={depthOffsetY}
            fill={depthFill}
            stroke={shadeColor(baseColor, 0.32, 0.85)}
            strokeWidth={1}
            lineJoin="round"
            listening={false}
          />
        ) : (
          <Rect
            x={bounds.x}
            y={bounds.y + depthOffsetY}
            width={bounds.width}
            height={bounds.height}
            cornerRadius={12}
            fill={depthFill}
            listening={false}
          />
        )
      ) : null}

      {/* Khối chính */}
      {isSeatedGridPreset && dimmedForSeats ? null : isRoundTable ? (
        <>
          <Ellipse
            {...commonProps}
            x={bounds.x + bounds.width / 2}
            y={bounds.y + bounds.height / 2}
            radiusX={bounds.width / 2}
            radiusY={bounds.height / 2}
            fill={shadeColor(baseColor, 0.3, selected || hovered ? 0.24 : 0.1)}
            stroke={selected ? "#ffffff" : shadeColor(baseColor, 1.2, 0.32)}
            strokeWidth={selected ? 2.2 : 1}
          />
          <Ellipse
            x={bounds.x + bounds.width / 2}
            y={bounds.y + bounds.height / 2}
            radiusX={tableRadius}
            radiusY={tableRadius}
            fill={shadeColor(baseColor, 0.85, 0.94)}
            stroke={shadeColor(baseColor, 1.35, 0.96)}
            strokeWidth={2}
            listening={false}
          />
        </>
      ) : hasTransformedOutline ? (
        <Line {...commonProps} points={transformedOutlinePoints} closed tension={0.08} />
      ) : shapeType === "ellipse" ? (
        <Ellipse
          {...commonProps}
          x={bounds.x + bounds.width / 2}
          y={bounds.y + bounds.height / 2}
          radiusX={bounds.width / 2}
          radiusY={bounds.height / 2}
        />
      ) : shapeType === "rect" && !pathData ? (
        <Rect {...commonProps} x={bounds.x} y={bounds.y} width={bounds.width} height={bounds.height} cornerRadius={12} />
      ) : shapeType === "polygon" && points.length >= 6 ? (
        <Line {...commonProps} points={points} closed />
      ) : pathData ? (
        <Path {...commonProps} data={pathData} />
      ) : (
        <Rect {...commonProps} x={bounds.x} y={bounds.y} width={bounds.width} height={bounds.height} cornerRadius={12} />
      )}

      {/* Đường tâm chữ thập của Venue Shape giúp căn đối xứng */}
      {isVenueFrame && showGuides ? (
        <>
          <Line
            points={[
              bounds.x,
              bounds.y + bounds.height / 2,
              bounds.x + bounds.width,
              bounds.y + bounds.height / 2,
            ]}
            stroke="rgba(218, 165, 134, 0.72)"
            strokeWidth={1}
            listening={false}
          />
          <Line
            points={[
              bounds.x + bounds.width / 2,
              bounds.y,
              bounds.x + bounds.width / 2,
              bounds.y + bounds.height,
            ]}
            stroke="rgba(218, 165, 134, 0.72)"
            strokeWidth={1}
            listening={false}
          />
        </>
      ) : null}

      {/* Nhãn khu vực 2 dòng căn đúng trọng tâm & xoay tiếp tuyến */}
      {shouldRenderLabel && label ? (
        <Group
          x={labelConfig.x}
          y={labelConfig.y}
          rotation={asNumber(seatLayoutObj.rotation) ?? labelConfig.rotation}
          listening={false}
        >
          <Text
            x={-labelConfig.width / 2}
            y={showSubtitleLine && !isRoundTable ? -(adaptiveTitleSize + 1) : -adaptiveTitleSize / 2}
            width={labelConfig.width}
            text={label.toUpperCase()}
            align={labelConfig.align}
            fill={textPrimaryColor}
            fontSize={isRoundTable ? Math.min(adaptiveTitleSize, 11.5) : adaptiveTitleSize}
            fontStyle="800"
            letterSpacing={isStageLike ? 2.5 : 0.4}
            listening={false}
            perfectDrawEnabled={false}
            shadowColor={textShadowColor}
            shadowBlur={4}
            shadowOpacity={0.85}
          />
          {showSubtitleLine && !isRoundTable && subtitle ? (
            <Text
              x={-labelConfig.width / 2}
              y={2}
              width={labelConfig.width}
              text={subtitle}
              align={labelConfig.align}
              fill={textSecondaryColor}
              fontSize={adaptiveSubtitleSize}
              fontStyle="700"
              letterSpacing={0.3}
              listening={false}
              perfectDrawEnabled={false}
              shadowColor={textShadowColor}
              shadowBlur={3}
              shadowOpacity={0.75}
            />
          ) : null}
        </Group>
      ) : null}
    </Group>
  );
}

export const SectorRenderer = memo(SectorRendererBase);

export function getSectorRendererBounds(sector: SharedSector, pointsAreRelative = false): BoundsLike {
  const bounds = getBounds(sector);
  const shapeType = normalizeShapeType(sector);
  const points = getPoints(sector, bounds, pointsAreRelative);
  if (shapeType === "polygon") {
    return getBoundsFromPoints(points) ?? bounds;
  }
  return bounds;
}
