import Footer from "../../components/common/Footer";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  Building2,
  Drama,
  Ellipsis,
  Film,
  Footprints,
  GraduationCap,
  MapPin,
  Moon,
  Mountain,
  Music4,
  Palette,
  Search,
  Tags,
  Ticket,
  Trophy,
  Users,
  Utensils,
} from "lucide-react";
import { Trans, useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import type { ReactNode } from "react";
import { categoryService } from "../../services/categoryService";
import { eventService } from "../../services/eventService";
import { venueService } from "../../services/venueService";
import type { EventSummary } from "../../types/api";
import "./discovery.css";

const placeholder = "/event-placeholder.svg";

const mainCategoryLimit = 6;

const categoryIcons = [
  { match: /chay.*(?:dia hinh|duong mon)|trail run/, Icon: Mountain },
  { match: /chay.*dem|night run/, Icon: Moon },
  { match: /chay bo|giai chay|marathon|running/, Icon: Footprints },
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

function EventImage({ event, eager = false }: { event: EventSummary; eager?: boolean }) {
  return <img src={event.bannerUrl || placeholder} alt="" loading={eager ? "eager" : "lazy"}
    onError={({ currentTarget }) => {
      if (!currentTarget.src.endsWith(placeholder)) currentTarget.src = placeholder;
    }} />;
}

function Section({ id, title, children, link }: {
  id: string; title: string; children: ReactNode; link?: { to: string; label: string };
}) {
  return <section className="discovery-section" aria-labelledby={id}>
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
  const locale = i18n.resolvedLanguage === "en" ? "en-US" : "vi-VN";
  const date = new Intl.DateTimeFormat(locale, { weekday: "long", day: "numeric", month: "short", timeZone: "Asia/Ho_Chi_Minh" });
  const upcomingDay = new Intl.DateTimeFormat(locale, { day: "2-digit", timeZone: "Asia/Ho_Chi_Minh" });
  const upcomingDate = new Intl.DateTimeFormat(locale, {
    weekday: "short",
    month: "short",
    year: "numeric",
    timeZone: "Asia/Ho_Chi_Minh",
  });
  const events = useQuery({ queryKey: ["home-public-events"], queryFn: () => eventService.getEvents({ size: 20 }) });
  const categories = useQuery({ queryKey: ["home-public-categories"], queryFn: categoryService.getCategories });
  const venues = useQuery({ queryKey: ["home-public-venues"], queryFn: venueService.getVenues });

  const mainCategories = categories.data?.slice(0, mainCategoryLimit) ?? [];

  const upcoming = (events.data?.content ?? [])
    .filter(event => Date.parse(event.startDatetime) >= Date.now())
    .sort((left, right) => Date.parse(left.startDatetime) - Date.parse(right.startDatetime));
  const withPhotos = upcoming.filter(event => event.thumbnailUrl);
  const featured = [...withPhotos, ...upcoming.filter(event => !event.thumbnailUrl)].slice(0, 3);

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

  return <div className="discovery">
    <main className="discovery-shell">
      <section className="discovery-hero" aria-labelledby="discovery-title">
        <div>
          <h1 id="discovery-title"><Trans i18nKey="discovery.title" components={{ accent: <span className="discovery-heading-accent" /> }} /> <span aria-hidden="true">✦</span></h1>
          <p className="discovery-intro">{t("discovery.intro")}</p>
        </div>
        <div className="discovery-collage" aria-hidden="true">
          {withPhotos.length ? withPhotos.slice(0, 2).map(event => <EventImage key={event.id} event={event} eager />)
            : <div className="discovery-hero-placeholder"><Ticket size={94} strokeWidth={1} /></div>}
        </div>
      </section>

      <Section id="discovery-categories" title={t("home.sections.categories")} link={{ to: "/search", label: t("discovery.viewCategories") }}>
        <QueryState pending={categories.isPending} error={categories.isError}
          empty={categories.data?.length === 0} retry={() => void categories.refetch()} />
        {!!categories.data?.length && <ul className="discovery-categories">
          {mainCategories.map(category => {
            const Icon = categoryIcon(category.name);
            return <li key={category.id}>
              <Link to={`/search?category=${encodeURIComponent(category.id)}`}>
                <Icon size={24} strokeWidth={1.75} aria-hidden="true" />
                <span>{category.name}</span>
              </Link>
            </li>;
          })}
          <li>
            <Link to="/search">
              <Ellipsis size={24} strokeWidth={1.75} aria-hidden="true" />
              <span>{t("discovery.otherCategories")}</span>
            </Link>
          </li>
        </ul>}
      </Section>

      <Section id="discovery-featured" title={t("discovery.featured")} link={{ to: "/search", label: t("home.upcoming.viewAll") }}>
        <QueryState pending={events.isPending} error={events.isError} empty={upcoming.length === 0}
          retry={() => void events.refetch()} />
        {!!featured.length && <div className="discovery-featured">
          {featured.map((event, index) => <Link key={event.id} className="discovery-feature" to={eventLink(event)}>
            <div className="discovery-image"><EventImage event={event} /></div>
            <div className="discovery-feature-body">
              {eventText(event)}
              <div className="discovery-feature-actions">
                {price(event)}
                {index > 0 && <ArrowRight size={17} aria-hidden="true" />}
              </div>
            </div>
          </Link>)}
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
            {eventText(event, false)}{price(event)}<ArrowRight className="discovery-row-arrow" size={17} aria-hidden="true" />
          </Link></li>)}
        </ul>
      </Section>}

      <Section id="discovery-venues" title={t("home.sections.venues")} link={{ to: "/venues", label: t("discovery.allVenues") }}>
        <QueryState pending={venues.isPending} error={venues.isError} empty={venues.data?.length === 0}
          retry={() => void venues.refetch()} />
        {!!venues.data?.length && <ul className="discovery-venues">
          {venues.data.slice(0, 3).map(venue => <li key={venue.id}>
            <div className="discovery-venue-media" aria-hidden="true">
              <Building2 size={32} strokeWidth={1.4} />
            </div>
            <div className="discovery-venue-body">
              <h3>{venue.name}</h3>
              {venue.address && <p>
                <MapPin size={14} strokeWidth={1.75} aria-hidden="true" />
                <span>{venue.address}</span>
              </p>}
            </div>
          </li>)}
        </ul>}
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
              {withPhotos[1] && <div className="discovery-promo-cover"><EventImage event={withPhotos[1]} /></div>}
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
                {featured[0] ? <EventImage event={featured[0]} /> : <Ticket size={28} />}
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
    </main>

    <Footer />
  </div>;
}