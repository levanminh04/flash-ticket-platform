import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import "./Footer.css";

export default function Footer() {
  const { t, i18n } = useTranslation();
  const isEnglish = i18n.resolvedLanguage === "en";

  return (
    <footer className="discovery-footer">
      <div className="discovery-footer-shell">
        <div className="discovery-footer-top">
          <div className="discovery-footer-brand-col">
            <Link to="/" className="discovery-brand">
              <img src="/icons/icons8-black-cat.png" alt="" width={28} height={28} />
              <span>FlashTicket</span>
            </Link>
            <p className="discovery-footer-desc">{t("footer.brandDescription")}</p>
            <div className="discovery-footer-socials" aria-label={isEnglish ? "Social links" : "Mạng xã hội"}>
              <a href="https://facebook.com" target="_blank" rel="noreferrer" aria-label="Facebook">
                <img src="https://img.icons8.com/color/48/facebook-new.png" alt="Facebook" width={24} height={24} loading="lazy" />
              </a>
              <a href="https://tiktok.com" target="_blank" rel="noreferrer" aria-label="TikTok">
                <img src="https://img.icons8.com/color/48/tiktok.png" alt="TikTok" width={24} height={24} loading="lazy" />
              </a>
              <a href="https://youtube.com" target="_blank" rel="noreferrer" aria-label="YouTube">
                <img src="https://img.icons8.com/color/48/youtube-play.png" alt="YouTube" width={24} height={24} loading="lazy" />
              </a>
              <a href="https://instagram.com" target="_blank" rel="noreferrer" aria-label="Instagram">
                <img src="https://img.icons8.com/color/48/instagram-new.png" alt="Instagram" width={24} height={24} loading="lazy" />
              </a>
            </div>
          </div>

          <div className="discovery-footer-columns">
            <div className="discovery-footer-col">
              <h2>{isEnglish ? "Explore" : "Khám phá"}</h2>
              <Link to="/search">{t("nav.events")}</Link>
              <Link to="/venues">{t("nav.venues")}</Link>
              <Link to="/my-tickets">{t("nav.tickets")}</Link>
              <Link to="/my-orders">{t("nav.orders")}</Link>
            </div>

            <div className="discovery-footer-col">
              <h2>{isEnglish ? "Organizers" : "Nhà tổ chức"}</h2>
              <Link to="/organizer">{t("nav.organizer")}</Link>
              <Link to="/organizers/apply">{isEnglish ? "Become an Organizer" : "Đăng ký tổ chức"}</Link>
              <Link to="/profile">{t("nav.myAccount")}</Link>
              <a href="#discovery-faq-title">{isEnglish ? "Help & FAQs" : "Câu hỏi thường gặp"}</a>
            </div>

            <div className="discovery-footer-col">
              <h2>{isEnglish ? "Company" : "Nền tảng"}</h2>
              <Link to="/">{isEnglish ? "Home" : "Trang chủ"}</Link>
              <a href="#discovery-featured">{isEnglish ? "Featured Events" : "Sự kiện nổi bật"}</a>
              <a href="#discovery-organizers">{isEnglish ? "Top Organizers" : "Nhà tổ chức tiêu biểu"}</a>
              <a href="#discovery-venues">{isEnglish ? "Featured Venues" : "Địa điểm nổi bật"}</a>
            </div>
          </div>
        </div>

        <div className="discovery-footer-bottom">
          <p className="discovery-copyright">{t("footer.copyright")}</p>
          <div className="discovery-footer-legal">
            <a href="#discovery-faq-title">{isEnglish ? "Privacy Policy" : "Chính sách bảo mật"}</a>
            <a href="#discovery-faq-title">{isEnglish ? "Terms of Service" : "Điều khoản sử dụng"}</a>
            <a href="#discovery-faq-title">{isEnglish ? "Cookies Settings" : "Cài đặt Cookie"}</a>
          </div>
        </div>
      </div>
    </footer>
  );
}
