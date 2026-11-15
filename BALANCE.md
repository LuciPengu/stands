# PvP balance pass — v0.1 (draft, tune after playtests)

Goal: no stand is a must-pick or a free win. Every number below lives in `stands.yml`, so tuning = edit + `/stands reload`, no rebuild.

Damage is in HP (2 HP = 1 heart), before armor. Cooldowns in seconds. Player has 20 HP.

## 0. Two damage modes (decided 11/15)

- **Vs stand users:** stand abilities deal **true damage** (ignores armor + Protection; totems/absorption/Resistance still work). Numbers in this file are the stand-vs-stand numbers. Time-stop stored-damage cap applies only here.
- **Vs players without a stand and mobs:** `power.vs-players-without-stand` (3x) / `power.vs-mobs` (4x), normal armor rules, no time-stop cap. Meant to feel unfair.
- Cooldowns: `power.cooldown-multiplier` (0.7).

## 1. Global rules

| Rule | Original | PvP version | Why |
|---|---|---|---|
| Kit shape | 0–3 abilities, random | **Every stand: Basic, Skill (L1), Skill 2 (L2), Ultimate (L3)** | Same number of tools each |
| Rush attacks | Strength IV–V + explosion at your feet, no cooldown | **Barrage cone, 3 blocks: 7 HP over 1s, cd 5s** | The old one could chain-kill and hit yourself |
| Explosions | Break blocks, hurt the caster | **No block damage, no self-damage** (config `griefing:`) | PvP arena friendly |
| Ultimates | 6–30s cd, some none | **60–120s cd, every ult has one** | Ult = a fight-turner, not spam |
| One-shots | Sword Tip, KC Jab, Nothingness, D4C dimension, Tusk spin | **Nothing does more than 12 HP in one hit** | Biggest fairness fix |
| Permanent effects | Spin 99999 ticks, dual existence | **Max 6s for any debuff** | Always counterplayable |
| Damage budget | — | **Close-range ~18 HP / 10s, ranged ~14, control ~10 + strong CC** | The core balance knob |
| Time stop | 4–10s freeze of the whole world | **2–3s, 30-block radius, see §2** | It was the auto-win |
| Stand Arrow | 10% chance: 30 dmg, no stand | Kept, configurable (`arrow-fail-chance`) | Iconic |
| Levels | 30 / 60 XP levels | Kept, configurable | |

## 2. Time stop (The World, Star Platinum, King Crimson)

- Freeze = Slowness 255 + jump lock + can't attack/use items + projectiles freeze in the air. Caster moves freely.
- Damage the caster does to frozen players is **stored and applied when time resumes, capped at 10 HP total**. Stops "stop time, kill" combos and keeps the anime "knives everywhere" moment.
- **Counterplay:** another time-stop user caught in it can move after 1s ("I can move in stopped time").
- Durations: **The World 3s**, **Star Platinum 2s** (but SP gets a stronger Basic), cd 90s.
- **King Crimson** instead: 3s Time Erase — caster becomes untargetable/invisible ghost and can reposition, nobody is frozen, ends with a guaranteed Epitaph-style 6 HP hit if they attack within 1s. cd 75s.

## 3. Per-stand changes

Role: **C** = close-range, **R** = ranged, **X** = control/utility.

