$ErrorActionPreference = "Stop"
docker compose up -d db
Write-Host "Database is starting. Run these in separate terminals:"
Write-Host "  `$env:SPRING_PROFILES_ACTIVE='local'; .\mvnw.cmd -pl backend spring-boot:run"
Write-Host "  cd web; npm install; npm run dev"
