import { useQuery } from "@tanstack/react-query";
import { FormEvent, useEffect, useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import AccountSidebar from "../../components/account/AccountSidebar";
import AccountCategoryNav from "../../components/common/AccountCategoryNav";
import { PROFILE_UPDATED_EVENT } from "../../components/layout/Navbar";
import { userService, type UserProfile } from "../../services/userService";

type Province = { ma_tinh: string; ten_day_du: string; url: string };
type Ward = { ma_xa: string; ma_tinh: string; ten: string };

async function addressCatalog<T>(path: string, signal: AbortSignal): Promise<T[]> {
  const response = await fetch("https://diachiviet.com/api/v1/" + path, { signal });
  if (!response.ok) throw new Error("Address catalog unavailable");
  const data = await response.json();
  if (!Array.isArray(data.dong)) throw new Error("Invalid address catalog");
  return data.dong;
}

function ProfileEditor() {
  const { t } = useTranslation();
  const { i18n } = useTranslation();
  const vi = i18n.resolvedLanguage?.startsWith("vi");
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [birthDate, setBirthDate] = useState("");
  const [phone, setPhone] = useState("");
  const [address, setAddress] = useState("");
  const [provinceCode, setProvinceCode] = useState("");
  const [wardCode, setWardCode] = useState("");
  const [street, setStreet] = useState("");
  const [uploading, setUploading] = useState(false);
  const [avatarError, setAvatarError] = useState(false);
  const provinces = useQuery({
    queryKey: ["vn-address-provinces"],
    queryFn: ({ signal }) => addressCatalog<Province>("tinh.json", signal),
    staleTime: 60 * 60 * 1000,
  });
  const province = provinces.data?.find(item => item.ma_tinh === provinceCode);
  const slug = province?.url.match(/\/hanh-chinh\/([a-z0-9-]+)\/$/)?.[1];
  const wards = useQuery({
    queryKey: ["vn-address-wards", slug],
    queryFn: ({ signal }) => addressCatalog<Ward>("xa/" + slug + ".json", signal),
    enabled: Boolean(slug),
    staleTime: 60 * 60 * 1000,
  });
  const ward = wards.data?.find(item => item.ma_xa === wardCode && item.ma_tinh === provinceCode);
  const [name, setName] = useState("");
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [loadFailed, setLoadFailed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveResult, setSaveResult] = useState<"success" | "error" | null>(null);

  useEffect(() => {
    let active = true;
    void userService.getProfile().then((profile) => {
      if (!active) return;
      if (!profile) {
        setLoadFailed(true);
        return;
      }
      const displayName = profile.displayName ?? "";
      setProfile(profile);
      setName(displayName);
      setBirthDate(profile.dateOfBirth ?? "");
      setPhone(profile.phoneNumber ?? "");
      setAddress(profile.address ?? "");
    });
    return () => {
      active = false;
    };
  }, [loadAttempt]);

  const trimmedName = name.trim();
  const nextAddress = province && ward
    ? [street.trim(), ward.ten, province.ten_day_du].filter(Boolean).join(", ")
    : address;
  const nameChanged = trimmedName !== (profile?.displayName ?? "");
  const changes = {
    ...(nameChanged ? { displayName: trimmedName } : {}),
    ...(birthDate !== (profile?.dateOfBirth ?? "") ? { dateOfBirth: birthDate || null } : {}),
    ...(phone !== (profile?.phoneNumber ?? "") ? { phoneNumber: phone || null } : {}),
    ...(nextAddress !== (profile?.address ?? "") ? { address: nextAddress || null } : {}),
  };
  const canSave = Boolean(profile) && !saving && !uploading && (!provinceCode || Boolean(ward)) &&
    (!nameChanged || Boolean(trimmedName)) && Object.keys(changes).length > 0;

  async function uploadAvatar(file: File) {
    setAvatarError(false);
    setUploading(true);
    try {
      const updated = await userService.uploadAvatar(file);
      setProfile(updated);
      window.dispatchEvent(new Event(PROFILE_UPDATED_EVENT));
    } catch {
      setAvatarError(true);
    } finally {
      setUploading(false);
    }
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSave) return;
    setSaving(true);
    setSaveResult(null);
    try {
      const profile = await userService.updateProfile(changes);
      const displayName = profile.displayName ?? "";
      setProfile(profile);
      setName(displayName);
      setBirthDate(profile.dateOfBirth ?? "");
      setPhone(profile.phoneNumber ?? "");
      setAddress(profile.address ?? "");
      setProvinceCode("");
      setWardCode("");
      setStreet("");
      setSaveResult("success");
      window.dispatchEvent(new Event(PROFILE_UPDATED_EVENT));
    } catch {
      setSaveResult("error");
    } finally {
      setSaving(false);
    }
  }

  if (loadFailed) {
    return (
      <section className="profile-card profile-editor-card">
        <p role="alert">{t("profile.loadFailed")}</p>
        <button type="button" className="btn btn-secondary" onClick={() => {
          setLoadFailed(false);
          setLoadAttempt((attempt) => attempt + 1);
        }}>
          {t("profile.retry")}
        </button>
      </section>
    );
  }

  if (!profile) {
    return <p className="profile-loading" role="status">{t("profile.loading")}</p>;
  }

  return (
    <form className="profile-card profile-editor-card" onSubmit={save}>
      <div className="profile-editor-header">
        <h2>{t("profile.editTitle")}</h2>
      </div>
      <label className="profile-field">
        <span>{t("profile.displayName")}</span>
        <input
          value={name}
          onChange={(event) => {
            setName(event.target.value);
            setSaveResult(null);
          }}
          required={nameChanged}
          maxLength={200}
          disabled={saving}
        />
      </label>
      <label className="profile-field">
        <span>Email</span>
        <input type="email" readOnly value={profile.email ?? ""} placeholder={t("profile.unavailable")} />
      </label>
      <label className="profile-field">
        <span>{vi ? "Ảnh đại diện (JPEG hoặc PNG, tối đa 2 MB)" : "Avatar (JPEG or PNG, up to 2 MB)"}</span>
        {profile.avatarUrl && <img src={profile.avatarUrl} alt="" width={72} height={72} style={{ borderRadius: "50%" }} />}
        <input type="file" accept="image/jpeg,image/png" disabled={saving || uploading} onChange={event => {
          const file = event.currentTarget.files?.[0];
          event.currentTarget.value = "";
          if (file) void uploadAvatar(file);
        }} />
      </label>
      {uploading && <p role="status">{vi ? "Đang tải ảnh…" : "Uploading avatar…"}</p>}
      {avatarError && <p role="alert">{vi ? "Không tải được ảnh. Kiểm tra định dạng và kích thước rồi thử lại." : "Upload failed. Check the image format and size, then try again."}</p>}
      <label className="profile-field">
        <span>{vi ? "Ngày sinh" : "Date of birth"}</span>
        <input value={birthDate} onChange={event => setBirthDate(event.target.value)} placeholder="DD-MM-YYYY" pattern="[0-9]{2}-[0-9]{2}-[0-9]{4}" maxLength={10} disabled={saving} />
      </label>
      <label className="profile-field">
        <span>{vi ? "Số điện thoại di động Việt Nam" : "Vietnamese mobile number"}</span>
        <input type="tel" value={phone} onChange={event => setPhone(event.target.value)} placeholder="09… / +84…" maxLength={20} disabled={saving} />
      </label>
      <fieldset disabled={saving}>
        <legend>{vi ? "Địa chỉ" : "Address"}</legend>
        {address && <p>{address} <button type="button" onClick={() => setAddress("")}>{vi ? "Xóa địa chỉ" : "Clear address"}</button></p>}
        <label className="profile-field">
          <span>{vi ? "Tỉnh / Thành phố" : "Province / City"}</span>
          <select value={provinceCode} onChange={event => { setProvinceCode(event.target.value); setWardCode(""); }}>
            <option value="">{vi ? "Chọn tỉnh / thành phố" : "Select a province / city"}</option>
            {provinces.data?.map(item => <option key={item.ma_tinh} value={item.ma_tinh}>{item.ten_day_du}</option>)}
          </select>
        </label>
        <label className="profile-field">
          <span>{vi ? "Xã / Phường / Đặc khu" : "Commune / Ward / Special zone"}</span>
          <select value={wardCode} disabled={!wards.data} onChange={event => setWardCode(event.target.value)}>
            <option value="">{vi ? "Chọn đơn vị cấp xã" : "Select a local division"}</option>
            {wards.data?.map(item => <option key={item.ma_xa} value={item.ma_xa}>{item.ten}</option>)}
          </select>
        </label>
        <label className="profile-field">
          <span>{vi ? "Số nhà, tên đường" : "Street address"}</span>
          <input value={street} disabled={!ward} maxLength={350} onChange={event => setStreet(event.target.value)} />
        </label>
        {(provinces.isError || wards.isError) && <p role="alert">{vi ? "Không tải được danh mục địa chỉ." : "Could not load address options."} <button type="button" onClick={() => void (provinces.isError ? provinces.refetch() : wards.refetch())}>{t("profile.retry")}</button></p>}
        <small><a href="https://diachiviet.com/api-tinh-thanh/">Địa Chỉ Việt · CC BY 4.0</a></small>
      </fieldset>
      <div className="profile-editor-actions">
        <button type="submit" className="btn btn-primary" disabled={!canSave}>
          {t(saving ? "profile.saving" : "profile.save")}
        </button>
      </div>
      {saveResult === "error" && <p role="alert">{t("profile.updateFailed")}</p>}
      {saveResult === "success" && <p role="status">{t("profile.updateSuccess")}</p>}
    </form>
  );
}

export default function ProfilePage() {
  const { t } = useTranslation();
  const { keycloak, initialized } = useKeycloak();
  return (
    <div className="profile-page">
      <AccountCategoryNav />
      <div className="container account-layout-container">
        <AccountSidebar />
        <div className="account-main-content">
          <h1 className="profile-page-title">{t("profile.title")}</h1>
          {!initialized ? (
            <p className="profile-loading" role="status">{t("profile.loading")}</p>
          ) : keycloak.authenticated ? (
            <ProfileEditor key={keycloak.subject} />
          ) : (
            <section className="profile-card profile-editor-card">
              <p>{t("profile.loginRequired")}</p>
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => void keycloak.login({ redirectUri: window.location.href })}
              >
                {t("auth.signIn")}
              </button>
            </section>
          )}
        </div>
      </div>
    </div>
  );
}
