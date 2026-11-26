import { useEffect, useRef, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import {
  BarChart3,
  Building2,
  Calendar,
  ChevronDown,
  LogOut,
  MapPin,
  MessageCircle,
  RotateCcw,
  Scale,
  UserRound,
} from "lucide-react";
import { useUserProfile } from "../../hooks/useUserProfile";
import LanguageSwitcher from "../common/LanguageSwitcher";
import { confirmDestructiveAction } from "../../lib/swal";
import "../organizer/tix-organizer-nav.css";

export type TixAdminNavTab = "sales" | "organizers" | "events" | "venues" | "reconciliation" | "refunds" | "chatbot";

interface TixAdminTopNavProps {
  activeTab?: TixAdminNavTab;
}

export default function TixAdminTopNav({ activeTab }: TixAdminTopNavProps) {
  const { keycloak } = useKeycloak();
  const { t, i18n } = useTranslation();
  const location = useLocation();
  const isVi = !i18n.resolvedLanguage?.startsWith("en");

  const currentTab: TixAdminNavTab =
    activeTab ??
    (location.pathname.startsWith("/admin/sales")
      ? "sales"
      : location.pathname.startsWith("/admin/events")
      ? "events"
      : location.pathname.startsWith("/admin/venues")
      ? "venues"
      : location.pathname.startsWith("/admin/reconciliation")
      ? "reconciliation"
      : location.pathname.startsWith("/admin/refunds")
      ? "refunds"
      : location.pathname.startsWith("/admin/chatbot")
      ? "chatbot"
      : "organizers");

  const { data: userProfile } = useUserProfile();
  const [avatarBroken, setAvatarBroken] = useState(false);
  const accountMenuRef = useRef<HTMLDetailsElement>(null);

  const accountName = (
    userProfile?.displayName ||
    `${userProfile?.firstName || ""} ${userProfile?.lastName || ""}`.trim() ||
    (keycloak.tokenParsed?.name as string | undefined) ||
    `${keycloak.tokenParsed?.given_name || ""} ${keycloak.tokenParsed?.family_name || ""}`.trim() ||
    (keycloak.tokenParsed?.preferred_username as string | undefined) ||
    (isVi ? "Quản trị viên" : "Administrator")
  ).trim();

  const avatarSrc = avatarBroken ? undefined : userProfile?.avatarUrl;
  const avatarInitials =
    accountName
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((word) => Array.from(word)[0])
      .join("")
      .toLocaleUpperCase() || "AD";

  const handleLogout = async () => {
    const confirmed = await confirmDestructiveAction({
      title: t("swal.logoutTitle", { defaultValue: "Đăng xuất" }),
      text: t("swal.logoutText", { defaultValue: "Bạn có chắc chắn muốn đăng xuất không?" }),
      confirmButtonText: t("swal.logoutConfirm", { defaultValue: "Đăng xuất" }),
      cancelButtonText: t("swal.logoutCancel", { defaultValue: "Hủy" }),
    });
    if (!confirmed) return;

    await keycloak.logout({
      redirectUri: `${window.location.origin}/`,
    });
  };

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      if (accountMenuRef.current && !accountMenuRef.current.contains(target)) {
        accountMenuRef.current.removeAttribute("open");
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && accountMenuRef.current) {
        accountMenuRef.current.removeAttribute("open");
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

  return (
    <header className="tix-topbar">
      {/* 1. Left Brand / Logo */}
      <Link to="/" className="logo" aria-label="Trang chủ FlashTicket">
        <img
          className="nav-brand-cat"
          src="/icons/icons8-black-cat.png"
          alt=""
          width={32}
          height={32}
        />
        <span>FlashTicket</span>
      </Link>

      {/* 2. Center Floating Pill Navigation */}
      <nav className="tix-pill-nav" aria-label="Admin Navigation">
        <Link
          to="/admin/sales"
          className={`tix-pill-item ${currentTab === "sales" ? "active" : ""}`}
        >
          <BarChart3 size={15} aria-hidden="true" />
          <span>{isVi ? "Doanh số" : "Sales"}</span>
        </Link>
        <Link
          to="/admin/organizers"
          className={`tix-pill-item ${currentTab === "organizers" ? "active" : ""}`}
        >
          <Building2 size={15} aria-hidden="true" />
          <span>{isVi ? "Ban tổ chức" : "Organizers"}</span>
        </Link>
        <Link
          to="/admin/events"
          className={`tix-pill-item ${currentTab === "events" ? "active" : ""}`}
        >
          <Calendar size={15} aria-hidden="true" />
          <span>{isVi ? "Sự kiện" : "Events"}</span>
        </Link>
        <Link
          to="/admin/venues"
          className={`tix-pill-item ${currentTab === "venues" ? "active" : ""}`}
        >
          <MapPin size={15} aria-hidden="true" />
          <span>{isVi ? "Địa điểm" : "Venues"}</span>
        </Link>
        <Link
          to="/admin/reconciliation"
          className={`tix-pill-item ${currentTab === "reconciliation" ? "active" : ""}`}
        >
          <Scale size={15} aria-hidden="true" />
          <span>{isVi ? "Đối soát" : "Reconciliation"}</span>
        </Link>
        <Link
          to="/admin/refunds"
          className={`tix-pill-item ${currentTab === "refunds" ? "active" : ""}`}
        >
          <RotateCcw size={15} aria-hidden="true" />
          <span>{isVi ? "Hoàn tiền" : "Refunds"}</span>
        </Link>
        <Link
          to="/admin/chatbot"
          className={`tix-pill-item ${currentTab === "chatbot" ? "active" : ""}`}
          aria-current={currentTab === "chatbot" ? "page" : undefined}
        >
          <MessageCircle size={15} aria-hidden="true" />
          <span>{t("chatbot.switchToAdmin")}</span>
        </Link>
      </nav>

      {/* 3. Right Account & Language Switcher */}
      <div className="tix-topbar-right">
        <details
          className="account-dropdown tix-account-dropdown"
          ref={accountMenuRef}
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              event.currentTarget.open = false;
              event.currentTarget.querySelector("summary")?.focus();
            }
          }}
        >
          <summary
            className="account-trigger tix-account-trigger"
            aria-label={t("nav.accountLabel", { defaultValue: "Tài khoản" })}
            title={accountName}
          >
            {avatarSrc ? (
              <img
                src={avatarSrc}
                alt=""
                className="nav-avatar tix-nav-avatar"
                onError={() => setAvatarBroken(true)}
              />
            ) : (
              <span className="nav-avatar nav-avatar-initials tix-nav-avatar" aria-hidden="true">
                {avatarInitials}
              </span>
            )}
            <span className="nav-account-label tix-nav-account-label">
              {t("nav.accountLabel", { defaultValue: "Tài khoản" })}
            </span>
            <ChevronDown className="account-trigger-chevron" size={15} />
          </summary>

          <div className="dropdown-menu tix-account-menu">
            <div className="tix-user-info-box">
              <div className="tix-user-name">{accountName}</div>
              <div className="tix-user-role">
                {userProfile?.email ||
                  (keycloak.tokenParsed?.email as string | undefined) ||
                  (isVi ? "Quản trị viên" : "Administrator")}
              </div>
            </div>
            <hr />
            <Link
              to="/profile"
              className="dropdown-item"
              onClick={() => accountMenuRef.current?.removeAttribute("open")}
            >
              <UserRound size={16} />
              <span>{t("nav.personalProfile", { defaultValue: "Hồ sơ cá nhân" })}</span>
            </Link>
            <hr />
            <button
              type="button"
              onClick={() => void handleLogout()}
              className="dropdown-item logout w-full text-left"
            >
              <LogOut size={16} />
              <span>{t("organizer.logout", { defaultValue: "Đăng xuất" })}</span>
            </button>
          </div>
        </details>

        <div className="tix-topbar-divider" />

        <LanguageSwitcher className="language-switcher-nav tix-language-switcher" />
      </div>
    </header>
  );
}
