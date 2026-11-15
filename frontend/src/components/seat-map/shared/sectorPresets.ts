import {
  SeatMapEditorBounds,
  SeatMapEditorDocument,
  SeatMapEditorShape,
} from "../editor/seatMapEditorTypes";
import { generateSeatsForShape } from "../editor/seatMapEditorUtils";
import { createClientId } from "./idUtils";
import { polarToCartesian } from "./sectorPathUtils";

const PRESET_COLORS = {
  stage: "#1e293b",
  foh: "#334155",
  fanzone: "#ec4899",
  vip: "#14b8a6",
  svip: "#2563eb",
  lounge: "#6366f1",
  cat1: "#f97316",
  cat2: "#eab308",
  cat3: "#a855f7",
  cat4: "#ef4444",
  cat5: "#22c55e",
};

function defaultSeatLayout(
  bounds: SeatMapEditorBounds,
  mode: "grid" | "arc" | "fan" = "grid",
  rows = 5,
  seatsPerRow = 10,
) {
  return {
    mode,
    rows,
    seatsPerRow,
    gapX: Math.max(16, Math.round(bounds.width / Math.max(seatsPerRow + 1, 4))),
    gapY: Math.max(16, Math.round(bounds.height / Math.max(rows + 1, 4))),
    paddingX: Math.max(10, Math.round(bounds.width * 0.08)),
    paddingY: Math.max(12, Math.round(bounds.height * 0.14)),
    offsetX: 0,
    offsetY: 0,
    seatRadius: 5,
    rowStartCharCode: "A".charCodeAt(0),
    seatStartNumber: 1,
  };
}

function createBaseShape(
  name: string,
  shapeType: SeatMapEditorShape["shapeType"],
  bounds: SeatMapEditorBounds,
  color: string,
  zIndex: number,
  mapData: Record<string, unknown>,
  options?: {
    sectorType?: "SEATED" | "STANDING";
    totalCapacity?: number;
    rows?: number;
    seatsPerRow?: number;
    autoGenerateSeats?: boolean;
  },
): SeatMapEditorShape {
  const isDecorative = shapeType === "stage" || shapeType === "foh" || mapData.decorative === true;
  const mode = shapeType === "ringSection" ? "arc" : shapeType === "fan" ? "fan" : "grid";
  const sectorType = isDecorative ? "STANDING" : (options?.sectorType ?? "SEATED");
  const rows = options?.rows ?? 5;
  const seatsPerRow = options?.seatsPerRow ?? 10;
  const baseShape: SeatMapEditorShape = {
    id: createClientId(),
    sectorId: createClientId(),
    name,
    sectorType,
    totalCapacity: isDecorative
      ? 0
      : sectorType === "STANDING"
        ? (options?.totalCapacity ?? 600)
        : undefined,
    shapeType,
    mapData: {
      ...mapData,
      shapeType,
      bounds,
    },
    color,
    bounds,
    points: [],
    label: name,
    seatCount: 0,
    seats: [],
    seatLayout: defaultSeatLayout(bounds, mode, rows, seatsPerRow),
    visible: true,
    locked: false,
    zIndex,
  };

  if (!isDecorative && sectorType === "SEATED" && options?.autoGenerateSeats !== false) {
    return generateSeatsForShape(baseShape, {
      mode,
      rows,
      seatsPerRow,
    });
  }

  return baseShape;
}

function createTemplateShape({
  name,
  code,
  shapeType,
  sectorType,
  totalCapacity,
  bounds,
  points = [],
  color,
  zIndex,
  rows = 5,
  seatsPerRow = 10,
  autoGenerateSeats = false,
  mapData,
}: {
  name: string;
  code?: string;
  shapeType: SeatMapEditorShape["shapeType"];
  sectorType: SeatMapEditorShape["sectorType"];
  totalCapacity?: number;
  bounds: SeatMapEditorBounds;
  points?: number[];
  color: string;
  zIndex: number;
  rows?: number;
  seatsPerRow?: number;
  autoGenerateSeats?: boolean;
  mapData: Record<string, unknown>;
}): SeatMapEditorShape {
  const mode = shapeType === "ringSection" ? "arc" : shapeType === "fan" ? "fan" : "grid";
  const baseShape: SeatMapEditorShape = {
    id: createClientId(),
    sectorId: createClientId(),
    name,
    code,
    sectorType,
    totalCapacity: sectorType === "STANDING" ? (totalCapacity ?? 500) : undefined,
    shapeType,
    mapData: {
      ...mapData,
      code,
      shapeType,
      bounds,
    },
    color,
    bounds,
    points,
    label: name,
    seatCount: 0,
    seats: [],
    seatLayout: defaultSeatLayout(bounds, mode, rows, seatsPerRow),
    visible: true,
    locked: false,
    zIndex,
  };

  if (sectorType === "SEATED" && autoGenerateSeats && mapData.decorative !== true) {
    return generateSeatsForShape(baseShape, {
      mode,
      rows,
      seatsPerRow,
    });
  }

  return baseShape;
}

function nextZIndex(document: SeatMapEditorDocument) {
  return document.shapes.length;
}

/**
 * Công cụ Chia Múi Khán Đài Vòng Cung Đồng Tâm (Parametric Bowl Slicer)
 * Tự động cắt một vành khán đài từ góc startAngle đến endAngle thành N phân khu đều nhau với khe hở gapDeg.
 */
