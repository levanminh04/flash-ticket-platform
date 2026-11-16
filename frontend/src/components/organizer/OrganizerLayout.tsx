import { ReactNode, useEffect, useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { Link, useNavigate } from "react-router-dom";
import {
  ArrowLeft,
  LoaderCircle,
  Lock,
  LogIn,
  RotateCcw,
  ShieldAlert,
  Sparkles,
} from "lucide-react";
import { toast } from "react-toastify";
import { useTranslation } from "react-i18next";
import Navbar from "../layout/Navbar";
import Footer from "../common/Footer";
import OrganizerSidebar from "./OrganizerSidebar";
import OrganizerTopBar from "./OrganizerTopBar";
import OrganizerEventWorkspaceNav from "./OrganizerEventWorkspaceNav";
import { hasRealmRole } from "../../lib/auth";
import { confirmDestructiveAction } from "../../lib/swal";
import {
  organizerWorkspaceService,
  OrganizerEventDetail,
} from "../../services/organizerWorkspaceService";
import { userService, UserProfile } from "../../services/userService";

type OrganizerLayoutProps = {
  title?: string;
  description?: string;
  actions?: ReactNode;
  requireOrganizer?: boolean;
  hideTopBar?: boolean;
  hideSidebar?: boolean;
  hideHeader?: boolean;
  className?: string;
  children: ReactNode;
  showWorkflowNav?: boolean;
  eventId?: string;
};

type OrganizerEventHeaderActionsProps = {
  eventId?: string;
};

function OrganizerEventHeaderActions({ eventId }: OrganizerEventHeaderActionsProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [eventDetail, setEventDetail] = useState<OrganizerEventDetail | null>(null);
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    if (!eventId) {
      setEventDetail(null);
      return;
    }

    let cancelled = false;
    organizerWorkspaceService
      .getMyEvent(eventId)
      .then((event) => {
        if (!cancelled) setEventDetail(event);
      })
      .catch(() => {
        if (!cancelled) setEventDetail(null);
      });

    return () => {
      cancelled = true;
    };
  }, [eventId]);

  if (!eventId) {
    return null;
  }

  if ((eventDetail?.statistics?.ticketsSold ?? 0) > 0) {
    return null;
  }

  const handleCancel = async () => {
    const confirmed = await confirmDestructiveAction({
      title: t("organizer.deleteEventConfirm"),
      text: t("organizer.deleteEventText"),
      confirmButtonText: t("organizer.deleteEvent"),
      cancelButtonText: t("organizer.deleteEventKeep"),
    });

    if (!confirmed) return;

    setCancelling(true);
    try {
      await organizerWorkspaceService.deleteEvent(eventId);
      toast.success(t("organizer.deleteEventSuccess"));
      navigate("/organizer/events");
    } catch {
      toast.error(t("organizer.deleteEventFailed"));
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div className="organizer-header-event-actions">
      <button
        type="button"
        className="organizer-header-event-button organizer-header-event-button-cancel"
        onClick={handleCancel}
        disabled={cancelling}
      >
        {cancelling ? t("organizer.deletingEvent") : t("organizer.deleteEvent")}
      </button>
    </div>
  );
}

