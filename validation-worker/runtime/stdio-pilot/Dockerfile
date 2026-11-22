FROM mcp-compass/typescript-sandbox:1.0
USER root
WORKDIR /opt/stdio-pilot
COPY validation-worker/runtime/stdio-pilot/install.mjs ./install.mjs
RUN node install.mjs
COPY validation-worker/runtime/stdio-pilot/probe.mjs ./probe.mjs
USER 65532:65532
WORKDIR /tmp
