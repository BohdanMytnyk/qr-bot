$ErrorActionPreference = "Stop"

$projectDirectory = Split-Path -Parent $PSScriptRoot
$environmentPath = Join-Path $PSScriptRoot ".env.local"
$composePath = Join-Path $PSScriptRoot "compose.local.yml"

if (-not (Test-Path -LiteralPath $environmentPath)) {
    throw "Create deploy/.env.local from deploy/.env.local.example and configure the separate local bot first."
}

foreach ($line in Get-Content -LiteralPath $environmentPath) {
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
        continue
    }
    $separator = $trimmed.IndexOf("=")
    if ($separator -lt 1) {
        throw "Invalid entry in deploy/.env.local. Expected NAME=value."
    }
    $name = $trimmed.Substring(0, $separator).Trim()
    $value = $trimmed.Substring($separator + 1).Trim()
    if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
        throw "Invalid environment variable name in deploy/.env.local."
    }
    Set-Item -LiteralPath "Env:$name" -Value $value
}

$env:TELEGRAM_BOT_TOKEN = $env:LOCAL_TELEGRAM_BOT_TOKEN
$env:TELEGRAM_BOT_USERNAME = $env:LOCAL_TELEGRAM_BOT_USERNAME
$env:TELEGRAM_CONTENT_CHANNEL_ID = $env:LOCAL_TELEGRAM_CONTENT_CHANNEL_ID
$env:TELEGRAM_RESTART_NOTIFICATION_CHAT_ID = $env:LOCAL_TELEGRAM_OWNER_ID
$env:SPRING_PROFILES_ACTIVE = "local"
$env:SPRING_DATA_MONGODB_URI = "mongodb://127.0.0.1:27019/qr_bot"
$env:QR_PUBLIC_BASE_URL = "https://qr.twob.cc"
$env:QR_BOT_LOG_FILE = Join-Path $projectDirectory "target\local-application.json"

& docker compose --project-name qr-bot-local --env-file $environmentPath `
    -f $composePath up --detach --wait mongodb
if ($LASTEXITCODE -ne 0) {
    throw "Local MongoDB startup failed."
}

Push-Location $projectDirectory
try {
    & mvn spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "Local Maven application failed." }
} finally {
    Pop-Location
}
