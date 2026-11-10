import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import axiosClient from "../../lib/axiosClient";
import { OrganizerSummary } from "../../services/organizerDashboardService";

export type EventWithImages = OrganizerSummary & {
  images: { imageId: string; objectReference: string; displayOrder: number }[];
};

export default function OrganizerDraftMedia<T extends EventWithImages>({ event, onSaved, onRefresh, disabled = false, onBusy }: {
  event: T;
  onSaved: (event: T) => void;
  onRefresh: () => void;
  disabled?: boolean;
  onBusy?: (busy: boolean) => void;
}) {
  const [error, setError] = useState("");
  const upload = useMutation({
    mutationFn: async (file: File) => {
      const form = new FormData();
      form.append("file", file);
      form.append("rowVersion", event.rowVersion);
      form.append("displayOrder", String(Math.max(-1, ...event.images.map(image => image.displayOrder)) + 1));
      const response = await axiosClient.post<T>(
        `/api/v1/organizer/events/${encodeURIComponent(event.eventId)}/images`, form,
        { headers: { "Content-Type": "multipart/form-data" } },
      );
      return response.data;
    },
    onMutate: () => onBusy?.(true),
    onSuccess: onSaved,
    onError: () => {
      setError("Không tải được ảnh. Nếu bản nháp đã thay đổi, hãy tải lại dữ liệu trước khi thử lại.");
    },
    onSettled: () => onBusy?.(false),
  });

  return <section aria-label="Ảnh sự kiện">
    <h3>Ảnh sự kiện</h3>
    {event.images.length ? (
      <ul>{event.images.map(image => <li key={image.imageId}>
        {/^https?:\/\//i.test(image.objectReference)
          ? <img src={image.objectReference} alt={`Ảnh sự kiện ${image.displayOrder + 1}`} width={160} />
          : image.objectReference}
      </li>)}</ul>
    ) : <p>Chưa có ảnh sự kiện.</p>}
    <label>Thêm ảnh <input type="file" accept="image/*" disabled={disabled || upload.isPending} onChange={e => {
      const file = e.currentTarget.files?.[0];
      e.currentTarget.value = "";
      if (file && !upload.isPending && !disabled) {
        setError("");
        upload.mutate(file);
      }
    }} /></label>
    {upload.isPending && <p role="status">Đang tải ảnh…</p>}
    {error && <><p role="alert">{error}</p><button className="btn btn-secondary" type="button" disabled={disabled} onClick={onRefresh}>Tải lại dữ liệu bản nháp</button></>}
  </section>;
}
