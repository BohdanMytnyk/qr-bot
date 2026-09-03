param(
    [Parameter(Mandatory = $true)]
    [string]$Artifact,

    [string]$SshHost = "macserver",
    [string]$RemoteDirectory = "/home/serveradmin/apps/qr-bot-prod",

    [switch]$RotateWebhookSecret
)

$ErrorActionPreference = "Stop"

if ($SshHost -notmatch '^[A-Za-z0-9._-]+$') {
    throw "Invalid SSH host: $SshHost"
}
if ($RemoteDirectory -notmatch '^/[A-Za-z0-9._/-]+$') {
    throw "RemoteDirectory must be an absolute Linux path without spaces"
}

$deployDirectory = $PSScriptRoot
$artifactPath = (Resolve-Path -LiteralPath $Artifact).Path
$environmentPath = Join-Path $deployDirectory ".env"

if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw "Missing $environmentPath. Copy .env.example to .env and replace every placeholder."
}

$environmentText = Get-Content -LiteralPath $environmentPath -Raw
if ($RotateWebhookSecret -or $environmentText -notmatch '(?m)^TELEGRAM_WEBHOOK_SECRET=.+$') {
    $secretBytes = [byte[]]::new(32)
    $randomNumberGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomNumberGenerator.GetBytes($secretBytes)
    }
    finally {
        $randomNumberGenerator.Dispose()
    }
    $webhookSecret = [Convert]::ToBase64String($secretBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    if ($environmentText -match '(?m)^TELEGRAM_WEBHOOK_SECRET=.*$') {
        $environmentText = $environmentText -replace '(?m)^TELEGRAM_WEBHOOK_SECRET=.*$', "TELEGRAM_WEBHOOK_SECRET=$webhookSecret"
        Set-Content -LiteralPath $environmentPath -Value $environmentText -NoNewline
    }
    else {
        Add-Content -LiteralPath $environmentPath -Value "`nTELEGRAM_WEBHOOK_SECRET=$webhookSecret"
    }
    $environmentText = Get-Content -LiteralPath $environmentPath -Raw
    Write-Host "Generated a new TELEGRAM_WEBHOOK_SECRET in deploy/.env"
}
$requiredVariables = @(
    "TELEGRAM_BOT_TOKEN",
    "TELEGRAM_BOT_USERNAME",
    "TELEGRAM_CONTENT_CHANNEL_ID",
    "TELEGRAM_WEBHOOK_SECRET",
    "MONGO_ROOT_USERNAME",
    "MONGO_ROOT_PASSWORD",
    "MONGO_APP_USERNAME",
    "MONGO_APP_PASSWORD"
)
foreach ($variable in $requiredVariables) {
    if ($environmentText -notmatch "(?m)^$([regex]::Escape($variable))=.+$") {
        throw "Missing required value $variable in $environmentPath"
    }
}
if ($environmentText -match '(?i)replace-with|your_qr_bot') {
    throw "Replace all placeholder values in $environmentPath before deploying"
}

# Fail before uploading anything when the remote Docker runtime is unavailable
# or the SSH user cannot access it.
& ssh $SshHost "command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1 && docker info >/dev/null 2>&1"
if ($LASTEXITCODE -ne 0) {
    throw "Docker with Compose is missing or unavailable to $SshHost's SSH user"
}

$stagingDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("qr-bot-deploy-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $stagingDirectory | Out-Null

try {
    Copy-Item -LiteralPath $artifactPath -Destination (Join-Path $stagingDirectory "app.jar")
    foreach ($name in @("Dockerfile", "compose.yml", "mongo-init.js", "mongosh", "configure-telegram-webhook.sh", ".dockerignore", ".env")) {
        Copy-Item -LiteralPath (Join-Path $deployDirectory $name) -Destination (Join-Path $stagingDirectory $name)
    }

    & ssh $SshHost "mkdir -p -- '$RemoteDirectory'"
    if ($LASTEXITCODE -ne 0) { throw "Failed to create remote deployment directory" }

    foreach ($name in @("app.jar", "Dockerfile", "compose.yml", "mongo-init.js", "mongosh", "configure-telegram-webhook.sh", ".dockerignore", ".env")) {
        & scp (Join-Path $stagingDirectory $name) "${SshHost}:${RemoteDirectory}/$name"
        if ($LASTEXITCODE -ne 0) { throw "Failed to upload $name" }
    }

    # Compose leaves MongoDB untouched when its configuration has not changed.
    # If configuration does change, it recreates only the container and reattaches
    # the same explicitly named data volume.
    $remoteCommand = @"
set -eu
chmod 600 '$RemoteDirectory/.env'
sed -i 's/\r$//' '$RemoteDirectory/configure-telegram-webhook.sh'
chmod 700 '$RemoteDirectory/configure-telegram-webhook.sh'
mkdir -p /home/serveradmin/bin
install -m 700 '$RemoteDirectory/mongosh' /home/serveradmin/bin/mongosh
cd '$RemoteDirectory'
docker compose --project-name qr-bot-prod --env-file .env up --detach --wait --wait-timeout 120 mongodb
docker compose --project-name qr-bot-prod --env-file .env build app
docker compose --project-name qr-bot-prod --env-file .env up --detach --no-deps --force-recreate --remove-orphans --wait --wait-timeout 120 app
'$RemoteDirectory/configure-telegram-webhook.sh' '$RemoteDirectory' 'https://qr.twob.cc/telegram/webhook'
docker image prune --force --filter label=com.docker.compose.project=qr-bot-prod --filter label=com.docker.compose.service=app
docker compose --project-name qr-bot-prod --env-file .env ps
"@
    & ssh $SshHost $remoteCommand
    if ($LASTEXITCODE -ne 0) { throw "Remote Docker Compose deployment failed" }

    Write-Host "Deployed qr-bot to ${SshHost}:${RemoteDirectory}"
}
finally {
    $resolvedTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    $resolvedStaging = [System.IO.Path]::GetFullPath($stagingDirectory)
    if ($resolvedStaging.StartsWith($resolvedTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedStaging -Recurse -Force -ErrorAction SilentlyContinue
    }
}
