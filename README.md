# WMBot

WMBot is a Meteor Client addon for Minecraft 1.21.8. It adds utility modules for 6b6t login and portal flow, stash scanning and kit delivery, inventory helpers, and high-altitude plane building.

## Requirements

Install WMBot as a client-side Fabric/Meteor addon alongside:

- Minecraft `1.21.8`
- Java `21`
- Fabric Loader
- Meteor Client
- Baritone for Minecraft `1.21.8`, either Baritone for Meteor or the normal Baritone release

## Modules

All modules are registered under the `WMBot` Meteor category.

- `6b6t-login`: Sends `/login <password>` after the 6b6t login prompt.
- `6b6t-portals`: Walks through configured 6b6t lobby portals after login.
- `stash-scanner`: Scans loaded stash containers and writes an inventory cache.
- `stash-kitbot`: Handles allowlisted kit requests and delivers matching shulkers.
- `auto-eat-stocker`: Keeps golden apples stocked in the hotbar for Auto Eat.
- `inventory-tools`: Adds quick `Take All` and `Put All` buttons to supported container screens.
- `plane-builder`: Builds an obsidian plane at Y 319 and replenishes supplies as needed.

## HUD

WMBot registers three HUD elements:

- `stash-scanner-stats`
- `stash-kitbot-stats`
- `plane-builder-stats`

## Local Data

WMBot stores local data under the Minecraft run directory in `watchmenbot/`:

- `watchmenbot/stash_inventory_cache.json`
- `watchmenbot/stash_kitbot_queue.json`
- `watchmenbot/stash_kitbot_stats.json`
- `watchmenbot/stash_kitbot_cooldowns.json`

## Development

Build the addon with `./gradlew build`.

Useful verification tasks:

```sh
./gradlew runPureTests
./gradlew runPlanePureTests
./gradlew runInventoryPureTests
```

Build outputs are written to `build/libs`.

Put the remapped WMBot jar in your Minecraft `mods` folder with Meteor Client and exactly one compatible Baritone jar for Minecraft 1.21.8.
