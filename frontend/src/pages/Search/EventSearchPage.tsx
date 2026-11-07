import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Calendar, LoaderCircle, MapPin, Search } from "lucide-react";
import { useTranslation } from "react-i18next";
import Footer from "../../components/common/Footer";
import { eventService } from "../../services/eventService";
import { categoryService } from "../../services/categoryService";
import { Category } from "../../types/api";

export default function EventSearchPage() {
  const { t, i18n } = useTranslation();
  const [params, setParams] = useSearchParams();
  const search = params.get("search") || "";
  const category = params.get("category") || "";
  const cursor = params.get("cursor") || "";
  const [keyword, setKeyword] = useState(search);
  const [categories, setCategories] = useState<Category[]>([]);
  const language = i18n.resolvedLanguage === "en" ? "en-US" : "vi-VN";

  useEffect(() => { setKeyword(search); }, [search]);
  useEffect(() => { void categoryService.getCategories().then(setCategories).catch(() => setCategories([])); }, []);
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["public-events", search, category, cursor],
    queryFn: () => eventService.getEvents({ search, category, cursor, size: 12 }),
  });

  const changeFilters = (nextSearch: string, nextCategory: string, nextCursor = "") => {
    const next = new URLSearchParams();
    if (nextSearch) next.set("search", nextSearch);
    if (nextCategory) next.set("category", nextCategory);
    if (nextCursor) next.set("cursor", nextCursor);
    setParams(next);
    window.scrollTo({ top: 0, behavior: "instant" });
  };

  return (
    <div className="event-search-page public-event-search">
      <main className="container public-event-search-main">
        <h1>{t("searchPage.discoverTitle")}</h1>
        <form className="public-event-filters" role="search" onSubmit={event => { event.preventDefault(); changeFilters(keyword.trim(), category); }}>
          <label>
            <span>{t("searchPage.keyword")}</span>
            <input className="form-control" name="keyword" value={keyword} onChange={event => setKeyword(event.target.value)} placeholder={t("searchPage.placeholder")} />
          </label>
          <label>
            <span>{t("searchPage.category")}</span>
            <select className="form-control" value={category} onChange={event => changeFilters(search, event.target.value)}>
              <option value="">{t("searchPage.allCategories")}</option>
              {categories.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}
            </select>
          </label>
          <button className="btn btn-primary" type="submit"><Search size={18} />{t("common.search")}</button>
          {(search || category || cursor) && <button className="btn btn-outline" type="button" onClick={() => changeFilters("", "")}>{t("searchPage.clearFilters")}</button>}
        </form>

        {isLoading ? <p role="status" className="home-event-state"><LoaderCircle size={24} />{t("searchPage.loading")}</p>
          : isError ? <div role="alert" className="home-event-state"><p>{t("home.events.error")}</p><button className="btn btn-primary" onClick={() => void refetch()}>{t("home.events.retry")}</button></div>
          : !data?.content.length ? <div role="status" className="home-event-state"><h2>{t("searchPage.noResults")}</h2><p>{t("searchPage.noResultsHint")}</p><button className="btn btn-primary" onClick={() => changeFilters("", "")}>{t("searchPage.clearFilters")}</button></div>
          : <>
            <p className="public-event-page-count">{t("searchPage.showingPage", { count: data.content.length })}</p>
            <div className="event-search-grid">
              {data.content.map(event => <article className="upcoming-event-card event-search-card" key={event.id}>
                <Link to={`/event/${event.id}`} className="upcoming-card-image-wrapper"><img src={event.bannerUrl} alt={event.title} loading="lazy" /></Link>
                <div className="upcoming-card-content">
                  <h2 className="upcoming-card-title"><Link to={`/event/${event.id}`}>{event.title}</Link></h2>
                  <p className="upcoming-card-location"><Calendar size={16} /><time dateTime={event.startDatetime}>{new Date(event.startDatetime).toLocaleDateString(language, { day: "numeric", month: "short", year: "numeric" })}</time></p>
                  <p className="upcoming-card-location"><MapPin size={16} />{event.venueName || t("searchPage.locationUpdating")}</p>
                  <div className="upcoming-card-bottom"><span className="upcoming-card-price">{event.minPriceAmount === undefined ? t("searchPage.priceUpdating") : t("searchPage.fromPrice", { price: BigInt(event.minPriceAmount).toLocaleString(language) })}</span><Link className="upcoming-card-buy-btn" to={`/event/${event.id}`}>{t("home.hero.getEventTicket")}</Link></div>
                </div>
              </article>)}
            </div>
            <nav className="public-event-pagination" aria-label={t("searchPage.pagesLabel")}>
              {cursor && <button className="btn btn-outline" onClick={() => changeFilters(search, category)}>{t("searchPage.firstPage")}</button>}
              {data.nextCursor && <button className="btn btn-primary" onClick={() => changeFilters(search, category, data.nextCursor!)}>{t("searchPage.nextPage")}</button>}
            </nav>
          </>}
      </main>
      <Footer />
    </div>
  );
}
