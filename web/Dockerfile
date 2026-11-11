FROM node:22-alpine AS docs
WORKDIR /build/docs
COPY docs-site/package*.json ./
RUN npm ci --no-audit --no-fund
COPY docs-site/ ./
RUN sed -i 's|/QuestPlus/docs/|/docs/|g' .vitepress/config.mts .vitepress/theme/custom.css \
    && sed -i 's|lastUpdated: true|lastUpdated: false|' .vitepress/config.mts \
    && npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=docs /build/docs/dist /usr/share/nginx/html/docs
EXPOSE 80
