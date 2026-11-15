import TixSelect from "./TixSelect";
import OrganizerDescriptionEditor from "./OrganizerDescriptionEditor";
import { descriptionContent } from "../../lib/eventDescription";
import { FormEvent, useState, useMemo, useRef, useEffect } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  Armchair,
  ArrowLeft,
  ArrowRight,
  Calendar,
  Check,
  CheckCircle2,
  Clock,
  FileText,
  Image as ImageIcon,
  Layers,
  Lock,
  MapPin,
  PenTool,
  Plus,
  Tag,
  Ticket,
  Trash2,
  Users,
  AlertCircle,
  Loader2,
} from "lucide-react";
import OrganizerLayout from "../../components/organizer/OrganizerLayout";
import TixOrganizerTopNav from "../../components/organizer/TixOrganizerTopNav";
import TixEventWorkflowStepper, {
  WorkflowStepId,
} from "../../components/organizer/TixEventWorkflowStepper";
import axiosClient from "../../lib/axiosClient";
import { categoryService } from "../../services/categoryService";
import { venueService } from "../../services/venueService";
import {
  organizerDashboardService,
  OrganizerSummary,
} from "../../services/organizerDashboardService";
import OrganizerDraftMedia, { EventWithImages } from "./OrganizerDraftMedia";
import { eventTicketPriceHint, eventTicketPricePattern } from "./eventDraftValidation";
import { useOrganizerGate } from "./useOrganizerGate";
import { toast } from "react-toastify";
import Lightbox from "yet-another-react-lightbox";
import "yet-another-react-lightbox/styles.css";
import { editorSeatMap } from "../../services/organizerSeatMapV1";
import {
  buildSeatMapEditorDocument,
  exportEditorDocumentToSvg,
} from "../../components/seat-map/editor/seatMapEditorUtils";
import "./organizer-event-create.css";

type SectorKind = "SEATED" | "STANDING";

type SectorTicketDraft = {
  localKey: string;
  ticketTypeId: string | null;
  name: string;
  unitPrice: string;
  colorCode: string;
};

const SECTOR_TICKET_PALETTE = [
  "#14b8a6",
  "#2563eb",
  "#db2777",
  "#ea580c",
  "#7c3aed",
  "#ca8a04",
  "#16a34a",
  "#0891b2",
];

function extractApiErrorMessage(error: unknown, fallback: string): string {
  const responseData = (error as { response?: { data?: { detail?: string; message?: string; title?: string } } })?.response?.data;
  const detail = responseData?.detail?.trim() || responseData?.message?.trim() || responseData?.title?.trim();
  return detail || fallback;
}

type Seat = {
  seatId: string;
  seatLabel: string;
  rowName: string;
  seatNumber: string;
  coordX: number;
  coordY: number;
  ticketTypeId: string | null;
  isHidden: boolean;
};

type Sector = {
  sectorId: string;
  name: string;
  code?: string | null;
  kind: SectorKind;
  configuredCapacity: string;
  displayOrder: number;
  colorCode: string | null;
  geometry?: unknown;
  seats: Seat[];
};

type SeatMap = {
  schemaVersion: number;
  layoutId: string;
  name: string;
  canvasWidth: number;
  canvasHeight: number;
  backgroundImageReference: string | null;
  layoutData?: { schemaVersion: number; decorations: object[] };
  sectors: Sector[];
};

type DraftTicket = {
  ticketTypeId: string;
  sectorId?: string | null;
  name: string;
  unitPrice: string;
  configuredQuantity: string | null;
  currency?: string;
  colorCode: string | null;
};

type OwnerDraft = EventWithImages & {
  description: string;
  categoryId: string | null;
  venueId: string | null;
  saleStartsAt: string;
  saleEndsAt: string;
  purchaseLimit: number;
  seatMap?: SeatMap | null;
  ticketTypes: DraftTicket[];
  promotions: DraftPromotion[];
};

type DiscountKind = "PERCENT" | "FIXED";
type DraftPromotion = {
  promotionId: string | null;
  code: string;
  startsAt: string;
  endsAt: string;
  maximumUses: string;
  discount: { kind: DiscountKind; amount: string | null; percent: number | null };
};
type PromotionFields = Omit<DraftPromotion, "discount"> & {
  key: string;
  kind: DiscountKind;
  value: string;
};

function toPromotionFields(promotions: DraftPromotion[]): PromotionFields[] {
  return promotions.map(({ discount, ...promotion }) => ({
    ...promotion,
    key: promotion.promotionId ?? crypto.randomUUID(),
    startsAt: localDateTime(promotion.startsAt),
    endsAt: localDateTime(promotion.endsAt),
    kind: discount.kind,
    value: String((discount.kind === "PERCENT" ? discount.percent : discount.amount) ?? ""),
  }));
}

function resolveWorkflowStep(
  rawStep: string | null,
  mode: OrganizerSummary["mode"]
): WorkflowStepId {
  if (rawStep === "map" || rawStep === "sectors") {
    return mode === "SEAT_MAP" ? "sectors" : "tickets";
  }
  if (rawStep === "pricing" || rawStep === "tickets") {
    return "tickets";
  }
  if (rawStep === "media" || rawStep === "review" || rawStep === "info") {
    return rawStep;
  }
  return "info";
}

function localDateTime(value?: string) {
  if (!value) return "";
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
    .toISOString()
    .slice(0, 23);
}

