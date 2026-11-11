import axiosClient from "../lib/axiosClient";

export interface UserProfile {
  userId: string;
  identitySubject: string;
  keycloakId?: string;
  firstName?: string;
  lastName?: string;
  displayName: string | null;
  avatarUrl?: string | null;
  bio?: string | null;
  dateOfBirth?: string | null;
  gender?: string | null;
  email?: string | null;
  address?: string | null;
  phoneNumber?: string | null;
  phone?: string;
  phoneVerified?: boolean | null;
  roles?: string[];
  status?: string;
  organizerProfileId?: string | null;
  language?: string | null;
  timezone?: string | null;
  currency?: string | null;
  lastLoginAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export const userService = {
  getProfile: async (): Promise<UserProfile | null> => {
    try {
      const response = await axiosClient.get<UserProfile>("/api/v1/me");
      return response.data;
    } catch {
      return null;
    }
  },

  uploadAvatar: async (file: File): Promise<UserProfile> => {
    const data = new FormData();
    data.append("file", file);
    const response = await axiosClient.putForm<UserProfile>("/api/v1/me/avatar", data);
    return response.data;
  },

  updateProfile: async (data: Partial<Pick<UserProfile, "displayName" | "dateOfBirth" | "address" | "phoneNumber">>): Promise<UserProfile> => {
    const response = await axiosClient.put<UserProfile>("/api/v1/me", data);
    return response.data;
  },
};
