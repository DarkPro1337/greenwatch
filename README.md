# Greenwatch

Telegram bot that watches [Greenhouse](https://www.greenhouse.io/) job boards and notifies you about new openings that match your filters (offices, Job Category, and other board metadata).

## Features

- Multi-user: each Telegram user has their own watches
- Interactive setup with inline buttons (offices + metadata fields discovered from the public Job Board API)
- SQLite storage (WAL mode)
- Periodic polling for new jobs (default: every 10 minutes)

## Requirements

- JDK 26 (project toolchain) or adjust `jvmToolchain` in `build.gradle.kts`
- A Telegram bot token from [@BotFather](https://t.me/BotFather)

## Run

```bash
export BOT_TOKEN="123456:ABC..."
./gradlew run
```

If you run `MainKt` from IntelliJ, add these **VM options** (JDK 26 otherwise prints reflective-access warnings from SQLite/Gson):

```
--enable-native-access=ALL-UNNAMED --enable-final-field-mutation=ALL-UNNAMED
```

Optional env vars:

| Variable                | Default         | Description                  |
|-------------------------|-----------------|------------------------------|
| `BOT_TOKEN`             | required        | Telegram bot token           |
| `DATABASE_PATH`         | `greenwatch.db` | SQLite file path             |
| `POLL_INTERVAL_MINUTES` | `10`            | How often to poll Greenhouse |

## Bot commands

| Command   | Action                                                                        |
|-----------|-------------------------------------------------------------------------------|
| `/start`  | Help                                                                          |
| `/add`    | Create a watch: enter board token (e.g. `jetbrains`), pick offices/categories |
| `/list`   | List your watches                                                             |
| `/remove` | Remove a watch                                                                |
| `/check`  | Fetch matching jobs now and notify about unseen ones                          |
| `/cancel` | Abort the add wizard                                                          |

## How filters work

Greenhouse Job Board API:

- `GET https://boards-api.greenhouse.io/v1/boards/{token}/jobs?content=true`

Offices and custom metadata options (Job Category, Team, …) are discovered from that jobs payload. The dedicated `/offices` endpoint is not used in the wizard because some boards embed huge nested department trees.

There is no server-side filter for `offices[]` / custom fields (those exist only on the hosted board UI). Greenwatch matches locally:

- selected offices → OR
- each metadata field (e.g. Job Category) → OR within field, AND across fields
- empty selection → no restriction on that dimension

When you create a watch, current matches are marked as seen so you are not flooded with history.

## Develop

```bash
./gradlew test
./gradlew run
```

## Stack

Kotlin, kotlin-telegram-bot, Ktor Client, kotlinx.serialization, Exposed, SQLite.
