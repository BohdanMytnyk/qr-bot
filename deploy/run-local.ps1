$ErrorActionPreference = "Stop"

$projectDirectory = Split-Path -Parent $PSScriptRoot
$environmentPath = Join-Path $PSScriptRoot ".env.local"
$artifactPath = Join-Path $projectDirectory "target\qr-bot-0.0.1-SNAPSHOT.jar"
$dockerArtifactPath = Join-Path $PSScriptRoot "app.jar"

if (-not (Test-Path -LiteralPath $environmentPath)) {
    throw "Create deploy/.env.local from deploy/.env.local.example and configure the separate local bot first."
}

Push-Location $projectDirectory
try {
    & mvn package
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }
    Copy-Item -LiteralPath $artifactPath -Destination $dockerArtifactPath -Force
    & docker compose --project-name qr-bot-local --env-file $environmentPath `
        -f (Join-Path $PSScriptRoot "compose.local.yml") up --detach --build --wait
    if ($LASTEXITCODE -ne 0) { throw "Local Docker Compose startup failed." }
} finally {
    Pop-Location
}
