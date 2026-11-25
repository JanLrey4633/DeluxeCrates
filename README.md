# DeluxeCrates 1.0.0

Paper plugin (Java 21, Paper API 1.21.11). Maven project.

## Build

```bash
mvn clean package
```

Copy `target/DeluxeCrates-1.0.0.jar` into `plugins/` and restart the server.

## Commands

- `/dc help`
- `/dc list`
- `/dc create <id>` creates a starter crate config.
- `/dc setcrate <id>` binds the block you are looking at (max 6 blocks) to a crate.
- `/dc key <player> <id> [amount]` gives plugin-bound keys.
- `/dc convert <file.yml>` imports a CrazyCrates crate file from `plugins/DeluxeCrates/import/`.
- `/dc reload` reloads configs and crate definitions.

## Importing CrazyCrates

1. Put the original crate YAML in `plugins/DeluxeCrates/import/`.
2. Run `/dc convert PosaidonCrate.yml`.
3. The generated crate config is saved in `plugins/DeluxeCrates/crates/`.

The importer keeps the complete original CrazyCrates section in `crazycrates-original` and also maps common fields and prize weights into DeluxeCrates' runtime format. Serialized `Items: Data:` payloads are retained verbatim. If the legacy serialized payload cannot be decoded by the server's Bukkit object stream, the configured display item is used as the fallback reward; verify imported prizes on a test server before production use.

## Crates

Right-click a registered crate block to open the crate menu. Left-click a crate icon to open it; right-click to preview the configured display items. Keys are plugin-bound items and cannot be substituted with ordinary items. Prize selection is weighted by each prize's `weight` value.

## Discord

Set `discord.enabled: true`, then set `discord.webhook-url` and optionally customize `username` and `message` in `config.yml`. Keep the webhook URL private. The webhook posts the player, crate, prize, time and date when a prize is awarded.

## Notes

- Use a full server restart after replacing the JAR; avoid `/reload`.
- Test imported serialized rewards, command rewards, and crate locations on a staging server before using on a live server.
- This build does not include a fully featured in-game crate editor, hologram integration, mass-open, animated roulette, or Geyser-specific name decoration.
