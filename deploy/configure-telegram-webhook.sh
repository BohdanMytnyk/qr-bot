#!/usr/bin/env bash
set -euo pipefail

deployment_directory=${1:-/home/serveradmin/apps/qr-bot-prod}
webhook_url=${2:-https://qr.twob.cc/telegram/webhook}

set -a
. "${deployment_directory}/.env"
set +a

curl --fail-with-body --silent --show-error \
    --request POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setWebhook" \
    --data-urlencode "url=${webhook_url}" \
    --data-urlencode "secret_token=${TELEGRAM_WEBHOOK_SECRET}"
printf '\n'
