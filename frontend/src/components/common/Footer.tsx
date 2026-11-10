import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import "./Footer.css";

export default function Footer() {
  const { t } = useTranslation();

  return (
    <footer className="discovery-footer">
      <div className="discovery-footer-shell">
        <div className="discovery-footer-grid">
          <div><Link to="/" className="discovery-brand">FlashTicket</Link><p>{t("footer.brandDescription")}</p></div>
          <div><h2>{t("footer.quickLinks")}</h2><Link to="/search">{t("nav.events")}</Link><Link to="/venues">{t("nav.venues")}</Link><Link to="/organizer">{t("nav.organizer")}</Link></div>
          <div><h2>{t("nav.myAccount")}</h2><Link to="/profile">{t("nav.myAccount")}</Link><Link to="/my-orders">{t("nav.orders")}</Link><Link to="/my-tickets">{t("nav.tickets")}</Link></div>
        </div>
        <p className="discovery-copyright">{t("footer.copyright")}</p>
      </div>
    </footer>
  );
}
