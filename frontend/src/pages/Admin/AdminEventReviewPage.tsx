import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import { isAxiosError } from "axios";
import {
  Armchair,
  Ban,
  Building2,
  CalendarClock,
  Check,
  CheckCircle2,
  ChevronRight,
  Clock,
  Copy,
  FileText,
  Images,
  LoaderCircle,
  MapPin,
  Search,
  ShieldAlert,
  Tag,
  Ticket,
  X,
} from "lucide-react";
import Lightbox from "yet-another-react-lightbox";
import "yet-another-react-lightbox/styles.css";
import { toast } from "react-toastify";
import Swal from "sweetalert2";

import { type TFunction } from "i18next";
import TixAdminTopNav from "../../components/admin/TixAdminTopNav";
import {
  buildSeatMapEditorDocument,
  exportEditorDocumentToSvg,
} from "../../components/seat-map/editor/seatMapEditorUtils";
import { hasRealmRole } from "../../lib/auth";
import axiosClient from "../../lib/axiosClient";
import { descriptionContent } from "../../lib/eventDescription";
import {
  cancellationService,
  type CancellationRequest,
} from "../../services/cancellationService";
import { categoryService } from "../../services/categoryService";
import { organizerService } from "../../services/organizerService";
import { editorSeatMap } from "../../services/organizerSeatMapV1";
import { venueService } from "../../services/venueService";
import type { EventImageContract, EventSeatMapContract } from "../../types/eventContract";
import "../Organizer/organizer-dashboard.css";
import "./admin-tix.css";

