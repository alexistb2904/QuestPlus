# The web component contains BOTH Quest Studio (React) and the API runtime.
FROM node:22-alpine AS frontend
WORKDIR /build/editor
COPY editor/package.json editor/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY editor/ ./
ARG VITE_QUESTPLUS_HUB_URL
ARG VITE_QUESTPLUS_DOCS_URL
RUN npm run build

FROM node:22-alpine
WORKDIR /app
COPY api/package.json api/package-lock.json api/server.mjs ./
RUN npm ci --omit=dev --no-audit --no-fund
COPY --from=frontend /build/editor/dist /app/public
ENV PORT=8787 HUB_BIND=0.0.0.0 WEB_STATIC_DIR=/app/public NODE_OPTIONS=--max-old-space-size=384
RUN mkdir -p /app/data/packs && chown -R node:node /app
USER node
EXPOSE 8787
CMD ["node","server.mjs"]
