import { useQuery } from "@tanstack/react-query";
import { useKeycloak } from "@react-keycloak/web";
import axios from "axios";
import { userService, type UserProfile } from "../services/userService";

export const USER_PROFILE_QUERY_KEY = ["user-profile"] as const;

export function useUserProfile() {
  const { keycloak, initialized } = useKeycloak();
  const isAuthenticated = Boolean(initialized && keycloak?.authenticated);

  return useQuery<UserProfile>({
    queryKey: [...USER_PROFILE_QUERY_KEY, keycloak?.subject ?? "anonymous"],
    queryFn: () => userService.getProfile(),
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000,
    retry: (failureCount, error) => {
      if (axios.isAxiosError(error) && (error.response?.status === 401 || error.response?.status === 403)) {
        return false;
      }
      return failureCount < 2;
    },
  });
}