export function buildParametricBowlRingSlices({
  cx,
  cy,
  innerRadius,
  outerRadius,
   slices,
  startZIndex = 0,
  autoGenerateSeats = true,
}: {
  cx: number;
  cy: number;
  innerRadius: number;
  outerRadius: number;
  slices: Array<{
    name: string;
    code: string;
    startAngle: number;
    endAngle: number;
    color: string;
    stylePreset?: string;
    sectorType?: "SEATED" | "STANDING";
    totalCapacity?: number;
    rows?: number;
    seatsPerRow?: number;
    subtitle?: string;
  }>;
  startZIndex?: number;
  autoGenerateSeats?: boolean;
}): SeatMapEditorShape[] {
  let zIndex = startZIndex;
  return slices.map((slice) => {
    const midAngle = (slice.startAngle + slice.endAngle) / 2;
    const midRadius = (innerRadius + outerRadius) / 2;
    const labelCenter = polarToCartesian(cx, cy, midRadius, midAngle);
    const sampleAngles = [slice.startAngle, midAngle, slice.endAngle];
    const samplePoints = sampleAngles.flatMap((deg) => [
      polarToCartesian(cx, cy, innerRadius, deg),
      polarToCartesian(cx, cy, outerRadius, deg),
    ]);
    const xs = samplePoints.map((p) => p.x);
    const ys = samplePoints.map((p) => p.y);
    const minX = Math.min(...xs);
    const minY = Math.min(...ys);
    const maxX = Math.max(...xs);
    const maxY = Math.max(...ys);
    const bounds: SeatMapEditorBounds = {
      x: Math.round(minX),
      y: Math.round(minY),
      width: Math.max(Math.round(maxX - minX), 48),
      height: Math.max(Math.round(maxY - minY), 48),
    };

    const rows = slice.rows ?? 5;
    const seatsPerRow = slice.seatsPerRow ?? 10;
    const sectorType = slice.sectorType ?? "SEATED";

    return createTemplateShape({
      name: slice.name,
      code: slice.code,
      shapeType: "ringSection",
      sectorType,
      totalCapacity: slice.totalCapacity,
      bounds,
      color: slice.color,
      zIndex: zIndex++,
      rows,
      seatsPerRow,
      autoGenerateSeats,
      mapData: {
        stylePreset: slice.stylePreset ?? "cat1",
        cx,
        cy,
        innerRadius,
        outerRadius,
        startAngle: slice.startAngle,
        endAngle: slice.endAngle,
        fill: slice.color,
        subtitle: slice.subtitle ?? (sectorType === "STANDING" ? "(ĐỨNG)" : "(NGỒI)"),
        labelPosition: {
          x: Math.round(labelCenter.x),
          y: Math.round(labelCenter.y),
        },
      },
    });
  });
}

/**
 * Mẫu 1: Sân vận động Quốc gia (Stadium Bowl - chuẩn Ảnh 3, 4, 5: Mỹ Tâm / Blackpink SVĐ Mỹ Đình)
 * Gồm:
 * - Sân khấu Chữ T (Main Stage + Catwalk + Đảo B-Stage trung tâm)
 * - Mặt sân (Pitch): FANZONE 1/2/3/4 (Đứng), GA 1/2/3/4 (Đứng), SVIP 1/2, SEASOUL LOUNGE 1/2, VVIP 1/2, VIP 3/4 + FOH
 * - Khán đài Vòng Trong (Inner Bowl): VIP 1, VIP 2, DELUXE 3, DELUXE 4, CAT 1A, CAT 1B
 * - Khán đài Vòng Ngoài (Outer Bowl): DELUXE 1, DELUXE 2, GA KHÁN ĐÀI 1, GA KHÁN ĐÀI 2
 */
