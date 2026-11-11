import axiosClient from "../lib/axiosClient";

export const organizerEventStatuses = ["DRAFT", "PENDING_APPROVAL", "APPROVED", "PUBLISHED", "CANCELLED"] as const;
export interface OrganizerSummary {
  eventId: string;
  title: string;
  status: typeof organizerEventStatuses[number];
  mode: "QUANTITY" | "SEAT_MAP";
  startsAt: string;
  endsAt: string;
  rowVersion: string;
  sourceConfigVersion: string;
}
export interface EventStatistics {
  eventId: string;
  asOf: string;
  salesAsOf: string;
  financeAsOf: string;
  purchasedTicketCount: string;
  currency: string;
  validGross: string;
  validRefunds: string;
  feeAmount: string;
  netAmount: string;
}
export interface PublicOrganizer {
  organizerSubject: string;
  organizationName: string;
  shortDescription: string;
  followerCount: number;
  logoUrl: string | null;
  bannerUrl: string | null;
}

export const organizerDashboardService = {
  profile: async (subject: string, signal?: AbortSignal) => {
    const response = await axiosClient.get<PublicOrganizer>(`/api/v1/organizers/${encodeURIComponent(subject)}`, { signal });
    return response.data;
  },
  uploadBranding: async (kind: "logo" | "banner", file: File) => {
    const form = new FormData();
    form.append("file", file);
    const response = await axiosClient.post<PublicOrganizer>(
      `/api/v1/organizers/me/${kind}`, form,
      { headers: { "Content-Type": "multipart/form-data" } },
    );
    return response.data;
  },
  events: async (cursor: string | null, signal?: AbortSignal, status?: OrganizerSummary["status"]) => {
    const response = await axiosClient.get<{ items: OrganizerSummary[]; nextCursor: string | null }>(
      "/api/v1/organizer/events", { params: { size: 20, cursor: cursor ?? undefined, status }, signal },
    );
    return response.data;
  },
  transition: async (eventId: string, action: "submit" | "publish" | "cancel", rowVersion: string) => {
    const response = await axiosClient.post<OrganizerSummary>(
      `/api/v1/organizer/events/${encodeURIComponent(eventId)}/${action}`, { rowVersion },
    );
    return response.data;
  },
  statistics: async (id: string, signal?: AbortSignal) => {
    const response = await axiosClient.get<EventStatistics>(`/api/v1/organizer/events/${id}/statistics`, { signal });
    return response.data;
  },
};
