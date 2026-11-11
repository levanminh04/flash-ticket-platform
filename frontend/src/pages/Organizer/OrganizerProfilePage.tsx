import { useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import OrganizerLayout from "../../components/organizer/OrganizerLayout";
import { organizerDashboardService } from "../../services/organizerDashboardService";
import { useOrganizerGate } from "./useOrganizerGate";

function Profile({ subject, issuer }: { subject: string; issuer?: string }) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const key = ["organizer-dashboard-profile", issuer, subject];
  const [notice, setNotice] = useState<{ text: string; error: boolean } | null>(null);
  const profile = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => organizerDashboardService.profile(subject, signal),
    retry: false,
  });
  const upload = useMutation({
    mutationFn: ({ kind, file }: { kind: "logo" | "banner"; file: File }) =>
      organizerDashboardService.uploadBranding(kind, file),
    onSuccess: async (updated, { kind }) => {
      await client.cancelQueries({ queryKey: key, exact: true });
      client.setQueryData(key, updated);
      setNotice({ text: t(kind === "logo" ? "organizerProfile.logoUpdated" : "organizerProfile.bannerUpdated"), error: false });
    },
    onError: (_, { kind }) => {
      setNotice({ text: t(kind === "logo" ? "organizerProfile.logoFailed" : "organizerProfile.bannerFailed"), error: true });
    },
  });

  function selectImage(kind: "logo" | "banner", file?: File) {
    if (!file || upload.isPending) return;
    if (!["image/png", "image/jpeg"].includes(file.type) || file.size === 0 || file.size > 2 * 1024 * 1024) {
      setNotice({ text: t("organizerProfile.invalidImage"), error: true });
      return;
    }
    setNotice(null);
    upload.mutate({ kind, file });
  }

  const data = profile.data;
  return (
    <section className="organizer-panel organizer-dashboard-card">
      {profile.isPending && <p role="status">{t("organizerProfile.loading")}</p>}
      {profile.isError && (
        <>
          <p role="alert">{t("organizerProfile.loadFailed")}</p>
          <button type="button" className="btn btn-secondary" disabled={profile.isFetching} onClick={() => void profile.refetch()}>{t("profile.retry")}</button>
        </>
      )}
      {data && (
        <>
          <div className="organizer-profile-banner">
            {data.bannerUrl ? <img src={data.bannerUrl} alt={data.organizationName} /> : <p>{t("organizerProfile.noBanner")}</p>}
          </div>
          <div className="organizer-profile-card">
            <div className="organizer-profile-avatar">
              {data.logoUrl ? <img src={data.logoUrl} alt={data.organizationName} /> : <span>{t("organizerProfile.noLogo")}</span>}
            </div>
            <div className="organizer-profile-copy">
              <h2>{data.organizationName}</h2>
              <p>{data.shortDescription}</p>
              <p>{t("dashboardV1.followers")} · {data.followerCount.toLocaleString()}</p>
            </div>
          </div>
          <div className="events-v1-controls" aria-busy={upload.isPending}>
            {(["logo", "banner"] as const).map(kind => (
              <label key={kind}>
                <span>{t(kind === "logo" ? "organizerProfile.changeLogo" : "organizerProfile.changeBanner")}</span>
                <input
                  type="file"
                  accept="image/png,image/jpeg"
                  disabled={upload.isPending || profile.isError}
                  onChange={event => {
                    const file = event.currentTarget.files?.[0];
                    event.currentTarget.value = "";
                    selectImage(kind, file);
                  }}
                />
              </label>
            ))}
          </div>
          {notice && <p role={notice.error ? "alert" : "status"}>{notice.text}</p>}
        </>
      )}
    </section>
  );
}

export default function OrganizerProfilePage() {
  const { t } = useTranslation();
  const { keycloak } = useKeycloak();
  const { ready } = useOrganizerGate();
  const subject = keycloak.tokenParsed?.sub;
  const issuer = keycloak.tokenParsed?.iss;
  return (
    <OrganizerLayout title={t("organizerProfile.title")} description={t("organizerProfile.description")} className="organizer-profile-page organizer-hub-page" hideTopBar>
      {ready && subject && <Profile key={`${issuer}:${subject}`} subject={subject} issuer={issuer} />}
    </OrganizerLayout>
  );
}
