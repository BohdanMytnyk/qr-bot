# QR Bot

A Spring Boot Telegram bot that creates QR deep links and stores finalized content as messages in a private Telegram channel.

## Current flows

- `/start` displays an inline main menu. It is the only command published in Telegram's command menu, as `🏠 Menu`. Callback buttons drive creation, type selection, listing, details, navigation, and deletion. Only content and password/code entry use free-form messages.
- `/start` fully resets pending workflow state and list preferences before showing the menu. Password prompts include a cancel button, and slash-prefixed input is reserved for commands rather than accepted as a password.
- `Create QR` offers `📄 Content`, `1️⃣ Single-use QR`, and `🎟️ Coupon`. Content and single-use QRs may use an optional password/code; coupons are owner-only and always ask their owner for explicit redemption confirmation.
- QR lists show only `ACTIVE` records, so `DELETED` and `REDEEMED` statuses and status controls are hidden. Controls appear above numbered items and provide independent type checkboxes plus `↓ Newest`/`↑ Oldest` creation-date ordering. All three type checkboxes share one row. Preferences are stored on the bot user.
- Expanding an owned item deletes the old navigation and copies the stored content with type, open count, creation date, and navigation controls attached directly to that copied message. A `Show QR and link` action displays its generated QR and deep link, followed by navigation. Owner previews bypass protected-password and one-time redemption rules and do not increment open analytics or redeem the QR.
- The current navigation message ID is stored on `bot_users`. After QR generation, the prior navigation is deleted, the QR image is sent, and a fresh main-menu message is sent last.
- Each added item immediately rebuilds the draft in the private channel and refreshes the owner's live preview; the superseded channel draft and preview are removed. Multiple text items are joined with blank lines. When photos or videos exist, the joined text becomes the first media caption so text and media appear as one Telegram payload. On `✅ Done`, the existing channel draft becomes final and its message IDs are stored on the QR.
- Delivery uses Telegram's bulk `copyMessages` API, preserving album grouping while hiding source attribution. Final QR records do not store text or file IDs.
- Protected content additionally asks the owner for a password/code. The password message is immediately deleted and only a per-QR salted PBKDF2-HMAC-SHA-256 hash is stored.
- A coupon is visible only to its creating Telegram account. An owner scan asks for explicit redemption confirmation before delivery and changing the status to `REDEEMED`.
- A single-use QR is atomically redeemed by the first scanner when unprotected, or by the first scanner with a correct answer when password-protected. Later scans explicitly report that it was already redeemed.
- Scanning the QR opens `https://t.me/<bot>?start=<token>`. Public content is delivered immediately. Protected content asks for the password/code, deletes that message before comparison, and delivers only on a successful match.
- Successful delivery records the user and UTC timestamp, increments the QR open count, and sends a fresh main-menu navigation message after the delivered content. Delivery uses a strategy interface so more QR types can be added without changing the dispatcher.
- New deep links use the QR's UUID directly. Previously issued token links remain readable as legacy aliases.

## Prerequisites

- Java 17 and Maven 3.9+
- MongoDB
- A Telegram bot
- A private Telegram channel where the bot is an administrator with permission to post
- `telegram-common` version `0.0.1` installed in the local Maven repository

## Configuration

Copy `.env.example` to `.env` and set:

- `TELEGRAM_BOT_TOKEN`: token from BotFather
- `TELEGRAM_BOT_USERNAME`: bot username without `@`
- `TELEGRAM_CONTENT_CHANNEL_ID`: numeric private channel ID (typically starts with `-100`)
- `TELEGRAM_RESTART_NOTIFICATION_CHAT_ID`: chat that receives `restarted` after successful startup
- `MONGODB_URI`: MongoDB connection URI
- `QR_BOT_LOG_LEVEL`: application log level, default `INFO`

The bot username must match the bot represented by the token or generated links will open the wrong bot.

## Run locally

First install the shared library:

```powershell
cd ..\telegram-common
mvn install
cd ..\qr-bot
mvn spring-boot:run
```

PowerShell does not automatically import `.env`; export those values into the process environment before running Maven.

## Tests

Run the fast unit suite without Docker:

```powershell
mvn test
```

Run the complete suite, including the Spring Boot webhook E2E test with a disposable MongoDB container:

```powershell
mvn verify -Pdocker-e2e
```

The E2E suite requires a running Docker engine. Telegram is replaced by a local HTTP stub; no real bot token, webhook secret, or external request is used.

## Deploy with Maven, SSH, and Docker Compose

The `docker-deploy` Maven profile runs the normal build and tests, packages the Spring Boot jar, and invokes `deploy/deploy.ps1` during Maven's `deploy` phase. The script copies the jar and deployment assets to `macserver` over SSH/SCP, then runs Docker Compose on the server.

Before the first deployment, copy `deploy/.env.example` to `deploy/.env` and replace every placeholder. This ignored file is transferred over SSH and stored with mode `0600` on the server.

From the `qr-bot` directory:

```powershell
mvn deploy -Pdocker-deploy
```

The profile skips Maven repository publication and performs the remote deployment instead. By default it connects with `ssh macserver` and deploys into `/home/serveradmin/apps/qr-bot-prod`. No ports are published to the LAN. MongoDB is available to the bot over an internal Docker network and on the server's loopback interface solely for SSH forwarding.

All long-lived Docker objects use explicit names:

