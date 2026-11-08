# syntax=docker/dockerfile:1
# CD builds this image from source on the runner; the server never builds. The runtime stage has no Node.
FROM node:22-alpine@sha256:0e1754758c2df4cfa3e68d59e2e1b7374bb7fd9ea6328eaaacf15cc934af8202 AS build
WORKDIR /app/frontend
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_GATEWAY_URL
ARG VITE_KEYCLOAK_URL
ARG VITE_KEYCLOAK_REALM
ARG VITE_KEYCLOAK_CLIENT_ID
RUN npm run build

FROM nginxinc/nginx-unprivileged:alpine@sha256:b5047b9b76845eac124358373c6852377c52e4b05834a47647e955dd91cb8f1e
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /app/frontend/dist /usr/share/nginx/html
EXPOSE 5173
# Bypass the image entrypoint scripts: they try to rewrite config, which fails on a read-only root filesystem.
ENTRYPOINT ["nginx", "-g", "daemon off;"]
