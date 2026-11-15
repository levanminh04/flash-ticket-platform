export interface SectorStylePreset {
  fill: string;
  stroke: string;
  strokeWidth: number;
  labelColor: string;
  labelFontSize: number;
  labelFontWeight: "normal" | "bold";
  shadowColor: string;
  shadowBlur: number;
  shadowOpacity: number;
  shadowOffsetX: number;
  shadowOffsetY: number;
}

export type SectorStylePresetName =
  | "fanzone"
  | "vip"
  | "svip"
  | "lounge"
  | "cat1"
  | "cat2"
  | "cat3"
  | "cat4"
  | "cat5"
  | "stage"
  | "foh"
  | "led"
  | "default";

export const sectorStylePresets: Record<SectorStylePresetName, SectorStylePreset> = {
  fanzone: {
    fill: "#ec4899",
    stroke: "#f9a8d4",
    strokeWidth: 1.8,
    labelColor: "#ffffff",
    labelFontSize: 15,
    labelFontWeight: "bold",
    shadowColor: "#000000",
    shadowBlur: 12,
    shadowOpacity: 0.38,
    shadowOffsetX: 0,
    shadowOffsetY: 6,
  },
  vip: {
    fill: "#14b8a6",
    stroke: "#5eead4",
    strokeWidth: 1.8,
    labelColor: "#ffffff",
    labelFontSize: 15,
    labelFontWeight: "bold",
    shadowColor: "#000000",
    shadowBlur: 12,
    shadowOpacity: 0.36,
    shadowOffsetX: 0,
    shadowOffsetY: 6,
  },
  svip: {
    fill: "#2563eb",
    stroke: "#93c5fd",
    strokeWidth: 1.8,
    labelColor: "#ffffff",
    labelFontSize: 15,
    labelFontWeight: "bold",
    shadowColor: "#000000",
    shadowBlur: 12,
    shadowOpacity: 0.36,
    shadowOffsetX: 0,
    shadowOffsetY: 6,
  },
  lounge: {
    fill: "#8b5cf6",
    stroke: "#c4b5fd",
    strokeWidth: 1.8,
    labelColor: "#ffffff",
    labelFontSize: 15,
    labelFontWeight: "bold",
    shadowColor: "#000000",
    shadowBlur: 12,
    shadowOpacity: 0.36,
    shadowOffsetX: 0,
    shadowOffsetY: 6,
  },
  cat1: {
    fill: "#f97316",
    stroke: "#fdba74",
    strokeWidth: 1.6,
    labelColor: "#ffffff",
    labelFontSize: 14,
    labelFontWeight: "bold",
    shadowColor: "#000000",
    shadowBlur: 10,
    shadowOpacity: 0.34,
    shadowOffsetX: 0,
    shadowOffsetY: 5,
  },
  cat2: {
    fill: "#eab308",
    stroke: "#fde047",
    strokeWidth: 1.6,
    labelColor: "#0f172a",
    labelFontSize: 14,
    labelFontWeight: "bold",
    shadowColor: "#000000",
    shadowBlur: 10,
    shadowOpacity: 0.34,
    shadowOffsetX: 0,
    shadowOffsetY: 5,
  },
  cat3: {
    fill: "#a855f7",
    stroke: "#d8b4fe",
    strokeWidth: 1.6,
    labelColor: "#ffffff",
    labelFontSize: 14,
    labelFontWeight: "bold",
    shadowColor: "#000000",
    shadowBlur: 10,
    shadowOpacity: 0.34,
    shadowOffsetX: 0,
    shadowOffsetY: 5,
  },
  cat4: {
    fill: "#ef4444",
    stroke: "#fca5a5",
    strokeWidth: 1.6,
    labelColor: "#ffffff",
    labelFontSize: 13,
    labelFontWeight: "bold",
    shadowColor: "#000000",
    shadowBlur: 10,
    shadowOpacity: 0.32,
    shadowOffsetX: 0,
    shadowOffsetY: 5,
  },
  cat5: {
    fill: "#22c55e",
    stroke: "#86efac",
    strokeWidth: 1.6,
    labelColor: "#052e16",
    labelFontSize: 13,
    labelFontWeight: "bold",
    shadowColor: "#000000",
    shadowBlur: 10,
    shadowOpacity: 0.32,
    shadowOffsetX: 0,
    shadowOffsetY: 5,
  },
  stage: {
    fill: "#1e293b",
    stroke: "#cbd5e1",
    strokeWidth: 2.2,
    labelColor: "#f8fafc",
    labelFontSize: 20,
    labelFontWeight: "bold",
    shadowColor: "#000000",
    shadowBlur: 18,
    shadowOpacity: 0.55,
    shadowOffsetX: 0,
    shadowOffsetY: 8,
  },
  foh: {
    fill: "#334155",
    stroke: "#94a3b8",
    strokeWidth: 1.6,
    labelColor: "#e2e8f0",
    labelFontSize: 12,
    labelFontWeight: "bold",
    shadowColor: "#000000",
    shadowBlur: 8,
    shadowOpacity: 0.35,
    shadowOffsetX: 0,
    shadowOffsetY: 4,
  },
  led: {
    fill: "#0f172a",
    stroke: "#38bdf8",
    strokeWidth: 1.5,
    labelColor: "#7dd3fc",
    labelFontSize: 11,
    labelFontWeight: "bold",
    shadowColor: "#0284c7",
    shadowBlur: 10,
    shadowOpacity: 0.4,
    shadowOffsetX: 0,
    shadowOffsetY: 3,
  },
  default: {
    fill: "#0ea5e9",
    stroke: "#7dd3fc",
    strokeWidth: 1.6,
    labelColor: "#ffffff",
    labelFontSize: 13,
    labelFontWeight: "bold",
    shadowColor: "#000000",
    shadowBlur: 8,
    shadowOpacity: 0.3,
    shadowOffsetX: 0,
    shadowOffsetY: 5,
  },
};

export function resolveSectorStylePreset(value: unknown): SectorStylePreset {
  return typeof value === "string" && value in sectorStylePresets
    ? sectorStylePresets[value as SectorStylePresetName]
    : sectorStylePresets.default;
}
