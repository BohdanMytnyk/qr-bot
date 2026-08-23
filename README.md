# QR Bot

A Spring Boot Telegram bot that creates QR deep links and stores finalized content as messages in a private Telegram channel.

## Current flows

- `/start` displays an inline main menu. It is the only command published in Telegram's command menu, as `🏠 Menu`. Callback buttons drive creation, type selection, listing, details, navigation, and deletion. Only content and password/code entry use free-form messages.
- `/start` fully resets pending workflow state and list preferences before showing the menu. Password prompts include a cancel button, and slash-prefixed input is reserved for commands rather than accepted as a password.
- `Create QR` asks for `Content`, `Protected content`, or owner-only `One-time content`; `My QRs` lists the owner's recent QRs as descriptive inline buttons showing type, status, and successful opens.
- QR lists show only `ACTIVE` records, so `DELETED` and `REDEEMED` statuses and status controls are hidden. Controls appear above numbered items and provide independent type checkboxes plus `↓ Newest`/`↑ Oldest` creation-date ordering. All three type checkboxes share one row. Preferences are stored on the bot user.
- Expanding an owned item deletes the old navigation and copies the stored content with type, open count, creation date, and navigation controls attached directly to that copied message. A `Show QR and link` action displays its generated QR and deep link, followed by navigation. Owner previews bypass protected-password and one-time redemption rules and do not increment open analytics or redeem the QR.
- The current navigation message ID is stored on `bot_users`. After QR generation, the prior navigation is deleted, the QR image is sent, and a fresh main-menu message is sent last.
- Each added item immediately rebuilds the draft in the private channel and refreshes the owner's live preview; the superseded channel draft and preview are removed. Multiple text items are joined with blank lines. When photos or videos exist, the joined text becomes the first media caption so text and media appear as one Telegram payload. On `✅ Done`, the existing channel draft becomes final and its message IDs are stored on the QR.
- Delivery uses Telegram's bulk `copyMessages` API, preserving album grouping while hiding source attribution. Final QR records do not store text or file IDs.
- Protected content additionally asks the owner for a password/code. The password message is immediately deleted and only a per-QR salted PBKDF2-HMAC-SHA-256 hash is stored.
- One-time content is visible only to its creating Telegram account. An owner scan asks for explicit redemption confirmation before delivery and changing the status to `REDEEMED`; other users and subsequent scans receive the same not-found response.
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

## Run with Docker Compose

From this directory, after creating `.env`:

```powershell
docker compose up --build -d
docker compose logs -f bot
```

## Persistence and analytics

- `qr_codes` stores token, type, owner, storage-channel message reference, creation time, and aggregate open count.
- `qr_accesses` stores one row per valid open with QR ID, Telegram user ID, username, and timestamp.
- `bot_users` stores each Telegram user's current workflow state, selected QR type, pending channel message, and pending protected QR. Separate creation/opening session collections are not used.
- `analytics_events` is an append-only customer-action stream for creation, scan, password, delivery, deletion, redemption, and menu funnels. Passwords are never included.

QR identifiers use random UUIDv4 values. Existing 43-character tokens are retained only so already-issued QR images continue working.