export default function OrganizerLayout({
  title = "",
  description = "",
  actions,
  requireOrganizer = true,
  hideTopBar = false,
  hideSidebar = false,
  hideHeader = false,
  className,
  children,
  showWorkflowNav = false,
  eventId,
}: OrganizerLayoutProps) {
  const { t } = useTranslation();
  const { keycloak, initialized } = useKeycloak();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(() => {
    return localStorage.getItem("organizer_sidebar_collapsed") === "true";
  });
  const hasOrganizerTokenRole = hasRealmRole(keycloak.tokenParsed, "ORGANIZER");
  const hasOrganizerProfileRole = Boolean(
    profile?.roles?.some((role) => role.toUpperCase() === "ORGANIZER"),
  );

  useEffect(() => {
    if (!initialized || !keycloak.authenticated || hasOrganizerTokenRole) return;

    userService
      .getProfile()
      .then((data) => setProfile(data))
      .catch(() => setProfile(null));
  }, [hasOrganizerTokenRole, initialized, keycloak.authenticated]);

  const refreshLoginSession = () => {
    void keycloak.login({
      redirectUri: window.location.href,
      prompt: "login",
    });
  };

  if (!initialized) {
    return (
      <div className="tix-auth-guard-page">
        <Navbar />
        <main className="tix-auth-guard-main">
          <div className="tix-auth-guard-card is-loading">
            <LoaderCircle className="tix-spin-icon" size={32} />
            <span style={{ fontSize: "14.5px", fontWeight: 500, color: "var(--text-muted, #666b74)" }}>
              {t("common.loading", "Đang tải...")}
            </span>
          </div>
        </main>
        <Footer />
      </div>
    );
  }

  if (requireOrganizer && !keycloak.authenticated) {
    return (
      <div className="tix-auth-guard-page">
        <Navbar />
        <main className="tix-auth-guard-main">
          <div className="tix-auth-guard-card">
            <div className="tix-auth-guard-icon-wrap">
              <Lock size={28} />
            </div>
            <h1 className="tix-auth-guard-title">{t("organizer.loginWorkspace")}</h1>
            <p className="tix-auth-guard-desc">{t("organizer.loginWorkspaceDescription")}</p>
            <div className="tix-auth-guard-actions">
              <button
                type="button"
                className="tix-btn-primary"
                onClick={() => void keycloak.login({ redirectUri: window.location.href })}
              >
                <LogIn size={16} />
                <span>{t("auth.signIn", "Đăng nhập")}</span>
              </button>
              <Link to="/" className="tix-btn-secondary">
                <ArrowLeft size={16} />
                <span>{t("organizer.backToHome", "Về trang chủ")}</span>
              </Link>
            </div>
          </div>
        </main>
        <Footer />
      </div>
    );
  }

  if (requireOrganizer && !hasOrganizerTokenRole) {
    return (
      <div className="tix-auth-guard-page">
        <Navbar />
        <main className="tix-auth-guard-main">
          <div className="tix-auth-guard-card">
            <div className="tix-auth-guard-icon-wrap is-warning">
              <ShieldAlert size={28} />
            </div>
            <h1 className="tix-auth-guard-title">
              {hasOrganizerProfileRole
                ? t("organizer.refreshLoginTitle")
                : t("organizer.organizerRoleMissing")}
            </h1>
            <p className="tix-auth-guard-desc">
              {hasOrganizerProfileRole
                ? t("organizer.refreshLoginDescription")
                : t("organizer.organizerRoleMissingDescription")}
            </p>
            <div className="tix-auth-guard-actions">
              {hasOrganizerProfileRole ? (
                <button
                  className="tix-btn-primary"
                  type="button"
                  onClick={refreshLoginSession}
                >
                  <RotateCcw size={16} />
                  <span>{t("organizer.refreshLogin")}</span>
                </button>
              ) : (
                <Link to="/organizers/apply" className="tix-btn-primary">
                  <Sparkles size={16} />
                  <span>{t("organizer.applyOrganizerBtn", "Đăng ký làm Nhà tổ chức")}</span>
                </Link>
              )}
              <Link to="/" className="tix-btn-secondary">
                <ArrowLeft size={16} />
                <span>{t("organizer.backToHome", "Về trang chủ")}</span>
              </Link>
            </div>
          </div>
        </main>
        <Footer />
      </div>
    );
  }

  return (
    <div
      className={`organizer-page ${isSidebarCollapsed ? "is-sidebar-collapsed" : ""} ${
        hideSidebar ? "is-no-sidebar" : ""
      } ${className || ""}`}
    >
      {!hideSidebar && (
        <OrganizerSidebar
          isCollapsed={isSidebarCollapsed}
          onToggle={() =>
            setIsSidebarCollapsed((value) => {
              const nextValue = !value;
              localStorage.setItem("organizer_sidebar_collapsed", String(nextValue));
              return nextValue;
            })
          }
        />
      )}

      <div className={`organizer-layout ${hideSidebar ? "is-no-sidebar-layout" : ""}`}>
        <main className={`organizer-main organizer-main-full ${showWorkflowNav ? "has-workflow-nav" : ""} ${hideSidebar ? "is-no-sidebar-main" : ""}`}>
          {!hideHeader && (
            <header className="organizer-header">
              <div className="organizer-header-title-area">
                <h1 className="organizer-title">{title}</h1>
                {description ? (
                  <p className="organizer-description">{description}</p>
                ) : null}
              </div>
              <div className="organizer-header-right">
                {showWorkflowNav ? <OrganizerEventHeaderActions eventId={eventId} /> : null}
                {!hideTopBar ? <OrganizerTopBar /> : null}
                {actions ? (
                  <div className="organizer-header-actions">{actions}</div>
                ) : null}
              </div>
            </header>
          )}

          {showWorkflowNav ? (
            <OrganizerEventWorkspaceNav eventId={eventId} />
          ) : null}

          <div className="organizer-content">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
