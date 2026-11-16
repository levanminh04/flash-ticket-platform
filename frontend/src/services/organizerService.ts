import axiosClient from "../lib/axiosClient";
import { gatewayFallbackClient, userDirectClient } from "../lib/internalServiceClients";

export type OrganizerImageType = "BANNER" | "GALLERY" | "SEAT_MAP";

import { ticketService, CheckInResult } from "./ticketService";

export interface OrganizerEventImage {
  id: string;
  imageUrl: string;
  publicId?: string | null;
  imageType: OrganizerImageType;
  altText?: string | null;
  displayOrder?: number | null;
  width?: number | null;
  height?: number | null;
  fileSizeBytes?: number | null;
  isPrimary?: boolean | null;
  createdAt?: string | null;
  createdBy?: string | null;
  isDeleted?: boolean | null;
}

export type CheckInResponse = CheckInResult;

export interface OrganizerProfile {
  id: string;
  userId: string;
  name: string;
  slug?: string | null;
  logoUrl?: string | null;
  bannerUrl?: string | null;
  description?: string | null;
  websiteUrl?: string | null;
  isVerified?: boolean | null;
  createdAt?: string | null;
  totalEvents?: number | null;
  totalTicketsSold?: number | null;
  followerCount?: number | null;
  averageRating?: number | null;
  email?: string | null;
  phone?: string | null;
}

export interface ApplyOrganizerPayload {
  organizationName: string;
  shortDescription: string;
  contactEmail: string;
  contactPhone: string;
  websiteUrl?: string | null;
  socialPageUrl: string;
  headquartersAddress: string;
  taxCode: string;
}

export interface OrganizerApplication {
  id: string;
  identitySubject: string;
  organizationName: string;
  shortDescription: string;
  contactEmail?: string | null;
  contactPhone?: string | null;
  websiteUrl?: string | null;
  socialPageUrl?: string | null;
  headquartersAddress?: string | null;
  taxCode?: string | null;
  status: "PENDING" | "ACTIVE" | "REJECTED";
  rejectionReason: string | null;
  decidedBySubject: string | null;
  decidedAt: string | null;
  createdAt: string;
  version: string;
}

export interface FollowResponse {
  organizerProfileId: string;
  organizerName: string;
  isFollowing: boolean;
  followerCount: number;
}

export interface OrganizerEventImageUpdatePayload {
  altText?: string | null;
  displayOrder?: number | null;
  imageType?: OrganizerImageType | null;
}

async function uploadOrganizerProfileImage(
  path: "/api/organizers/me/logo" | "/api/organizers/me/banner",
  file: File,
): Promise<OrganizerProfile> {
  const formData = new FormData();
  formData.append("file", file);

  try {
    const response = await gatewayFallbackClient.post<OrganizerProfile>(
      path,
      formData,
      {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      },
    );
    return response.data;
  } catch {
    const response = await userDirectClient.post<OrganizerProfile>(
      path,
      formData,
      {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      },
    );
    return response.data;
  }
}

