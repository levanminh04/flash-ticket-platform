import axiosClient from "../lib/axiosClient";
import { Venue } from "../types/api";

export const venueService = {
  getVenues: async () => {
    const response = await axiosClient.get<{ id: string; name: string; address: string }[]>("/api/v1/venues");
    return response.data.map(venue => ({ ...venue, slug: venue.id, city: "" } satisfies Venue));
  },
};
