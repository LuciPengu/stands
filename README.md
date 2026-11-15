# Stands

JoJo-style stands for Paper servers. Players need no mods: the server sends the models and sounds as a resource pack.

A remake of my 2020 Forge mod as a server plugin, with the original models, skins and sounds.

- 22 stands, each with a basic attack, two skills and an ultimate
- Animated stand models that follow the player
- Time stop, with counterplay for other time-stop users
- Stand-vs-stand damage ignores armor, so fights don't depend on gear
- Duels (`/stand duel`) where nobody dies or drops items, and opt-in PvP
- Stands level up from fighting
- An in-game guide: `/stand guide`

## Install

1. Paper 26.3 or newer, Java 25.
2. Put `stands-1.0.0.jar` and [BetterModel](https://modrinth.com/plugin/bettermodel) 3.6.0+ in `plugins/`.
3. Start the server once. Stands installs its models into BetterModel by itself.
4. In `plugins/Stands/config.yml`, set `pack.public-url` to `http://<your server address>:8765` and open port 8765. Without this, players won't see the models or hear the sounds.

## Controls (hold the Stand Disc)

| Key | Action |
|---|---|
| Q | Summon / unsummon |
| Right-click | Basic attack |
| Sneak + Right-click | Skill |
| F | Skill 2 (stand level 2) |
| Sneak + F | Ultimate (stand level 3) |

Get a stand by crafting a Stand Arrow (gold, gold / soul sand, gold / stick) and right-clicking it.

## Commands

| Command | What it does |
|---|---|
| `/stand guide` | Controls and every stand's abilities |
| `/stand info` | Your stand, level and XP |
| `/stand pvp` | Turn stand PvP on or off |
| `/stand duel <player>`, `/stand accept` | Duels |
| `/stand levelup` | Pay XP levels to level your stand |
| `/stand disc` | Get your Stand Disc back |
| `/stand give`, `switch`, `arrow`, `setlevel`, `remove`, `testmode`, `reload` | Admin (`stands.admin`) |

Every damage number, cooldown and XP value is in `config.yml`.

## Building

`cd plugin && gradle build` (Java 25). `tools/` has the scripts that converted the original mod's models and added the animations.

Fan project. Not affiliated with or endorsed by the owners of JoJo's Bizarre Adventure.