function EventDraftForm({
  initialEvent,
  queryKey,
}: {
  initialEvent?: OwnerDraft;
  queryKey: string[];
}) {
  const { t, i18n } = useTranslation();
  const numberLocale = i18n.language?.startsWith("en") ? "en-US" : "vi-VN";
  const client = useQueryClient();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const rawStepParam = searchParams.get("step");

  const [mode, setMode] = useState<OrganizerSummary["mode"]>(
    initialEvent?.mode ?? "QUANTITY"
  );

  const [activeStep, setActiveStep] = useState<WorkflowStepId>(() =>
    resolveWorkflowStep(rawStepParam, initialEvent?.mode ?? "QUANTITY")
  );

  useEffect(() => {
    setActiveStep(resolveWorkflowStep(rawStepParam, mode));
  }, [rawStepParam, mode]);

  const nextStepAfterSave = useRef<WorkflowStepId | null>(null);

  const handleStepChange = (newStep: WorkflowStepId) => {
    const resolved = mode === "QUANTITY" && newStep === "sectors" ? "tickets" : newStep;
    setActiveStep(resolved);
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set("step", resolved);
      return next;
    });
  };
  const [ticketIds, setTicketIds] = useState<string[]>(() =>
    initialEvent
      ? initialEvent.ticketTypes.map((ticket) => ticket.ticketTypeId)
      : []
  );
  const [tierColors, setTierColors] = useState<Record<string, string>>({});
  const [event, setEvent] = useState(initialEvent);
  const [promotions, setPromotions] = useState(() => toPromotionFields(initialEvent?.promotions ?? []));
  const [promotionsDirty, setPromotionsDirty] = useState(false);

  function updatePromotion(key: string, fields: Partial<PromotionFields>) {
    setPromotions((items) => items.map((item) => item.key === key ? { ...item, ...fields } : item));
    setPromotionsDirty(true);
  }
  const buildSectorPricingInputs = (
    saved?: OwnerDraft | null
  ): Record<string, SectorTicketDraft[]> => {
    const nextMap: Record<string, SectorTicketDraft[]> = {};
    for (const sector of saved?.seatMap?.sectors ?? []) {
      const matched = (saved?.ticketTypes ?? []).filter(
        (tt) => tt.sectorId === sector.sectorId
      );
      if (matched.length > 0) {
        nextMap[sector.sectorId] = matched.map((tt, idx) => ({
          localKey: tt.ticketTypeId,
          ticketTypeId: tt.ticketTypeId,
          name: tt.name,
          unitPrice: tt.unitPrice ?? "",
          colorCode:
            tt.colorCode && /^#[0-9a-fA-F]{6}$/.test(tt.colorCode)
              ? tt.colorCode
              : sector.colorCode && /^#[0-9a-fA-F]{6}$/.test(sector.colorCode)
              ? sector.colorCode
              : SECTOR_TICKET_PALETTE[idx % SECTOR_TICKET_PALETTE.length],
        }));
      } else {
        nextMap[sector.sectorId] = [
          {
            localKey: crypto.randomUUID(),
            ticketTypeId: null,
            name: sector.name,
            unitPrice: "",
            colorCode:
              sector.colorCode && /^#[0-9a-fA-F]{6}$/.test(sector.colorCode)
                ? sector.colorCode
                : SECTOR_TICKET_PALETTE[0],
          },
        ];
      }
    }
    return nextMap;
  };
  const [sectorPricingInputs, setSectorPricingInputs] = useState<
    Record<string, SectorTicketDraft[]>
  >(() => buildSectorPricingInputs(initialEvent));
  const [sectorPricingDirty, setSectorPricingDirty] = useState(false);
  const [selectedCategoryId, setSelectedCategoryId] = useState<string>(
    initialEvent?.categoryId ?? ""
  );
  const [selectedVenueId, setSelectedVenueId] = useState<string>(
    initialEvent?.venueId ?? ""
  );
  const [formRevision, setFormRevision] = useState(0);
  const [dirty, setDirty] = useState(false);
  const eventPath = event
    ? "/api/v1/organizer/events/" + encodeURIComponent(event.eventId)
    : null;
  const [mediaBusy, setMediaBusy] = useState(false);
  const [error, setError] = useState("");
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);

  const options = useQuery({
    queryKey: ["event-draft-references"],
    queryFn: async () => {
      const [categories, venues] = await Promise.all([
        categoryService.getCategories(),
        venueService.getVenues(),
      ]);
      return { categories, venues };
    },
    retry: false,
  });

  const selectedCategoryName = options.data?.categories.find(
    (c) => c.id === selectedCategoryId
  )?.name;
  const selectedVenue = options.data?.venues.find(
    (v) => v.id === selectedVenueId
  );
  const selectedVenueName = selectedVenue?.name;

  function syncSectorInputs(saved: OwnerDraft) {
    setSectorPricingInputs(buildSectorPricingInputs(saved));
    setSectorPricingDirty(false);
  }

  function update(saved: OwnerDraft) {
    setEvent(saved);
    syncSectorInputs(saved);
    client.setQueryData([...queryKey, saved.eventId], saved);
    void client.invalidateQueries({ queryKey: ["organizer-event-list"] });
    void client.invalidateQueries({ queryKey: ["organizer-events-list-complete"] });
    void client.invalidateQueries({ queryKey: ["admin-event-review"] });
  }

  function resetForm(saved: OwnerDraft) {
    update(saved);
    setMode(saved.mode);
    setSelectedCategoryId(saved.categoryId ?? "");
    setSelectedVenueId(saved.venueId ?? "");
    setTicketIds(saved.ticketTypes.map((ticket) => ticket.ticketTypeId));
    setTierColors({});
    setFormRevision((value) => value + 1);
    setDirty(false);
    setError("");
  }

  const saveDraft = useMutation({
    mutationFn: async ({ draft, base }: { draft: object; base?: OwnerDraft }) => {
      if (base && eventPath) {
        return (
          await axiosClient.put<OwnerDraft>(eventPath, {
            rowVersion: base.rowVersion,
            draft,
          })
        ).data;
      }
      return (await axiosClient.post<OwnerDraft>("/api/v1/organizer/events", draft)).data;
    },
    onSuccess: (saved, { base }) => {
      resetForm(saved);
      setPromotions(toPromotionFields(saved.promotions));
      setPromotionsDirty(false);
      if (!base) {
        navigate(`/organizer/events/${saved.eventId}/edit?step=media`, {
          replace: true,
        });
      } else if (nextStepAfterSave.current) {
        handleStepChange(nextStepAfterSave.current);
        nextStepAfterSave.current = null;
      }
    },
    onError: () =>
      setError(
        t(
          "organizerEventCreate.saveError",
          "Không lưu được bản nháp. Hãy kiểm tra dữ liệu và thử lại."
        )
      ),
  });

  const saveSectorPricing = useMutation({
    mutationFn: async (
      ticketTypes: {
        ticketTypeId: string | null;
        sectorId: string;
        name: string;
        unitPrice: string;
        currency: string;
        colorCode: string | null;
      }[]
    ) => {
      if (!event || !eventPath) throw new Error("Missing event");
      return (
        await axiosClient.put<OwnerDraft>(`${eventPath}/ticket-types`, {
          rowVersion: event.rowVersion,
          ticketTypes,
        })
      ).data;
    },
    onSuccess: (saved) => {
      resetForm(saved);
      toast.success(
        t(
          "organizerEventCreate.sectorPricingSaved",
          "Đã lưu cấu hình loại vé theo khu vực."
        )
      );
    },
    onError: (err) =>
      setError(
        extractApiErrorMessage(
          err,
          t(
            "organizerEventCreate.sectorPricingError",
            "Không lưu được loại vé theo khu vực. Hãy kiểm tra giá vé (VNĐ nguyên dương) và thử lại."
          )
        )
      ),
  });

  async function handleSaveSectorPricing(): Promise<OwnerDraft | null> {
    if (!event || !event.seatMap?.sectors?.length || busy) return null;
    const priceRegex = new RegExp(`^(?:${eventTicketPricePattern})$`);
    const payload: {
      ticketTypeId: string | null;
      sectorId: string;
      name: string;
      unitPrice: string;
      currency: string;
      colorCode: string | null;
    }[] = [];

    for (const sector of event.seatMap.sectors) {
      const drafts = sectorPricingInputs[sector.sectorId] ?? [];
      if (!drafts.length) {
        setError(
          t(
            "organizerEventCreate.sectorTicketNameRequired",
            `Vui lòng tạo ít nhất 1 loại vé cho khu vực "${sector.name}".`
          )
        );
        return null;
      }

      for (const draft of drafts) {
        const nameVal = draft.name.trim();
        const priceVal = draft.unitPrice.trim();
        const colorVal = /^#[0-9a-fA-F]{6}$/.test(draft.colorCode.trim())
          ? draft.colorCode.trim()
          : sector.colorCode || "#14b8a6";

        if (!nameVal) {
          setError(
            t(
              "organizerEventCreate.sectorTicketNameRequired",
              `Vui lòng nhập tên hạng vé cho khu vực "${sector.name}".`
            )
          );
          return null;
        }
        if (!priceVal || !priceRegex.test(priceVal)) {
          setError(`${sector.name} (${nameVal}): ${eventTicketPriceHint}`);
          return null;
        }

        payload.push({
          ticketTypeId: draft.ticketTypeId,
          sectorId: sector.sectorId,
          name: nameVal,
          unitPrice: priceVal,
          currency: "VND",
          colorCode: colorVal,
        });
      }
    }

    setError("");
    try {
      return await saveSectorPricing.mutateAsync(payload);
    } catch {
      return null;
    }
  }

  const submit = useMutation({
    mutationFn: (draft: OwnerDraft) =>
      organizerDashboardService.transition(draft.eventId, "submit", draft.rowVersion),
    onSuccess: (saved, draft) => update({ ...draft, ...saved }),
    onError: () =>
      setError(
        t(
          "organizerEventCreate.submitError",
          "Chưa gửi duyệt được. Bản nháp đã lưu. Nếu phiên bản đã thay đổi, hãy mở lại danh sách sự kiện."
        )
      ),
  });

  function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    saveForm(new FormData(e.currentTarget), event);
  }

  function saveForm(values: FormData, base?: OwnerDraft) {
    if (busy) return;
    const read = (key: string) => String(values.get(key) ?? "").trim();

    const failValidation = (step: WorkflowStepId, message: string) => {
      nextStepAfterSave.current = null;
      setError(message);
      if (activeStep !== step) {
        handleStepChange(step);
      }
    };

    const title = read("title");
    if (!title) {
      failValidation(
        "info",
        t("organizerEventCreate.titleRequired", "Vui lòng nhập tên sự kiện.")
      );
      return;
    }

    const venueId = read("venueId");
    if (!venueId) {
      failValidation(
        "info",
        t("organizerEventCreate.venueRequired", "Vui lòng chọn địa điểm tổ chức.")
      );
      return;
    }

    const description = read("description");
    if (!description) {
      failValidation(
        "info",
        t("organizerEventCreate.descriptionRequired", "Vui lòng nhập mô tả chi tiết.")
      );
      return;
    }

    const start = new Date(read("startsAt"));
    const end = new Date(read("endsAt"));
    const saleStart = new Date(read("saleStartsAt"));
    const saleEnd = new Date(read("saleEndsAt"));

    if (
      Number.isNaN(start.getTime()) ||
      Number.isNaN(end.getTime()) ||
      Number.isNaN(saleStart.getTime()) ||
      Number.isNaN(saleEnd.getTime()) ||
      !(start < end && saleStart < saleEnd && saleEnd <= end)
    ) {
      failValidation(
        "info",
        t(
          "organizerEventCreate.timeValidationError",
          "Giờ bắt đầu phải trước giờ kết thúc sự kiện. Giờ kết thúc bán vé không được sau khi sự kiện kết thúc."
        )
      );
      return;
    }

    const purchaseLimitRaw = read("purchaseLimit");
    const purchaseLimit = Number(purchaseLimitRaw);
    if (
      !/^[1-9][0-9]*$/.test(purchaseLimitRaw) ||
      !Number.isSafeInteger(purchaseLimit) ||
      purchaseLimit > 2147483647
    ) {
      failValidation(
        "info",
        t(
          "organizerEventCreate.purchaseLimitInvalid",
          "Giới hạn vé mỗi người mua phải là số nguyên dương."
        )
      );
      return;
    }

    const ticketTypes: {
      ticketTypeId: string | null;
      name: string;
      unitPrice: string;
      configuredQuantity: string;
      currency: string;
      colorCode: string;
    }[] = [];

    if (mode === "QUANTITY") {
      if (activeStep === "tickets" && ticketIds.length === 0) {
        failValidation(
          "tickets",
          t(
            "organizerEventCreate.ticketsRequired",
            "Vui lòng thêm ít nhất 1 hạng vé và nhập đầy đủ tên, giá vé và số lượng."
          )
        );
        return;
      }

      const priceRegex = new RegExp(`^(?:${eventTicketPricePattern})$`);

      for (let index = 0; index < ticketIds.length; index += 1) {
        const id = ticketIds[index];
        const existingTicket = event?.ticketTypes.find(
          (ticket) => ticket.ticketTypeId === id
        );
        const nameVal = read(`name-${id}`);
        const priceVal = read(`price-${id}`);
        const qtyVal = read(`quantity-${id}`);

        if (!nameVal) {
          failValidation(
            "tickets",
            t("organizerEventCreate.ticketNameRequired", {
              index: index + 1,
              defaultValue: `Hạng vé #${index + 1}: Tên loại vé không được để trống.`,
            })
          );
          return;
        }

        if (!priceVal || !priceRegex.test(priceVal)) {
          failValidation(
            "tickets",
            t("organizerEventCreate.ticketPriceInvalid", {
              index: index + 1,
              name: nameVal,
              defaultValue: `Hạng vé #${index + 1} (${nameVal}): ${eventTicketPriceHint}`,
            })
          );
          return;
        }

        const qtyRegex =
          existingTicket?.configuredQuantity === "0"
            ? /^(?:0|[1-9][0-9]*)$/
            : /^[1-9][0-9]*$/;
        if (!qtyVal || !qtyRegex.test(qtyVal)) {
          failValidation(
            "tickets",
            t("organizerEventCreate.ticketQuantityInvalid", {
              index: index + 1,
              name: nameVal,
              defaultValue: `Hạng vé #${index + 1} (${nameVal}): Vui lòng nhập số lượng vé nguyên dương.`,
            })
          );
          return;
        }

        ticketTypes.push({
          ticketTypeId: existingTicket?.ticketTypeId ?? null,
          name: nameVal,
          unitPrice: priceVal,
          configuredQuantity: qtyVal,
          currency: "VND",
          colorCode: read(`color-${id}`) || "#2563eb",
        });
      }
    }

    const voucherPayload: DraftPromotion[] = [];
    const codes = new Set<string>();
    if (promotionsDirty) {
      for (const item of promotions) {
        const code = item.code.trim().toUpperCase();
        const startsAt = new Date(item.startsAt);
        const endsAt = new Date(item.endsAt);
        const percent = Number(item.value);
        const validDiscount = item.kind === "PERCENT"
          ? /^[1-9][0-9]{0,2}$/.test(item.value) && percent <= 100
          : /^[1-9][0-9]{0,18}$/.test(item.value);
        if (!code || codes.has(code) || !(startsAt < endsAt) || !validDiscount ||
            !/^[1-9][0-9]{0,18}$/.test(item.maximumUses) ||
            BigInt(item.maximumUses) > 9223372036854775807n) {
          failValidation("tickets", t("organizerEventCreate.invalidPromotion", "Kiểm tra mã không trùng, thời hạn, mức giảm và giới hạn lượt dùng."));
          return;
        }
        codes.add(code);
        voucherPayload.push({
          promotionId: item.promotionId,
          code,
          startsAt: startsAt.toISOString(),
          endsAt: endsAt.toISOString(),
          maximumUses: item.maximumUses,
          discount: item.kind === "PERCENT"
            ? { kind: "PERCENT", percent, amount: null }
            : { kind: "FIXED", percent: null, amount: item.value },
        });
      }
    }

    setError("");
    const draft = {
      title,
      description,
      categoryId: read("categoryId") || null,
      venueId,
      mode,
      purchaseLimit,
      startsAt: start.toISOString(),
      endsAt: end.toISOString(),
      saleStartsAt: saleStart.toISOString(),
      saleEndsAt: saleEnd.toISOString(),
      ticketTypes,
      images: event?.images.map(({ imageId, objectReference, displayOrder }) => ({
        imageId, objectReference, displayOrder,
      })) ?? [],
      promotions: promotionsDirty ? voucherPayload : base?.promotions ?? [],
    };
    saveDraft.mutate({ draft, base });
  }

  const refresh = useMutation({
    mutationFn: async (path: string) => (await axiosClient.get<OwnerDraft>(path)).data,
    onSuccess: (saved) => {
      resetForm(saved);
      setPromotions(toPromotionFields(saved.promotions));
      setPromotionsDirty(false);
    },
    onError: () =>
      setError(
        t("organizerEventCreate.reloadError", "Không tải lại được dữ liệu bản nháp.")
      ),
  });

  const banner = event?.images.find(image => image.role === "BANNER");
  const gallery = useMemo(
    () =>
      (event?.images ?? [])
        .filter((image) => image.role === "GALLERY")
        .slice()
        .sort((a, b) => a.displayOrder - b.displayOrder),
    [event?.images]
  );
  const sectors = event?.seatMap?.sectors ?? [];
  const totalSectorCapacity = useMemo(
    () =>
      sectors.reduce(
        (sum, sector) => sum + (Number(sector.configuredCapacity) || 0),
        0
      ),
    [sectors]
  );
  const totalTicketsCount = useMemo(
    () =>
      mode === "SEAT_MAP"
        ? totalSectorCapacity
        : (event?.ticketTypes ?? []).reduce(
            (sum, tier) => sum + (Number(tier.configuredQuantity) || 0),
            0
          ),
    [event?.ticketTypes, mode, totalSectorCapacity]
  );
  const seatMapConfigured = useMemo(() => {
    if (mode !== "SEAT_MAP") return true;
    if (!sectors.length) return false;
    return sectors.every((sector) => {
      const types = (event?.ticketTypes ?? []).filter(
        (ticket) => ticket.sectorId === sector.sectorId
      );
      if (!types.length) return false;
      if (sector.kind === "STANDING") return true;
      const visibleSeats = (sector.seats ?? []).filter((s) => !s.isHidden);
      return (
        visibleSeats.length > 0 &&
        visibleSeats.every((seat) =>
          types.some((ticket) => ticket.ticketTypeId === seat.ticketTypeId)
        )
      );
    });
  }, [event?.ticketTypes, mode, sectors]);
  const seatMapPreviewUrl = useMemo(() => {
    if (mode !== "SEAT_MAP" || !event?.seatMap) {
      return null;
    }
    try {
      const editorMap = editorSeatMap(event as unknown as Parameters<typeof editorSeatMap>[0]);
      const doc = buildSeatMapEditorDocument(editorMap);
      if (!doc) {
        return null;
      }
      const svg = exportEditorDocumentToSvg(doc);
      return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
    } catch {
      return null;
    }
  }, [event, mode]);
  const reviewLightboxSlides = useMemo(
    () => [
      ...gallery.map((image, index) => ({
        src: image.objectReference,
        alt: `${event?.title || "Event"} #${index + 1}`,
      })),
      ...(seatMapPreviewUrl
        ? [
            {
              src: seatMapPreviewUrl,
              alt: `${event?.title || "Event"} - Sơ đồ ghế`,
            },
          ]
        : []),
    ],
    [event?.title, gallery, seatMapPreviewUrl]
  );
  const formatReviewDateTime = (value?: string) => {
    if (!value) return "—";
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return "—";
    return parsed.toLocaleString(numberLocale, {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  };
  const busy =
    saveDraft.isPending ||
    saveSectorPricing.isPending ||
    submit.isPending ||
    mediaBusy ||
    refresh.isPending;

  // Trường hợp sự kiện không ở trạng thái nháp DRAFT
  if (event && event.status !== "DRAFT") {
    const isPendingApproval = event.status === "PENDING_APPROVAL";
    return (
      <div className="tix-form-flow">
        <TixEventWorkflowStepper
          currentStep="review"
          mode={mode}
          eventId={event.eventId}
          hasImages={Boolean(event.images && event.images.length > 0)}
          hasSectors={sectors.length > 0}
          hasTickets={
            mode === "SEAT_MAP"
              ? seatMapConfigured
              : Boolean(event.ticketTypes && event.ticketTypes.length > 0)
          }
          allCompleted
        />
        <div className="tix-form-card tix-submitted-card">
          <h2 className="tix-submitted-title">{event.title}</h2>
          <p className="tix-submitted-desc">
            {isPendingApproval
              ? t(
                  "organizerEventCreate.submittedReviewNotice",
                  "Sự kiện đã được gửi kiểm duyệt. Đội ngũ kiểm duyệt sẽ xử lý trong vòng 24 giờ làm việc."
                )
              : t(
                  "organizerEventCreate.nonDraftNotice",
                  "Chỉ chỉnh sửa được sự kiện đang ở trạng thái bản nháp."
                )}
          </p>

          <div className="tix-submitted-actions">
            <Link className="tix-btn-primary" to="/organizer/events">
              {t("organizerEventCreate.backToList", "Về danh sách sự kiện")}
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <>
      {options.isPending && (
        <div className="tix-form-card" style={{ alignItems: "center", padding: "40px" }}>
          <Loader2 className="tix-spin-icon" size={28} />
          <span style={{ fontSize: "14px", color: "var(--text-muted, #666b74)", marginTop: "8px" }}>
            {t("organizerEventCreate.loadingReferences", "Đang tải danh mục và địa điểm…")}
          </span>
        </div>
      )}

      {options.isError && (
        <div className="tix-form-alert-error" role="alert">
          <AlertCircle size={18} />
          <span>
            {t(
              "organizerEventCreate.errorLoadingReferences",
              "Không tải được danh mục hoặc địa điểm tổ chức."
            )}
          </span>
          <button
            className="tix-btn-retry"
            type="button"
            onClick={() => void options.refetch()}
          >
            {t("common.retry", "Thử lại")}
          </button>
        </div>
      )}

      {options.data && (
        <div className="tix-form-flow">
          <TixEventWorkflowStepper
            currentStep={activeStep}
            mode={mode}
            eventId={event?.eventId}
            hasImages={Boolean(event?.images && event.images.length > 0)}
            hasSectors={sectors.length > 0}
            hasTickets={
              mode === "SEAT_MAP"
                ? seatMapConfigured
                : Boolean(event?.ticketTypes && event.ticketTypes.length > 0)
            }
            onStepClick={handleStepChange}
          />

          <form
            key={formRevision}
            className="tix-form-flow"
            noValidate
            onChange={() => setDirty(true)}
            onSubmit={save}
          >
            {/* ============================================================
                BƯỚC 1: THÔNG TIN SỰ KIỆN (STEP 1: EVENT INFO)
                ============================================================ */}
            <div
              className="tix-step-container"
              style={{
                display: activeStep === "info" ? "flex" : "none",
                flexDirection: "column",
                gap: "24px",
              }}
            >
              {/* Card 1: Hình thức phát hành vé (Ticket Setup Mode) */}
              <div className="tix-form-card">
              <div className="tix-form-card-header">
                <div className="tix-card-title-row">
                  <Layers size={20} className="tix-group-header-icon" />
                  <h3>{t("organizerEventCreate.modeSectionTitle", "Hình thức phát hành vé")}</h3>
                </div>
                <p className="tix-card-desc">
                  {t(
                    "organizerEventCreate.modeSectionSubtitle",
                    "Lựa chọn cách thức phát hành vé phù hợp với địa điểm và quy mô sự kiện của bạn."
                  )}
                </p>
              </div>

            <div className="tix-mode-grid">
              {/* Option 1: Vé số lượng (QUANTITY) */}
              <div
                className={`tix-mode-card ${mode === "QUANTITY" ? "is-selected" : ""} ${
                  Boolean(event) ? "is-disabled" : ""
                }`}
                onClick={() => {
                  if (!event) {
                    setMode("QUANTITY");
                    setDirty(true);
                  }
                }}
                role="radio"
                aria-checked={mode === "QUANTITY"}
                tabIndex={event ? -1 : 0}
              >
                <div className="tix-mode-icon-circle">
                  <Ticket size={24} />
                </div>
                <div className="tix-mode-content">
                  <div className="tix-mode-title-row">
                    <span className="tix-mode-title">
                      {t("organizerEventCreate.modeQuantityTitle", "Vé số lượng")}
                    </span>
                    <div
                      className={`tix-mode-radio-circle ${
                        mode === "QUANTITY" ? "is-checked" : ""
                      }`}
                    >
                      {mode === "QUANTITY" && <Check size={12} strokeWidth={3} />}
                    </div>
                  </div>
                  <p className="tix-mode-desc">
                    {t(
                      "organizerEventCreate.modeQuantityDesc",
                      "Phù hợp cho sự kiện vé vào cổng, đứng tự do, hòa nhạc ngoài trời, hội thảo không cần chia chỗ ngồi cụ thể."
                    )}
                  </p>
                </div>
              </div>

              {/* Option 2: Sơ đồ ghế (SEAT_MAP) */}
              <div
                className={`tix-mode-card ${mode === "SEAT_MAP" ? "is-selected" : ""} ${
                  Boolean(event) ? "is-disabled" : ""
                }`}
                onClick={() => {
                  if (!event) {
                    setMode("SEAT_MAP");
                    setDirty(true);
                  }
                }}
                role="radio"
                aria-checked={mode === "SEAT_MAP"}
                tabIndex={event ? -1 : 0}
              >
                <div className="tix-mode-icon-circle">
                  <Armchair size={24} />
                </div>
                <div className="tix-mode-content">
                  <div className="tix-mode-title-row">
                    <span className="tix-mode-title">
                      {t("organizerEventCreate.modeSeatMapTitle", "Theo sơ đồ ghế")}
                    </span>
                    <div
                      className={`tix-mode-radio-circle ${
                        mode === "SEAT_MAP" ? "is-checked" : ""
                      }`}
                    >
                      {mode === "SEAT_MAP" && <Check size={12} strokeWidth={3} />}
                    </div>
                  </div>
                  <p className="tix-mode-desc">
                    {t(
                      "organizerEventCreate.modeSeatMapDesc",
                      "Phù hợp cho nhà hát, rạp hát, sân vận động, sự kiện có phân khu khán đài VIP/Thường và chọn từng ghế ngồi."
                    )}
                  </p>
                </div>
              </div>
            </div>

            {Boolean(event) && (
              <div className="tix-mode-locked-hint">
                <Lock size={14} />
                <span>
                  {t(
                    "organizerEventCreate.modeLockedNotice",
                    "Hình thức vé được cố định sau khi khởi tạo sự kiện để đảm bảo tính toàn vẹn của dữ liệu bán vé."
                  )}
                </span>
              </div>
            )}
          </div>

          {/* Card 2: Thông tin cơ bản sự kiện */}
          <div className="tix-form-card">
            <div className="tix-form-card-header">
              <div className="tix-card-title-row">
                <FileText size={20} className="tix-group-header-icon" />
                <h3>{t("organizerEventCreate.infoSectionTitle", "Thông tin sự kiện")}</h3>
              </div>
              <p className="tix-card-desc">
                {t(
                  "organizerEventCreate.infoSectionSubtitle",
                  "Điền thông tin chi tiết để khán giả nắm rõ nội dung và địa điểm tổ chức."
                )}
              </p>
            </div>

            <div className="tix-form-grid">
              <div className="tix-field-wrap tix-form-span-2">
                <label className="tix-field-label">
                  {t("organizerEventCreate.fieldEventName", "Tên sự kiện")}
                  <span className="required">*</span>
                </label>
                <input
                  className="tix-input"
                  name="title"
                  defaultValue={event?.title}
                  placeholder={t(
                    "organizerEventCreate.placeholderEventName",
                    "Nhập tên sự kiện nổi bật..."
                  )}
                  required
                  disabled={busy}
                />
              </div>

              <div className="tix-field-wrap">
                <label className="tix-field-label">
                  {t("organizerEventCreate.fieldCategory", "Danh mục sự kiện")}
                  <span className="required">*</span>
                </label>
                <TixSelect
                  name="categoryId"
                  value={selectedCategoryId}
                  onChange={(val) => {
                    setSelectedCategoryId(val);
                    setDirty(true);
                  }}
                  options={options.data.categories.map((item) => ({
                    value: item.id,
                    label: item.name,
                  }))}
                  placeholder={t(
                    "organizerEventCreate.selectCategoryPlaceholder",
                    "Chọn danh mục"
                  )}
                  disabled={busy}
                  required
                />
              </div>

              <div className="tix-field-wrap">
                <label className="tix-field-label">
                  {t("organizerEventCreate.fieldVenue", "Địa điểm tổ chức")}
                  <span className="required">*</span>
                </label>
                <TixSelect
                  name="venueId"
                  value={selectedVenueId}
                  onChange={(val) => {
                    setSelectedVenueId(val);
                    setDirty(true);
                  }}
                  options={options.data.venues.map((item) => ({
                    value: item.id,
                    label: item.name,
                  }))}
                  placeholder={t(
                    "organizerEventCreate.selectVenuePlaceholder",
                    "Chọn địa điểm"
                  )}
                  disabled={busy}
                  required
                />
              </div>

              <div className="tix-field-wrap tix-form-span-2">
                <label className="tix-field-label">
                  {t("organizerEventCreate.fieldDescription", "Mô tả chi tiết")}{" "}
                  <span className="required">*</span>
                </label>
                <OrganizerDescriptionEditor initialValue={event?.description ?? ""}
                  disabled={busy} onChange={() => setDirty(true)} />
              </div>
            </div>
          </div>

          {/* Card 3: Thời gian diễn ra & Mở bán vé */}
          <div className="tix-form-card">
            <div className="tix-form-card-header">
              <div className="tix-card-title-row">
                <Calendar size={20} className="tix-group-header-icon" />
                <h3>{t("organizerEventCreate.scheduleSectionTitle", "Lịch trình & Bán vé")}</h3>
              </div>
              <p className="tix-card-desc">
                {t(
                  "organizerEventCreate.scheduleSectionSubtitle",
                  "Cấu hình khung giờ tổ chức và thời hạn mở bán vé cho khán giả."
                )}
              </p>
            </div>

            <div className="tix-form-grid">
              {/* Nhóm thời gian sự kiện */}
              <div className="tix-schedule-subgroup">
                <span className="tix-schedule-subgroup-title">
                  <Clock size={15} />
                  <span>
                    {t(
                      "organizerEventCreate.subgroupEventTimeline",
                      "Thời gian diễn ra sự kiện"
                    )}
                  </span>
                </span>
                <div className="tix-field-wrap">
                  <label className="tix-field-label">
                    {t("organizerEventCreate.fieldStartsAt", "Bắt đầu sự kiện")}
                    <span className="required">*</span>
                  </label>
                  <input
                    className="tix-input"
                    name="startsAt"
                    type="datetime-local"
                    step="1"
                    defaultValue={localDateTime(event?.startsAt)}
                    required
                    disabled={busy}
                  />
                </div>
                <div className="tix-field-wrap">
                  <label className="tix-field-label">
                    {t("organizerEventCreate.fieldEndsAt", "Kết thúc sự kiện")}
                    <span className="required">*</span>
                  </label>
                  <input
                    className="tix-input"
                    name="endsAt"
                    type="datetime-local"
                    step="1"
                    defaultValue={localDateTime(event?.endsAt)}
                    required
                    disabled={busy}
                  />
                </div>
              </div>

              {/* Nhóm thời gian bán vé */}
              <div className="tix-schedule-subgroup">
                <span className="tix-schedule-subgroup-title">
                  <Clock size={15} />
                  <span>
                    {t(
                      "organizerEventCreate.subgroupSalesTimeline",
                      "Thời gian mở bán vé"
                    )}
                  </span>
                </span>
                <div className="tix-field-wrap">
                  <label className="tix-field-label">
                    {t("organizerEventCreate.fieldSaleStartsAt", "Bắt đầu bán vé")}
                    <span className="required">*</span>
                  </label>
                  <input
                    className="tix-input"
                    name="saleStartsAt"
                    type="datetime-local"
                    step="1"
                    defaultValue={localDateTime(event?.saleStartsAt)}
                    required
                    disabled={busy}
                  />
                </div>
                <div className="tix-field-wrap">
                  <label className="tix-field-label">
                    {t("organizerEventCreate.fieldSaleEndsAt", "Kết thúc bán vé")}
                    <span className="required">*</span>
                  </label>
                  <input
                    className="tix-input"
                    name="saleEndsAt"
                    type="datetime-local"
                    step="1"
                    defaultValue={localDateTime(event?.saleEndsAt)}
                    required
                    disabled={busy}
                  />
                </div>
              </div>

              <div className="tix-field-wrap tix-form-span-2">
                <label className="tix-field-label">
                  {t("organizerEventCreate.fieldPurchaseLimit", "Giới hạn vé mỗi người mua")}
                  <span className="required">*</span>
                  <span className="tix-field-hint">
                    {t(
                      "organizerEventCreate.purchaseLimitHint",
                      "(Số vé tối đa cho một đơn hàng, mặc định: 4 vé)"
                    )}
                  </span>
                </label>
                <input
                  className="tix-input"
                  name="purchaseLimit"
                  defaultValue={event?.purchaseLimit ?? 4}
                  type="number"
                  min={1}
                  max={2147483647}
                  step={1}
                  required
                  disabled={busy}
                />
                <p className="tix-form-notice" style={{ marginTop: "4px" }}>
                  {t(
                    "organizerEventCreate.timezoneNotice",
                    "* Mọi khung giờ được hiển thị theo múi giờ trên thiết bị hiện tại của bạn."
                  )}
                </p>
              </div>
            </div>
          </div>

          {error && activeStep === "info" && (
            <div className="tix-form-alert-error" role="alert">
              <AlertCircle size={18} />
              <span>{error}</span>
            </div>
          )}

          {/* Form Actions Footer Bar - Step 1 */}
          <div className="tix-form-actions-bar">
            <Link className="tix-btn-secondary" to="/organizer/events">
              {t("common.cancel", "Hủy bỏ")}
            </Link>
            <button
              type="submit"
              className="tix-btn-primary"
              disabled={busy || options.data.venues.length === 0}
              onClick={() => {
                nextStepAfterSave.current = "media";
              }}
            >
              {saveDraft.isPending ? (
                <>
                  <Loader2 size={16} className="tix-spin-icon" />
                  <span>{t("common.saving", "Đang lưu…")}</span>
                </>
              ) : (
                <>
                  <span>
                    {event
                      ? t("organizerStepper.btnSaveAndContinue", "Lưu & Tiếp tục")
                      : t("organizerStepper.btnSaveAndContinue", "Lưu & Tiếp tục")}
                  </span>
                  <ArrowRight size={16} />
                </>
              )}
            </button>
          </div>
        </div>

        {/* ============================================================
            BƯỚC 2: HÌNH ẢNH & BANNER (STEP 2: MEDIA)
            ============================================================ */}
        <div
          className="tix-step-container"
          style={{
            display: activeStep === "media" ? "flex" : "none",
            flexDirection: "column",
            gap: "24px",
          }}
        >
          {event && eventPath ? (
            <>
              <div className="tix-form-card">
                <OrganizerDraftMedia
                  event={event}
                  onSaved={update}
                  onBusy={setMediaBusy}
                  disabled={busy}
                  onRefresh={() => refresh.mutate(eventPath)}
                />
              </div>

              <div className="tix-form-actions-bar">
                <button
                  type="button"
                  className="tix-btn-secondary"
                  onClick={() => handleStepChange("info")}
                >
                  <ArrowLeft size={16} />
                  <span>{t("organizerStepper.btnBack", "Quay lại")}</span>
                </button>

                <button
                  type="button"
                  className="tix-btn-primary"
                  onClick={() => handleStepChange(mode === "SEAT_MAP" ? "sectors" : "tickets")}
                >
                  <span>{t("organizerStepper.btnContinue", "Tiếp tục")}</span>
                  <ArrowRight size={16} />
                </button>
              </div>
            </>
          ) : (
            <div className="tix-form-card" style={{ textAlign: "center", padding: "40px" }}>
              <p style={{ color: "var(--text-muted, #666b74)", fontSize: "14.5px", margin: "0 0 16px 0" }}>
                {t(
                  "organizerStepper.saveFirstForMedia",
                  "Vui lòng lưu thông tin sự kiện ở Bước 1 trước khi tải ảnh lên."
                )}
              </p>
              <button
                type="button"
                className="tix-btn-primary"
                onClick={() => handleStepChange("info")}
              >
                <ArrowLeft size={16} />
                <span>{t("organizerStepper.btnBack", "Quay lại Bước 1")}</span>
              </button>
            </div>
          )}
        </div>

        {/* ============================================================
            BƯỚC 3 (SEAT_MAP): SƠ ĐỒ KHU VỰC (STEP 3: SECTORS)
            ============================================================ */}
        {mode === "SEAT_MAP" && (
          <div
            className="tix-step-container"
            style={{
              display: activeStep === "sectors" ? "flex" : "none",
              flexDirection: "column",
              gap: "24px",
            }}
          >
            <div className="tix-form-card">
              <div className="tix-form-card-header is-between">
                <div>
                  <div className="tix-card-title-row">
                    <Layers size={20} className="tix-group-header-icon" />
                    <h3>{t("organizerSeatMap.step2SectorTitle", "Sơ đồ khu vực & Khán đài")}</h3>
                  </div>
                  <p className="tix-card-desc">
                    {t(
                      "organizerSeatMap.step2SectorDesc",
                      "Thiết lập các khu vực (khu ngồi có ghế hoặc khu đứng tự do) và bố trí vị trí khán đài trước khi định giá vé."
                    )}
                  </p>
                </div>

                {event && (
                  <button
                    type="button"
                    className="tix-btn-primary"
                    disabled={busy}
                    onClick={() =>
                      navigate(`/organizer/events/${event.eventId}/seat-map?phase=sectors`)
                    }
                  >
                    <PenTool size={16} />
                    <span>
                      {sectors.length > 0
                        ? t("organizerSeatMap.editSectorsBtn", "Chỉnh sửa sơ đồ khu vực")
                        : t("organizerSeatMap.openSectorBuilderBtn", "Mở trình vẽ khu vực")}
                    </span>
                  </button>
                )}
              </div>

              {sectors.length === 0 ? (
                <div className="tix-seatmap-empty-studio">
                  <div className="tix-seatmap-empty-main">
                    <h4 className="tix-seatmap-empty-title">
                      {t("organizerSeatMap.noSectorsTitle", "Bắt đầu thiết kế sơ đồ khu vực cho sự kiện")}
                    </h4>
                    <p className="tix-seatmap-empty-desc">
                      {t(
                        "organizerSeatMap.noSectorsSub",
                        "Sử dụng công cụ vẽ trực quan để bố trí sân khấu, tạo các phân khu khán đài và thiết lập quy mô sức chứa trước khi định giá vé."
                      )}
                    </p>
                  </div>

                  <div className="tix-seatmap-guide-list">
                    <div className="tix-seatmap-guide-card">
                      <span className="tix-seatmap-guide-icon">
                        <Armchair size={18} />
                      </span>
                      <div className="tix-seatmap-guide-text">
                        <strong>{t("organizerSeatMap.guideSeatedTitle", "Khán đài có ghế ngồi")}</strong>
                        <span>
                          {t(
                            "organizerSeatMap.guideSeatedDesc",
                            "Tự động sinh hàng ghế, đánh số thứ tự ghế và cho phép người mua chọn từng vị trí ngồi."
                          )}
                        </span>
                      </div>
                    </div>

                    <div className="tix-seatmap-guide-card">
                      <span className="tix-seatmap-guide-icon">
                        <Users size={18} />
                      </span>
                      <div className="tix-seatmap-guide-text">
                        <strong>{t("organizerSeatMap.guideStandingTitle", "Khu vực đứng tự do")}</strong>
                        <span>
                          {t(
                            "organizerSeatMap.guideStandingDesc",
                            "Giới hạn theo tổng sức chứa của phân khu (phù hợp cho Fanzone, GA, khu đứng quanh sân khấu)."
                          )}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
                  <div className="tix-seatmap-summary-bar">
                    <span>
                      {t("organizerSeatMap.sectorSummaryCount", {
                        count: sectors.length,
                        defaultValue: `Đã cấu hình ${sectors.length} khu vực`,
                      })}
                    </span>
                    <span>
                      {t("organizerSeatMap.sectorSummaryCapacity", {
                        capacity: totalSectorCapacity.toLocaleString(numberLocale),
                        defaultValue: `Tổng sức chứa: ${totalSectorCapacity.toLocaleString(numberLocale)} chỗ`,
                      })}
                    </span>
                  </div>

                  <div className="tix-review-tiers-list">
                    {sectors.map((sector) => {
                      const visibleSeatCount = sector.seats.filter((seat) => !seat.isHidden).length;
                      return (
                        <div key={sector.sectorId} className="tix-review-tier-row">
                          <div className="tix-review-tier-info">
                            <span
                              className="tix-review-tier-color"
                              style={{ backgroundColor: sector.colorCode || "#2563eb" }}
                            />
                            <span className="tix-review-tier-name">{sector.name}</span>
                            <span className="tix-review-tier-qty">
                              {sector.kind === "SEATED"
                                ? t("organizerSeatMap.sectorTypeSeated", "Khu ghế ngồi")
                                : t("organizerSeatMap.sectorTypeStanding", "Khu đứng tự do")}
                            </span>
                          </div>
                          <span className="tix-review-tier-price" style={{ fontSize: "13.5px" }}>
                            {sector.kind === "SEATED"
                              ? `${visibleSeatCount.toLocaleString(numberLocale)} ghế hiển thị (${Number(
                                  sector.configuredCapacity || 0
                                ).toLocaleString(numberLocale)} chỗ)`
                              : `${Number(sector.configuredCapacity || 0).toLocaleString(numberLocale)} chỗ đứng`}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>

            {error && (
              <div className="tix-form-alert-error" role="alert">
                <AlertCircle size={18} />
                <span>{error}</span>
              </div>
            )}

            <div className="tix-form-actions-bar">
              <button
                type="button"
                className="tix-btn-secondary"
                onClick={() => handleStepChange("media")}
              >
                <ArrowLeft size={16} />
                <span>{t("organizerStepper.btnBack", "Quay lại")}</span>
              </button>

              <button
                type="button"
                className="tix-btn-primary"
                disabled={sectors.length === 0}
                onClick={() => handleStepChange("tickets")}
              >
                <span>{t("organizerStepper.btnContinue", "Tiếp tục")}</span>
                <ArrowRight size={16} />
              </button>
            </div>
          </div>
        )}

        {/* ============================================================
            BƯỚC 3 (QUANTITY) / BƯỚC 4 (SEAT_MAP): HẠNG VÉ & GÁN GHẾ
            ============================================================ */}
        <div
          className="tix-step-container"
          style={{
            display: activeStep === "tickets" ? "flex" : "none",
            flexDirection: "column",
            gap: "24px",
          }}
        >
          {/* Card 4: Thiết lập hạng vé (Chỉ khi mode === "QUANTITY") */}
          {mode === "QUANTITY" && (
            <div className="tix-form-card">
              <div className="tix-form-card-header">
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", width: "100%" }}>
                  <div className="tix-card-title-row">
                    <Tag size={20} className="tix-group-header-icon" />
                    <h3>{t("organizerEventCreate.tiersSectionTitle", "Hạng vé & Giá bán")}</h3>
                  </div>
                  <button
                    type="button"
                    className="tix-btn-secondary"
                    disabled={busy}
                    onClick={() => {
                      setError("");
                      setDirty(true);
                      setTicketIds((ids) => [...ids, crypto.randomUUID()]);
                    }}
                  >
                    <Plus size={15} />
                    <span>
                      {t("organizerEventCreate.addTierBtn", "Thêm loại vé mới")}
                    </span>
                  </button>
                </div>
                <p className="tix-card-desc">
                  {t(
                    "organizerEventCreate.tiersSectionSubtitle",
                    "Thiết lập tên hạng vé, giá bán và số lượng vé phát hành tương ứng."
                  )}
                </p>
              </div>

              <div className="tix-tiers-list">
                {ticketIds.length === 0 ? (
                  <div className="tix-tier-card-item" style={{ alignItems: "center", textAlign: "center", padding: "28px 20px" }}>
                    <p className="tix-card-desc" style={{ margin: 0 }}>
                      {t(
                        "organizerEventCreate.emptyTiersPrompt",
                        "Chưa có hạng vé nào. Nhấn \"Thêm loại vé mới\" để nhập tên, giá bán và số lượng vé phát hành."
                      )}
                    </p>
                    <button
                      type="button"
                      className="tix-btn-secondary"
                      disabled={busy}
                      onClick={() => {
                        setError("");
                        setDirty(true);
                        setTicketIds([crypto.randomUUID()]);
                      }}
                    >
                      <Plus size={15} />
                      <span>{t("organizerEventCreate.addTierBtn", "Thêm loại vé mới")}</span>
                    </button>
                  </div>
                ) : (
                  ticketIds.map((id, index) => {
                  const ticket = event?.ticketTypes.find(
                    (type) => type.ticketTypeId === id
                  );
                  const tierColor = tierColors[id] ?? ticket?.colorCode ?? "#2563eb";
                  const hex = tierColor.replace("#", "");
                  const r = parseInt(hex.slice(0, 2), 16) || 0;
                  const g = parseInt(hex.slice(2, 4), 16) || 0;
                  const b = parseInt(hex.slice(4, 6), 16) || 0;
                  const isLightBg = (0.299 * r + 0.587 * g + 0.114 * b) / 255 > 0.65;
                  return (
                    <div key={id} className="tix-tier-card-item">
                      <div className="tix-tier-item-top">
                        <span
                          className="tix-tier-number-badge"
                          style={{
                            backgroundColor: tierColor,
                            color: isLightBg ? "#15171a" : "#ffffff",
                          }}
                        >
                          {t("organizerEventCreate.tierBadge", {
                            index: index + 1,
                            defaultValue: `Hạng vé #${index + 1}`,
                          })}
                        </span>
                        {ticketIds.length > 0 && (
                          <button
                            type="button"
                            className="tix-tier-delete-btn"
                            disabled={busy}
                            onClick={() => {
                              setError("");
                              setDirty(true);
                              setTicketIds((ids) => ids.filter((val) => val !== id));
                            }}
                            title={t("organizerEventCreate.deleteTierBtn", "Xóa hạng vé")}
                          >
                            <Trash2 size={14} />
                            <span>{t("common.delete", "Xóa")}</span>
                          </button>
                        )}
                      </div>

                      <div className="tix-tier-inputs-grid">
                        <div className="tix-field-wrap">
                          <label className="tix-field-label">
                            {t("organizerEventCreate.fieldTierName", "Tên loại vé")}
                            <span className="required">*</span>
                          </label>
                          <input
                            className="tix-input"
                            name={`name-${id}`}
                            defaultValue={ticket?.name}
                            placeholder={t(
                              "organizerEventCreate.placeholderTierName",
                              "Ví dụ: VIP, Tiêu chuẩn..."
                            )}
                            required
                            disabled={busy}
                          />
                        </div>

                        <div className="tix-field-wrap">
                          <label className="tix-field-label">
                            {t("organizerEventCreate.fieldTierPrice", "Giá vé (VNĐ)")}
                            <span className="required">*</span>
                          </label>
                          <input
                            className="tix-input"
                            name={`price-${id}`}
                            defaultValue={ticket?.unitPrice}
                            inputMode="numeric"
                            pattern={eventTicketPricePattern}
                            title={eventTicketPriceHint}
                            placeholder={t(
                              "organizerEventCreate.placeholderTierPrice",
                              "Ví dụ: 150000"
                            )}
                            required
                            disabled={busy}
                          />
                        </div>

                        <div className="tix-field-wrap">
                          <label className="tix-field-label">
                            {t("organizerEventCreate.fieldTierQuantity", "Số lượng")}
                            <span className="required">*</span>
                          </label>
                          <input
                            className="tix-input"
                            name={`quantity-${id}`}
                            defaultValue={ticket?.configuredQuantity ?? undefined}
                            inputMode="numeric"
                            pattern={
                              ticket?.configuredQuantity === "0"
                                ? "0|[1-9][0-9]*"
                                : "[1-9][0-9]*"
                            }
                            placeholder={t(
                              "organizerEventCreate.placeholderTierQuantity",
                              "Ví dụ: 100"
                            )}
                            required
                            disabled={busy}
                          />
                        </div>

                        <div className="tix-field-wrap">
                          <label className="tix-field-label">
                            {t("organizerEventCreate.fieldTierColor", "Màu sắc")}
                          </label>
                          <div className="tix-color-picker-wrap">
                            <input
                              className="tix-color-input"
                              name={`color-${id}`}
                              type="color"
                              defaultValue={ticket?.colorCode ?? "#2563eb"}
                              onChange={(e) => {
                                const next = e.currentTarget.value;
                                setTierColors((prev) => ({ ...prev, [id]: next }));
                                setDirty(true);
                              }}
                              disabled={busy}
                            />
                          </div>
                        </div>
                      </div>
                    </div>
                  );
                  })
                )}
              </div>
            </div>
          )}

          {/* Card cho mode SEAT_MAP: Bước 4 - Loại vé & Gán ghế */}
          {mode === "SEAT_MAP" && (
            <>
              {sectors.length === 0 ? (
                <div className="tix-form-card">
                  <div className="tix-form-card-header">
                    <div className="tix-card-title-row">
                      <Armchair size={20} className="tix-group-header-icon" />
                      <h3>{t("organizerSeatMap.step3PricingTitle", "Đặt giá vé & Gán ghế theo khu vực")}</h3>
                    </div>
                    <p className="tix-card-desc">
                      {t(
                        "organizerSeatMap.needSectorsFirst",
                        "Bạn cần thiết lập ít nhất một khu vực ở Bước 3 (Sơ đồ khu vực) trước khi tạo loại vé và gán ghế."
                      )}
                    </p>
                  </div>
                  <div>
                    <button
                      type="button"
                      className="tix-btn-primary"
                      onClick={() => handleStepChange("sectors")}
                    >
                      <ArrowLeft size={16} />
                      <span>{t("organizerSeatMap.backToSectorsBtn", "Quay lại Bước 3: Sơ đồ khu vực")}</span>
                    </button>
                  </div>
                </div>
              ) : (
                <>
                  {/* Card 1: Đặt giá vé theo từng khu vực */}
                  <div className="tix-form-card">
                    <div className="tix-form-card-header is-between">
                      <div>
                        <div className="tix-card-title-row">
                          <Ticket size={20} className="tix-group-header-icon" />
                          <h3>{t("organizerSeatMap.sectorPricingTitle", "1. Đặt loại vé & Giá vé theo khu vực")}</h3>
                        </div>
                        <p className="tix-card-desc">
                          {t(
                            "organizerSeatMap.sectorPricingDesc",
                            "Mỗi khu vực trên sơ đồ tương ứng với một hạng vé chuẩn. Sau khi lưu, bạn có thể gán hạng vé chi tiết cho từng ghế trên sơ đồ."
                          )}
                        </p>
                      </div>

                      <button
                        type="button"
                        className="tix-btn-primary"
                        disabled={busy}
                        onClick={() => void handleSaveSectorPricing()}
                      >
                        {saveSectorPricing.isPending ? (
                          <>
                            <Loader2 size={16} className="tix-spin-icon" />
                            <span>{t("common.saving", "Đang lưu…")}</span>
                          </>
                        ) : (
                          <>
                            <CheckCircle2 size={16} />
                            <span>{t("organizerSeatMap.saveSectorPricingBtn", "Lưu loại vé theo khu")}</span>
                          </>
                        )}
                      </button>
                    </div>

                    <div className="tix-tiers-list">
                      {sectors.map((sector, index) => {
                        const drafts = sectorPricingInputs[sector.sectorId] ?? [
                          {
                            localKey: `${sector.sectorId}-default`,
                            ticketTypeId: null,
                            name: sector.name,
                            unitPrice: "",
                            colorCode: sector.colorCode || "#14b8a6",
                          },
                        ];
                        const savedTickets = (event?.ticketTypes ?? []).filter(
                          (item) => item.sectorId === sector.sectorId
                        );
                        const assignedTicketTypeIds = new Set(
                          (sector.seats ?? [])
                            .filter((seat) => !seat.isHidden && seat.ticketTypeId)
                            .map((seat) => seat.ticketTypeId as string)
                        );
                        return (
                          <div key={sector.sectorId} className="tix-tier-card-item">
                            <div className="tix-tier-item-top">
                              <div style={{ display: "flex", alignItems: "center", gap: "8px", flexWrap: "wrap" }}>
                                <span
                                  style={{
                                    width: "12px",
                                    height: "12px",
                                    borderRadius: "50%",
                                    backgroundColor: sector.colorCode || "#14b8a6",
                                    flexShrink: 0,
                                  }}
                                />
                                <span style={{ fontSize: "14px", fontWeight: 700, color: "#0f172a" }}>
                                  {sector.name}
                                </span>
                                <span className="tix-tier-number-badge">
                                  {sector.kind === "SEATED" ? "Khu ghế ngồi" : "Khu đứng tự do"} •{" "}
                                  {Number(sector.configuredCapacity || 0).toLocaleString(numberLocale)} chỗ
                                </span>
                              </div>
                              <div style={{ display: "flex", alignItems: "center", gap: "10px", flexWrap: "wrap" }}>
                                <span
                                  style={{
                                    fontSize: "12.5px",
                                    fontWeight: 600,
                                    color: savedTickets.length > 0 && !sectorPricingDirty ? "#15803d" : "#b45309",
                                  }}
                                >
                                  {savedTickets.length > 0
                                    ? sectorPricingDirty
                                      ? `Đã lưu ${savedTickets.length} loại vé (có thay đổi chưa lưu)`
                                      : `Đã lưu ${savedTickets.length} loại vé`
                                    : t("organizerSeatMap.unsavedTierStatus", "Chưa lưu giá vé")}
                                </span>
                                {sector.kind === "SEATED" && (
                                  <button
                                    type="button"
                                    className="tix-btn-secondary"
                                    disabled={busy}
                                    style={{ padding: "5px 10px", fontSize: "12.5px" }}
                                    onClick={() => {
                                      const nextColor =
                                        SECTOR_TICKET_PALETTE[drafts.length % SECTOR_TICKET_PALETTE.length];
                                      setSectorPricingInputs((prev) => ({
                                        ...prev,
                                        [sector.sectorId]: [
                                          ...drafts,
                                          {
                                            localKey: crypto.randomUUID(),
                                            ticketTypeId: null,
                                            name: `${sector.name} - Hạng ${drafts.length + 1}`,
                                            unitPrice: "",
                                            colorCode: nextColor,
                                          },
                                        ],
                                      }));
                                      setSectorPricingDirty(true);
                                    }}
                                  >
                                    <Plus size={14} />
                                    <span>Thêm loại vé</span>
                                  </button>
                                )}
                              </div>
                            </div>

                            <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                              {drafts.map((draft, draftIdx) => {
                                const isAssignedToSeat = Boolean(
                                  draft.ticketTypeId && assignedTicketTypeIds.has(draft.ticketTypeId)
                                );
                                return (
                                  <div
                                    key={draft.localKey}
                                    className="tix-tier-inputs-grid"
                                    style={{
                                      gridTemplateColumns:
                                        drafts.length > 1
                                          ? "1.4fr 1fr 130px auto"
                                          : "1.4fr 1fr 130px",
                                      alignItems: "end",
                                    }}
                                  >
                                    <div className="tix-field-wrap">
                                      <label
                                        className="tix-field-label"
                                        htmlFor={`sector-ticket-name-${index}-${draftIdx}`}
                                      >
                                        {t("organizerEventCreate.ticketFieldName", "Tên loại vé")}
                                        <span className="required">*</span>
                                      </label>
                                      <input
                                        id={`sector-ticket-name-${index}-${draftIdx}`}
                                        type="text"
                                        className="tix-input"
                                        value={draft.name}
                                        disabled={busy}
                                        placeholder={`Vé ${sector.name}`}
                                        onChange={(e) => {
                                          const nextValue = e.target.value;
                                          setSectorPricingInputs((prev) => ({
                                            ...prev,
                                            [sector.sectorId]: drafts.map((item) =>
                                              item.localKey === draft.localKey
                                                ? { ...item, name: nextValue }
                                                : item
                                            ),
                                          }));
                                          setSectorPricingDirty(true);
                                        }}
                                      />
                                    </div>

                                    <div className="tix-field-wrap">
                                      <label
                                        className="tix-field-label"
                                        htmlFor={`sector-ticket-price-${index}-${draftIdx}`}
                                      >
                                        {t("organizerEventCreate.ticketFieldPrice", "Giá vé (VNĐ)")}
                                        <span className="required">*</span>
                                      </label>
                                      <input
                                        id={`sector-ticket-price-${index}-${draftIdx}`}
                                        type="number"
                                        min={0}
                                        step="1000"
                                        className="tix-input"
                                        value={draft.unitPrice}
                                        disabled={busy}
                                        placeholder="0"
                                        onChange={(e) => {
                                          const nextValue = e.target.value;
                                          setSectorPricingInputs((prev) => ({
                                            ...prev,
                                            [sector.sectorId]: drafts.map((item) =>
                                              item.localKey === draft.localKey
                                                ? { ...item, unitPrice: nextValue }
                                                : item
                                            ),
                                          }));
                                          setSectorPricingDirty(true);
                                        }}
                                      />
                                    </div>

                                    <div className="tix-field-wrap">
                                      <label
                                        className="tix-field-label"
                                        htmlFor={`sector-ticket-color-${index}-${draftIdx}`}
                                      >
                                        Màu vé
                                      </label>
                                      <div
                                        style={{
                                          display: "flex",
                                          alignItems: "center",
                                          gap: "8px",
                                          height: "42px",
                                          padding: "0 10px",
                                          borderRadius: "10px",
                                          border: "1px solid #d1d5db",
                                          backgroundColor: "#ffffff",
                                        }}
                                      >
                                        <input
                                          id={`sector-ticket-color-${index}-${draftIdx}`}
                                          type="color"
                                          value={draft.colorCode || "#14b8a6"}
                                          disabled={busy}
                                          onChange={(e) => {
                                            const nextColor = e.target.value;
                                            setSectorPricingInputs((prev) => ({
                                              ...prev,
                                              [sector.sectorId]: drafts.map((item) =>
                                                item.localKey === draft.localKey
                                                  ? { ...item, colorCode: nextColor }
                                                  : item
                                              ),
                                            }));
                                            setSectorPricingDirty(true);
                                          }}
                                          style={{
                                            width: "24px",
                                            height: "24px",
                                            padding: 0,
                                            border: "none",
                                            background: "transparent",
                                            cursor: "pointer",
                                          }}
                                        />
                                        <span style={{ fontSize: "12px", color: "#475569", fontFamily: "monospace" }}>
                                          {(draft.colorCode || "#14b8a6").toUpperCase()}
                                        </span>
                                      </div>
                                    </div>

                                    {drafts.length > 1 && (
                                      <button
                                        type="button"
                                        className="tix-btn-secondary"
                                        disabled={busy || isAssignedToSeat}
                                        title={
                                          isAssignedToSeat
                                            ? "Không thể xóa loại vé đang được gán cho ghế trong khu"
                                            : "Xóa loại vé này"
                                        }
                                        style={{
                                          height: "42px",
                                          padding: "0 10px",
                                          color: isAssignedToSeat ? "#94a3b8" : "#dc2626",
                                          borderColor: isAssignedToSeat ? "#e2e8f0" : "#fecaca",
                                        }}
                                        onClick={() => {
                                          setSectorPricingInputs((prev) => ({
                                            ...prev,
                                            [sector.sectorId]: drafts.filter(
                                              (item) => item.localKey !== draft.localKey
                                            ),
                                          }));
                                          setSectorPricingDirty(true);
                                        }}
                                      >
                                        <Trash2 size={15} />
                                      </button>
                                    )}
                                  </div>
                                );
                              })}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  {/* Card 2: Gán loại vé cho từng ghế & hoàn thiện sơ đồ */}
                  <div className="tix-form-card">
                    <div className="tix-form-card-header is-between">
                      <div>
                        <div className="tix-card-title-row">
                          <Armchair size={20} className="tix-group-header-icon" />
                          <h3>{t("organizerSeatMap.seatAssignmentTitle", "2. Chỉnh ghế & Gán loại vé trên sơ đồ")}</h3>
                        </div>
                        <p className="tix-card-desc">
                          {t(
                            "organizerSeatMap.seatAssignmentDesc",
                            "Mở trình chỉnh sửa sơ đồ ghế để ẩn/hiện ghế, tạo thêm hạng vé đặc biệt (VIP, VVIP...) và gán hạng vé cho toàn bộ ghế hiển thị."
                          )}
                        </p>
                      </div>

                      {event && (
                        <button
                          type="button"
                          className="tix-btn-primary"
                          disabled={busy || (!event.ticketTypes.length && !sectorPricingDirty)}
                          onClick={async () => {
                            if (sectorPricingDirty) {
                              const saved = await handleSaveSectorPricing();
                              if (!saved) return;
                            }
                            navigate(`/organizer/events/${event.eventId}/seat-map`);
                          }}
                        >
                          <Armchair size={16} />
                          <span>
                            {t("organizerSeatMap.openSeatAssignmentBtn", "Chỉnh ghế và gán loại vé")}
                          </span>
                        </button>
                      )}
                    </div>

                    <div className="tix-review-tiers-list">
                      {sectors.map((sector) => {
                        const visibleSeats = sector.seats.filter((seat) => !seat.isHidden);
                        const assignedSeats = visibleSeats.filter((seat) =>
                          Boolean(seat.ticketTypeId?.trim())
                        );
                        const hasStandingTicket = Boolean(
                          event?.ticketTypes.some((item) => item.sectorId === sector.sectorId)
                        );
                        const isSectorReady =
                          sector.kind === "SEATED"
                            ? visibleSeats.length > 0 && assignedSeats.length === visibleSeats.length
                            : Number(sector.configuredCapacity) > 0 && hasStandingTicket;

                        return (
                          <div key={sector.sectorId} className="tix-review-tier-row">
                            <div className="tix-review-tier-info">
                              <span
                                className="tix-review-tier-color"
                                style={{ backgroundColor: sector.colorCode || "#2563eb" }}
                              />
                              <span className="tix-review-tier-name">{sector.name}</span>
                              <span className="tix-review-tier-qty">
                                {sector.kind === "SEATED"
                                  ? `${assignedSeats.length}/${visibleSeats.length} ghế đã gán loại vé`
                                  : hasStandingTicket
                                  ? `${Number(sector.configuredCapacity || 0).toLocaleString(
                                      numberLocale
                                    )} chỗ đứng đã có loại vé`
                                  : "Chưa lưu loại vé cho khu đứng"}
                              </span>
                            </div>
                            <span
                              style={{
                                fontSize: "12.5px",
                                fontWeight: 700,
                                color: isSectorReady ? "#15803d" : "#b45309",
                              }}
                            >
                              {isSectorReady ? "Đã hoàn tất" : "Cần gán vé / lưu giá"}
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </>
              )}
            </>
          )}

          <section className="tix-form-card">
            <div className="tix-form-card-header is-between">
              <div>
                <div className="tix-card-title-row">
                  <Tag size={20} className="tix-group-header-icon" />
                  <h3>{t("organizerEventCreate.promotionTitle", "Mã giảm giá")}</h3>
                </div>
                <p className="tix-card-desc">
                  {t(
                    "organizerEventCreate.promotionSubtitle",
                    "Thiết lập mã khuyến mãi giảm theo phần trăm hoặc số tiền cố định (không bắt buộc).",
                  )}
                </p>
              </div>
              <button type="button" className="tix-btn-secondary" disabled={busy} onClick={() => {
                setPromotions((items) => [...items, {
                  key: crypto.randomUUID(), promotionId: null, code: "",
                  startsAt: localDateTime(event?.saleStartsAt),
                  endsAt: localDateTime(event?.saleEndsAt),
                  kind: "PERCENT", value: "", maximumUses: "",
                }]);
                setPromotionsDirty(true);
              }}>
                <Plus size={15} aria-hidden="true" />
                <span>{t("organizerEventCreate.addPromotion", "Thêm mã giảm giá")}</span>
              </button>
            </div>
            <div className="tix-tiers-list">
              {promotions.length === 0 && (
                <div className="tix-tier-card-item tix-promo-empty">
                  <p className="tix-card-desc">{t("organizerEventCreate.noPromotions", "Chưa có mã giảm giá.")}</p>
                </div>
              )}
              {promotions.map((item, index) => (
                <fieldset key={item.key} className="tix-tier-card-item tix-promo-fieldset" disabled={busy}>
                  <legend className="sr-only">{t("organizerEventCreate.promotionItem", { defaultValue: "Mã giảm giá {{number}}", number: index + 1 })}</legend>
                  <div className="tix-tier-item-top">
                    <span className="tix-tier-number-badge">
                      {t("organizerEventCreate.promotionItem", { defaultValue: "Mã giảm giá {{number}}", number: index + 1 })}
                    </span>
                    <button
                      type="button"
                      className="tix-tier-delete-btn"
                      onClick={() => {
                        setPromotions((items) => items.filter((promotion) => promotion.key !== item.key));
                        setPromotionsDirty(true);
                      }}
                    >
                      <Trash2 size={14} aria-hidden="true" />
                      <span>{t("organizerEventCreate.removePromotion", "Xóa mã")}</span>
                    </button>
                  </div>
                  <div className="tix-form-grid">
                    <label className="tix-field-wrap">
                      <span className="tix-field-label">{t("organizerEventCreate.promotionCode", "Mã")}</span>
                      <input
                        className="tix-input"
                        value={item.code}
                        placeholder={t("organizerEventCreate.placeholderPromotionCode", "Ví dụ: FLASH20, EARLYBIRD...")}
                        onChange={(e) => updatePromotion(item.key, { code: e.target.value })}
                      />
                    </label>
                    <div className="tix-field-wrap">
                      <span className="tix-field-label">{t("organizerEventCreate.discountKind", "Loại giảm giá")}</span>
                      <TixSelect
                        ariaLabel={t("organizerEventCreate.discountKind", "Loại giảm giá")}
                        value={item.kind}
                        onChange={(value) => updatePromotion(item.key, { kind: value === "FIXED" ? "FIXED" : "PERCENT", value: "" })}
                        options={[
                          { value: "PERCENT", label: t("organizerEventCreate.discountPercent", "Theo phần trăm") },
                          { value: "FIXED", label: t("organizerEventCreate.discountFixed", "Số tiền cố định") },
                        ]}
                        disabled={busy}
                      />
                    </div>
                    <label className="tix-field-wrap">
                      <span className="tix-field-label">{item.kind === "PERCENT" ? t("organizerEventCreate.discountValuePercent", "Mức giảm (%)") : t("organizerEventCreate.discountValueFixed", "Mức giảm (VNĐ)")}</span>
                      <input
                        className="tix-input"
                        inputMode="numeric"
                        value={item.value}
                        placeholder={
                          item.kind === "PERCENT"
                            ? t("organizerEventCreate.placeholderDiscountPercent", "Ví dụ: 20 (1 - 100)")
                            : t("organizerEventCreate.placeholderDiscountFixed", "Ví dụ: 50000")
                        }
                        onChange={(e) => updatePromotion(item.key, { value: e.target.value })}
                      />
                    </label>
                    <label className="tix-field-wrap">
                      <span className="tix-field-label">{t("organizerEventCreate.promotionMaxUses", "Số lượt sử dụng tối đa")}</span>
                      <input
                        className="tix-input"
                        inputMode="numeric"
                        value={item.maximumUses}
                        placeholder={t("organizerEventCreate.placeholderPromotionMaxUses", "Ví dụ: 100")}
                        onChange={(e) => updatePromotion(item.key, { maximumUses: e.target.value })}
                      />
                    </label>
                    <label className="tix-field-wrap">
                      <span className="tix-field-label">{t("organizerEventCreate.promotionStart", "Có hiệu lực từ")}</span>
                      <input className="tix-input" type="datetime-local" step="0.001" value={item.startsAt} onChange={(e) => updatePromotion(item.key, { startsAt: e.target.value })} />
                    </label>
                    <label className="tix-field-wrap">
                      <span className="tix-field-label">{t("organizerEventCreate.promotionEnd", "Hết hiệu lực lúc")}</span>
                      <input className="tix-input" type="datetime-local" step="0.001" value={item.endsAt} onChange={(e) => updatePromotion(item.key, { endsAt: e.target.value })} />
                    </label>
                  </div>
                </fieldset>
              ))}
            </div>
          </section>

          {/* Alert thông báo lỗi form nếu có */}
          {error && (
            <div className="tix-form-alert-error" role="alert">
              <AlertCircle size={18} />
              <span>{error}</span>
            </div>
          )}

          {/* Form Actions Footer Bar - Step Tickets */}
          <div className="tix-form-actions-bar">
            <button
              type="button"
              className="tix-btn-secondary"
              onClick={() => handleStepChange(mode === "SEAT_MAP" ? "sectors" : "media")}
            >
              <ArrowLeft size={16} />
              <span>{t("organizerStepper.btnBack", "Quay lại")}</span>
            </button>

            {mode === "QUANTITY" ? (
              <button
                type="submit"
                className="tix-btn-primary"
                disabled={busy}
                onClick={() => {
                  nextStepAfterSave.current = "review";
                }}
              >
                {saveDraft.isPending ? (
                  <>
                    <Loader2 size={16} className="tix-spin-icon" />
                    <span>{t("common.saving", "Đang lưu…")}</span>
                  </>
                ) : (
                  <>
                    <span>{t("organizerStepper.btnSaveAndReview", "Lưu & Xem lại")}</span>
                    <ArrowRight size={16} />
                  </>
                )}
              </button>
            ) : (
              <button
                type="button"
                className="tix-btn-primary"
                disabled={busy}
                onClick={async (click) => {
                  const form = click.currentTarget.form;
                  if (!form || !event) return;
                  const values = new FormData(form);
                  const saved = sectorPricingDirty ? await handleSaveSectorPricing() : event;
                  if (!saved) return;
                  if (promotionsDirty) {
                    nextStepAfterSave.current = "review";
                    saveForm(values, saved);
                  } else {
                    handleStepChange("review");
                  }
                }}
              >
                <span>{t("organizerStepper.btnReview", "Xem lại & Gửi duyệt")}</span>
                <ArrowRight size={16} />
              </button>
            )}
          </div>
        </div>

        {/* ============================================================
            BƯỚC 4: XEM LẠI & GỬI DUYỆT (STEP 4: REVIEW)
            ============================================================ */}
        <div
          className="tix-step-container"
          style={{
            display: activeStep === "review" ? "flex" : "none",
            flexDirection: "column",
            gap: "24px",
          }}
        >
          <div className="tix-form-card">
            <div className="tix-form-card-header">
              <div className="tix-card-title-row">
                <CheckCircle2 size={20} className="tix-group-header-icon" />
                <h3>{t("organizerStepper.reviewTitle", "Tổng quan & Gửi duyệt")}</h3>
              </div>
              <p className="tix-card-desc">
                {t(
                  "organizerStepper.reviewSubtitle",
                  "Vui lòng kiểm tra lại toàn bộ thông tin trước khi gửi sự kiện để ban quản trị phê duyệt."
                )}
              </p>
            </div>

            <div className="tix-review-section">
              {/* Hero Preview: Banner 16:9 + Event Header */}
              <div className="tix-review-hero">
                <div className="tix-review-banner-wrap">
                  {banner ? (
                    <img
                      src={banner.objectReference}
                      alt={event?.title || "Event cover"}
                      className="tix-review-banner-img"
                    />
                  ) : (
                    <div className="tix-review-banner-empty">
                      <ImageIcon size={32} />
                      <span>{t("organizerStepper.noBannerUploaded", "Chưa có ảnh bìa sự kiện")}</span>
                    </div>
                  )}
                </div>

                <div className="tix-review-hero-body">
                  <div className="tix-review-pills">
                    {selectedCategoryName && (
                      <span className="tix-review-pill is-category">
                        {selectedCategoryName}
                      </span>
                    )}
                    <span className="tix-review-pill">
                      {mode === "SEAT_MAP"
                        ? t("organizerEventCreate.modeSeatMapTitle", "Theo sơ đồ ghế")
                        : t("organizerEventCreate.modeQuantityTitle", "Vé số lượng")}
                    </span>
                    <span className="tix-review-pill is-draft">
                      {t("organizerHub.statusDraft", "Bản nháp")}
                    </span>
                  </div>

                  <h2 className="tix-review-event-title">
                    {event?.title || t("organizerStepper.untitledEvent", "(Chưa đặt tên sự kiện)")}
                  </h2>
                </div>
              </div>

              {/* 2-Column Structured Review Layout */}
              <div className="tix-review-layout">
                {/* Left Column: Description, Gallery, Seat Map */}
                <div className="tix-review-main-col">
                  <section className="tix-review-panel">
                    <div className="tix-review-panel-head">
                      <FileText size={16} />
                      <h4>{t("organizerEventCreate.fieldDescription", "Mô tả sự kiện")}</h4>
                    </div>
                    {event?.description?.trim() ? (
                      <div className="tix-review-description tix-description-content">{descriptionContent(event.description)}</div>
                    ) : (
                      <p className="tix-review-empty-text">
                        {t("organizerStepper.noDescription", "Chưa có mô tả chi tiết cho sự kiện.")}
                      </p>
                    )}
                  </section>

                  <section className="tix-review-panel">
                    <div className="tix-review-panel-head">
                      <ImageIcon size={16} />
                      <h4>
                        {t("organizerMedia.galleryPhotos", "Ảnh thư viện")} ({gallery.length})
                      </h4>
                    </div>
                    {gallery.length > 0 ? (
                      <div className="tix-review-gallery-grid">
                        {gallery.map((image, index) => (
                          <button
                            key={image.imageId}
                            type="button"
                            className="tix-review-gallery-thumb"
                            onClick={() => setLightboxIndex(index)}
                            style={{ cursor: "zoom-in", padding: 0, border: "none", background: "transparent" }}
                          >
                            <img
                              src={image.objectReference}
                              alt={`${event?.title || "Event"} #${index + 1}`}
                            />
                            <span className="tix-review-gallery-order">#{index + 1}</span>
                          </button>
                        ))}
                      </div>
                    ) : (
                      <p className="tix-review-empty-text">
                        {t("organizerStepper.noGalleryUploaded", "Chưa tải lên ảnh thư viện nào.")}
                      </p>
                    )}
                  </section>

                  {mode === "SEAT_MAP" && (
                    <section className="tix-review-panel">
                      <div className="tix-review-panel-head is-between">
                        <div className="tix-review-panel-title">
                          <Armchair size={16} />
                          <h4>{t("organizerSeatMap.reviewSeatMapTitle", "Sơ đồ ghế")}</h4>
                        </div>
                        {seatMapPreviewUrl && (
                          <span style={{ fontSize: "12px", color: "#64748b" }}>
                            Nhấn vào sơ đồ để phóng to
                          </span>
                        )}
                      </div>
                      {seatMapPreviewUrl ? (
                        <button
                          type="button"
                          onClick={() => setLightboxIndex(gallery.length)}
                          style={{
                            display: "block",
                            width: "100%",
                            padding: "12px",
                            borderRadius: "10px",
                            border: "1px solid #e2e8f0",
                            backgroundColor: "#ffffff",
                            cursor: "zoom-in",
                          }}
                        >
                          <img
                            src={seatMapPreviewUrl}
                            alt={`${event?.title || "Event"} - Sơ đồ ghế`}
                            style={{
                              width: "100%",
                              maxHeight: "420px",
                              objectFit: "contain",
                              display: "block",
                            }}
                          />
                        </button>
                      ) : (
                        <p className="tix-review-empty-text">
                          {t("organizerSeatMap.noSeatMapPreview", "Chưa có dữ liệu sơ đồ ghế.")}
                        </p>
                      )}
                    </section>
                  )}
                </div>

                {/* Right Column: Schedule, Venue, Ticket Tiers, Readiness */}
                <aside className="tix-review-side-col">
                  <section className="tix-review-panel">
                    <div className="tix-review-panel-head">
                      <h4>{t("organizerStepper.reviewScheduleVenue", "Lịch trình & Địa điểm")}</h4>
                    </div>

                    <div className="tix-review-meta-list">
                      <div className="tix-review-meta-row">
                        <span className="tix-review-meta-icon">
                          <Calendar size={15} />
                        </span>
                        <div className="tix-review-meta-content">
                          <span className="tix-review-label">
                            {t("organizerStepper.reviewStartsAt", "Thời gian bắt đầu")}
                          </span>
                          <span className="tix-review-value">
                            {formatReviewDateTime(event?.startsAt)}
                          </span>
                          <span className="tix-review-label" style={{ marginTop: "6px" }}>
                            {t("organizerStepper.reviewEndsAt", "Thời gian kết thúc")}
                          </span>
                          <span className="tix-review-value">
                            {formatReviewDateTime(event?.endsAt)}
                          </span>
                        </div>
                      </div>

                      <div className="tix-review-meta-row">
                        <span className="tix-review-meta-icon">
                          <Clock size={15} />
                        </span>
                        <div className="tix-review-meta-content">
                          <span className="tix-review-label">
                            {t("organizerStepper.reviewSalesWindow", "Thời gian mở bán vé")}
                          </span>
                          <span className="tix-review-value">
                            {formatReviewDateTime(event?.saleStartsAt)} – {formatReviewDateTime(event?.saleEndsAt)}
                          </span>
                        </div>
                      </div>

                      <div className="tix-review-meta-row">
                        <span className="tix-review-meta-icon">
                          <MapPin size={15} />
                        </span>
                        <div className="tix-review-meta-content">
                          <span className="tix-review-label">
                            {t("organizerStepper.reviewVenue", "Địa điểm")}
                          </span>
                          <span className="tix-review-value">
                            {selectedVenueName || "—"}
                          </span>
                          {selectedVenue?.address && (
                            <span className="tix-review-subvalue">{selectedVenue.address}</span>
                          )}
                        </div>
                      </div>

                      <div className="tix-review-meta-row">
                        <span className="tix-review-meta-icon">
                          <Ticket size={15} />
                        </span>
                        <div className="tix-review-meta-content">
                          <span className="tix-review-label">
                            {t("organizerEventCreate.fieldPurchaseLimit", "Giới hạn vé mỗi người mua")}
                          </span>
                          <span className="tix-review-value">
                            {event?.purchaseLimit
                              ? t("organizerStepper.purchaseLimitValue", {
                                  count: event.purchaseLimit,
                                  defaultValue: `Tối đa ${event.purchaseLimit} vé / người`,
                                })
                              : "—"}
                          </span>
                        </div>
                      </div>
                    </div>
                  </section>

                  <section className="tix-review-panel">
                    <div className="tix-review-panel-head is-between">
                      <div className="tix-review-panel-title">
                        <Ticket size={16} />
                        <h4>{t("organizerStepper.reviewTiersList", "Các hạng vé phát hành")}</h4>
                      </div>
                      {event?.ticketTypes && event.ticketTypes.length > 0 && (
                        <span className="tix-review-capacity-badge">
                          {t("organizerStepper.totalCapacity", {
                            count: totalTicketsCount.toLocaleString(numberLocale),
                            defaultValue: `Tổng ${totalTicketsCount.toLocaleString(numberLocale)} vé`,
                          })}
                        </span>
                      )}
                    </div>

                    {event?.ticketTypes && event.ticketTypes.length > 0 ? (
                      <div className="tix-review-tiers-list">
                        {event.ticketTypes.map((tier) => {
                          const linkedSector = tier.sectorId
                            ? sectors.find((item) => item.sectorId === tier.sectorId)
                            : undefined;
                          const ticketCount = Number(tier.configuredQuantity || 0);
                          return (
                            <div key={tier.ticketTypeId} className="tix-review-tier-row">
                              <div className="tix-review-tier-info">
                                <span
                                  className="tix-review-tier-color"
                                  style={{ backgroundColor: tier.colorCode || "#2563eb" }}
                                />
                                <span className="tix-review-tier-name">{tier.name}</span>
                                {!linkedSector && (
                                  <span className="tix-review-tier-qty">
                                    {t("organizerStepper.tierTicketCount", {
                                      count: ticketCount.toLocaleString(numberLocale),
                                      defaultValue: `${ticketCount.toLocaleString(numberLocale)} vé`,
                                    })}
                                  </span>
                                )}
                              </div>
                              <span className="tix-review-tier-price">
                                {Number(tier.unitPrice).toLocaleString(numberLocale)} đ
                              </span>
                            </div>
                          );
                        })}
                      </div>
                    ) : (
                      <p className="tix-review-empty-text">
                        {mode === "SEAT_MAP"
                          ? t(
                              "organizerStepper.seatMapReviewHint",
                              "Hạng vé sẽ được đồng bộ sau khi hoàn tất cấu hình sơ đồ ghế."
                            )
                          : t("organizerStepper.noTiersYet", "Chưa có hạng vé nào được lưu.")}
                      </p>
                    )}
                  </section>

                  {!!event?.promotions.length && (
                    <section>
                      <h3>{t("organizerEventCreate.promotionTitle", "Mã giảm giá")}</h3>
                      <div className="tix-review-tiers-list">
                        {event.promotions.map(({ promotionId, code, discount }) => (
                          <div className="tix-review-tier-row" key={promotionId ?? code}>
                            <span className="tix-review-tier-name">{code}</span>
                            <span className="tix-review-tier-price">
                              {discount.kind === "PERCENT"
                                ? `${discount.percent}%`
                                : `${BigInt(discount.amount ?? "0").toLocaleString(numberLocale)} đ`}
                            </span>
                          </div>
                        ))}
                      </div>
                    </section>
                  )}

                  {mode === "SEAT_MAP" && !seatMapConfigured ? (
                    <div
                      className="tix-review-ready-banner"
                      style={{
                        background: "rgba(245, 158, 11, 0.12)",
                        borderColor: "rgba(217, 119, 6, 0.35)",
                        color: "#b45309",
                      }}
                    >
                      <AlertCircle size={20} style={{ flexShrink: 0 }} />
                      <span>
                        {t(
                          "organizerSeatMap.notReadySubmitNotice",
                          "Vui lòng hoàn tất tạo sơ đồ khu vực, lưu giá vé theo khu và gán loại vé cho toàn bộ ghế hiển thị trước khi gửi duyệt."
                        )}
                      </span>
                    </div>
                  ) : null}
                </aside>
              </div>
            </div>

            {/* Error in review step */}
            {error && (
              <div className="tix-form-alert-error" role="alert" style={{ marginTop: "16px" }}>
                <AlertCircle size={18} />
                <span>{error}</span>
              </div>
            )}
          </div>

          {/* Action buttons in Review step */}
          <div className="tix-form-actions-bar">
            <button
              type="button"
              className="tix-btn-secondary"
              onClick={() => handleStepChange("tickets")}
            >
              <ArrowLeft size={16} />
              <span>{t("organizerStepper.btnBack", "Quay lại")}</span>
            </button>

            {event && (
              <button
                type="button"
                className="tix-btn-primary"
                disabled={
                  busy ||
                  dirty ||
                  promotionsDirty ||
                  (mode === "QUANTITY" && (!event.ticketTypes || event.ticketTypes.length === 0)) ||
                  (mode === "SEAT_MAP" && !seatMapConfigured)
                }
                onClick={() => {
                  setError("");
                  submit.mutate(event);
                }}
              >
                {submit.isPending ? (
                  <>
                    <Loader2 size={16} className="tix-spin-icon" />
                    <span>{t("organizerEventCreate.submitting", "Đang gửi duyệt…")}</span>
                  </>
                ) : (
                  <span>{t("organizerEventCreate.submitReviewBtn", "Gửi duyệt sự kiện")}</span>
                )}
              </button>
            )}
          </div>

          {dirty && (
            <p className="tix-form-notice" style={{ textAlign: "right", marginTop: "8px" }}>
              {t(
                "organizerEventCreate.dirtyNotice",
                "* Hãy lưu thay đổi của bản nháp trước khi chuyển sang bước tiếp theo."
              )}
            </p>
          )}
        </div>
      </form>
    </div>
  )}
  <Lightbox
    open={lightboxIndex !== null && reviewLightboxSlides.length > 0}
    close={() => setLightboxIndex(null)}
    index={lightboxIndex ?? 0}
    slides={reviewLightboxSlides}
  />
</>
  );
}

function EditEventDraft({
  eventId,
  queryKey,
}: {
  eventId: string;
  queryKey: string[];
}) {
  const { t } = useTranslation();
  const draft = useQuery({
    queryKey: [...queryKey, eventId],
    queryFn: async ({ signal }) =>
      (
        await axiosClient.get<OwnerDraft>(
          "/api/v1/organizer/events/" + encodeURIComponent(eventId),
          { signal }
        )
      ).data,
    retry: false,
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });

  if (draft.isPending || (draft.isFetching && !draft.isFetchedAfterMount)) {
    return (
      <div className="tix-form-card" style={{ alignItems: "center", padding: "48px" }}>
        <Loader2 className="tix-spin-icon" size={28} />
        <span style={{ fontSize: "14px", color: "var(--text-muted, #666b74)", marginTop: "10px" }}>
          {t("organizerEventCreate.loadingDraft", "Đang tải dữ liệu sự kiện…")}
        </span>
      </div>
    );
  }

  if (draft.isError) {
    return (
      <div className="tix-form-alert-error" role="alert">
        <AlertCircle size={18} />
        <span>
          {t("organizerEventCreate.errorLoadingDraft", "Không tải được thông tin sự kiện.")}
        </span>
        <button
          type="button"
          className="tix-btn-retry"
          onClick={() => void draft.refetch()}
        >
          {t("common.retry", "Thử lại")}
        </button>
      </div>
    );
  }

  return <EventDraftForm initialEvent={draft.data} queryKey={queryKey} />;
}

export default function OrganizerEventCreatePage() {
  const { t } = useTranslation();
  const { eventId } = useParams();
  const { ready } = useOrganizerGate();
  const { keycloak } = useKeycloak();
  const subject = keycloak.tokenParsed?.sub;
  const issuer = keycloak.tokenParsed?.iss ?? "";
  const queryKey = useMemo(
    () => ["organizer-owner-draft", issuer, subject ?? ""],
    [issuer, subject]
  );

  return (
    <OrganizerLayout
      hideSidebar
      hideHeader
      requireOrganizer
      className="organizer-hub-page"
    >
      <div className="tix-event-create-page">
        <div className="tix-event-create-container">
          {/* Topbar đồng bộ 100% với Dashboard TIX */}
          <TixOrganizerTopNav activeTab="create" />

          {/* Header Card của trang tạo/sửa sự kiện */}
          <div className="tix-create-header-wrap">
            <div className="tix-create-title-row">
              <div className="tix-create-title-block">
                <h1>
                  {eventId
                    ? t("organizerEventCreate.editTitle", "Chỉnh sửa sự kiện")
                    : t("organizerEventCreate.createTitle", "Tạo sự kiện mới")}
                </h1>
                <p>
                  {t(
                    "organizerEventCreate.pageSubtitle",
                    "Thiết lập thông tin sự kiện, thời gian diễn ra và phương thức bán vé để mở bán trên FlashTicket."
                  )}
                </p>
              </div>

              {eventId && (
                <span className="tix-create-status-badge is-draft">
                  {t("organizerHub.statusDraft", "Bản nháp")}
                </span>
              )}
            </div>
          </div>

          {/* Nội dung Form */}
          <div className="tix-event-content-stack">
            {ready &&
              subject &&
              (eventId ? (
                <EditEventDraft
                  key={[...queryKey, eventId].join(":")}
                  eventId={eventId}
                  queryKey={queryKey}
                />
              ) : (
                <EventDraftForm key={queryKey.join(":")} queryKey={queryKey} />
              ))}
          </div>
        </div>
      </div>
    </OrganizerLayout>
  );
}
