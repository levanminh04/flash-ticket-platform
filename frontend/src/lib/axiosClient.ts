// File: src/lib/axiosClient.ts
import axios from "axios";
import keycloak from "./keycloak";

declare module "axios" {
  interface AxiosRequestConfig {
    expectedAuthOwner?: { issuer: string; subject: string };
    isRequestContextCurrent?: () => boolean;
  }
}

// Dev dùng Vite proxy cùng origin; bản build gọi origin Gateway đã cấu hình.
const baseURL = import.meta.env.DEV ? undefined : import.meta.env.VITE_API_GATEWAY_URL;

const axiosClient = axios.create({
  baseURL: baseURL || undefined,
  headers: {
    "Content-Type": "application/json",
  },
});

// Tự động đính kèm Bearer Token vào mọi Request
axiosClient.interceptors.request.use(
  async (config) => {
    const expected = config.expectedAuthOwner;
    // Guard trước và sau refresh: không relay thao tác của A bằng token B.
    if (expected && (!keycloak.authenticated || keycloak.tokenParsed?.iss !== expected.issuer || keycloak.tokenParsed?.sub !== expected.subject)) {
      throw new Error("AUTH_OWNER_CHANGED");
    }
    if (keycloak && keycloak.authenticated) {
      if (keycloak.isTokenExpired(30)) {
        try {
          await keycloak.updateToken(30);
        } catch {
          keycloak.clearToken();
          throw new Error("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        }
      }
      if (expected && (!keycloak.authenticated || !keycloak.token || keycloak.tokenParsed?.iss !== expected.issuer || keycloak.tokenParsed?.sub !== expected.subject)) {
        throw new Error("AUTH_OWNER_CHANGED");
      }
      if (keycloak.token) {
        config.headers.Authorization = `Bearer ${keycloak.token}`;
      }
    }
    if (config.isRequestContextCurrent && !config.isRequestContextCurrent()) throw new Error("REQUEST_CONTEXT_CHANGED");
    return config;
  },
  (error) => Promise.reject(error),
);

export default axiosClient;
