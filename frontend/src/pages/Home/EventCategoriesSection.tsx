import {
  DotsThreeCircle,
  Guitar,
  Headphones,
  MoonStars,
  MusicNotes,
  PersonSimpleRun,
  UsersThree,
  type Icon,
} from "@phosphor-icons/react";
import { ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";
import type { Category } from "../../types/api";

interface CategoryVisualTheme {
  Icon: Icon;
  cardClass: string;
  iconBoxClass: string;
  exploreClass: string;
}

const THEME_MUSIC: CategoryVisualTheme = {
  Icon: MusicNotes,
  cardClass:
    "bg-rose-100/70 border-rose-200 hover:border-rose-500 hover:bg-rose-200/80 hover:shadow-xl hover:shadow-rose-300/60",
  iconBoxClass: "bg-white text-rose-600 shadow-sm group-hover:shadow-md",
  exploreClass: "text-rose-700",
};

const THEME_INDIE: CategoryVisualTheme = {
  Icon: Headphones,
  cardClass:
    "bg-violet-100/70 border-violet-200 hover:border-violet-500 hover:bg-violet-200/80 hover:shadow-xl hover:shadow-violet-300/60",
  iconBoxClass: "bg-white text-violet-600 shadow-sm group-hover:shadow-md",
  exploreClass: "text-violet-700",
};

const THEME_NIGHT_RUN: CategoryVisualTheme = {
  Icon: MoonStars,
  cardClass:
    "bg-indigo-100/70 border-indigo-200 hover:border-indigo-500 hover:bg-indigo-200/80 hover:shadow-xl hover:shadow-indigo-300/60",
  iconBoxClass: "bg-white text-indigo-600 shadow-sm group-hover:shadow-md",
  exploreClass: "text-indigo-700",
};

const THEME_ACOUSTIC: CategoryVisualTheme = {
  Icon: Guitar,
  cardClass:
    "bg-amber-100/70 border-amber-200 hover:border-amber-500 hover:bg-amber-200/80 hover:shadow-xl hover:shadow-amber-300/60",
  iconBoxClass: "bg-white text-amber-600 shadow-sm group-hover:shadow-md",
  exploreClass: "text-amber-700",
};

const THEME_TRAIL: CategoryVisualTheme = {
  Icon: PersonSimpleRun,
  cardClass:
    "bg-emerald-100/70 border-emerald-200 hover:border-emerald-500 hover:bg-emerald-200/80 hover:shadow-xl hover:shadow-emerald-300/60",
  iconBoxClass: "bg-white text-emerald-600 shadow-sm group-hover:shadow-md",
  exploreClass: "text-emerald-700",
};

const THEME_CONFERENCE: CategoryVisualTheme = {
  Icon: UsersThree,
  cardClass:
    "bg-sky-100/70 border-sky-200 hover:border-sky-500 hover:bg-sky-200/80 hover:shadow-xl hover:shadow-sky-300/60",
  iconBoxClass: "bg-white text-sky-600 shadow-sm group-hover:shadow-md",
  exploreClass: "text-sky-700",
};

const THEME_OTHER: CategoryVisualTheme = {
  Icon: DotsThreeCircle,
  cardClass:
    "bg-slate-100/85 border-slate-300/80 hover:border-slate-500 hover:bg-slate-200/90 hover:shadow-xl hover:shadow-slate-300/70",
  iconBoxClass: "bg-white text-slate-600 shadow-sm group-hover:shadow-md",
  exploreClass: "text-slate-700",
};

const FALLBACK_THEMES: CategoryVisualTheme[] = [
  THEME_MUSIC,
  THEME_INDIE,
  THEME_NIGHT_RUN,
  THEME_ACOUSTIC,
  THEME_TRAIL,
  THEME_CONFERENCE,
];

function resolveCategoryTheme(name: string, index: number): CategoryVisualTheme {
  const normalized = name
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(/đ/g, "d");

  if (/indie/.test(normalized)) return THEME_INDIE;
  if (/chay.*dem|night run/.test(normalized)) return THEME_NIGHT_RUN;
  if (/chill|acoustic|guitar/.test(normalized)) return THEME_ACOUSTIC;
  if (/trail|dia hinh|duong mon/.test(normalized)) return THEME_TRAIL;
  if (/hoi thao|workshop|conference|seminar/.test(normalized)) return THEME_CONFERENCE;
  if (/am nhac|nhac song|music|concert/.test(normalized)) return THEME_MUSIC;

  return FALLBACK_THEMES[index % FALLBACK_THEMES.length];
}

interface EventCategoriesSectionProps {
  categories: Category[];
  isPending: boolean;
  isError: boolean;
  isEnglish: boolean;
  title: string;
  viewAllLabel: string;
  otherLabel: string;
  errorLabel: string;
  emptyLabel: string;
  retryLabel: string;
  onRetry: () => void;
}

export default function EventCategoriesSection({
  categories,
  isPending,
  isError,
  isEnglish,
  title,
  viewAllLabel,
  otherLabel,
  errorLabel,
  emptyLabel,
  retryLabel,
  onRetry,
}: EventCategoriesSectionProps) {
  const exploreLabel = isEnglish ? "Explore →" : "Khám phá →";
  const topSix = categories.slice(0, 6);

  return (
    <section id="discovery-categories" className="discovery-section" aria-labelledby="discovery-categories-heading">
      <div className="mb-6 flex flex-wrap items-baseline justify-between gap-3">
        <h2 id="discovery-categories-heading" className="text-2xl font-extrabold tracking-tight text-slate-900">
          {title}
        </h2>
        <Link
          className="inline-flex items-center gap-1 text-sm font-semibold text-slate-600 transition-colors hover:text-amber-700"
          to="/search"
        >
          <span>{viewAllLabel}</span>
          <ChevronRight size={16} aria-hidden="true" />
        </Link>
      </div>

      {isPending && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-7" aria-hidden="true">
          {Array.from({ length: 7 }).map((_, idx) => (
            <div
              key={idx}
              className="h-36 animate-pulse rounded-2xl border border-slate-100 bg-slate-100/80 p-5"
            />
          ))}
        </div>
      )}

      {isError && (
        <div className="discovery-state" role="alert">
          {errorLabel}{" "}
          <button type="button" onClick={onRetry}>
            {retryLabel}
          </button>
        </div>
      )}

      {!isPending && !isError && categories.length === 0 && (
        <p className="discovery-state">{emptyLabel}</p>
      )}

      {!isPending && !isError && categories.length > 0 && (
        <ul className="grid grid-cols-2 items-stretch gap-4 sm:grid-cols-4 lg:grid-cols-7">
          {topSix.map((category, index) => {
            const theme = resolveCategoryTheme(category.name, index);
            const Icon = theme.Icon;
            return (
              <li key={category.id} className="h-full">
                <Link
                  to={`/search?category=${encodeURIComponent(category.id)}`}
                  className={`group flex h-full flex-col items-center justify-between rounded-2xl border p-5 text-center transition-all duration-300 ease-out hover:-translate-y-1.5 hover:scale-[1.04] ${theme.cardClass}`}
                >
                  <div className="flex flex-col items-center">
                    <span
                      className={`mb-3.5 flex h-12 w-12 items-center justify-center rounded-full transition-transform duration-300 ease-out group-hover:-rotate-6 group-hover:scale-110 ${theme.iconBoxClass}`}
                    >
                      <Icon size={26} weight="duotone" aria-hidden="true" />
                    </span>
                    <span className="text-sm font-semibold leading-snug text-slate-800">
                      {category.name}
                    </span>
                  </div>
                  <span
                    className={`mt-3 text-xs font-semibold opacity-0 translate-y-1 transition-all duration-300 group-hover:translate-y-0 group-hover:opacity-100 ${theme.exploreClass}`}
                  >
                    {exploreLabel}
                  </span>
                </Link>
              </li>
            );
          })}

          <li className="h-full">
            <Link
              to="/search"
              className={`group flex h-full flex-col items-center justify-between rounded-2xl border p-5 text-center transition-all duration-300 ease-out hover:-translate-y-1.5 hover:scale-[1.04] ${THEME_OTHER.cardClass}`}
            >
              <div className="flex flex-col items-center">
                <span
                  className={`mb-3.5 flex h-12 w-12 items-center justify-center rounded-full transition-transform duration-300 ease-out group-hover:-rotate-6 group-hover:scale-110 ${THEME_OTHER.iconBoxClass}`}
                >
                  <THEME_OTHER.Icon size={26} weight="duotone" aria-hidden="true" />
                </span>
                <span className="text-sm font-semibold leading-snug text-slate-800">
                  {otherLabel}
                </span>
              </div>
              <span
                className={`mt-3 text-xs font-semibold opacity-0 translate-y-1 transition-all duration-300 group-hover:translate-y-0 group-hover:opacity-100 ${THEME_OTHER.exploreClass}`}
              >
                {exploreLabel}
              </span>
            </Link>
          </li>
        </ul>
      )}
    </section>
  );
}
