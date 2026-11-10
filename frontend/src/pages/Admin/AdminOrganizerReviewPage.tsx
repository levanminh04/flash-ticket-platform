import { useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useKeycloak } from "@react-keycloak/web";
import { useState } from "react";
import { LoaderCircle, ShieldAlert } from "lucide-react";
import { toast } from "react-toastify";
import Swal from "sweetalert2";
import { useTranslation } from "react-i18next";
import { hasRealmRole } from "../../lib/auth";
import { OrganizerApplication, organizerService } from "../../services/organizerService";

type Status = OrganizerApplication["status"];
const statuses: Status[] = ["PENDING", "ACTIVE", "REJECTED"];

function ApplicationReview({ subject }: { subject: string }) {
  const { t } = useTranslation();
  const { keycloak } = useKeycloak();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<Status>("PENDING");
  const queryScope = ["admin-organizer-applications", subject];
  const applications = useInfiniteQuery({
    queryKey: [...queryScope, status],
    queryFn: ({ pageParam, signal }) => organizerService.getAdminApplications(status, pageParam, signal),
    initialPageParam: null as string | null,
    getNextPageParam: page => page.nextCursor ?? undefined,
    retry: false,
  });
  const rows = applications.data?.pages.flatMap(page => page.items) ?? [];

  const decision = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) =>
      reason === undefined ? organizerService.approveApplication(id) : organizerService.rejectApplication(id, reason),
    onSuccess: () => {
      toast.success(t("adminOrganizer.updateSuccess"));
      return queryClient.invalidateQueries({ queryKey: queryScope });
    },
    onError: () => {
      toast.error(t("adminOrganizer.updateFailed"));
      void queryClient.invalidateQueries({ queryKey: queryScope });
    },
  });

  async function reject(application: OrganizerApplication) {
    const result = await Swal.fire({
      title: t("adminOrganizer.rejectTitle"),
      input: "textarea",
      inputLabel: t("adminOrganizer.rejectReason"),
      inputPlaceholder: t("adminOrganizer.rejectPlaceholder"),
      inputAttributes: { maxlength: "2000" },
      showCancelButton: true,
      confirmButtonText: t("adminOrganizer.rejectConfirm"),
      cancelButtonText: t("adminOrganizer.rejectCancel"),
      inputValidator: value => {
        const reason = value.trim();
        if (!reason) return t("adminOrganizer.rejectRequired");
        return reason.length > 2000 ? t("adminOrganizer.reasonTooLong") : null;
      },
    });
    if (result.isConfirmed && result.value && keycloak.authenticated &&
        keycloak.tokenParsed?.sub === subject && hasRealmRole(keycloak.tokenParsed, "ADMIN")) {
      decision.mutate({ id: application.id, reason: result.value.trim() });
    }
  }

  return (
    <main className="admin-organizer-page">
      <section className="container admin-organizer-wrap">
        <div className="admin-organizer-header">
          <div>
            <h1>{t("adminOrganizer.headerTitle")}</h1>
            <p>{t("adminOrganizer.headerDescription")}</p>
          </div>
          <label>
            <span>{t("adminOrganizer.status")}</span>
            <select value={status} onChange={event => setStatus(event.target.value as Status)}>
              {statuses.map(value => (
                <option key={value} value={value}>{t(`organizer.applicationStatus.${value}`)}</option>
              ))}
            </select>
          </label>
        </div>

        {applications.isPending && (
          <div className="admin-organizer-loading" role="status">
            <LoaderCircle size={20} className="spin" aria-hidden="true" />
            {t("adminOrganizer.loading")}
          </div>
        )}
        {applications.isError && (
          <div className="admin-organizer-state">
            <p role="alert">{t("adminOrganizer.loadFailed")}</p>
            <button className="btn btn-secondary" type="button" disabled={applications.isFetching}
              onClick={() => void (applications.isFetchNextPageError ? applications.fetchNextPage() : applications.refetch())}>
              {t("profile.retry")}
            </button>
          </div>
        )}
        {applications.data && (
          <div className="admin-organizer-table-wrap">
            <table className="admin-organizer-table">
              <thead>
                <tr>
                  <th scope="col">{t("adminOrganizer.tableName")}</th>
                  <th scope="col">{t("organizer.shortDescription")}</th>
                  <th scope="col">{t("adminOrganizer.status")}</th>
                  <th scope="col">{t("adminOrganizer.tableActions")}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(application => (
                  <tr key={application.id}>
                    <td><strong>{application.organizationName}</strong></td>
                    <td>{application.shortDescription}</td>
                    <td>
                      {t(`organizer.applicationStatus.${application.status}`)}
                      {application.rejectionReason && (
                        <p>{t("adminOrganizer.rejectReason")}: {application.rejectionReason}</p>
                      )}
                    </td>
                    <td>
                      {application.status === "PENDING" && (
                        <div className="admin-organizer-actions">
                          <button className="btn btn-primary" type="button" disabled={decision.isPending}
                            onClick={() => decision.mutate({ id: application.id })}>
                            {t("adminOrganizer.approve")}
                          </button>
                          <button className="btn btn-secondary" type="button" disabled={decision.isPending}
                            onClick={() => void reject(application)}>
                            {t("adminOrganizer.reject")}
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
                {rows.length === 0 && <tr><td colSpan={4}>{t("adminOrganizer.noRows")}</td></tr>}
              </tbody>
            </table>
            {applications.hasNextPage && !applications.isFetchNextPageError && (
              <button className="btn btn-secondary" type="button" disabled={applications.isFetching}
                onClick={() => void applications.fetchNextPage()}>
                {t(applications.isFetchingNextPage ? "adminOrganizer.loading" : "adminOrganizer.loadMore")}
              </button>
            )}
          </div>
        )}
      </section>
    </main>
  );
}

export default function AdminOrganizerReviewPage() {
  const { t } = useTranslation();
  const { keycloak, initialized } = useKeycloak();
  const subject = keycloak.tokenParsed?.sub;

  if (initialized && keycloak.authenticated && subject && hasRealmRole(keycloak.tokenParsed, "ADMIN")) {
    return <ApplicationReview key={subject} subject={subject} />;
  }

  return (
    <main className="admin-organizer-page">
      <section className="admin-organizer-state">
        {!initialized ? <p role="status">{t("adminOrganizer.loading")}</p> : (
          <>
            <ShieldAlert size={42} aria-hidden="true" />
            <h1>{t("adminOrganizer.noAccessTitle")}</h1>
            <p>{t("adminOrganizer.noAccessDescription")}</p>
          </>
        )}
      </section>
    </main>
  );
}
