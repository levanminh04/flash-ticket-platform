import { useQuery, useQueryClient } from "@tanstack/react-query";
import axios from "axios";
import { FormEvent, useEffect, useRef, useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import {
  Camera,
  SquarePen,
  Lock,
  Calendar,
  Phone,
  Home,
  ChevronDown,
} from "lucide-react";
import { toast } from "react-toastify";
import AccountSidebar from "../../components/account/AccountSidebar";
import Footer from "../../components/common/Footer";
import { PROFILE_UPDATED_EVENT } from "../../components/layout/Navbar";
import { useUserProfile, USER_PROFILE_QUERY_KEY } from "../../hooks/useUserProfile";
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

function getInitials(name?: string | null, email?: string | null): string {
  const text = (name || "").trim() || (email || "").split("@")[0].trim();
  if (!text) return "FT";
  const words = text.split(/\s+/).filter(Boolean);
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

function formatDisplayDate(val?: string | null): string {
  if (!val) return "";
  const m = val.match(/^(\d{2})[-/](\d{2})[-/](\d{4})$/);
  if (m) {
    return `${m[1]}/${m[2]}/${m[3]}`;
  }
  return val;
}

function formatProgressiveDate(digits: string, isDeleting = false): string {
  if (!digits) return "";
  if (digits.length < 2) {
    return digits;
  }
  if (digits.length === 2) {
    return isDeleting ? digits : `${digits}/`;
  }
  if (digits.length < 4) {
    return `${digits.slice(0, 2)}/${digits.slice(2)}`;
  }
  if (digits.length === 4) {
    return isDeleting ? `${digits.slice(0, 2)}/${digits.slice(2)}` : `${digits.slice(0, 2)}/${digits.slice(2)}/`;
  }
  return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4, 8)}`;
}

function calculateCursorPos(rawVal: string, rawPos: number, formattedVal: string, isDeleting: boolean): number {
  const rawBefore = rawVal.slice(0, rawPos);
  const digitsBefore = rawBefore.replace(/\D/g, "").length;
  const rawHadTrailingSlash = rawBefore.endsWith("/");

  if (digitsBefore === 0) return 0;

  let seenDigits = 0;
  let targetIdx = 0;

  for (let i = 0; i < formattedVal.length; i++) {
    if (/\d/.test(formattedVal[i])) {
      seenDigits++;
      if (seenDigits === digitsBefore) {
        targetIdx = i + 1;
        break;
      }
    }
  }

  if (!isDeleting) {
    if (targetIdx < formattedVal.length && formattedVal[targetIdx] === "/") {
      targetIdx++;
    }
  } else {
    if (rawHadTrailingSlash && targetIdx < formattedVal.length && formattedVal[targetIdx] === "/") {
      targetIdx++;
    }
  }

  return Math.min(targetIdx, formattedVal.length);
}

function validateDateOfBirth(val: string, vi: boolean): { isValid: boolean; error?: string; normalized: string | null } {
  const trimmed = val.trim();
  if (!trimmed) {
    return { isValid: true, normalized: null };
  }

  const match = trimmed.match(/^(\d{2})[/.-](\d{2})[/.-](\d{4})$/);
  if (!match) {
    return {
      isValid: false,
      error: vi
        ? "Ngày sinh chưa đúng định dạng DD/MM/YYYY (ví dụ: 15/05/1998)."
        : "Date of birth must be DD/MM/YYYY format (e.g. 15/05/1998).",
      normalized: null,
    };
  }

  const d = parseInt(match[1], 10);
  const m = parseInt(match[2], 10);
  const y = parseInt(match[3], 10);

  if (m < 1 || m > 12) {
    return {
      isValid: false,
      error: vi ? "Tháng sinh không hợp lệ (từ 01 đến 12)." : "Invalid birth month (01 to 12).",
      normalized: null,
    };
  }

  if (y < 1900) {
    return {
      isValid: false,
      error: vi ? "Năm sinh không hợp lệ (từ năm 1900 trở đi)." : "Invalid birth year (from 1900 onwards).",
      normalized: null,
    };
  }

  const isLeapYear = (y % 4 === 0 && y % 100 !== 0) || (y % 400 === 0);
  let maxDays = 31;
  if ([4, 6, 9, 11].includes(m)) {
    maxDays = 30;
  } else if (m === 2) {
    maxDays = isLeapYear ? 29 : 28;
  }

  if (d < 1 || d > maxDays) {
    if (m === 2 && d === 29 && !isLeapYear) {
      return {
        isValid: false,
        error: vi
          ? `Năm ${y} không phải năm nhuận, tháng 2 chỉ có 28 ngày.`
          : `Year ${y} is not a leap year, February only has 28 days.`,
        normalized: null,
      };
    }
    return {
      isValid: false,
      error: vi
        ? `Ngày sinh không hợp lệ (tháng ${m} chỉ có tối đa ${maxDays} ngày).`
        : `Invalid birth day (month ${m} has at most ${maxDays} days).`,
      normalized: null,
    };
  }

  // Future date check
  const now = new Date();
  const inputDate = new Date(y, m - 1, d);
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());

  if (inputDate > today) {
    return {
      isValid: false,
      error: vi ? "Ngày sinh không được ở tương lai." : "Date of birth cannot be in the future.",
      normalized: null,
    };
  }

  const normalized = `${match[1]}-${match[2]}-${match[3]}`;
  return { isValid: true, normalized };
}

function ProfileContent({ profile }: { profile: UserProfile }) {
  const { t, i18n } = useTranslation();
  const vi = i18n.resolvedLanguage?.startsWith("vi");
  const notUpdated = t("profile.unavailable");
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [editMode, setEditMode] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState("");

  // Form states
  const [name, setName] = useState(profile.displayName ?? "");
  const [birthDate, setBirthDate] = useState(formatDisplayDate(profile.dateOfBirth));
  const [phone, setPhone] = useState(profile.phoneNumber ?? "");
  const [address, setAddress] = useState(profile.address ?? "");
  const [provinceCode, setProvinceCode] = useState("");
  const [wardCode, setWardCode] = useState("");
  const [street, setStreet] = useState("");

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
  const prefilledProvinceForRef = useRef<string | null>(null);
  const prefilledWardForRef = useRef<string | null>(null);

  useEffect(() => {
    setName(profile.displayName ?? "");
    setBirthDate(formatDisplayDate(profile.dateOfBirth));
    setPhone(profile.phoneNumber ?? "");
    setAddress(profile.address ?? "");
    prefilledProvinceForRef.current = null;
    prefilledWardForRef.current = null;
  }, [profile]);

  useEffect(() => {
    if (!editMode || !profile.address || !provinces.data?.length) return;
    if (prefilledProvinceForRef.current === profile.address || provinceCode) return;

    const parts = profile.address.split(",").map(part => part.trim()).filter(Boolean);
    if (!parts.length) return;
    const provinceCandidate = parts[parts.length - 1].toLowerCase();
    const matchedProvince = provinces.data.find(item => item.ten_day_du.toLowerCase() === provinceCandidate);
    prefilledProvinceForRef.current = profile.address;
    if (matchedProvince) {
      setProvinceCode(matchedProvince.ma_tinh);
    }
  }, [editMode, profile.address, provinces.data, provinceCode]);

  useEffect(() => {
    if (!editMode || !profile.address || !provinceCode || !wards.data?.length) return;
    if (prefilledWardForRef.current === `${profile.address}|${provinceCode}` || wardCode) return;

    const parts = profile.address.split(",").map(part => part.trim()).filter(Boolean);
    if (parts.length < 2) return;
    const wardCandidate = parts[parts.length - 2].toLowerCase();
    const matchedWard = wards.data.find(
      item => item.ma_tinh === provinceCode && item.ten.toLowerCase() === wardCandidate
    );
    prefilledWardForRef.current = `${profile.address}|${provinceCode}`;
    if (matchedWard) {
      setWardCode(matchedWard.ma_xa);
      const streetPart = parts.slice(0, -2).join(", ");
      if (streetPart && !street) {
        setStreet(streetPart);
      }
    }
  }, [editMode, profile.address, provinceCode, wards.data, wardCode, street]);

  useEffect(() => {
    const handlePointerDown = (event: PointerEvent) => {
      document.querySelectorAll<HTMLDetailsElement>(".profile-dropdown[open]").forEach(menu => {
        if (!menu.contains(event.target as Node)) {
          menu.open = false;
        }
      });
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        const openMenu = document.querySelector<HTMLDetailsElement>(".profile-dropdown[open]");
        if (openMenu) {
          openMenu.open = false;
          openMenu.querySelector("summary")?.focus();
        }
      }
    };

    const handleScroll = () => {
      document.querySelectorAll<HTMLDetailsElement>(".profile-dropdown[open]").forEach(menu => {
        menu.open = false;
      });
    };

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    window.addEventListener("scroll", handleScroll, { passive: true });

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("scroll", handleScroll);
    };
  }, []);

  const trimmedName = name.trim();
  const nameChanged = trimmedName !== (profile.displayName ?? "");
  const dobChanged = birthDate.trim() !== formatDisplayDate(profile.dateOfBirth);
  const phoneClean = phone.trim().replace(/[\s.-]/g, "");
  const profilePhoneClean = (profile.phoneNumber ?? "").replace(/[\s.-]/g, "");
  const phoneChanged = phoneClean !== profilePhoneClean;

  const hasAddressDraft = Boolean(provinceCode || wardCode || street.trim());
  const hasCompleteAddress = Boolean(province && ward);
  const nextAddress = hasCompleteAddress
    ? [street.trim(), ward!.ten, province!.ten_day_du].filter(Boolean).join(", ")
    : address;
  const addressChanged = hasCompleteAddress
    ? nextAddress !== (profile.address ?? "")
    : hasAddressDraft;

  const hasChanges = nameChanged || dobChanged || phoneChanged || addressChanged;
  const canSave = !saving && !uploading && (!nameChanged || Boolean(trimmedName)) && hasChanges;

  const handleDobChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const input = e.target;
    const rawVal = input.value;
    const rawPos = input.selectionStart ?? rawVal.length;
    const isDeleting = rawVal.length < birthDate.length;

    const digits = rawVal.replace(/\D/g, "").slice(0, 8);
    const formatted = formatProgressiveDate(digits, isDeleting);
    const nextPos = calculateCursorPos(rawVal, rawPos, formatted, isDeleting);

    setBirthDate(formatted);
    setErrorMsg("");

    requestAnimationFrame(() => {
      input.setSelectionRange(nextPos, nextPos);
    });
  };

  const handleDobKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    const input = e.currentTarget;
    const { selectionStart, selectionEnd, value } = input;

    if (e.key === "Backspace") {
      if (selectionStart === selectionEnd && selectionStart !== null) {
        if (selectionStart > 0 && value[selectionStart - 1] === "/") {
          e.preventDefault();
          const before = value.slice(0, selectionStart - 2);
          const after = value.slice(selectionStart);
          const nextVal = before + after;
          const digits = nextVal.replace(/\D/g, "").slice(0, 8);
          const formatted = formatProgressiveDate(digits, true);
          const newCursor = Math.max(0, selectionStart - 2);
          setBirthDate(formatted);
          setErrorMsg("");
          requestAnimationFrame(() => {
            input.setSelectionRange(newCursor, newCursor);
          });
          return;
        }
      }
    } else if (e.key === "Delete") {
      if (selectionStart === selectionEnd && selectionStart !== null) {
        if (selectionStart < value.length && value[selectionStart] === "/") {
          e.preventDefault();
          const before = value.slice(0, selectionStart);
          const after = value.slice(selectionStart + 2);
          const nextVal = before + after;
          const digits = nextVal.replace(/\D/g, "").slice(0, 8);
          const formatted = formatProgressiveDate(digits, true);
          setBirthDate(formatted);
          setErrorMsg("");
          requestAnimationFrame(() => {
            input.setSelectionRange(selectionStart, selectionStart);
          });
          return;
        }
      }
    } else if (e.key === "/") {
      if (selectionStart === selectionEnd && selectionStart !== null) {
        if (selectionStart === 1 && /^\d$/.test(value)) {
          e.preventDefault();
          const formatted = `0${value}/`;
          setBirthDate(formatted);
          setErrorMsg("");
          requestAnimationFrame(() => {
            input.setSelectionRange(3, 3);
          });
          return;
        }
        if (selectionStart === 4 && /^\d{2}\/\d$/.test(value)) {
          e.preventDefault();
          const formatted = `${value.slice(0, 3)}0${value.slice(3)}/`;
          setBirthDate(formatted);
          setErrorMsg("");
          requestAnimationFrame(() => {
            input.setSelectionRange(6, 6);
          });
          return;
        }
        if (selectionStart < value.length && value[selectionStart] === "/") {
          e.preventDefault();
          input.setSelectionRange(selectionStart + 1, selectionStart + 1);
          return;
        }
      }
    }
  };

  const handleDobPaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    const pasted = e.clipboardData.getData("text").trim();
    const isoMatch = pasted.match(/^(\d{4})[-/.](\d{2})[-/.](\d{2})$/);
    if (isoMatch) {
      e.preventDefault();
      const [, y, m, d] = isoMatch;
      const formatted = `${d}/${m}/${y}`;
      setBirthDate(formatted);
      setErrorMsg("");
      requestAnimationFrame(() => {
        e.currentTarget.setSelectionRange(formatted.length, formatted.length);
      });
      return;
    }
  };

  async function handleAvatarChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    if (!["image/jpeg", "image/png"].includes(file.type)) {
      toast.error(vi ? "Chỉ hỗ trợ ảnh định dạng JPEG hoặc PNG." : "Only JPEG or PNG images are supported.");
      return;
    }

    if (file.size > 2 * 1024 * 1024) {
      toast.error(vi ? "Kích thước ảnh tối đa là 2 MB." : "Maximum image size is 2 MB.");
      return;
    }

    setUploading(true);
    try {
      const updated = await userService.uploadAvatar(file);
      queryClient.setQueriesData<UserProfile | null>({ queryKey: USER_PROFILE_QUERY_KEY }, updated);
      await queryClient.invalidateQueries({ queryKey: USER_PROFILE_QUERY_KEY });
      window.dispatchEvent(new Event(PROFILE_UPDATED_EVENT));
      toast.success(vi ? "Đã cập nhật ảnh đại diện thành công." : "Profile photo updated successfully.");
    } catch {
      toast.error(vi ? "Không tải được ảnh đại diện. Vui lòng thử lại sau." : "Avatar upload failed. Please try again.");
    } finally {
      setUploading(false);
    }
  }

  function handleCancel() {
    setName(profile.displayName ?? "");
    setBirthDate(formatDisplayDate(profile.dateOfBirth));
    setPhone(profile.phoneNumber ?? "");
    setAddress(profile.address ?? "");
    setProvinceCode("");
    setWardCode("");
    setStreet("");
    prefilledProvinceForRef.current = null;
    prefilledWardForRef.current = null;
    setErrorMsg("");
    setEditMode(false);
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSave) return;

    setErrorMsg("");

    // Validate phone if present
    if (phoneClean) {
      const phoneRegex = /^(?:0|\+84)(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9])[0-9]{7}$/;
      if (!phoneRegex.test(phoneClean)) {
        setErrorMsg(vi ? "Số điện thoại không đúng định dạng di động Việt Nam (ví dụ: 0912345678 hoặc +84912345678)." : "Invalid Vietnamese mobile number format.");
        return;
      }
    }

    // Validate date of birth if present
    const dobResult = validateDateOfBirth(birthDate, Boolean(vi));
    if (!dobResult.isValid) {
      setErrorMsg(dobResult.error || (vi ? "Ngày sinh không hợp lệ." : "Invalid date of birth."));
      return;
    }

    // Validate address completeness when user modifies any address field
    if (hasAddressDraft && !hasCompleteAddress) {
      setErrorMsg(
        vi
          ? "Vui lòng chọn đầy đủ Tỉnh / Thành phố và Xã / Phường trước khi lưu địa chỉ."
          : "Please select both Province / City and Commune / Ward before saving your address."
      );
      return;
    }

    const changes: Partial<Pick<UserProfile, "displayName" | "dateOfBirth" | "address" | "phoneNumber">> = {};
    if (nameChanged) changes.displayName = trimmedName;
    if (dobChanged) changes.dateOfBirth = dobResult.normalized;
    if (phoneChanged) changes.phoneNumber = phoneClean || null;
    if (addressChanged && hasCompleteAddress) changes.address = nextAddress || null;

    setSaving(true);
    try {
      const updated = await userService.updateProfile(changes);
      queryClient.setQueriesData<UserProfile | null>({ queryKey: USER_PROFILE_QUERY_KEY }, updated);
      setName(updated.displayName ?? "");
      setBirthDate(formatDisplayDate(updated.dateOfBirth));
      setPhone(updated.phoneNumber ?? "");
      setAddress(updated.address ?? "");
      setProvinceCode("");
      setWardCode("");
      setStreet("");
      await queryClient.invalidateQueries({ queryKey: USER_PROFILE_QUERY_KEY });
      window.dispatchEvent(new Event(PROFILE_UPDATED_EVENT));
      toast.success(vi ? "Cập nhật hồ sơ thành công." : "Profile updated successfully.");
      setEditMode(false);
    } catch {
      setErrorMsg(vi ? "Không thể cập nhật hồ sơ lúc này. Vui lòng kiểm tra lại." : "Could not update profile right now. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  const initials = getInitials(profile.displayName, profile.email);

  return (
    <>
      {/* 1. Account Identity Header Card */}
      <section className="profile-account-card" aria-label={vi ? "Nhận diện tài khoản" : "Account identity"}>
        <div className="profile-account-bg" aria-hidden="true">
          <svg viewBox="0 0 1024 128" fill="none" preserveAspectRatio="none">
            <defs>
              <filter id="profileSoftBack" x="-10%" y="-10%" width="120%" height="120%">
                <feGaussianBlur stdDeviation="2.4" />
              </filter>
              <filter id="profileSoftFront" x="-10%" y="-10%" width="120%" height="120%">
                <feGaussianBlur stdDeviation="1.8" />
              </filter>
              <linearGradient id="waveBackGrad" x1="0%" y1="15%" x2="100%" y2="85%">
                <stop offset="0%" stopColor="#fff8ec" stopOpacity="0" />
                <stop offset="25%" stopColor="#fef1d6" stopOpacity="0.25" />
                <stop offset="65%" stopColor="#fde7b6" stopOpacity="0.45" />
                <stop offset="100%" stopColor="#fde09e" stopOpacity="0.52" />
              </linearGradient>
              <linearGradient id="waveFrontGrad" x1="12%" y1="0%" x2="88%" y2="100%">
                <stop offset="0%" stopColor="#fef5e0" stopOpacity="0.18" />
                <stop offset="32%" stopColor="#fde4a6" stopOpacity="0.48" />
                <stop offset="68%" stopColor="#fed998" stopOpacity="0.58" />
                <stop offset="100%" stopColor="#fdd58c" stopOpacity="0.50" />
              </linearGradient>
            </defs>
            {/* 1. Upper wave: soft sweeping arch entering from top edge, curving down smoothly */}
            <path
              d="M 770 0 C 735 25, 710 52, 695 72 C 680 92, 670 114, 655 128 L 1024 128 L 1024 0 Z"
              fill="url(#waveBackGrad)"
              filter="url(#profileSoftBack)"
            />
            {/* 2. Lower undulating wave: flowing silk dune rising from bottom, cresting at 745, gentle dip under button, leveling to right */}
            <path
              d="M 565 128 C 622 128, 676 96, 745 80 C 795 68, 835 86, 875 97 C 920 106, 965 93, 1024 91 L 1024 128 Z"
              fill="url(#waveFrontGrad)"
              filter="url(#profileSoftFront)"
            />
          </svg>
        </div>

        <div className="profile-account-identity">
          <div className="profile-account-avatar">
            {profile.avatarUrl ? (
              <img src={profile.avatarUrl} alt={profile.displayName || "Avatar"} />
            ) : (
              <span className="profile-account-avatar-initials">{initials}</span>
            )}
          </div>
          <div className="profile-account-info">
            <h2 className="profile-account-name">{profile.displayName || notUpdated}</h2>
            <p className="profile-account-email">{profile.email || notUpdated}</p>
          </div>
        </div>

        <div className="profile-account-action">
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png"
            style={{ display: "none" }}
            onChange={handleAvatarChange}
          />
          <button
            type="button"
            className="profile-change-avatar-btn"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading || saving}
          >
            <Camera size={15} aria-hidden="true" />
            <span>{uploading ? (vi ? "Đang tải…" : "Uploading…") : (vi ? "Thay ảnh" : "Change photo")}</span>
          </button>
        </div>
      </section>

      {/* 2. Profile Content Card (View Mode / Edit Mode) */}
      <section className="profile-content-card" aria-label={vi ? "Thông tin hồ sơ" : "Profile information"}>
        {!editMode ? (
          /* VIEW MODE */
          <div className="profile-view-container">
            <div className="profile-content-top-action">
              <button
                type="button"
                className="profile-edit-trigger-btn"
                onClick={() => setEditMode(true)}
              >
                <SquarePen size={15} aria-hidden="true" />
                <span>{vi ? "Chỉnh sửa" : "Edit"}</span>
              </button>
            </div>

            <h2 className="profile-section-heading">{vi ? "Thông tin cá nhân" : "Personal information"}</h2>
            <div className="profile-view-grid">
              <div className="profile-view-item">
                <span className="profile-view-label">{vi ? "Tên hiển thị" : "Display name"}</span>
                <span className={`profile-view-value ${!profile.displayName ? "profile-view-value--empty" : ""}`}>
                  {profile.displayName || notUpdated}
                </span>
              </div>

              <div className="profile-view-item">
                <span className="profile-view-label">Email</span>
                <span className={`profile-view-value ${!profile.email ? "profile-view-value--empty" : ""}`}>
                  {profile.email || notUpdated}
                </span>
              </div>

              <div className="profile-view-item">
                <span className="profile-view-label">{vi ? "Ngày sinh" : "Date of birth"}</span>
                <span className={`profile-view-value ${!profile.dateOfBirth ? "profile-view-value--empty" : ""}`}>
                  {formatDisplayDate(profile.dateOfBirth) || notUpdated}
                </span>
              </div>

              <div className="profile-view-item">
                <span className="profile-view-label">{vi ? "Số điện thoại" : "Phone number"}</span>
                <span className={`profile-view-value ${!profile.phoneNumber ? "profile-view-value--empty" : ""}`}>
                  {profile.phoneNumber || notUpdated}
                </span>
              </div>
            </div>

            <hr className="profile-divider" />

            <h2 className="profile-section-heading">{vi ? "Địa chỉ liên hệ" : "Contact address"}</h2>
            <div className="profile-view-grid">
              <div className="profile-view-item profile-view-item--full">
                <span className="profile-view-label">{vi ? "Địa chỉ" : "Address"}</span>
                <span className={`profile-view-value ${!profile.address ? "profile-view-value--empty" : ""}`}>
                  {profile.address || notUpdated}
                </span>
              </div>
            </div>
          </div>
        ) : (
          /* EDIT MODE */
          <form className="profile-edit-form" onSubmit={handleSave}>
            <h2 className="profile-section-heading">{vi ? "Thông tin cá nhân" : "Personal information"}</h2>
            <div className="profile-edit-grid">
              <div className="profile-field-group">
                <label htmlFor="profile-name">{vi ? "Tên hiển thị" : "Display name"}</label>
                <div className="profile-input-wrapper">
                  <input
                    id="profile-name"
                    type="text"
                    className="profile-input"
                    value={name}
                    onChange={e => { setName(e.target.value); setErrorMsg(""); }}
                    maxLength={200}
                    placeholder={vi ? "Nhập tên hiển thị" : "Enter display name"}
                    disabled={saving}
                  />
                </div>
              </div>

              <div className="profile-field-group">
                <label htmlFor="profile-email">Email</label>
                <div className="profile-input-wrapper profile-input-wrapper--readonly">
                  <input
                    id="profile-email"
                    type="email"
                    className="profile-input profile-input--readonly"
                    value={profile.email ?? ""}
                    readOnly
                    placeholder={notUpdated}
                  />
                  <Lock size={16} className="profile-input-trailing-icon" aria-hidden="true" />
                </div>
              </div>

              <div className="profile-field-group">
                <label htmlFor="profile-dob">{vi ? "Ngày sinh" : "Date of birth"}</label>
                <div className="profile-input-wrapper">
                  <Calendar size={16} className="profile-input-leading-icon" aria-hidden="true" />
                  <input
                    id="profile-dob"
                    type="text"
                    inputMode="numeric"
                    autoComplete="bday"
                    className="profile-input profile-input--with-leading-icon"
                    value={birthDate}
                    onChange={handleDobChange}
                    onKeyDown={handleDobKeyDown}
                    onPaste={handleDobPaste}
                    placeholder="DD/MM/YYYY"
                    maxLength={10}
                    disabled={saving}
                  />
                </div>
              </div>

              <div className="profile-field-group">
                <label htmlFor="profile-phone">{vi ? "Số điện thoại" : "Phone number"}</label>
                <div className="profile-input-wrapper">
                  <Phone size={16} className="profile-input-leading-icon" aria-hidden="true" />
                  <input
                    id="profile-phone"
                    type="tel"
                    className="profile-input profile-input--with-leading-icon"
                    value={phone}
                    onChange={e => { setPhone(e.target.value); setErrorMsg(""); }}
                    placeholder={vi ? "Nhập số điện thoại" : "Enter phone number"}
                    maxLength={20}
                    disabled={saving}
                  />
                </div>
              </div>
            </div>

            <hr className="profile-divider" />

            <h2 className="profile-section-heading">{vi ? "Địa chỉ liên hệ" : "Contact address"}</h2>
            <div className="profile-edit-grid">
              <div className="profile-field-group">
                <span id="profile-province-label" className="profile-field-label">{vi ? "Tỉnh / Thành phố" : "Province / City"}</span>
                <details className={`profile-dropdown ${saving ? "profile-dropdown--disabled" : ""}`}>
                  <summary
                    aria-labelledby="profile-province-label profile-province-value"
                    className="profile-dropdown-summary"
                    onClick={e => {
                      if (saving) e.preventDefault();
                    }}
                  >
                    <span id="profile-province-value" className={!province ? "profile-dropdown-placeholder" : ""}>
                      {provinces.isLoading
                        ? (vi ? "Đang tải tỉnh / thành phố…" : "Loading provinces…")
                        : province?.ten_day_du || (vi ? "Chọn tỉnh / thành phố" : "Select province / city")}
                    </span>
                    <ChevronDown className="profile-dropdown-chevron" size={16} aria-hidden="true" />
                  </summary>
                  {!saving && (
                    <div className="profile-dropdown-options" role="listbox">
                      <button
                        type="button"
                        role="option"
                        aria-selected={!provinceCode}
                        className="profile-dropdown-option"
                        onClick={e => {
                          setProvinceCode("");
                          setWardCode("");
                          setErrorMsg("");
                          const menu = e.currentTarget.closest("details");
                          if (menu) menu.open = false;
                          menu?.querySelector("summary")?.focus();
                        }}
                      >
                        {vi ? "Chọn tỉnh / thành phố" : "Select province / city"}
                      </button>
                      {provinces.data?.map(item => (
                        <button
                          key={item.ma_tinh}
                          type="button"
                          role="option"
                          aria-selected={provinceCode === item.ma_tinh}
                          className="profile-dropdown-option"
                          onClick={e => {
                            setProvinceCode(item.ma_tinh);
                            setWardCode("");
                            setErrorMsg("");
                            const menu = e.currentTarget.closest("details");
                            if (menu) menu.open = false;
                            menu?.querySelector("summary")?.focus();
                          }}
                        >
                          {item.ten_day_du}
                        </button>
                      ))}
                    </div>
                  )}
                </details>
                {provinces.isError && (
                  <p className="profile-form-error" role="alert">
                    {vi ? "Không tải được danh sách tỉnh / thành phố." : "Could not load provinces."}{" "}
                    <button
                      type="button"
                      className="underline font-medium"
                      onClick={() => void provinces.refetch()}
                    >
                      {t("profile.retry")}
                    </button>
                  </p>
                )}
              </div>

              <div className="profile-field-group">
                <span id="profile-ward-label" className="profile-field-label">{vi ? "Xã / Phường / Đặc khu" : "Commune / Ward"}</span>
                <details
                  className={`profile-dropdown ${!provinceCode || saving || !wards.data?.length ? "profile-dropdown--disabled" : ""}`}
                >
                  <summary
                    aria-labelledby="profile-ward-label profile-ward-value"
                    className="profile-dropdown-summary"
                    onClick={e => {
                      if (!provinceCode || saving || !wards.data?.length) {
                        e.preventDefault();
                      }
                    }}
                  >
                    <span id="profile-ward-value" className={!ward ? "profile-dropdown-placeholder" : ""}>
                      {wards.isLoading
                        ? (vi ? "Đang tải xã / phường…" : "Loading wards…")
                        : ward?.ten || (vi ? "Chọn xã / phường" : "Select ward")}
                    </span>
                    <ChevronDown className="profile-dropdown-chevron" size={16} aria-hidden="true" />
                  </summary>
                  {provinceCode && Boolean(wards.data?.length) && !saving && (
                    <div className="profile-dropdown-options" role="listbox">
                      <button
                        type="button"
                        role="option"
                        aria-selected={!wardCode}
                        className="profile-dropdown-option"
                        onClick={e => {
                          setWardCode("");
                          setErrorMsg("");
                          const menu = e.currentTarget.closest("details");
                          if (menu) menu.open = false;
                          menu?.querySelector("summary")?.focus();
                        }}
                      >
                        {vi ? "Chọn xã / phường" : "Select ward"}
                      </button>
                      {wards.data?.map(item => (
                        <button
                          key={item.ma_xa}
                          type="button"
                          role="option"
                          aria-selected={wardCode === item.ma_xa}
                          className="profile-dropdown-option"
                          onClick={e => {
                            setWardCode(item.ma_xa);
                            setErrorMsg("");
                            const menu = e.currentTarget.closest("details");
                            if (menu) menu.open = false;
                            menu?.querySelector("summary")?.focus();
                          }}
                        >
                          {item.ten}
                        </button>
                      ))}
                    </div>
                  )}
                </details>
                {wards.isError && (
                  <p className="profile-form-error" role="alert">
                    {vi ? "Không tải được danh sách xã / phường." : "Could not load wards."}{" "}
                    <button
                      type="button"
                      className="underline font-medium"
                      onClick={() => void wards.refetch()}
                    >
                      {t("profile.retry")}
                    </button>
                  </p>
                )}
              </div>

              <div className="profile-field-group profile-field-group--full">
                <label htmlFor="profile-street">{vi ? "Số nhà, tên đường" : "Street address"}</label>
                <div className="profile-input-wrapper">
                  <Home size={16} className="profile-input-leading-icon" aria-hidden="true" />
                  <input
                    id="profile-street"
                    type="text"
                    className="profile-input profile-input--with-leading-icon"
                    value={street}
                    onChange={e => { setStreet(e.target.value); setErrorMsg(""); }}
                    placeholder={vi ? "Nhập địa chỉ chi tiết" : "Enter street address"}
                    maxLength={350}
                    disabled={saving}
                  />
                </div>
                {address && !provinceCode && (
                  <p className="profile-current-address-hint">
                    {vi ? "Địa chỉ hiện tại: " : "Current address: "}
                    <strong>{address}</strong>
                  </p>
                )}
              </div>
            </div>

            {errorMsg && (
              <p className="profile-form-error" role="alert">
                {errorMsg}
              </p>
            )}

            <div className="profile-form-actions">
              <button
                type="button"
                className="profile-btn profile-btn--cancel"
                onClick={handleCancel}
                disabled={saving}
              >
                {vi ? "Hủy thay đổi" : "Cancel"}
              </button>
              <button
                type="submit"
                className="profile-btn profile-btn--save"
                disabled={!canSave}
              >
                {saving ? (vi ? "Đang lưu…" : "Saving…") : (vi ? "Lưu thay đổi" : "Save changes")}
              </button>
            </div>
          </form>
        )}
      </section>
    </>
  );
}

export default function ProfilePage() {
  const { t, i18n } = useTranslation();
  const vi = i18n.resolvedLanguage !== "en";
  const { keycloak, initialized } = useKeycloak();
  const { data: profile, isLoading, isError, error, refetch } = useUserProfile();
  const errorStatus = axios.isAxiosError(error) ? error.response?.status : undefined;

  return (
    <div className="profile-page">
      <div className="container account-layout-container">
        <AccountSidebar />
        <main className="account-main-content">
          <h1 className="profile-page-title">{t("profile.title")}</h1>

          {!initialized || isLoading ? (
            <div className="profile-loading" role="status">
              <div className="loading-spinner" aria-hidden="true" />
              <span>{t("profile.loading")}…</span>
            </div>
          ) : !keycloak.authenticated ? (
            <section className="profile-content-card">
              <p>{t("profile.loginRequired")}</p>
              <button
                type="button"
                className="profile-btn profile-btn--save"
                onClick={() => void keycloak.login({ redirectUri: window.location.href })}
              >
                {t("auth.signIn")}
              </button>
            </section>
          ) : isError || !profile ? (
            <section className="profile-content-card">
              {errorStatus === 401 ? (
                <>
                  <p role="alert">
                    {vi
                      ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để xem hồ sơ."
                      : "Your session has expired. Please sign in again to view your profile."}
                  </p>
                  <button
                    type="button"
                    className="profile-btn profile-btn--save"
                    onClick={() => void keycloak.login({ redirectUri: window.location.href })}
                  >
                    {t("auth.signIn")}
                  </button>
                </>
              ) : errorStatus === 403 ? (
                <>
                  <p role="alert">
                    {vi
                      ? "Tài khoản của bạn không có quyền truy cập hồ sơ này."
                      : "Your account does not have permission to access this profile."}
                  </p>
                  <button type="button" className="profile-btn profile-btn--cancel" onClick={() => void refetch()}>
                    {t("profile.retry")}
                  </button>
                </>
              ) : (
                <>
                  <p role="alert">{t("profile.loadFailed")}</p>
                  <button type="button" className="profile-btn profile-btn--cancel" onClick={() => void refetch()}>
                    {t("profile.retry")}
                  </button>
                </>
              )}
            </section>
          ) : (
            <ProfileContent key={profile.userId || keycloak.subject} profile={profile} />
          )}
        </main>
      </div>
      <Footer />
    </div>
  );
}
