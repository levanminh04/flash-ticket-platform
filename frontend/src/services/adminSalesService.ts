import axiosClient from "../lib/axiosClient";

export type SalesGrouping = "DAY" | "WEEK" | "MONTH";
export type AdminSalesSortBy = "soldRevenue" | "soldTicketCount" | "soldOrderCount";
export type SortDirection = "ASC" | "DESC";
export type AdminSalesEventState = "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "PUBLISHED" | "CANCELLED";
export type OrderState =
  | "PENDING_PAYMENT"
  | "ISSUING"
  | "COMPLETED"
  | "CANCELLED"
  | "EXPIRED"
  | "ISSUANCE_FAILED";

export interface StateSalesItem {
  state: OrderState;
  orderCount: number;
  ticketCount: number;
  subtotal: string;
  discount: string;
  total: string;
}

export interface AdminSalesSummaryView {
  from: string;
  to: string;
  organizerSubject: string | null;
  timeZone: string;
  asOf: string;
  currency: string;
  soldOrderCount: number;
  soldTicketCount: number;
  soldSubtotal: string;
  soldDiscount: string;
  soldRevenue: string;
  byState: StateSalesItem[];
}

export interface AdminSalesChartItem {
  periodStart: string;
  periodEnd: string;
  soldOrderCount: number;
  soldTicketCount: number;
  soldRevenue: string;
}

export interface AdminSalesChartView {
  from: string;
  to: string;
  organizerSubject: string | null;
  timeZone: string;
  asOf: string;
  currency: string;
  groupBy: SalesGrouping;
  items: AdminSalesChartItem[];
}

export interface AdminEventSalesItem {
  eventId: string;
  eventTitle: string;
  organizerSubject: string;
  eventStatus: AdminSalesEventState;
  soldOrderCount: number;
  soldTicketCount: number;
  soldRevenue: string;
}

export interface AdminEventSalesPage {
  from: string;
  to: string;
  organizerSubject: string | null;
  timeZone: string;
  asOf: string;
  currency: string;
  items: AdminEventSalesItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const adminSalesService = {
  getSalesSummary: async (
    params: { from?: string; to?: string },
    signal?: AbortSignal,
  ): Promise<AdminSalesSummaryView> => {
    const response = await axiosClient.get<AdminSalesSummaryView>("/api/v1/admin/sales/summary", {
      params,
      signal,
    });
    return response.data;
  },

  getSalesChart: async (
    params: { from?: string; to?: string; groupBy?: SalesGrouping },
    signal?: AbortSignal,
  ): Promise<AdminSalesChartView> => {
    const response = await axiosClient.get<AdminSalesChartView>("/api/v1/admin/sales/chart", {
      params: { groupBy: "DAY", ...params },
      signal,
    });
    return response.data;
  },

  getEventSalesStatistics: async (
    params: {
      from?: string;
      to?: string;
      page?: number;
      size?: number;
      sortBy?: AdminSalesSortBy;
      direction?: SortDirection;
    },
    signal?: AbortSignal,
  ): Promise<AdminEventSalesPage> => {
    const response = await axiosClient.get<AdminEventSalesPage>(
      "/api/v1/admin/events/sales-statistics",
      {
        params: {
          page: 0,
          size: 10,
          sortBy: "soldRevenue",
          direction: "DESC",
          ...params,
        },
        signal,
      },
    );
    return response.data;
  },
};
