FROM node:22-alpine
WORKDIR /app
COPY package.json server.mjs ./
ENV PORT=8787
RUN mkdir -p /app/data/packs && chown -R node:node /app
EXPOSE 8787
USER node
CMD ["node", "server.mjs"]
