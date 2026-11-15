import { isAxiosError } from "axios";
import { useMemo, useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import {
  BadgeCheck,
  Building2,
  Calendar,
  Camera,
  Check,
  Copy,
  ExternalLink,
  FileText,
  Globe,
  LoaderCircle,
  Mail,
  MapPin,
  Phone,
  PlusCircle,
  RefreshCw,
  Share2,
  Users,
} from "lucide-react";
import { toast } from "react-toastify";
import OrganizerLayout from "../../components/organizer/OrganizerLayout";
import TixOrganizerTopNav from "../../components/organizer/TixOrganizerTopNav";
import {
  organizerDashboardService,
  OrganizerSummary,
} from "../../services/organizerDashboardService";
import { organizerService } from "../../services/organizerService";
import { useOrganizerGate } from "./useOrganizerGate";
import "./organizer-dashboard.css";
import "./organizer-profile.css";

const salesHistoryStart = "1970-01-01";
const brandingImageTypes = "image/png,image/jpeg,image/webp";
const brandingDimensions = {
  logo: { ratio: [1, 1] },
  banner: { ratio: [16, 9] },
} as const;

function ProfileWorkspace({ subject, issuer }: { subject: string; issuer?: string }) {
  const { t, i18n } = useTranslation();
  const client = useQueryClient();
  const locale = i18n.resolvedLanguage === "en" ? "en-US" : "vi-VN";
  const scope = [issuer, subject];
  const profileKey = ["organizer-dashboard-profile", ...scope];

  const [copiedSubject, setCopiedSubject] = useState(false);

  // 1. Hồ sơ công khai của Ban tổ chức (GET /api/v1/organizers/{subject})
  const profileQuery = useQuery({
    queryKey: profileKey,
    queryFn: ({ signal }) => organizerDashboardService.profile(subject, signal),
    retry: false,
  });

  // 2. Hồ sơ đăng ký Ban tổ chức để lấy thông tin liên hệ & pháp lý thật (GET /api/v1/organizer-applications)
  const applicationsQuery = useQuery({
    queryKey: ["organizer-my-applications", ...scope],
    queryFn: async ({ signal }) => {
      const seen = new Set<string>();
      let cursor: string | undefined;
      do {
        const page = await organizerService.getMyApplications(signal, cursor);
        const active = page.items.find(item => item.status === "ACTIVE");
        if (active) return active;
        cursor = page.nextCursor ?? undefined;
        if (cursor) {
          if (seen.has(cursor)) throw new Error("Repeated organizer application cursor");
          seen.add(cursor);
        }
      } while (cursor);
      return null;
    },
    retry: false,
  });

  // 3. Danh sách sự kiện thật của Ban tổ chức
  const eventsQuery = useQuery({
    queryKey: ["organizer-events-list-complete", ...scope],
    queryFn: async ({ signal }) => {
      const events = new Map<string, OrganizerSummary>();
      const seen = new Set<string>();
      let cursor: string | null = null;
      do {
        const page = await organizerDashboardService.events(cursor, signal);
        page.items.forEach((event) => events.set(event.eventId, event));
        cursor = page.nextCursor;
        if (cursor) {
          if (seen.has(cursor)) throw new Error("Repeated organizer event cursor");
          seen.add(cursor);
        }
      } while (cursor);
      return [...events.values()];
    },
    retry: false,
  });

  const salesSummaryQuery = useQuery({
    queryKey: ["organizer-profile-sales-summary", ...scope, salesHistoryStart],
    queryFn: ({ signal }) => organizerDashboardService.salesSummary(subject, salesHistoryStart, undefined, signal),
    retry: false,
  });

  const upload = useMutation({
    mutationFn: async ({ kind, file }: { kind: "logo" | "banner"; file: File }) => {
      const image = new Image();
      const url = URL.createObjectURL(file);
      try {
        image.src = url;
        await image.decode();
      } catch {
        throw new Error("INVALID_IMAGE");
      } finally {
        URL.revokeObjectURL(url);
      }
      const [width, height] = brandingDimensions[kind].ratio;
      if (image.naturalWidth * height !== image.naturalHeight * width) {
        throw new Error("INVALID_BRANDING_DIMENSIONS");
      }
      return organizerDashboardService.uploadBranding(kind, file);
    },
    onSuccess: async (updated, { kind }) => {
      await client.cancelQueries({ queryKey: profileKey, exact: true });
      client.setQueryData(profileKey, updated);
      toast.success(
        t(kind === "logo" ? "organizerProfile.logoUpdated" : "organizerProfile.bannerUpdated")
      );
    },
    onError: (error, { kind }) => {
      const code = isAxiosError<{ code?: string }>(error) ? error.response?.data?.code : error.message;
      if (code === "INVALID_BRANDING_DIMENSIONS") {
        const [width, height] = brandingDimensions[kind].ratio;
        toast.error(t("organizerProfile.brandingDimensions", { ratio: `${width}:${height}` }));
        return;
      }
      if (code === "INVALID_IMAGE") {
        toast.error(t("organizerProfile.invalidImage"));
        return;
      }
      toast.error(
        t(kind === "logo" ? "organizerProfile.logoFailed" : "organizerProfile.bannerFailed")
      );
    },
  });

  function selectImage(kind: "logo" | "banner", file?: File) {
    if (!file || upload.isPending) return;
    if (!brandingImageTypes.split(",").includes(file.type) || file.size === 0 || file.size > 5 * 1024 * 1024) {
      toast.error(t("organizerProfile.invalidImage"));
      return;
    }
    upload.mutate({ kind, file });
  }

  const data = profileQuery.data;

  const activeApplication = applicationsQuery.data;
  const salesSummary = salesSummaryQuery.data;

  const eventsList = useMemo(
    () => (eventsQuery.isSuccess ? eventsQuery.data : []),
    [eventsQuery.isSuccess, eventsQuery.data]
  );

  const publishedCount = useMemo(
    () => eventsList.filter((e) => e.status === "PUBLISHED").length,
    [eventsList]
  );

  const formatMoney = (value: string | number, currency = "VND") => {
    try {
      const num = typeof value === "string" ? BigInt(value) : BigInt(Math.round(value));
      return `${num.toLocaleString(locale)} ${currency === "VND" ? "đ" : currency}`;
    } catch {
      return `${value} đ`;
    }
  };

  const orgInitials = useMemo(() => {
    const name = data?.organizationName || "TC";
    return (
      name
        .trim()
        .split(/\s+/)
        .slice(0, 2)
        .map((w) => Array.from(w)[0])
        .join("")
        .toLocaleUpperCase() || "TC"
    );
  }, [data?.organizationName]);

  const isRefreshing =
    profileQuery.isFetching ||
    applicationsQuery.isFetching ||
    eventsQuery.isFetching ||
    salesSummaryQuery.isFetching;

  const handleRefresh = () => {
    void Promise.all([
      profileQuery.refetch(),
      applicationsQuery.refetch(),
      eventsQuery.refetch(),
      salesSummaryQuery.refetch(),
    ]);
  };

  const handleCopySubject = () => {
    if (!data?.organizerSubject) return;
    void navigator.clipboard.writeText(data.organizerSubject);
    setCopiedSubject(true);
    toast.success(t("organizerProfile.copiedSubject", "Đã sao chép mã định danh!"));
    setTimeout(() => setCopiedSubject(false), 1500);
  };

  const uploadingLogo = upload.isPending && upload.variables?.kind === "logo";
  const uploadingBanner = upload.isPending && upload.variables?.kind === "banner";

  return (
    <div className="tix-dashboard-page tix-profile-page">
      <div className="tix-dashboard-container">
        {/* 1. Topbar đồng bộ với /organizer, /organizer/events, /organizer/events/new */}
        <TixOrganizerTopNav activeTab="profile" />

        {/* 2. Overview Header */}
        <div className="tix-overview-header">
          <div className="tix-overview-header-left">
            <div className="tix-overview-title-block">
              <h1 className="tix-overview-main-title">
                {t("organizerProfile.title", "Hồ sơ ban tổ chức")}
              </h1>
              <p className="tix-overview-subtitle">
                {t("organizerProfile.description", "Thông tin chi tiết về ban tổ chức.")}
              </p>
            </div>

            <div className="tix-overview-badges-row">
              {data && (
                <span className="tix-overview-badge">
                  <Users size={13} aria-hidden="true" />
                  <span>
                    {t("organizerHub.followersCount", {
                      count: data.followerCount,
                      defaultValue: `${data.followerCount.toLocaleString(locale)} người theo dõi`,
                    })}
                  </span>
                </span>
              )}
              {eventsQuery.isSuccess && (
                <span className="tix-overview-badge">
                  <Calendar size={13} aria-hidden="true" />
                  <span>
                    {t("organizerHub.eventsCount", {
                      count: eventsList.length,
                      defaultValue: `${eventsList.length} sự kiện`,
                    })}
                  </span>
                </span>
              )}
              {activeApplication?.status === "ACTIVE" && (
                <span className="tix-overview-badge">
                  <BadgeCheck size={13} aria-hidden="true" />
                  <span>{t("organizerProfile.verifiedBadge", "Hoạt động")}</span>
                </span>
              )}
            </div>
          </div>

          <div className="tix-overview-header-right">
            <button
              type="button"
              className="tix-filter-select-btn"
              disabled={isRefreshing || upload.isPending}
              onClick={handleRefresh}
            >
              <RefreshCw size={14} className={isRefreshing ? "tix-spin" : ""} aria-hidden="true" />
              <span>{t("paymentV1.refresh", "Làm mới")}</span>
            </button>

            <Link
              className="tix-primary-cta-btn"
              to={`/organizers/${encodeURIComponent(subject)}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              <ExternalLink size={14} aria-hidden="true" />
              <span>{t("organizerProfile.viewPublicProfile", "Xem trang công khai")}</span>
            </Link>
          </div>
        </div>

        {/* Loading / Error states */}
        {profileQuery.isPending && (
          <div className="tix-orders-card" role="status" style={{ textAlign: "center", padding: "48px 16px" }}>
            {t("organizerProfile.loading", "Đang tải hồ sơ ban tổ chức...")}
          </div>
        )}

        {profileQuery.isError && (
          <div className="tix-profile-error-card" role="alert">
            <span>{t("organizerProfile.loadFailed", "Không thể tải hồ sơ ban tổ chức.")}</span>
            <button
              type="button"
              className="tix-filter-select-btn"
              disabled={profileQuery.isFetching}
              onClick={() => void profileQuery.refetch()}
            >
              {t("profile.retry", "Thử lại")}
            </button>
          </div>
        )}

        {/* 3. Main Content */}
        {data && (
          <>
            {/* Hero Branding Card: Banner + Logo + Organization Name & Short Desc */}
            <div className="tix-profile-hero-card">
              <label
                className={`tix-profile-banner-wrap ${uploadingBanner ? "is-uploading" : ""} ${
                  upload.isPending ? "is-disabled" : ""
                }`}
                title={t("organizerProfile.changeBanner", "Đổi banner ban tổ chức")}
                aria-label={t("organizerProfile.changeBanner", "Đổi banner ban tổ chức")}
              >
                {data.bannerUrl && (
                  <img
                    src={data.bannerUrl}
                    alt={data.organizationName}
                    className="tix-profile-banner-img"
                  />
                )}

                <span className="tix-profile-media-overlay" aria-hidden="true">
                  {uploadingBanner ? (
                    <LoaderCircle size={26} className="tix-spin tix-profile-camera-icon" />
                  ) : (
                    <Camera size={26} className="tix-profile-camera-icon" />
                  )}
                </span>

                <input
                  type="file"
                  accept={brandingImageTypes}
                  hidden
                  disabled={upload.isPending || profileQuery.isError}
                  onChange={(event) => {
                    const file = event.currentTarget.files?.[0];
                    event.currentTarget.value = "";
                    selectImage("banner", file);
                  }}
                />
              </label>

              <div className="tix-profile-identity-body">
                <div className="tix-profile-identity-main">
                  <div className="tix-profile-avatar-col">
                    <label
                      className={`tix-profile-avatar-box ${uploadingLogo ? "is-uploading" : ""} ${
                        upload.isPending ? "is-disabled" : ""
                      }`}
                      title={t("organizerProfile.changeLogo", "Đổi logo ban tổ chức")}
                      aria-label={t("organizerProfile.changeLogo", "Đổi logo ban tổ chức")}
                    >
                      {data.logoUrl ? (
                        <img src={data.logoUrl} alt={data.organizationName} />
                      ) : (
                        <span>{orgInitials}</span>
                      )}

                      <span className="tix-profile-media-overlay" aria-hidden="true">
                        {uploadingLogo ? (
                          <LoaderCircle size={22} className="tix-spin tix-profile-camera-icon" />
                        ) : (
                          <Camera size={22} className="tix-profile-camera-icon" />
                        )}
                      </span>

                      <input
                        type="file"
                        accept={brandingImageTypes}
                        hidden
                        disabled={upload.isPending || profileQuery.isError}
                        onChange={(event) => {
                          const file = event.currentTarget.files?.[0];
                          event.currentTarget.value = "";
                          selectImage("logo", file);
                        }}
                      />
                    </label>
                  </div>

                  <div className="tix-profile-copy">
                    <div className="tix-profile-name-row">
                      <h2 className="tix-profile-org-name">{data.organizationName}</h2>
                      {activeApplication?.status === "ACTIVE" && (
                        <span className="tix-profile-verified-pill">
                          <BadgeCheck size={13} aria-hidden="true" />
                          <span>{t("organizerProfile.verifiedBadge", "Hoạt động")}</span>
                        </span>
                      )}
                    </div>
                    <p className="tix-profile-desc">
                      {data.shortDescription ||
                        t("organizerProfile.descriptionMissing", "Organizer chưa cập nhật mô tả.")}
                    </p>
                  </div>
                </div>
              </div>
            </div>

            {/* 4. 2-Column Detail Grid */}
            <div className="tix-profile-grid">
              {/* Left: Identity & Contact Info from real OrganizerApplication + Profile API */}
              <div className="tix-orders-card">
                <div className="tix-card-header">
                  <div>
                    <h3 className="tix-card-title">
                      {t("organizerProfile.identityInfo", "Thông tin định danh")}
                    </h3>
                    <p className="tix-card-subtitle">
                      {t("organizerProfile.description", "Thông tin chi tiết về ban tổ chức.")}
                    </p>
                  </div>
                </div>

                <div className="tix-profile-info-list">
                  <div className="tix-profile-info-row">
                    <span className="tix-profile-info-label">
                      <Building2 size={14} aria-hidden="true" />
                      <span>{t("organizerProfile.title", "Hồ sơ ban tổ chức")}</span>
                    </span>
                    <span className="tix-profile-info-value">{data.organizationName}</span>
                  </div>

                  {applicationsQuery.isPending && <p role="status">{t("organizerProfile.applicationLoading")}</p>}
                  {applicationsQuery.isError && <p role="alert">{t("organizerProfile.applicationLoadFailed")}</p>}
                  {applicationsQuery.isSuccess && !activeApplication && <p>{t("organizerProfile.noActiveApplication")}</p>}
                  {activeApplication && <>
                  <div className="tix-profile-info-row">
                    <span className="tix-profile-info-label">
                      <Mail size={14} aria-hidden="true" />
                      <span>{t("organizerProfile.contactEmail", "Email liên hệ")}</span>
                    </span>
                    <span
                      className={`tix-profile-info-value ${
                        !activeApplication?.contactEmail ? "is-muted" : ""
                      }`}
                    >
                      {activeApplication?.contactEmail ||
                        t("organizerProfile.emailMissing", "Chưa cập nhật email")}
                    </span>
                  </div>

                  <div className="tix-profile-info-row">
                    <span className="tix-profile-info-label">
                      <Phone size={14} aria-hidden="true" />
                      <span>{t("organizerProfile.contactPhone", "Số điện thoại")}</span>
                    </span>
                    <span
                      className={`tix-profile-info-value ${
                        !activeApplication?.contactPhone ? "is-muted" : ""
                      }`}
                    >
                      {activeApplication?.contactPhone ||
                        t("organizerProfile.phoneMissing", "Chưa cập nhật số điện thoại")}
                    </span>
                  </div>

                  <div className="tix-profile-info-row">
                    <span className="tix-profile-info-label">
                      <Globe size={14} aria-hidden="true" />
                      <span>{t("organizerProfile.website", "Website")}</span>
                    </span>
                    {activeApplication?.websiteUrl ? (
                      <a
                        href={activeApplication.websiteUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="tix-profile-info-value tix-profile-info-link"
                      >
                        {activeApplication.websiteUrl}
                      </a>
                    ) : (
                      <span className="tix-profile-info-value is-muted">
                        {t("organizerProfile.websiteMissing", "Chưa cập nhật website")}
                      </span>
                    )}
                  </div>

                  <div className="tix-profile-info-row">
                    <span className="tix-profile-info-label">
                      <Share2 size={14} aria-hidden="true" />
                      <span>{t("organizerProfile.socialPage", "Trang mạng xã hội")}</span>
                    </span>
                    {activeApplication?.socialPageUrl ? (
                      <a
                        href={activeApplication.socialPageUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="tix-profile-info-value tix-profile-info-link"
                      >
                        {activeApplication.socialPageUrl}
                      </a>
                    ) : (
                      <span className="tix-profile-info-value is-muted">
                        {t("organizerProfile.notUpdated", "Chưa cập nhật")}
                      </span>
                    )}
                  </div>

                  <div className="tix-profile-info-row">
                    <span className="tix-profile-info-label">
                      <MapPin size={14} aria-hidden="true" />
                      <span>{t("organizerProfile.headquarters", "Địa chỉ trụ sở")}</span>
                    </span>
                    <span
                      className={`tix-profile-info-value ${
                        !activeApplication?.headquartersAddress ? "is-muted" : ""
                      }`}
                    >
                      {activeApplication?.headquartersAddress ||
                        t("organizerProfile.notUpdated", "Chưa cập nhật")}
                    </span>
                  </div>

                  <div className="tix-profile-info-row">
                    <span className="tix-profile-info-label">
                      <FileText size={14} aria-hidden="true" />
                      <span>{t("organizerProfile.taxCode", "Mã số thuế")}</span>
                    </span>
                    <span
                      className={`tix-profile-info-value ${
                        !activeApplication?.taxCode ? "is-muted" : ""
                      }`}
                    >
                      {activeApplication?.taxCode ||
                        t("organizerProfile.notUpdated", "Chưa cập nhật")}
                    </span>
                  </div>

                  </>}
                  <div className="tix-profile-info-row">
                    <span className="tix-profile-info-label">
                      <BadgeCheck size={14} aria-hidden="true" />
                      <span>{t("organizerProfile.organizerId", "Mã định danh Ban tổ chức")}</span>
                    </span>
                    <button
                      type="button"
                      className="tix-profile-copy-btn"
                      onClick={handleCopySubject}
                      title={data.organizerSubject}
                    >
                      <span>{data.organizerSubject}</span>
                      {copiedSubject ? <Check size={13} color="#10b981" /> : <Copy size={13} />}
                    </button>
                  </div>
                </div>
              </div>

              {/* Right: Real Operational Stats */}
              <div className="tix-orders-card">
                <div className="tix-card-header">
                  <div>
                    <h3 className="tix-card-title">
                      {t("organizerProfile.currentStats", "Chỉ số hiện có")}
                    </h3>
                    <p className="tix-card-subtitle">
                      {t("organizerProfile.salesAllTime")}
                    </p>
                  </div>
                </div>

                {salesSummaryQuery.isError && <p role="alert">{t("organizerProfile.salesLoadFailed")}</p>}
                <div className="tix-profile-stats-grid">
                  <div className="tix-profile-stat-tile">
                    <span className="tix-profile-stat-label">
                      {t("dashboardV1.followers", "Người theo dõi")}
                    </span>
                    <span className="tix-profile-stat-val">
                      {data.followerCount.toLocaleString(locale)}
                    </span>
                  </div>

                  <div className="tix-profile-stat-tile">
                    <span className="tix-profile-stat-label">
                      {t("organizerProfile.totalEvents", "Tổng sự kiện")}
                    </span>
                    <span className="tix-profile-stat-val">
                      {eventsQuery.isSuccess ? eventsList.length.toLocaleString(locale) : "—"}
                    </span>
                    {eventsQuery.isSuccess && (
                      <span className="tix-profile-stat-sub">
                        {t("organizerProfile.publishedCountNote", {
                          count: publishedCount,
                          defaultValue: `${publishedCount} đã công bố`,
                        })}
                      </span>
                    )}
                  </div>

                  <div className="tix-profile-stat-tile">
                    <span className="tix-profile-stat-label">
                      {t("organizerProfile.totalTicketsSold", "Tổng vé đã bán")}
                    </span>
                    <span className="tix-profile-stat-val">
                      {salesSummary ? salesSummary.soldTicketCount.toLocaleString(locale) : "—"}
                    </span>
                  </div>

                  <div className="tix-profile-stat-tile">
                    <span className="tix-profile-stat-label">
                      {t("organizerProfile.totalRevenue", "Tổng doanh thu")}
                    </span>
                    <span className="tix-profile-stat-val">
                      {salesSummary ? formatMoney(salesSummary.soldRevenue, salesSummary.currency) : "—"}
                    </span>
                  </div>
                </div>

                <div className="tix-profile-quick-actions">
                  <Link className="tix-filter-select-btn" to="/organizer/events">
                    <Calendar size={14} aria-hidden="true" />
                    <span>{t("organizerProfile.manageEvents", "Quản lý sự kiện")}</span>
                  </Link>
                  <Link className="tix-primary-cta-btn" to="/organizer/events/new">
                    <PlusCircle size={14} aria-hidden="true" />
                    <span>{t("organizerHub.createNewEvent", "Tạo sự kiện mới")}</span>
                  </Link>
                </div>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

export default function OrganizerProfilePage() {
  const { keycloak } = useKeycloak();
  const { ready } = useOrganizerGate();
  const subject = keycloak.tokenParsed?.sub;
  const issuer = keycloak.tokenParsed?.iss;

  return (
    <OrganizerLayout
      hideSidebar
      hideHeader
      requireOrganizer
      className="organizer-hub-page"
    >
      {ready && subject && (
        <ProfileWorkspace key={`${issuer}:${subject}`} subject={subject} issuer={issuer} />
      )}
    </OrganizerLayout>
  );
}
