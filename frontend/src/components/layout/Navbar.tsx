// File: src/components/layout/Navbar.tsx
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import {
  ArrowRight,
  ChevronDown,
  Clock3,
  CalendarDays,
  Music4,
  Trophy,
  Ticket,
  LayoutDashboard,
  LogIn,
  LogOut,
  RotateCcw,
  Search,
  ShoppingBag,
  UserPlus,
  UserRound,
  X,
  Sparkles,
} from "lucide-react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { toast } from "react-toastify";
import { useTranslation } from "react-i18next";
import { confirmDestructiveAction } from "../../lib/swal";
import { hasRealmRole } from "../../lib/auth";
import { eventService } from "../../services/eventService";
import { userService, UserProfile } from "../../services/userService";
import { EventSummary } from "../../types/api";
import { CHATBOT_PROMPT_EVENT } from "../chatbot/FloatingChatbot";
import LanguageSwitcher from "../common/LanguageSwitcher";

const LOGIN_TOAST_KEY = "flashTicket:pendingLoginToast";
export const PROFILE_UPDATED_EVENT = "flashTicket:profileUpdated";

function getSuggestionImage(event: EventSummary) {
  return (
    event.thumbnailUrl ||
    event.bannerUrl ||
    event.images?.find((image) => image.type === "THUMBNAIL")?.url ||
    event.images?.find((image) => image.type === "BANNER")?.url ||
    "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?q=80&w=500&auto=format&fit=crop"
  );
}

function getSuggestionStartDate(event: EventSummary) {
  return new Date(event.schedule?.startDatetime || event.startDatetime);
}

function formatSuggestionDate(event: EventSummary, language: string, fallback: string) {
  const date = getSuggestionStartDate(event);
  if (Number.isNaN(date.getTime())) return fallback;

  return date
    .toLocaleDateString(language === "en" ? "en-US" : "vi-VN", {
      day: "numeric",
      month: "long",
      year: "numeric",
    })
    .replace(" tháng ", " Tháng ");
}

