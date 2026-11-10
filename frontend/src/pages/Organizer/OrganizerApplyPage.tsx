import { FormEvent, useRef, useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { LoaderCircle, SendHorizontal, ShieldCheck } from "lucide-react";
import { useTranslation } from "react-i18next";
import Footer from "../../components/common/Footer";
import { OrganizerApplication, organizerService } from "../../services/organizerService";

function OrganizerApplicationForm() {
  const { t } = useTranslation();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [failed, setFailed] = useState(false);
  const [application, setApplication] = useState<OrganizerApplication | null>(null);
  const inFlight = useRef(false);
  const organizationName = name.trim();
  const shortDescription = description.trim();
  const valid = organizationName.length > 0 && organizationName.length <= 200
    && shortDescription.length > 0 && shortDescription.length <= 2000;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!valid || inFlight.current) return;
    inFlight.current = true;
    setSubmitting(true);
    setFailed(false);
    try {
      setApplication(await organizerService.applyForOrganizer({ organizationName, shortDescription }));
    } catch {
      setFailed(true);
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  }

  if (application) {
    return (
      <section className="organizer-application-success" role="status">
        <ShieldCheck size={36} aria-hidden="true" />
        <h2>{t("organizer.submittedTitle")}</h2>
        <p>{application.organizationName}</p>
        <p>{t(`organizer.applicationStatus.${application.status}`)}</p>
        <p>{t("organizer.applicationId")}: {application.id}</p>
      </section>
    );
  }

  return (
    <form className="organizer-application-form" onSubmit={submit}>
      <label className="organizer-application-span-2">
        <span>{t("organizer.organizationName")}</span>
        <input value={name} onChange={event => setName(event.target.value)} required maxLength={200} disabled={submitting} />
      </label>
      <label className="organizer-application-span-2">
        <span>{t("organizer.shortDescription")}</span>
        <textarea value={description} onChange={event => setDescription(event.target.value)} required maxLength={2000} rows={4} disabled={submitting} />
      </label>
      {failed && <p className="organizer-application-span-2" role="alert">{t("organizer.applyFailed")}</p>}
      <div className="organizer-application-actions">
        <button className="btn btn-primary" type="submit" disabled={!valid || submitting}>
          {submitting ? <LoaderCircle size={16} className="spin" /> : <SendHorizontal size={16} />}
          {t("organizer.submitApplication")}
        </button>
      </div>
    </form>
  );
}

export default function OrganizerApplyPage() {
  const { t } = useTranslation();
  const { keycloak, initialized } = useKeycloak();

  if (!initialized || !keycloak.authenticated) {
    return (
      <main className="organizer-application-page">
        <section className="organizer-application-state">
          <ShieldCheck size={42} aria-hidden="true" />
          <h1>{t("organizer.applyTitle")}</h1>
          {initialized ? (
            <>
              <p>{t("organizer.applyLoginRequired")}</p>
              <button className="btn btn-primary" type="button" onClick={() => void keycloak.login()}>
                {t("auth.signIn")}
              </button>
            </>
          ) : <p role="status">{t("profile.loading")}</p>}
        </section>
      </main>
    );
  }

  return (
    <main className="organizer-application-page">
      <section className="container organizer-application-wrap">
        <div className="organizer-application-header">
          <h1>{t("organizer.applyTitle")}</h1>
          <p>{t("organizer.applyDescription")}</p>
        </div>
        <OrganizerApplicationForm key={keycloak.tokenParsed?.sub} />
      </section>
      <Footer />
    </main>
  );
}
