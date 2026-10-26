<div align="center">

**English｜[中文](README_zh_TW.md)**

# 🗄️ Cupboard

A Rust-style territory protection plugin for Minecraft PvP servers.

![Platforms](https://img.shields.io/badge/platforms-Spigot%20%7C%20Paper%20%7C%20Purpur%20%7C%20Folia%20%7C%20Leaves%20%7C%20Luminol-blue)
![Minecraft](https://img.shields.io/badge/minecraft-1.20%2B-brightgreen)
![Java](https://img.shields.io/badge/java-17%2B-orange)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

</div>

Cupboard lets players protect their builds and items with a single **Block of Gold**, while destructive mechanics like TNT raiding stay in play — fully configurable to match your server's balance and style.

---

## Table of Contents

- [Description](#description)
- [Features](#features)
- [Protection Details](#protection-details)
- [Commands & Permissions](#commands--permissions)
- [Installation](#installation)
- [Language Files](#language-files)
- [Compatibility](#compatibility)
- [License](#license)
- [Credits](#credits)

---

## Description

Cupboard adds a territory protection system similar to Rust. By placing a Block of Gold, players claim a **19×19×19** cube area centered on the block. Within this area, unauthorized players cannot place or break blocks — but explosives like TNT remain effective (configurable), so raiding stays part of the game without breaking Minecraft's core mechanics.

## Features

| Category | Highlights |
|---|---|
| 🛡️ Protection | 19×19×19 area centered on a Block of Gold, access granted via right-click |
| 💥 TNT | Configurable TNT damage in protected areas; TNT can destroy obsidian, water, and lava |
| 🧟 Creeper | Creeper explosion damage blocked by default (configurable) |
| 🌀 Nether Portals | Can't be blocked by solid blocks, covered by lava, or used by non-player entities; search radius is customizable |
| 🐷 Extras | PigZombies drop Nether Wart |
| 🌍 World Border | Configurable, can shrink over time, adjustable scale for the Nether and The End |
| 🌐 Localization | Fully translatable via language files |

## Protection Details

**✅ Protected**
1. Unauthorized players cannot place or break blocks
2. Unauthorized players cannot use stone pressure plates or stone buttons
3. Mobs cannot trigger stone pressure plates
4. Creepers cannot destroy blocks within the protected area

**❌ Not Protected**
1. Unauthorized players can place TNT, which ignites immediately (configurable)
2. Unauthorized players can still use wooden pressure plates/buttons, chests, and armor stands
3. TNT can destroy blocks inside the protected area (configurable)

## Commands & Permissions

- `/kill` — lets a player kill themselves (useful if trapped in a protected area)
- No permission system required
- Operators in Creative Mode automatically bypass protections

## Installation

1. Download the plugin `.jar` file
2. Place it in the `plugins/` folder of your server
3. Restart the server
4. Edit `config.yml` and the locale file (`locales/`) to adjust settings

## Language Files

Locale files live under `plugins/Cupboard/locales/`. Point `config.yml`'s `locale` key to a file name (without `.yml`) to switch languages. Bundled locales:

| Locale key | Language |
|---|---|
| `en` | English (default) |
| `zh` | Chinese (Simplified) |
| `zh_TW` | Chinese (Taiwan, Traditional) |
| `zh_HK` | Chinese (Hong Kong, Traditional) |

To add your own translation, create a new `.yml` file in `locales/` using the same keys, then point `locale` to its file name.

## Compatibility

Tested and compatible with Spigot, Bukkit, Paper, Purpur, Leaves, Folia, and Luminol.

- Targets the Minecraft 1.20 API baseline (1.20.x and newer)
- Requires Java 17+
- Folia is supported through the compatibility scheduler layer
- Purpur is supported as a Paper fork with the same Bukkit/Paper API surface

## License

MIT, GPL-3.0, etc.

## Credits

Developed by [YourNameHere].
Inspired by the territory protection system in Rust.