function normalizeSuggestionText(value?: string | null) {
  return (value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .trim();
}

function getSuggestionSearchText(event: EventSummary) {
  return normalizeSuggestionText(
    [
      event.title,
      event.shortDescription,
      event.category?.name,
      event.venue?.name,
      event.venue?.city,
      event.venueName,
      event.city,
      ...(event.tags || []),
      ...(event.categories || []).map((category) => category.name),
    ]
      .filter(Boolean)
      .join(" "),
  );
}

function getUpcomingSuggestions(events: EventSummary[], keyword: string) {
  const now = new Date();
  const keywordParts = normalizeSuggestionText(keyword).split(/\s+/).filter(Boolean);

  return events
    .filter((event) => {
      const startDate = getSuggestionStartDate(event);
      if (Number.isNaN(startDate.getTime()) || startDate < now) return false;

      const eventText = getSuggestionSearchText(event);
      return keywordParts.every((part) => eventText.includes(part));
    })
    .sort(
      (first, second) =>
        getSuggestionStartDate(first).getTime() -
        getSuggestionStartDate(second).getTime(),
    )
    .slice(0, 3);
}

const Navbar = () => {
  const { keycloak, initialized } = useKeycloak();
  const { i18n, t } = useTranslation();
  const location = useLocation();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [avatarBroken, setAvatarBroken] = useState(false);
  const [showAuthPopup, setShowAuthPopup] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const navigate = useNavigate();
  const searchDropdownRef = useRef<HTMLDivElement>(null);
  const [activeSuggestion, setActiveSuggestion] = useState(-1);
  const [isSearchFocused, setIsSearchFocused] = useState(false);
  const [searchStatus, setSearchStatus] = useState<"pending" | "success" | "error">("pending");
  const [searchSuggestions, setSearchSuggestions] = useState<EventSummary[]>([]);
  const accountMenuRef = useRef<HTMLDetailsElement>(null);
  const promptMenuRef = useRef<HTMLDetailsElement>(null);
  const authDialogRef = useRef<HTMLDialogElement>(null);
  const authTriggerRef = useRef<HTMLButtonElement>(null);
  const isAuthenticated = initialized && !!keycloak?.authenticated;
  const hasOrganizerTokenRole = hasRealmRole(keycloak?.tokenParsed, "ORGANIZER");
  const hasOrganizerProfileRole = Boolean(
    profile?.roles?.some((role) => role.toUpperCase() === "ORGANIZER"),
  );
  const isOrganizer = hasOrganizerTokenRole || hasOrganizerProfileRole;
  const isAdmin = hasRealmRole(keycloak?.tokenParsed, "ADMIN");
  const hasOrganizerApplication = Boolean(profile?.organizerProfileId);
  const quickPrompts = useMemo(
    () => [
      t("nav.chatbotPromptMusic"),
      t("nav.chatbotPromptWeekend"),
      t("nav.chatbotPromptSport"),
    ],
    [t],
  );

  const accountName = (
    profile?.displayName ||
    `${profile?.firstName || ""} ${profile?.lastName || ""}`.trim() ||
    (keycloak.tokenParsed?.name as string | undefined) ||
    `${keycloak.tokenParsed?.given_name || ""} ${keycloak.tokenParsed?.family_name || ""}`.trim() ||
    (keycloak.tokenParsed?.preferred_username as string | undefined) ||
    "User"
  ).trim();

  const avatarSrc = avatarBroken ? undefined : profile?.avatarUrl;
  const avatarInitials = accountName.trim().split(/\s+/).slice(0, 2)
    .map(word => Array.from(word)[0]).join("").toLocaleUpperCase();


  const handleLogout = async () => {
    const confirmed = await confirmDestructiveAction({
      title: t("swal.logoutTitle"),
      text: t("swal.logoutText"),
      confirmButtonText: t("swal.logoutConfirm"),
      cancelButtonText: t("swal.logoutCancel"),
    });
    if (!confirmed) return;

    await keycloak.logout({
      redirectUri: `${window.location.origin}/`,
    });
  };

  const handleQuickPrompt = (prompt: string) => {
    window.dispatchEvent(new CustomEvent(CHATBOT_PROMPT_EVENT, { detail: prompt }));
    promptMenuRef.current?.removeAttribute("open");
    promptMenuRef.current?.querySelector("summary")?.focus();
  };

  const handleLogin = () => {
    sessionStorage.setItem(LOGIN_TOAST_KEY, "1");
    void keycloak?.login();
  };

  const handleRegister = () => {
    void keycloak?.register();
  };

  useEffect(() => {
    if (!isAuthenticated) return;
    const shouldNotify = sessionStorage.getItem(LOGIN_TOAST_KEY) === "1";
    if (!shouldNotify) return;

    toast.success(t("auth.loginSuccess"));
    sessionStorage.removeItem(LOGIN_TOAST_KEY);
  }, [isAuthenticated, t]);

  useEffect(() => {
    if (!isAuthenticated) {
      setProfile(null);
      return;
    }

    let cancelled = false;

    const loadProfile = async () => {
      const data = await userService.getProfile();
      if (!cancelled) {
        setProfile(data);
        setAvatarBroken(false);
      }
    };

    void loadProfile();

    window.addEventListener(PROFILE_UPDATED_EVENT, loadProfile);
    return () => {
      cancelled = true;
      window.removeEventListener(PROFILE_UPDATED_EVENT, loadProfile);
    };
  }, [isAuthenticated]);

  useEffect(() => {
    const keyword = searchTerm.trim();
    if (keyword.length < 2) {
      setSearchSuggestions([]);
      return;
    }

    setSearchStatus("pending");
    setSearchSuggestions([]);
    let cancelled = false;
    const timer = window.setTimeout(async () => {
      try {
        const page = await eventService.getEvents({
          search: keyword,
          size: 8,
          sort: "startDatetime,asc",
        });
        let upcoming = getUpcomingSuggestions(page.content, keyword);

        if (upcoming.length < 3) {
          const [fallbackKeyword] = keyword.split(/\s+/);
          const fallbackPage = await eventService.getEvents({
            search: fallbackKeyword,
            size: 12,
            sort: "startDatetime,asc",
          });
          upcoming = getUpcomingSuggestions(fallbackPage.content, keyword);
        }

        if (!cancelled) {
          setSearchSuggestions(upcoming);
          setSearchStatus("success");
        }
      } catch {
        if (!cancelled) setSearchStatus("error");
      }
    }, 250);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [searchTerm]);

  const searchUrl = searchTerm.trim() ? `/search?search=${encodeURIComponent(searchTerm.trim())}` : "/search";
  const shouldShowSearchSuggestions =
    isSearchFocused && searchTerm.trim().length >= 2;

  useLayoutEffect(() => {
    const dropdown = searchDropdownRef.current;
    const searchBar = dropdown?.parentElement;
    if (!dropdown || !searchBar) return;

    const fitDropdown = () => {
      const availableWidth = document.documentElement.clientWidth - searchBar.getBoundingClientRect().left - 16;
      dropdown.style.setProperty("--search-dropdown-available", Math.max(0, availableWidth) + "px");
    };

    fitDropdown();
    window.addEventListener("resize", fitDropdown);
    const observer = new ResizeObserver(fitDropdown);
    observer.observe(searchBar);
    return () => {
      window.removeEventListener("resize", fitDropdown);
      observer.disconnect();
    };
  }, [shouldShowSearchSuggestions]);

  useEffect(() => {
    if (shouldShowSearchSuggestions && activeSuggestion >= 0) {
      document.getElementById("nav-search-option-" + activeSuggestion)?.scrollIntoView({ block: "nearest" });
    }
  }, [activeSuggestion, shouldShowSearchSuggestions]);

  useEffect(() => {
    if (!showAuthPopup || isAuthenticated) return;
    const dialog = authDialogRef.current;
    // Native dialog giữ focus trong popup và chặn thao tác với nội dung bên dưới.
    dialog?.showModal();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      dialog?.close();
      document.body.style.overflow = previousOverflow;
      authTriggerRef.current?.focus();
    };
  }, [showAuthPopup, isAuthenticated]);

  useEffect(() => {
    const closeOnOutsideClick = (event: PointerEvent) => {
      if (!accountMenuRef.current?.contains(event.target as Node)) {
        accountMenuRef.current?.removeAttribute("open");
      }
      if (!promptMenuRef.current?.contains(event.target as Node)) {
        promptMenuRef.current?.removeAttribute("open");
      }
    };
    document.addEventListener("pointerdown", closeOnOutsideClick);
    return () => document.removeEventListener("pointerdown", closeOnOutsideClick);
  }, []);

  useEffect(() => {
    accountMenuRef.current?.removeAttribute("open");
    promptMenuRef.current?.removeAttribute("open");
    setIsSearchFocused(false);
  }, [location.pathname, location.search]);

  useEffect(() => {
    if (location.pathname !== "/" || location.hash !== "#discovery-promo-title") return;
    document.getElementById("discovery-promo-title")?.scrollIntoView({
      behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth",
      block: "start",
    });
  }, [location.pathname, location.hash, location.key]);

  return (
    <>
    <nav className="navbar discovery-navbar" data-discovery-header={location.pathname === "/" || location.pathname === "/search" ? true : undefined} aria-label={t("nav.home")}>
      <div className="container nav-container">
        <div className="nav-left">
          <Link
            to="/"
            className="logo"
            style={{ transition: "none" }}
          >
            <img className="nav-brand-cat" src="/icons/icons8-black-cat.png" alt="" width={32} height={32} />
            <span>FlashTicket</span>
          </Link>
        </div>

        <div className="discovery-nav-links">
          <Link to="/search" aria-current={location.pathname === "/search" ? "page" : location.pathname.startsWith("/event/") ? "location" : undefined}>{t("nav.events")}</Link>
          <Link to="/#discovery-promo-title">{t("nav.contact")}</Link>
        </div>
        <div className="nav-center">
          <form
            className="search-bar"
            role="search"
            aria-label={t("common.search")}
            autoComplete="off"
            onSubmit={(e) => {
              e.preventDefault();
              const selected = shouldShowSearchSuggestions ? searchSuggestions[activeSuggestion] : undefined;
              setIsSearchFocused(false);
              setActiveSuggestion(-1);
              navigate(selected ? `/event/${selected.slug || selected.id}` : searchUrl);
            }}
            onFocus={() => {
              promptMenuRef.current?.removeAttribute("open");
              setIsSearchFocused(true);
            }}
            onBlur={(event) => {
              if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
                setIsSearchFocused(false);
                setActiveSuggestion(-1);
              }
            }}
            onKeyDown={(event) => {
              if (event.nativeEvent.isComposing) return;
              if (event.key === "Escape" && shouldShowSearchSuggestions) {
                event.preventDefault();
                event.currentTarget.querySelector("input")?.focus();
                setIsSearchFocused(false);
                setActiveSuggestion(-1);
              }
              if (event.target instanceof HTMLInputElement && searchTerm.trim().length >= 2 &&
                  (event.key === "ArrowDown" || event.key === "ArrowUp")) {
                event.preventDefault();
                setIsSearchFocused(true);
                const count = searchSuggestions.length;
                if (count) setActiveSuggestion(index => event.key === "ArrowDown"
                  ? (index + 1) % count
                  : index <= 0 ? count - 1 : index - 1);
              }
            }}
          >
            <input
              name="search"
              role="combobox"
              aria-autocomplete="list"
              aria-expanded={shouldShowSearchSuggestions}
              aria-controls={shouldShowSearchSuggestions ? "nav-search-options" : undefined}
              aria-activedescendant={shouldShowSearchSuggestions && searchSuggestions[activeSuggestion] ? `nav-search-option-${activeSuggestion}` : undefined}
              aria-label={t("common.search")}
              type="text"
              className="search-input"
              placeholder={t("nav.searchPlaceholder")}
              value={searchTerm}
              onChange={(event) => {
                setActiveSuggestion(-1);
                setIsSearchFocused(true);
                setSearchTerm(event.target.value);
              }}
            />
            <button type="submit" className="search-submit" aria-label={t("common.search")}>
              <Search className="search-icon" size={18} />
            </button>

            {shouldShowSearchSuggestions ? (
              <div className="search-suggestion-dropdown" ref={searchDropdownRef}>
                {searchStatus !== "success" || !searchSuggestions.length ? (
                  <p className="nav-search-status" role="status">{t(`nav.searchStatus.${searchStatus}`)}</p>
                ) : null}
                <div id="nav-search-options" role="listbox" aria-label={t("common.search")}>
                  {searchSuggestions.map((event, index) => (
                    <Link
                      key={event.id}
                      id={`nav-search-option-${index}`}
                      role="option"
                      aria-selected={activeSuggestion === index}
                      tabIndex={-1}
                      to={`/event/${event.slug || event.id}`}
                      className="search-suggestion-main"
                      onClick={() => {
                        setActiveSuggestion(-1);
                        setIsSearchFocused(false);
                      }}
                    >
                      <img src={getSuggestionImage(event)} alt="" className="search-suggestion-thumb" />
                      <span className="search-suggestion-copy">
                        <strong>{event.title}</strong>
                        <small>{formatSuggestionDate(event, i18n.resolvedLanguage || "vi", t("common.updating"))}</small>
                      </span>
                      <ArrowRight size={16} aria-hidden="true" />
                    </Link>
                  ))}
                </div>
                <Link to={searchUrl} className="search-suggestion-more" onClick={() => {
                  setActiveSuggestion(-1);
                  setIsSearchFocused(false);
                }}>
                  <span>{t("nav.searchAllResults", { keyword: searchTerm.trim() })}</span>
                  <ArrowRight size={16} aria-hidden="true" />
                </Link>
              </div>
            ) : null}
          </form>

          <details
            className="nav-prompt-dropdown"
            ref={promptMenuRef}
            onToggle={(event) => {
              if (event.currentTarget.open) setIsSearchFocused(false);
            }}
            onKeyDown={(event) => {
              if (event.key === "Escape") {
                event.currentTarget.open = false;
                event.currentTarget.querySelector("summary")?.focus();
              }
            }}
          >
            <summary className="nav-prompt-trigger" aria-label={t("nav.chatbotPrompt")}>
              <Sparkles className="nav-prompt-ai-icon" size={19} aria-hidden="true" />
              <span className="nav-prompt-label">{t("nav.chatbotPrompt")}</span>
              <ChevronDown size={15} />
            </summary>
            <div className="nav-prompt-menu">
              {quickPrompts.map((prompt, index) => {
                const Icon = [Music4, CalendarDays, Trophy][index];
                return (
                  <button
                    key={prompt}
                    type="button"
                    className="nav-prompt-item"
                    onClick={() => handleQuickPrompt(prompt)}
                  >
                    <Icon size={18} aria-hidden="true" />
                    {prompt}
                  </button>
                );
              })}
            </div>
          </details>
        </div>

        <div className="nav-right nav-actions">
          {!initialized ? (
            <div className="nav-account-placeholder" aria-hidden="true" />
          ) : !isAuthenticated ? (
            <>
              <button
                type="button"
                className="nav-quick-link nav-auth-trigger"
                ref={authTriggerRef}
                onClick={() => setShowAuthPopup(true)}
                aria-haspopup="dialog"
                aria-expanded={showAuthPopup}
                aria-label={t("auth.signIn")}
                title={t("auth.signIn")}
              >
                <UserRound size={20} />
                <span>
                  <strong>{t("auth.signIn")}</strong>

                </span>
              </button>
            </>
          ) : (
            <details
              className="account-dropdown"
              ref={accountMenuRef}
              onKeyDown={(event) => {
                if (event.key === "Escape") {
                  event.currentTarget.open = false;
                  event.currentTarget.querySelector("summary")?.focus();
                }
              }}
            >
              <summary className="account-trigger" aria-label={t("nav.accountLabel")} title={accountName}>
                {avatarSrc ? (
                  <img src={avatarSrc} alt="" className="nav-avatar"
                    onError={() => setAvatarBroken(true)} />
                ) : (
                  <span className="nav-avatar nav-avatar-initials" aria-hidden="true">{avatarInitials}</span>
                )}
                <span className="nav-account-label">{t("nav.accountLabel")}</span>
                <ChevronDown className="account-trigger-chevron" size={16} />
              </summary>

              <div className="dropdown-menu">
                <Link to="/profile" className="dropdown-item">
                  <UserRound size={17} />
                  {t("nav.personalProfile")}
                </Link>
                <Link to="/my-tickets" className="dropdown-item">
                  <Ticket size={17} />
                  {t("nav.myTickets")}
                </Link>
                <Link to="/my-orders" className="dropdown-item">
                  <ShoppingBag size={17} />
                  {t("nav.myOrders")}
                </Link>
                <hr />
                {isOrganizer ? (
                  <Link to="/organizer" className="dropdown-item">
                    <LayoutDashboard size={17} />
                    {t("nav.organizerWorkspace")}
                  </Link>
                ) : hasOrganizerApplication ? (
                  <div className="dropdown-item dropdown-item-muted">
                    <Clock3 size={17} />
                    {t("nav.organizerPending")}
                  </div>
                ) : (
                  <Link to="/organizers/apply" className="dropdown-item">
                    <UserPlus size={17} />
                    {t("nav.organizerApply")}
                  </Link>
                )}
                {isAdmin && (
                  <Link to="/admin/events" className="dropdown-item">
                    <LayoutDashboard size={17} />
                    {t("adminEventReview.title")}
                  </Link>
                )}
                {isAdmin ? (
                  <Link to="/admin/organizers" className="dropdown-item">
                    <LayoutDashboard size={17} />
                    {t("nav.adminOrganizerReview")}
                  </Link>
                ) : null}
                {isAdmin ? (
                  <Link to="/admin/refunds" className="dropdown-item">
                    <RotateCcw size={17} />
                    {t("nav.adminRefunds", "Quản lý hoàn tiền")}
                  </Link>
                ) : null}
                <hr />
                <button
                  onClick={() => void handleLogout()}
                  className="dropdown-item logout w-full text-left"
                >
                  <LogOut size={17} />
                  {t("organizer.logout")}
                </button>
              </div>
            </details>
          )}
        </div>
        <LanguageSwitcher className="language-switcher-nav" />
      </div>
    </nav>
    {showAuthPopup && !isAuthenticated ? (
      <dialog
        className="auth-popup-backdrop"
        ref={authDialogRef}
        aria-labelledby="auth-popup-title"
        onCancel={() => setShowAuthPopup(false)}
        onClick={(event) => {
          if (event.target === event.currentTarget) setShowAuthPopup(false);
        }}
      >
        <div className="auth-popup-card">
          <div className="auth-popup-header">
            <button
              type="button"
              className="auth-popup-close"
              autoFocus
              aria-label={t("common.close")}
              onClick={() => setShowAuthPopup(false)}
            >
              <X size={18} />
            </button>
            <div className="auth-popup-user-icon" aria-hidden="true">
              <UserRound size={32} />
            </div>
            <h2 id="auth-popup-title">{t("auth.welcomeBack")}</h2>
            <p>{t("auth.signInPrompt")}</p>
          </div>

          <div className="auth-popup-body">
            <button
              type="button"
              className="auth-popup-button auth-popup-button-primary"
              onClick={handleLogin}
            >
              <LogIn size={18} />
              {t("auth.signIn")}
            </button>
            <button
              type="button"
              className="auth-popup-button auth-popup-button-secondary"
              onClick={handleRegister}
            >
              <UserPlus size={18} />
              {t("auth.createAccount")}
            </button>
          </div>
        </div>
      </dialog>
    ) : null}
    </>
  );
};

export default Navbar;