function formatDateTime(value: string | null | undefined, locale = "vi-VN"): string {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString(locale, {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatMoney(amount: string | number | null | undefined, currency = "VND", locale = "vi-VN"): string {
  if (amount === null || amount === undefined || amount === "") return "—";
  const num = typeof amount === "number" ? amount : Number(amount);
  if (Number.isNaN(num)) return `${amount} ${currency}`;
  const formatted = new Intl.NumberFormat(locale, { maximumFractionDigits: 0 }).format(num);
  return currency.toUpperCase() === "VND" ? `${formatted} ₫` : `${formatted} ${currency}`;
}

function shortId(value: string): string {
  if (!value) return "—";
  return value.length > 16 ? `${value.slice(0, 8)}…${value.slice(-4)}` : value;
}

async function copyToClipboard(
  text: string,
  label: string,
  t: TFunction,
): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
    toast.success(
      t("adminRefunds.copied", {
        label,
        defaultValue: "Đã sao chép {{label}}",
      }),
    );
  } catch {
    toast.error(t("adminRefunds.copyError", "Không thể sao chép vào clipboard."));
  }
}

interface EventTicketTypeDetail {
  ticketTypeId: string;
  name: string;
  unitPrice: string;
  currency: string;
  sectorId?: string | null;
  configuredQuantity?: string | null;
  colorCode?: string | null;
}

interface EventPromotionDetail {
  promotionId: string;
  code: string;
  startsAt?: string | null;
  endsAt?: string | null;
  maximumUses?: string | null;
  discount?: {
    kind?: string;
    amount?: string | null;
    percent?: number | null;
  } | null;
}

interface OrganizerSummary {
  eventId: string;
  organizerSubject?: string;
  title: string;
  status: string;
  mode?: string | null;
  startsAt?: string | null;
  endsAt?: string | null;
  rowVersion: string;
}

interface EventDetail extends OrganizerSummary {
  description: string | null;
  categoryId?: string | null;
  venueId?: string | null;
  saleStartsAt?: string | null;
  saleEndsAt?: string | null;
  purchaseLimit?: number;
  sourceConfigVersion?: string | null;
  images?: EventImageContract[];
  ticketTypes: EventTicketTypeDetail[];
  promotions?: EventPromotionDetail[];
  seatMap?: EventSeatMapContract | null;
}

const path = "/api/v1/admin/events";

type AdminEventTab = "NEW_EVENTS" | "CANCELLATIONS";

function ReviewQueue({ searchTerm }: { searchTerm: string }) {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const [selected, setSelected] = useState<string | null>(null);
  const [fee, setFee] = useState("0.05");
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [lightboxIndex, setLightboxIndex] = useState(-1);

  const locale = i18n.resolvedLanguage?.startsWith("en") ? "en-US" : "vi-VN";

  useEffect(() => {
    setLightboxIndex(-1);
  }, [selected]);

  const list = useInfiniteQuery({
    queryKey: ["admin-events", "PENDING_APPROVAL"],
    initialPageParam: undefined as string | undefined,
    queryFn: async ({ pageParam, signal }) =>
      (
        await axiosClient.get<{
          items: OrganizerSummary[];
          nextCursor: string | null;
        }>(path, {
          params: {
            status: "PENDING_APPROVAL",
            cursor: pageParam,
            size: 20,
          },
          signal,
        })
      ).data,
    getNextPageParam: (page, _, __, cursors) =>
      page.nextCursor && !cursors.includes(page.nextCursor) ? page.nextCursor : undefined,
  });

  const detail = useQuery({
    queryKey: ["admin-event", selected],
    enabled: selected !== null,
    queryFn: async ({ signal }) =>
      (await axiosClient.get<EventDetail>(`${path}/${selected}`, { signal })).data,
  });

  const categoriesQuery = useQuery({
    queryKey: ["admin-event-categories"],
    staleTime: 5 * 60 * 1000,
    queryFn: () => categoryService.getCategories(),
  });

  const venuesQuery = useQuery({
    queryKey: ["admin-event-venues"],
    staleTime: 5 * 60 * 1000,
    queryFn: () => venueService.getVenues(),
  });

  const organizerSubject = detail.data?.organizerSubject;
  const organizerQuery = useQuery({
    queryKey: ["admin-event-organizer", organizerSubject],
    enabled: Boolean(organizerSubject),
    retry: false,
    staleTime: 5 * 60 * 1000,
    queryFn: ({ signal }) => organizerService.getPublicOrganizerBySlug(organizerSubject!, signal),
  });

  const act = useMutation({
    mutationFn: async ({
      event,
      action,
    }: {
      event: EventDetail;
      action: "approve" | "return-to-draft";
    }) =>
      axiosClient.post(`${path}/${event.eventId}/${action}`, {
        rowVersion: event.rowVersion,
        ...(action === "approve" ? { feeRate: fee.trim() } : {}),
      }),
    onSuccess: async () => {
      setErr(null);
      const okMsg = t("eventsV1.success");
      setMsg(okMsg);
      toast.success(okMsg);
      setSelected(null);
      await qc.invalidateQueries({ queryKey: ["admin-events"] });
    },
    onError: (error) => {
      setMsg(null);
      const errMsg =
        isAxiosError<{ message?: string }>(error) && error.response?.data?.message
          ? error.response.data.message
          : t("eventsV1.error");
      setErr(errMsg);
      toast.error(errMsg);
    },
  });

  const allItems = useMemo(
    () => [
      ...new Map(
        (list.data?.pages.flatMap((p) => p.items) ?? []).map((item) => [item.eventId, item]),
      ).values(),
    ],
    [list.data],
  );
  const normalizedSearch = searchTerm.trim().toLowerCase();
  const items = useMemo(
    () =>
      normalizedSearch
        ? allItems.filter(
            (item) =>
              (item.title ?? "").toLowerCase().includes(normalizedSearch) ||
              (item.eventId ?? "").toLowerCase().includes(normalizedSearch) ||
              (item.organizerSubject ?? "").toLowerCase().includes(normalizedSearch),
          )
        : allItems,
    [allItems, normalizedSearch],
  );

  const validFee = /^(?:0(?:\.[0-9]{1,8})?|1(?:\.0{1,8})?)$/.test(fee.trim());
  const event = detail.data;
  const trimmedDescription = event?.description?.trim() ?? "";
  const renderedDescription = useMemo(
    () => (trimmedDescription ? descriptionContent(trimmedDescription) : null),
    [trimmedDescription],
  );

  const categoryName = useMemo(() => {
    if (!event?.categoryId) return null;
    return categoriesQuery.data?.find((cat) => cat.id === event.categoryId)?.name ?? null;
  }, [categoriesQuery.data, event?.categoryId]);

  const venue = useMemo(() => {
    if (!event?.venueId) return null;
    return venuesQuery.data?.find((item) => item.id === event.venueId) ?? null;
  }, [venuesQuery.data, event?.venueId]);

  const { posterImage, galleryImages, photoSlides } = useMemo(() => {
    const rawImages = [...(event?.images ?? [])].sort((a, b) => a.displayOrder - b.displayOrder);
    if (rawImages.length === 0) {
      return {
        posterImage: null as EventImageContract | null,
        galleryImages: [] as EventImageContract[],
        photoSlides: [] as { src: string; alt?: string }[],
      };
    }
    const chosenPoster = rawImages.find((img) => img.role === "BANNER") ?? rawImages[0];
    const remaining = rawImages.filter((img) => img.imageId !== chosenPoster.imageId);
    const orderedForSlides = [chosenPoster, ...remaining];
    return {
      posterImage: chosenPoster,
      galleryImages: remaining,
      photoSlides: orderedForSlides.map((img, idx) => ({
        src: img.objectReference,
        alt: `${event?.title || "Event"} #${idx + 1}`,
      })),
    };
  }, [event?.images, event?.title]);

  const sortedTicketTypes = useMemo(() => {
    if (!event?.ticketTypes) return [];
    return [...event.ticketTypes].sort((a, b) => Number(a.unitPrice) - Number(b.unitPrice));
  }, [event?.ticketTypes]);

  const seatMapPreviewUrl = useMemo(() => {
    if (event?.mode !== "SEAT_MAP" || !event.seatMap) return null;
    try {
      const editorMap = editorSeatMap(event as unknown as Parameters<typeof editorSeatMap>[0]);
      const doc = buildSeatMapEditorDocument(editorMap);
      if (!doc) return null;
      const svg = exportEditorDocumentToSvg(doc);
      return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
    } catch {
      return null;
    }
  }, [event]);

  const lightboxSlides = useMemo(
    () => [
      ...photoSlides,
      ...(seatMapPreviewUrl
        ? [
            {
              src: seatMapPreviewUrl,
              alt: `${event?.title || "Event"} - Sơ đồ chỗ ngồi`,
            },
          ]
        : []),
    ],
    [event?.title, photoSlides, seatMapPreviewUrl],
  );

  const onApprove = (e: FormEvent) => {
    e.preventDefault();
    if (!event || !validFee) return;
    setMsg(null);
    setErr(null);
    act.mutate({ event, action: "approve" });
  };

  return (
    <div className="tix-admin-split-grid">
      {/* Left: Queue List */}
      <section
        className="tix-orders-card"
        aria-labelledby="event-review-list-title"
        aria-busy={list.isFetching}
      >
        <div className="tix-card-header">
          <div>
            <h2 id="event-review-list-title" className="tix-card-title">
              {t("adminEventReview.queueTitle", "Hàng đợi chờ duyệt")}
            </h2>
            <p className="tix-card-subtitle">
              {list.hasNextPage
                ? t("adminEventReview.queueSubtitleHasMore", {
                    count: items.length,
                    loaded: allItems.length,
                    defaultValue: normalizedSearch
                      ? "{{count}} sự kiện khớp trong {{loaded}} sự kiện đã tải (vẫn còn trang sau)"
                      : "{{loaded}} sự kiện đã tải (vẫn còn trang sau)",
                  })
                : t("adminEventReview.queueSubtitle", {
                    count: items.length,
                    defaultValue: "{{count}} sự kiện đang chờ phê duyệt",
                  })}
            </p>
          </div>
        </div>

        {list.isPending && (
          <div className="tix-admin-state-box" role="status">
            <LoaderCircle size={26} className="tix-spin" aria-hidden="true" />
            <span>{t("eventsV1.loading", "Đang tải...")}</span>
          </div>
        )}

        {list.isError && (
          <div className="tix-admin-error-box" role="alert">
            <span>{t("adminEventReview.loadFailed", "Không thể tải danh sách sự kiện.")}</span>
            <button
              type="button"
              className="tix-admin-btn tix-admin-btn-secondary"
              disabled={list.isFetching}
              onClick={() =>
                void (list.isFetchNextPageError ? list.fetchNextPage() : list.refetch())
              }
            >
              {t("profile.retry", "Thử lại")}
            </button>
          </div>
        )}

        {list.isSuccess && items.length === 0 && (
          <div className="tix-admin-state-box">
            <CalendarClock size={28} color="var(--text-muted, #666b74)" aria-hidden="true" />
            <span>
              {normalizedSearch
                ? list.hasNextPage
                  ? t(
                      "adminEventReview.emptySearchHasMore",
                      "Chưa thấy sự kiện khớp trong các trang đã tải. Hãy bấm Tải thêm để kiểm tra trang sau.",
                    )
                  : t("adminEventReview.emptySearch", "Không tìm thấy sự kiện phù hợp.")
                : t("eventsV1.empty", "Tất cả sự kiện đã được xử lý.")}
            </span>
          </div>
        )}

        {items.length > 0 && (
          <div className="tix-admin-app-list">
            {items.map((item) => {
              const isSelected = selected === item.eventId;
              return (
                <button
                  key={item.eventId}
                  type="button"
                  className={`tix-admin-app-item ${isSelected ? "is-selected" : ""}`}
                  disabled={act.isPending}
                  aria-pressed={isSelected}
                  aria-controls="event-review-details"
                  onClick={() => {
                    setSelected(item.eventId);
                    setMsg(null);
                    setErr(null);
                  }}
                >
                  <div className="tix-admin-app-main">
                    <span className="tix-admin-app-icon" aria-hidden="true">
                      <CalendarClock size={17} />
                    </span>
                    <div className="tix-admin-app-copy">
                      <strong>{item.title}</strong>
                      <span className="tix-admin-mono">{item.eventId}</span>
                    </div>
                  </div>
                  <div className="tix-admin-app-right">
                    <span className="tix-status-badge is-pending">
                      {t("adminEventReview.pendingBadge", "Chờ duyệt")}
                    </span>
                    <ChevronRight size={16} color="var(--text-muted, #666b74)" aria-hidden="true" />
                  </div>
                </button>
              );
            })}
          </div>
        )}

        {list.hasNextPage && !list.isFetchNextPageError && (
          <div style={{ marginTop: 14, display: "flex", justifyContent: "center" }}>
            <button
              type="button"
              className="tix-admin-btn tix-admin-btn-secondary"
              disabled={list.isFetching || act.isPending}
              onClick={() => void list.fetchNextPage()}
            >
              {list.isFetchingNextPage ? (
                <LoaderCircle size={15} className="tix-spin" aria-hidden="true" />
              ) : null}
              <span>
                {list.isFetchingNextPage
                  ? t("eventsV1.loading", "Đang tải...")
                  : t("eventsV1.loadMore", "Tải thêm")}
              </span>
            </button>
          </div>
        )}
      </section>

      {/* Right: Event Detail & Decision */}
      <section
        id="event-review-details"
        className="tix-orders-card"
        aria-label={t("adminEventReview.details", "Chi tiết sự kiện")}
      >
        {msg && (
          <div className="tix-admin-success-box" style={{ marginBottom: 16 }} role="status">
            <CheckCircle2 size={16} aria-hidden="true" />
            <span>{msg}</span>
          </div>
        )}
        {err && (
          <div className="tix-admin-error-box" style={{ marginBottom: 16 }} role="alert">
            <span>{err}</span>
          </div>
        )}

        {list.isPending ? (
          <div className="tix-admin-state-box" role="status">
            <LoaderCircle size={26} className="tix-spin" aria-hidden="true" />
            <span>{t("adminEventReview.loadingDetails", "Đang tải thông tin chi tiết…")}</span>
          </div>
        ) : selected === null ? (
          <div className="tix-admin-state-box">
            <CalendarClock size={28} color="var(--text-muted, #666b74)" aria-hidden="true" />
            <span>
              {t(
                "adminEventReview.selectPromptDesc",
                "Chọn một sự kiện ở danh sách bên trái để xem thông tin chi tiết, hạng vé và cấu hình phí nền tảng.",
              )}
            </span>
          </div>
        ) : detail.isLoading ? (
          <div className="tix-admin-state-box" role="status">
            <LoaderCircle size={26} className="tix-spin" aria-hidden="true" />
            <span>{t("adminEventReview.loadingDetails", "Đang tải thông tin chi tiết…")}</span>
          </div>
        ) : detail.isError || !event ? (
          <div className="tix-admin-error-box" role="alert">
            <span>{t("adminEventReview.detailLoadFailed", "Không thể tải thông tin sự kiện.")}</span>
            <button
              type="button"
              className="tix-admin-btn tix-admin-btn-secondary"
              disabled={detail.isFetching}
              onClick={() => void detail.refetch()}
            >
              {t("profile.retry", "Thử lại")}
            </button>
          </div>
        ) : (
          <>
            <div className="tix-admin-review-groups">
              {/* Event Preview Hero Card */}
              <article className="tix-admin-event-hero">
                <button
                  type="button"
                  className="tix-admin-event-poster"
                  disabled={photoSlides.length === 0}
                  onClick={() => {
                    if (photoSlides.length > 0) setLightboxIndex(0);
                  }}
                  aria-label={t("adminEventReview.previewBanner", "Xem ảnh sự kiện cỡ lớn")}
                >
                  <img
                    src={posterImage?.objectReference || "/event-placeholder.svg"}
                    alt={event.title}
                    onError={(e) => {
                      if (e.currentTarget.src.endsWith("/event-placeholder.svg")) return;
                      e.currentTarget.src = "/event-placeholder.svg";
                    }}
                  />
                  {photoSlides.length > 0 && (
                    <span className="tix-admin-event-poster-zoom">
                      <Images size={12} aria-hidden="true" />
                      <span>{photoSlides.length}</span>
                    </span>
                  )}
                </button>

                <div className="tix-admin-event-hero-body">
                  {(event.mode || categoryName || event.categoryId) && (
                    <div className="tix-admin-event-badges">
                      {event.mode && (
                        <span className="tix-admin-event-chip">
                          {t(`eventsV1.${event.mode}`, event.mode)}
                        </span>
                      )}
                      {(categoryName || event.categoryId) && (
                        <span className="tix-admin-event-chip">
                          <Tag size={12} aria-hidden="true" />
                          <span>{categoryName ?? shortId(event.categoryId ?? "")}</span>
                        </span>
                      )}
                    </div>
                  )}

                  <div className="tix-admin-event-title-wrap">
                    <span className="tix-admin-event-title-label">
                      {t("adminEventReview.eventNamePrefix", "Tên sự kiện:")}
                    </span>
                    <h3 className="tix-admin-event-hero-title">{event.title}</h3>
                  </div>

                  <div className="tix-admin-event-meta-list">
                    <div className="tix-admin-event-meta-item">
                      <span className="tix-admin-event-meta-icon" aria-hidden="true">
                        {organizerQuery.data?.logoUrl ? (
                          <img
                            className="tix-admin-organizer-avatar"
                            src={organizerQuery.data.logoUrl}
                            alt=""
                          />
                        ) : (
                          <Building2 size={15} />
                        )}
                      </span>
                      <span className="tix-admin-event-meta-strong">
                        {organizerQuery.data?.name ||
                          event.organizerSubject ||
                          t("profile.unavailable", "Chưa có thông tin")}
                      </span>
                    </div>

                    <div className="tix-admin-event-meta-item">
                      <span className="tix-admin-event-meta-icon" aria-hidden="true">
                        <MapPin size={15} />
                      </span>
                      <span>
                        {venue ? (
                          <>
                            <strong className="tix-admin-event-meta-strong">{venue.name}</strong>
                            {venue.address ? ` — ${venue.address}` : ""}
                          </>
                        ) : event.venueId ? (
                          <span>{event.venueId}</span>
                        ) : (
                          <em>{t("profile.unavailable", "Chưa có thông tin")}</em>
                        )}
                      </span>
                    </div>
                  </div>
                </div>

                {galleryImages.length > 0 && (
                  <div className="tix-admin-gallery-strip" aria-label={t("adminEventReview.gallery", "Thư viện ảnh")}>
                    {galleryImages.map((img, idx) => (
                      <button
                        key={img.imageId}
                        type="button"
                        className="tix-admin-gallery-thumb"
                        onClick={() => setLightboxIndex(idx + 1)}
                        aria-label={`${t("adminEventReview.galleryImage", "Ảnh sự kiện")} ${idx + 2}`}
                      >
                        <img
                          src={img.objectReference}
                          alt=""
                          onError={(e) => {
                            if (e.currentTarget.src.endsWith("/event-placeholder.svg")) return;
                            e.currentTarget.src = "/event-placeholder.svg";
                          }}
                        />
                      </button>
                    ))}
                  </div>
                )}
              </article>

              {lightboxSlides.length > 0 && (
                <Lightbox
                  open={lightboxIndex >= 0}
                  close={() => setLightboxIndex(-1)}
                  index={Math.max(0, lightboxIndex)}
                  slides={lightboxSlides}
                />
              )}

              {/* Group 1: Schedule, Sale Window & Limit */}
              {(event.startsAt ||
                event.endsAt ||
                event.saleStartsAt ||
                event.saleEndsAt ||
                typeof event.purchaseLimit === "number") && (
                <section className="tix-admin-review-group">
                  <span className="tix-admin-review-group-icon" aria-hidden="true">
                    <CalendarClock size={18} />
                  </span>
                  <div>
                    <div className="tix-admin-review-group-header">
                      <h3>{t("adminEventReview.saleGroupTitle", "Thông tin mở bán")}</h3>
                    </div>
                    <dl className="tix-admin-review-dl">
                      {(event.startsAt || event.endsAt) && (
                        <div>
                          <dt>{t("adminEventReview.eventScheduleLabel", "Thời gian diễn ra")}</dt>
                          <dd>
                            {formatDateTime(event.startsAt, locale)} —{" "}
                            {formatDateTime(event.endsAt, locale)}
                          </dd>
                        </div>
                      )}
                      {(event.saleStartsAt || event.saleEndsAt) && (
                        <div>
                          <dt>{t("adminEventReview.saleWindowLabel", "Thời gian mở bán")}</dt>
                          <dd>
                            {formatDateTime(event.saleStartsAt, locale)} —{" "}
                            {formatDateTime(event.saleEndsAt, locale)}
                          </dd>
                        </div>
                      )}
                      {typeof event.purchaseLimit === "number" && (
                        <div>
                          <dt>{t("adminEventReview.purchaseLimitLabel", "Giới hạn mua")}</dt>
                          <dd>
                            {event.purchaseLimit > 0
                              ? `${event.purchaseLimit} ${t("adminEventReview.ticketUnit", "vé")}`
                              : t("adminEventReview.unlimited", "Không giới hạn")}
                          </dd>
                        </div>
                      )}
                    </dl>
                  </div>
                </section>
              )}

              {/* Group 2: Event Description */}
              <section className="tix-admin-review-group">
                <span className="tix-admin-review-group-icon" aria-hidden="true">
                  <FileText size={18} />
                </span>
                <div>
                  <div className="tix-admin-review-group-header">
                    <h3>{t("adminEventReview.descriptionLabel", "Mô tả sự kiện")}</h3>
                  </div>
                  <div className="tix-admin-rich-description">
                    {renderedDescription ?? (
                      <em className="tix-admin-empty-text">
                        {t("adminEventReview.noDescription", "Chưa có mô tả.")}
                      </em>
                    )}
                  </div>
                </div>
              </section>

              {/* Group 3: Ticket Types */}
              <section className="tix-admin-review-group">
                <span className="tix-admin-review-group-icon" aria-hidden="true">
                  <Ticket size={18} />
                </span>
                <div>
                  <div className="tix-admin-review-group-header">
                    <h3>{t("eventsV1.ticketTypes", "Hạng vé")}</h3>
                  </div>
                  {sortedTicketTypes.length === 0 ? (
                    <span style={{ fontSize: 13.5, color: "var(--text-subtle, #565e69)" }}>
                      {t("adminEventReview.noTicketTypes", "Chưa có hạng vé nào.")}
                    </span>
                  ) : (
                    <div className="tix-admin-ticket-grid">
                      {sortedTicketTypes.map((tt) => (
                        <div key={tt.ticketTypeId} className="tix-admin-ticket-card">
                          <div className="tix-admin-ticket-card-main">
                            <span
                              className="tix-admin-ticket-swatch"
                              style={{ backgroundColor: tt.colorCode || "#3b82f6" }}
                              aria-hidden="true"
                            />
                            <span className="tix-admin-ticket-name" title={tt.name}>
                              {tt.name}
                            </span>
                          </div>
                          <span className="tix-admin-ticket-price">
                            {formatMoney(tt.unitPrice, tt.currency, locale)}
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </section>

              {/* Group 4: Promotions (when configured) */}
              {event.promotions && event.promotions.length > 0 && (
                <section className="tix-admin-review-group">
                  <span className="tix-admin-review-group-icon" aria-hidden="true">
                    <Tag size={18} />
                  </span>
                  <div>
                    <div className="tix-admin-review-group-header">
                      <h3>{t("adminEventReview.promotionsTitle", "Khuyến mãi")}</h3>
                    </div>
                    <div className="tix-admin-promo-list">
                      {event.promotions.map((promo) => {
                        const discountLabel = promo.discount?.percent
                          ? `-${promo.discount.percent}%`
                          : promo.discount?.amount
                            ? `-${formatMoney(promo.discount.amount, "VND", locale)}`
                            : "—";
                        return (
                          <div key={promo.promotionId} className="tix-admin-promo-item">
                            <div className="tix-admin-promo-top">
                              <span className="tix-admin-promo-code">{promo.code}</span>
                              <span className="tix-admin-promo-value">{discountLabel}</span>
                            </div>
                            <div className="tix-admin-promo-meta">
                              {formatDateTime(promo.startsAt, locale)} — {formatDateTime(promo.endsAt, locale)}
                            </div>
                            {promo.maximumUses && (
                              <div className="tix-admin-promo-meta">
                                {t("adminEventReview.maxUses", {
                                  count: Number(promo.maximumUses),
                                  defaultValue: `Tối đa ${promo.maximumUses} lượt dùng`,
                                })}
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </section>
              )}

              {/* Group 5: Seat Map Preview (when SEAT_MAP) */}
              {event.mode === "SEAT_MAP" && (
                <section className="tix-admin-review-group">
                  <span className="tix-admin-review-group-icon" aria-hidden="true">
                    <Armchair size={18} />
                  </span>
                  <div>
                    <div className="tix-admin-review-group-header">
                      <h3>{t("adminEventReview.seatMapPreviewTitle", "Sơ đồ chỗ ngồi")}</h3>
                      {seatMapPreviewUrl && (
                        <span style={{ fontSize: 12, color: "var(--text-subtle, #64748b)" }}>
                          {t("adminEventReview.clickSeatMapZoom", "Nhấn vào sơ đồ để phóng to")}
                        </span>
                      )}
                    </div>

                    {seatMapPreviewUrl ? (
                      <button
                        type="button"
                        className="tix-admin-seatmap-card"
                        onClick={() => setLightboxIndex(photoSlides.length)}
                        aria-label={t("adminEventReview.previewSeatMap", "Xem sơ đồ chỗ ngồi cỡ lớn")}
                      >
                        <img
                          src={seatMapPreviewUrl}
                          alt={`${event.title} - ${t("adminEventReview.seatMapPreviewTitle", "Sơ đồ chỗ ngồi")}`}
                        />
                      </button>
                    ) : (
                      <em className="tix-admin-empty-text">
                        {t("organizerSeatMap.noSeatMapPreview", "Chưa có dữ liệu sơ đồ ghế.")}
                      </em>
                    )}
                  </div>
                </section>
              )}
            </div>

            <form className="tix-admin-fee-form" onSubmit={onApprove}>
              <div className="tix-admin-fee-field">
                <label htmlFor="admin-event-fee-rate">
                  {t("eventsV1.feeLabel", "Tỷ lệ phí nền tảng (0 - 1)")}
                </label>
                <input
                  id="admin-event-fee-rate"
                  type="text"
                  className="tix-admin-input"
                  value={fee}
                  onChange={(e) => setFee(e.target.value)}
                  placeholder="0.05"
                />
                {!validFee && (
                  <span style={{ fontSize: "0.78rem", color: "#dc2626" }}>
                    {t(
                      "eventsV1.feeValidation",
                      "Nhập số thập phân hợp lệ từ 0 đến 1 (tối đa 8 chữ số thập phân).",
                    )}
                  </span>
                )}
              </div>

              <div className="tix-admin-actions-btns">
                <button
                  type="button"
                  className="tix-admin-btn tix-admin-btn-danger-outline"
                  disabled={act.isPending}
                  onClick={() => {
                    setMsg(null);
                    setErr(null);
                    act.mutate({ event, action: "return-to-draft" });
                  }}
                >
                  <span>{t("eventsV1.returnToDraft", "Trả về bản nháp")}</span>
                </button>
                <button
                  type="submit"
                  className="tix-admin-btn tix-admin-btn-primary"
                  disabled={!validFee || act.isPending}
                >
                  {act.isPending ? (
                    <LoaderCircle size={15} className="tix-spin" aria-hidden="true" />
                  ) : (
                    <Check size={15} aria-hidden="true" />
                  )}
                  <span>{t("eventsV1.approve", "Phê duyệt")}</span>
                </button>
              </div>
            </form>
          </>
        )}
      </section>
    </div>
  );
}

function CancellationRequestsQueue({ searchTerm }: { searchTerm: string }) {
  const { t } = useTranslation();
  const qc = useQueryClient();

  const requestsQuery = useInfiniteQuery({
    queryKey: ["admin-cancellation-requests", "PENDING"],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam, signal }) =>
      cancellationService.getAdminPendingCancellationRequests("PENDING", 20, pageParam, signal),
    getNextPageParam: (page, _, __, cursors) =>
      page.nextCursor && !cursors.includes(page.nextCursor) ? page.nextCursor : undefined,
  });

  const approveMutation = useMutation({
    mutationFn: ({ requestId, rowVersion }: { requestId: string; rowVersion: string }) =>
      cancellationService.approveCancellationRequest(requestId, rowVersion),
    onSuccess: async () => {
      toast.success(
        t(
          "adminEventReview.cancelApproveSuccess",
          "Đã phê duyệt yêu cầu hủy sự kiện. Hệ thống đang hoàn tiền cho người mua.",
        ),
      );
      await qc.invalidateQueries({ queryKey: ["admin-cancellation-requests"] });
    },
    onError: (err) => {
      const msg = isAxiosError<{ message?: string }>(err)
        ? err.response?.data?.message || t("eventsV1.error")
        : t("eventsV1.error");
      toast.error(msg);
    },
  });

  const rejectMutation = useMutation({
    mutationFn: ({
      requestId,
      rowVersion,
      reason,
    }: {
      requestId: string;
      rowVersion: string;
      reason: string;
    }) => cancellationService.rejectCancellationRequest(requestId, rowVersion, reason),
    onSuccess: async () => {
      toast.success(t("adminEventReview.cancelRejectSuccess", "Đã từ chối yêu cầu hủy."));
      await qc.invalidateQueries({ queryKey: ["admin-cancellation-requests"] });
    },
    onError: (err) => {
      const msg = isAxiosError<{ message?: string }>(err)
        ? err.response?.data?.message || t("eventsV1.error")
        : t("eventsV1.error");
      toast.error(msg);
    },
  });

  const handleApprove = async (req: CancellationRequest) => {
    const result = await Swal.fire({
      title: t("adminEventReview.cancelApproveConfirmTitle", "Phê duyệt hủy sự kiện?"),
      html: `<p style="font-size:14px;color:#334155;">Sự kiện <strong>${req.eventId}</strong> sẽ bị hủy ngay lập tức. Toàn bộ vé chưa check-in sẽ được hoàn tiền tự động.</p>`,
      icon: "warning",
      showCancelButton: true,
      confirmButtonText: t("adminEventReview.cancelApproveConfirmBtn", "Phê duyệt hủy"),
      cancelButtonText: t("common.cancel", "Hủy"),
      confirmButtonColor: "#dc2626",
    });

    if (result.isConfirmed) {
      approveMutation.mutate({ requestId: req.requestId, rowVersion: req.eventRowVersion });
    }
  };

  const handleReject = async (req: CancellationRequest) => {
    const result = await Swal.fire({
      title: t("adminEventReview.cancelRejectConfirmTitle", "Từ chối yêu cầu hủy"),
      input: "textarea",
      inputLabel: t("adminEventReview.cancelRejectReasonLabel", "Lý do từ chối (gửi tới Ban tổ chức)"),
      inputPlaceholder: t("adminEventReview.cancelRejectReasonPlaceholder", "Nhập lý do từ chối..."),
      showCancelButton: true,
      confirmButtonText: t("adminEventReview.cancelRejectConfirmBtn", "Xác nhận từ chối"),
      cancelButtonText: t("common.cancel", "Hủy"),
      confirmButtonColor: "#111827",
      inputValidator: (val) => {
        if (!val || val.trim().length < 5) {
          return t("adminEventReview.cancelRejectValidation", "Vui lòng nhập lý do tối thiểu 5 ký tự.");
        }
        return null;
      },
    });

    if (result.isConfirmed && result.value) {
      rejectMutation.mutate({
        requestId: req.requestId,
        rowVersion: req.eventRowVersion,
        reason: result.value.trim(),
      });
    }
  };

  const handleCopy = (text: string, label: string) => copyToClipboard(text, label, t);

  const allItems = useMemo(
    () => [
      ...new Map(
        (requestsQuery.data?.pages.flatMap((page) => page.items) ?? []).map((req) => [
          req.requestId,
          req,
        ]),
      ).values(),
    ],
    [requestsQuery.data],
  );

  const normalizedSearch = searchTerm.trim().toLowerCase();
  const items = useMemo(
    () =>
      normalizedSearch
        ? allItems.filter(
            (req) =>
              (req.requestId ?? "").toLowerCase().includes(normalizedSearch) ||
              (req.eventId ?? "").toLowerCase().includes(normalizedSearch) ||
              (req.requestedBy ?? "").toLowerCase().includes(normalizedSearch) ||
              (req.reason ?? "").toLowerCase().includes(normalizedSearch),
          )
        : allItems,
    [allItems, normalizedSearch],
  );

  return (
    <section className="tix-orders-card" aria-busy={requestsQuery.isFetching}>
      <div className="tix-card-header" style={{ marginBottom: 16 }}>
        <div>
          <h2 className="tix-card-title">
            {t("adminEventReview.cancellationsTitle", "Yêu cầu hủy sự kiện")}
          </h2>
          <p className="tix-card-subtitle">
            {requestsQuery.hasNextPage
              ? t("adminEventReview.cancellationsSubtitleHasMore", {
                  count: items.length,
                  loaded: allItems.length,
                  defaultValue: normalizedSearch
                    ? "{{count}} yêu cầu khớp trong {{loaded}} yêu cầu đã tải (vẫn còn trang sau)"
                    : "{{loaded}} yêu cầu đã tải (vẫn còn trang sau)",
                })
              : t("adminEventReview.cancellationsSubtitle", {
                  count: items.length,
                  defaultValue: "{{count}} yêu cầu đang chờ Admin xử lý",
                })}
          </p>
        </div>
      </div>

      {requestsQuery.isPending ? (
        <div className="tix-admin-state-box" role="status">
          <LoaderCircle size={26} className="tix-spin" aria-hidden="true" />
          <span>{t("eventsV1.loading", "Đang tải...")}</span>
        </div>
      ) : requestsQuery.isError ? (
        <div className="tix-admin-error-box" role="alert">
          <span>{t("adminEventReview.cancelLoadError", "Không thể tải danh sách yêu cầu hủy.")}</span>
          <button
            type="button"
            className="tix-admin-btn tix-admin-btn-secondary"
            disabled={requestsQuery.isFetching}
            onClick={() =>
              void (requestsQuery.isFetchNextPageError
                ? requestsQuery.fetchNextPage()
                : requestsQuery.refetch())
            }
          >
            {t("profile.retry", "Thử lại")}
          </button>
        </div>
      ) : items.length === 0 ? (
        <div className="tix-admin-state-box">
          <CheckCircle2 size={28} color="#16a34a" aria-hidden="true" />
          <span>
            {normalizedSearch
              ? requestsQuery.hasNextPage
                ? t(
                    "adminEventReview.emptyCancelSearchHasMore",
                    "Chưa thấy yêu cầu hủy khớp trong các trang đã tải. Hãy bấm Tải thêm để kiểm tra trang sau.",
                  )
                : t("adminEventReview.emptySearch", "Không tìm thấy yêu cầu phù hợp.")
              : t("adminEventReview.emptyCancellationsDesc", "Hiện không có sự kiện nào đang chờ duyệt hủy.")}
          </span>
        </div>
      ) : (
        <div className="tix-table-wrapper">
          <table className="tix-orders-table">
            <thead>
              <tr>
                <th scope="col">{t("adminEventReview.colRequestId", "Mã yêu cầu")}</th>
                <th scope="col">{t("adminEventReview.colEventId", "Mã sự kiện")}</th>
                <th scope="col">{t("adminEventReview.colRequestedBy", "Người gửi")}</th>
                <th scope="col">{t("adminEventReview.colRequestedAt", "Thời gian gửi")}</th>
                <th scope="col">{t("adminEventReview.colReason", "Lý do hủy")}</th>
                <th scope="col" style={{ textAlign: "left", width: "1%" }}>
                  {t("adminEventReview.colActions", "Thao tác")}
                </th>
              </tr>
            </thead>
            <tbody>
              {items.map((req) => (
                <tr key={req.requestId}>
                  <td>
                    <div className="tix-admin-id-cell">
                      <span title={req.requestId}>{shortId(req.requestId)}</span>
                      <button
                        type="button"
                        className="tix-admin-copy-icon-btn"
                        onClick={() => void handleCopy(req.requestId, "Request ID")}
                        aria-label={t("adminEventReview.copyRequestId", "Sao chép mã yêu cầu")}
                        title={req.requestId}
                      >
                        <Copy size={13} aria-hidden="true" />
                      </button>
                    </div>
                  </td>
                  <td>
                    <div className="tix-admin-id-cell">
                      <span title={req.eventId}>{shortId(req.eventId)}</span>
                      <button
                        type="button"
                        className="tix-admin-copy-icon-btn"
                        onClick={() => void handleCopy(req.eventId, "Event ID")}
                        aria-label={t("adminEventReview.copyEventId", "Sao chép mã sự kiện")}
                        title={req.eventId}
                      >
                        <Copy size={13} aria-hidden="true" />
                      </button>
                    </div>
                  </td>
                  <td>
                    <div className="tix-admin-id-cell">
                      <span title={req.requestedBy}>{shortId(req.requestedBy)}</span>
                      <button
                        type="button"
                        className="tix-admin-copy-icon-btn"
                        onClick={() => void handleCopy(req.requestedBy, "User ID")}
                        aria-label={t("adminEventReview.copyRequestedBy", "Sao chép mã người gửi")}
                        title={req.requestedBy}
                      >
                        <Copy size={13} aria-hidden="true" />
                      </button>
                    </div>
                  </td>
                  <td style={{ whiteSpace: "nowrap", color: "var(--text-subtle, #565e69)" }}>
                    {formatDateTime(req.requestedAt)}
                  </td>
                  <td className="tix-admin-reason-cell">{req.reason}</td>
                  <td style={{ textAlign: "left", whiteSpace: "nowrap", width: "1%" }}>
                    <div className="tix-admin-table-actions">
                      <button
                        type="button"
                        className="tix-admin-btn tix-admin-btn-danger"
                        style={{ minHeight: 32, padding: "5px 12px", fontSize: "0.8rem" }}
                        disabled={approveMutation.isPending || rejectMutation.isPending}
                        onClick={() => void handleApprove(req)}
                      >
                        <span>{t("adminEventReview.approveCancelBtn", "Duyệt hủy")}</span>
                      </button>
                      <button
                        type="button"
                        className="tix-admin-btn tix-admin-btn-secondary"
                        style={{ minHeight: 32, padding: "5px 12px", fontSize: "0.8rem" }}
                        disabled={approveMutation.isPending || rejectMutation.isPending}
                        onClick={() => void handleReject(req)}
                      >
                        <span>{t("adminEventReview.rejectCancelBtn", "Từ chối")}</span>
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {requestsQuery.hasNextPage && !requestsQuery.isFetchNextPageError && (
        <div style={{ display: "flex", justifyContent: "center", marginTop: 14 }}>
          <button
            type="button"
            className="tix-admin-btn tix-admin-btn-secondary"
            onClick={() => void requestsQuery.fetchNextPage()}
            disabled={requestsQuery.isFetchingNextPage}
          >
            {requestsQuery.isFetchingNextPage ? (
              <LoaderCircle size={15} className="tix-spin" aria-hidden="true" />
            ) : null}
            <span>
              {requestsQuery.isFetchingNextPage
                ? t("eventsV1.loading", "Đang tải...")
                : t("eventsV1.loadMore", "Tải thêm")}
            </span>
          </button>
        </div>
      )}
    </section>
  );
}

export function AdminEventReviewPage() {
  const { t } = useTranslation();
  const { keycloak, initialized } = useKeycloak();
  const [activeTab, setActiveTab] = useState<AdminEventTab>("NEW_EVENTS");
  const [searchTerm, setSearchTerm] = useState("");

  if (!initialized) {
    return (
      <div className="tix-dashboard-page tix-admin-page">
        <div className="tix-dashboard-container">
          <TixAdminTopNav activeTab="events" />
          <div className="tix-orders-card tix-admin-state-box" role="status">
            <LoaderCircle size={26} className="tix-spin" aria-hidden="true" />
            <span>{t("eventsV1.loading", "Đang tải...")}</span>
          </div>
        </div>
      </div>
    );
  }

  const isAuthenticated = Boolean(keycloak.authenticated);
  const isAdmin = hasRealmRole(keycloak.tokenParsed, "ADMIN");

  if (!isAuthenticated || !isAdmin) {
    return (
      <div className="tix-dashboard-page tix-admin-page">
        <div className="tix-dashboard-container">
          <TixAdminTopNav activeTab="events" />
          <div className="tix-orders-card tix-admin-state-box" role="alert">
            <ShieldAlert size={32} color="#dc2626" aria-hidden="true" />
            <strong style={{ color: "var(--text-strong, #15171a)" }}>
              {t("adminEventReview.accessDeniedTitle", "Truy cập bị từ chối")}
            </strong>
            <span>
              {t(
                "adminEventReview.accessDeniedDesc",
                "Bạn cần đăng nhập với tài khoản Quản trị viên (ADMIN) để phê duyệt sự kiện.",
              )}
            </span>
            {!isAuthenticated && (
              <button
                type="button"
                className="tix-admin-btn tix-admin-btn-primary"
                onClick={() => void keycloak.login()}
              >
                {t("nav.login", "Đăng nhập")}
              </button>
            )}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="tix-dashboard-page tix-admin-page">
      <div className="tix-dashboard-container">
        <TixAdminTopNav activeTab="events" />

        {/* Overview Header */}
        <div className="tix-overview-header">
          <div className="tix-overview-header-left">
            <div className="tix-overview-title-block">
              <h1 className="tix-overview-main-title">
                {t("adminEventReview.pageTitle", "Duyệt sự kiện")}
              </h1>
              <p className="tix-overview-subtitle">
                {t(
                  "adminEventReview.pageSubtitle",
                  "Phê duyệt sự kiện mới, thiết lập tỷ lệ phí nền tảng và xử lý yêu cầu hủy sự kiện.",
                )}
              </p>
            </div>
          </div>
        </div>

        {/* Sub-tabs & Search */}
        <div className="tix-subtabs-row">
          <div className="tix-subtabs-group" role="group" aria-label={t("adminEventReview.pageTitle", "Duyệt sự kiện")}>
            <button
              type="button"
              className={`tix-subtab-btn ${activeTab === "NEW_EVENTS" ? "active" : ""}`}
              onClick={() => setActiveTab("NEW_EVENTS")}
            >
              <Clock size={14} aria-hidden="true" />
              <span>{t("adminEventReview.tabNewEvents", "Duyệt sự kiện mới")}</span>
            </button>
            <button
              type="button"
              className={`tix-subtab-btn ${activeTab === "CANCELLATIONS" ? "active" : ""}`}
              onClick={() => setActiveTab("CANCELLATIONS")}
            >
              <Ban size={14} aria-hidden="true" />
              <span>{t("adminEventReview.tabCancellations", "Duyệt hủy sự kiện")}</span>
            </button>
          </div>

          <div className="tix-search-input-wrap">
            <Search size={15} aria-hidden="true" />
            <input
              type="search"
              className="tix-search-input"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder={
                activeTab === "NEW_EVENTS"
                  ? t("adminEventReview.searchPlaceholder", "Tìm theo tên sự kiện, Event ID...")
                  : t("adminEventReview.searchCancelPlaceholder", "Tìm mã yêu cầu, Event ID, người gửi...")
              }
            />
            {searchTerm && (
              <button
                type="button"
                className="tix-search-clear-btn"
                onClick={() => setSearchTerm("")}
                aria-label={t("organizerHub.clearSearch", "Xóa từ khóa tìm kiếm")}
              >
                <X size={13} />
              </button>
            )}
          </div>
        </div>

        {activeTab === "NEW_EVENTS" ? (
          <ReviewQueue searchTerm={searchTerm} />
        ) : (
          <CancellationRequestsQueue searchTerm={searchTerm} />
        )}
      </div>
    </div>
  );
}

export default AdminEventReviewPage;
