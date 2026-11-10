import { useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import OrganizerLayout from "../../components/organizer/OrganizerLayout";
import { useOrganizerGate } from "./useOrganizerGate";
import { organizerDashboardService, organizerEventStatuses } from "../../services/organizerDashboardService";

function Dashboard({ subject, issuer }: { subject: string; issuer?: string }) {
  const { t, i18n } = useTranslation();
  const [selectedId, setSelectedId] = useState("");
  const scope = [issuer, subject];
  const profile = useQuery({
    queryKey: ["organizer-dashboard-profile", ...scope],
    queryFn: ({ signal }) => organizerDashboardService.profile(subject, signal),
    retry: false,
  });
  const events = useInfiniteQuery({
    queryKey: ["organizer-dashboard-events", ...scope],
    initialPageParam: null as string | null,
    queryFn: ({ pageParam, signal }) => organizerDashboardService.events(pageParam, signal),
    getNextPageParam: page => page.nextCursor ?? undefined,
    retry: false,
  });
  const rows = events.data?.pages.flatMap(page => page.items) ?? [];
  const statistics = useQuery({
    queryKey: ["organizer-dashboard-statistics", ...scope, selectedId],
    queryFn: ({ signal }) => organizerDashboardService.statistics(selectedId, signal),
    enabled: Boolean(selectedId),
    retry: false,
  });
  const date = (value: string) => new Date(value).toLocaleString(i18n.resolvedLanguage);
  const amount = (value: string) => BigInt(value).toLocaleString(i18n.resolvedLanguage);
  const metrics = ["purchasedTicketCount", "validGross", "validRefunds", "feeAmount", "netAmount"] as const;

  return (
    <>
      <section className="organizer-panel organizer-dashboard-card">
        <h2>{t("dashboardV1.organization")}</h2>
        {profile.isPending && <p role="status">{t("organizerHub.loading")}</p>}
        {profile.isError && <><p role="alert">{t("dashboardV1.profileFailed")}</p><button className="btn btn-secondary" onClick={() => void profile.refetch()}>{t("profile.retry")}</button></>}
        {profile.data && <><h3>{profile.data.organizationName}</h3><p>{profile.data.shortDescription}</p><p>{t("dashboardV1.followers")}: {profile.data.followerCount}</p></>}
      </section>

      <section className="organizer-panel organizer-dashboard-card">
        <h2>{t("dashboardV1.events")}</h2>
        {events.isPending && <p role="status">{t("organizerHub.loading")}</p>}
        {events.isError && <><p role="alert">{t("organizerHub.loadFailed")}</p><button className="btn btn-secondary" onClick={() => void (events.isFetchNextPageError ? events.fetchNextPage() : events.refetch())}>{t("profile.retry")}</button></>}
        {events.data && <>
          <p>{t(events.hasNextPage ? "dashboardV1.loadedCount" : "dashboardV1.totalCount", { count: rows.length })}</p>
          <ul className="dashboard-status-counts">
            {organizerEventStatuses.map(status => <li key={status}>{t(`dashboardV1.status.${status}`)}: {rows.filter(event => event.status === status).length}</li>)}
          </ul>
          {rows.length === 0 && <p>{t("dashboardV1.empty")}</p>}
          {rows.length > 0 && <div className="dashboard-table-wrap"><table className="dashboard-events-table">
            <thead><tr><th scope="col">{t("dashboardV1.event")}</th><th scope="col">{t("adminOrganizer.status")}</th><th scope="col">{t("dashboardV1.startsAt")}</th><th scope="col">{t("dashboardV1.statistics")}</th></tr></thead>
            <tbody>{rows.map(event => <tr key={event.eventId}>
              <td>{event.title}</td><td>{t(`dashboardV1.status.${event.status}`)}</td><td>{date(event.startsAt)}</td>
              <td><button className="btn btn-secondary" onClick={() => setSelectedId(event.eventId)} aria-pressed={selectedId === event.eventId}>{t("dashboardV1.viewStatistics")}</button></td>
            </tr>)}</tbody>
          </table></div>}
          {events.hasNextPage && !events.isFetchNextPageError && <button className="btn btn-secondary" disabled={events.isFetching} onClick={() => void events.fetchNextPage()}>{t("adminOrganizer.loadMore")}</button>}
        </>}
      </section>

      {selectedId && <section className="organizer-panel organizer-dashboard-card" aria-live="polite">
        <h2>{t("dashboardV1.statistics")} — {rows.find(event => event.eventId === selectedId)?.title}</h2>
        {statistics.isPending && <p role="status">{t("organizerHub.loading")}</p>}
        {statistics.isError && <p role="alert">{t("dashboardV1.statisticsFailed")}</p>}
        <button className="btn btn-secondary" disabled={statistics.isFetching} onClick={() => void statistics.refetch()}>{t("paymentV1.refresh")}</button>
        {statistics.data && <>
          <dl className="dashboard-metrics">{metrics.map(key => <div key={key}><dt>{t(`dashboardV1.${key}`)}</dt><dd>{amount(statistics.data[key])}{key !== "purchasedTicketCount" ? ` ${statistics.data.currency}` : ""}</dd></div>)}</dl>
          <p>{t("dashboardV1.salesAsOf")}: {date(statistics.data.salesAsOf)}</p>
          <p>{t("dashboardV1.financeAsOf")}: {date(statistics.data.financeAsOf)}</p>
        </>}
      </section>}
    </>
  );
}

export default function OrganizerHubPage() {
  const { t } = useTranslation();
  const { keycloak } = useKeycloak();
  const { ready } = useOrganizerGate();
  const subject = keycloak.tokenParsed?.sub;
  return (
    <OrganizerLayout title={t("organizer.overview")} description={t("dashboardV1.description")} className="organizer-hub-page" hideTopBar>
      {ready && subject && <Dashboard key={`${keycloak.tokenParsed?.iss}:${subject}`} subject={subject} issuer={keycloak.tokenParsed?.iss} />}
    </OrganizerLayout>
  );
}
