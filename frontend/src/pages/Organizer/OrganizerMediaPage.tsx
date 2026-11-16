import { useState } from "react";
import { useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router-dom";
import OrganizerLayout from "../../components/organizer/OrganizerLayout";
import axiosClient from "../../lib/axiosClient";
import { organizerDashboardService } from "../../services/organizerDashboardService";
import OrganizerDraftMedia, { EventWithImages } from "./OrganizerDraftMedia";
import { useOrganizerGate } from "./useOrganizerGate";
import "./organizer-event-create.css";

type OwnerEvent = EventWithImages & {
  seatMap: { backgroundImageReference: string | null } | null;
};

function EventImages({ eventId, scope }: { eventId: string; scope: string[] }) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const queryKey = ["organizer-event-media", ...scope, eventId];
  const query = useQuery({
    queryKey,
    queryFn: async ({ signal }) => (await axiosClient.get<OwnerEvent>(
      "/api/v1/organizer/events/" + encodeURIComponent(eventId), { signal },
    )).data,
    retry: false,
    refetchOnWindowFocus: false,
  });

  if (query.isPending) return <p role="status">{t("organizerMedia.loadingImages")}</p>;
  if (query.isError) return <>
    <p role="alert">{t("organizerMedia.loadEventOrImagesFailed")}</p>
    <button type="button" className="btn btn-secondary" onClick={() => void query.refetch()}>{t("profile.retry")}</button>
  </>;

  const event = query.data;
  const editable = event.status === "DRAFT";
  return <>
    <Link to="/organizer/media">{t("organizerMedia.selectEvent")}</Link>
    <h2>{event.title}</h2>
    {!editable && <p>{t("organizerMedia.draftImagesOnly")}</p>}
    <OrganizerDraftMedia
      event={event}
      readOnly={!editable}
      disabled={query.isFetching}
      onSaved={saved => client.setQueryData(queryKey, saved)}
      onRefresh={() => void query.refetch()}
    />
    {event.mode === "SEAT_MAP" && <section>
      <h3>{t("organizerMedia.seatMapBackground")}</h3>
      {event.seatMap?.backgroundImageReference ? (
        <p>{t("organizerMedia.seatMapBackgroundSaved")}</p>
      ) : <p>{t("organizerMedia.seatMapBackgroundMissing")}</p>}
      {editable && <Link to={"/organizer/events/" + eventId + "/configure-seating"}>{t("organizerMedia.configureSeatMap")}</Link>}
    </section>}
  </>;
}

function MediaEvents({ scope }: { scope: string[] }) {
  const { t } = useTranslation();
  const [search, setSearch] = useState("");
  const events = useInfiniteQuery({
    queryKey: ["organizer-media-events", ...scope],
    initialPageParam: null as string | null,
    queryFn: ({ pageParam, signal }) => organizerDashboardService.events(pageParam, signal),
    getNextPageParam: (page, _, __, cursors) =>
      page.nextCursor && !cursors.includes(page.nextCursor) ? page.nextCursor : undefined,
    retry: false,
  });
  const rows = events.data?.pages.flatMap(page => page.items) ?? [];
  const visible = rows.filter(event => event.title.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()));
  return <>
    <h2>{t("organizerMedia.selectEvent")}</h2>
    {rows.length > 0 && <input type="search" className="organizer-input" value={search}
      onChange={event => setSearch(event.target.value)} aria-label={t("organizerMedia.searchPlaceholder")}
      placeholder={t("organizerMedia.searchPlaceholder")} /> }
    {events.isPending && <p role="status">{t("organizerMedia.loadingEvents")}</p>}
    {events.isError && <>
      <p role="alert">{t("organizerMedia.loadEventsFailed")}</p>
      <button type="button" className="btn btn-secondary" onClick={() => void (
        events.isFetchNextPageError ? events.fetchNextPage() : events.refetch()
      )}>{t("profile.retry")}</button>
    </>}
    {events.isSuccess && rows.length === 0 && <p>{t("eventsV1.empty")}</p>}
    {rows.length > 0 && visible.length === 0 && <p>{t("organizerMedia.noMatchingLoadedEvents")}</p>}
    {visible.length > 0 && <ul>{visible.map(event => <li key={event.eventId}>
      <Link to={"/organizer/events/" + event.eventId + "/media"}>{event.title}</Link>
    </li>)}</ul>}
    {events.hasNextPage && !events.isFetchNextPageError && <button type="button" className="btn btn-secondary"
      disabled={events.isFetching} onClick={() => void events.fetchNextPage()}>{t("adminOrganizer.loadMore")}</button>}
  </>;
}

export default function OrganizerMediaPage() {
  const { eventId } = useParams();
  const { keycloak } = useKeycloak();
  const { ready } = useOrganizerGate();
  const { t } = useTranslation();
  const subject = keycloak.tokenParsed?.sub;
  const scope = [keycloak.tokenParsed?.iss ?? "", subject ?? ""];
  return <OrganizerLayout title={t("organizerMedia.workspaceTitle")} description={t("organizerMedia.workspaceDescription")}
    hideTopBar className="organizer-media-page">
    {ready && subject && <section className="organizer-panel organizer-dashboard-card">
      {eventId ? <EventImages key={[...scope, eventId].join(":")} eventId={eventId} scope={scope} /> : <MediaEvents scope={scope} />}
    </section>}
  </OrganizerLayout>;
}
