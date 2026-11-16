import { useRef, useState, useEffect } from "react";
import { Link, useLocation } from "react-router-dom";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import {
  Building2,
  Calendar,
  ChevronDown,
  LayoutGrid,
  LogOut,
  PlusCircle,
  QrCode,
  UserRound,
} from "lucide-react";
import { useUserProfile } from "../../hooks/useUserProfile";
import LanguageSwitcher from "../common/LanguageSwitcher";
import { confirmDestructiveAction } from "../../lib/swal";
import "./tix-organizer-nav.css";

export type TixNavTab = "dashboard" | "create" | "events" | "profile" | "checkin";

interface TixOrganizerTopNavProps {
  activeTab?: TixNavTab;
}

export default function TixOrganizerTopNav({ activeTab }: TixOrganizerTopNavProps) {
  const { keycloak } = useKeycloak();
  const { t } = useTranslation();
  const location = useLocation();

  // Xác định tab đang active tự động nếu không truyền prop
  const currentTab: TixNavTab =
    activeTab ??
    (location.pathname === "/organizer"
      ? "dashboard"
      : location.pathname.startsWith("/organizer/events/new")
      ? "create"
      : location.pathname.startsWith("/organizer/events")
      ? "events"
      : location.pathname.startsWith("/organizer/profile")
      ? "profile"
      : location.pathname.startsWith("/organizer/check-in")
      ? "checkin"
      : "dashboard");

  // User Profile
  const { data: userProfile } = useUserProfile();
  const [avatarBroken, setAvatarBroken] = useState(false);
  const accountMenuRef = useRef<HTMLDetailsElement>(null);

  const accountName = (
    userProfile?.displayName ||
    `${userProfile?.firstName || ""} ${userProfile?.lastName || ""}`.trim() ||
    (keycloak.tokenParsed?.name as string | undefined) ||
    `${keycloak.tokenParsed?.given_name || ""} ${keycloak.tokenParsed?.family_name || ""}`.trim() ||
    (keycloak.tokenParsed?.preferred_username as string | undefined) ||
    "Ban tổ chức"
  ).trim();

  const avatarSrc = avatarBroken ? undefined : userProfile?.avatarUrl;
  const avatarInitials =
    accountName
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((word) => Array.from(word)[0])
      .join("")
      .toLocaleUpperCase() || "TC";

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

  // Đóng dropdown khi click ngoài hoặc ấn Escape
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
      <nav className="tix-pill-nav" aria-label="Organizer Navigation">
        <Link
          to="/organizer"
          className={`tix-pill-item ${currentTab === "dashboard" ? "active" : ""}`}
        >
          <LayoutGrid size={15} aria-hidden="true" />
          <span>{t("organizer.dashboard", { defaultValue: "Dashboard" })}</span>
        </Link>
        <Link
          to="/organizer/events/new"
          className={`tix-pill-item ${currentTab === "create" ? "active" : ""}`}
        >
          <PlusCircle size={15} aria-hidden="true" />
          <span>{t("organizer.createEvent", { defaultValue: "Tạo sự kiện" })}</span>
        </Link>
        <Link
          to="/organizer/events"
          className={`tix-pill-item ${currentTab === "events" ? "active" : ""}`}
        >
          <Calendar size={15} aria-hidden="true" />
          <span>{t("organizer.events", { defaultValue: "Sự kiện của tôi" })}</span>
        </Link>
        <Link
          to="/organizer/profile"
          className={`tix-pill-item ${currentTab === "profile" ? "active" : ""}`}
        >
          <Building2 size={15} aria-hidden="true" />
          <span>{t("organizer.profile", { defaultValue: "Hồ sơ" })}</span>
        </Link>
        <Link
          to="/organizer/check-in"
          className={`tix-pill-item ${currentTab === "checkin" ? "active" : ""}`}
        >
          <QrCode size={15} aria-hidden="true" />
          <span>{t("organizer.checkIn", { defaultValue: "Check-in" })}</span>
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
                  t("organizer.organizerRole", { defaultValue: "Ban tổ chức" })}
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