export const organizerService = {
  applyForOrganizer: async (payload: ApplyOrganizerPayload): Promise<OrganizerApplication> => {
    const response = await axiosClient.post<OrganizerApplication>("/api/v1/organizer-applications", payload);
    return response.data;
  },

  getMyApplications: async (
    signal?: AbortSignal,
    cursor?: string,
  ): Promise<{ items: OrganizerApplication[]; nextCursor: string | null }> => {
    const response = await axiosClient.get<{ items: OrganizerApplication[]; nextCursor: string | null }>(
      "/api/v1/organizer-applications",
      { signal, params: { cursor } },
    );
    return response.data;
  },

  getMyApplicationById: async (
    id: string,
    signal?: AbortSignal,
  ): Promise<OrganizerApplication> => {
    const response = await axiosClient.get<OrganizerApplication>(
      `/api/v1/organizer-applications/${encodeURIComponent(id)}`,
      { signal },
    );
    return response.data;
  },

  followOrganizer: async (organizerProfileId: string): Promise<FollowResponse> => {
    const response = await axiosClient.put<{
      organizerSubject: string;
      following: boolean;
      followerCount: number;
    }>(`/api/v1/organizers/${encodeURIComponent(organizerProfileId)}/follow`);
    return {
      organizerProfileId: response.data.organizerSubject,
      organizerName: "",
      isFollowing: response.data.following,
      followerCount: response.data.followerCount,
    };
  },

  unfollowOrganizer: async (
    organizerProfileId: string,
  ): Promise<FollowResponse> => {
    const response = await axiosClient.delete<{
      organizerSubject: string;
      following: boolean;
      followerCount: number;
    }>(`/api/v1/organizers/${encodeURIComponent(organizerProfileId)}/follow`);
    return {
      organizerProfileId: response.data.organizerSubject,
      organizerName: "",
      isFollowing: response.data.following,
      followerCount: response.data.followerCount,
    };
  },

  isFollowingOrganizer: async (
    organizerProfileId: string,
  ): Promise<boolean> => {
    const response = await axiosClient.get<{
      organizerSubject: string;
      following: boolean;
      followerCount: number;
    }>(`/api/v1/organizers/${encodeURIComponent(organizerProfileId)}/follow`);
    return response.data.following;
  },

  getAdminApplications: async (
    status: OrganizerApplication["status"],
    cursor: string | null,
    signal?: AbortSignal,
    query?: string,
  ) => {
    const q = query?.trim() || undefined;
    const response = await axiosClient.get<{ items: OrganizerApplication[]; nextCursor: string | null }>(
      "/api/v1/admin/organizer-applications",
      { params: { status, size: 20, cursor: cursor ?? undefined, q }, signal },
    );
    return response.data;
  },

  approveApplication: async (id: string) => {
    const response = await axiosClient.post<OrganizerApplication>(
      `/api/v1/admin/organizer-applications/${encodeURIComponent(id)}/approve`,
    );
    return response.data;
  },

  rejectApplication: async (id: string, reason: string) => {
    const response = await axiosClient.post<OrganizerApplication>(
      `/api/v1/admin/organizer-applications/${encodeURIComponent(id)}/reject`,
      { reason },
    );
    return response.data;
  },

  getPublicOrganizerBySlug: async (slug: string, signal?: AbortSignal): Promise<OrganizerProfile> => {
    const response = await axiosClient.get<{
      organizerSubject: string;
      organizationName: string;
      shortDescription: string;
      followerCount: number;
      logoUrl?: string | null;
      bannerUrl?: string | null;
    }>(`/api/v1/organizers/${encodeURIComponent(slug)}`, { signal });
    const data = response.data;
    return {
      id: data.organizerSubject,
      userId: data.organizerSubject,
      name: data.organizationName,
      slug: data.organizerSubject,
      description: data.shortDescription,
      followerCount: data.followerCount,
      logoUrl: data.logoUrl ?? null,
      bannerUrl: data.bannerUrl ?? null,
      isVerified: true,
    };
  },

  getMyOrganizerProfile: async (): Promise<OrganizerProfile> => {
    try {
      const response = await gatewayFallbackClient.get<OrganizerProfile>(
        "/api/organizers/me",
      );
      return response.data;
    } catch {
      const response = await userDirectClient.get<OrganizerProfile>(
        "/api/organizers/me",
      );
      return response.data;
    }
  },

  getOrganizerByUserId: async (userId: string): Promise<OrganizerProfile> => {
    const path = `/api/internal/organizers/by-user/${encodeURIComponent(userId)}`;

    try {
      const response = await gatewayFallbackClient.get<OrganizerProfile>(path);
      return response.data;
    } catch {
      const response = await userDirectClient.get<OrganizerProfile>(path);
      return response.data;
    }
  },

  uploadLogo: async (file: File): Promise<OrganizerProfile> =>
    uploadOrganizerProfileImage("/api/organizers/me/logo", file),

  uploadBanner: async (file: File): Promise<OrganizerProfile> =>
    uploadOrganizerProfileImage("/api/organizers/me/banner", file),

  getEventImages: async (eventId: string): Promise<OrganizerEventImage[]> => {
    const response = await axiosClient.get<OrganizerEventImage[]>(
      `/api/organizer/events/${eventId}/images`,
    );
    return response.data;
  },

  uploadEventImage: async (
    eventId: string,
    file: File,
    imageType: OrganizerImageType,
  ): Promise<OrganizerEventImage> => {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("imageType", imageType);

    const response = await axiosClient.post<OrganizerEventImage>(
      `/api/organizer/events/${eventId}/images`,
      formData,
      {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      },
    );
    return response.data;
  },

  deleteEventImage: async (eventId: string, imageId: string): Promise<void> => {
    await axiosClient.delete(`/api/organizer/events/${eventId}/images/${imageId}`);
  },

  updateEventImage: async (
    eventId: string,
    imageId: string,
    payload: OrganizerEventImageUpdatePayload,
  ): Promise<OrganizerEventImage> => {
    const response = await axiosClient.patch<OrganizerEventImage>(
      `/api/organizer/events/${eventId}/images/${imageId}`,
      payload,
    );
    return response.data;
  },

  checkInTicket: async (
    eventId: string,
    qrToken: string,
  ): Promise<CheckInResult> => {
    return ticketService.checkIn(eventId, qrToken);
  },
};
