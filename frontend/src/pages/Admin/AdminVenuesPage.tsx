import { ChangeEvent, FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useKeycloak } from "@react-keycloak/web";
import { isAxiosError } from "axios";
import {
  Building2,
  Camera,
  Check,
  Copy,
  ExternalLink,
  ImagePlus,
  LoaderCircle,
  MapPin,
  Plus,
  Search,
  ShieldAlert,
  X,
} from "lucide-react";
import { toast } from "react-toastify";
import { useTranslation } from "react-i18next";
import { hasRealmRole } from "../../lib/auth";
import { venueService } from "../../services/venueService";
import { Venue } from "../../types/api";
import TixAdminTopNav from "../../components/admin/TixAdminTopNav";
import "../Organizer/organizer-dashboard.css";
import "./admin-tix.css";
import "./admin-venues.css";

interface VenueFormState {
  name: string;
  address: string;
  description: string;
  googleMapsUrl: string;
}

const emptyForm: VenueFormState = {
  name: "",
  address: "",
  description: "",
  googleMapsUrl: "",
};

function isValidGoogleMapsUrl(value: string): boolean {
  const clean = value.trim();
  if (!clean) return true;
  if (clean.length > 500) return false;
  try {
    const uri = new URL(clean);
    if (uri.protocol !== "https:" || uri.username || uri.password || uri.port) {
      return false;
    }
    const host = uri.hostname.toLowerCase();
    const path = uri.pathname;
    if (host === "maps.app.goo.gl") return path.length > 1;
    if (host === "maps.google.com" || host === "maps.google.com.vn") return true;
    return host === "www.google.com" && (path === "/maps" || path.startsWith("/maps/"));
  } catch {
    return false;
  }
}

function getApiErrorMessage(error: unknown, isVi: boolean): string {
  if (isAxiosError(error)) {
    const data = error.response?.data as
      | { code?: string; message?: string; violations?: { field?: string; code?: string }[] }
      | undefined;
    const violation = data?.violations?.[0];
    const field = violation?.field;
    const code = violation?.code ?? data?.code;
    if (field === "googleMapsUrl") {
      return isVi
        ? "Link Google Maps phải dùng HTTPS và thuộc domain google.com/maps, maps.google.com hoặc maps.app.goo.gl."
        : "Google Maps URL must use HTTPS and belong to google.com/maps, maps.google.com, or maps.app.goo.gl.";
    }
    if (code === "INVALID_VENUE_IMAGE_RATIO") {
      return isVi
        ? "Ảnh địa điểm phải có đúng tỷ lệ 16:9 (ví dụ: 1280×720, 1600×900, 1920×1080)."
        : "Venue image must have an exact 16:9 aspect ratio (e.g., 1280x720, 1600x900, 1920x1080).";
    }
    if (code === "IMAGE_TOO_LARGE") {
      return isVi
        ? "Ảnh vượt quá dung lượng tối đa 5MB hoặc độ phân giải cho phép."
        : "Image exceeds the 5MB limit or maximum allowed resolution.";
    }
    if (code === "UNSUPPORTED_IMAGE") {
      return isVi
        ? "Định dạng ảnh không được hỗ trợ. Chỉ chấp nhận PNG, JPG hoặc WEBP."
        : "Unsupported image format. Only PNG, JPG, or WEBP are allowed.";
    }
    if (field === "image") {
      return isVi
        ? "Ảnh tải lên không hợp lệ hoặc không đúng định dạng PNG, JPG, WEBP."
        : "Uploaded image is invalid or not a valid PNG, JPG, or WEBP file.";
    }
    if (data?.message) return data.message;
  }
  return isVi ? "Không thể lưu thông tin địa điểm. Vui lòng thử lại." : "Could not save venue. Please try again.";
}

