FROM node:22.22.0-bookworm-slim

WORKDIR /opt/mcp-compass/runtime
COPY validation-worker/runtime/typescript-v1/package.json ./package.json
COPY validation-worker/runtime/typescript-v1/package-lock.json ./package-lock.json
RUN npm ci --ignore-scripts \
    && npm cache verify

WORKDIR /workspace
