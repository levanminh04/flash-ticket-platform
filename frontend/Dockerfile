# syntax=docker/dockerfile:1
# CD builds this image from source on the runner; the server never builds. The runtime stage has no Node.
# Build dist trên kiến trúc runner để npm không chạy qua QEMU; dist dùng chung cho cả hai kiến trúc runtime.
FROM --platform=$BUILDPLATFORM node:22-alpine@sha256:0e1754758c2df4cfa3e68d59e2e1b7374bb7fd9ea6328eaaacf15cc934af8202 AS build
WORKDIR /app/frontend
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_GATEWAY_URL
ARG VITE_KEYCLOAK_URL
ARG VITE_KEYCLOAK_REALM
ARG VITE_KEYCLOAK_CLIENT_ID
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.31.7-alpine-slim@sha256:a8cb0f33d51fa107c96fd2bd3a71aaf564ec131e9d2bdf8700944f0ed8b170d7
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /app/frontend/dist /usr/share/nginx/html
EXPOSE 5173
# Bypass the image entrypoint scripts: they try to rewrite config, which fails on a read-only root filesystem.
ENTRYPOINT ["nginx", "-g", "daemon off;"]
