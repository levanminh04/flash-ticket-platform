import { useEffect, useMemo, useState } from "react";
import { useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useKeycloak } from "@react-keycloak/web";
import {
  Ban,
  Building2,
  Check,
  ChevronRight,
  CircleCheck,
  Clock,
  ExternalLink,
  FileText,
  Link2,
  LoaderCircle,
  Search,
  ShieldAlert,
  UserRound,
  X,
} from "lucide-react";
import { isAxiosError } from "axios";
import { toast } from "react-toastify";
import Swal from "sweetalert2";
import { useTranslation } from "react-i18next";
import { hasRealmRole } from "../../lib/auth";
import { OrganizerApplication, organizerService } from "../../services/organizerService";
import TixAdminTopNav from "../../components/admin/TixAdminTopNav";
import "../Organizer/organizer-dashboard.css";
import "./admin-organizer-review.css";
import "./admin-tix.css";

type Status = OrganizerApplication["status"];
const statuses: Status[] = ["PENDING", "ACTIVE", "REJECTED"];

const statusIcons = { PENDING: Clock, ACTIVE: CircleCheck, REJECTED: Ban };

function getStatusBadgeClass(status: Status) {
  switch (status) {
    case "ACTIVE":
      return "is-active";
    case "REJECTED":
      return "is-rejected";
    default:
      return "is-pending";
  }
}

function externalLink(url: string | null | undefined) {
  if (!url?.startsWith("https://")) return null;
  return (
    <a href={url} target="_blank" rel="noopener noreferrer">
      <span>{url}</span>
      <ExternalLink size={13} aria-hidden="true" />
    </a>
  );
}