export function createConcertOvalTemplate(document: SeatMapEditorDocument): SeatMapEditorShape[] {
  const width = Math.max(document.width || 1280, 1280);
  const height = Math.max(document.height || 860, 720);
  const cx = Math.round(width / 2);
  const cy = Math.round(height * 0.44);
  let zIndex = nextZIndex(document);

  // Bán kính 2 tầng khán đài vòng cung đồng tâm (Horseshoe Stadium Bowl)
  const innerBowlR1 = Math.round(Math.min(width, height) * 0.31);
  const innerBowlR2 = Math.round(Math.min(width, height) * 0.42);
  const outerBowlR1 = Math.round(Math.min(width, height) * 0.445);
  const outerBowlR2 = Math.round(Math.min(width, height) * 0.545);

  const bowlShapes = [
    ...buildParametricBowlRingSlices({
      cx,
      cy,
      innerRadius: innerBowlR1,
      outerRadius: innerBowlR2,
      startZIndex: zIndex,
      autoGenerateSeats: true,
      slices: [
        {
          name: "VIP 1",
          code: "VIP1",
          startAngle: -118,
          endAngle: -64,
          color: "#14b8a6",
          stylePreset: "vip",
          rows: 5,
          seatsPerRow: 12,
        },
        {
          name: "VIP 2",
          code: "VIP2",
          startAngle: 64,
          endAngle: 118,
          color: "#14b8a6",
          stylePreset: "vip",
          rows: 5,
          seatsPerRow: 12,
        },
        {
          name: "DELUXE 3",
          code: "DLX3",
          startAngle: -154,
          endAngle: -121,
          color: "#f97316",
          stylePreset: "cat1",
          rows: 5,
          seatsPerRow: 9,
        },
        {
          name: "DELUXE 4",
          code: "DLX4",
          startAngle: 121,
          endAngle: 154,
          color: "#f97316",
          stylePreset: "cat1",
          rows: 5,
          seatsPerRow: 9,
        },
        {
          name: "CAT 1A",
          code: "CAT1A",
          startAngle: -178,
          endAngle: -157,
          color: "#a855f7",
          stylePreset: "cat3",
          rows: 4,
          seatsPerRow: 8,
        },
        {
          name: "CAT 1B",
          code: "CAT1B",
          startAngle: 157,
          endAngle: 178,
          color: "#a855f7",
          stylePreset: "cat3",
          rows: 4,
          seatsPerRow: 8,
        },
      ],
    }),
    ...buildParametricBowlRingSlices({
      cx,
      cy,
      innerRadius: outerBowlR1,
      outerRadius: outerBowlR2,
      startZIndex: zIndex + 6,
      autoGenerateSeats: true,
      slices: [
        {
          name: "DELUXE 1",
          code: "DLX1",
          startAngle: -116,
          endAngle: -72,
          color: "#f97316",
          stylePreset: "cat1",
          rows: 5,
          seatsPerRow: 12,
        },
        {
          name: "DELUXE 2",
          code: "DLX2",
          startAngle: 72,
          endAngle: 116,
          color: "#f97316",
          stylePreset: "cat1",
          rows: 5,
          seatsPerRow: 12,
        },
        {
          name: "CAT 2A",
          code: "CAT2A",
          startAngle: -152,
          endAngle: -119,
          color: "#eab308",
          stylePreset: "cat2",
          rows: 5,
          seatsPerRow: 10,
        },
        {
          name: "CAT 2B",
          code: "CAT2B",
          startAngle: 119,
          endAngle: 152,
          color: "#eab308",
          stylePreset: "cat2",
          rows: 5,
          seatsPerRow: 10,
        },
      ],
    }),
  ];
  zIndex += bowlShapes.length;

  // Khối mặt sân (Floor / Pitch) đối xứng chuẩn quanh Sân khấu chữ T
  const stageTopY = Math.round(cy - innerBowlR1 * 0.82);
  const mainStageW = 240;
  const mainStageH = 44;
  const catwalkW = 34;
  const catwalkH = 118;

  // Sân khấu Chữ T (Polygon 8 đỉnh)
  const stageBounds: SeatMapEditorBounds = {
    x: cx - mainStageW / 2,
    y: stageTopY,
    width: mainStageW,
    height: mainStageH + catwalkH,
  };
  const stagePoints = [
    0, 0,
    mainStageW, 0,
    mainStageW, mainStageH,
    mainStageW / 2 + catwalkW / 2, mainStageH,
    mainStageW / 2 + catwalkW / 2, mainStageH + catwalkH,
    mainStageW / 2 - catwalkW / 2, mainStageH + catwalkH,
    mainStageW / 2 - catwalkW / 2, mainStageH,
    0, mainStageH,
  ];
  const stageShape = createTemplateShape({
    name: "STAGE",
    code: "STAGE",
    shapeType: "polygon",
    sectorType: "STANDING",
    totalCapacity: 0,
    bounds: stageBounds,
    points: stagePoints,
    color: "#1e293b",
    zIndex: zIndex + 20,
    mapData: {
      stylePreset: "stage",
      fill: "#1e293b",
      decorative: true,
      hideSubtitle: true,
      labelPosition: { x: cx, y: stageTopY + mainStageH / 2 },
      labelWidth: mainStageW * 0.8,
    },
  });

  const floorTopY = stageTopY + mainStageH + 8;
  const fanzoneW = 76;
  const fanzoneH = 104;
  const gaFloorW = 66;

  const rectSector = ({
    name,
    code,
    x,
    y,
    w,
    h,
    color,
    stylePreset,
    sectorType,
    totalCapacity,
    rows = 4,
    seatsPerRow = 8,
  }: {
    name: string;
    code: string;
    x: number;
    y: number;
    w: number;
    h: number;
    color: string;
    stylePreset: string;
    sectorType: "STANDING" | "SEATED";
    totalCapacity?: number;
    rows?: number;
    seatsPerRow?: number;
  }) => {
    const bounds = { x: Math.round(x), y: Math.round(y), width: Math.round(w), height: Math.round(h) };
    return createTemplateShape({
      name,
      code,
      shapeType: "roundedRect",
      sectorType,
      totalCapacity,
      bounds,
      color,
      zIndex: zIndex++,
      rows,
      seatsPerRow,
      autoGenerateSeats: sectorType === "SEATED",
      mapData: {
        stylePreset,
        cornerRadius: 8,
        fill: color,
        subtitle: sectorType === "STANDING" ? "(ĐỨNG)" : "(NGỒI)",
        labelPosition: { x: bounds.x + bounds.width / 2, y: bounds.y + bounds.height / 2 },
        labelWidth: bounds.width * 0.92,
      },
    });
  };

  const fanzone1 = rectSector({
    name: "FANZONE 1",
    code: "FZ1",
    x: cx - catwalkW / 2 - 6 - fanzoneW,
    y: floorTopY,
    w: fanzoneW,
    h: fanzoneH,
    color: "#ec4899",
    stylePreset: "fanzone",
    sectorType: "STANDING",
    totalCapacity: 800,
  });

  const fanzone2 = rectSector({
    name: "FANZONE 2",
    code: "FZ2",
    x: cx + catwalkW / 2 + 6,
    y: floorTopY,
    w: fanzoneW,
    h: fanzoneH,
    color: "#ec4899",
    stylePreset: "fanzone",
    sectorType: "STANDING",
    totalCapacity: 800,
  });

  const ga1 = rectSector({
    name: "GA 1",
    code: "GA1",
    x: cx - catwalkW / 2 - 6 - fanzoneW - 8 - gaFloorW,
    y: floorTopY,
    w: gaFloorW,
    h: fanzoneH,
    color: "#eab308",
    stylePreset: "cat2",
    sectorType: "STANDING",
    totalCapacity: 1000,
  });

  const ga2 = rectSector({
    name: "GA 2",
    code: "GA2",
    x: cx + catwalkW / 2 + 6 + fanzoneW + 8,
    y: floorTopY,
    w: gaFloorW,
    h: fanzoneH,
    color: "#eab308",
    stylePreset: "cat2",
    sectorType: "STANDING",
    totalCapacity: 1000,
  });

  const svipY = floorTopY + fanzoneH + 10;
  const svipW = 112;
  const svipH = 44;

  const svip1 = rectSector({
    name: "SVIP 1",
    code: "SVIP1",
    x: cx - 5 - svipW,
    y: svipY,
    w: svipW,
    h: svipH,
    color: "#2563eb",
    stylePreset: "svip",
    sectorType: "SEATED",
    rows: 4,
    seatsPerRow: 10,
  });

  const svip2 = rectSector({
    name: "SVIP 2",
    code: "SVIP2",
    x: cx + 5,
    y: svipY,
    w: svipW,
    h: svipH,
    color: "#2563eb",
    stylePreset: "svip",
    sectorType: "SEATED",
    rows: 4,
    seatsPerRow: 10,
  });

  const loungeY = svipY + svipH + 8;
  const loungeW = 92;
  const loungeH = 42;
  const vvipW = 54;

  const lounge1 = rectSector({
    name: "LOUNGE 1",
    code: "LNG1",
    x: cx - 5 - loungeW,
    y: loungeY,
    w: loungeW,
    h: loungeH,
    color: "#4f46e5",
    stylePreset: "lounge",
    sectorType: "SEATED",
    rows: 3,
    seatsPerRow: 8,
  });

  const lounge2 = rectSector({
    name: "LOUNGE 2",
    code: "LNG2",
    x: cx + 5,
    y: loungeY,
    w: loungeW,
    h: loungeH,
    color: "#4f46e5",
    stylePreset: "lounge",
    sectorType: "SEATED",
    rows: 3,
    seatsPerRow: 8,
  });

  const vvip1 = rectSector({
    name: "VVIP 1",
    code: "VVIP1",
    x: cx - 5 - loungeW - 8 - vvipW,
    y: loungeY,
    w: vvipW,
    h: loungeH,
    color: "#0ea5e9",
    stylePreset: "default",
    sectorType: "SEATED",
    rows: 3,
    seatsPerRow: 5,
  });

  const vvip2 = rectSector({
    name: "VVIP 2",
    code: "VVIP2",
    x: cx + 5 + loungeW + 8,
    y: loungeY,
    w: vvipW,
    h: loungeH,
    color: "#0ea5e9",
    stylePreset: "default",
    sectorType: "SEATED",
    rows: 3,
    seatsPerRow: 5,
  });

  const fohY = loungeY + loungeH + 8;
  const fohW = 56;
  const fohH = 34;
  const fohShape = createTemplateShape({
    name: "FOH",
    code: "FOH",
    shapeType: "foh",
    sectorType: "STANDING",
    totalCapacity: 0,
    bounds: { x: cx - fohW / 2, y: fohY, width: fohW, height: fohH },
    color: "#334155",
    zIndex: zIndex + 21,
    mapData: {
      stylePreset: "foh",
      cornerRadius: 6,
      fill: "#334155",
      decorative: true,
      hideSubtitle: true,
      labelPosition: { x: cx, y: fohY + fohH / 2 },
      labelWidth: fohW,
    },
  });

  return [
    ...bowlShapes,
    ga1,
    ga2,
    fanzone1,
    fanzone2,
    svip1,
    svip2,
    vvip1,
    lounge1,
    lounge2,
    vvip2,
    stageShape,
    fohShape,
  ];
}