| Stand | Role | Basic | Skill (L1) | Skill 2 (L2) | Ultimate (L3) | Main nerf / buff |
|---|---|---|---|---|---|---|
| Star Platinum | C | ORA rush 8 HP / cd 5 | Star Finger poke 5 HP, 6 blocks / cd 8 | Block-throw 4 HP + knockback / cd 10 | Time stop 2s / cd 90 | Strength V → fixed numbers |
| The World | C | MUDA rush 7 HP / cd 5 | Knives ×5, 3 HP each / cd 12 | Short dash 5 blocks / cd 10 | Time stop 3s / cd 90; shift: Road Roller 10 HP AoE / cd 120 | 7s → 3s stop |
| King Crimson | C | Rush 7 / cd 5 | Jab 10 HP / cd 14 (was Strength XV) | Time Skip teleport 9 blocks / cd 12 | Time Erase 3s ghost / cd 75 | Jab one-shot removed |
| Killer Queen | X | Punch 6 / cd 5 | Touch bomb: tag a block/player, detonate 8 HP / cd 10 | Sheer Heart Attack-style seeker 5 HP / cd 20 | Bites the Dust: at ≤6 HP, heal to 12 + TP to spawn / cd 180 | BtD heals half, not full |
| Gold Experience | X | Rush 6 / cd 5 | Life Giver: spawn 1 mob that fights for you 8s / cd 15 | Life Boost: Speed II + Strength I 6s / cd 20 | Life Sensor: enemies within 40 glow 8s / cd 60 | Speed III → II |
| GE Requiem | C | Rush 8 / cd 5 | Life Giver / cd 12 | Life Boost / cd 18 | **Return to Zero:** next lethal hit within 5s is cancelled and the attacker takes 8 / cd 120 | Removed per-tick full heal (was invincible) |
| Magician's Red | R | Fireball 5 + fire 3s / cd 4 | Flame wall 4 blocks / cd 12 | Power Boost: Strength I 6s / cd 20 | Crossfire Hurricane: 5 fireballs, 4 each / cd 75 | Structure paste only cosmetic |
| Silver Chariot | C | Lunge 6 HP, 3 blocks / cd 4 | Rapier flurry 7 / cd 6 | Sword Tip shot 7 HP, kb 3 / cd 15 | Armor Off: Speed III + 20% more damage taken 8s / cd 60 | Sword Tip 10 dmg kb 20 → 7 kb 3; no more "set HP to 1 heart" |
| The Hand | X | Punch 6 / cd 5 | Air Erase: pull target 5 blocks to you / cd 10 | Erase block 3×3 (arena-safe) / cd 8 | DELETE: 10 HP ignoring armor, 3-block reach / cd 90 | Void-dimension instakill removed |
| Weather Report | R | Lightning 5 HP / cd 8 | Wind Boost Speed II 5s / cd 15 | Gust knockback cone / cd 10 | Heavy Weather: Blindness + Slowness II 4s, radius 10 / cd 90 | Lightning had no cooldown |
| Hierophant Green | R | Emerald Splash 4 × 2 HP / cd 6 | Web line / cd 14 | Tendril grab: root 1.5s / cd 16 | 20m Emerald Splash 8 × 2 HP spread / cd 75 | |
| Sticky Fingers | C | ARI rush 7 / cd 5 | Zipper dash 6 blocks through a wall / cd 12 | Zipper trap block 6 HP on step / cd 15 | Zip-away: 3s invisible in a zipper, heal 4 / cd 75 | Zipper 15 dmg per tick → 6 once |
| Aerosmith | R | Minigun 8 × 1 HP, 25 blocks / cd 6 | Bomb 7 HP AoE / cd 12 | CO₂ radar: see enemies 6s / cd 25 | Strafing run 12 HP total / cd 75 | 40-block explosion spam removed |
| Tusk Act 4 | R | Nail shot 4 HP / cd 3 | Nail ×5 / cd 12 | Golden spin shot 6 / cd 15 | Infinite Rotation: 1 HP/s + can't sprint 6s / cd 100 | Permanent spin → 6s |
| D4C | X | Punch 6 / cd 5 | Dimension hop: 1.5s untargetable + 8-block reposition / cd 20 | Clone decoy 6s / cd 25 | Love Train lite: 50% damage reduction 5s / cd 100 | Parallel-world DOT (instakill) removed |
| Crazy Diamond | C | DORA rush 8 / cd 5 | Restore: heal self 6 HP over 3s / cd 20 | Fix item durability / cd 30 | Restore Ally: heal teammates 8 / cd 75, **no longer heals enemies** | |
| Purple Haze | X | Virus punch 5 + Poison II 4s / cd 7 | Rage Mode 8s: basics have no cd / cd 45 | Capsule throw: Poison II cloud / cd 18 | Toxic Haze: Wither II 5s, radius 5, hits you too / cd 90 | Poison XX → II; Wither 255 → II |
| Echoes Act 3 | X | Punch 5 / cd 5 | FWOOSH fire / cd 6 | SPLASH water / cd 8 | Three Freeze: Slowness IV + no jump 3s, single target / cd 60 | 100s slowness 255 → 3s |
| Metallica | X | Iron spikes 5 HP / cd 6 | Razor blades: Bleed 1 HP/s 5s / cd 15 | Iron grab: pull nearest iron weapon user / cd 20 | Invisibility 5s, ends on attack / cd 60 | 10s invis → 5s |
| Anubis | C | Sword (8 HP melee) | Anubis Sense: Speed II + Jump II 5s / cd 15 | Memory: +1 dmg per hit on the same target, max +4 / passive | Possession dash 9 HP / cd 60 | Passive Strength V → stacks |
| Hermit Purple | X | Whip 6 HP, 5 blocks / cd 4 | Grapple swing 10 blocks / cd 10 | Vine trap slow / cd 14 | Hermit Boost: Regen I + Speed I 8s / cd 90 | Was 5 buffs at once |
| The Emperor | R | Gun 4 HP, 40-block hitscan / cd 1.2 | Curving shot (homes a bit) / cd 6 | Slide dash / cd 10 | Bullet storm 6 × 3 HP / cd 75 | 250-block no-cd explosion gun → real gun |

## 4. How we'll check it

1. Each stand starts at the role budget above (sum of damage per 10s in a duel loop).
2. Playtest 1v1s on the test server; log kills per stand (`/stands stats`).
3. Any stand with >60% win rate over 20+ fights gets −10% damage or +10% cooldown and goes back in.
