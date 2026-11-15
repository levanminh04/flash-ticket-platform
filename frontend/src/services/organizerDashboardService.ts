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
export interface PublicOrganizer {
  organizerSubject: string;
  organizationName: string;
  shortDescription: string;
  followerCount: number;
  logoUrl: string | null;
  bannerUrl: string | null;
}

interface SalesReport {
  from: string;
  to: string;
  asOf: string;
  currency: string;
}

interface SalesTotals {
  soldOrderCount: number;
  soldTicketCount: number;
  soldRevenue: string;
}

interface SalesChart extends SalesReport {
  items: (SalesTotals & { periodStart: string; periodEnd: string })[];
}

interface EventSalesPage extends SalesReport {
  items: (SalesTotals & { eventId: string; eventTitle: string })[];
  page: number;
  totalPages: number;
}

const organizerPath = (subject: string) => "/api/v1/organizers/" + encodeURIComponent(subject);

export const organizerDashboardService = {
  salesSummary: async (subject: string, from?: string, to?: string, signal?: AbortSignal) =>
    (await axiosClient.get<SalesReport & SalesTotals>(organizerPath(subject) + "/sales/summary", {
      params: { from: from || undefined, to: to || undefined },
      signal,
    })).data,
  salesChart: async (subject: string, from?: string, to?: string, groupBy = "DAY", signal?: AbortSignal) =>
    (await axiosClient.get<SalesChart>(organizerPath(subject) + "/sales/chart", {
      params: { from: from || undefined, to: to || undefined, groupBy },
      signal,
    })).data,
  eventSales: async (
    subject: string,
    page: number,
    from?: string,
    to?: string,
    signal?: AbortSignal,
    size = 100,
  ) =>
    (await axiosClient.get<EventSalesPage>(organizerPath(subject) + "/events/sales-statistics", {
      params: {
        page,
        size,
        sortBy: "soldTicketCount",
        direction: "DESC",
        from: from || undefined,
        to: to || undefined,
      },
      signal,
    })).data,
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
  events: async (
    cursor: string | null,
    signal?: AbortSignal,
    status?: OrganizerSummary["status"],
    size = 20,
  ) => {
    const response = await axiosClient.get<{ items: OrganizerSummary[]; nextCursor: string | null }>(
      "/api/v1/organizer/events", { params: { size, cursor: cursor ?? undefined, status }, signal },
    );
    return response.data;
  },
  transition: async (eventId: string, action: "submit" | "publish" | "cancel", rowVersion: string) => {
    const response = await axiosClient.post<OrganizerSummary>(
      `/api/v1/organizer/events/${encodeURIComponent(eventId)}/${action}`, { rowVersion },
    );
    return response.data;
  },
  eventSalesSummary: async (eventId: string, signal?: AbortSignal, from?: string, to?: string) => {
    const response = await axiosClient.get<SalesReport & SalesTotals>(
      "/api/v1/organizer/events/" + encodeURIComponent(eventId) + "/sales-summary",
      {
        params: { from: from || undefined, to: to || undefined },
        signal,
      },
    );
    return response.data;
  },
};