function VenueManagementWorkspace() {
  const { i18n } = useTranslation();
  const queryClient = useQueryClient();
  const isVi = !i18n.resolvedLanguage?.startsWith("en");
  const copyText = (vi: string, en: string) => (isVi ? vi : en);

  const [mode, setMode] = useState<"edit" | "create">("edit");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [form, setForm] = useState<VenueFormState>(emptyForm);
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const venuesQuery = useQuery<Venue[], Error>({
    queryKey: ["venues"],
    queryFn: venueService.getVenues,
  });

  const venues = venuesQuery.data ?? [];

  const filteredVenues = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return venues;
    return venues.filter(
      (venue) =>
        venue.name.toLowerCase().includes(q) ||
        venue.address.toLowerCase().includes(q) ||
        venue.id.toLowerCase().includes(q),
    );
  }, [venues, searchQuery]);

  const selectedVenue = useMemo(
    () => (mode === "edit" ? venues.find((v) => v.id === selectedId) ?? filteredVenues[0] ?? null : null),
    [mode, venues, selectedId, filteredVenues],
  );

  useEffect(() => {
    if (mode === "edit" && selectedVenue && selectedVenue.id !== selectedId) {
      setSelectedId(selectedVenue.id);
    }
  }, [mode, selectedVenue, selectedId]);

  useEffect(() => {
    if (mode === "create") {
      setForm(emptyForm);
      setImageFile(null);
      setFormError(null);
      return;
    }
    if (selectedVenue) {
      setForm({
        name: selectedVenue.name ?? "",
        address: selectedVenue.address ?? "",
        description: selectedVenue.description ?? "",
        googleMapsUrl: selectedVenue.googleMapsUrl ?? "",
      });
      setImageFile(null);
      setFormError(null);
    }
  }, [mode, selectedVenue]);

  useEffect(() => {
    if (!imageFile) {
      setPreviewUrl(null);
      return;
    }
    const url = URL.createObjectURL(imageFile);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [imageFile]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        name: form.name,
        address: form.address,
        description: form.description,
        googleMapsUrl: form.googleMapsUrl,
        image: imageFile,
      };
      if (mode === "create") {
        return venueService.createAdminVenue(payload);
      }
      if (!selectedVenue) {
        throw new Error(copyText("Chưa chọn địa điểm.", "No venue selected."));
      }
      return venueService.updateAdminVenue(selectedVenue.id, payload);
    },
    onSuccess: async (savedVenue) => {
      await queryClient.invalidateQueries({ queryKey: ["venues"] });
      setImageFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
      setMode("edit");
      setSelectedId(savedVenue.id);
      toast.success(
        mode === "create"
          ? copyText("Đã tạo địa điểm mới.", "Venue created.")
          : copyText("Đã cập nhật địa điểm.", "Venue updated."),
      );
    },
    onError: (error) => {
      const msg = getApiErrorMessage(error, isVi);
      setFormError(msg);
      toast.error(msg);
    },
  });

  const handleSelectVenue = (venue: Venue) => {
    setMode("edit");
    setSelectedId(venue.id);
    setImageFile(null);
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const handleStartCreate = () => {
    setMode("create");
    setSelectedId(null);
    setForm(emptyForm);
    setImageFile(null);
    setFormError(null);
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const handleImageChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith("image/")) {
      setFormError(copyText("Vui lòng chọn tệp hình ảnh hợp lệ.", "Please select a valid image file."));
      return;
    }
    setFormError(null);
    setImageFile(file);
  };

  const handleCopyId = async (id: string) => {
    try {
      await navigator.clipboard.writeText(id);
      setCopiedId(id);
      window.setTimeout(() => setCopiedId((prev) => (prev === id ? null : prev)), 1500);
    } catch {
      toast.error(copyText("Không thể sao chép ID.", "Could not copy ID."));
    }
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFormError(null);
    const cleanName = form.name.trim();
    const cleanAddress = form.address.trim();
    if (!cleanName || cleanName.length > 255) {
      setFormError(
        copyText("Tên địa điểm không được để trống và tối đa 255 ký tự.", "Venue name is required (max 255 chars)."),
      );
      return;
    }
    if (!cleanAddress || cleanAddress.length > 500) {
      setFormError(
        copyText("Địa chỉ không được để trống và tối đa 500 ký tự.", "Address is required (max 500 chars)."),
      );
      return;
    }
    if (form.description.trim().length > 2000) {
      setFormError(copyText("Mô tả tối đa 2000 ký tự.", "Description cannot exceed 2000 characters."));
      return;
    }
    if (!isValidGoogleMapsUrl(form.googleMapsUrl)) {
      setFormError(
        copyText(
          "Link Google Maps phải dùng HTTPS (ví dụ: https://maps.app.goo.gl/... hoặc https://www.google.com/maps/...).",
          "Google Maps URL must be HTTPS (e.g., https://maps.app.goo.gl/... or https://www.google.com/maps/...).",
        ),
      );
      return;
    }
    saveMutation.mutate();
  };

  const activeImageSrc = previewUrl || (mode === "edit" ? selectedVenue?.imageUrl ?? null : null);

  return (
    <div className="tix-dashboard-page tix-admin-page">
      <div className="tix-dashboard-container">
        <TixAdminTopNav activeTab="venues" />

        <div className="tix-overview-header">
          <div className="tix-overview-header-left">
            <div className="tix-overview-title-block">
              <h1 className="tix-overview-main-title">
                {copyText("Quản lý địa điểm", "Venue Management")}
              </h1>
              <p className="tix-overview-subtitle">
                {copyText(
                  "Tạo mới và cập nhật thông tin địa điểm tổ chức sự kiện, hình ảnh và liên kết bản đồ.",
                  "Create and update event venues, cover images, and Google Maps links.",
                )}
              </p>
            </div>
          </div>
          <div className="tix-overview-header-actions">
            <button
              type="button"
              className="tix-admin-btn tix-admin-btn-primary"
              onClick={handleStartCreate}
            >
              <Plus size={16} aria-hidden="true" />
              <span>{copyText("Thêm địa điểm", "Add Venue")}</span>
            </button>
          </div>
        </div>

        <div className="tix-subtabs-row">
          <div className="tix-search-input-wrap" style={{ maxWidth: 420, width: "100%" }}>
            <Search size={15} aria-hidden="true" />
            <input
              type="search"
              className="tix-search-input"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder={copyText("Tìm theo tên địa điểm, địa chỉ, ID...", "Search by venue name, address, ID...")}
              aria-label={copyText("Tìm kiếm địa điểm", "Search venues")}
            />
            {searchQuery && (
              <button
                type="button"
                className="tix-search-clear-btn"
                onClick={() => setSearchQuery("")}
                aria-label={copyText("Xóa từ khóa tìm kiếm", "Clear search")}
              >
                <X size={13} />
              </button>
            )}
          </div>
        </div>

        <div className="tix-admin-split-grid">
          {/* Left column: Venue list */}
          <section className="tix-orders-card" aria-label={copyText("Danh sách địa điểm", "Venue list")}>
            <div className="tix-card-head">
              <div>
                <h2 className="tix-card-title">{copyText("Danh sách địa điểm", "Venues")}</h2>
                <p className="tix-card-subtitle">
                  {copyText(`${filteredVenues.length} địa điểm`, `${filteredVenues.length} venues`)}
                </p>
              </div>
            </div>

            {venuesQuery.isLoading ? (
              <div className="tix-admin-state-box" role="status">
                <LoaderCircle size={24} className="tix-spin" aria-hidden="true" />
                <span>{copyText("Đang tải danh sách địa điểm...", "Loading venues...")}</span>
              </div>
            ) : venuesQuery.isError ? (
              <div className="tix-admin-state-box" role="alert">
                <span>{venuesQuery.error?.message || copyText("Không tải được địa điểm.", "Failed to load venues.")}</span>
              </div>
            ) : filteredVenues.length === 0 ? (
              <div className="tix-admin-state-box">
                <MapPin size={26} color="var(--text-muted, #666b74)" aria-hidden="true" />
                <span>{copyText("Không có địa điểm phù hợp.", "No matching venues found.")}</span>
              </div>
            ) : (
              <div className="tix-admin-list">
                {filteredVenues.map((venue) => {
                  const isActive = mode === "edit" && selectedVenue?.id === venue.id;
                  return (
                    <button
                      key={venue.id}
                      type="button"
                      className={`tix-admin-list-item ${isActive ? "is-selected" : ""}`}
                      onClick={() => handleSelectVenue(venue)}
                      aria-pressed={isActive}
                    >
                      <div className="tix-admin-venues-item-row">
                        <div className="tix-admin-venues-thumb">
                          {venue.imageUrl ? (
                            <img src={venue.imageUrl} alt="" />
                          ) : (
                            <Building2 size={20} aria-hidden="true" />
                          )}
                        </div>
                        <div className="tix-admin-venues-item-copy">
                          <div className="tix-admin-list-item-top">
                            <span className="tix-admin-list-item-title">{venue.name}</span>
                            {venue.googleMapsUrl && (
                              <span className="tix-status-badge is-active" style={{ flexShrink: 0 }}>
                                Maps
                              </span>
                            )}
                          </div>
                          <span className="tix-admin-list-item-meta">{venue.address}</span>
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </section>

          {/* Right column: Create / Edit Form */}
          <section
            className="tix-orders-card"
            aria-label={mode === "create" ? copyText("Thêm địa điểm mới", "Create venue") : copyText("Chi tiết địa điểm", "Venue details")}
          >
            <div className="tix-card-head tix-admin-venues-card-head">
              <div className="tix-admin-venues-card-head-copy">
                <h2 className="tix-card-title">
                  {mode === "create"
                    ? copyText("Thêm địa điểm mới", "Add New Venue")
                    : selectedVenue
                    ? selectedVenue.name
                    : copyText("Chi tiết địa điểm", "Venue Details")}
                </h2>
                <p className="tix-card-subtitle">
                  {mode === "create"
                    ? copyText("Nhập thông tin địa điểm để tạo mới trên hệ thống.", "Fill in venue details to publish it for events.")
                    : selectedVenue
                    ? copyText("Chỉnh sửa thông tin và ảnh đại diện của địa điểm.", "Update venue details and cover image.")
                    : copyText("Chọn một địa điểm ở danh sách bên trái hoặc bấm Thêm địa điểm.", "Select a venue on the left or click Add Venue.")}
                </p>
              </div>
              {mode === "edit" && selectedVenue && (
                <button
                  type="button"
                  className="tix-admin-btn tix-admin-btn-secondary tix-admin-venues-copy-btn"
                  onClick={() => void handleCopyId(selectedVenue.id)}
                  title={selectedVenue.id}
                >
                  {copiedId === selectedVenue.id ? <Check size={14} /> : <Copy size={14} />}
                  <span>{copiedId === selectedVenue.id ? copyText("Đã chép ID", "Copied ID") : copyText("Chép ID", "Copy ID")}</span>
                </button>
              )}
            </div>

            {mode === "edit" && !selectedVenue ? (
              <div className="tix-admin-state-box">
                <MapPin size={28} color="var(--text-muted, #666b74)" aria-hidden="true" />
                <span>{copyText("Chọn một địa điểm để xem và chỉnh sửa.", "Select a venue to view and edit.")}</span>
              </div>
            ) : (
              <form className="tix-admin-venues-form" onSubmit={handleSubmit}>
                <div className="tix-admin-venues-image-box">
                  <div
                    className={`tix-admin-venues-preview ${activeImageSrc ? "has-image" : ""}`}
                    role="button"
                    tabIndex={0}
                    aria-label={
                      activeImageSrc
                        ? copyText("Thay ảnh địa điểm", "Replace venue image")
                        : copyText("Tải ảnh địa điểm", "Upload venue image")
                    }
                    onClick={() => fileInputRef.current?.click()}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        fileInputRef.current?.click();
                      }
                    }}
                  >
                    {activeImageSrc ? (
                      <img src={activeImageSrc} alt={form.name || "Venue preview"} />
                    ) : (
                      <div className="tix-admin-venues-preview-empty">
                        <ImagePlus size={26} aria-hidden="true" />
                        <span>{copyText("Chưa có ảnh địa điểm", "No venue image yet")}</span>
                      </div>
                    )}
                    <div className="tix-admin-venues-camera-overlay" aria-hidden="true">
                      <Camera size={28} strokeWidth={1.9} />
                    </div>
                  </div>
                  <input
                    ref={fileInputRef}
                    id="admin-venue-image-input"
                    type="file"
                    accept="image/*"
                    tabIndex={-1}
                    aria-hidden="true"
                    className="tix-admin-venues-file-input"
                    onChange={handleImageChange}
                  />
                  <div className="tix-admin-venues-hint">
                    <span>
                      {copyText(
                        "Hỗ trợ PNG, JPG, WEBP (tối đa 5MB), bắt buộc đúng tỷ lệ 16:9.",
                        "Supports PNG, JPG, WEBP (max 5MB), exact 16:9 aspect ratio required.",
                      )}
                    </span>
                  </div>
                  {imageFile && (
                    <div className="tix-admin-venues-image-actions">
                      <button
                        type="button"
                        className="tix-admin-btn tix-admin-btn-secondary"
                        onClick={() => {
                          setImageFile(null);
                          if (fileInputRef.current) fileInputRef.current.value = "";
                        }}
                      >
                        <X size={15} aria-hidden="true" />
                        <span>{copyText("Bỏ ảnh vừa chọn", "Clear selected")}</span>
                      </button>
                    </div>
                  )}
                </div>

                <div className="tix-admin-venues-field">
                  <label htmlFor="admin-venue-name">
                    {copyText("Tên địa điểm", "Venue name")} <span style={{ color: "#dc2626" }}>*</span>
                  </label>
                  <input
                    id="admin-venue-name"
                    type="text"
                    required
                    maxLength={255}
                    value={form.name}
                    onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                    placeholder={copyText("Ví dụ: Nhà hát Lớn Hà Nội", "e.g., Hanoi Opera House")}
                  />
                </div>

                <div className="tix-admin-venues-field">
                  <label htmlFor="admin-venue-address">
                    {copyText("Địa chỉ", "Address")} <span style={{ color: "#dc2626" }}>*</span>
                  </label>
                  <input
                    id="admin-venue-address"
                    type="text"
                    required
                    maxLength={500}
                    value={form.address}
                    onChange={(e) => setForm((prev) => ({ ...prev, address: e.target.value }))}
                    placeholder={copyText("Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành", "Street, district, city")}
                  />
                </div>

                <div className="tix-admin-venues-field">
                  <label htmlFor="admin-venue-maps-url">{copyText("Link Google Maps", "Google Maps URL")}</label>
                  <input
                    id="admin-venue-maps-url"
                    type="url"
                    maxLength={500}
                    value={form.googleMapsUrl}
                    onChange={(e) => setForm((prev) => ({ ...prev, googleMapsUrl: e.target.value }))}
                    placeholder="https://maps.app.goo.gl/... hoặc https://www.google.com/maps/..."
                  />
                  <div className="tix-admin-venues-hint">
                    <span>
                      {copyText(
                        "Tùy chọn. Hỗ trợ https://www.google.com/maps/..., maps.google.com hoặc maps.app.goo.gl.",
                        "Optional. Supports https://www.google.com/maps/..., maps.google.com, or maps.app.goo.gl.",
                      )}
                    </span>
                    {form.googleMapsUrl.trim() && isValidGoogleMapsUrl(form.googleMapsUrl) && (
                      <a
                        className="tix-admin-venues-maps-link"
                        href={form.googleMapsUrl.trim()}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        <span>{copyText("Mở bản đồ", "Open map")}</span>
                        <ExternalLink size={12} aria-hidden="true" />
                      </a>
                    )}
                  </div>
                </div>

                <div className="tix-admin-venues-field">
                  <label htmlFor="admin-venue-description">{copyText("Mô tả", "Description")}</label>
                  <textarea
                    id="admin-venue-description"
                    maxLength={2000}
                    value={form.description}
                    onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
                    placeholder={copyText("Thông tin thêm về sức chứa, bãi đỗ xe, tiện ích...", "Additional details about capacity, parking, facilities...")}
                  />
                  <div className="tix-admin-venues-hint">
                    <span>{copyText("Tùy chọn, tối đa 2000 ký tự.", "Optional, max 2000 characters.")}</span>
                    <span>{form.description.length}/2000</span>
                  </div>
                </div>

                {formError && (
                  <div className="tix-admin-venues-error" role="alert">
                    {formError}
                  </div>
                )}

                <div className="tix-admin-venues-actions">
                  {mode === "create" && venues.length > 0 && (
                    <button
                      type="button"
                      className="tix-admin-btn tix-admin-btn-secondary"
                      onClick={() => {
                        setMode("edit");
                        setSelectedId(venues[0].id);
                      }}
                      disabled={saveMutation.isPending}
                    >
                      {copyText("Hủy", "Cancel")}
                    </button>
                  )}
                  <button
                    type="submit"
                    className="tix-admin-btn tix-admin-btn-primary"
                    disabled={saveMutation.isPending}
                  >
                    {saveMutation.isPending && (
                      <LoaderCircle size={15} className="tix-spin" aria-hidden="true" />
                    )}
                    <span>
                      {saveMutation.isPending
                        ? copyText("Đang lưu...", "Saving...")
                        : mode === "create"
                        ? copyText("Tạo địa điểm", "Create Venue")
                        : copyText("Lưu thay đổi", "Save Changes")}
                    </span>
                  </button>
                </div>
              </form>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}

export default function AdminVenuesPage() {
  const { t } = useTranslation();
  const { keycloak, initialized } = useKeycloak();
  const subject = keycloak.tokenParsed?.sub;

  if (!initialized) {
    return (
      <div className="tix-dashboard-page tix-admin-page">
        <div className="tix-dashboard-container">
          <TixAdminTopNav activeTab="venues" />
          <div className="tix-orders-card tix-admin-state-box" role="status">
            <LoaderCircle size={26} className="tix-spin" aria-hidden="true" />
            <span>{t("adminOrganizer.loading")}</span>
          </div>
        </div>
      </div>
    );
  }

  if (!keycloak.authenticated || !subject || !hasRealmRole(keycloak.tokenParsed, "ADMIN")) {
    return (
      <div className="tix-dashboard-page tix-admin-page">
        <div className="tix-dashboard-container">
          <TixAdminTopNav activeTab="venues" />
          <div className="tix-orders-card tix-admin-state-box" role="alert">
            <ShieldAlert size={32} color="#dc2626" aria-hidden="true" />
            <strong style={{ color: "var(--text-strong, #15171a)" }}>
              {t("adminOrganizer.noAccessTitle", "Không có quyền truy cập")}
            </strong>
            <span>{t("adminOrganizer.noAccessDescription")}</span>
            {!keycloak.authenticated && (
              <button
                type="button"
                className="tix-admin-btn tix-admin-btn-primary"
                onClick={() => void keycloak.login()}
              >
                {t("auth.login", "Đăng nhập")}
              </button>
            )}
          </div>
        </div>
      </div>
    );
  }

  return <VenueManagementWorkspace />;
}