/**
 * Mẫu 2: Concert Sân Khấu Chữ T & Đảo Trung Tâm (Festival / Arena - chuẩn Ảnh 2 "Em Xinh Say Hi")
 * Gồm:
 * - Sân khấu Chữ T + Đảo trung tâm, Khán đài cánh CAT 1A/2A & CAT 1B/2B
 * - FANZONE A & FANZONE B ôm sát đảo sân khấu
 * - GA 1A/1B, GA 2A/2B, SVIP A/B, VIP A/B, FOH và SKY LOUNGE
 */
export function createFestivalTStageTemplate(document: SeatMapEditorDocument): SeatMapEditorShape[] {
  const width = Math.max(document.width || 1280, 1280);
  const cx = Math.round(width / 2);
  const topY = 56;
  let zIndex = nextZIndex(document);

  // Sân khấu Chữ T + Đảo trung tâm (Polygon)
  const stageW = 340;
  const stageH = 46;
  const catwalkW = 42;
  const islandW = 136;
  const islandH = 54;
  const totalStageH = stageH + 40 + islandH;
  const stageBounds: SeatMapEditorBounds = {
    x: cx - stageW / 2,
    y: topY,
    width: stageW,
    height: totalStageH,
  };
  const stagePoints = [
    0, 0,
    stageW, 0,
    stageW, stageH,
    stageW / 2 + catwalkW / 2, stageH,
    stageW / 2 + catwalkW / 2, stageH + 40,
    stageW / 2 + islandW / 2, stageH + 40,
    stageW / 2 + islandW / 2, totalStageH,
    stageW / 2 - islandW / 2, totalStageH,
    stageW / 2 - islandW / 2, stageH + 40,
    stageW / 2 - catwalkW / 2, stageH + 40,
    stageW / 2 - catwalkW / 2, stageH,
    0, stageH,
  ];

  const stageShape = createTemplateShape({
    name: "STAGE",
    code: "STAGE",
    shapeType: "polygon",
    sectorType: "STANDING",
    totalCapacity: 0,
    bounds: stageBounds,
    points: stagePoints,
    color: "#1e293b",
    zIndex: zIndex + 30,
    mapData: {
      stylePreset: "stage",
      fill: "#1e293b",
      decorative: true,
      hideSubtitle: true,
      labelPosition: { x: cx, y: topY + stageH / 2 },
      labelWidth: 220,
    },
  });

  const addBlock = ({
    name,
    code,
    x,
    y,
    w,
    h,
    color,
    stylePreset,
    sectorType,
    totalCapacity,
    rows = 5,
    seatsPerRow = 10,
  }: {
    name: string;
    code: string;
    x: number;
    y: number;
    w: number;
    h: number;
    color: string;
    stylePreset: string;
    sectorType: "STANDING" | "SEATED";
    totalCapacity?: number;
    rows?: number;
    seatsPerRow?: number;
  }) => {
    const bounds = { x: Math.round(x), y: Math.round(y), width: Math.round(w), height: Math.round(h) };
    return createTemplateShape({
      name,
      code,
      shapeType: "roundedRect",
      sectorType,
      totalCapacity,
      bounds,
      color,
      zIndex: zIndex++,
      rows,
      seatsPerRow,
      autoGenerateSeats: sectorType === "SEATED",
      mapData: {
        stylePreset,
        cornerRadius: 8,
        fill: color,
        subtitle: sectorType === "STANDING" ? "(ĐỨNG)" : "(NGỒI)",
        labelPosition: { x: bounds.x + bounds.width / 2, y: bounds.y + bounds.height / 2 },
        labelWidth: bounds.width * 0.92,
      },
    });
  };

  const row1Y = topY + stageH + 12;
  const row1H = 82;
  const row2Y = row1Y + row1H + 12;
  const row2H = 82;

  const fanzoneW = 132;
  const ga1W = 92;
  const ga2W = 72;
  const wingCatW = 96;

  // Hàng 1 & Hàng 2 Khu Đứng (FANZONE, GA 1, GA 2) + Khán đài 2 cánh (CAT 1, CAT 2)
  const fanzoneA1 = addBlock({
    name: "FANZONE A",
    code: "FZA",
    x: cx - islandW / 2 - 10 - fanzoneW,
    y: row1Y,
    w: fanzoneW,
    h: row1H + row2H + 12,
    color: "#f43f5e",
    stylePreset: "fanzone",
    sectorType: "STANDING",
    totalCapacity: 1200,
  });

  const fanzoneB1 = addBlock({
    name: "FANZONE B",
    code: "FZB",
    x: cx + islandW / 2 + 10,
    y: row1Y,
    w: fanzoneW,
    h: row1H + row2H + 12,
    color: "#f43f5e",
    stylePreset: "fanzone",
    sectorType: "STANDING",
    totalCapacity: 1200,
  });

  const ga1ALeftX = cx - islandW / 2 - 10 - fanzoneW - 10 - ga1W;
  const ga1BRightX = cx + islandW / 2 + 10 + fanzoneW + 10;

  const ga1A = addBlock({
    name: "GA 1A",
    code: "GA1A",
    x: ga1ALeftX,
    y: row1Y,
    w: ga1W,
    h: row1H + row2H + 12,
    color: "#ec4899",
    stylePreset: "fanzone",
    sectorType: "STANDING",
    totalCapacity: 900,
  });

  const ga1B = addBlock({
    name: "GA 1B",
    code: "GA1B",
    x: ga1BRightX,
    y: row1Y,
    w: ga1W,
    h: row1H + row2H + 12,
    color: "#ec4899",
    stylePreset: "fanzone",
    sectorType: "STANDING",
    totalCapacity: 900,
  });

  const ga2ALeftX = ga1ALeftX - 10 - ga2W;
  const ga2BRightX = ga1BRightX + ga1W + 10;

  const ga2A = addBlock({
    name: "GA 2A",
    code: "GA2A",
    x: ga2ALeftX,
    y: row1Y,
    w: ga2W,
    h: row1H + row2H + 12,
    color: "#db2777",
    stylePreset: "fanzone",
    sectorType: "STANDING",
    totalCapacity: 700,
  });

  const ga2B = addBlock({
    name: "GA 2B",
    code: "GA2B",
    x: ga2BRightX,
    y: row1Y,
    w: ga2W,
    h: row1H + row2H + 12,
    color: "#db2777",
    stylePreset: "fanzone",
    sectorType: "STANDING",
    totalCapacity: 700,
  });

  // Cánh khán đài ngồi 2 bên (CAT 2A / CAT 1A bên trái & CAT 2B / CAT 1B bên phải)
  const wingLeftX = ga2ALeftX - 14 - wingCatW;
  const wingRightX = ga2BRightX + ga2W + 14;

  const cat2ATop = addBlock({
    name: "CAT 2A",
    code: "CAT2A",
    x: wingLeftX,
    y: row1Y - 12,
    w: wingCatW,
    h: 58,
    color: "#b45309",
    stylePreset: "cat1",
    sectorType: "SEATED",
    rows: 4,
    seatsPerRow: 8,
  });

  const cat1A = addBlock({
    name: "CAT 1A",
    code: "CAT1A",
    x: wingLeftX,
    y: row1Y + 54,
    w: wingCatW,
    h: 122,
    color: "#991b1b",
    stylePreset: "cat4",
    sectorType: "SEATED",
    rows: 7,
    seatsPerRow: 8,
  });

  const cat2BTop = addBlock({
    name: "CAT 2B",
    code: "CAT2B",
    x: wingRightX,
    y: row1Y - 12,
    w: wingCatW,
    h: 58,
    color: "#b45309",
    stylePreset: "cat1",
    sectorType: "SEATED",
    rows: 4,
    seatsPerRow: 8,
  });

  const cat1B = addBlock({
    name: "CAT 1B",
    code: "CAT1B",
    x: wingRightX,
    y: row1Y + 54,
    w: wingCatW,
    h: 122,
    color: "#991b1b",
    stylePreset: "cat4",
    sectorType: "SEATED",
    rows: 7,
    seatsPerRow: 8,
  });

  // Hàng VIP / SVIP phía dưới
  const row3Y = row2Y + row2H + 16;
  const row3H = 62;
  const svipW = 128;
  const vipW = 98;

  const svipA = addBlock({
    name: "SVIP A",
    code: "SVIPA",
    x: cx - 36 - svipW,
    y: row3Y,
    w: svipW,
    h: row3H,
    color: "#c084fc",
    stylePreset: "cat3",
    sectorType: "SEATED",
    rows: 4,
    seatsPerRow: 10,
  });

  const svipB = addBlock({
    name: "SVIP B",
    code: "SVIPB",
    x: cx + 36,
    y: row3Y,
    w: svipW,
    h: row3H,
    color: "#c084fc",
    stylePreset: "cat3",
    sectorType: "SEATED",
    rows: 4,
    seatsPerRow: 10,
  });

  const vipA = addBlock({
    name: "VIP A",
    code: "VIPA",
    x: cx - 36 - svipW - 8 - vipW,
    y: row3Y,
    w: vipW,
    h: row3H,
    color: "#d946ef",
    stylePreset: "cat3",
    sectorType: "SEATED",
    rows: 4,
    seatsPerRow: 8,
  });

  const vipB = addBlock({
    name: "VIP B",
    code: "VIPB",
    x: cx + 36 + svipW + 8,
    y: row3Y,
    w: vipW,
    h: row3H,
    color: "#d946ef",
    stylePreset: "cat3",
    sectorType: "SEATED",
    rows: 4,
    seatsPerRow: 8,
  });

  // FOH ở giữa & SKY LOUNGE ở dưới cùng
  const fohShape = createTemplateShape({
    name: "FOH",
    code: "FOH",
    shapeType: "foh",
    sectorType: "STANDING",
    totalCapacity: 0,
    bounds: { x: cx - 28, y: row3Y + 4, width: 56, height: 54 },
    color: "#334155",
    zIndex: zIndex + 31,
    mapData: {
      stylePreset: "foh",
      cornerRadius: 6,
      fill: "#334155",
      decorative: true,
      hideSubtitle: true,
      labelPosition: { x: cx, y: row3Y + 31 },
      labelWidth: 56,
    },
  });

  const skyLoungeY = row3Y + row3H + 14;
  const skyLoungeW = 310;
  const skyLoungeH = 56;
  const skyLounge = addBlock({
    name: "SKY LOUNGE",
    code: "SKYLNG",
    x: cx - skyLoungeW / 2,
    y: skyLoungeY,
    w: skyLoungeW,
    h: skyLoungeH,
    color: "#8b5cf6",
    stylePreset: "lounge",
    sectorType: "SEATED",
    rows: 3,
    seatsPerRow: 14,
  });

  return [
    cat2ATop,
    cat1A,
    cat2BTop,
    cat1B,
    ga2A,
    ga1A,
    fanzoneA1,
    fanzoneB1,
    ga1B,
    ga2B,
    vipA,
    svipA,
    svipB,
    vipB,
    skyLounge,
    stageShape,
    fohShape,
  ];
}

