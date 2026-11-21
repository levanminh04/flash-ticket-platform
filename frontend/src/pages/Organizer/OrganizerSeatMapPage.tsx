import { ChangeEvent, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { toast } from "react-toastify";
import { ArrowLeft, Loader2 } from "lucide-react";
import OrganizerLayout from "../../components/organizer/OrganizerLayout";
import TixOrganizerTopNav from "../../components/organizer/TixOrganizerTopNav";
import TixEventWorkflowStepper from "../../components/organizer/TixEventWorkflowStepper";
import "./organizer-event-create.css";
import SeatMapCanvasShell from "../../components/seat-map/editor/SeatMapCanvasShell";
import SeatMapRightSidebar from "../../components/seat-map/editor/SeatMapRightSidebar";
import SeatMapTopToolbar, {
  SeatMapShapeMenuKey,
} from "../../components/seat-map/editor/SeatMapTopToolbar";
import {
  buildDefaultSeatLayoutConfig,
  createEditorDraftKey,
  exportEditorDocument,
  exportEditorDocumentToSvg,
  generateSeatsForShape,
  importEditorDocument,
  translateShape,
} from "../../components/seat-map/editor/seatMapEditorUtils";
import { useSeatMapEditorState } from "../../components/seat-map/editor/useSeatMapEditorState";
import {
  OrganizerPublicTicketType,
  OrganizerSeatMap,
} from "../../services/organizerWorkspaceService";
import {
  SeatMapEditorDocument,
  SeatMapEditorBounds,
  SeatMapEditorShape,
  SeatMapEditorSectorType,
} from "../../components/seat-map/editor/seatMapEditorTypes";
import {
  asNumber,
  polarToCartesian,
} from "../../components/seat-map/shared/sectorPathUtils";
import { editorSeatMap, editorTickets, readSeatMapOwner, saveSeatMapOwner, uploadSeatMapBackground } from "../../services/organizerSeatMapV1";
import { useOrganizerGate } from "./useOrganizerGate";
import {
  dispatchOrganizerWorkflowDirty,
  dispatchOrganizerWorkflowSaveResult,
} from "./organizerWorkflowEvents";

type SeatLayoutFieldKey =
  | "rows"
  | "seatsPerRow"
  | "gapX"
  | "gapY"
  | "paddingX"
  | "paddingY"
  | "seatRadius"
  | "seatStartNumber";

const SEAT_LAYOUT_MINIMUMS: Record<SeatLayoutFieldKey, number> = {
  rows: 1,
  seatsPerRow: 1,
  gapX: 1,
  gapY: 1,
  paddingX: 0,
  paddingY: 0,
  seatRadius: 1,
  seatStartNumber: 1,
};

const SEAT_MAP_CONTENT_PADDING = 40;

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

function getBoundsFromPoints(points: Array<{ x: number; y: number }>): SeatMapEditorBounds {
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

function getPublishValidationBounds(shape: SeatMapEditorShape): SeatMapEditorBounds {
  let baseBounds: SeatMapEditorBounds = shape.bounds;

  if (shape.shapeType === "ringSection" || shape.shapeType === "fan") {
    const mapData = shape.mapData ?? {};
    const cx = asNumber(mapData.cx) ?? shape.bounds.x + shape.bounds.width / 2;
    const cy = asNumber(mapData.cy) ?? shape.bounds.y + shape.bounds.height / 2;
    const outerRadius = Math.max(
      asNumber(mapData.outerRadius) ?? Math.max(shape.bounds.width, shape.bounds.height) / 2,
      1,
    );
    const innerRadius = Math.max(
      asNumber(mapData.innerRadius) ?? (shape.shapeType === "ringSection" ? outerRadius * 0.72 : 0),
      0,
    );
    const startAngle = asNumber(mapData.startAngle) ?? -70;
    const endAngle = asNumber(mapData.endAngle) ?? 70;
    const sampleAngles = [startAngle, endAngle, -180, -90, 0, 90, 180].filter((angle) =>
      isAngleInSweep(angle, startAngle, endAngle),
    );
    const points = sampleAngles.flatMap((angle) => [
      polarToCartesian(cx, cy, outerRadius, angle),
      polarToCartesian(cx, cy, innerRadius, angle),
    ]);

    baseBounds = getBoundsFromPoints(points);
  }

  let minX = baseBounds.x;
  let minY = baseBounds.y;
  let maxX = baseBounds.x + baseBounds.width;
  let maxY = baseBounds.y + baseBounds.height;

  const outlinePoints = shape.mapData?.transformedOutlinePoints;
  if (Array.isArray(outlinePoints)) {
    for (let index = 0; index + 1 < outlinePoints.length; index += 2) {
      const px = asNumber(outlinePoints[index]);
      const py = asNumber(outlinePoints[index + 1]);
      if (px !== null && py !== null) {
        minX = Math.min(minX, px);
        minY = Math.min(minY, py);
        maxX = Math.max(maxX, px);
        maxY = Math.max(maxY, py);
      }
    }
  }

  const seatPad = Math.max(shape.seatLayout?.seatRadius ?? 8.5, 8);
  for (const seat of shape.seats) {
    if (seat.hidden === true) continue;
    if (Number.isFinite(seat.x) && Number.isFinite(seat.y)) {
      minX = Math.min(minX, seat.x - seatPad);
      minY = Math.min(minY, seat.y - seatPad);
      maxX = Math.max(maxX, seat.x + seatPad);
      maxY = Math.max(maxY, seat.y + seatPad);
    }
  }

  return {
    x: minX,
    y: minY,
    width: Math.max(maxX - minX, 1),
    height: Math.max(maxY - minY, 1),
  };
}

function getDocumentContentBounds(document: SeatMapEditorDocument) {
  const visibleShapes = document.shapes.filter((shape) => shape.visible !== false);
  if (!visibleShapes.length) {
    return null;
  }

  const bounds = visibleShapes.map(getPublishValidationBounds);
  const minX = Math.min(...bounds.map((item) => item.x));
  const minY = Math.min(...bounds.map((item) => item.y));
  const maxX = Math.max(...bounds.map((item) => item.x + item.width));
  const maxY = Math.max(...bounds.map((item) => item.y + item.height));

  return { minX, minY, maxX, maxY };
}

function fitSeatMapDocumentToContent(document: SeatMapEditorDocument): SeatMapEditorDocument {
  const contentBounds = getDocumentContentBounds(document);
  if (!contentBounds) {
    return document;
  }

  const deltaX =
    contentBounds.minX < SEAT_MAP_CONTENT_PADDING
      ? SEAT_MAP_CONTENT_PADDING - contentBounds.minX
      : 0;
  const deltaY =
    contentBounds.minY < SEAT_MAP_CONTENT_PADDING
      ? SEAT_MAP_CONTENT_PADDING - contentBounds.minY
      : 0;
  const fittedShapes =
    deltaX || deltaY
      ? document.shapes.map((shape) => translateShape(shape, deltaX, deltaY))
      : document.shapes;
  const maxX = contentBounds.maxX + deltaX;
  const maxY = contentBounds.maxY + deltaY;

  return {
    ...document,
    width: Math.ceil(Math.max(document.width, maxX + SEAT_MAP_CONTENT_PADDING)),
    height: Math.ceil(Math.max(document.height, maxY + SEAT_MAP_CONTENT_PADDING)),
    shapes: fittedShapes,
  };
}

function validateSeatMapDocumentForPublish(document: SeatMapEditorDocument) {
  if (!Number.isFinite(document.width) || document.width <= 0) {
    return "Chiều rộng canvas phải lớn hơn 0.";
  }
  if (!Number.isFinite(document.height) || document.height <= 0) {
    return "Chiều cao canvas phải lớn hơn 0.";
  }

  const visibleShapes = document.shapes.filter((shape) => shape.visible !== false);
  const invalidSector = visibleShapes.find(
    (shape) => {
      const validationBounds = getPublishValidationBounds(shape);
      return (
        !shape.name.trim() ||
        !Number.isFinite(validationBounds.width) ||
        !Number.isFinite(validationBounds.height) ||
        validationBounds.width <= 0 ||
        validationBounds.height <= 0
      );
    },
  );
  if (invalidSector) {
    return `Khu "${invalidSector.name || invalidSector.id}" phải có tên và nằm trong canvas với kích thước lớn hơn 0.`;
  }

  const invalidSeat = visibleShapes
    .filter((shape) => shape.sectorType === "SEATED")
    .flatMap((shape) =>
      shape.seats
        .filter((seat) => seat.hidden !== true)
        .map((seat) => ({ shape, seat })),
    )
    .find(
      ({ seat }) =>
        !Number.isFinite(seat.x) ||
        !Number.isFinite(seat.y) ||
        seat.x < 0 ||
        seat.y < 0 ||
        seat.x > document.width ||
        seat.y > document.height,
    );
  if (invalidSeat) {
    return `Ghế "${invalidSeat.seat.label}" trong khu "${invalidSeat.shape.name}" phải nằm trong canvas.`;
  }

  return null;
}

const DEFAULT_LAYOUT_INPUTS: Record<SeatLayoutFieldKey, string> = {
  rows: "",
  seatsPerRow: "",
  gapX: "",
  gapY: "",
  paddingX: "",
  paddingY: "",
  seatRadius: "",
  seatStartNumber: "",
};

function normalizeSeatLayoutValue(rawValue: string, minimum: number) {
  const parsedValue = Number(rawValue);
  if (!Number.isFinite(parsedValue)) {
    return minimum;
  }

  return Math.max(minimum, parsedValue);
}

const PROTECTED_SEAT_STATUSES = new Set(["RESERVED", "SOLD"]);

function getTicketTypeSectorId(ticketType: { eventSectorId?: string | null; sectorId?: string | null }) {
  return ticketType.eventSectorId || ticketType.sectorId || "";
}

function getStandingShapeTicketTypeIds(shape: { ticketTypeId?: string; ticketTypeIds?: string[] }) {
  return shape.ticketTypeIds?.length ? shape.ticketTypeIds : shape.ticketTypeId ? [shape.ticketTypeId] : [];
}

function isProtectedSeatStatus(status?: string) {
  return status ? PROTECTED_SEAT_STATUSES.has(status.toUpperCase()) : false;
}

function getRequestErrorMessage(error: unknown) {
  const response = (error as { response?: { data?: unknown; status?: number } })?.response;
  const data = response?.data;

  if (data && typeof data === "object") {
    const message =
      (data as { message?: unknown; error?: unknown }).message ??
      (data as { message?: unknown; error?: unknown }).error;
    if (typeof message === "string" && message.trim()) {
      return message;
    }
  }

  if (typeof data === "string" && data.trim()) {
    return data;
  }

  if (response?.status) {
    return `Không lưu được sơ đồ (HTTP ${response.status}).`;
  }

  return error instanceof Error
    ? `Không lưu được sơ đồ. ${error.message}`
    : "Không lưu được sơ đồ.";
}

export default function OrganizerSeatMapPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const isSectorSetupPhase = searchParams.get("phase") === "sectors";
  const { ready } = useOrganizerGate();
  const [eventDetail, setEventDetail] = useState<Awaited<ReturnType<typeof readSeatMapOwner>> | null>(null);
  const editorDraftId = eventDetail ? `${eventDetail.eventId}:${eventDetail.rowVersion}` : undefined;
  const [organizerTicketTypes, setOrganizerTicketTypes] = useState<OrganizerPublicTicketType[]>([]);
  const [seatMap, setSeatMap] = useState<OrganizerSeatMap | null>(null);
  const [loading, setLoading] = useState(true);
  const [publishing, setPublishing] = useState(false);
  const [uploadingBackground, setUploadingBackground] = useState(false);
  const [editingShapeId, setEditingShapeId] = useState<string | null>(null);
  const [showGrid, setShowGrid] = useState(true);
  const [seatLayoutInputs, setSeatLayoutInputs] = useState<Record<SeatLayoutFieldKey, string>>(DEFAULT_LAYOUT_INPUTS);
  const [, setUnsavedChanges] = useState(false);
  const importFileInputRef = useRef<HTMLInputElement | null>(null);
  const svgFileInputRef = useRef<HTMLInputElement | null>(null);
  const hasInitializedDocumentRef = useRef(false);
  const ignoreNextDocumentChangeRef = useRef(false);

  useEffect(() => {
    if (!ready || !eventId) {
      return;
    }

    let cancelled = false;

    const load = async () => {
      setLoading(true);
      try {
        const nextEvent = await readSeatMapOwner(eventId);
        if (nextEvent.mode !== "SEAT_MAP" || nextEvent.status !== "DRAFT") throw new Error("Chỉ sửa được sơ đồ của bản nháp SEAT_MAP.");
        if (!cancelled) {
          setEventDetail(nextEvent);
          setOrganizerTicketTypes(editorTickets(nextEvent));
          setSeatMap(editorSeatMap(nextEvent));
          setUnsavedChanges(false);
          if (isSectorSetupPhase && (nextEvent.seatMap?.sectors.length ?? 0) > 0) {
            dispatchOrganizerWorkflowSaveResult(true);
          }
        }
      } catch {
        if (!cancelled) {
          toast.error("Không tải được sơ đồ của bản nháp.");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    void load();

    return () => {
      cancelled = true;
    };
  }, [eventId, isSectorSetupPhase, ready]);

  const editorSource = seatMap;
  const [resetViewSignal, setResetViewSignal] = useState(0);
  const ticketTypes = organizerTicketTypes;
  const ticketTypeById = useMemo(
    () => new Map(ticketTypes.map((ticketType) => [ticketType.id, ticketType] as const)),
    [ticketTypes],
  );
  const publishedSectorIds = useMemo(
    () => new Set((seatMap?.sectors ?? []).map((sector) => sector.id)),
    [seatMap],
  );
  const ticketTypeCountBySectorId = useMemo(() => {
    const counts = new Map<string, number>();
    for (const ticketType of ticketTypes) {
      const sectorId = getTicketTypeSectorId(ticketType);
      if (sectorId) {
        counts.set(sectorId, (counts.get(sectorId) ?? 0) + 1);
      }
    }
    return counts;
  }, [ticketTypes]);
  const {
    activeTool,
    document,
    hasDraft,
    referenceImageVisible,
    selectedSeat,
    selectedSeatId,
    selectedSeatIds,
    selectedSeats,
    selectedShape,
    selectedShapeIds,
    canRedo,
    canUndo,
    clearSeatSelection,
    viewport,
    addTrapezoid,
    addDiamond,
    addHexagon,
    addCircle,
    addEllipse,
    addFanSection,
    addConcertOvalTemplate,
    addFestivalTStageTemplate,
    addSlicedRingArc,
    addMainStage,
    addStage,
    addFoh,
    addSeatedBlock,
    addRoundedBlock,
    addRoundTable,
    addBottomRingSection,
    addLeftSideRing,
    addRightSideRing,
    addVipLeftCurved,
    addVipRightCurved,
    clearDraft,
    clearSeats,
    cloneShapeById,
    createSeatRowsFromBounds,
    flipShapeH,
    flipShapeV,
    hideSeat,
    hideSelectedSeats,
    moveSeat,
    moveSeatBlock,
    openOrCreateVenueShape,
    regenerateSeats,
    removeShape,
    removeShapes,
    replaceDocument,
    resizeSeatBlock,
    resizeShape,
    restoreAllSeats,
    restoreSeat,
    restoreSelectedSeats,
    redo,
    selectSeat,
    selectSeatsInShape,
    selectShape,
    selectShapes,
    setActiveTool,
    setReferenceImageUrl,
    setReferenceImageVisible,
    setViewport,
    toggleShapeLocked,
    toggleShapeVisibility,
    transformPolygon,
    translateShapeBy,
    translateShapesBy,
    undo,
    updateSeatLayout,
    updateSeatLayoutAndGenerate,
    updateShape,
    updateSeat,
  } = useSeatMapEditorState(editorDraftId, editorSource);

  useEffect(() => {
    if (!document) {
      return;
    }
    if (!hasInitializedDocumentRef.current) {
      hasInitializedDocumentRef.current = true;
      return;
    }
    if (ignoreNextDocumentChangeRef.current) {
      ignoreNextDocumentChangeRef.current = false;
      return;
    }
    setUnsavedChanges(true);
    dispatchOrganizerWorkflowDirty();
  }, [document]);

  useEffect(() => {
    if (!selectedShape) {
      setSeatLayoutInputs(DEFAULT_LAYOUT_INPUTS);
      return;
    }

    const seatLayout =
      selectedShape.seatLayout ??
      buildDefaultSeatLayoutConfig(selectedShape.bounds, selectedShape.seats.length);

    setSeatLayoutInputs({
      rows: String(seatLayout.rows),
      seatsPerRow: String(seatLayout.seatsPerRow),
      gapX: String(seatLayout.gapX),
      gapY: String(seatLayout.gapY),
      paddingX: String(seatLayout.paddingX),
      paddingY: String(seatLayout.paddingY),
      seatRadius: String(seatLayout.seatRadius),
      seatStartNumber: String(seatLayout.seatStartNumber),
    });
  }, [selectedShape]);

  useEffect(() => {
    if (!editingShapeId || !document?.shapes.some((shape) => shape.id === editingShapeId)) {
      setEditingShapeId(null);
    }
  }, [document, editingShapeId]);

  const seatDropdownOptions = useMemo(() => {
    if (!selectedShape) return { rows: [], seatNumbers: [] };
    const rowMap = new Map<string, boolean>();
    const noMap = new Map<string, boolean>();

    for (const seat of selectedShape.seats) {
      if (seat.rowName) {
        if (!rowMap.has(seat.rowName)) rowMap.set(seat.rowName, seat.hidden ?? false);
        else if (seat.hidden) rowMap.set(seat.rowName, true);
      }
      if (seat.seatNumber) {
        if (selectedSeat && seat.rowName === selectedSeat.rowName) {
          noMap.set(seat.seatNumber, seat.hidden ?? false);
        } else if (!selectedSeat || !noMap.has(seat.seatNumber)) {
          if (!noMap.has(seat.seatNumber)) noMap.set(seat.seatNumber, seat.hidden ?? false);
          else if (seat.hidden) noMap.set(seat.seatNumber, true);
        }
      }
    }

    return {
      rows: Array.from(rowMap.entries())
        .map(([value, hidden]) => ({ value, hidden }))
        .sort((a, b) => a.value.localeCompare(b.value)),
      seatNumbers: Array.from(noMap.entries())
        .map(([value, hidden]) => ({ value, hidden }))
        .sort((a, b) => Number(a.value) - Number(b.value)),
    };
  }, [selectedSeat, selectedShape]);

  const handleSeatLayoutInputChange =
    (shapeId: string, key: string) => (event: ChangeEvent<HTMLInputElement>) => {
      const fieldKey = key as SeatLayoutFieldKey;
      const rawValue = event.target.value;
      setSeatLayoutInputs((current) => ({
        ...current,
        [fieldKey]: rawValue,
      }));

      if (rawValue.trim() === "") {
        return;
      }

      updateSeatLayout(shapeId, {
        [fieldKey]: normalizeSeatLayoutValue(rawValue, SEAT_LAYOUT_MINIMUMS[fieldKey]),
      });
    };

  const handleSeatLayoutInputBlur = (shapeId: string, key: string) => {
    const fieldKey = key as SeatLayoutFieldKey;
    const normalizedValue = normalizeSeatLayoutValue(seatLayoutInputs[fieldKey], SEAT_LAYOUT_MINIMUMS[fieldKey]);
    setSeatLayoutInputs((current) => ({
      ...current,
      [fieldKey]: String(normalizedValue),
    }));
    updateSeatLayout(shapeId, { [fieldKey]: normalizedValue });
  };

  const handleShapeMetaChange = (
    shapeId: string,
    patch: {
      name?: string;
      color?: string;
      ticketTypeId?: string;
      ticketTypeIds?: string[];
      sectorType?: SeatMapEditorSectorType;
      totalCapacity?: number;
    },
  ) => {
    const currentShape = document?.shapes.find((shape) => shape.id === shapeId);
    if (!currentShape) {
      return;
    }

    if (
      patch.sectorType !== undefined &&
      patch.sectorType !== currentShape.sectorType &&
      publishedSectorIds.has(shapeId) &&
      (currentShape.seats.length > 0 || (ticketTypeCountBySectorId.get(shapeId) ?? 0) > 0)
    ) {
      toast.error("Không thể đổi loại khu vực khi sector đã có ghế hoặc loại vé.");
      return;
    }

    if (
      patch.ticketTypeIds !== undefined &&
      patch.ticketTypeIds.some((ticketTypeId) => {
        const ticketType = ticketTypeById.get(ticketTypeId);
        return !ticketType || getTicketTypeSectorId(ticketType) !== shapeId;
      })
    ) {
      toast.error("Seat type không thuộc khu đang chọn.");
      return;
    }

    updateShape(shapeId, (shape) => {
      let nextShape: SeatMapEditorShape = {
        ...shape,
        ...(patch.name !== undefined ? { name: patch.name, label: patch.name } : {}),
        ...(patch.color !== undefined
          ? {
              color: patch.color,
              mapData: {
                ...(shape.mapData ?? {}),
                fill: patch.color,
              },
            }
          : {}),
        ...(patch.sectorType !== undefined
          ? {
              sectorType: patch.sectorType,
              seats: patch.sectorType === "STANDING" ? [] : shape.seats,
              seatCount: patch.sectorType === "STANDING" ? 0 : shape.seatCount,
              totalCapacity:
                patch.sectorType === "STANDING"
                  ? shape.totalCapacity && shape.totalCapacity >= 1
                    ? shape.totalCapacity
                    : 500
                  : shape.totalCapacity,
            }
          : {}),
        ...("totalCapacity" in patch ? { totalCapacity: patch.totalCapacity } : {}),
        ...(patch.ticketTypeId !== undefined
          ? (() => {
              const assignedTicket = patch.ticketTypeId ? ticketTypeById.get(patch.ticketTypeId) : undefined;
              return {
                ticketTypeId: patch.ticketTypeId || undefined,
                ticketTypeName: assignedTicket?.name ?? undefined,
                ticketTypeIds: patch.ticketTypeId ? [patch.ticketTypeId] : [],
                ticketTypeNames: assignedTicket?.name ? [assignedTicket.name] : [],
                ...(shape.sectorType === "SEATED"
                  ? {
                      seats: shape.seats.map((seat) =>
                        seat.hidden === true
                          ? seat
                          : {
                              ...seat,
                              ticketTypeId: patch.ticketTypeId || undefined,
                              colorCode: assignedTicket?.colorCode ?? undefined,
                            },
                      ),
                    }
                  : {}),
              };
            })()
          : {}),
        ...(patch.ticketTypeIds !== undefined
          ? {
              ticketTypeIds: patch.ticketTypeIds,
              ticketTypeNames: patch.ticketTypeIds
                .map((ticketTypeId) => ticketTypes.find((ticketType) => ticketType.id === ticketTypeId)?.name ?? "")
                .filter(Boolean),
              ticketTypeId: patch.ticketTypeIds[0] || undefined,
              ticketTypeName: patch.ticketTypeIds[0]
                ? ticketTypes.find((ticketType) => ticketType.id === patch.ticketTypeIds?.[0])?.name ?? undefined
                : undefined,
            }
          : {}),
      };

      if (patch.sectorType === "SEATED" && nextShape.seats.length === 0) {
        nextShape = generateSeatsForShape(nextShape);
      }

      return nextShape;
    });

    if (patch.ticketTypeIds !== undefined && currentShape.sectorType === "STANDING") {
      toast.success(`Đã gán ${patch.ticketTypeIds.length} loại vé cho khu đứng "${currentShape.name}".`);
      return;
    }

    if (patch.ticketTypeId && currentShape.sectorType === "STANDING") {
      const ticketType = ticketTypeById.get(patch.ticketTypeId);
      toast.success(`Đã gán "${ticketType?.name ?? "loại vé"}" cho khu đứng "${currentShape.name}".`);
    }
  };

  const handleAssignTicketTypeToSeats = (
    shapeId: string,
    scope: "seat" | "selected" | "all",
    value: string,
    ticketTypeId: string,
  ) => {
    if (!ticketTypeId) {
      return;
    }

    const ticketType = ticketTypeById.get(ticketTypeId);
    if (!ticketType || getTicketTypeSectorId(ticketType) !== shapeId) {
      toast.error("Seat type không thuộc khu đang chọn.");
      return;
    }

    const targetSelectedSeatIds = value
      ? value.split(",").map((seatId) => seatId.trim()).filter(Boolean)
      : selectedSeatIds;
    const assignedSeatCount =
      document?.shapes
        .find((shape) => shape.id === shapeId)
        ?.seats.filter((seat) => {
          if (scope === "all") {
            return seat.hidden !== true;
          }

          if (scope === "seat") {
            return seat.id === value;
          }

          return targetSelectedSeatIds.includes(seat.id);
        }).length ?? 0;

    updateShape(shapeId, (shape) => ({
      ...shape,
      seats: shape.seats.map((seat) => {
        const matches =
          scope === "all" ||
          (scope === "seat" && seat.id === value) ||
          (scope === "selected" && targetSelectedSeatIds.includes(seat.id));

        if (!matches) {
          return seat;
        }

        return {
          ...seat,
          ticketTypeId,
          colorCode: ticketType.colorCode ?? seat.colorCode,
        };
      }),
    }));

    if (assignedSeatCount > 0) {
      toast.success(`Đã gán "${ticketType.name}" cho ${assignedSeatCount} ghế.`);
    }
  };

  const hasProtectedSeats = (shapeId: string, seatIds?: string[]) => {
    const shape = document?.shapes.find((item) => item.id === shapeId);
    if (!shape) {
      return false;
    }
    const targetSeatIds = seatIds ? new Set(seatIds) : null;
    return shape.seats.some((seat) =>
      (!targetSeatIds || targetSeatIds.has(seat.id)) &&
      isProtectedSeatStatus(seat.inventoryStatus),
    );
  };

  const hasCommittedSectorState = (shapeId: string) => {
    const shape = document?.shapes.find((item) => item.id === shapeId);
    return Boolean(
      shape &&
      (shape.seats.some((seat) => isProtectedSeatStatus(seat.inventoryStatus)) ||
        (ticketTypeCountBySectorId.get(shapeId) ?? 0) > 0),
    );
  };

  const handleToggleShapeVisibility = (shapeId: string) => {
    if (publishedSectorIds.has(shapeId) && hasCommittedSectorState(shapeId)) {
      toast.error("Không thể ẩn sector đã có loại vé hoặc ghế đã giữ/đã bán.");
      return;
    }
    toggleShapeVisibility(shapeId);
  };

  const handleRemoveShape = (shapeId: string) => {
    if (publishedSectorIds.has(shapeId) && hasCommittedSectorState(shapeId)) {
      toast.error("Không thể xóa sector đã có loại vé hoặc ghế đã giữ/đã bán.");
      return;
    }
    removeShape(shapeId);
  };

  const handleClearSeats = (shapeId: string) => {
    if (hasProtectedSeats(shapeId)) {
      toast.error("Không thể xóa ghế đang được giữ hoặc đã bán.");
      return;
    }
    clearSeats(shapeId);
  };

  const handleHideSeat = (shapeId: string, seatId: string) => {
    if (hasProtectedSeats(shapeId, [seatId])) {
      toast.error("Không thể ẩn ghế đang được giữ hoặc đã bán.");
      return;
    }
    hideSeat(shapeId, seatId);
  };

  const handleHideSelectedSeats = (shapeId: string, seatIds: string[]) => {
    if (hasProtectedSeats(shapeId, seatIds)) {
      toast.error("Không thể ẩn ghế đang được giữ hoặc đã bán.");
      return;
    }
    hideSelectedSeats(shapeId, seatIds);
  };

  const handleUpdateSeat = (shapeId: string, seatId: string, patch: Parameters<typeof updateSeat>[2]) => {
    if (hasProtectedSeats(shapeId, [seatId])) {
      toast.error("Không thể sửa ghế đang được giữ hoặc đã bán.");
      return;
    }
    updateSeat(shapeId, seatId, patch);

    if ("ticketTypeId" in patch && patch.ticketTypeId) {
      const ticketType = ticketTypeById.get(patch.ticketTypeId);
      toast.success(`Đã gán "${ticketType?.name ?? "loại vé"}" cho ghế.`);
    }
  };

  const handleMoveSeat = (shapeId: string, seatId: string, nextX: number, nextY: number) => {
    if (hasProtectedSeats(shapeId, [seatId])) {
      toast.error("Không thể di chuyển ghế đang được giữ hoặc đã bán.");
      return;
    }
    moveSeat(shapeId, seatId, nextX, nextY);
  };

  const handleMoveSeatBlock = (shapeId: string, deltaX: number, deltaY: number) => {
    if (hasProtectedSeats(shapeId)) {
      toast.error("Không thể di chuyển nhóm ghế có ghế đang được giữ hoặc đã bán.");
      return;
    }
    moveSeatBlock(shapeId, deltaX, deltaY);
  };

  const handleResizeSeatBlock = (
    shapeId: string,
    previousBounds: Parameters<typeof resizeSeatBlock>[1],
    nextBounds: Parameters<typeof resizeSeatBlock>[2],
  ) => {
    if (hasProtectedSeats(shapeId)) {
      toast.error("Không thể resize nhóm ghế có ghế đang được giữ hoặc đã bán.");
      return;
    }
    resizeSeatBlock(shapeId, previousBounds, nextBounds);
  };

  const handleResizeShape = (
    shapeId: string,
    nextBounds: Parameters<typeof resizeShape>[1],
    nextPoints?: Parameters<typeof resizeShape>[2],
  ) => {
    if (hasProtectedSeats(shapeId)) {
      toast.error("Không thể resize sector có ghế đang được giữ hoặc đã bán.");
      return;
    }
    resizeShape(shapeId, nextBounds, nextPoints);
  };

  const handleTransformPolygon = (
    shapeId: string,
    nextBounds: Parameters<typeof transformPolygon>[1],
    scaleX: number,
    scaleY: number,
  ) => {
    if (hasProtectedSeats(shapeId)) {
      toast.error("Không thể chỉnh sector có ghế đang được giữ hoặc đã bán.");
      return;
    }
    transformPolygon(shapeId, nextBounds, scaleX, scaleY);
  };

  const handleTranslateShapeBy = (shapeId: string, deltaX: number, deltaY: number) => {
    if (hasProtectedSeats(shapeId)) {
      toast.error("Không thể di chuyển sector có ghế đang được giữ hoặc đã bán.");
      return;
    }
    translateShapeBy(shapeId, deltaX, deltaY);
  };

  const handleTranslateShapesBy = (shapeIds: string[], deltaX: number, deltaY: number) => {
    if (shapeIds.some((shapeId) => hasProtectedSeats(shapeId))) {
      toast.error("Không thể di chuyển nhóm sector có ghế đang được giữ hoặc đã bán.");
      return;
    }
    translateShapesBy(shapeIds, deltaX, deltaY);
  };

  const handleRegenerateSeats = (shapeId: string) => {
    if (hasProtectedSeats(shapeId)) {
      toast.error("Không thể tạo lại ghế cho sector có ghế đang được giữ hoặc đã bán.");
      return;
    }
    regenerateSeats(shapeId);
  };

  const handleUpdateSeatLayoutAndGenerate = (
    shapeId: string,
    patch: Parameters<typeof updateSeatLayoutAndGenerate>[1],
  ) => {
    if (hasProtectedSeats(shapeId)) {
      toast.error("Không thể thay đổi sơ đồ ghế cho sector có ghế đang được giữ hoặc đã bán.");
      return;
    }
    updateSeatLayoutAndGenerate(shapeId, patch);
  };

  const handleFlipShapeH = (shapeId: string) => {
    if (hasProtectedSeats(shapeId)) {
      toast.error("Không thể lật sector có ghế đang được giữ hoặc đã bán.");
      return;
    }
    flipShapeH(shapeId);
  };

  const handleFlipShapeV = (shapeId: string) => {
    if (hasProtectedSeats(shapeId)) {
      toast.error("Không thể lật sector có ghế đang được giữ hoặc đã bán.");
      return;
    }
    flipShapeV(shapeId);
  };

  const handlePublish = async () => {
    if (!eventId || !document || publishing || uploadingBackground) {
      return;
    }

    const publishDocument = fitSeatMapDocumentToContent(document);
    const documentValidationMessage = validateSeatMapDocumentForPublish(publishDocument);
    if (documentValidationMessage) {
      toast.error(documentValidationMessage);
      return;
    }

    const isSellableStandingShape = (shape: SeatMapEditorShape) =>
      shape.visible !== false &&
      shape.sectorType === "STANDING" &&
      shape.mapData?.decorative !== true &&
      shape.shapeType !== "stage" &&
      shape.shapeType !== "foh";

    const invalidStandingSector = publishDocument.shapes.find(
      (shape) =>
        isSellableStandingShape(shape) &&
        (!shape.totalCapacity || shape.totalCapacity < 1),
    );
    if (invalidStandingSector) {
      toast.error(`Khu "${invalidStandingSector.name}" cần nhập sức chứa tối đa.`);
      return;
    }

    const unassignedStandingSector = !isSectorSetupPhase
      ? publishDocument.shapes.find(
          (shape) =>
            isSellableStandingShape(shape) &&
            getStandingShapeTicketTypeIds(shape).length === 0,
        )
      : null;
    if (unassignedStandingSector) {
      toast.error(`Khu đứng "${unassignedStandingSector.name}" cần chọn loại vé trước khi publish.`);
      return;
    }

    const mismatchedStandingTicketTypeSector = publishDocument.shapes
      .filter((shape) => isSellableStandingShape(shape))
      .flatMap((shape) =>
        getStandingShapeTicketTypeIds(shape).map((ticketTypeId) => ({
          shape,
          ticketTypeId,
          ticketType: ticketTypeById.get(ticketTypeId),
        })),
      )
      .find(({ shape, ticketType }) => !ticketType || getTicketTypeSectorId(ticketType) !== shape.id);
    if (mismatchedStandingTicketTypeSector) {
      toast.error(
        `Loại vé "${mismatchedStandingTicketTypeSector.ticketType?.name ?? mismatchedStandingTicketTypeSector.ticketTypeId}" không thuộc khu đứng "${mismatchedStandingTicketTypeSector.shape.name}".`,
      );
      return;
    }

    const unassignedSeat = publishDocument.shapes
      .filter((shape) => shape.visible !== false && shape.sectorType === "SEATED")
      .flatMap((shape) =>
        shape.seats
          .filter((seat) => seat.hidden !== true && !seat.ticketTypeId)
          .map((seat) => ({ shape, seat })),
      )[0];
    if (!isSectorSetupPhase && unassignedSeat) {
      toast.error(
        `Ghế "${unassignedSeat.seat.label}" trong khu "${unassignedSeat.shape.name}" cần chọn seat type trước khi publish.`,
      );
      return;
    }

    const mismatchedTicketTypeSector = publishDocument.shapes
      .filter((shape) => shape.visible !== false && shape.sectorType === "SEATED")
      .flatMap((shape) =>
        shape.seats
          .filter((seat) => seat.hidden !== true && seat.ticketTypeId)
          .map((seat) => ({ shape, seat, ticketType: ticketTypeById.get(seat.ticketTypeId || "") })),
      )
      .find(({ shape, ticketType }) => !ticketType || getTicketTypeSectorId(ticketType) !== shape.id);
    if (mismatchedTicketTypeSector) {
      toast.error(
        `Seat type "${mismatchedTicketTypeSector.ticketType?.name ?? mismatchedTicketTypeSector.seat.ticketTypeId}" không thuộc khu "${mismatchedTicketTypeSector.shape.name}". Hãy chọn seat type đúng khu hoặc tạo lại seat type cho khu này.`,
      );
      return;
    }

    if (!eventDetail) return;
    setPublishing(true);
    try {
      const saved = await saveSeatMapOwner(eventDetail, publishDocument);
      window.localStorage.removeItem(createEditorDraftKey(editorDraftId ?? eventId));
      ignoreNextDocumentChangeRef.current = true;
      setEventDetail(saved);
      setSeatMap(editorSeatMap(saved));
      setOrganizerTicketTypes(editorTickets(saved));
      setUnsavedChanges(false);
      dispatchOrganizerWorkflowSaveResult(true);
      toast.success("Đã lưu sơ đồ.");
      navigate(`/organizer/events/${eventId}/edit?step=${isSectorSetupPhase ? "tickets" : "review"}`);
    } catch (error) {
      dispatchOrganizerWorkflowSaveResult(false);
      const status = (error as { response?: { status?: number } })?.response?.status;
      if (status && status >= 400 && status < 500) {
        toast.error(getRequestErrorMessage(error));
        return;
      }

      toast.error(getRequestErrorMessage(error));
    } finally {
      setPublishing(false);
    }
  };

  const handleExportJson = () => {
    if (!document || !eventId) {
      return;
    }

    const blob = new Blob([exportEditorDocument(document)], { type: "application/json;charset=utf-8" });
    const objectUrl = window.URL.createObjectURL(blob);
    const anchor = window.document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = `seat-map-${eventId}.json`;
    anchor.click();
    window.URL.revokeObjectURL(objectUrl);
  };

  const handleImportJsonClick = () => {
    importFileInputRef.current?.click();
  };

  const handleExportSvg = () => {
    if (!document || !eventId) {
      return;
    }

    const svgContent = exportEditorDocumentToSvg(document, true);
    const blob = new Blob([svgContent], { type: "image/svg+xml;charset=utf-8" });
    const objectUrl = window.URL.createObjectURL(blob);
    const anchor = window.document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = `seat-map-${eventId}.svg`;
    anchor.click();
    window.URL.revokeObjectURL(objectUrl);
    toast.success("Đã xuất sơ đồ ghế ra tệp SVG.");
  };

  const handleImportSvgClick = () => {
    svgFileInputRef.current?.click();
  };

  const handleImportSvg = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || !eventId || uploadingBackground || publishing) return;
    setUploadingBackground(true);
    try {
      const reference = await uploadSeatMapBackground(eventId, file);
      setReferenceImageUrl(reference);
      setUnsavedChanges(true);
      toast.success("Đã tải ảnh nền SVG. Lưu sơ đồ để áp dụng.");
    } catch (error) {
      const status = (error as { response?: { status?: number } }).response?.status;
      toast.error(status === 413 ? "File SVG vượt quá dung lượng cho phép."
        : status === 422 ? "SVG không hợp lệ hoặc chứa nội dung không được hỗ trợ."
        : "Không tải được ảnh nền SVG.");
    } finally {
      setUploadingBackground(false);
    }
  };

  const handleImportJson = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";

    if (!file) {
      return;
    }

    try {
      const rawValue = await file.text();
      const importedDocument = importEditorDocument(rawValue);
      if (!importedDocument) {
        toast.error("Invalid seat map editor JSON file.");
        return;
      }

      replaceDocument(importedDocument);
      setUnsavedChanges(true);
      toast.success("Seat map JSON imported.");
    } catch {
      toast.error("Cannot read JSON file.");
    }
  };

  const handleClearDraft = () => {
    clearDraft();
    setUnsavedChanges(false);
  };

  const handleDeleteSelected = () => {
    if (selectedShape && selectedSeatIds.length > 0) {
      if (selectedSeatIds.length === 1) {
        handleHideSeat(selectedShape.id, selectedSeatIds[0]);
      } else {
        handleHideSelectedSeats(selectedShape.id, selectedSeatIds);
      }
      return;
    }

    if (!selectedShapeIds.length) {
      return;
    }

    const blockedShape = selectedShapeIds.find(
      (shapeId) => publishedSectorIds.has(shapeId) && hasCommittedSectorState(shapeId),
    );
    if (blockedShape) {
      toast.error("Không thể xóa sector đã có loại vé hoặc ghế đã giữ/đã bán.");
      return;
    }

    if (selectedShapeIds.length === 1) {
      removeShape(selectedShapeIds[0]);
    } else {
      removeShapes(selectedShapeIds);
    }
  };

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const tagName = target?.tagName?.toUpperCase() ?? "";
      if (
        tagName === "INPUT" ||
        tagName === "TEXTAREA" ||
        tagName === "SELECT" ||
        target?.isContentEditable
      ) {
        return;
      }

      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "z") {
        event.preventDefault();
        if (event.shiftKey) {
          if (canRedo) redo();
        } else if (canUndo) {
          undo();
        }
        return;
      }

      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "y") {
        event.preventDefault();
        if (canRedo) redo();
        return;
      }

      if (event.key === "Delete" || event.key === "Backspace") {
        if (selectedSeatIds.length > 0 || selectedShapeIds.length > 0) {
          event.preventDefault();
          handleDeleteSelected();
        }
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [canRedo, canUndo, redo, selectedSeatIds, selectedShapeIds, undo]);

  const handleCreateSeatTypeForShape = (shapeId: string) => {
    if (!eventId) {
      return;
    }
    if (!publishedSectorIds.has(shapeId)) {
      toast.error("Hãy lưu khu vực trước khi tạo loại vé cho khu đó.");
      return;
    }

    navigate(`/organizer/events/${eventId}/edit?step=tickets`);
  };

  return (
    <OrganizerLayout
      title={isSectorSetupPhase ? "Sơ đồ khu vực" : "Hoàn thiện sơ đồ"}
      description={
        isSectorSetupPhase
          ? "Tạo các khu vực đứng hoặc ngồi trước khi cấu hình loại vé."
          : "Sinh ghế, gán loại vé và lưu sơ đồ để gửi duyệt."
      }
      hideTopBar
      hideSidebar
      hideHeader
      requireOrganizer
      className="organizer-hub-page organizer-seat-map-page"
    >
      <div className="tix-event-create-page">
        <div
          className="tix-event-create-container"
          style={{ maxWidth: "1360px", margin: "0 auto" }}
        >
          <TixOrganizerTopNav activeTab="create" />

          <div className="tix-create-header-wrap">
            <div className="tix-create-title-row">
              <div className="tix-create-title-block">
                <h1>Chỉnh sửa sự kiện</h1>
                <p>
                  Thiết lập thông tin sự kiện, thời gian diễn ra và phương thức bán vé để mở bán trên FlashTicket.
                </p>
              </div>
              {eventId && (
                <span className="tix-create-status-badge is-draft">Bản nháp</span>
              )}
            </div>
          </div>

          <TixEventWorkflowStepper
            currentStep={isSectorSetupPhase ? "sectors" : "tickets"}
            mode="SEAT_MAP"
            eventId={eventId}
            hasImages={Boolean(eventDetail)}
            hasSectors={Boolean(eventDetail?.seatMap?.sectors?.length)}
            hasTickets={Boolean(eventDetail?.ticketTypes?.length)}
            onStepClick={(step) => navigate(`/organizer/events/${eventId}/edit?step=${step}`)}
          />

          <div className="seat-map-editor-shell" style={{ margin: 0, minHeight: "auto" }}>

        {loading ? (
          <section className="organizer-panel organizer-empty-state">
            <div className="loading-spinner" />
            <p>Đang tải trình vẽ sơ đồ…</p>
          </section>
        ) : !document ? (
          <section className="organizer-panel organizer-empty-state">
            <p>Không mở được trình vẽ. Hãy quay lại bản nháp rồi thử lại.</p>
          </section>
        ) : (
          <div className="seat-map-workbench-frame">
            <input
              ref={svgFileInputRef}
              type="file"
              accept=".svg,image/svg+xml"
              style={{ display: "none" }}
              onChange={handleImportSvg}
            />
            <SeatMapTopToolbar
              activeTool={activeTool}
              canUndo={canUndo}
              canRedo={canRedo}
              hasSelectedShape={selectedShapeIds.length > 0}
              selectedSeatCount={selectedSeats.filter((s) => !s.hidden).length}
              selectedShapeHiddenSeatCount={
                selectedShape ? selectedShape.seats.filter((s) => s.hidden).length : 0
              }
              onSelectTool={setActiveTool}
              onOpenOrCreateVenueShape={openOrCreateVenueShape}
              isVenueShapeSelected={Boolean(selectedShape?.mapData?.isVenueShape === true)}
              onAddShapeMenu={(key: SeatMapShapeMenuKey) => {
                switch (key) {
                  case "rectangle":
                    addSeatedBlock();
                    break;
                  case "standing_rect":
                    addRoundedBlock();
                    break;
                  case "trapezoid":
                    addTrapezoid();
                    break;
                  case "fan":
                    addFanSection();
                    break;
                  case "ring":
                    addBottomRingSection();
                    break;
                  case "radial_sectors":
                    addSlicedRingArc();
                    break;
                  case "curved_left":
                    addVipLeftCurved();
                    break;
                  case "curved_right":
                    addVipRightCurved();
                    break;
                  case "stage":
                    addMainStage();
                    break;
                  case "t_stage":
                    addStage();
                    break;
                  case "foh":
                    addFoh();
                    break;
                  case "stadium_bowl":
                    addConcertOvalTemplate();
                    break;
                  case "concert_t_stage":
                    addFestivalTStageTemplate();
                    break;
                }
              }}
              onDuplicateSelected={() => {
                if (selectedShapeIds.length === 1) {
                  cloneShapeById(selectedShapeIds[0]);
                }
              }}
              onResetView={() => setResetViewSignal((prev) => prev + 1)}
              showGrid={showGrid}
              onToggleGrid={() => setShowGrid((prev) => !prev)}
              onUndo={undo}
              onRedo={redo}
              onDeleteSelected={handleDeleteSelected}
              onHideSelectedSeats={() => {
                const visibleIds = selectedSeats.filter((s) => !s.hidden).map((s) => s.id);
                if (selectedShape && visibleIds.length > 0) {
                  handleHideSelectedSeats(selectedShape.id, visibleIds);
                }
              }}
              onRestoreHiddenSeats={() => {
                if (!selectedShape) return;
                const hiddenIds = selectedSeats.filter((s) => s.hidden).map((s) => s.id);
                if (hiddenIds.length > 0) {
                  restoreSelectedSeats(selectedShape.id, hiddenIds);
                } else {
                  restoreAllSeats(selectedShape.id);
                }
              }}
              onImportJson={handleImportJsonClick}
              onExportJson={handleExportJson}
              onImportSvg={handleImportSvgClick}
              onExportSvg={handleExportSvg}
              onResetDraft={handleClearDraft}
            />
            <main className="seat-map-editor-main" style={{ margin: 0 }}>
            <SeatMapCanvasShell
              document={document}
              viewport={viewport}
              referenceImageVisible={referenceImageVisible}
              showGrid={showGrid}
              externalResetViewKey={resetViewSignal}
              activeTool={activeTool}
              selectedShapeIds={selectedShapeIds}
              selectedSeatId={selectedSeatId}
              selectedSeatIds={selectedSeatIds}
              onMoveSeat={handleMoveSeat}
              onMoveSeatBlock={handleMoveSeatBlock}
              onResizeSeatBlock={handleResizeSeatBlock}
              onResizeShape={handleResizeShape}
              onSelectSeat={selectSeat}
              onSelectSeatsInShape={selectSeatsInShape}
              onCreateSeatRowsFromBounds={createSeatRowsFromBounds}
              onCloneSelectedShape={
                selectedShapeIds.length === 1
                  ? () => cloneShapeById(selectedShapeIds[0])
                  : undefined
              }
              onSelectShape={selectShape}
              onSelectShapes={selectShapes}
              onTransformPolygon={handleTransformPolygon}
              onTranslateShape={handleTranslateShapeBy}
              onTranslateShapes={handleTranslateShapesBy}
              onViewportChange={setViewport}
              onDeleteSelected={handleDeleteSelected}
              onSetActiveTool={setActiveTool}
              onToggleReference={() => setReferenceImageVisible((current) => !current)}
              onUndo={undo}
              onRedo={redo}
              canUndo={canUndo}
              canRedo={canRedo}
              onClearSeatSelection={clearSeatSelection}
            />

            <SeatMapRightSidebar
              assignmentEnabled={!isSectorSetupPhase}
              activeTool={activeTool}
              document={document}
              selectedShape={selectedShape}
              selectedSeat={selectedSeat}
              selectedSeats={selectedSeats}
              selectedShapeIds={selectedShapeIds}
              editingShapeId={editingShapeId}
              seatLayoutInputs={seatLayoutInputs}
              seatDropdownOptions={seatDropdownOptions}
              ticketTypes={ticketTypes}
              importFileInputRef={importFileInputRef}
              onImportJson={handleImportJson}
              onImportJsonClick={handleImportJsonClick}
              onExportJson={handleExportJson}
              onSetActiveTool={setActiveTool}
              onAddTrapezoid={addTrapezoid}
              onAddDiamond={addDiamond}
              onAddHexagon={addHexagon}
              onAddCircle={addCircle}
              onAddEllipse={addEllipse}
              onAddRoundedBlock={addRoundedBlock}
              onAddSeatedBlock={addSeatedBlock}
              onAddRoundTable={addRoundTable}
              onAddConcertOvalTemplate={addConcertOvalTemplate}
              onAddFestivalTStageTemplate={addFestivalTStageTemplate}
              onAddSlicedRingArc={addSlicedRingArc}
              onAddMainStage={addMainStage}
              onAddStage={addStage}
              onAddFoh={addFoh}
              onAddVipLeftCurved={addVipLeftCurved}
              onAddVipRightCurved={addVipRightCurved}
              onAddBottomRingSection={addBottomRingSection}
              onAddFanSection={addFanSection}
              onAddLeftSideRing={addLeftSideRing}
              onAddRightSideRing={addRightSideRing}
              onSelectShape={selectShape}
              onToggleShapeVisibility={handleToggleShapeVisibility}
              onToggleShapeLocked={toggleShapeLocked}
              onRemoveShape={handleRemoveShape}
              onCloneShape={cloneShapeById}
              onFlipShapeH={handleFlipShapeH}
              onFlipShapeV={handleFlipShapeV}
              onResizeShape={handleResizeShape}
              onUpdateShape={updateShape}
              onSetEditingShapeId={setEditingShapeId}
              onShapeMetaChange={handleShapeMetaChange}
              onSeatLayoutInputChange={handleSeatLayoutInputChange}
              onSeatLayoutInputBlur={handleSeatLayoutInputBlur}
              onUpdateSeatLayoutAndGenerate={handleUpdateSeatLayoutAndGenerate}
              onGenerateSeats={handleRegenerateSeats}
              onRestoreHiddenSeats={restoreAllSeats}
              onClearSeats={handleClearSeats}
              onUpdateSeat={handleUpdateSeat}
              onAssignTicketTypeToSeats={handleAssignTicketTypeToSeats}
              onHideSeat={handleHideSeat}
              onHideSelectedSeats={handleHideSelectedSeats}
              onRestoreSeat={restoreSeat}
              onRestoreSelectedSeats={restoreSelectedSeats}
              onClearDraft={handleClearDraft}
              canClearDraft={hasDraft}
              onCreateSeatTypeForShape={handleCreateSeatTypeForShape}
            />
          </main>
          </div>
        )}
          </div>

          <div className="tix-form-actions-bar">
            <button
              type="button"
              className="tix-btn-secondary"
              onClick={() =>
                navigate(
                  `/organizer/events/${eventId}/edit?step=${isSectorSetupPhase ? "sectors" : "tickets"}`
                )
              }
            >
              <ArrowLeft size={16} />
              <span>Quay lại</span>
            </button>
            <button
              type="button"
              className="tix-btn-primary"
              disabled={loading || !document || publishing || uploadingBackground}
              onClick={() => void handlePublish()}
            >
              {publishing ? (
                <>
                  <Loader2 size={16} className="tix-spin-icon" />
                  <span>Đang lưu…</span>
                </>
              ) : (
                <span>Lưu sơ đồ</span>
              )}
            </button>
          </div>
        </div>
      </div>
    </OrganizerLayout>
  );
}