function formatDecisionDate(iso?: string | null): string {
  if (!iso) return "";
  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    const day = String(d.getDate()).padStart(2, "0");
    const month = String(d.getMonth() + 1).padStart(2, "0");
    const year = d.getFullYear();
    return `${day}/${month}/${year}`;
  } catch {
    return iso;
  }
}

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function ApplicationReview({ subject }: { subject: string }) {
  const { t, i18n } = useTranslation();
  const { keycloak } = useKeycloak();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<Status>("PENDING");
  const [selectedId, setSelectedId] = useState<string>();
  const [searchQuery, setSearchQuery] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);

  const vi = i18n.resolvedLanguage?.startsWith("vi");
  const copy = (vietnamese: string, english: string) => (vi ? vietnamese : english);
  const statusLabel = (value: Status) => t("organizer.applicationStatus." + value);
  const queryScope = ["admin-organizer-applications", subject];

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedSearch(searchQuery.trim());
    }, 250);
    return () => window.clearTimeout(timer);
  }, [searchQuery]);

  const applications = useInfiniteQuery({
    queryKey: [...queryScope, status, debouncedSearch],
    queryFn: ({ pageParam, signal }) =>
      organizerService.getAdminApplications(status, pageParam, signal, debouncedSearch),
    initialPageParam: null as string | null,
    getNextPageParam: (page, _, __, cursors) =>
      page.nextCursor && !cursors.includes(page.nextCursor) ? page.nextCursor : undefined,
    retry: false,
  });

  const rows = useMemo(
    () => [
      ...new Map(
        (applications.data?.pages.flatMap((page) => page.items) ?? []).map((item) => [item.id, item])
      ).values(),
    ],
    [applications.data]
  );

  const visibleRows = useMemo(() => {
    const q = searchQuery.trim().toLocaleLowerCase();
    if (!q) return rows;
    return rows.filter(
      (item) =>
        item.organizationName?.toLocaleLowerCase().includes(q) ||
        item.contactEmail?.toLocaleLowerCase().includes(q) ||
        item.taxCode?.toLocaleLowerCase().includes(q)
    );
  }, [rows, searchQuery]);

  const selected = visibleRows.find((item) => item.id === selectedId) ?? visibleRows[0];

  const decision = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) => {
      if (
        !keycloak.authenticated ||
        keycloak.tokenParsed?.sub !== subject ||
        !hasRealmRole(keycloak.tokenParsed, "ADMIN")
      ) {
        throw new Error("Admin session ended");
      }
      return reason === undefined
        ? organizerService.approveApplication(id)
        : organizerService.rejectApplication(id, reason);
    },
    onSuccess: (_, { id }) => {
      queryClient.setQueriesData<NonNullable<typeof applications.data>>(
        { queryKey: [...queryScope, "PENDING"], exact: true },
        (data) =>
          data && {
            ...data,
            pages: data.pages.map((page) => ({
              ...page,
              items: page.items.filter((item) => item.id !== id),
            })),
          }
      );
      toast.success(t("adminOrganizer.updateSuccess"));
      return queryClient.invalidateQueries({ queryKey: queryScope });
    },
    onError: () => queryClient.invalidateQueries({ queryKey: queryScope }),
  });

  const busy = decision.isPending || dialogOpen;

  function errorMessage(error: unknown) {
    const response = isAxiosError(error) ? error.response : undefined;
    if (response?.status === 409) {
      if (response.data?.code === "APPLICATION_ROLE_GRANT_IN_PROGRESS") {
        return copy(
          "Hồ sơ vẫn đang chờ duyệt nhưng quá trình cấp quyền nhà tổ chức đã bắt đầu, không thể từ chối lúc này.",
          "The application is still pending, but organizer role grant is already in progress and cannot be rejected."
        );
      }
      return copy(
        "Hồ sơ đã thay đổi trạng thái hoặc đang trong quá trình cấp quyền. Hãy tải lại danh sách.",
        "The application status changed or role grant is already in progress. Reload the list."
      );
    }
    if (response?.status === 503 && response.data?.code === "ROLE_GRANT_UNAVAILABLE") {
      return copy(
        "Chưa thể cấp quyền nhà tổ chức. Hồ sơ đã được yêu cầu tải lại.",
        "Could not grant the organizer role. The application list was refreshed."
      );
    }
    return t("adminOrganizer.updateFailed");
  }

  async function review(application: OrganizerApplication, reject: boolean) {
    setDialogOpen(true);
    try {
      if (reject) {
        await Swal.fire({
          title: t("adminOrganizer.rejectTitle"),
          html: `<p class="org-reject-context">${escapeHtml(copy("Đang xét duyệt: ", "Reviewing: "))}<strong>${escapeHtml(application.organizationName)}</strong></p>`,
          input: "textarea",
          inputAttributes: {
            maxlength: "2000",
            placeholder: copy("Nhập lý do từ chối hồ sơ...", "Enter rejection reason..."),
            "aria-label": t("adminOrganizer.rejectReason"),
          },
          showCancelButton: true,
          reverseButtons: true,
          confirmButtonText: t("adminOrganizer.rejectConfirm"),
          cancelButtonText: t("adminOrganizer.rejectCancel"),
          buttonsStyling: false,
          showLoaderOnConfirm: true,
          allowOutsideClick: () => !Swal.isLoading(),
          allowEscapeKey: () => !Swal.isLoading(),
          customClass: {
            container: "org-reject-container",
            popup: "org-reject-popup",
            title: "org-reject-title",
            htmlContainer: "org-reject-html",
            input: "org-reject-textarea",
            validationMessage: "org-reject-validation",
            actions: "org-reject-actions",
            confirmButton: "org-reject-confirm-btn",
            cancelButton: "org-reject-cancel-btn",
          },
          didOpen: () => {
            const input = Swal.getInput();
            if (!input) return;
            const count = document.createElement("div");
            count.className = "org-reject-counter";
            count.id = "organizer-rejection-count";
            input.setAttribute("aria-describedby", count.id);
            const formatCount = (n: number) =>
              n.toLocaleString(i18n.language) + " / " + (2000).toLocaleString(i18n.language);
            const update = () => {
              count.textContent = formatCount(input.value.length);
            };
            input.insertAdjacentElement("afterend", count);
            input.addEventListener("input", update);
            update();
            setTimeout(() => input.focus(), 60);
          },
          inputValidator: (value: string) => {
            if (!value.trim()) return t("adminOrganizer.rejectRequired");
            return value.trim().length > 2000 ? t("adminOrganizer.reasonTooLong") : null;
          },
          preConfirm: async (value) => {
            try {
              await decision.mutateAsync({ id: application.id, reason: String(value).trim() });
            } catch (error) {
              Swal.showValidationMessage(errorMessage(error));
              return false;
            }
          },
        });
      } else {
        await Swal.fire({
          title: copy("Duyệt hồ sơ", "Approve application"),
          text: application.organizationName,
          showCancelButton: true,
          confirmButtonText: copy("Duyệt hồ sơ", "Approve application"),
          cancelButtonText: t("adminOrganizer.rejectCancel"),
          confirmButtonColor: "#15171a",
          showLoaderOnConfirm: true,
          allowOutsideClick: () => !Swal.isLoading(),
          allowEscapeKey: () => !Swal.isLoading(),
          preConfirm: async () => {
            try {
              await decision.mutateAsync({ id: application.id });
            } catch (error) {
              Swal.showValidationMessage(errorMessage(error));
              return false;
            }
          },
        });
      }
    } finally {
      setDialogOpen(false);
    }
  }

  const groups = selected && [
    {
      title: copy("Thông tin tổ chức", "Organization information"),
      Icon: Building2,
      fields: [
        [copy("Tên tổ chức", "Organization name"), selected.organizationName],
        [copy("Mô tả ngắn", "Description"), selected.shortDescription],
      ],
    },
    {
      title: copy("Liên hệ & pháp lý", "Contact and legal information"),
      Icon: UserRound,
      fields: [
        [copy("Email liên hệ", "Contact email"), selected.contactEmail],
        [copy("Số điện thoại", "Phone number"), selected.contactPhone],
        [t("adminOrganizer.taxCode"), selected.taxCode],
      ],
    },
    {
      title: copy("Kênh xác thực và địa chỉ", "Official channels and address"),
      Icon: Link2,
      fields: [
        [t("adminOrganizer.headquartersAddress"), selected.headquartersAddress],
        [t("adminOrganizer.socialPage"), externalLink(selected.socialPageUrl)],
        ["Website", externalLink(selected.websiteUrl) ?? <em>{t("profile.unavailable")}</em>],
      ],
    },
    ...(selected.status === "REJECTED"
      ? [
          {
            title: t("adminOrganizer.rejectReason"),
            Icon: Ban,
            fields: [
              [
                copy("Chi tiết lý do", "Reason details"),
                selected.rejectionReason || <em>{t("profile.unavailable")}</em>,
              ],
            ],
          },
        ]
      : []),
  ];

  return (
    <div className="tix-dashboard-page tix-admin-page">
      <div className="tix-dashboard-container">
        <TixAdminTopNav activeTab="organizers" />

        {/* Overview Header */}
        <div className="tix-overview-header">
          <div className="tix-overview-header-left">
            <div className="tix-overview-title-block">
              <h1 className="tix-overview-main-title">
                {t("adminOrganizer.headerTitle", "Duyệt đăng ký Ban tổ chức")}
              </h1>
              <p className="tix-overview-subtitle">
                {t(
                  "adminOrganizer.headerDescription",
                  "Kiểm tra hồ sơ đăng ký và phê duyệt hoặc từ chối quyền Ban tổ chức."
                )}
              </p>
            </div>
          </div>
        </div>

        {/* Sub-tabs & Search */}
        <div className="tix-subtabs-row">
          <div className="tix-subtabs-group" role="group" aria-label={t("adminOrganizer.status")}>
            {statuses.map((value) => {
              const Icon = statusIcons[value];
              return (
                <button
                  type="button"
                  key={value}
                  className={`tix-subtab-btn ${status === value ? "active" : ""}`}
                  disabled={busy}
                  onClick={() => {
                    setStatus(value);
                    setSelectedId(undefined);
                  }}
                >
                  <Icon size={14} aria-hidden="true" />
                  <span>{statusLabel(value)}</span>
                </button>
              );
            })}
          </div>

          <div className="tix-search-input-wrap">
            <Search size={15} aria-hidden="true" />
            <input
              type="search"
              className="tix-search-input"
              placeholder={copy("Tìm theo tên tổ chức, email, MST...", "Search organization, email, tax code...")}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
            {searchQuery && (
              <button
                type="button"
                className="tix-search-clear-btn"
                onClick={() => setSearchQuery("")}
                aria-label={t("organizerHub.clearSearch", "Xóa từ khóa tìm kiếm")}
              >
                <X size={13} />
              </button>
            )}
          </div>
        </div>

        {/* Master-Detail Grid */}
        <div className="tix-admin-split-grid">
          {/* Left: Applications List */}
          <section
            className="tix-orders-card"
            aria-labelledby="organizer-review-list-title"
            aria-busy={applications.isFetching}
          >
            <div className="tix-card-header">
              <div>
                <h2 id="organizer-review-list-title" className="tix-card-title">
                  {copy("Hồ sơ đăng ký", "Applications")}
                </h2>
                <p className="tix-card-subtitle">
                  {applications.hasNextPage
                    ? searchQuery.trim()
                      ? copy(
                          `${visibleRows.length} hồ sơ khớp trong ${rows.length} hồ sơ đã tải (vẫn còn trang sau)`,
                          `${visibleRows.length} matching in ${rows.length} loaded applications (more pages available)`
                        )
                      : copy(
                          `${rows.length} hồ sơ đã tải (vẫn còn trang sau)`,
                          `${rows.length} applications loaded (more pages available)`
                        )
                    : copy(
                        `${visibleRows.length} hồ sơ đang hiển thị`,
                        `${visibleRows.length} applications shown`
                      )}
                </p>
              </div>
            </div>

            {applications.isPending && (
              <div className="tix-admin-state-box" role="status">
                <LoaderCircle size={26} className="tix-spin" aria-hidden="true" />
                <span>{t("adminOrganizer.loading")}</span>
              </div>
            )}

            {applications.isError && (
              <div className="tix-admin-error-box" role="alert">
                <span>{t("adminOrganizer.loadFailed")}</span>
                <button
                  type="button"
                  className="tix-admin-btn tix-admin-btn-secondary"
                  disabled={applications.isFetching}
                  onClick={() =>
                    void (applications.isFetchNextPageError
                      ? applications.fetchNextPage()
                      : applications.refetch())
                  }
                >
                  {t("profile.retry")}
                </button>
              </div>
            )}

            {applications.isSuccess && visibleRows.length === 0 && (
              <div className="tix-admin-state-box">
                <FileText size={28} color="var(--text-muted, #666b74)" aria-hidden="true" />
                <span>
                  {searchQuery.trim() && applications.hasNextPage
                    ? copy(
                        `Chưa thấy hồ sơ khớp trên ${rows.length} hồ sơ đã tải. Vẫn còn trang sau, hãy bấm tải thêm để kiểm tra tiếp.`,
                        `No matches in the ${rows.length} loaded applications. More pages remain—load more to check further.`
                      )
                    : searchQuery.trim()
                      ? copy(
                          "Không tìm thấy hồ sơ nào khớp với từ khóa tìm kiếm.",
                          "No applications match your search query."
                        )
                      : t("adminOrganizer.noRows")}
                </span>
              </div>
            )}

            {visibleRows.length > 0 && (
              <div className="tix-admin-app-list">
                {visibleRows.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    className={`tix-admin-app-item ${selected?.id === item.id ? "is-selected" : ""}`}
                    disabled={busy}
                    aria-pressed={selected?.id === item.id}
                    aria-controls="organizer-review-details"
                    onClick={() => setSelectedId(item.id)}
                  >
                    <div className="tix-admin-app-main">
                      <span className="tix-admin-app-icon" aria-hidden="true">
                        <FileText size={17} />
                      </span>
                      <div className="tix-admin-app-copy">
                        <strong>{item.organizationName?.trim() || t("profile.unavailable")}</strong>
                        <span>{item.contactEmail?.trim() || t("profile.unavailable")}</span>
                      </div>
                    </div>
                    <div className="tix-admin-app-right">
                      <span className={`tix-status-badge ${getStatusBadgeClass(item.status)}`}>
                        {statusLabel(item.status)}
                      </span>
                      <ChevronRight size={16} color="var(--text-muted, #666b74)" aria-hidden="true" />
                    </div>
                  </button>
                ))}
              </div>
            )}

            {applications.hasNextPage && !applications.isFetchNextPageError && (
              <div style={{ marginTop: 14, display: "flex", justifyContent: "center" }}>
                <button
                  type="button"
                  className="tix-admin-btn tix-admin-btn-secondary"
                  disabled={applications.isFetching || busy}
                  onClick={() => void applications.fetchNextPage()}
                >
                  {t(applications.isFetchingNextPage ? "adminOrganizer.loading" : "adminOrganizer.loadMore")}
                </button>
              </div>
            )}
          </section>

          {/* Right: Application Detail */}
          <section
            id="organizer-review-details"
            className="tix-orders-card"
            aria-label={copy("Chi tiết hồ sơ", "Application details")}
          >
            {applications.isPending ? (
              <div className="tix-admin-state-box" role="status">
                <LoaderCircle size={26} className="tix-spin" aria-hidden="true" />
                <span>{copy("Đang tải thông tin chi tiết…", "Loading details…")}</span>
              </div>
            ) : selected && groups ? (
              <>
                <div className="tix-admin-review-groups">
                  {groups.map(({ title, Icon, fields }, index) => (
                    <section className="tix-admin-review-group" key={title}>
                      <span className="tix-admin-review-group-icon" aria-hidden="true">
                        <Icon size={18} />
                      </span>
                      <div>
                        <div className="tix-admin-review-group-header">
                          <h3>{title}</h3>
                          {index === 0 && selected.decidedAt && (
                            <span className="tix-admin-decided-at">
                              <span className="tix-admin-decided-label">
                                {copy("Ngày xử lý:", "Decision date:")}
                              </span>
                              <time dateTime={selected.decidedAt}>
                                {formatDecisionDate(selected.decidedAt)}
                              </time>
                            </span>
                          )}
                        </div>
                        <dl className="tix-admin-review-dl">
                          {fields.map(([label, value]) => (
                            <div key={String(label)}>
                              <dt>{label}</dt>
                              <dd>{value || <em>{t("profile.unavailable")}</em>}</dd>
                            </div>
                          ))}
                        </dl>
                      </div>
                    </section>
                  ))}
                </div>

                {selected.status === "PENDING" && (
                  <div className="tix-admin-actions-bar">
                    <small style={{ color: "var(--text-muted, #666b74)", fontSize: 12 }}>
                      {copy(
                        "Khi từ chối, bạn cần cung cấp lý do.",
                        "A reason is required when rejecting an application."
                      )}
                    </small>
                    <div className="tix-admin-actions-btns">
                      <button
                        type="button"
                        className="tix-admin-btn tix-admin-btn-danger-outline"
                        disabled={busy}
                        onClick={() => void review(selected, true)}
                      >
                        <Ban size={15} aria-hidden="true" />
                        <span>{t("adminOrganizer.reject")}</span>
                      </button>
                      <button
                        type="button"
                        className="tix-admin-btn tix-admin-btn-primary"
                        disabled={busy}
                        onClick={() => void review(selected, false)}
                      >
                        <Check size={15} aria-hidden="true" />
                        <span>{copy("Duyệt hồ sơ", "Approve application")}</span>
                      </button>
                    </div>
                  </div>
                )}
              </>
            ) : (
              <div className="tix-admin-state-box">
                <Building2 size={28} color="var(--text-muted, #666b74)" aria-hidden="true" />
                <span>{copy("Chọn hồ sơ để xem chi tiết", "Select an application to view its details")}</span>
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}

export default function AdminOrganizerReviewPage() {
  const { t } = useTranslation();
  const { keycloak, initialized } = useKeycloak();
  const subject = keycloak.tokenParsed?.sub;

  if (!initialized) {
    return (
      <div className="tix-dashboard-page tix-admin-page">
        <div className="tix-dashboard-container">
          <TixAdminTopNav activeTab="organizers" />
          <div className="tix-orders-card tix-admin-state-box" role="status">
            <LoaderCircle size={26} className="tix-spin" aria-hidden="true" />
            <span>{t("adminOrganizer.loading")}</span>
          </div>
        </div>
      </div>
    );
  }

  if (!keycloak.authenticated || !subject || !hasRealmRole(keycloak.tokenParsed, "ADMIN")) {
    return (
      <div className="tix-dashboard-page tix-admin-page">
        <div className="tix-dashboard-container">
          <TixAdminTopNav activeTab="organizers" />
          <div className="tix-orders-card tix-admin-state-box" role="alert">
            <ShieldAlert size={32} color="#dc2626" aria-hidden="true" />
            <strong style={{ color: "var(--text-strong, #15171a)" }}>
              {t("adminOrganizer.noAccessTitle", "Không có quyền truy cập")}
            </strong>
            <span>{t("adminOrganizer.noAccessDescription")}</span>
            {!keycloak.authenticated && (
              <button
                type="button"
                className="tix-admin-btn tix-admin-btn-primary"
                onClick={() => void keycloak.login()}
              >
                {t("auth.login", "Đăng nhập")}
              </button>
            )}
          </div>
        </div>
      </div>
    );
  }

  return <ApplicationReview subject={subject} />;
}
