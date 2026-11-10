import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { ArrowRight, Calendar, ChevronDown, LayoutGrid, List, MapPin, RotateCcw, Search, SlidersHorizontal, Tag } from "lucide-react";
import { useTranslation } from "react-i18next";
import Footer from "../../components/common/Footer";
import { eventService } from "../../services/eventService";
import { categoryService } from "../../services/categoryService";
import { venueService } from "../../services/venueService";
import type { EventSummary } from "../../types/api";
import "./event-search.css";

const zone = "Asia/Ho_Chi_Minh";
const placeholder = "/event-placeholder.svg";
const dateKey = (date: Date) => date.toLocaleDateString("sv-SE", { timeZone: zone });
const emptyFilters = { from: "", to: "", price: "", venue: "" };
type Filter = "date" | "price" | "venue" | "all";

export default function EventSearchPage() {
  const { t, i18n } = useTranslation();
  const en = i18n.resolvedLanguage === "en";
  const locale = en ? "en-US" : "vi-VN";
  const copy = (vi: string, english: string) => en ? english : vi;
  const [params, setParams] = useSearchParams();
  const search = params.get("search") || "";
  const category = params.get("category") || "";
  const cursor = params.get("cursor") || "";
  const [keyword, setKeyword] = useState(search);
  const [list, setList] = useState(false);
  const [panel, setPanel] = useState<Filter | null>(null);
  const filters = { from: params.get("from") || "", to: params.get("to") || "", price: params.get("price") || "", venue: params.get("venue") || "" };
  const [draft, setDraft] = useState(filters);
  const toolbar = useRef<HTMLDivElement>(null);
  const categoryMenu = useRef<HTMLDetailsElement>(null);
  const trigger = useRef<HTMLButtonElement | null>(null);
  const categories = useQuery({ queryKey: ["search-categories"], queryFn: () => categoryService.getCategories() });
  const results = useInfiniteQuery({
    queryKey: ["editorial-events", search, category, cursor],
    initialPageParam: cursor,
    queryFn: ({ pageParam }) => eventService.getEvents({ search, category, cursor: pageParam, size: 12 }),
    getNextPageParam: (last, _, __, cursors) => last.nextCursor && !cursors.includes(last.nextCursor) ? last.nextCursor : undefined,
  });

  useEffect(() => {
    const close = () => categoryMenu.current?.removeAttribute("open");
    const outside = (event: PointerEvent) => {
      if (!categoryMenu.current?.contains(event.target as Node)) close();
    };
    window.addEventListener("scroll", close, { passive: true });
    document.addEventListener("pointerdown", outside);
    return () => {
      window.removeEventListener("scroll", close);
      document.removeEventListener("pointerdown", outside);
    };
  }, []);

  useEffect(() => setKeyword(search), [search]);
  useEffect(() => {
    if (!panel) return;
    const reposition = () => {
      const button = trigger.current;
      if (!button) return;
      toolbar.current?.style.setProperty("--filter-left", button.offsetLeft + "px");
      toolbar.current?.style.setProperty("--filter-top", (button.offsetTop + button.offsetHeight) + "px");
    };
    const outside = (event: PointerEvent) => {
      toolbar.current?.querySelectorAll<HTMLDetailsElement>(".explore-filter-select[open]").forEach(menu => {
        if (!menu.contains(event.target as Node)) menu.open = false;
      });
      if (!toolbar.current?.contains(event.target as Node)) setPanel(null);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        const menu = toolbar.current?.querySelector<HTMLDetailsElement>(".explore-filter-select[open]");
        if (menu) {
          menu.open = false;
          menu.querySelector("summary")?.focus();
          return;
        }
        setPanel(null);
        trigger.current?.focus();
      }
    };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("keydown", escape);
    window.addEventListener("resize", reposition);
    return () => {
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("keydown", escape);
      window.removeEventListener("resize", reposition);
    };
  }, [panel]);

  function changeMain(nextSearch: string, nextCategory: string) {
    const next = new URLSearchParams(params);
    next.delete("cursor");
    nextSearch ? next.set("search", nextSearch) : next.delete("search");
    nextCategory ? next.set("category", nextCategory) : next.delete("category");
    setParams(next);
  }

  function clear() {
    setKeyword("");
    setParams({});
    setPanel(null);
  }

  const events = Array.from(new Map(results.data?.pages.flatMap(page => page.content).map(event => [event.id, event])).values());
  const venueQuery = useQuery({ queryKey: ["venues"], queryFn: venueService.getVenues });
  const venues = [...new Set(venueQuery.data?.map(venue => venue.name).filter(Boolean))].sort((a, b) => a.localeCompare(b, locale));
  const visible = events.filter(event => {
    const day = dateKey(new Date(event.startDatetime));
    const price = event.minPrice;
    return (!filters.from || day >= filters.from) && (!filters.to || day <= filters.to)
      && (!filters.venue || event.venueName === filters.venue)
      && (!filters.price || (price !== undefined && event.ticketTypes?.some(ticket => ticket.price === price && ticket.currency === "VND") && price <= Number(filters.price)));
  });
  const findingVenueEvents = Boolean(filters.venue && !visible.length && results.hasNextPage && !results.isError);
  const { fetchNextPage, isFetching } = results;
  useEffect(() => {
    if (findingVenueEvents && !isFetching) void fetchNextPage({ cancelRefetch: false });
  }, [findingVenueEvents, isFetching, fetchNextPage]);

  const spotlight = list ? [] : visible.slice(0, 3);
  const groups = new Map<string, EventSummary[]>();
  const month = new Intl.DateTimeFormat(locale, { month: "long", year: "numeric", timeZone: zone });
  for (const event of visible.slice(spotlight.length).sort((a, b) => Date.parse(a.startDatetime) - Date.parse(b.startDatetime))) {
    const label = month.format(new Date(event.startDatetime));
    const group = groups.get(label) ?? [];
    group.push(event);
    groups.set(label, group);
  }

  function card(event: EventSummary, kind: "lead" | "side" | "row") {
    const date = new Date(event.startDatetime);
    const ticket = event.ticketTypes?.find(type => type.unitPrice === event.minPriceAmount);
    const price = ticket && event.minPriceAmount !== undefined
      ? new Intl.NumberFormat(locale, { style: "currency", currency: ticket.currency }).format(BigInt(event.minPriceAmount))
      : null;
    return <Link key={event.id} className={`explore-event explore-event--${kind}`} to={`/event/${encodeURIComponent(event.slug || event.id)}`}>
      {kind === "row" && <time className="explore-day" dateTime={event.startDatetime}>
        <strong>{date.toLocaleDateString(locale, { day: "2-digit", timeZone: zone })}</strong>
        {date.toLocaleDateString(locale, { month: "short", timeZone: zone })}
      </time>}
      <img className="explore-image" src={event.bannerUrl?.startsWith("https://") || event.bannerUrl?.startsWith("http://") ? event.bannerUrl : placeholder} alt="" loading={kind === "lead" ? "eager" : "lazy"}
        onError={({ currentTarget }) => { if (!currentTarget.src.endsWith(placeholder)) currentTarget.src = placeholder; }} />
      <div className="explore-event-copy">
        {kind !== "row" && <time dateTime={event.startDatetime}><Calendar size={14} />{date.toLocaleDateString(locale, { weekday: "short", day: "numeric", month: "short", year: "numeric", timeZone: zone })}</time>}
        <h3>{event.title}</h3>
        <p><MapPin size={14} /><span>{event.venueName || t("searchPage.locationUpdating")}</span></p>
        <strong className="explore-price">{price ? copy(`Từ ${price}`, `From ${price}`) : t("searchPage.priceUpdating")}</strong>
      </div>
      <span className="explore-arrow"><ArrowRight size={18} /></span>
    </Link>;
  }


  const active = Object.values(filters).some(Boolean);
  const controls = [
    ["date", Calendar, copy("Thời gian", "Date"), Boolean(filters.from || filters.to)],
    ["price", Tag, copy("Giá vé", "Ticket price"), Boolean(filters.price)],
    ["venue", MapPin, filters.venue || copy("Địa điểm", "Venue"), Boolean(filters.venue)],
    ["all", SlidersHorizontal, copy("Bộ lọc", "Filters"), active],
  ] as const;

  function preset(kind: "today" | "weekend" | "week") {
    const start = new Date(`${dateKey(new Date())}T00:00:00+07:00`);
    const weekday = new Date(`${dateKey(start)}T00:00:00Z`).getUTCDay();
    const days = kind === "weekend" ? (weekday === 0 ? 0 : 6 - weekday) : 0;
    const end = kind === "week" ? 6 : kind === "weekend" && weekday !== 0 ? days + 1 : days;
    setDraft({ ...draft, from: dateKey(new Date(+start + days * 86400000)), to: dateKey(new Date(+start + end * 86400000)) });
  }

  return <div className="explore-page">
    <main className="explore-shell">
      <section className="explore-intro" aria-labelledby="explore-title">
        <div><h1 id="explore-title">{copy("Tìm một trải nghiệm", "Find an experience")}<br /><span>{copy("đáng nhớ", "worth remembering")}</span></h1>
          <p>{copy("Khám phá các sự kiện âm nhạc, thể thao, hội thảo và nhiều hoạt động hấp dẫn khác.", "Discover music, sports, workshops and more events near you.")}</p></div>
      </section>
      <form className="explore-search" role="search" aria-label={copy("Tìm sự kiện", "Find events")} onSubmit={event => { event.preventDefault(); changeMain(keyword.trim(), category); }}>
        <label><Search size={20} /><input aria-label={t("searchPage.keyword")} value={keyword} onChange={event => setKeyword(event.target.value)} placeholder={copy("Tìm tên sự kiện, nghệ sĩ...", "Search events, artists...")} /></label>
        <details className="explore-category" ref={categoryMenu} onKeyDown={event => {
          if (event.key !== "Escape" || !event.currentTarget.open) return;
          event.stopPropagation();
          event.currentTarget.open = false;
          event.currentTarget.querySelector("summary")?.focus();
        }}>
          <summary>
            <LayoutGrid size={18} aria-hidden="true" />
            <span>{categories.data?.find(item => item.id === category)?.name ?? t("searchPage.allCategories")}</span>
            <ChevronDown className="explore-chevron" size={16} aria-hidden="true" />
          </summary>
          <div className="explore-category-options" role="group" aria-label={t("searchPage.category")}>
            {[{ id: "", name: t("searchPage.allCategories") }, ...(categories.data ?? [])].map(item => (
              <button key={item.id} type="button" aria-pressed={category === item.id} onClick={() => {
                categoryMenu.current?.removeAttribute("open");
                categoryMenu.current?.querySelector("summary")?.focus();
                changeMain(search, item.id);
              }}>{item.name}</button>
            ))}
          </div>
        </details>
        <button className="explore-primary" type="submit"><Search size={18} />{t("common.search")}</button>
      </form>
      {categories.isError && <p role="status">{copy("Không tải được danh mục.", "Could not load categories.")} <button type="button" onClick={() => void categories.refetch()}>{t("home.events.retry")}</button></p>}
      <div className="explore-toolbar" ref={toolbar}>
        {controls.map(([key, Icon, label, selected]) => <button key={key} type="button" className="explore-chip" data-active={selected} aria-expanded={panel === key} aria-controls={panel === key ? "explore-filter" : undefined} onClick={event => {
          trigger.current = event.currentTarget;
          toolbar.current?.style.setProperty("--filter-left", `${event.currentTarget.offsetLeft}px`);
          toolbar.current?.style.setProperty("--filter-top", `${event.currentTarget.offsetTop + event.currentTarget.offsetHeight}px`);
          setDraft(filters);
          setPanel(panel === key ? null : key);
        }}><Icon size={17} /><span>{label}</span><ChevronDown className="explore-chevron" size={15} aria-hidden="true" /></button>)}
        <button type="button" className="explore-clear" onClick={clear}><RotateCcw size={17} />{t("searchPage.clearFilters")}</button>
        {panel && <form id="explore-filter" className="explore-popover" data-panel={panel} onSubmit={event => {
          event.preventDefault();
          const next = new URLSearchParams(params);
          Object.entries(draft).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key));
          setParams(next);
          setPanel(null);
          trigger.current?.focus();
        }}>
          <strong>{controls.find(([key]) => key === panel)?.[2]}</strong>
            {(panel === "date" || panel === "all") && <>
              <div className="explore-presets">
                <button type="button" onClick={() => setDraft({ ...draft, from: "", to: "" })}>{copy("Tất cả", "Any date")}</button>
                <button type="button" onClick={() => preset("today")}>{copy("Hôm nay", "Today")}</button>
                <button type="button" onClick={() => preset("weekend")}>{copy("Cuối tuần", "Weekend")}</button>
                <button type="button" onClick={() => preset("week")}>{copy("7 ngày tới", "Next 7 days")}</button>
              </div>
              <div className="explore-dates">
                <label>{copy("Từ ngày", "From")}<input type="date" max={draft.to || undefined} value={draft.from} onChange={event => setDraft({ ...draft, from: event.target.value })} /></label>
                <label>{copy("Đến ngày", "To")}<input type="date" min={draft.from || undefined} value={draft.to} onChange={event => setDraft({ ...draft, to: event.target.value })} /></label>
              </div>
            </>}
            {(panel === "price" || panel === "all") && <div className="explore-filter-field">
              <span id="explore-price-label">{copy("Giá tối đa (VND)", "Maximum price (VND)")}</span>
              <details className="explore-category explore-filter-select">
                <summary aria-labelledby="explore-price-label explore-price-value">
                  <span id="explore-price-value">{draft.price === "0" ? copy("Miễn phí", "Free") : draft.price ? new Intl.NumberFormat(locale, { style: "currency", currency: "VND" }).format(Number(draft.price)) : copy("Mọi mức giá", "Any price")}</span>
                  <ChevronDown className="explore-chevron" size={16} aria-hidden="true" />
                </summary>
                <div className="explore-category-options">
                  {["", "0", "100000", "250000", "500000", "1000000"].map(value => (
                    <button key={value} type="button" aria-pressed={draft.price === value} onClick={event => {
                      const menu = event.currentTarget.closest("details");
                      menu?.removeAttribute("open");
                      menu?.querySelector("summary")?.focus();
                      setDraft({ ...draft, price: value });
                    }}>{value === "0" ? copy("Miễn phí", "Free") : value ? new Intl.NumberFormat(locale, { style: "currency", currency: "VND" }).format(Number(value)) : copy("Mọi mức giá", "Any price")}</button>
                  ))}
                </div>
              </details>
            </div>}
            {(panel === "venue" || panel === "all") && <div className="explore-filter-field">
              <span id="explore-venue-label">{copy("Địa điểm", "Venue")}</span>
              {venueQuery.isPending && <p role="status">{copy("Đang tải địa điểm…", "Loading venues…")}</p>}
              {venueQuery.isError && <p role="alert">{copy("Không tải được địa điểm.", "Could not load venues.")} <button type="button" onClick={() => void venueQuery.refetch()}>{t("home.events.retry")}</button></p>}
              <details className="explore-category explore-filter-select">
                <summary aria-labelledby="explore-venue-label explore-venue-value">
                  <span id="explore-venue-value">{draft.venue || copy("Mọi địa điểm", "Any venue")}</span>
                  <ChevronDown className="explore-chevron" size={16} aria-hidden="true" />
                </summary>
                <div className="explore-category-options">
                  {[...new Set(["", ...venues, draft.venue])].map(value => (
                    <button key={value} type="button" aria-pressed={draft.venue === value} onClick={event => {
                      const menu = event.currentTarget.closest("details");
                      menu?.removeAttribute("open");
                      menu?.querySelector("summary")?.focus();
                      setDraft({ ...draft, venue: value });
                    }}>{value || copy("Mọi địa điểm", "Any venue")}</button>
                  ))}
                </div>
              </details>
            </div>}
            <div className="explore-filter-actions"><button type="button" onClick={() => setDraft({ ...draft, ...(panel === "all" ? emptyFilters : panel === "date" ? { from: "", to: "" } : { [panel]: "" }) })}>{copy("Đặt lại", "Reset")}</button>
              <button className="explore-primary" type="submit">{copy("Áp dụng", "Apply")}</button></div>
        </form>}
      </div>

      <section className="explore-results" aria-labelledby="explore-results-title">
        <div className="explore-results-head">
          <h2 id="explore-results-title">{copy(`${visible.length} sự kiện đang hiển thị`, `${visible.length} events shown`)}</h2>
          <div className="explore-toggle" data-list={list}>
            <button type="button" aria-label={copy("Bố cục nổi bật", "Editorial view")} aria-pressed={!list} onClick={() => setList(false)}><LayoutGrid size={18} aria-hidden="true" /></button>
            <button type="button" aria-label={copy("Bố cục danh sách", "List view")} aria-pressed={list} onClick={() => setList(true)}><List size={18} aria-hidden="true" /></button>
          </div>
        </div>
        {(results.isPending || findingVenueEvents) ? <div className="explore-skeleton" role="status" aria-label={t("searchPage.loading")} /> : results.isError && !results.data ?
          <div className="explore-state" role="alert"><p>{t("home.events.error")}</p><button className="explore-primary" onClick={() => void results.refetch()}>{t("home.events.retry")}</button></div>
          : !visible.length ? <div className="explore-state" role="status"><h3>{t("searchPage.noResults")}</h3><p>{t("searchPage.noResultsHint")}</p><button onClick={clear}>{t("searchPage.clearFilters")}</button></div>
          : <>
            {spotlight.length > 0 && <div className="explore-spotlight" data-single={spotlight.length === 1}>{spotlight.map((event, index) => card(event, index === 0 ? "lead" : "side"))}</div>}
            <div className="explore-groups" data-list={list}>
              {[...groups].map(([label, items]) => <section key={label}><h2>{label}</h2>{items.map(event => card(event, "row"))}</section>)}
            </div>
          </>}
        {results.isFetchNextPageError && <p role="alert" className="explore-state">{t("home.events.error")}</p>}
        {results.hasNextPage && !findingVenueEvents && <button className="explore-more" type="button" disabled={results.isFetchingNextPage} onClick={() => void results.fetchNextPage()}>
          {results.isFetchingNextPage ? t("searchPage.loading") : results.isFetchNextPageError ? t("home.events.retry") : copy("Xem thêm sự kiện", "Load more events")}<ArrowRight size={18} />
        </button>}
      </section>
    </main>
    <Footer />
  </div>;
}
