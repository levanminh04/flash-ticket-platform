import { useRef, useState } from "react";
import { isAxiosError } from "axios";
import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { toast } from "react-toastify";
import { Inbox, Loader2, ArrowDown, ArrowUp } from "lucide-react";
import axiosClient from "../../lib/axiosClient";
import { confirmDestructiveAction } from "../../lib/swal";
import type { EventImageContract, EventImageRole } from "../../types/eventContract";
import { OrganizerSummary } from "../../services/organizerDashboardService";

export type EventWithImages = OrganizerSummary & {
  images: EventImageContract[];
};

const acceptedImages = "image/png,image/jpeg,image/webp";

type ImageAction =
  | { kind: "upload"; file: File; role: EventImageRole; imageId?: string }
  | { kind: "remove"; imageId: string }
  | { kind: "reorder"; imageId: string; displayOrder: number };

const imageErrors: Record<string, string> = {
  INVALID_BANNER_DIMENSIONS: "mediaBannerDimensions",
  BANNER_ALREADY_EXISTS: "mediaStaleVersion",
  INVALID_IMAGE: "mediaInvalidImage",
  UNSUPPORTED_IMAGE: "mediaInvalidImage",
  IMAGE_TOO_LARGE: "mediaTooLarge",
  IMAGE_STORAGE_UNAVAILABLE: "mediaStorageUnavailable",
  IMAGE_LIMIT_REACHED: "mediaLimitReached",
  STALE_EVENT_VERSION: "mediaStaleVersion",
  IMAGE_ORDER_CONFLICT: "mediaStaleVersion",
  IMAGE_NOT_FOUND: "mediaStaleVersion",
  INVALID_EVENT_STATE: "mediaStaleVersion",
};

