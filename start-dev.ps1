$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$frontendPath = Join-Path $repoRoot "frontend"

Write-Host "Starting Digital Wallet backend stack with Docker Compose..." -ForegroundColor Cyan
docker compose up -d --build

Write-Host "Starting frontend dev server in a new PowerShell window..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "Set-Location '$frontendPath'; npm run dev"
)

Write-Host ""
Write-Host "Backend stack is starting in Docker." -ForegroundColor Green
Write-Host "Frontend dev server is opening in a separate window." -ForegroundColor Green
Write-Host "Gateway URL: http://localhost:8090" -ForegroundColor Yellow
Write-Host "Frontend URL: http://localhost:5173" -ForegroundColor Yellow
Write-Host ""
Write-Host "Useful commands:" -ForegroundColor Cyan
Write-Host "  docker compose ps"
Write-Host "  docker compose logs -f auth-service"
Write-Host "  docker compose logs -f notification-service"
Write-Host "  docker compose down"
