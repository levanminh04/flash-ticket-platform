import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useKeycloak } from "@react-keycloak/web";
import { Link, useParams } from "react-router-dom";
import {
  BadgeCheck,
  Building2,
  CalendarDays,
  Check,
  Copy,
  Heart,
  LoaderCircle,
  Search,
} from "lucide-react";
import { toast } from "react-toastify";
import { useTranslation } from "react-i18next";
import Footer from "../../components/common/Footer";
import { OrganizerProfile, organizerService } from "../../services/organizerService";
import "./organizer-dashboard.css";
import "./organizer-profile.css";

function organizerInitials(name?: string | null) {
  if (!name) return "FT";
  return name
    .split(" ")
    .map((part) => part.trim()[0])
    .filter(Boolean)
    .slice(0, 2)
    .join("")
    .toUpperCase();
}

export default function PublicOrganizerPage() {
  const { t } = useTranslation();
  const { slug } = useParams<{ slug: string }>();
  const { keycloak, initialized } = useKeycloak();
  const queryClient = useQueryClient();

  const [copiedSubject, setCopiedSubject] = useState(false);

  const organizerQuery = useQuery({
    queryKey: ["public-organizer", slug],
    queryFn: ({ signal }) => organizerService.getPublicOrganizerBySlug(slug || "", signal),
    enabled: Boolean(slug),
  });

  const organizer = organizerQuery.data;
  const organizerProfileId = organizer?.id;
  const organizerUserId = organizer?.userId;
  const isOwnOrganizer = Boolean(keycloak.subject && keycloak.subject === organizerUserId);


  const { data: isFollowing = false } = useQuery({
    queryKey: ["organizer-follow-status", organizerProfileId],
    queryFn: () => organizerService.isFollowingOrganizer(organizerProfileId || ""),
    enabled:
      initialized &&
      Boolean(keycloak.authenticated) &&
      Boolean(organizerProfileId) &&
      !isOwnOrganizer,
  });

  const followMutation = useMutation({
    mutationFn: async () => {
      if (!organizerProfileId) throw new Error("Missing organizer profile id");
      return isFollowing
        ? organizerService.unfollowOrganizer(organizerProfileId)
        : organizerService.followOrganizer(organizerProfileId);
    },
    onSuccess: (response) => {
      queryClient.setQueryData(
        ["organizer-follow-status", organizerProfileId],
        response.isFollowing,
      );
      queryClient.setQueryData<OrganizerProfile | undefined>(
        ["public-organizer", slug],
        (current) =>
          current
            ? {
                ...current,
                followerCount: response.followerCount,
              }
            : current,
      );
      toast.success(
        response.isFollowing
          ? t("publicOrganizer.followSuccess")
          : t("publicOrganizer.unfollowSuccess"),
      );
    },
    onError: () => {
      toast.error(t("publicOrganizer.followFailed"));
    },
  });

  const handleFollowClick = () => {
    if (!keycloak.authenticated) {
      void keycloak.login();
      return;
    }
    followMutation.mutate();
  };

  const handleCopySubject = () => {
    const idValue = organizer?.userId || organizer?.id;
    if (!idValue) return;
    navigator.clipboard?.writeText(idValue);
    setCopiedSubject(true);
    toast.success(t("organizerProfile.copiedSubject", "Đã sao chép mã định danh!"));
    setTimeout(() => setCopiedSubject(false), 1500);
  };

  if (organizerQuery.isLoading) {
    return (
      <div className="tix-public-organizer-wrapper">
        <main className="tix-dashboard-page tix-profile-page tix-public-organizer-page">
          <div className="tix-dashboard-container">
            <div className="tix-public-state-card" role="status" aria-live="polite">
              <LoaderCircle className="tix-spin" size={24} />
              <span>{t("publicOrganizer.loading")}</span>
            </div>
          </div>
        </main>
        <Footer />
      </div>
    );
  }

  if (organizerQuery.isError || !organizer) {
    return (
      <div className="tix-public-organizer-wrapper">
        <main className="tix-dashboard-page tix-profile-page tix-public-organizer-page">
          <div className="tix-dashboard-container">
            <section className="tix-public-state-card">
              <h1>{t("publicOrganizer.notFoundTitle")}</h1>
              <p>{t("publicOrganizer.notFoundDescription")}</p>
              <Link className="tix-primary-cta-btn" to="/search" style={{ textDecoration: "none" }}>
                <Search size={14} aria-hidden="true" />
                <span>{t("publicOrganizer.searchOther")}</span>
              </Link>
            </section>
          </div>
        </main>
        <Footer />
      </div>
    );
  }

  const orgSubject = organizer.userId || organizer.id;

  return (
    <div className="tix-public-organizer-wrapper">
      <main className="tix-dashboard-page tix-profile-page tix-public-organizer-page">
        <div className="tix-dashboard-container">
          {/* 1. Hero Branding Card — Banner + Squircle Avatar + Org Name & Description */}
          <div className="tix-profile-hero-card">
            <div className="tix-profile-banner-wrap is-readonly">
              {organizer.bannerUrl ? (
                <img
                  src={organizer.bannerUrl}
                  alt={organizer.name}
                  className="tix-profile-banner-img"
                />
              ) : (
                <div className="tix-profile-banner-empty">
                  <span>{t("publicOrganizer.bannerMissing")}</span>
                </div>
              )}

              {!isOwnOrganizer && (
                <div className="tix-public-banner-action">
                  <button
                    type="button"
                    className={`tix-primary-cta-btn tix-public-follow-btn ${
                      isFollowing ? "is-following" : ""
                    }`}
                    onClick={handleFollowClick}
                    disabled={followMutation.isPending}
                  >
                    {followMutation.isPending ? (
                      <LoaderCircle size={14} className="tix-spin" aria-hidden="true" />
                    ) : (
                      <Heart size={14} aria-hidden="true" />
                    )}
                    <span>
                      {isFollowing ? t("publicOrganizer.unfollow") : t("publicOrganizer.follow")}
                    </span>
                  </button>
                </div>
              )}
            </div>

            <div className="tix-profile-identity-body">
              <div className="tix-profile-identity-main">
                <div className="tix-profile-avatar-col">
                  <div className="tix-profile-avatar-box is-readonly">
                    {organizer.logoUrl ? (
                      <img src={organizer.logoUrl} alt={organizer.name} />
                    ) : (
                      <span>{organizerInitials(organizer.name)}</span>
                    )}
                  </div>
                </div>

                <div className="tix-profile-copy">
                  <div className="tix-profile-name-row">
                    <h1 className="tix-profile-org-name">{organizer.name}</h1>
                    {organizer.isVerified && (
                      <span className="tix-profile-verified-pill">
                        <BadgeCheck size={13} aria-hidden="true" />
                        <span>{t("organizerProfile.verifiedBadge", "Hoạt động")}</span>
                      </span>
                    )}
                  </div>
                  <div className="tix-public-description-row">
                    <p className="tix-profile-desc">
                      {organizer.description || t("publicOrganizer.descriptionMissing")}
                    </p>
                    {typeof organizer.followerCount === "number" && Number.isFinite(organizer.followerCount) && (
                      <span className="tix-overview-badge">
                        {t("organizerHub.followersCount", { count: organizer.followerCount })}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* 3. 2-Column Detail Grid — đồng bộ 1.4fr / 1fr với /organizer/profile */}
          <div className="tix-profile-grid">
            <div className="tix-orders-card">
              <div className="tix-card-header">
                <div>
                  <h3 className="tix-card-title">
                    {t("organizerProfile.identityInfo", "Thông tin định danh")}
                  </h3>
                </div>
              </div>

              <div className="tix-profile-info-list">
                <div className="tix-profile-info-row">
                  <span className="tix-profile-info-label">
                    <Building2 size={14} aria-hidden="true" />
                    <span>{t("organizerProfile.title", "Hồ sơ ban tổ chức")}</span>
                  </span>
                  <span className="tix-profile-info-value">{organizer.name}</span>
                </div>

                <div className="tix-profile-info-row">
                  <span className="tix-profile-info-label">
                    <BadgeCheck size={14} aria-hidden="true" />
                    <span>{t("organizerProfile.organizerId", "Mã định danh Ban tổ chức")}</span>
                  </span>
                  <button
                    type="button"
                    className="tix-profile-copy-btn"
                    onClick={handleCopySubject}
                    title={orgSubject}
                  >
                    <span>{orgSubject}</span>
                    {copiedSubject ? <Check size={13} color="#10b981" /> : <Copy size={13} />}
                  </button>
                </div>
              </div>

              {isOwnOrganizer && (
                <div className="tix-profile-quick-actions">
                  <Link
                    className="tix-primary-cta-btn"
                    to="/organizer/events"
                    style={{ textDecoration: "none" }}
                  >
                    <CalendarDays size={14} aria-hidden="true" />
                    <span>{t("organizerProfile.manageEvents", "Quản lý sự kiện")}</span>
                  </Link>
                </div>
              )}
            </div>
          </div>
        </div>
      </main>
      <Footer />
    </div>
  );
}