export default function OrganizerDraftMedia<T extends EventWithImages>({
  event,
  onSaved,
  onRefresh,
  disabled = false,
  readOnly = false,
  onBusy,
}: {
  event: T;
  onSaved: (event: T) => void;
  onRefresh: () => void;
  disabled?: boolean;
  readOnly?: boolean;
  onBusy?: (busy: boolean) => void;
}) {
  const { t, i18n } = useTranslation();
  const inFlight = useRef(false);
  const [pendingTarget, setPendingTarget] = useState<string | null>(null);
  const banner = event.images.find(image => image.role === "BANNER");
  const gallery = event.images.filter(image => image.role === "GALLERY")
    .sort((a, b) => a.displayOrder - b.displayOrder);
  const path = "/api/v1/organizer/events/" + encodeURIComponent(event.eventId) + "/images";
  const isEn = i18n.resolvedLanguage === "en";

  const mutation = useMutation({
    mutationFn: async (action: ImageAction) => {
      const imagePath = action.imageId ? path + "/" + encodeURIComponent(action.imageId) : path;
      const rowVersion = event.rowVersion;
      if (action.kind === "remove") {
        const confirmed = await confirmDestructiveAction({
          title: t("organizerEventCreate.mediaDeleteTitle"),
          text: t("organizerEventCreate.mediaDeleteText"),
          confirmButtonText: t("organizerEventCreate.mediaDelete"),
          cancelButtonText: t("common.cancel", "Hủy"),
        });
        if (!confirmed) return;
        return (await axiosClient.delete<T>(imagePath, { params: { rowVersion } })).data;
      }
      if (action.kind === "reorder") {
        return (await axiosClient.patch<T>(imagePath, { rowVersion, displayOrder: action.displayOrder })).data;
      }
      if (action.role === "BANNER") {
        const preview = new Image();
        const url = URL.createObjectURL(action.file);
        try {
          preview.src = url;
          await preview.decode();
        } catch {
          throw new Error(t("organizerEventCreate.mediaInvalidImage"));
        } finally {
          URL.revokeObjectURL(url);
        }
        const { naturalWidth: width, naturalHeight: height } = preview;
        if (width < 1280 || height < 720 || width * 9 !== height * 16) {
          throw new Error(t("organizerEventCreate.mediaBannerDimensionsActual", { width, height }));
        }
      }
      const form = new FormData();
      form.append("file", action.file);
      form.append("rowVersion", rowVersion);
      const config = { headers: { "Content-Type": "multipart/form-data" } };
      if (action.imageId) return (await axiosClient.put<T>(imagePath, form, config)).data;
      form.append("role", action.role);
      if (action.role === "GALLERY")
        form.append("displayOrder", String((gallery[gallery.length - 1]?.displayOrder ?? -1) + 1));
      return (await axiosClient.post<T>(path, form, config)).data;
    },
    onSuccess: saved => { if (saved) onSaved(saved); },
    onError: problem => {
      const response = isAxiosError<{ code?: string }>(problem) ? problem.response : undefined;
      if (!isAxiosError(problem) && problem instanceof Error) {
        toast.error(problem.message);
        return;
      }
      const key = response?.status === 413 ? "mediaTooLarge" : imageErrors[response?.data?.code ?? ""];
      toast.error(t("organizerEventCreate." + (key ?? "mediaSaveError")));
      if (!key || key === "mediaStaleVersion") {
        onRefresh();
      }
    },
    onSettled: () => {
      inFlight.current = false;
      setPendingTarget(null);
      onBusy?.(false);
    },
  });
  const busy = disabled || readOnly || mutation.isPending;

  function run(action: ImageAction, target?: string) {
    if (busy || inFlight.current) return;
    inFlight.current = true;
    setPendingTarget(target ?? action.imageId ?? (action.kind === "upload" ? action.role : null));
    onBusy?.(true);
    mutation.mutate(action);
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>, role: EventImageRole, imageId?: string, target?: string) {
    e.stopPropagation();
    const file = e.currentTarget.files?.[0];
    e.currentTarget.value = "";
    if (!file || busy || inFlight.current) return;
    if (!file.size || !acceptedImages.split(",").includes(file.type)) {
      toast.error(t("organizerEventCreate.mediaInvalidImage"));
      return;
    }
    run({ kind: "upload", file, role, imageId }, target ?? imageId ?? role);
  }

  function imageInput(role: EventImageRole, imageId?: string) {
    return (
      <label className="tix-media-action-btn tix-media-replace">
        <span>{t(imageId ? "organizerEventCreate.mediaReplace" : role === "BANNER"
          ? "organizerEventCreate.mediaAddBanner" : "organizerEventCreate.addMorePhoto")}</span>
        <input type="file" className="tix-visually-hidden-input" accept={acceptedImages}
          disabled={busy} onChange={e => handleFileChange(e, role, imageId)} />
      </label>
    );
  }

  function removeButton(imageId: string) {
    return (
      <button type="button" className="tix-media-action-btn is-danger" disabled={busy}
        onClick={() => run({ kind: "remove", imageId })}>
        {t("organizerEventCreate.mediaDelete")}
      </button>
    );
  }

  function preview(image?: EventImageContract) {
    return <img className="tix-media-img" src={image?.objectReference || "/event-placeholder.svg"} alt=""
      onError={({ currentTarget }) => {
        if (!currentTarget.src.endsWith("/event-placeholder.svg")) currentTarget.src = "/event-placeholder.svg";
      }} />;
  }

  const loadingOverlay = (
    <div className="tix-media-slot-loading" role="status">
      <Loader2 size={22} className="tix-spin-icon" />
      <span>{t("organizerEventCreate.uploading")}</span>
    </div>
  );

  function renderGalleryDropzone(target: string, extraClass = "") {
    return (
      <label className={`tix-media-dropzone-slot is-gallery ${extraClass} ${busy ? "is-disabled" : ""}`}>
        <input
          type="file"
          className="tix-visually-hidden-input"
          accept={acceptedImages}
          disabled={busy}
          onChange={e => handleFileChange(e, "GALLERY", undefined, target)}
        />
        {mutation.isPending && pendingTarget === target ? (
          <div className="tix-media-uploading-state" role="status">
            <Loader2 size={24} className="tix-spin-icon" />
            <span>{t("organizerEventCreate.uploading")}</span>
          </div>
        ) : (
          <>
            <span className="tix-media-dropzone-icon">
              <Inbox size={26} strokeWidth={1.75} aria-hidden="true" />
            </span>
            <span className="tix-media-dropzone-title">
              {isEn
                ? "Add event gallery image for other placements"
                : "Thêm ảnh sự kiện để hiển thị ở các vị trí khác"}
            </span>
            <span className="tix-media-dropzone-spec">
              {isEn ? "3:4 ratio" : "Khung tỷ lệ 3:4"}
            </span>
          </>
        )}
      </label>
    );
  }

  const bannerDropLabel = (
    <label className={`tix-media-dropzone-slot is-banner ${busy ? "is-disabled" : ""}`}>
      <input
        type="file"
        className="tix-visually-hidden-input"
        accept={acceptedImages}
        disabled={busy}
        onChange={e => handleFileChange(e, "BANNER", undefined, "banner-drop")}
      />
      {mutation.isPending && pendingTarget === "banner-drop" ? (
        <div className="tix-media-uploading-state" role="status">
          <Loader2 size={24} className="tix-spin-icon" />
          <span>{t("organizerEventCreate.uploading")}</span>
        </div>
      ) : (
        <>
          <span className="tix-media-dropzone-icon">
            <Inbox size={26} strokeWidth={1.75} aria-hidden="true" />
          </span>
          <span className="tix-media-dropzone-title">
            {isEn ? "Add event banner image" : "Thêm ảnh nền sự kiện"}
          </span>
          <span className="tix-media-dropzone-spec">
            {isEn ? "16:9 ratio" : "Khung tỷ lệ 16:9"}
          </span>
        </>
      )}
    </label>
  );

  return (
    <div className="tix-media-manager">
      <div className="tix-media-workspace">
        {/* Dual Proportion Showcase Matching Reference Layout (Left 3:4 Gallery + Right 16:9 Banner) */}
        <section className="tix-media-showcase-grid">
          {/* Left Column: 3:4 Gallery Slot */}
          <div className="tix-media-slot-col">
            <div className="tix-media-slot-head">
              <span className="tix-media-slot-title">{t("organizerMedia.galleryPhotos")}</span>
            </div>
            {gallery.length > 0 ? (
              <div className="tix-media-slot-filled is-gallery">
                {preview(gallery[0])}
                {mutation.isPending && pendingTarget === gallery[0].imageId && loadingOverlay}
                {!readOnly && (
                  <div className="tix-media-slot-overlay">
                    {imageInput("GALLERY", gallery[0].imageId)}
                    {removeButton(gallery[0].imageId)}
                  </div>
                )}
              </div>
            ) : !readOnly ? (
              renderGalleryDropzone("gallery-drop")
            ) : (
              <div className="tix-media-slot-filled is-gallery">
                {preview(undefined)}
              </div>
            )}
          </div>

          {/* Right Column: 16:9 Banner Slot */}
          <div className="tix-media-slot-col">
            <div className="tix-media-slot-head">
              <span className="tix-media-slot-title">{t("organizerEventCreate.mediaBannerTitle")}</span>
            </div>
            {banner ? (
              <div className="tix-media-slot-filled is-banner">
                {preview(banner)}
                {mutation.isPending && pendingTarget === banner.imageId && loadingOverlay}
                {!readOnly && (
                  <div className="tix-media-slot-overlay">
                    {imageInput("BANNER", banner.imageId)}
                    {removeButton(banner.imageId)}
                  </div>
                )}
              </div>
            ) : !readOnly ? (
              bannerDropLabel
            ) : (
              <div className="tix-media-slot-filled is-banner">
                {preview(undefined)}
              </div>
            )}
          </div>
        </section>

        {/* Gallery Management List (Reorder / Replace / Delete) */}
        {(gallery.length > 0 || readOnly) && (
          <section className="tix-media-gallery-section">
            <div className="tix-media-gallery-header">
              <h3 className="tix-media-gallery-label">
                {t("organizerMedia.galleryPhotos")} ({gallery.length})
              </h3>
            </div>
            {gallery.length > 0 && (
              <div className="tix-media-grid">
                {gallery.map((image, index) => (
                  <div className="tix-media-card" key={image.imageId}>
                    <div className="tix-media-preview-wrap">
                      {preview(image)}
                      <span className="tix-media-order-badge">#{index + 1}</span>
                      {mutation.isPending && pendingTarget === image.imageId && loadingOverlay}
                    </div>
                    {!readOnly && (
                      <div
                        className="tix-media-card-actions"
                        role="group"
                        aria-label={t("organizerEventCreate.mediaImageLabel", { number: index + 1 })}
                      >
                        {imageInput("GALLERY", image.imageId)}
                        {removeButton(image.imageId)}
                        {[-1, 1].map(direction => {
                          const neighbor = gallery[index + direction];
                          const label = t(direction < 0 ? "organizerEventCreate.mediaMoveEarlier" : "organizerEventCreate.mediaMoveLater");
                          return (
                            <button
                              key={direction}
                              type="button"
                              className="tix-media-action-btn is-icon-only"
                              disabled={busy || !neighbor}
                              aria-label={label}
                              title={label}
                              onClick={() => neighbor && run({ kind: "reorder", imageId: image.imageId, displayOrder: neighbor.displayOrder })}
                            >
                              {direction < 0 ? <ArrowUp size={14} /> : <ArrowDown size={14} />}
                            </button>
                          );
                        })}
                      </div>
                    )}
                  </div>
                ))}
                {!readOnly &&
                  Array.from({ length: 4 - (gallery.length % 4) }, (_, slotIndex) => (
                    <div key={`gallery-empty-${slotIndex}`} className="tix-media-grid-slot-wrap">
                      {renderGalleryDropzone(`gallery-grid-drop-${slotIndex}`, "is-grid-tile")}
                    </div>
                  ))}
              </div>
            )}
            {!gallery.length && readOnly && <p className="tix-media-format-note">{t("organizerMedia.noImages")}</p>}
          </section>
        )}
      </div>
    </div>
  );
}
