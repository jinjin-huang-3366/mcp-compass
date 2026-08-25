FROM node:22.14.0-bookworm-slim

WORKDIR /opt/mcp-compass/runtime
COPY backend/src/main/resources/generator/typescript/v1/package.json ./package.json
COPY backend/src/main/resources/generator/typescript/v1/package-lock.json ./package-lock.json
RUN npm ci --ignore-scripts \
    && npm cache verify

WORKDIR /workspace
