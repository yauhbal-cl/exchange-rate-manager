# Build context is the repo root (needs ../contracts/openapi.yaml for codegen).
FROM node:22-alpine AS build
RUN apk add --no-cache openjdk21-jre-headless
WORKDIR /workspace

COPY contracts ./contracts
COPY frontend/package.json frontend/package-lock.json frontend/
WORKDIR /workspace/frontend
RUN npm ci

COPY frontend/. .
RUN npm run generate:api
RUN npm run build

FROM nginx:alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
