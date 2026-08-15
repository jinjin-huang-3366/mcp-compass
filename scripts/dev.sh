#!/usr/bin/env bash
set -euo pipefail

docker compose up -d db
printf '\nDatabase is starting. Run these in separate terminals:\n'
printf '  SPRING_PROFILES_ACTIVE=local ./mvnw -pl backend spring-boot:run\n'
printf '  cd web && npm install && npm run dev\n'
