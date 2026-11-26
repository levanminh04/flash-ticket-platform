import axiosClient from "../lib/axiosClient";
import { Venue } from "../types/api";

export interface RawVenueResponse {
  id: string;
  name: string;
  address: string;
  description?: string | null;
  imageUrl?: string | null;
  googleMapsUrl?: string | null;
}

export interface AdminVenuePayload {
  name: string;
  address: string;
  description?: string;
  googleMapsUrl?: string;
  image?: File | null;
}

function mapRawVenue(venue: RawVenueResponse): Venue {
  return {
    ...venue,
    slug: venue.id,
    city: "",
    imageUrls: venue.imageUrl ? [venue.imageUrl] : undefined,
  };
}

function buildVenueFormData(payload: AdminVenuePayload, includeEmptyOptional: boolean): FormData {
  const formData = new FormData();
  formData.append("name", payload.name.trim());
  formData.append("address", payload.address.trim());

  if (payload.description !== undefined) {
    const cleanDesc = payload.description.trim();
    if (cleanDesc || includeEmptyOptional) {
      formData.append("description", cleanDesc);
    }
  }

  if (payload.googleMapsUrl !== undefined) {
    const cleanUrl = payload.googleMapsUrl.trim();
    if (cleanUrl || includeEmptyOptional) {
      formData.append("googleMapsUrl", cleanUrl);
    }
  }

  if (payload.image) {
    formData.append("image", payload.image);
  }

  return formData;
}

export const venueService = {
  getVenues: async (): Promise<Venue[]> => {
    const response = await axiosClient.get<RawVenueResponse[]>("/api/v1/venues");
    return response.data.map(mapRawVenue);
  },

  createAdminVenue: async (payload: AdminVenuePayload): Promise<Venue> => {
    const formData = buildVenueFormData(payload, false);
    const response = await axiosClient.post<RawVenueResponse>("/api/v1/admin/venues", formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    return mapRawVenue(response.data);
  },

  updateAdminVenue: async (venueId: string, payload: AdminVenuePayload): Promise<Venue> => {
    const formData = buildVenueFormData(payload, true);
    const response = await axiosClient.patch<RawVenueResponse>(
      `/api/v1/admin/venues/${encodeURIComponent(venueId)}`,
      formData,
      {
        headers: { "Content-Type": "multipart/form-data" },
      },
    );
    return mapRawVenue(response.data);
  },
};
