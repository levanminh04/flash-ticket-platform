import { useEffect, useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";

export default function AuthRedirectPage() {
  const { keycloak, initialized } = useKeycloak();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    if (!initialized) return;
    if (keycloak.authenticated) { navigate("/", { replace: true }); return; }
    const options = { redirectUri: `${window.location.origin}/` };
    const request = pathname === "/register" ? keycloak.register(options) : keycloak.login(options);
    void request.catch(() => setFailed(true));
  }, [initialized, keycloak, pathname, navigate]);
  return <main className="container auth-redirect-state" role={failed ? "alert" : "status"}>
    <h1>{t(pathname === "/register" ? "auth.createAccount" : "auth.signIn")}</h1>
    <p>{t(failed ? "auth.unavailable" : "auth.redirecting")}</p>
    <Link className="btn btn-outline" to="/">{t("eventDetail.returnHome")}</Link>
  </main>;
}