/**
 * Công cụ Chia múi Khán đài Vòng cung Tự động (Parametric 6-Slice Stadium Arc)
 */
export function createSlicedRingArcPreset(document: SeatMapEditorDocument): SeatMapEditorShape[] {
  const width = Math.max(document.width || 1280, 960);
  const height = Math.max(document.height || 760, 640);
  const cx = Math.round(width / 2);
  const cy = Math.round(height * 0.42);
  const innerRadius = Math.round(Math.min(width, height) * 0.34);
  const outerRadius = Math.round(Math.min(width, height) * 0.46);
  const palette = ["#14b8a6", "#f97316", "#eab308", "#eab308", "#f97316", "#14b8a6"];
  const totalSlices = 6;
  const startSweep = -150;
  const endSweep = 150;
  const gapDeg = 3.2;
  const stepDeg = (endSweep - startSweep - gapDeg * (totalSlices - 1)) / totalSlices;

  const slices = Array.from({ length: totalSlices }, (_, idx) => {
    const startAngle = Number((startSweep + idx * (stepDeg + gapDeg)).toFixed(1));
    const endAngle = Number((startAngle + stepDeg).toFixed(1));
    return {
      name: `KHÁN ĐÀI ${idx + 1}`,
      code: `SEC${idx + 1}`,
      startAngle,
      endAngle,
      color: palette[idx % palette.length],
      stylePreset: idx === 0 || idx === totalSlices - 1 ? "vip" : "cat1",
      rows: 5,
      seatsPerRow: 10,
    };
  });

  return buildParametricBowlRingSlices({
    cx,
    cy,
    innerRadius,
    outerRadius,
    slices,
    startZIndex: nextZIndex(document),
    autoGenerateSeats: true,
  });
}

