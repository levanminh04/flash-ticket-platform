FROM node:22-alpine
WORKDIR /app/frontend
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_GATEWAY_URL
ARG VITE_KEYCLOAK_URL
ARG VITE_KEYCLOAK_REALM
ARG VITE_KEYCLOAK_CLIENT_ID
RUN npm run build && chown node:node /app/frontend
USER node
EXPOSE 5173
CMD ["npm", "run", "preview", "--", "--host", "0.0.0.0", "--port", "5173", "--strictPort"]
