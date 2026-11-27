import Footer from "../../components/common/Footer";
import MovingLogoStrip from "../../components/common/MovingLogoStrip";
import EventCategoriesSection from "./EventCategoriesSection";
import FaqSection from "./FaqSection";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useKeycloak } from "@react-keycloak/web";
import {
  ArrowLeft,
  ArrowRight,
  Building2,
  Calendar,
  Check,
  ChevronRight,
  Drama,
  Film,
  Footprints,
  GraduationCap,
  MapPin,
  Mic,
  Moon,
  Mountain,
  Music4,
  Palette,
  QrCode,
  Search,
  Tags,
  Ticket,
  Trophy,
  Users,
  Utensils,
} from "lucide-react";
import { Trans, useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { Fragment, useEffect, useState, type ReactNode } from "react";
import { categoryService } from "../../services/categoryService";
import { eventService } from "../../services/eventService";
import { organizerService, type OrganizerProfile } from "../../services/organizerService";
import { venueService } from "../../services/venueService";
import type { EventSummary, Venue } from "../../types/api";
import "./discovery.css";

const placeholder = "/event-placeholder.svg";
const heroMockImages = [
  "/promo/mock-event-concert.jpg",
  "/promo/mock-event-art.jpg",
];

const categoryIcons = [
  { match: /chay.*(?:dia hinh|duong mon)|trail run/, Icon: Mountain },
  { match: /chay.*dem|night run/, Icon: Moon },
  { match: /chay bo|giai chay|marathon|running/, Icon: Footprints },
  { match: /nhac song|live music/, Icon: Mic },
  { match: /am nhac|music|concert/, Icon: Music4 },
  { match: /san khau|kich|theatre|theater|comedy/, Icon: Drama },
  { match: /nghe thuat|trien lam|art\b|exhibition/, Icon: Palette },
  { match: /the thao|sport|fitness/, Icon: Trophy },
  { match: /workshop|hoc tap|giao duc|education/, Icon: GraduationCap },
  { match: /hoi thao|cong dong|conference|community/, Icon: Users },
  { match: /am thuc|food|culinary/, Icon: Utensils },
  { match: /phim|cinema|film/, Icon: Film },
];

function categoryIcon(name: string) {
  const normalized = name.toLowerCase().normalize("NFD").replace(/\p{M}/gu, "").replace(/đ/g, "d");
  return categoryIcons.find(({ match }) => match.test(normalized))?.Icon ?? Tags;
}

function eventLink(event: EventSummary) {
  return `/event/${encodeURIComponent(event.slug || event.id)}`;
}

function EventImage({ event }: { event: EventSummary }) {
  return <img src={event.bannerUrl || placeholder} alt="" loading="lazy"
    onError={({ currentTarget }) => {
      if (!currentTarget.src.endsWith(placeholder)) currentTarget.src = placeholder;
    }} />;
}

function VenueImage({ venue }: { venue: Venue }) {
  const [failed, setFailed] = useState(false);
  const src = venue.imageUrl || venue.imageUrls?.[0];
  if (!src || failed) return <Building2 size={32} strokeWidth={1.4} />;
  return <img src={src} alt={venue.name} loading="lazy" onError={() => setFailed(true)} />;
}

function Section({ id, title, children, link, className }: {
  id: string; title: string; children: ReactNode; link?: { to: string; label: string }; className?: string;
}) {
  return <section className={className ? `discovery-section ${className}` : "discovery-section"} aria-labelledby={id}>
    <div className="discovery-section-head">
      <h2 id={id}>{title}</h2>
      {link && <Link to={link.to}>{link.label}<ArrowRight size={16} aria-hidden="true" /></Link>}
    </div>
    {children}
  </section>;
}

function QueryState({ pending, error, empty, retry }: {
  pending: boolean; error: boolean; empty: boolean; retry: () => void;
}) {
  const { t } = useTranslation();
  if (pending) return <div className="discovery-skeleton" role="status"><span className="sr-only">{t("home.events.loading")}</span></div>;
  if (error) return <div className="discovery-state" role="alert">
    {t("discovery.error")} <button type="button" onClick={retry}>{t("home.events.retry")}</button>
  </div>;
  return empty ? <p className="discovery-state">{t("discovery.empty")}</p> : null;
}

export default function HomePage() {
  const { t, i18n } = useTranslation();
  const { keycloak } = useKeycloak();
  const queryClient = useQueryClient();
  const isAuthenticated = Boolean(keycloak.authenticated);
  const isEnglish = i18n.resolvedLanguage === "en";
  const locale = isEnglish ? "en-US" : "vi-VN";
  const date = new Intl.DateTimeFormat(locale, { weekday: "long", day: "numeric", month: "short", timeZone: "Asia/Ho_Chi_Minh" });
  const upcomingDay = new Intl.DateTimeFormat(locale, { day: "2-digit", timeZone: "Asia/Ho_Chi_Minh" });
  const shortDate = new Intl.DateTimeFormat(locale, { day: "numeric", month: "short", timeZone: "Asia/Ho_Chi_Minh" });
  const upcomingDate = new Intl.DateTimeFormat(locale, {
    weekday: "short",
    month: "short",
    year: "numeric",
    timeZone: "Asia/Ho_Chi_Minh",
  });
  const homeDiscovery = useQuery({
    queryKey: ["home-discovery"],
    queryFn: ({ signal }) => eventService.getHomeDiscovery(signal),
    staleTime: 60 * 1000,
  });
  const categories = useQuery({
    queryKey: ["public-categories"],
    queryFn: ({ signal }) => categoryService.getCategories(signal),
    staleTime: 5 * 60 * 1000,
  });
  const venues = useQuery({
    queryKey: ["public-venues"],
    queryFn: ({ signal }) => venueService.getVenues(signal),
    staleTime: 5 * 60 * 1000,
  });

  const discovery = homeDiscovery.data;
  const mainCategories = categories.data?.slice(0, 6) ?? [];
  const totalLiveEvents = discovery?.totalLiveEvents ?? 0;
  const featured = discovery?.featured ?? [];
  const upcoming = discovery?.upcoming ?? [];
  const withPhotos = [...featured, ...upcoming].filter(event => event.thumbnailUrl);

  const topCategorySections = (discovery?.topCategories ?? []).map(section => ({
    category: {
      id: section.categoryId,
      name: section.categoryName,
      slug: section.categoryId,
      displayOrder: 0,
    },
    events: section.events,
  }));

  const organizerEntries = discovery?.topOrganizers ?? [];
  const organizerSubjects = organizerEntries.map(item => item.organizerSubject);
  const organizerCountBySubject = new Map(
    organizerEntries.map(item => [item.organizerSubject, item.eventCount]),
  );

  const organizersQuery = useQuery({
    queryKey: ["home-public-organizers", organizerSubjects],
    enabled: organizerSubjects.length > 0,
    staleTime: 5 * 60 * 1000,
    queryFn: async ({ signal }) => {
      const profiles = await organizerService.getPublicOrganizersBatch(organizerSubjects, signal);
      return profiles
        .map(profile => ({
          profile,
          eventCount: organizerCountBySubject.get(profile.id) ?? 0,
        }))
        .sort(
          (left, right) =>
            (right.profile.followerCount ?? 0) - (left.profile.followerCount ?? 0) ||
            right.eventCount - left.eventCount,
        )
        .slice(0, 3);
    },
  });

  const featuredOrganizers = organizersQuery.data ?? [];
  const featuredOrganizerIds = featuredOrganizers.map(item => item.profile.id);

  const followingQuery = useQuery({
    queryKey: ["home-following-organizers", featuredOrganizerIds],
    enabled: isAuthenticated && featuredOrganizerIds.length > 0,
    staleTime: 60 * 1000,
    queryFn: ({ signal }) => organizerService.getFollowingStatuses(featuredOrganizerIds, signal),
  });

  const followMutation = useMutation({
    mutationFn: async ({ organizerId, currentlyFollowing }: { organizerId: string; currentlyFollowing: boolean }) =>
      currentlyFollowing
        ? organizerService.unfollowOrganizer(organizerId)
        : organizerService.followOrganizer(organizerId),
    onSuccess: result => {
      queryClient.setQueryData<Record<string, boolean>>(
        ["home-following-organizers", featuredOrganizerIds],
        prev => ({ ...(prev ?? {}), [result.organizerProfileId]: result.isFollowing }),
      );
      queryClient.setQueryData<{ profile: OrganizerProfile; eventCount: number }[]>(
        ["home-public-organizers", organizerSubjects],
        prev =>
          prev?.map(item =>
            item.profile.id === result.organizerProfileId
              ? { ...item, profile: { ...item.profile, followerCount: result.followerCount } }
              : item,
          ),
      );
    },
  });

  function eventText(event: EventSummary, showDate = true) {
    return <div className="discovery-event-text">
      {showDate && <time dateTime={event.startDatetime}>{date.format(new Date(event.startDatetime))}</time>}
      <h3>{event.title}</h3>
      {event.venueName && <span className="discovery-meta"><MapPin size={14} aria-hidden="true" />{event.venueName}</span>}
    </div>;
  }

  function price(event: EventSummary) {
    const ticket = event.ticketTypes?.find(type => type.unitPrice === event.minPriceAmount);
    if (!ticket?.currency || event.minPriceAmount == null) return null;
    const amount = new Intl.NumberFormat(locale, { style: "currency", currency: ticket.currency }).format(BigInt(event.minPriceAmount));
    return <span className="discovery-price">{t("discovery.from", { amount })}</span>;
  }

  const [heroSlideIndex, setHeroSlideIndex] = useState(0);
  const [isHeroPaused, setIsHeroPaused] = useState(false);

  const heroEvents = featured.length ? featured : upcoming.slice(0, 4);
  const heroSlides = heroEvents.length
    ? heroEvents.map((event, index) => ({
        id: event.id,
        to: eventLink(event),
        imageUrl: event.bannerUrl || heroMockImages[index % heroMockImages.length],
        title: event.title,
        dateLabel: shortDate.format(new Date(event.startDatetime)),
        venueLabel: event.venueName || "",
      }))
    : [
        {
          id: "mock-1",
          to: "/search",
          imageUrl: heroMockImages[0],
          title: isEnglish ? "Live Concert & Music Festivals" : "Đêm nhạc & Lễ hội Âm nhạc trực tiếp",
          dateLabel: isEnglish ? "On sale now" : "Đang mở bán",
          venueLabel: "FlashTicket",
        },
        {
          id: "mock-2",
          to: "/search",
          imageUrl: heroMockImages[1],
          title: isEnglish ? "Art, Stage & Creative Workshops" : "Nghệ thuật, Sân khấu & Workshop tuyển chọn",
          dateLabel: isEnglish ? "Explore events" : "Khám phá ngay",
          venueLabel: "FlashTicket",
        },
      ];

  const activeHeroIndex = heroSlideIndex % heroSlides.length;

  useEffect(() => {
    if (heroSlides.length <= 1 || isHeroPaused) return;
    if (typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      return;
    }
    const timer = window.setInterval(() => {
      setHeroSlideIndex(prev => (prev + 1) % heroSlides.length);
    }, 4500);
    return () => window.clearInterval(timer);
  }, [heroSlides.length, isHeroPaused]);

  function featuredCard(event: EventSummary, lead = false) {
    const startAt = new Date(event.startDatetime);
    const badgeDate = shortDate.format(startAt).toUpperCase();
    const badgeYear = new Intl.DateTimeFormat(locale, { year: "numeric", timeZone: "Asia/Ho_Chi_Minh" }).format(startAt);
    return <Link key={event.id} className={lead ? "discovery-feature discovery-feature--lead" : "discovery-feature"} to={eventLink(event)}>
      <div className="discovery-image">
        <EventImage event={event} />
        {lead && (
          <time className="discovery-feature-date-badge" dateTime={event.startDatetime}>
            <span className="discovery-feature-date-main">{badgeDate}</span>
            <span className="discovery-feature-date-year">{badgeYear}</span>
          </time>
        )}
      </div>
      <div className="discovery-feature-body">
        {eventText(event, !lead)}
        <div className="discovery-feature-actions">
          {price(event)}
        </div>
      </div>
    </Link>;
  }

  return <>
    <div className="discovery">
      <main className="discovery-shell">
      <section className="discovery-hero" aria-labelledby="discovery-title">
        <div className="discovery-hero-backdrop" aria-hidden="true">
          <img src={heroSlides[activeHeroIndex]?.imageUrl || heroMockImages[0]} alt="" />
        </div>
        <div className="discovery-hero-copy">
          <h1 id="discovery-title">
            {isEnglish ? (
              <>
                Instant tickets for every <span className="discovery-heading-accent">live moment</span>{" "}
                <span aria-hidden="true">✦</span>
              </>
            ) : (
              <>
                Săn vé tức thì cho mọi <span className="discovery-heading-accent">khoảnh khắc</span>{" "}
                <span aria-hidden="true">✦</span>
              </>
            )}
          </h1>
          <p className="discovery-intro">
            {isEnglish
              ? "Discover curated concerts, marathons, theatre shows, and workshops — visual seat selection and instant QR tickets in seconds."
              : "Khám phá concert, giải chạy, sân khấu kịch và workshop tuyển chọn — chọn ghế trực quan, nhận vé QR trong vài giây."}
          </p>

          <dl className="discovery-hero-stats">
            <div className="discovery-hero-stat">
              <div className="discovery-hero-stat-head">
                <span className="discovery-hero-stat-icon" aria-hidden="true">
                  <Ticket size={16} />
                </span>
                <dd>{totalLiveEvents > 0 ? `${totalLiveEvents}+` : "100+"}</dd>
              </div>
              <dt>{isEnglish ? "Live events" : "Sự kiện mở bán"}</dt>
            </div>
            <div className="discovery-hero-stat">
              <div className="discovery-hero-stat-head">
                <span className="discovery-hero-stat-icon" aria-hidden="true">
                  <MapPin size={16} />
                </span>
                <dd>{venues.data?.length ? `${venues.data.length}+` : "10+"}</dd>
              </div>
              <dt>{isEnglish ? "Partner venues" : "Địa điểm đối tác"}</dt>
            </div>
            <div className="discovery-hero-stat">
              <div className="discovery-hero-stat-head">
                <span className="discovery-hero-stat-icon" aria-hidden="true">
                  <QrCode size={16} />
                </span>
                <dd>{isEnglish ? "5 sec" : "5 giây"}</dd>
              </div>
              <dt>{isEnglish ? "Instant QR ticket" : "Nhận vé QR tức thì"}</dt>
            </div>
          </dl>
        </div>

        <div
          className="discovery-hero-deck"
          onMouseEnter={() => setIsHeroPaused(true)}
          onMouseLeave={() => setIsHeroPaused(false)}
          onFocusCapture={() => setIsHeroPaused(true)}
          onBlurCapture={() => setIsHeroPaused(false)}
        >
          <div className="discovery-hero-stage">
            {heroSlides.map((slide, index) => {
              const total = heroSlides.length;
              const offset = (index - activeHeroIndex + total) % total;
              const stateClass =
                offset === 0
                  ? "is-active"
                  : offset === 1
                    ? "is-next"
                    : offset === total - 1
                      ? "is-prev"
                      : "is-hidden";
              return (
                <Link
                  key={slide.id}
                  to={slide.to}
                  className={`discovery-hero-slide ${stateClass}`}
                  aria-hidden={offset !== 0}
                  tabIndex={offset === 0 ? 0 : -1}
                >
                  <img
                    src={slide.imageUrl}
                    alt={offset === 0 ? slide.title : ""}
                    onError={({ currentTarget }) => {
                      if (!currentTarget.src.endsWith(placeholder)) currentTarget.src = placeholder;
                    }}
                  />
                  <div className="discovery-hero-slide-overlay">
                    <div className="discovery-hero-slide-info">
                      <h3>{slide.title}</h3>
                      <div className="discovery-hero-slide-meta">
                        <span>
                          <Calendar size={13} aria-hidden="true" />
                          {slide.dateLabel}
                        </span>
                        {slide.venueLabel && (
                          <span>
                            <MapPin size={13} aria-hidden="true" />
                            {slide.venueLabel}
                          </span>
                        )}
                      </div>
                    </div>
                    <span className="discovery-hero-slide-cta">
                      {isEnglish ? "Get Event Ticket" : "Đặt vé ngay"}
                    </span>
                  </div>
                </Link>
              );
            })}

            {heroSlides.length > 1 && (
              <>
                <button
                  type="button"
                  className="discovery-hero-arrow discovery-hero-arrow--prev"
                  aria-label={isEnglish ? "Previous event" : "Sự kiện trước"}
                  onClick={() => setHeroSlideIndex(prev => (prev - 1 + heroSlides.length) % heroSlides.length)}
                >
                  <ArrowLeft size={16} aria-hidden="true" />
                </button>
                <button
                  type="button"
                  className="discovery-hero-arrow discovery-hero-arrow--next"
                  aria-label={isEnglish ? "Next event" : "Sự kiện tiếp theo"}
                  onClick={() => setHeroSlideIndex(prev => (prev + 1) % heroSlides.length)}
                >
                  <ArrowRight size={16} aria-hidden="true" />
                </button>
              </>
            )}
          </div>

          {heroSlides.length > 1 && (
            <div className="discovery-hero-controls">
              <div className="discovery-hero-dots" role="tablist" aria-label={isEnglish ? "Hero slides" : "Chọn sự kiện nổi bật"}>
                {heroSlides.map((slide, index) => (
                  <button
                    key={slide.id}
                    type="button"
                    role="tab"
                    aria-selected={index === activeHeroIndex}
                    aria-label={slide.title}
                    className={`discovery-hero-dot${index === activeHeroIndex ? " is-active" : ""}`}
                    onClick={() => setHeroSlideIndex(index)}
                  />
                ))}
              </div>
            </div>
          )}
        </div>
      </section>

      <EventCategoriesSection
        categories={categories.data ?? []}
        isPending={categories.isPending}
        isError={categories.isError}
        isEnglish={isEnglish}
        title={t("home.sections.categories")}
        viewAllLabel={t("discovery.viewCategories")}
        otherLabel={t("discovery.otherCategories")}
        errorLabel={t("discovery.error")}
        emptyLabel={t("discovery.empty")}
        retryLabel={t("home.events.retry")}
        onRetry={() => void categories.refetch()}
      />

      {!!featuredOrganizers.length && <section className="discovery-section discovery-organizers-band" aria-labelledby="discovery-organizers">
        <div className="discovery-section-head discovery-organizers-head">
          <div>
            <h2 id="discovery-organizers">{isEnglish ? "Featured Organizers" : "Nhà tổ chức tiêu biểu"}</h2>
            <p className="discovery-organizers-subtitle">
              {isEnglish
                ? "Trusted event creators with the largest communities on FlashTicket"
                : "Những đơn vị sản xuất sự kiện uy tín được cộng đồng theo dõi nhiều nhất"}
            </p>
          </div>
        </div>
        <ul className="discovery-organizers">
          {featuredOrganizers.map(({ profile, eventCount }) => {
            const isFollowing = Boolean(followingQuery.data?.[profile.id]);
            const isBusy =
              followMutation.isPending && followMutation.variables?.organizerId === profile.id;
            const initial = (profile.name || "O").trim().charAt(0).toUpperCase();
            const organizerUrl = `/organizers/${encodeURIComponent(profile.slug || profile.id)}`;
            return <li key={profile.id} className="discovery-organizer-card">
              <Link to={organizerUrl} className="discovery-organizer-cover" aria-hidden="true" tabIndex={-1}>
                {profile.bannerUrl && <img src={profile.bannerUrl} alt="" loading="lazy" />}
              </Link>
              <div className="discovery-organizer-body">
                <Link to={organizerUrl} className="discovery-organizer-avatar" aria-hidden="true" tabIndex={-1}>
                  {profile.logoUrl ? <img src={profile.logoUrl} alt="" loading="lazy" /> : <span>{initial}</span>}
                </Link>
                <Link to={organizerUrl} className="discovery-organizer-title-row">
                  <h3>{profile.name}</h3>
                  <span className="discovery-organizer-verified" aria-hidden="true">
                    <Check size={11} strokeWidth={3} />
                  </span>
                </Link>
                <p className="discovery-organizer-desc">
                  {profile.description ||
                    (isEnglish
                      ? "Official event organizer on FlashTicket."
                      : "Đơn vị tổ chức sự kiện chính thức trên FlashTicket.")}
                </p>
                <div className="discovery-organizer-footer">
                  <span className="discovery-organizer-stats">
                    <strong>{new Intl.NumberFormat(locale).format(profile.followerCount ?? 0)}</strong>{" "}
                    {isEnglish ? "followers" : "theo dõi"} ·{" "}
                    <strong>{eventCount}</strong> {isEnglish ? "events" : "sự kiện"}
                  </span>
                  <button
                    type="button"
                    className={`discovery-organizer-follow${isFollowing ? " is-following" : ""}`}
                    disabled={isBusy}
                    aria-pressed={isFollowing}
                    onClick={() => {
                      if (!isAuthenticated) {
                        void keycloak.login();
                        return;
                      }
                      followMutation.mutate({
                        organizerId: profile.id,
                        currentlyFollowing: isFollowing,
                      });
                    }}
                  >
                    {isFollowing
                      ? isEnglish
                        ? "Following"
                        : "Đang theo dõi"
                      : isEnglish
                        ? "Follow"
                        : "Theo dõi"}
                  </button>
                </div>
              </div>
            </li>;
          })}
        </ul>
      </section>}

      <Section id="discovery-featured" title={t("discovery.featured")} link={{ to: "/search", label: t("home.upcoming.viewAll") }}>
        <QueryState pending={homeDiscovery.isPending} error={homeDiscovery.isError} empty={featured.length === 0 && upcoming.length === 0}
          retry={() => void homeDiscovery.refetch()} />
        {!!featured.length && <div className="discovery-featured">
          {featuredCard(featured[0], true)}
          {featured.length > 1 && <div className="discovery-feature-list">
            {featured.slice(1).map(event => featuredCard(event))}
          </div>}
        </div>}
      </Section>

      {!!upcoming.length && <Section id="discovery-upcoming" title={t("home.sections.upcoming")}
        link={{ to: "/search", label: t("home.upcoming.viewAll") }}>
        <ul className="discovery-upcoming">
          {upcoming.slice(0, 4).map(event => <li key={event.id}><Link to={eventLink(event)}>
            <time className="discovery-upcoming-date" dateTime={event.startDatetime}>
              <span className="discovery-upcoming-day">{upcomingDay.format(new Date(event.startDatetime))}</span>
              <span className="discovery-upcoming-date-detail">{upcomingDate.format(new Date(event.startDatetime))}</span>
            </time>
            <div className="discovery-image"><EventImage event={event} /></div>
            {eventText(event, false)}{price(event)}
          </Link></li>)}
        </ul>
      </Section>}

      <MovingLogoStrip />

      {!!topCategorySections.length && <div className="discovery-category-rows">
        {topCategorySections.map(({ category, events: categoryEvents }, categoryIndex) => {
          const Icon = categoryIcon(category.name);
          const headingId = `discovery-cat-${category.id}`;
          return <Fragment key={category.id}>
            <section className="discovery-section discovery-category-row" aria-labelledby={headingId}>
              <div className="discovery-category-row-head">
                <Link className="discovery-category-row-link" to={`/search?category=${encodeURIComponent(category.id)}`}>
                  <Icon size={20} strokeWidth={2} aria-hidden="true" />
                  <h2 id={headingId}>{category.name}</h2>
                  <ChevronRight size={20} strokeWidth={2.2} aria-hidden="true" />
                </Link>
              </div>
              <ul className="discovery-category-grid">
                {categoryEvents.slice(0, 5).map(event => {
                  const formattedDate = shortDate.format(new Date(event.startDatetime));
                  const metaLabel = event.venueName ? `${event.venueName} • ${formattedDate}` : formattedDate;
                  return <li key={event.id}>
                    <Link className="discovery-category-card" to={eventLink(event)}>
                      <div className="discovery-image discovery-category-card-media"><EventImage event={event} /></div>
                      <div className="discovery-category-card-body">
                        <h3>{event.title}</h3>
                        {price(event)}
                        <span className="discovery-meta">
                          <MapPin size={13} aria-hidden="true" />
                          <span>{metaLabel}</span>
                        </span>
                      </div>
                    </Link>
                  </li>;
                })}
              </ul>
            </section>
            {categoryIndex === 0 && (
              <div className="discovery-category-banner">
                <img
                  src="/promo/vnpay-promo-banner.webp"
                  alt={isEnglish ? "VNPAY-QR Payment Promotion on FlashTicket" : "Ưu đãi thanh toán VNPAY-QR trên FlashTicket"}
                  loading="lazy"
                />
              </div>
            )}
            {categoryIndex === 2 && (
              <div className="discovery-category-banner">
                <img
                  src="/promo/promo-kpop-glowup.jpg"
                  alt={isEnglish ? "K-POP Glow UP! — Exclusive 1,000,000đ Combo" : "K-POP Glow UP! — Combo đặc quyền 1.000.000đ"}
                  loading="lazy"
                />
              </div>
            )}
          </Fragment>;
        })}
      </div>}

      <Section id="discovery-venues" className="discovery-venues-band" title={t("home.sections.venues")} link={{ to: "/venues", label: t("discovery.allVenues") }}>
        <QueryState pending={venues.isPending} error={venues.isError} empty={venues.data?.length === 0}
          retry={() => void venues.refetch()} />
        {!!venues.data?.length && (() => {
          const sortedVenues = [...venues.data].sort(
            (left, right) =>
              Number(Boolean(right.imageUrl || right.imageUrls?.[0])) -
              Number(Boolean(left.imageUrl || left.imageUrls?.[0])),
          );
          const featuredVenues = sortedVenues.slice(0, 3);
          const collagePool = Array.from(
            new Set([
              ...sortedVenues.slice(3).map(v => v.imageUrl || v.imageUrls?.[0]).filter((url): url is string => Boolean(url)),
              ...withPhotos.map(e => e.bannerUrl).filter((url): url is string => Boolean(url)),
              ...heroMockImages,
            ]),
          );
          const collageImages = [0, 1, 2, 3].map(idx => collagePool[idx % collagePool.length] || placeholder);

          return (
            <ul className="discovery-venues">
              {featuredVenues.map(venue => (
                <li key={venue.id}>
                  <Link className="discovery-venue-card" to={`/search?venue=${encodeURIComponent(venue.name)}`}>
                    <div className="discovery-venue-media" aria-hidden="true">
                      <VenueImage venue={venue} />
                    </div>
                    <div className="discovery-venue-overlay">
                      <h3>{venue.name}</h3>
                      {venue.address && <p>{venue.address}</p>}
                    </div>
                  </Link>
                </li>
              ))}
              <li>
                <Link className="discovery-venue-card discovery-venue-card--more" to="/search">
                  <div className="discovery-venue-collage" aria-hidden="true">
                    {collageImages.map((src, idx) => (
                      <img key={`${src}-${idx}`} src={src} alt="" loading="lazy" />
                    ))}
                  </div>
                  <div className="discovery-venue-overlay">
                    <h3>{isEnglish ? "Other locations" : "Địa điểm khác"}</h3>
                  </div>
                </Link>
              </li>
            </ul>
          );
        })()}
      </Section>

      <section className="discovery-promo" aria-labelledby="discovery-promo-title">
        <div className="discovery-promo-copy">
          <h2 id="discovery-promo-title"><Trans i18nKey="discovery.promo" components={{ accent: <span className="discovery-heading-accent" /> }} /></h2>
          <p>{t("discovery.promoBody")}</p>
        </div>
        <div className="discovery-promo-visual" aria-hidden="true">
          <div className="discovery-promo-device discovery-promo-device-back">
            <div className="discovery-promo-display">
              <div className="discovery-promo-header">
                <strong className="discovery-promo-wordmark">FlashTicket</strong>
                <Ticket size={14} />
              </div>
              <strong className="discovery-promo-screen-title">{t("home.sections.categories")}</strong>
              <div className="discovery-promo-topics">
                {mainCategories.slice(0, 4).map(category => {
                  const Icon = categoryIcon(category.name);
                  return <div key={category.id}><Icon size={14} /><span>{category.name}</span></div>;
                })}
              </div>
              <div className="discovery-promo-cover">
                <img src={withPhotos[1]?.thumbnailUrl || heroMockImages[1]} alt="" loading="lazy" />
              </div>
            </div>
          </div>
          <div className="discovery-promo-device discovery-promo-device-front">
            <div className="discovery-promo-display">
              <div className="discovery-promo-header">
                <strong className="discovery-promo-wordmark">FlashTicket</strong>
                <Ticket size={14} />
              </div>
              <div className="discovery-promo-search">
                <Search size={12} />
                <span>{t("common.search")}</span>
              </div>
              <strong className="discovery-promo-screen-title">{t("discovery.featured")}</strong>
              <div className="discovery-promo-cover">
                <img src={featured[0]?.thumbnailUrl || heroMockImages[0]} alt="" loading="lazy" />
              </div>
              {featured[0] && <div className="discovery-promo-event">
                <strong>{featured[0].title}</strong>
                <small>{date.format(new Date(featured[0].startDatetime))}</small>
                {featured[0].venueName && <small>{featured[0].venueName}</small>}
              </div>}
            </div>
          </div>
        </div>
        <div className="discovery-promo-downloads" role="group" aria-label={t("discovery.promoMockup")}>
          <div className="discovery-promo-badges">
            <img src="/promo/app-store.svg" alt="Download on the App Store" loading="lazy" />
            <img src="/promo/google-play.png" alt="Get it on Google Play" loading="lazy" />
          </div>
          <img className="discovery-promo-qr" src="/promo/mockup-qr.svg" alt={t("discovery.promoMockup")} loading="lazy" />
        </div>
      </section>

      <FaqSection />
    </main>
  </div>
  <Footer />
</>;
}