- Application container/image: `qr-bot-prod-app`
- MongoDB container: `qr-bot-prod-mongodb`
- MongoDB data volume: `qr-bot-prod-mongodb-data`
- Private database network: `qr-bot-prod-mongodb-network`
- Application outbound network: `qr-bot-prod-outbound-network`

Routine Maven deployments leave MongoDB untouched unless its Compose configuration changes, build a new application image, and replace `qr-bot-prod-app`. The explicitly named MongoDB volume is reattached even if its container must be recreated, so application redeployment preserves the same database and data. After a successful deployment, only dangling images labeled for the `qr-bot-prod` application service are pruned.

The deployment also installs `~/bin/mongosh`, a zero-overhead wrapper that opens the shell inside `qr-bot-prod-mongodb` with the restricted application credentials. MongoDB is never published to a LAN-facing interface.

### Access MongoDB from another machine

MongoDB is bound to `127.0.0.1:27018` on the server, so it cannot be reached
directly from the LAN. Start an SSH tunnel from Windows and leave this command
running:

```powershell
ssh -N -L 27018:127.0.0.1:27018 macserver
```

Connect MongoDB Compass or a local `mongosh` to `127.0.0.1:27018`. Use the
restricted application username and password from `deploy/.env`, database
`qr_bot`, and authentication database `qr_bot`. For example:

```powershell
mongosh --host 127.0.0.1 --port 27018 --username qr_app --authenticationDatabase qr_bot qr_bot
```

Enter the password interactively instead of placing it in the command or shell
history. Closing the SSH command immediately removes access from the Windows
machine. Do not change the Compose binding from `127.0.0.1` to `0.0.0.0`.

Useful operations:

```powershell
ssh macserver "cd /home/serveradmin/apps/qr-bot-prod && docker compose ps"
ssh macserver "cd /home/serveradmin/apps/qr-bot-prod && docker compose logs -f app"
ssh macserver "cd /home/serveradmin/apps/qr-bot-prod && docker compose down"
```

The application also writes structured JSON Lines logs to the persistent
`qr-bot-prod-application-logs` Docker volume. Each log event is appended
immediately as one independent JSON object on one line; the file is not one
large JSON array. The active file rolls daily or whenever it reaches 20 MB.
Completed segments are gzip-compressed and retained for 35 days. The volume
survives routine application replacement and `docker compose down`.

Read the current structured log remotely:

```powershell
ssh macserver "docker exec qr-bot-prod-app tail -n 200 /var/log/qr-bot/application.json"
```

Filter the live stream locally by correlation or customer identifier:

```powershell
ssh macserver "docker exec qr-bot-prod-app cat /var/log/qr-bot/application.json" | Select-String 'customerId=123|updateId=456'
```

Download the current log to the Windows machine for offline analysis:

```powershell
ssh macserver "docker exec qr-bot-prod-app cat /var/log/qr-bot/application.json" | Set-Content -Encoding utf8 .\qr-bot-logs.jsonl
```

Inspect the fields emitted by the currently configured Spring Boot structured
logging format:

```powershell
Get-Content .\qr-bot-logs.jsonl | Select-Object -First 1 | ConvertFrom-Json | Format-List *
```

Open the log as an interactive, searchable table:

```powershell
Get-Content .\qr-bot-logs.jsonl |
    ConvertFrom-Json |
    Select-Object '@timestamp', level, logger_name, message |
    Out-GridView
```

Show warnings and errors from the last six hours:

```powershell
$since = (Get-Date).AddHours(-6)

Get-Content .\qr-bot-logs.jsonl |
    ConvertFrom-Json |
    Where-Object {
        [datetimeoffset]$_.'@timestamp' -ge $since -and
        $_.level -in @('WARN', 'ERROR')
    } |
    Select-Object '@timestamp', level, logger_name, message |
    Out-GridView
```

Find events for a particular customer or Telegram update:

```powershell
Get-Content .\qr-bot-logs.jsonl |
    ConvertFrom-Json |
    Where-Object { $_.message -match 'customerId=123|updateId=456' } |
    Select-Object '@timestamp', level, message |
    Format-Table -Wrap
```

Logs may contain customer identifiers and operational details. Keep downloaded
files private and prefer these local tools over uploading logs to a public JSON
viewer.

`docker compose down` preserves the external-name-stable `qr-bot-prod-mongodb-data` volume. Do not add `--volumes` unless permanent database deletion is intended. MongoDB creates its application user only when the data volume is initialized, so changing MongoDB credentials in `.env` later also requires rotating the user inside MongoDB.

The production stack uses Telegram webhooks through the Cloudflare Tunnel. Local development can use the polling configuration.

## Persistence and analytics

- `qr_codes` stores token, type, owner, storage-channel message reference, creation time, and aggregate open count.
- `qr_accesses` stores one row per valid open with QR ID, Telegram user ID, username, and timestamp.
- `bot_users` stores each Telegram user's current workflow state, selected QR type, pending channel message, and pending protected QR. Separate creation/opening session collections are not used.
- `analytics_events` is an append-only customer-action stream for creation, scan, password, delivery, deletion, redemption, and menu funnels. Passwords are never included. A MongoDB TTL index automatically removes events after 31 days.

QR identifiers use random UUIDv4 values. Existing 43-character tokens are retained only so already-issued QR images continue working.