export function createMainStagePreset(document: SeatMapEditorDocument): SeatMapEditorShape {
  const cx = Math.round((document.width || 1280) / 2);
  const stageW = 320;
  const stageH = 58;
  const bounds = {
    x: cx - stageW / 2,
    y: 52,
    width: stageW,
    height: stageH,
  };

  return createBaseShape("STAGE", "stage", bounds, PRESET_COLORS.stage, nextZIndex(document), {
    stylePreset: "stage",
    fill: PRESET_COLORS.stage,
    decorative: true,
    hideSubtitle: true,
    cornerRadius: 10,
    labelPosition: { x: cx, y: bounds.y + stageH / 2 },
    labelWidth: stageW * 0.85,
  });
}

export function createStagePreset(document: SeatMapEditorDocument): SeatMapEditorShape {
  const cx = Math.round((document.width || 1280) / 2);
  const mainW = 280;
  const mainH = 48;
  const catwalkW = 40;
  const catwalkH = 110;
  const bounds = {
    x: cx - mainW / 2,
    y: 52,
    width: mainW,
    height: mainH + catwalkH,
  };
  const points = [
    0, 0,
    mainW, 0,
    mainW, mainH,
    mainW / 2 + catwalkW / 2, mainH,
    mainW / 2 + catwalkW / 2, mainH + catwalkH,
    mainW / 2 - catwalkW / 2, mainH + catwalkH,
    mainW / 2 - catwalkW / 2, mainH,
    0, mainH,
  ];

  return {
    ...createBaseShape("STAGE", "polygon", bounds, PRESET_COLORS.stage, nextZIndex(document), {
      stylePreset: "stage",
      fill: PRESET_COLORS.stage,
      decorative: true,
      hideSubtitle: true,
      labelPosition: { x: cx, y: bounds.y + mainH / 2 },
      labelWidth: mainW * 0.8,
    }),
    points,
  };
}

