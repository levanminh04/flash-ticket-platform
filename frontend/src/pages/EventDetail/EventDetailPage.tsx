import { type CSSProperties, useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Armchair, ArrowRight, Building2, Calendar, Clock, MapPin, Share2 } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { useTranslation } from "react-i18next";
import Lightbox from "yet-another-react-lightbox";
import "yet-another-react-lightbox/styles.css";
import { eventService } from "../../services/eventService";
import { bookingService } from "../../services/bookingService";
import { organizerService } from "../../services/organizerService";
import { categoryService } from "../../services/categoryService";
import Footer from "../../components/common/Footer";
import { descriptionContent } from "../../lib/eventDescription";
import "./event-detail.css";

const timeZone = "Asia/Ho_Chi_Minh";
const placeholder = "/event-placeholder.svg";
type TabKey = "about" | "photos" | "organizer";

export default function EventDetailPage() {
  const { i18n, t } = useTranslation();
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const locale = i18n.resolvedLanguage?.startsWith("en") ? "en-US" : "vi-VN";
  const [selection, setSelection] = useState<{ eventId: string; ticketId: string }>();
  const [gallery, setGallery] = useState<{ eventId: string; index: number }>();
  const [failedPoster, setFailedPoster] = useState<string>();
  const [activeTab, setActiveTab] = useState<TabKey>("about");
  const [failedLogo, setFailedLogo] = useState<string>();
  const [shareFeedback, setShareFeedback] = useState("");

  useEffect(() => {
    setActiveTab("about");
    setShareFeedback("");
    window.scrollTo({ top: 0 });
  }, [slug]);

  const eventQuery = useQuery({
    queryKey: ["public-event-detail", slug],
    queryFn: ({ signal }) => eventService.getEventDetails(slug!, signal),
    enabled: Boolean(slug),
    retry: false,
  });
  const event = eventQuery.data;
  const eventId = event?.id;
  const organizerSubject = event?.organizer?.id;
  const categoryId = event?.categoryId || undefined;

  const organizerQuery = useQuery({
    queryKey: ["public-event-organizer", organizerSubject],
    queryFn: ({ signal }) => organizerService.getPublicOrganizerBySlug(organizerSubject!, signal),
    enabled: Boolean(organizerSubject),
    retry: false,
  });

  const availabilityQuery = useQuery({
    queryKey: ["public-event-availability", eventId],
    queryFn: ({ signal }) => bookingService.getAvailability(eventId!, signal),
    enabled: Boolean(eventId),
  });

  const categoriesQuery = useQuery({
    queryKey: ["public-categories"],
    queryFn: categoryService.getCategories,
    staleTime: 5 * 60 * 1000,
  });

  const sameCategoryQuery = useQuery({
    queryKey: ["public-related-events-category", eventId, categoryId],
    queryFn: ({ signal }) => eventService.getEvents({ category: categoryId, size: 5 }, signal),
    enabled: Boolean(eventId && categoryId),
    retry: 1,
  });

  const sameCategoryEvents = useMemo(
    () => (sameCategoryQuery.data?.content ?? []).filter(item => item.id !== eventId).slice(0, 4),
    [sameCategoryQuery.data, eventId],
  );

  const shouldUseFallback = Boolean(eventId)
    && (!categoryId || (sameCategoryQuery.isSuccess && sameCategoryEvents.length === 0));

  const fallbackEventsQuery = useQuery({
    queryKey: ["public-related-events-upcoming", eventId],
    queryFn: ({ signal }) => eventService.getEvents({ size: 5 }, signal),
    enabled: shouldUseFallback,
    retry: 1,
  });

  const fallbackEvents = useMemo(
    () => (fallbackEventsQuery.data?.content ?? []).filter(item => item.id !== eventId).slice(0, 4),
    [fallbackEventsQuery.data, eventId],
  );

  const categoryNameById = useMemo(
    () => new Map((categoriesQuery.data ?? []).map(cat => [cat.id, cat.name])),
    [categoriesQuery.data],
  );

  const description = event?.description?.trim() || "";
  const content = useMemo(() => descriptionContent(description), [description]);

  if (eventQuery.isLoading || !event) {
    const missing = !eventQuery.error || (isAxiosError(eventQuery.error) && [400, 404].includes(eventQuery.error.response?.status ?? 0));
    return (
      <div className="event-editorial">
        <main className="event-editorial-shell">
          {eventQuery.isLoading ? (
            <div role="status" aria-label={t("eventDetail.loading")}>
              <div className="event-editorial-hero" aria-hidden="true">
                <div className="event-editorial-poster event-editorial-skeleton" />
                <div className="event-editorial-skeleton event-editorial-skeleton-copy" />
              </div>
              <div className="event-editorial-lower" aria-hidden="true">
                <div />
                <div className="event-editorial-skeleton event-editorial-skeleton-copy" />
              </div>
            </div>
          ) : (
            <div className="event-editorial-message">
              <h1>{t(missing ? "eventDetail.notFound" : "eventDetail.loadFailed")}</h1>
              <Link className="btn btn-primary" to="/search">{t("nav.events")}</Link>
            </div>
          )}
        </main>
        <Footer />
      </div>
    );
  }

  const organizer = organizerQuery.data ?? (event.organizer?.name ? event.organizer : undefined);
  const availability = !availabilityQuery.isError && availabilityQuery.data?.eventId === event.id ? availabilityQuery.data : undefined;
  const byType = new Map(availability?.ticketTypes.map(type => [type.ticketTypeId, Number(type.available)]));
  const bySector = new Map(availability?.sectors.map(sector => [sector.sectorId, Number(sector.available)]));
  const sectorColorById = new Map(
    (event.seatMap?.sectors ?? [])
      .filter(sector => Boolean(sector.colorCode?.trim()))
      .map(sector => [sector.id, sector.colorCode!.trim()]),
  );
  const ticketColor = (ticket: NonNullable<typeof event.ticketTypes>[number]) =>
    ticket.colorCode?.trim() || (ticket.sectorId ? sectorColorById.get(ticket.sectorId) : undefined);
  const seatMap = event.mode === "SEAT_MAP";
  const ticketPrice = (ticket: NonNullable<typeof event.ticketTypes>[number]) => BigInt(ticket.unitPrice ?? ticket.price);
  const tickets = [...(event.ticketTypes ?? [])].sort((a, b) => ticketPrice(a) < ticketPrice(b) ? -1 : ticketPrice(a) > ticketPrice(b) ? 1 : 0);
  const remaining = (ticket: typeof tickets[number]) => {
    const count = byType.get(ticket.id);
    if (count === undefined || !Number.isSafeInteger(count) || count < 0) return undefined;
    if (!seatMap || count === 0) return count;
    const sectorCount = ticket.sectorId ? bySector.get(ticket.sectorId) : undefined;
    return sectorCount === undefined || !Number.isSafeInteger(sectorCount) || sectorCount < 0
      ? undefined : Math.min(count, sectorCount);
  };
  const canBook = (!seatMap || Boolean(event.seatMap)) && tickets.some(ticket => (remaining(ticket) ?? 0) > 0);
  const soldOut = tickets.length > 0 && tickets.every(ticket => remaining(ticket) === 0);

  // Auto-select first available ticket if nothing selected yet or currently selected ticket is invalid
  const availableTickets = tickets.filter(ticket => (remaining(ticket) ?? 0) > 0);
  const selectedTicketId = selection?.eventId === event.id
    ? selection.ticketId
    : availableTickets[0]?.id;
  const selectedTicket = tickets.find(ticket => ticket.id === selectedTicketId && (remaining(ticket) ?? 0) > 0);
  const canContinue = seatMap ? canBook : (canBook && Boolean(selectedTicket));

  const bookPath = `/events/${event.id}/book`;
  const minPrice = event.minPriceAmount !== undefined ? BigInt(event.minPriceAmount) : event.minPrice ?? (tickets[0] && ticketPrice(tickets[0]));
  const formatPrice = (amount: bigint | number) => {
    if (amount === 0 || amount === 0n) return t("eventDetail.free");
    return `${new Intl.NumberFormat(locale).format(amount)} đ`;
  };

  const start = new Date(event.startDatetime ?? "");
  const end = new Date(event.endDatetime ?? "");
  const validStart = Number.isFinite(start.getTime());
  const validEnd = Number.isFinite(end.getTime());
  const day = new Intl.DateTimeFormat(locale, { timeZone, day: "2-digit", month: "2-digit", year: "numeric" });
  const time = new Intl.DateTimeFormat(locale, { timeZone, hour: "2-digit", minute: "2-digit" });

  const heroImage = event.images?.find(image => image.type === "BANNER" && image.url)?.url
    || event.bannerUrl || placeholder;
  const showBackdrop = Boolean(heroImage) && heroImage !== placeholder && heroImage !== failedPoster;
  const galleryImages = [...new Set((event.images ?? []).flatMap(image => image.type === "GALLERY" && image.url ? [image.url] : []))];

  const onImageError = (image: HTMLImageElement) => {
    if (image.getAttribute("src") !== placeholder) image.src = placeholder;
  };

  const share = async () => {
    const url = window.location.href;
    try {
      if (navigator.share) {
        await navigator.share({ title: event.title, url });
        return;
      }
      await navigator.clipboard.writeText(url);
      setShareFeedback(t("eventDetail.linkCopied"));
    } catch (error) {
      if (!(error instanceof DOMException && error.name === "AbortError")) setShareFeedback(t("eventDetail.shareFailed"));
    }
  };

  const availableTabs = [
    description ? { id: "about" as const, label: locale === "vi-VN" ? "Giới thiệu" : "Overview" } : null,
    galleryImages.length > 0 ? { id: "photos" as const, label: locale === "vi-VN" ? "Hình ảnh" : "Photos" } : null,
    (organizer || organizerQuery.isError) ? { id: "organizer" as const, label: t("eventDetail.organizer") } : null,
  ].filter((item): item is { id: TabKey; label: string } => Boolean(item));

  const currentTab = availableTabs.some(tab => tab.id === activeTab)
    ? activeTab
    : (availableTabs[0]?.id ?? "about");

  const currentCategoryName = categoryId ? categoryNameById.get(categoryId) : undefined;
  const showingSameCategory = Boolean(categoryId) && sameCategoryEvents.length > 0;
  const relatedEvents = showingSameCategory ? sameCategoryEvents : fallbackEvents;
  const relatedPending = (Boolean(categoryId) && sameCategoryQuery.isPending)
    || (shouldUseFallback && fallbackEventsQuery.isPending);
  const relatedError = (Boolean(categoryId) && sameCategoryQuery.isError)
    || (shouldUseFallback && fallbackEventsQuery.isError);
  const showRelatedSection = relatedPending || relatedError || relatedEvents.length > 0;
  const exploreMorePath = showingSameCategory && categoryId
    ? `/search?category=${encodeURIComponent(categoryId)}`
    : "/search";
  const relatedCardDate = new Intl.DateTimeFormat(locale, {
    timeZone,
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });

  return (
    <div className="event-editorial">
      {showBackdrop && (
        <div className="event-editorial-backdrop" aria-hidden="true">
          <img src={heroImage} alt="" onError={() => setFailedPoster(heroImage)} />
          <div className="event-editorial-backdrop-overlay" />
        </div>
      )}
      <main className="event-editorial-shell">
        <section className="event-editorial-hero" aria-labelledby="event-editorial-title">
          <img
            key={heroImage}
            className="event-editorial-poster"
            src={heroImage}
            alt={event.title}
            onError={e => {
              setFailedPoster(heroImage);
              onImageError(e.currentTarget);
            }}
          />
          <div className="event-editorial-intro">
            <h1 id="event-editorial-title">{event.title}</h1>
            {event.shortDescription?.trim() && (
              <p className="event-editorial-short-desc" style={{ color: "var(--text-secondary)", fontSize: "1.02rem", lineHeight: 1.55, marginBlock: "8px 14px", maxWidth: "60ch" }}>
                {event.shortDescription}
              </p>
            )}
            <div className="event-editorial-meta">
              {validStart && (
                <div className="event-editorial-meta-item">
                  <Calendar size={18} aria-hidden="true" />
                  <span className="event-editorial-meta-text">
                    <span className="event-editorial-meta-label">{t("eventDetail.startTime")}:</span>{" "}
                    {`${time.format(start)} · ${day.format(start)}`}
                  </span>
                </div>
              )}
              {validEnd && (
                <div className="event-editorial-meta-item">
                  <Clock size={18} aria-hidden="true" />
                  <span className="event-editorial-meta-text">
                    <span className="event-editorial-meta-label">{t("eventDetail.endTime")}:</span>{" "}
                    {`${time.format(end)} · ${day.format(end)}`}
                  </span>
                </div>
              )}
              <div className="event-editorial-meta-item">
                <MapPin size={18} aria-hidden="true" />
                <span className="event-editorial-meta-text">
                  {event.venueName || t("profile.unavailable")}
                </span>
              </div>
            </div>

            {shareFeedback && <p className="event-editorial-feedback" role="status">{shareFeedback}</p>}

            <div className="event-editorial-actions">
              <div className="event-editorial-price-wrap">
                {minPrice === undefined ? (
                  <p className="event-editorial-price">{t("eventDetail.priceUpdating")}</p>
                ) : (
                  <p className="event-editorial-price">
                    {minPrice !== 0 && minPrice !== 0n && (
                      <span className="event-editorial-price-label">{t("eventDetail.fromPrice")}</span>
                    )}
                    <strong>{formatPrice(minPrice)}</strong>
                  </p>
                )}
              </div>
              <button
                className="event-editorial-share"
                type="button"
                aria-label={t("eventDetail.share")}
                onClick={() => void share()}
              >
                <Share2 size={16} aria-hidden="true" />
                <span>{t("eventDetail.share")}</span>
              </button>
            </div>
          </div>
        </section>

        <div className="event-editorial-lower">
          <div className="event-editorial-content">
            {availableTabs.length > 0 && (
              <nav className="event-editorial-tabs" aria-label={t("eventDetail.about")}>
                {availableTabs.map(tab => (
                  <a
                    key={tab.id}
                    href={`#section-${tab.id}`}
                    aria-current={currentTab === tab.id ? "location" : undefined}
                    className={`event-editorial-tab${currentTab === tab.id ? " is-active" : ""}`}
                    onClick={(eventClick) => {
                      eventClick.preventDefault();
                      setActiveTab(tab.id);
                      document.getElementById(`section-${tab.id}`)?.scrollIntoView({
                        behavior: "smooth",
                        block: "start",
                      });
                    }}
                  >
                    {tab.label}
                  </a>
                ))}
              </nav>
            )}

            <div className="event-editorial-tabpanels">
              {description && (
                <section id="section-about" aria-labelledby="heading-about" className="event-editorial-panel">
                  <h2 id="heading-about" className="event-editorial-section-title">{t("eventDetail.about")}</h2>
                  <div className="event-editorial-description">{content}</div>
                </section>
              )}

              {galleryImages.length > 0 && (
                <section id="section-photos" aria-labelledby="heading-photos" className="event-editorial-panel">
                  <div className="event-editorial-section-head">
                    <h2 id="heading-photos" className="event-editorial-section-title">{t("eventDetail.gallery")}</h2>
                    <p className="event-editorial-section-subtitle">{t("eventDetail.eventGalleryDescription")}</p>
                  </div>
                  <div className="event-editorial-gallery">
                    {galleryImages.map((url, index) => (
                      <button
                        type="button"
                        key={url}
                        onClick={() => setGallery({ eventId: event.id, index })}
                        aria-label={`${t("eventDetail.gallery")} ${index + 1}`}
                      >
                        <img src={url} alt="" loading="lazy" onError={e => onImageError(e.currentTarget)} />
                      </button>
                    ))}
                  </div>
                  <Lightbox
                    open={gallery?.eventId === event.id}
                    index={gallery?.index ?? 0}
                    close={() => setGallery(undefined)}
                    slides={galleryImages.map(src => ({ src }))}
                  />
                </section>
              )}

              {(organizer || organizerQuery.isError) && (
                <section id="section-organizer" aria-labelledby="heading-organizer" className="event-editorial-panel">
                  <h2 id="heading-organizer" className="event-editorial-section-title">{t("eventDetail.organizer")}</h2>
                  <div className="event-editorial-organizer">
                    <div className="event-editorial-organizer-logo">
                      {organizer?.logoUrl && organizer.logoUrl !== failedLogo ? (
                        <img
                          src={organizer.logoUrl}
                          alt=""
                          loading="lazy"
                          onError={() => setFailedLogo(organizer.logoUrl ?? undefined)}
                        />
                      ) : (
                        <Building2 size={27} aria-hidden="true" />
                      )}
                    </div>
                    <div className="event-editorial-organizer-info">
                      <h3>{organizer?.name || t("eventDetail.organizer")}</h3>
                      <p>{organizer?.description || t("profile.unavailable")}</p>
                    </div>
                    {organizer?.slug && (
                      <Link to={`/organizers/${organizer.slug}`} className="event-editorial-organizer-link">
                        {t("eventDetail.viewOrganizer")} <ArrowRight size={17} aria-hidden="true" />
                      </Link>
                    )}
                  </div>
                </section>
              )}
            </div>
          </div>

          <aside className="event-editorial-tickets" aria-labelledby="event-tickets-title">
            <h2 id="event-tickets-title">{t("eventDetail.selectTicketType")}</h2>
            <div className="event-editorial-ticket-list" role={seatMap ? undefined : "radiogroup"} aria-labelledby="event-tickets-title">
              {tickets.map(ticket => {
                const count = remaining(ticket);
                const isSelected = !seatMap && selectedTicket?.id === ticket.id;
                const isDisabled = count === 0 || count === undefined;
                const color = ticketColor(ticket);
                return (
                  <label
                    className={`event-editorial-ticket${isSelected ? " is-selected" : ""}${isDisabled ? " is-disabled" : ""}`}
                    data-has-color={color ? "true" : undefined}
                    style={color ? ({ "--ticket-color": color } as CSSProperties) : undefined}
                    key={ticket.id}
                  >
                    {!seatMap && (
                      <input
                        type="radio"
                        name="event-ticket-type"
                        checked={isSelected}
                        disabled={isDisabled}
                        onChange={() => setSelection({ eventId: event.id, ticketId: ticket.id })}
                      />
                    )}
                    <span className="event-editorial-ticket-main">
                      <strong>{ticket.name}</strong>
                      {count !== undefined && (
                        <small data-sold-out={count === 0}>
                          {count === 0 ? t("eventDetail.soldOut") : t("eventDetail.ticketsRemaining", { count })}
                        </small>
                      )}
                    </span>
                    <strong className="event-editorial-ticket-price">{formatPrice(ticketPrice(ticket))}</strong>
                  </label>
                );
              })}
            </div>
            {tickets.length === 0 && <p className="event-editorial-ticket-status">{t("eventDetail.ticketTypeEmpty")}</p>}
            {availabilityQuery.isPending && <p className="event-editorial-ticket-status" role="status">{t("eventDetail.loading")}</p>}
            {!availabilityQuery.isPending && !canBook && !soldOut && tickets.length > 0 && (
              <p className="event-editorial-ticket-status" role="status">{t("eventDetail.availabilityUnavailable")}</p>
            )}
            {seatMap && event.seatMap && canBook && (
              <Link className="event-editorial-seatmap" to={bookPath}>
                <Armchair size={22} aria-hidden="true" />
                <span>{t("eventDetail.viewSeatMap")}</span>
                <ArrowRight size={18} aria-hidden="true" />
              </Link>
            )}
            <button
              type="button"
              className="btn btn-primary event-editorial-book"
              disabled={!canContinue}
              onClick={() => navigate(selectedTicket ? `${bookPath}?ticketType=${encodeURIComponent(selectedTicket.id)}` : bookPath)}
            >
              <span>{t(soldOut ? "eventDetail.soldOut" : seatMap ? "eventDetail.chooseSeats" : "eventDetail.continueTickets")}</span>
              {canContinue && <ArrowRight size={18} aria-hidden="true" />}
            </button>
          </aside>
        </div>

        {showRelatedSection && (
          <section className="event-editorial-related" aria-labelledby="event-related-title">
            <div className="event-editorial-related-head">
              <div className="event-editorial-related-heading">
                <h2 id="event-related-title">{t("eventDetail.youMayLike")}</h2>
                {!relatedPending && !relatedError && (
                  <p className="event-editorial-related-subtitle">
                    {showingSameCategory && currentCategoryName
                      ? t("eventDetail.youMayLikeSameCategory", { category: currentCategoryName })
                      : t("eventDetail.youMayLikeUpcoming")}
                  </p>
                )}
              </div>
              <Link className="event-editorial-related-more" to={exploreMorePath}>
                <span>{t("eventDetail.exploreMoreEvents")}</span>
                <ArrowRight size={16} aria-hidden="true" />
              </Link>
            </div>

            {relatedPending ? (
              <div className="event-editorial-related-grid" role="status" aria-label={t("eventDetail.loading")}>
                {Array.from({ length: 4 }).map((_, index) => (
                  <div key={index} className="event-editorial-related-skeleton" aria-hidden="true" />
                ))}
              </div>
            ) : relatedError ? (
              <div className="event-editorial-related-error" role="alert">
                <p>{t("eventDetail.relatedEventsLoadFailed")}</p>
                <button
                  type="button"
                  onClick={() => {
                    if (categoryId && sameCategoryQuery.isError) void sameCategoryQuery.refetch();
                    if (shouldUseFallback && fallbackEventsQuery.isError) void fallbackEventsQuery.refetch();
                  }}
                >
                  {t("home.events.retry")}
                </button>
              </div>
            ) : (
              <div className="event-editorial-related-grid">
                {relatedEvents.map((item) => {
                  const itemStart = new Date(item.startDatetime);
                  const validItemStart = Number.isFinite(itemStart.getTime());
                  const itemCategoryName = item.categoryId ? categoryNameById.get(item.categoryId) : undefined;
                  const itemMinPrice = item.minPriceAmount !== undefined
                    ? BigInt(item.minPriceAmount)
                    : item.minPrice !== undefined
                      ? BigInt(Math.round(item.minPrice))
                      : undefined;
                  const itemImage = item.bannerUrl || placeholder;
                  return (
                    <Link
                      key={item.id}
                      className="event-editorial-related-card"
                      to={`/event/${encodeURIComponent(item.slug || item.id)}`}
                    >
                      <div className="event-editorial-related-media">
                        <img
                          src={itemImage}
                          alt=""
                          loading="lazy"
                          onError={e => onImageError(e.currentTarget)}
                        />
                      </div>
                      <div className="event-editorial-related-body">
                        <div className="event-editorial-related-meta-top">
                          {validItemStart && (
                            <time dateTime={item.startDatetime}>
                              <Calendar size={13} aria-hidden="true" />
                              <span>{relatedCardDate.format(itemStart)}</span>
                            </time>
                          )}
                          {itemCategoryName && (
                            <span className="event-editorial-related-tag">{itemCategoryName}</span>
                          )}
                        </div>
                        <h3>{item.title}</h3>
                        <p className="event-editorial-related-venue">
                          <MapPin size={14} aria-hidden="true" />
                          <span>{item.venueName || t("profile.unavailable")}</span>
                        </p>
                        <div className="event-editorial-related-footer">
                          <span className="event-editorial-related-price">
                            {itemMinPrice === undefined
                              ? t("eventDetail.priceUpdating")
                              : itemMinPrice === 0n
                                ? t("eventDetail.free")
                                : `${t("eventDetail.fromPrice")} ${formatPrice(itemMinPrice)}`}
                          </span>
                          <span className="event-editorial-related-arrow" aria-hidden="true">
                            <ArrowRight size={15} />
                          </span>
                        </div>
                      </div>
                    </Link>
                  );
                })}
              </div>
            )}
          </section>
        )}
      </main>
      <Footer />
    </div>
  );
}
