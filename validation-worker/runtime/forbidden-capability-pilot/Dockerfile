FROM python:3.12-alpine3.23
RUN apk add --no-cache postgresql17 postgresql17-client \
    && addgroup -g 65532 fixture \
    && adduser -D -H -u 65532 -G fixture fixture
WORKDIR /opt/forbidden-capability-pilot
COPY validation-worker/runtime/forbidden-capability-pilot/probe.py ./probe.py
USER 65532:65532
