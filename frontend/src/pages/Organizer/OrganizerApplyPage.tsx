import { FormEvent, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import {
  ArrowRight,
  Info,
  LoaderCircle,
  RotateCcw,
  ShieldAlert,
} from "lucide-react";
import { toast } from "react-toastify";
import Footer from "../../components/common/Footer";
import { OrganizerApplication, organizerService } from "../../services/organizerService";
import "./organizer-apply.css";

const EMAIL_REGEX = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const PHONE_REGEX = /^(?:0|\+84)(?:2[0-9]{9}|(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9])[0-9]{7})$/;
const HTTPS_URL_REGEX = /^https:\/\/\S+$/;
const TAX_CODE_REGEX = /^[0-9]{10}([0-9]{2}|-?[0-9]{3})?$/;

function formatDate(iso?: string | null, locale = "vi-VN"): string {
  if (!iso) return "—";
  try {
    const d = new Date(iso);
    return d.toLocaleDateString(locale, {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return iso;
  }
}

function pickCurrentApplication(items?: OrganizerApplication[] | null): OrganizerApplication | null {
  if (!items || items.length === 0) return null;
  const sorted = [...items].sort((a, b) => {
    const timeA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
    const timeB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
    return timeB - timeA;
  });

  const active = sorted.find((a) => a.status === "ACTIVE");
  if (active) return active;

  const pending = sorted.find((a) => a.status === "PENDING");
  if (pending) return pending;

  return sorted[0];
}

export default function OrganizerApplyPage() {
  const { t, i18n } = useTranslation();
  const { keycloak, initialized } = useKeycloak();

  const [name, setName] = useState("");
  const [taxCodeInput, setTaxCodeInput] = useState("");
  const [emailInput, setEmailInput] = useState("");
  const [phoneInput, setPhoneInput] = useState("");
  const [socialUrlInput, setSocialUrlInput] = useState("");
  const [websiteUrlInput, setWebsiteUrlInput] = useState("");
  const [addressInput, setAddressInput] = useState("");
  const [description, setDescription] = useState("");

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [loadingApps, setLoadingApps] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [currentApplication, setCurrentApplication] = useState<OrganizerApplication | null>(null);
  const [isReapplying, setIsReapplying] = useState(false);
  const inFlight = useRef(false);

  const isVi = i18n.resolvedLanguage === "vi" || i18n.language === "vi";
  const notUpdated = isVi ? "Chưa cập nhật" : "Not updated";

  // Fetch current user's existing application on mount
  useEffect(() => {
    if (!initialized || !keycloak.authenticated) return;
    const controller = new AbortController();
    setLoadingApps(true);
    setLoadFailed(false);

    void organizerService
      .getMyApplications(controller.signal)
      .then((res) => {
        if (!controller.signal.aborted) {
          // Select most relevant application (ACTIVE > PENDING > latest REJECTED)
          setCurrentApplication(pickCurrentApplication(res.items));
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setLoadFailed(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoadingApps(false);
        }
      });

    return () => controller.abort();
  }, [initialized, keycloak.authenticated, keycloak.subject, loadAttempt]);

  const clearFieldError = (field: string) => {
    if (fieldErrors[field]) {
      setFieldErrors((prev) => {
        const next = { ...prev };
        delete next[field];
        return next;
      });
    }
    if (generalError) setGeneralError("");
  };

  const validateForm = (): boolean => {
    const errors: Record<string, string> = {};
    const orgName = name.trim();
    const tax = taxCodeInput.trim().replace(/\s+/g, "");
    const email = emailInput.trim();
    const phone = phoneInput.trim().replace(/[ .-]/g, "");
    const social = socialUrlInput.trim();
    const website = websiteUrlInput.trim();
    const address = addressInput.trim();
    const desc = description.trim();

    if (!orgName) {
      errors.organizationName = isVi ? "Vui lòng nhập tên tổ chức." : "Organization name is required.";
    } else if (orgName.length > 200) {
      errors.organizationName = isVi ? "Tên tổ chức tối đa 200 ký tự." : "Max 200 characters.";
    }

    if (!tax) {
      errors.taxCode = isVi ? "Vui lòng nhập mã số thuế hoặc CCCD." : "Tax code or Citizen ID is required.";
    } else if (!TAX_CODE_REGEX.test(tax)) {
      errors.taxCode = isVi
        ? "MST (10/13 số) hoặc CCCD (12 số) không hợp lệ."
        : "Invalid tax code (10/13 digits) or Citizen ID (12 digits).";
    }

    if (!email) {
      errors.contactEmail = isVi ? "Vui lòng nhập email liên hệ." : "Contact email is required.";
    } else if (email.length > 254 || !EMAIL_REGEX.test(email)) {
      errors.contactEmail = isVi ? "Email liên hệ không hợp lệ." : "Invalid email address.";
    }

    if (!phone) {
      errors.contactPhone = isVi ? "Vui lòng nhập số điện thoại." : "Phone number is required.";
    } else if (!PHONE_REGEX.test(phone)) {
      errors.contactPhone = isVi
        ? "Số điện thoại Việt Nam không hợp lệ (0... hoặc +84...)."
        : "Invalid Vietnam phone number (0... or +84...).";
    }

    if (!social) {
      errors.socialPageUrl = isVi ? "Vui lòng nhập link Fanpage / kênh chính thức." : "Social channel URL is required.";
    } else if (social.length > 500 || !HTTPS_URL_REGEX.test(social)) {
      errors.socialPageUrl = isVi
        ? "Link Fanpage / kênh chính thức phải bắt đầu bằng https://."
        : "Social URL must start with https:// and be under 500 chars.";
    }

    if (website && (website.length > 500 || !HTTPS_URL_REGEX.test(website))) {
      errors.websiteUrl = isVi
        ? "Website phải bắt đầu bằng https:// và tối đa 500 ký tự."
        : "Website URL must start with https:// and be under 500 chars.";
    }

    if (!address) {
      errors.headquartersAddress = isVi ? "Vui lòng nhập địa chỉ trụ sở." : "Headquarters address is required.";
    } else if (address.length > 500) {
      errors.headquartersAddress = isVi ? "Địa chỉ tối đa 500 ký tự." : "Address max 500 characters.";
    }

    if (!desc) {
      errors.shortDescription = isVi ? "Vui lòng nhập mô tả ngắn." : "Short description is required.";
    } else if (desc.length > 2000) {
      errors.shortDescription = isVi ? "Mô tả tối đa 2000 ký tự." : "Description max 2000 characters.";
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleStartReapply = () => {
    if (!currentApplication) return;
    setName(currentApplication.organizationName || "");
    setTaxCodeInput(currentApplication.taxCode || "");
    setEmailInput(currentApplication.contactEmail || "");
    setPhoneInput(currentApplication.contactPhone || "");
    setSocialUrlInput(currentApplication.socialPageUrl || "");
    setWebsiteUrlInput(currentApplication.websiteUrl || "");
    setAddressInput(currentApplication.headquartersAddress || "");
    setDescription(currentApplication.shortDescription || "");
    setFieldErrors({});
    setGeneralError("");
    setIsReapplying(true);
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (inFlight.current || submitting) return;

    if (!validateForm()) {
      return;
    }

    if (!keycloak.authenticated) {
      void keycloak.login({ redirectUri: window.location.href });
      return;
    }

    inFlight.current = true;
    setSubmitting(true);
    setGeneralError("");

    try {
      const created = await organizerService.applyForOrganizer({
        organizationName: name.trim(),
        shortDescription: description.trim(),
        contactEmail: emailInput.trim(),
        contactPhone: phoneInput.trim().replace(/[ .-]/g, ""),
        websiteUrl: websiteUrlInput.trim() ? websiteUrlInput.trim() : null,
        socialPageUrl: socialUrlInput.trim(),
        headquartersAddress: addressInput.trim(),
        taxCode: taxCodeInput.trim().replace(/\s+/g, ""),
      });

      setCurrentApplication(created);
      setIsReapplying(false);
      toast.success(t("organizer.applySuccess", "Đã gửi đơn đăng ký nhà tổ chức."));
    } catch (err: unknown) {
      const errorObj = err as { response?: { status?: number; data?: { code?: string; message?: string } } };
      const status = errorObj?.response?.status;
      const code = errorObj?.response?.data?.code;

      if (status === 409 || code === "APPLICATION_ALREADY_EXISTS") {
        toast.warning(t("organizer.alreadySubmittedWarning", "Hồ sơ của bạn đã tồn tại trong hệ thống."));
        // Sync application state
        try {
          const res = await organizerService.getMyApplications();
          if (res.items && res.items.length > 0) {
            setCurrentApplication(pickCurrentApplication(res.items));
          }
          setIsReapplying(false);
        } catch {
          setGeneralError(t("organizer.alreadySubmittedWarning", "Hồ sơ của bạn đã tồn tại trong hệ thống."));
        }
      } else if (status === 400 || code === "INVALID_APPLICATION") {
        setGeneralError(
          errorObj?.response?.data?.message ||
            (isVi
              ? "Thông tin hồ sơ không hợp lệ. Vui lòng kiểm tra lại."
              : "Invalid application fields. Please check your inputs.")
        );
      } else {
        setGeneralError(
          t("organizer.applyFailed", "Không thể gửi đơn đăng ký lúc này. Vui lòng thử lại sau.")
        );
      }
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  };

  return (
    <div className="organizer-apply-page">
      <div className="organizer-apply-wrap">
        <div className="organizer-apply-layout">
          {/* VÙNG BÊN TRÁI — ORGANIZER INTRODUCTION */}
          <aside className="organizer-intro-column">
            <div className="organizer-intro-header">
              <h1 className="organizer-intro-title">
                {isVi ? (
                  <>
                    Đăng ký
                    <br />
                    <span className="organizer-intro-highlight">nhà tổ chức</span>
                  </>
                ) : (
                  <>
                    Apply as
                    <br />
                    <span className="organizer-intro-highlight">organizer</span>
                  </>
                )}
              </h1>
              <p className="organizer-intro-subtitle">
                {t("organizer.applyIntroSubtitle", "Bắt đầu hành trình tổ chức sự kiện trên FlashTicket.")}
              </p>
            </div>

            {/* ORGANIZER REGISTRATION GUIDE (3 STEPS) */}
            <div className="organizer-guide-steps" aria-label={isVi ? "Các bước đăng ký" : "Registration steps"}>
              {/* Step 01 */}
              <div className="organizer-guide-step">
                <div className="organizer-step-number" aria-hidden="true">01</div>
                <div className="organizer-step-content">
                  <h2 className="organizer-step-title">{t("organizer.step1Title", "Thông tin tổ chức")}</h2>
                  <p className="organizer-step-desc">
                    {t("organizer.step1Desc", "Cung cấp thông tin cơ bản về đơn vị tổ chức sự kiện.")}
                  </p>
                </div>
              </div>

              {/* Step 02 */}
              <div className="organizer-guide-step">
                <div className="organizer-step-number" aria-hidden="true">02</div>
                <div className="organizer-step-content">
                  <h2 className="organizer-step-title">{t("organizer.step2Title", "Kênh liên hệ")}</h2>
                  <p className="organizer-step-desc">
                    {t("organizer.step2Desc", "Bổ sung email, số điện thoại và các kênh truyền thông chính thức.")}
                  </p>
                </div>
              </div>

              {/* Step 03 */}
              <div className="organizer-guide-step">
                <div className="organizer-step-number" aria-hidden="true">03</div>
                <div className="organizer-step-content">
                  <h2 className="organizer-step-title">{t("organizer.step3Title", "Hoàn tất hồ sơ")}</h2>
                  <p className="organizer-step-desc">
                    {t("organizer.step3Desc", "Kiểm tra và gửi hồ sơ để được xét duyệt quyền tổ chức.")}
                  </p>
                </div>
              </div>
            </div>

            {/* INTRO FOOTER: NOTICE */}
            <div className="organizer-intro-footer">
              <div className="organizer-intro-notice">
                <Info size={16} className="organizer-notice-icon" aria-hidden="true" />
                <span>{t("organizer.reviewNotice", "Hồ sơ được xét duyệt trước khi cấp quyền tổ chức.")}</span>
              </div>
            </div>
          </aside>

          {/* VÙNG BÊN PHẢI — APPLICATION FORM HOẶC STATUS VIEW */}
          <main className="organizer-form-column">
            {keycloak.authenticated && loadingApps ? (
              <div className="organizer-loading-state" role="status">
                <LoaderCircle className="spin" size={32} />
                <span>{t("profile.loading", "Đang tải…")}</span>
              </div>
            ) : keycloak.authenticated && loadFailed ? (
              <section className="organizer-status-card">
                <p role="alert">{isVi ? "Không tải được hồ sơ đăng ký." : "Could not load your application."}</p>
                <button type="button" className="organizer-submit-btn" onClick={() => setLoadAttempt(value => value + 1)}>
                  {t("home.events.retry")}
                </button>
              </section>
            ) : currentApplication && !isReapplying ? (
              /* NẾU ĐÃ CÓ HỒ SƠ: HIỂN THỊ STATUS CARD (PENDING / ACTIVE / REJECTED) */
              <section className="organizer-status-card" aria-label={t("organizer.submittedDetails", "Chi tiết hồ sơ")}>
                <div className="organizer-status-header">
                  <div className="organizer-status-info">
                    <div className="organizer-status-title-row">
                      <h2>
                        {currentApplication.status === "ACTIVE"
                          ? t("organizer.activeTitle", "Hồ sơ đã được phê duyệt")
                          : currentApplication.status === "REJECTED"
                          ? t("organizer.rejectedTitle", "Hồ sơ không được phê duyệt")
                          : t("organizer.pendingReviewTitle", "Hồ sơ đang chờ xét duyệt")}
                      </h2>

                      <span
                        className={`organizer-status-badge organizer-status-badge--${currentApplication.status.toLowerCase()}`}
                        data-status={currentApplication.status}
                      >
                        {t(
                          "organizer.applicationStatus." + currentApplication.status,
                          currentApplication.status === "ACTIVE"
                            ? (isVi ? "Đã duyệt" : "Approved")
                            : currentApplication.status === "REJECTED"
                            ? (isVi ? "Đã từ chối" : "Rejected")
                            : (isVi ? "Đang chờ duyệt" : "Pending Review")
                        )}
                      </span>
                    </div>

                    <p>
                      {currentApplication.status === "ACTIVE"
                        ? t(
                            "organizer.activeDesc",
                            "Chúc mừng! Bạn đã được cấp quyền Nhà tổ chức sự kiện trên FlashTicket."
                          )
                        : currentApplication.status === "REJECTED"
                        ? currentApplication.rejectionReason
                          ? `${t("organizer.rejectedReasonLabel", "Lý do từ chối:")} ${currentApplication.rejectionReason}`
                          : t(
                              "organizer.rejectedNotice",
                              "Hồ sơ đăng ký của bạn không được phê duyệt. Vui lòng liên hệ ban quản trị để biết thêm chi tiết."
                            )
                        : t(
                            "organizer.pendingReviewDesc",
                            "Hồ sơ của bạn đã được gửi thành công và đang được Ban quản trị xem xét. Quá trình xét duyệt thường diễn ra trong vòng 24-48 giờ làm việc."
                          )}
                    </p>
                  </div>
                </div>

                {/* SUBMITTED APPLICATION DETAILS (Typography aligned with Profile "Thông tin cá nhân") */}
                <dl className="organizer-details-dl">
                  <div className="organizer-details-dl-item">
                    <dt>{t("organizer.organizationName", "Tên tổ chức")}</dt>
                    <dd>{currentApplication.organizationName}</dd>
                  </div>

                  <div className="organizer-details-dl-item">
                    <dt>{isVi ? "Mã số thuế / CCCD" : t("organizer.taxCodeLabel")}</dt>
                    <dd className={!currentApplication.taxCode ? "organizer-details-val--empty" : ""}>
                      {currentApplication.taxCode || notUpdated}
                    </dd>
                  </div>

                  <div className="organizer-details-dl-item">
                    <dt>{t("organizer.contactEmail", "Email liên hệ")}</dt>
                    <dd className={!currentApplication.contactEmail ? "organizer-details-val--empty" : ""}>
                      {currentApplication.contactEmail || notUpdated}
                    </dd>
                  </div>

                  <div className="organizer-details-dl-item">
                    <dt>{t("organizer.contactPhone", "Số điện thoại")}</dt>
                    <dd className={!currentApplication.contactPhone ? "organizer-details-val--empty" : ""}>
                      {currentApplication.contactPhone || notUpdated}
                    </dd>
                  </div>

                  <div className="organizer-details-dl-item">
                    <dt>{t("organizer.socialPageUrl", "Fanpage / Kênh chính thức")}</dt>
                    <dd>
                      {currentApplication.socialPageUrl ? (
                        <a href={currentApplication.socialPageUrl} target="_blank" rel="noreferrer">
                          {currentApplication.socialPageUrl}
                        </a>
                      ) : (
                        <span className="organizer-details-val--empty">{notUpdated}</span>
                      )}
                    </dd>
                  </div>

                  <div className="organizer-details-dl-item">
                    <dt>{isVi ? "Website chính thức (tuỳ chọn)" : t("organizer.websiteUrl", "Website")}</dt>
                    <dd>
                      {currentApplication.websiteUrl ? (
                        <a href={currentApplication.websiteUrl} target="_blank" rel="noreferrer">
                          {currentApplication.websiteUrl}
                        </a>
                      ) : (
                        <span className="organizer-details-val--empty">{notUpdated}</span>
                      )}
                    </dd>
                  </div>

                  <div className="organizer-details-dl-item">
                    <dt>{t("organizer.headquartersAddress", "Địa chỉ trụ sở")}</dt>
                    <dd className={!currentApplication.headquartersAddress ? "organizer-details-val--empty" : ""}>
                      {currentApplication.headquartersAddress || notUpdated}
                    </dd>
                  </div>

                  <div className="organizer-details-dl-item">
                    <dt>{t("organizer.shortDescription", "Mô tả ngắn")}</dt>
                    <dd className={!currentApplication.shortDescription ? "organizer-details-val--empty" : ""}>
                      {currentApplication.shortDescription || notUpdated}
                    </dd>
                  </div>

                  <div className="organizer-details-dl-item">
                    <dt>{isVi ? "Mã hồ sơ" : "Application ID"}</dt>
                    <dd>{currentApplication.id}</dd>
                  </div>

                  <div className="organizer-details-dl-item">
                    <dt>{isVi ? "Ngày nộp đơn" : "Submitted date"}</dt>
                    <dd>{formatDate(currentApplication.createdAt, isVi ? "vi-VN" : "en-US")}</dd>
                  </div>
                </dl>

                {currentApplication.status === "ACTIVE" && (
                  <div style={{ display: "flex", justifyContent: "flex-end" }}>
                    <Link to="/organizer" className="organizer-submit-btn">
                      <span>{t("organizer.goToWorkspace", "Truy cập Organizer Workspace")}</span>
                      <ArrowRight size={17} aria-hidden="true" />
                    </Link>
                  </div>
                )}

                {currentApplication.status === "REJECTED" && (
                  <div style={{ display: "flex", justifyContent: "flex-end" }}>
                    <button
                      type="button"
                      className="organizer-submit-btn"
                      onClick={handleStartReapply}
                    >
                      <RotateCcw size={17} aria-hidden="true" />
                      <span>{t("organizer.reapplyBtn", isVi ? "Gửi lại hồ sơ" : "Submit new application")}</span>
                    </button>
                  </div>
                )}
              </section>
            ) : (
              /* FORM ĐĂNG KÝ MỚI LIÊN TỤC VỚI 3 NHÓM */
              <form className="organizer-apply-form" onSubmit={handleSubmit} noValidate>
                {/* Form Header */}
                <div className="organizer-form-header">
                  <h2 className="organizer-form-title">{t("organizer.formTitle", "Thông tin đăng ký")}</h2>
                  <span className="organizer-required-hint">
                    {t("organizer.requiredHintPrefix", "Các mục")}{" "}
                    <span className="organizer-asterisk">*</span>{" "}
                    {t("organizer.requiredHintSuffix", "là bắt buộc")}
                  </span>
                </div>

                {isReapplying && (
                  <div className="organizer-reapply-banner" role="status">
                    <div className="organizer-reapply-banner-content">
                      <Info size={18} className="organizer-reapply-banner-icon" aria-hidden="true" />
                      <div className="organizer-reapply-banner-text">
                        <strong className="organizer-reapply-banner-title">
                          {t("organizer.reapplyingTitle", isVi ? "Gửi lại hồ sơ mới" : "Reapplying for Organizer")}
                        </strong>
                        <p className="organizer-reapply-banner-desc">
                          {t(
                            "organizer.reapplyNotice",
                            isVi
                              ? "Hồ sơ trước đó của bạn không được phê duyệt. Vui lòng kiểm tra, cập nhật các thông tin cần thiết và gửi lại đơn đăng ký mới."
                              : "Your previous application was not approved. Please review and update your information before submitting a new application."
                          )}
                        </p>
                      </div>
                    </div>
                    <button
                      type="button"
                      className="organizer-reapply-cancel-btn"
                      onClick={() => setIsReapplying(false)}
                    >
                      {t("organizer.cancelReapply", isVi ? "Xem lại hồ sơ trước" : "View previous application")}
                    </button>
                  </div>
                )}

                {/* NHÓM 01 — THÔNG TIN ĐƠN VỊ */}
                <section className="organizer-form-group" aria-label={t("organizer.group1Title", "Thông tin đơn vị")}>
                  <div className="organizer-group-header">
                    <span className="organizer-group-num">01</span>
                    <span className="organizer-group-divider">|</span>
                    <h3 className="organizer-group-title">{t("organizer.group1Title", "Thông tin đơn vị")}</h3>
                  </div>

                  <div className="organizer-field-grid organizer-field-grid--2">
                    <div className="organizer-field">
                      <label htmlFor="organizationName">
                        {t("organizer.organizationName", "Tên tổ chức")} <span className="organizer-asterisk">*</span>
                      </label>
                      <input
                        id="organizationName"
                        type="text"
                        value={name}
                        onChange={(e) => {
                          setName(e.target.value);
                          clearFieldError("organizationName");
                        }}
                        placeholder={isVi ? "Nhập tên tổ chức / doanh nghiệp" : "Enter organization name"}
                        maxLength={200}
                        disabled={submitting}
                        className={
                          fieldErrors.organizationName
                            ? "organizer-input organizer-input--error"
                            : "organizer-input"
                        }
                      />
                      {fieldErrors.organizationName && (
                        <p className="organizer-field-error" role="alert">
                          {fieldErrors.organizationName}
                        </p>
                      )}
                    </div>

                    <div className="organizer-field">
                      <label htmlFor="taxCode">
                        {isVi ? "Mã số thuế / CCCD" : t("organizer.taxCodeLabel")}{" "}
                        <span className="organizer-asterisk">*</span>
                      </label>
                      <input
                        id="taxCode"
                        type="text"
                        value={taxCodeInput}
                        onChange={(e) => {
                          setTaxCodeInput(e.target.value);
                          clearFieldError("taxCode");
                        }}
                        placeholder={t("organizer.taxCodePlaceholder", "MST 10/13 số hoặc CCCD 12 số")}
                        maxLength={14}
                        disabled={submitting}
                        className={
                          fieldErrors.taxCode ? "organizer-input organizer-input--error" : "organizer-input"
                        }
                      />
                      {fieldErrors.taxCode && (
                        <p className="organizer-field-error" role="alert">
                          {fieldErrors.taxCode}
                        </p>
                      )}
                    </div>
                  </div>
                </section>

                {/* NHÓM 02 — THÔNG TIN LIÊN HỆ */}
                <section className="organizer-form-group" aria-label={t("organizer.group2Title", "Thông tin liên hệ")}>
                  <div className="organizer-group-header">
                    <span className="organizer-group-num">02</span>
                    <span className="organizer-group-divider">|</span>
                    <h3 className="organizer-group-title">{t("organizer.group2Title", "Thông tin liên hệ")}</h3>
                  </div>

                  <div className="organizer-field-grid organizer-field-grid--2">
                    <div className="organizer-field">
                      <label htmlFor="contactEmail">
                        {isVi ? "Email liên hệ" : t("organizer.contactEmail")}{" "}
                        <span className="organizer-asterisk">*</span>
                      </label>
                      <input
                        id="contactEmail"
                        type="email"
                        value={emailInput}
                        onChange={(e) => {
                          setEmailInput(e.target.value);
                          clearFieldError("contactEmail");
                        }}
                        placeholder="email@congty.vn"
                        maxLength={254}
                        disabled={submitting}
                        className={
                          fieldErrors.contactEmail
                            ? "organizer-input organizer-input--error"
                            : "organizer-input"
                        }
                      />
                      {fieldErrors.contactEmail && (
                        <p className="organizer-field-error" role="alert">
                          {fieldErrors.contactEmail}
                        </p>
                      )}
                    </div>

                    <div className="organizer-field">
                      <label htmlFor="contactPhone">
                        {isVi ? "Số điện thoại" : t("organizer.contactPhone")}{" "}
                        <span className="organizer-asterisk">*</span>
                      </label>
                      <input
                        id="contactPhone"
                        type="tel"
                        value={phoneInput}
                        onChange={(e) => {
                          setPhoneInput(e.target.value);
                          clearFieldError("contactPhone");
                        }}
                        placeholder="09... hoặc +84..."
                        maxLength={20}
                        disabled={submitting}
                        className={
                          fieldErrors.contactPhone
                            ? "organizer-input organizer-input--error"
                            : "organizer-input"
                        }
                      />
                      {fieldErrors.contactPhone && (
                        <p className="organizer-field-error" role="alert">
                          {fieldErrors.contactPhone}
                        </p>
                      )}
                    </div>

                    <div className="organizer-field">
                      <label htmlFor="socialPageUrl">
                        {isVi ? "Fanpage / Kênh chính thức" : t("organizer.socialPageUrl")}{" "}
                        <span className="organizer-asterisk">*</span>
                      </label>
                      <input
                        id="socialPageUrl"
                        type="url"
                        value={socialUrlInput}
                        onChange={(e) => {
                          setSocialUrlInput(e.target.value);
                          clearFieldError("socialPageUrl");
                        }}
                        placeholder="https://facebook.com/..."
                        maxLength={500}
                        disabled={submitting}
                        className={
                          fieldErrors.socialPageUrl
                            ? "organizer-input organizer-input--error"
                            : "organizer-input"
                        }
                      />
                      {fieldErrors.socialPageUrl && (
                        <p className="organizer-field-error" role="alert">
                          {fieldErrors.socialPageUrl}
                        </p>
                      )}
                    </div>

                    <div className="organizer-field">
                      <label htmlFor="websiteUrl">
                        {isVi ? "Website (tùy chọn)" : t("organizer.websiteUrl")}
                      </label>
                      <input
                        id="websiteUrl"
                        type="url"
                        value={websiteUrlInput}
                        onChange={(e) => {
                          setWebsiteUrlInput(e.target.value);
                          clearFieldError("websiteUrl");
                        }}
                        placeholder="https://..."
                        maxLength={500}
                        disabled={submitting}
                        className={
                          fieldErrors.websiteUrl
                            ? "organizer-input organizer-input--error"
                            : "organizer-input"
                        }
                      />
                      {fieldErrors.websiteUrl && (
                        <p className="organizer-field-error" role="alert">
                          {fieldErrors.websiteUrl}
                        </p>
                      )}
                    </div>
                  </div>
                </section>

                {/* NHÓM 03 — ĐỊA CHỈ & GIỚI THIỆU */}
                <section className="organizer-form-group" aria-label={t("organizer.group3Title", "Địa chỉ & giới thiệu")}>
                  <div className="organizer-group-header">
                    <span className="organizer-group-num">03</span>
                    <span className="organizer-group-divider">|</span>
                    <h3 className="organizer-group-title">{t("organizer.group3Title", "Địa chỉ & giới thiệu")}</h3>
                  </div>

                  <div className="organizer-field-grid organizer-field-grid--full">
                    <div className="organizer-field organizer-field--full">
                      <label htmlFor="headquartersAddress">
                        {t("organizer.headquartersAddress", "Địa chỉ trụ sở")}{" "}
                        <span className="organizer-asterisk">*</span>
                      </label>
                      <input
                        id="headquartersAddress"
                        type="text"
                        value={addressInput}
                        onChange={(e) => {
                          setAddressInput(e.target.value);
                          clearFieldError("headquartersAddress");
                        }}
                        placeholder={
                          isVi
                            ? "Số nhà, đường, phường/xã, tỉnh/thành phố"
                            : t("organizer.headquartersAddressPlaceholder")
                        }
                        maxLength={500}
                        disabled={submitting}
                        className={
                          fieldErrors.headquartersAddress
                            ? "organizer-input organizer-input--error"
                            : "organizer-input"
                        }
                      />
                      {fieldErrors.headquartersAddress && (
                        <p className="organizer-field-error" role="alert">
                          {fieldErrors.headquartersAddress}
                        </p>
                      )}
                    </div>

                    <div className="organizer-field organizer-field--full">
                      <label htmlFor="shortDescription">
                        {t("organizer.shortDescription", "Mô tả ngắn")}{" "}
                        <span className="organizer-asterisk">*</span>
                      </label>
                      <textarea
                        id="shortDescription"
                        value={description}
                        onChange={(e) => {
                          setDescription(e.target.value);
                          clearFieldError("shortDescription");
                        }}
                        placeholder={
                          isVi
                            ? "Mô tả ngắn gọn về đơn vị tổ chức, quy mô và các loại hình sự kiện dự kiến..."
                            : "Brief description of the organization, scale, and planned events..."
                        }
                        rows={4}
                        maxLength={2000}
                        disabled={submitting}
                        className={
                          fieldErrors.shortDescription
                            ? "organizer-input organizer-textarea organizer-input--error"
                            : "organizer-input organizer-textarea"
                        }
                      />
                      {fieldErrors.shortDescription && (
                        <p className="organizer-field-error" role="alert">
                          {fieldErrors.shortDescription}
                        </p>
                      )}
                    </div>
                  </div>
                </section>

                {/* GENERAL ERROR BANNER */}
                {generalError && (
                  <div className="organizer-general-error" role="alert">
                    <ShieldAlert size={18} aria-hidden="true" />
                    <span>{generalError}</span>
                  </div>
                )}

                {/* FORM FOOTER */}
                <div className="organizer-form-footer">
                  <button type="submit" className="organizer-submit-btn" disabled={submitting}>
                    {submitting ? (
                      <>
                        <LoaderCircle size={17} className="spin" aria-hidden="true" />
                        <span>{t("organizer.submitting", "Đang gửi hồ sơ…")}</span>
                      </>
                    ) : (
                      <>
                        <span>
                          {isReapplying
                            ? t("organizer.reapplySubmitButton", isVi ? "Gửi lại đơn đăng ký" : "Submit new application")
                            : (isVi ? "Gửi hồ sơ đăng ký" : t("organizer.submitApplication", "Gửi hồ sơ đăng ký"))}
                        </span>
                        <ArrowRight size={17} aria-hidden="true" />
                      </>
                    )}
                  </button>
                </div>
              </form>
            )}
          </main>
        </div>
      </div>

      <Footer />
    </div>
  );
}
