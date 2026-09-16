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

## Deploy

Push to `main` builds a Docker image (JDK 26), pushes it to GHCR, and restarts the container over SSH.

### One-time: VPS

SSH in as an admin user, then create a dedicated `deploy` account (no sudo) and put the CI public key in **that** user’s `authorized_keys`:

```bash
sudo adduser --disabled-password --gecos "" deploy
sudo usermod -aG docker deploy

sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -fsSL "https://github.com/docker/compose/releases/download/v2.32.4/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

sudo mkdir -p /opt/greenwatch/data /home/deploy/.ssh
sudo chown -R deploy:deploy /opt/greenwatch /home/deploy/.ssh
sudo chmod 700 /home/deploy/.ssh
# bind-mounted SQLite dir; the image entrypoint chowns it to uid 1000 on start
sudo chown 1000:1000 /opt/greenwatch/data

sudo nano /home/deploy/.ssh/authorized_keys   # paste the CI public key
sudo chmod 600 /home/deploy/.ssh/authorized_keys
sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys

sudo nano /opt/greenwatch/.env               # BOT_TOKEN=...
sudo chown deploy:deploy /opt/greenwatch/.env
sudo chmod 600 /opt/greenwatch/.env
```

`.env` stays on the server and is never overwritten by CI. SQLite lives in `/opt/greenwatch/data`.

### One-time: GitHub

1. Generate a deploy key on your laptop (do not reuse your personal SSH key):

   ```bash
   ssh-keygen -t ed25519 -f greenwatch-deploy -N "" -C "github-actions-greenwatch"
   ```

   Put `greenwatch-deploy.pub` on the VPS (`authorized_keys` above).  
   Put the **private** key into a GitHub secret. Delete the local private key when done if you do not need it.

2. Repo **Settings → Secrets and variables → Actions**:

   | Secret            | Value                           |
   |-------------------|---------------------------------|
   | `SSH_HOST`        | VPS IP                          |
   | `SSH_USER`        | `deploy`                        |
   | `SSH_PRIVATE_KEY` | contents of `greenwatch-deploy` |
   | `SSH_PORT`        | `22` (optional)                 |

3. After the first successful image push: GitHub → **Packages** → `greenwatch` → Package settings → link it to this repository (needed so Actions can pull with `GITHUB_TOKEN`). If the pull on the VPS returns 403, make the package public or leave it private and keep the login step in the workflow.

`BOT_TOKEN` is **not** a GitHub secret; it stays in `/opt/greenwatch/.env`.

### Ship it

Merge or push to `main`, or run **Actions → Deploy → Run workflow**. Check:

```bash
docker compose -f /opt/greenwatch/compose.yaml ps
docker logs -f greenwatch
```

## Stack

Kotlin, kotlin-telegram-bot, Ktor Client, kotlinx.serialization, Exposed, SQLite.