export function createFohPreset(document: SeatMapEditorDocument): SeatMapEditorShape {
  const bounds = {
    x: Math.round(document.width / 2 - 42),
    y: Math.round(Math.min(document.height * 0.56, 460)),
    width: 84,
    height: 44,
  };

  return createBaseShape("FOH", "foh", bounds, PRESET_COLORS.foh, nextZIndex(document), {
    stylePreset: "foh",
    fill: PRESET_COLORS.foh,
    decorative: true,
    hideSubtitle: true,
    cornerRadius: 8,
    labelPosition: { x: bounds.x + bounds.width / 2, y: bounds.y + bounds.height / 2 },
    labelWidth: bounds.width,
  });
}

export function createVipLeftCurvedPreset(document: SeatMapEditorDocument): SeatMapEditorShape {
  const cx = Math.round((document.width || 1280) / 2);
  const cy = Math.round((document.height || 860) * 0.42);
  const outerRadius = Math.round(Math.min(document.width * 0.34, 390));
  const innerRadius = Math.round(outerRadius * 0.74);
  const startAngle = -118;
  const endAngle = -60;
  const midCenter = polarToCartesian(cx, cy, (innerRadius + outerRadius) / 2, (startAngle + endAngle) / 2);
  const bounds = {
    x: cx - outerRadius,
    y: Math.round(cy - outerRadius * 0.6),
    width: Math.round(outerRadius * 0.75),
    height: Math.round(outerRadius * 1.15),
  };

  return createBaseShape(
    "VIP CÁNH TRÁI",
    "ringSection",
    bounds,
    PRESET_COLORS.vip,
    nextZIndex(document),
    {
      stylePreset: "vip",
      fill: PRESET_COLORS.vip,
      cx,
      cy,
      innerRadius,
      outerRadius,
      startAngle,
      endAngle,
      labelPosition: { x: Math.round(midCenter.x), y: Math.round(midCenter.y) },
    },
    { sectorType: "SEATED", rows: 5, seatsPerRow: 12 },
  );
}

export function createVipRightCurvedPreset(document: SeatMapEditorDocument): SeatMapEditorShape {
  const cx = Math.round((document.width || 1280) / 2);
  const cy = Math.round((document.height || 860) * 0.42);
  const outerRadius = Math.round(Math.min(document.width * 0.34, 390));
  const innerRadius = Math.round(outerRadius * 0.74);
  const startAngle = 60;
  const endAngle = 118;
  const midCenter = polarToCartesian(cx, cy, (innerRadius + outerRadius) / 2, (startAngle + endAngle) / 2);
  const bounds = {
    x: Math.round(cx + outerRadius * 0.25),
    y: Math.round(cy - outerRadius * 0.6),
    width: Math.round(outerRadius * 0.75),
    height: Math.round(outerRadius * 1.15),
  };

  return createBaseShape(
    "VIP CÁNH PHẢI",
    "ringSection",
    bounds,
    PRESET_COLORS.vip,
    nextZIndex(document),
    {
      stylePreset: "vip",
      fill: PRESET_COLORS.vip,
      cx,
      cy,
      innerRadius,
      outerRadius,
      startAngle,
      endAngle,
      labelPosition: { x: Math.round(midCenter.x), y: Math.round(midCenter.y) },
    },
    { sectorType: "SEATED", rows: 5, seatsPerRow: 12 },
  );
}

export function createCatBottomRingPreset(document: SeatMapEditorDocument): SeatMapEditorShape {
  const cx = Math.round((document.width || 1280) / 2);
  const cy = Math.round((document.height || 860) * 0.36);
  const outerRadius = Math.round(Math.min(document.width * 0.38, 430));
  const innerRadius = Math.round(outerRadius * 0.76);
  const startAngle = 138;
  const endAngle = 222;
  const midCenter = polarToCartesian(cx, cy, (innerRadius + outerRadius) / 2, (startAngle + endAngle) / 2);
  const bounds = {
    x: Math.round(cx - outerRadius * 0.75),
    y: Math.round(cy + innerRadius * 0.55),
    width: Math.round(outerRadius * 1.5),
    height: Math.round(outerRadius - innerRadius * 0.55),
  };

  return createBaseShape(
    "KHÁN ĐÀI VÒNG CUNG",
    "ringSection",
    bounds,
    PRESET_COLORS.cat1,
    nextZIndex(document),
    {
      stylePreset: "cat1",
      fill: PRESET_COLORS.cat1,
      cx,
      cy,
      innerRadius,
      outerRadius,
      startAngle,
      endAngle,
      labelPosition: { x: Math.round(midCenter.x), y: Math.round(midCenter.y) },
    },
    { sectorType: "SEATED", rows: 5, seatsPerRow: 14 },
  );
}

