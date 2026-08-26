# TelegramAuth

Spigot/Paper plugin that registers players through a Telegram bot and requires password + Telegram confirmation before they can play.

## Requirements

- Java 8+
- Spigot or Paper 1.20+
- A Telegram bot token from [@BotFather](https://t.me/BotFather)

## Build

```bash
mvn -DskipTests package
```

The shaded jar is written to `target/TelegramAuth-2.0.jar`.

## Install

1. Put the jar in `plugins/`.
2. Start the server once to generate `plugins/TelegramAuth/config.yml`.
3. Fill in the bot credentials and server details.
4. Restart the server.

The bot stays disabled while `username` or `token` is still `changeme`.

## Configuration

```yaml
username: changeme
token: changeme
server-ip: changeme
telegram-link: changeme
rules-url: changeme
min-password-length: 6
login-timeout-seconds: 60
max-login-attempts: 5
skip-password-on-same-ip: true
admin-telegram-ids: []
```

| Key | Description |
|---|---|
| `username` | Bot username without `@` |
| `token` | Bot token from BotFather |
| `server-ip` | Address shown after registration |
| `telegram-link` | Bot URL shown to unregistered players |
| `rules-url` | Link sent after a successful login |
| `min-password-length` | Minimum password length |
| `login-timeout-seconds` | Kick if the player does not finish auth in time |
| `max-login-attempts` | Wrong-password attempts before kick |
| `skip-password-on-same-ip` | If the IP matches the last login, skip `/login` and only ask for Telegram confirmation |
| `admin-telegram-ids` | Telegram chat IDs allowed to use `/whitelist` in the bot |

Player data is stored in `plugins/TelegramAuth/auth_data.yml`. Passwords are hashed. New hashes are salted SHA-256; older unsalted SHA-256 hashes still verify.

## Auth flow

1. The player registers in Telegram (`/start` → nickname → password) and is added to the whitelist.
2. An unregistered or non-whitelisted player is kicked on login.
3. A registered player joins frozen and muted. They must run `/login <password>` unless the last IP matches and `skip-password-on-same-ip` is true.
4. Telegram asks the linked account to confirm the login.
5. Until confirmation, the player cannot move, chat, use commands (except `/login` / `/l`), break/place blocks, open inventories, or fight.
6. Timeout or too many wrong passwords kicks the player.
7. Denying the login in Telegram kicks the account and rotates the password.

## In-game commands

| Command | Permission | Description |
|---|---|---|
| `/login <password>` | everyone | Authenticate. Alias: `/l` |
| `/changepassword <old> <new> <new>` | everyone | Change password after a successful login. Alias: `/cp` |
| `/whitelist add <player>` | `telegramAuth.whitelist` | Add a registered player to the whitelist |
| `/whitelist remove <player>` | `telegramAuth.whitelist` | Remove from the whitelist and kick if online |
| `/whitelist list` | `telegramAuth.whitelist` | List whitelisted players |

`telegramAuth.whitelist` defaults to `op`.

This plugin registers `/whitelist` and overrides the vanilla command.

## Telegram commands

| Command | Who | Description |
|---|---|---|
| `/start` | anyone | Register, or show the linked account |
| `/kick` | linked account | Kick yourself from the server |
| `/whitelist add\|remove\|list` | `admin-telegram-ids` only | Same whitelist controls as in-game |
| `#message` | linked account, while online | Send that text as the player in Minecraft chat |

Registration nicknames must match Minecraft rules: 3–16 characters, `A-Z`, `a-z`, `0-9`, `_`.

## License

[GNU General Public License v3.0](LICENSE) or later.
