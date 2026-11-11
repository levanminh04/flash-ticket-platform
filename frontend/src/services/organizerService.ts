import axiosClient from "../lib/axiosClient";
import { gatewayFallbackClient, userDirectClient } from "../lib/internalServiceClients";

export type OrganizerImageType =
  | "BANNER"
  | "POSTER"
  | "SEAT_MAP"
  | "GALLERY"
  | "THUMBNAIL";

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
}

export interface OrganizerApplication {
  id: string;
  identitySubject: string;
  organizationName: string;
  shortDescription: string;
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

async function userServicePost<TResponse, TPayload>(
  path: string,
  payload?: TPayload,
): Promise<TResponse> {
  try {
    const response = await gatewayFallbackClient.post<TResponse>(path, payload);
    return response.data;
  } catch {
    const response = await userDirectClient.post<TResponse>(path, payload);
    return response.data;
  }
}

async function userServiceDelete<TResponse>(path: string): Promise<TResponse> {
  try {
    const response = await gatewayFallbackClient.delete<TResponse>(path);
    return response.data;
  } catch {
    const response = await userDirectClient.delete<TResponse>(path);
    return response.data;
  }
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

  followOrganizer: async (organizerProfileId: string): Promise<FollowResponse> =>
    userServicePost<FollowResponse, undefined>(
      `/api/organizers/${encodeURIComponent(organizerProfileId)}/follow`,
    ),

  unfollowOrganizer: async (
    organizerProfileId: string,
  ): Promise<FollowResponse> =>
    userServiceDelete<FollowResponse>(
      `/api/organizers/${encodeURIComponent(organizerProfileId)}/follow`,
    ),

  isFollowingOrganizer: async (
    organizerProfileId: string,
  ): Promise<boolean> => {
    const path = `/api/organizers/${encodeURIComponent(organizerProfileId)}/is-following`;

    try {
      const response = await gatewayFallbackClient.get<{ isFollowing: boolean }>(path);
      return response.data.isFollowing;
    } catch {
      const response = await userDirectClient.get<{ isFollowing: boolean }>(path);
      return response.data.isFollowing;
    }
  },

  getAdminApplications: async (
    status: OrganizerApplication["status"],
    cursor: string | null,
    signal?: AbortSignal,
  ) => {
    const response = await axiosClient.get<{ items: OrganizerApplication[]; nextCursor: string | null }>(
      "/api/v1/admin/organizer-applications",
      { params: { status, size: 20, cursor: cursor ?? undefined }, signal },
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

  getPublicOrganizerBySlug: async (slug: string): Promise<OrganizerProfile> => {
    const path = `/api/organizers/public/${encodeURIComponent(slug)}`;

    try {
      const response = await gatewayFallbackClient.get<OrganizerProfile>(path);
      return response.data;
    } catch {
      const response = await userDirectClient.get<OrganizerProfile>(path);
      return response.data;
    }
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
