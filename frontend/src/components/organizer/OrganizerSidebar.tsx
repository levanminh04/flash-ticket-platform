import { NavLink } from "react-router-dom";
import type { ComponentType } from "react";
import {
  LayoutGrid as BiSolidCategoryAlt,
  CalendarDays as FaCalendarAlt,
  UserRound as FaUserAlt,
  QrCode as IoQrCodeSharp,
  type LucideProps as IconProps,
} from "lucide-react";
import { useTranslation } from "react-i18next";

const navItems: Array<{
  to: string;
  labelKey: string;
  descriptionKey: string;
  icon: ComponentType<IconProps>;
  end?: boolean;
}> = [
  {
    to: "/organizer",
    labelKey: "organizer.overview",
    descriptionKey: "organizer.dashboard",
    icon: BiSolidCategoryAlt,
    end: true,
  },
  {
    to: "/organizer/events",
    labelKey: "organizer.eventManagement",
    descriptionKey: "organizer.events",
    icon: FaCalendarAlt,
  },
  {
    to: "/organizer/profile",
    labelKey: "organizer.profile",
    descriptionKey: "organizer.profile",
    icon: FaUserAlt,
  },
  {
    to: "/organizer/check-in",
    labelKey: "organizer.checkIn",
    descriptionKey: "organizer.checkIn",
    icon: IoQrCodeSharp,
  },
];

type OrganizerSidebarProps = {
  isCollapsed: boolean;
  onToggle: () => void;
};

export default function OrganizerSidebar({
  isCollapsed,
  onToggle,
}: OrganizerSidebarProps) {
  const { t } = useTranslation();

  return (
    <aside
      className={`organizer-sidebar ${isCollapsed ? "is-collapsed" : ""}`}
      aria-label={t("nav.organizer")}
    >
      <div className="organizer-sidebar-brand">
        <button
          type="button"
          className="organizer-sidebar-logo"
          onClick={onToggle}
          aria-label={isCollapsed ? t("organizer.openSidebar") : t("organizer.closeSidebar")}
          title={isCollapsed ? t("organizer.openSidebar") : t("organizer.closeSidebar")}
        >
          O
        </button>
        <div>
          <h1>Organizer</h1>
          <p>{t("organizer.eventOperations")}</p>
        </div>
      </div>

      <nav className="organizer-sidebar-nav">
        {navItems.map((item) => {
          const Icon = item.icon;

          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `organizer-nav-link ${isActive ? "active" : ""}`
              }
            >
              <Icon size={20} />
              <span>
                <strong>{t(item.labelKey)}</strong>
                <small>{t(item.descriptionKey)}</small>
              </span>
            </NavLink>
          );
        })}
      </nav>
    </aside>
  );
}
