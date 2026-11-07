// File: src/lib/axiosClient.ts
import axios from "axios";
import keycloak from "./keycloak";

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
    // Chỉ xử lý token nếu user đã đăng nhập
    if (keycloak && keycloak.authenticated) {
      if (keycloak.isTokenExpired(30)) {
        try {
          await keycloak.updateToken(30);
        } catch {
          keycloak.clearToken();
          throw new Error("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        }
      }
      if (keycloak.token) {
        config.headers.Authorization = `Bearer ${keycloak.token}`;
      }
    }
    return config;
  },
  (error) => Promise.reject(error),
);

export default axiosClient;