export function createLeftSideRingPreset(document: SeatMapEditorDocument): SeatMapEditorShape {
  return createVipLeftCurvedPreset(document);
}

export function createRightSideRingPreset(document: SeatMapEditorDocument): SeatMapEditorShape {
  return createVipRightCurvedPreset(document);
}

export function createFanPreset(document: SeatMapEditorDocument): SeatMapEditorShape {
  const cx = Math.round((document.width || 1280) / 2);
  const cy = 200;
  const outerRadius = Math.round(Math.min(document.width * 0.28, 340));
  const innerRadius = 96;
  const startAngle = 142;
  const endAngle = 218;
  const midCenter = polarToCartesian(cx, cy, (innerRadius + outerRadius) / 2, (startAngle + endAngle) / 2);
  const bounds = {
    x: Math.round(cx - outerRadius * 0.72),
    y: Math.round(cy + innerRadius * 0.65),
    width: Math.round(outerRadius * 1.44),
    height: Math.round(outerRadius - innerRadius * 0.65),
  };

  return createBaseShape(
    "KHÁN ĐÀI RẺ QUẠT",
    "fan",
    bounds,
    PRESET_COLORS.cat2,
    nextZIndex(document),
    {
      stylePreset: "cat2",
      fill: PRESET_COLORS.cat2,
      cx,
      cy,
      innerRadius,
      outerRadius,
      startAngle,
      endAngle,
      labelPosition: { x: Math.round(midCenter.x), y: Math.round(midCenter.y) },
    },
    { sectorType: "SEATED", rows: 5, seatsPerRow: 12 },
  );
}

export function createSeatedBlockPreset(document: SeatMapEditorDocument): SeatMapEditorShape {
  const bounds = {
    x: 160 + (document.shapes.length % 4) * 38,
    y: 220 + (document.shapes.length % 3) * 34,
    width: 184,
    height: 104,
  };

  return createBaseShape(
    `KHÁN ĐÀI ${document.shapes.length + 1}`,
    "roundedRect",
    bounds,
    PRESET_COLORS.svip,
    nextZIndex(document),
    {
      stylePreset: "svip",
      fill: PRESET_COLORS.svip,
      cornerRadius: 10,
      labelPosition: { x: bounds.x + bounds.width / 2, y: bounds.y + bounds.height / 2 },
      labelWidth: bounds.width * 0.88,
    },
    { sectorType: "SEATED", rows: 5, seatsPerRow: 10 },
  );
}

export function createRoundedBlockPreset(document: SeatMapEditorDocument): SeatMapEditorShape {
  const bounds = {
    x: 120 + (document.shapes.length % 4) * 42,
    y: 180 + (document.shapes.length % 3) * 38,
    width: 176,
    height: 102,
  };

  return createBaseShape(
    `FANZONE ${document.shapes.length + 1}`,
    "roundedRect",
    bounds,
    PRESET_COLORS.fanzone,
    nextZIndex(document),
    {
      stylePreset: "fanzone",
      fill: PRESET_COLORS.fanzone,
      cornerRadius: 10,
      labelPosition: { x: bounds.x + bounds.width / 2, y: bounds.y + bounds.height / 2 },
      labelWidth: bounds.width * 0.88,
    },
    { sectorType: "STANDING", totalCapacity: 600, autoGenerateSeats: false },
  );
}

export function createRoundTablePreset(document: SeatMapEditorDocument): SeatMapEditorShape {
  const tableIndex =
    document.shapes.filter(
      (shape) =>
        shape.mapData?.presetType === "round_table" || shape.seatLayout?.mode === "table",
    ).length + 1;
  const size = 132;
  const bounds: SeatMapEditorBounds = {
    x: 240 + ((tableIndex - 1) % 4) * 152,
    y: 260 + (Math.floor((tableIndex - 1) / 4) % 3) * 152,
    width: size,
    height: size,
  };
  const name = `BÀN ${tableIndex}`;
  const baseShape: SeatMapEditorShape = {
    id: createClientId(),
    sectorId: createClientId(),
    name,
    sectorType: "SEATED",
    totalCapacity: 8,
    shapeType: "circle",
    mapData: {
      shapeType: "circle",
      presetType: "round_table",
      stylePreset: "lounge",
      fill: PRESET_COLORS.lounge,
      bounds,
      labelPosition: { x: bounds.x + bounds.width / 2, y: bounds.y + bounds.height / 2 },
      labelWidth: bounds.width * 0.72,
    },
    color: PRESET_COLORS.lounge,
    bounds,
    points: [],
    label: name,
    seatCount: 0,
    seats: [],
    seatLayout: {
      ...defaultSeatLayout(bounds, "grid", 1, 8),
      mode: "table",
      rows: 1,
      seatsPerRow: 8,
      seatRadius: 6,
      rowLabelPosition: "none",
      rowAlignment: "center",
      outlineMode: "auto",
    },
    visible: true,
    locked: false,
    zIndex: nextZIndex(document),
  };

  return generateSeatsForShape(baseShape, {
    mode: "table",
    rows: 1,
    seatsPerRow: 8,
    rowLabelPosition: "none",
  });
}


