# JoJo mod (`jjbamodtst`, MCreator / Forge 1.14.4) - stand reference for a Paper port

Source root: `decompiled/net/mcreator/jjbamodtst/` (abbreviated `src/` below).
Names: `assets/jjbamodtst/lang/en_us.json`, `assets/jjbamodtst/sounds.json`; data: `decompiled/data/jjbamodtst/` (advancements, recipes, structures).
Line ranges are approximate lines in the Vineflower output. Anything uncertain is marked `(?)`.
Total: **22 stands** (18 have a spawnable entity, 4 are item/block based: Metallica, Anubis, Hermit Purple, The Emperor).

Short file aliases used below (all under `src/procedures/`):

| Alias | File | Bound to |
|---|---|---|
| SUMMON-A | `SummonStandOnKeyPressedOnKeyPressedProcedure.java` (2339 lines) | summon key (stands: Metallica, SC, TW, MR, SP, KQ, GE, KC, GER, Anubis, HP, Emperor, Hand, WR, HG) |
| SUMMON-B | `SummonStandOnKeyPressed2Procedure.java` (928) | summon key 2 (SF, Aerosmith, Tusk, D4C, CD, PH, Echoes) |
| MAIN-A | `MainStandAttackOnKeyPressedProcedure.java` (3818) | main attack key |
| MAIN-B | `MainStandAttackOnKeyPressed2Procedure.java` (2356) | main attack key 2 (Hamon, SF, Aero, Tusk, D4C, CD, PH, HP, Echoes) |
| ULT-A | `UltimateStandAttackProcedureProcedure.java` (1822) | ultimate key |
| ULT-B | `UltimateStandAttackOnKeyPressed2Procedure.java` (1151) | ultimate key 2 (SF, Aero, Tusk, D4C, CD, PH, Echoes, HP) |

---

# 1. Core systems

## 1.1 Stand storage = vanilla advancements (no NBT/score)
All state is stored as **advancements** with an `minecraft:impossible` trigger (`decompiled/data/jjbamodtst/advancements/*.json`). Procedures test them with `ServerPlayerEntity.getAdvancements().getProgress(...).isDone()` and grant/revoke them directly or via `advancement revoke @p only ...`.

| Advancement id | Meaning |
|---|---|
| `standactivated` | player owns a stand (gate for everything) |
| `<name>stand` | which stand. Ids: `starplatinumstand, theworldstand, kingcrimsonstand, killerqueenstand, goldexperiencestand, gerstand, magiciansredstand, silverchariotstand, thehandstand, weatherreportstand, heirophantgreenstand, stickyfingersstand, aerosmithstand, tusk4stand, d4cstand, crazydiamondstand, purplehazestand, echoesact3stand, metallicastand, anubisstand, hermit_purple_stand, theemperorstand` |
| `standon` / `standoff` | stand currently summoned / not summoned (mutually exclusive: summon grants one and revokes the other) |
| `standlevel2`, `standlevel3` | stand level (level 1 = neither) |
| `hamonactivated`, `vampireactivated` | Hamon user / vampire |

Other persistent state: entity NBT double `KingCrimsonTimeErasure` (King Crimson time-erase counter, `entity.getPersistentData()`).
Potion effects used as state/cooldown flags: `maincooldown`, `ultimatecooldown`, `timestop`, `despawnnoanimation`, `thespin`, `ragemode`, `dualexistancedamadge` (see 1.5).

## 1.2 How a stand is obtained
* **Random Stand Arrow** (`item/StandArrowItem.java`, proc `StandArrowRightClickedInAirProcedure.java`, 683 lines). Recipe (`recipes/standarrowrecipie.json`, shaped): row1 gold ingot, gold ingot / row2 soul sand, gold ingot / row3 stick -> 1 arrow. Right click in air:
  1. Always removes 1 Stand Arrow from inventory.
  2. If the player has no `standactivated`: roll `Math.random()` = r. **r >= 0.90 (10%): player takes 30 generic damage and gets nothing** (lethal at 20 HP). Otherwise deals **3 generic damage** and grants `standactivated` + one stand by band: r>0.85 Star Platinum (5%); >=0.80 Gold Experience (5%); >=0.75 The World; >=0.70 King Crimson; >=0.65 The Emperor; >=0.60 Anubis; >=0.55 The Hand; >=0.50 Magician's Red; >=0.45 Metallica; >=0.40 Weather Report; >=0.35 Hierophant Green; >=0.30 Silver Chariot; >=0.25 Sticky Fingers; >=0.20 Aerosmith; >=0.15 Tusk Act 4; >=0.10 D4C (each 5%); >=0.075 Purple Haze (2.5%); >=0.05 Crazy Diamond (2.5%); <0.05 Killer Queen (5%). Hermit Purple and Echoes Act 3 are **not** in the random table.
  3. If the player already has a stand that is not Gold Experience: 30 damage. If Gold Experience and `standlevel3`: grant `gerstand`, revoke `goldexperiencestand`, `standon`, `standoff`, despawn the GE entity (Requiem evolution; player must re-summon). If GE but below level 3: chat "your gold experience must be level 3 to obtain requiem".
* **Stand-specific arrows** (not craftable; no recipe, creative tab "Stands"): one item per stand, each with its own tiny procedure (~60 lines): `AerosmithStandArrowRightClickedProcedure, AnubisStandActivationProcedure, CrazyDiamondStandArrowOnRightClickedProcedure, D4CStandArrowRightClickedProcedure, EchoesAct3StandArrowProcedure, GoldExperienceStandArrowRightClickedInAirProcedure, HeirophantGreenStandArrowRightClickedProcedure, HermitPurpleStandActivateProcedure, KillerQueenStandArrowRightClickedInAirProcedure, KingCrimsonStandArrowRightClickedinAirProcedure, MagiciansRedStandArrowProcedure, MetallicaStandArrowProcedureProcedure, PurpleHazeStandArrowRightClickedProcedure, SilverChariotStandArrowRightClickedProcedure, StarPlatinumStandArrowRightClickedInAirProcedure, StickyFingersStandArrowRightClickedProcedure, TheEmperorStandArrowRightClickedProcedure, TheHandStandArrowProcedure, TheWorldStandArrowRightClickedInAirProcedure, TuskAct4StandArrowRightClickedProcedure, WeatherReportStandArrowRightClickedProcedure`. Each: if `standactivated` -> chat "you already have a stand" else grant `standactivated` + `<stand>stand`. The item is **not consumed** and there is no damage.
* **Memory Disc** (`item/StandEraserItem.java`, `StandEraserRightClickedInAirProcedure.java`). Recipe shapeless: 4 iron ingot + soul sand. If the user has stand/vampire/hamon: if `standon` -> "please deactivate your stand to wipe your memory"; else runs `advancement revoke @p everything`, broadcasts "a player has wiped their memory and removed their abilities", `effect clear @p jjbamodtst:maincooldown`, `effect clear @p jjbamodtst:ultimatecooldown`, consumes 1 disc. Otherwise "you dont have a stand".

## 1.3 Summon / de-summon (the "Summon Stand" key)
SUMMON-A / SUMMON-B. If `standactivated`, for the owned stand:
* **Entity stands**: if `standon`: grant `standoff`, run `effect give @e[type=jjbamodtst:<stand>,limit=1,sort=nearest] jjbamodtst:despawnnoanimation 255 255 true`, action-bar message "<Name> De-Activated", `advancement revoke @p only jjbamodtst:standon`. Else: grant `standon`, run `summon jjbamodtst:<stand>` (spawns at the player), message "<Name> Activated!", `advancement revoke @p only jjbamodtst:standoff`.
  * On spawn, `SummonSoundEffectProcedure.java` (called from each entity's `onInitialSpawn`) plays sound `jjbamodtst:summonstand` (vol 60, pitch 1), runs `particle flash ~ ~ ~ 0 0 0 0 10` and `data merge entity @e[type=!minecraft:player,sort=nearest,limit=1] {Invulnerable:1}` (marks the nearest non-player entity invulnerable = the stand).
  * The `despawnnoanimation` potion tick (`DespawnNoAnimationOnPotionActiveTickProcedure.java`) plays `unsummonstand` (vol 60), `particle flash ~ ~ ~ 0 0 0 0 10`, then `entity.remove()` server side. So "despawn" = give the stand entity this potion.
  * Weather Report: de-summon also runs `weather clear`; summon runs `weather rain` (SUMMON-A ~2043-2212).
  * Gold Experience Requiem summon: sets player health to 20 and plays `geractivation` (SUMMON-A 1215-1367).
  * King Crimson summon/desummon: if the player is not in creative: `setGameType(SURVIVAL)` then `effect clear @e minecraft:jump_force`, `effect clear @e minecraft:slow_falling`, `effect clear @e minecraft:slowness`, `effect clear @e minecraft:timestop` (note: these clear the effect from **all entities**; `jump_force` and `minecraft:timestop` are not valid 1.14 effect ids, so those two commands probably error (?)) (SUMMON-A 999-1214).
* **Item stands (Metallica, Anubis, Hermit Purple, Emperor)**: no entity. Summon gives the item (`ItemHandlerHelper.giveItemToPlayer`: Anubis axe, Hermit Purple "whip", The Emperor gun; Metallica gives nothing) plus `unsummonstand`/`summonstand` sound (vol 60) and `particle flash`. De-summon removes up to 36 of that item from the inventory (`inventory.clearMatchingItems(..., 36)`). Metallica just toggles `standon`/`standoff`.
* Summon key ignores cooldowns and time stop. Standard messages are actionbar text.

## 1.4 Stand follow, stats, immunities
* **Follow** (`StandFollowProcedure.java`, `PlayerTickEvent` END, every tick): while `standon`, for the owner's stand runs `execute unless entity @e[type=jjbamodtst:<stand>,distance=..5] run tp @e[type=jjbamodtst:<stand>,distance=5..,limit=1,sort=nearest] @p` (executed at the player's position, so the stand is teleported onto the nearest player when it is more than 5 blocks away). Done for all 18 entity stands (SP, GE, TW, GER, KC, KQ, Hand, WR, MR, HG, SC, SF, Aero, Tusk, D4C, CD, PH, Echoes). Multi-player caveat: `@e[...limit=1]`/`@p` select globally, so two owners of the same stand type conflict.
* **Entities** (`src/entity/*Entity.java`, all identical templates): `CreatureEntity`, `EntityClassification.MONSTER`, size 0.6 x 1.8, fire immune, tracking range 64, max HP 1000, movement speed 0.3, attack damage 30 (no target AI, never attacks on its own), goals: TemptGoal(3.0, air), RandomWalking(1.0), Swim, LookAt(Player, 400). `attackEntityFrom` returns false for: arrows, **damage whose direct source is a player**, potion entities, fall, drowning, cactus, lightning (?) (SRG constants field_76379_h/76369_e/76367_g/180137_b). Each has its own custom model (Java `EntityModel`) + texture in `assets/jjbamodtst/textures/`. Aerosmith additionally has an idle ambient sound (`aerosmith_idle`). Stands do nothing themselves: **every "stand attack" is a buff/effect/explosion applied to the player**; the stand entity is only a visual escort.
* The main-attack "Ora/Muda" attacks are: potion Strength + an explosion at the block the player looks at (see 1.7 for exact helper semantic).

## 1.5 Custom potion effects (`src/potion/`)
| Effect id | Behavior |
|---|---|
| `maincooldown` | harmful, `isReady()` true; no tick logic. Presence blocks MAIN keys. Shown as an icon. |
| `ultimatecooldown` | same, blocks ULT keys. |
| `timestop` | no tick logic; flag. Presence blocks MAIN, ULT keys and Anubis' in-hand buff. |
| `despawnnoanimation` | each tick removes the entity + sound/particle (see 1.3). |
| `thespin` | each tick: yaw += 20.0 and 1.0 generic damage (`TheSpinOnPotionActiveTickProcedure.java`). |
| `ragemode` | beneficial; on expiry sends chat "Rage Mode Deactivated" (`RageModePotionExpiresProcedure.java`). Used as a gate for Purple Haze. |
| `dualexistancedamadge` | each tick 6.0 generic damage (`ParallelDimensionKillProcedure.java`). I-frames mean ~1 hit/10 ticks in practice (?). |

## 1.6 Keybinds (client -> server packet, `src/keybind/`)
Each is a Forge `KeyBinding`, category `key.categories.misc`, sends a `KeyBindingPressedMessage(type 0)` on **key press** (GLFW action 1, only when no GUI is open); the server handler (`context.getSender()`) calls the procedure with `entity`, `x,y,z`, `world`. There are two bindings per action because MCreator procedures hit a size limit; both are registered with the **same default key**, so one press runs both procedures (each only acts for its own set of stands). Names are in `lang`.

| Action (lang key) | Default key (GLFW code) | Procedures |
|---|---|---|
| Summon Stand (`summonstand`, `summonstand2`) | **M** (77) | SUMMON-A + SUMMON-B |
| Main Stand Attack (`mainstandattack`, `mainstandattack2`) | **N** (78) | MAIN-A + MAIN-B |
| Ultimate Stand Attack (`ultimatestandattack`, `ultimatestandattack2`) | **B** (66) | ULT-A + ULT-B |
| Level Up Button (`levelupbutton`) | **V** (86) | `LevelUpButtonOnKeyPressedProcedure.java` |

That is 7 bindings (3 x 2 + 1). Modifiers are read server-side from the player: **Shift = `entity.isShiftKeyDown()`** and **Sprint = `entity.isSprinting()`**; many abilities branch on these. Many second-tier abilities also require `standlevel2` ("L2").

## 1.7 Common mechanics
* **Cooldown mechanism**: MAIN keys run only if the player has neither `maincooldown` nor `timestop`. ULT keys run only if the player has `standlevel3` and neither `ultimatecooldown` nor `timestop`. Each ability applies its own cooldown by giving itself the effect (`new EffectInstance(MainCooldownPotion, ticks, 1, false, false)`). **Many basic attacks apply no cooldown at all** (marked "no cd" below). Cooldown numbers below are ticks (20 = 1 s). Exception: Purple Haze "Enraged Viral Punch" runs outside the MAIN cooldown check (but still blocked by `timestop`).
* **Level-up** (`LevelUpButtonOnKeyPressedProcedure.java`, 195 lines): needs `standactivated`. Level 1->2 costs **30 XP levels** (`addExperienceLevel(-30)`), grants `standlevel2`; level 2->3 costs **60 XP levels**, grants `standlevel3`. Each plays `summonstand` (vol 60) + `particle flash ~ ~ ~ 0 0 0 0 10`. Otherwise chat "you dont have enough xp to get to level 2 you need 30 levels of xp" (same for 3 / 60) or "your stand cannot level up any further".
* **look(d)** (helper used everywhere, written out as nested `RayTraceContext` calls): ray trace from the eye along the look vector for `d` blocks (`BlockMode.OUTLINE`, `FluidMode.NONE`, ignores the player). Result block pos = the **hit block** if something is hit, otherwise the end point `eye + look*d`. Because many abilities require `world.isAirBlock(look(d))`, they only work when the line of sight is clear for `d` blocks. `look(d)` XYZ is used for block placement, teleport targets, explosion centers.
* **Explosion helper** "boom(d, P)": `world.createExplosion(null, look(d).x, .y, .z, P, Explosion.Mode.BREAK)` (blocks destroyed, no fire; damages entities incl. owner). Used for the "Ora/Muda" style punches (d = 2; P as listed).
* **Temporary blocks**: Hermit, Metal Poison, Nothingness, Parallel Portal, Reverse Heal, SC Lunge and Viral Punch blocks all use hardness -1 / resistance 3600000, and schedule themselves; `NothingnessUpdateTickProcedure` sets the block to air on its scheduled tick (default tick rate 10 = ~0.5 s). Zipper blocks and Remote Bomb persist.
* **Teleports** (`entity.setPositionAndUpdate` + `connection.setPlayerLocation`): destination x/z/y from `look(d)`; many abilities keep the player's current Y (see each).
* **De-summon on death / leave** (`DeSummonOnDeath`, `DeSummonOnDeath2`, `DeSummonOnLeave`, `DeSummonOnLeave2`, `DeSummonOnLeaveDimension`, `DeSummonOnLeaveDimension2`, ~1100 lines for the "-A" versions, ~480 for the "-2" versions): events `LivingDeathEvent`, `PlayerLoggedOutEvent`, `EntityTravelToDimensionEvent`. If the entity has `standactivated` then for its stand: grant `standoff`, `effect give @e[type=jjbamodtst:<stand>,limit=1,sort=nearest] jjbamodtst:despawnnoanimation 255 255 true`, `advancement revoke @p only jjbamodtst:standon`. Item stands remove their item (Anubis/Emperor: up to 36); Weather Report adds `weather clear`; King Crimson sets SURVIVAL (if not creative) and clears the effect set above. Notes: handlers fire for every `LivingDeathEvent` (not only players; non-players just fail the advancement check); in `DeSummonOnDeath2` the Purple Haze despawn branch is guarded by `crazydiamondstand` instead of `purplehazestand` (copy/paste bug, so PH is not despawned on death by itself); Hermit Purple does not appear in the death handler (?). Stands survive death only because `standon` is revoked (the advancement `<name>stand` is kept).
* **GER regeneration** (`GerHealProcedure.java`, `PlayerTickEvent` END): while `gerstand`+`standon`: each tick set health to 20, apply Absorption (dur 1 tick, amp 20), Glowing (dur 20, amp 255) and run `effect give @e[type=jjbamodtst:ger] minecraft:glowing 1 255 true`.
* **King Crimson time-erase timer** (`TimeErasureProcedure.java`, `PlayerTickEvent`): every tick for owners of `kingcrimsonstand`, increments NBT `KingCrimsonTimeErasure`; at >= 200 (10 s) sets non-creative player back to SURVIVAL; at >= 201 resets to 0. (Counter runs constantly, so a KC owner in spectator is forced to survival within 10 s.)
* **Hamon / Vampire** (`VampireHamonTickProcedure.java`, `PlayerTickEvent` END):
  * `hamonactivated`: re-applies Strength (dur 20, amp 1), Speed (20, amp 2), Jump Boost (20, amp 1) every tick. Granted by right clicking the Hamon book (`item/HamonBookItem.java`, `HamonScrollOnRightClickedProcedure.java`): "you already have the ability to use hamon" / "you can't control hamon as a vampire" else grants it. Hamon active ability: MAIN-B key N, "Hamon Flames": if `look(5)` is air, places Fire (MAIN-B 105-229; blocked by the shared main cooldown but sets none). The book has no crafting recipe; the file `recipes/hamonbookrecipie.json` actually crafts a Steel Ball (5 iron ingots, shape `1 / 345 / 7`).
  * `vampireactivated`: if block light at feet or at head >= 15 (`world.getLight`, so it also triggers from torches/glowstone, not only sun (?)): 5.0 fire-type damage + set on fire 1 s, every tick. Otherwise (not burning): Strength (20, amp 1), Speed (20, amp 2), Jump (20, amp 1), Night Vision (20, amp 255), Regeneration (20, amp 3).
  * Vampire can only be obtained by `GetVampireProcedure.java` (grants it if the player has no Hamon and wears the Stone Mask helmet) but **nothing calls that procedure** (no event/ item hook found), so vampire is only reachable by `/advancement grant` (?). Stone Mask helmet: `item/StoneMaskItem.java`; recipe `recipes/stonemaskrecipie.json` (shaped `stone stone polished_andesite / stone soul_sand stone`).
* **Dimensions** (`src/world/dimension/`): `jjbamodtst:paralleldinemsion` (Overworld-style chunk generator, biomes plains/desert/forest, `ParallelDinemsionDimension.java`) and `jjbamodtst:thehanddimension` (End-style generator, `the_void` biome, `TheHandDimensionDimension.java`).
* **Structures** (`data/jjbamodtst/structures/*.nbt`): `roadroller.nbt` (The World ult) and `magiciansflames.nbt` (Magician's Red ult), placed with `Template.addBlocksToWorld`.
* **Sounds** (`assets/jjbamodtst/sounds.json`): event id -> ogg: `ora` (ora), `geractivation` (ger), `theworldrush` (mudamudamuda_dio), `killerqueenremotedetonation` (trigger_bites_the_dust), `goldenexperiencelifegiver` (life_sfx), `timestop`, `bitesthedusttimereverse` (bites_the_dust_9), `conscious_time_erasure`, `quicktimeerase` (quick_time_erasure), `crimsonjab` (kingcrimsonpunches), `gunsfx` (emperor_3), `emperorgunsfx` (emperoratk), `handerase` (the_hand_erase), `handteleport` (thehandteleport), `emeraldsplash` (shortemeraldsplash), `webplacement`, `chariotslash` (silverchariotslash), `ari` (zipperattack), `zipperplace` (stickyfingersplacezipper), `zipperzip`, `areosmithminigun` (aerosmithminigun), `aerosmithsonar` (sonar_sfx), `aerosmith_idle`/`aerosmithidle`, `roadroller` (roadrollersfx), `tuskrush`/`tuskora` (uskra), `dojyann` (ojyan), `torturedance` (record), `goldexperiencerush` (mudamudamuda_giorno), `unsummonstand` (standunsummon), `summonstand` (standsummon), `starplatrush` (oraoraora_jotaro), `crazydiamonddora` (crazyiamondora), `reverse` (crazy_diamond_3_2), `viruspunch` (urplehazeviruspunch), `rage` (purplehazerage), `ultimatevirus` (purplehazeuptimatevirus), `echoeseffect` (echoessoundeffect). All plays use `SoundCategory.NEUTRAL`, volume 60, pitch 1 unless stated; vanilla sounds used: `block.fire.ambient`, `block.gravel.fall`, `block.anvil.use`.
* **Time stop mechanism** (shared by The World, Star Platinum, King Crimson): vanilla commands run with `@e` as everything in the loaded world, then the caster is cleared with `@p`: `effect give @e minecraft:blindness 1 255 true` (1 s flash), `effect give @e[type=!<stand>] minecraft:slowness N 255 true`, `effect give @e[type=minecraft:player] minecraft:jump_boost N 150 true`, `effect give @e[type=!<stand>] minecraft:slow_falling N 255 true`, `effect give @e[type=!<stand>] minecraft:mining_fatiuge N 255 true` (**typo; invalid id, this command errors and does nothing**), `effect give @e jjbamodtst:timestop N 255 true`, `effect give @e minecraft:glowing N 255 true`, then `effect clear @p minecraft:slowness`, `effect clear @p minecraft:jump_boost`, `effect clear @p minecraft:mining_fatigue`, `effect clear @p jjbamodtst:timestop`. N in seconds (command durations are seconds, `EffectInstance` durations are ticks).
* **Throwables/items** (all `item/`, damage = arrow-style entity damage, knockback = arrow punch; each stack has max damage 1 so it is a one-shot): Knife (dmg 4.5, speed 1.0 shoot, kb 1, drops itself back as an item on block hit unless thrower is creative; recipe 3 knives from iron ingot over stick), Steel Ball (dmg 9.0, kb 3, drops itself on block hit), Nail Bullet (dmg 3.0, kb 0, speed 2.0), Infinity Nail Bullet (dmg 0.1; on living hit applies `thespin` dur 99999 amp 1 via `NailBulletBulletHitsLivingEntityProcedure`; message "INFINITE ROTATION!"), Emerald (dmg 1.0, kb 7, speed 5.0; on block hit explosion power 2.0 BREAK at impact; sound `emeraldsplash`, message "20 METER EMERALD SPLASH!"), Weak Emerald (dmg 3.0, kb 2, speed 1.0; sound `emeraldsplash`), Sword Tip (dmg 10.0, kb 20, speed 10.0; sound `chariotslash`). Stand abilities that "give" these **overwrite the main-hand slot**.

---

# 2. Stands

Conventions: **N** = Main Stand Attack key, **B** = Ultimate key, **M** = Summon, **V** = level up. "L2/L3" = stand level needed. Durations/cooldowns in ticks. `boom(d,P)` = explosion helper (1.7). "cd" = cooldown given. Entity stats are the shared template in 1.4 unless stated. Messages are action-bar text.

### 1. Star Platinum  (`starplatinum`, advancement `starplatinumstand`)
* Obtain: random arrow (5%), `StarPlatinumStandArrowRightClickedInAirProcedure.java`.
* Summon: SUMMON-A 618-744.
* **N** (MAIN-A 305-409; no L2 needed, no sneak/sprint branch, **no cd**): message "Ora!", Strength dur 30 amp 4, sound `starplatrush`, `boom(2, 0.7)`.
* **B** (L3, ULT-A 512-793): "STAR PLATINUM! THE WORLD!", sound `timestop`, ult cd 360, broadcast "Time has stopped for 4 seconds ". Time-stop commands (1.7) with N = 4 and `[type=!jjbamodtst:starplatinum]` filter.
* Porting: N and potion effects map trivially (Strength + `world.createExplosion`). Time stop is feasible with potion effects + cancelled interact/move events but cannot freeze client camera/projectiles/blocks cleanly (hard-ish). Custom model needs a resource pack (ItemDisplay/armor-stand head with CustomModelData).

### 2. The World  (`theworld`, `theworldstand`)
* Obtain: random (5%); `TheWorldStandArrowRightClickedInAirProcedure.java`.
* Summon: SUMMON-A 363-489.
* **N** (MAIN-A 410-547):
  * default: "Muda!", Strength 30 amp 2, sound `theworldrush`, `boom(2, 0.7)`, no cd.
  * Shift + L2: if inventory already has Knives -> "You Still Have Throwing Knives"; else "Throwing Knives", main cd 120, sets main hand to **5 Knives** (Knife: dmg 4.5, kb 1).
* **B** (L3, ULT-A 102-511):
  * Shift: "ROAD ROLLER!", sound `roadroller`, ult cd 600, `boom(7, 6.0)` with Y = player Y, then places structure `jjbamodtst:roadroller` at (look(7).x - 4, player Y, look(7).z - 2).
  * No shift: "THE WORLD! TIME STOP!", sound `timestop`, ult cd 360, broadcast "Time has stopped for 7 seconds ", time-stop commands with N = 7 and filter `[type=!jjbamodtst:theworld]`.
* Porting: knives = thrown Snowball/Arrow with custom damage (clean). Road roller = load `.nbt` via `StructureManager` (clean but format conversion needed, 1.14 -> modern). Time stop as above.

### 3. King Crimson  (`kingcrimson`, `kingcrimsonstand`)
* Obtain: random (5%); `KingCrimsonStandArrowRightClickedinAirProcedure.java`.
* Summon: SUMMON-A 999-1214 (also forces SURVIVAL and clears time effects, see 1.3).
* **N** (MAIN-A 1504-1800):
  * No shift + L2: sound `quicktimeerase`, "Time Skip", main cd 100. If also **sprinting**: teleport to (look(9).x, player Y, look(9).z) and advance world time by **+500 ticks** (`world.setDayTime(dayTime+500)`). (Without sprint only the cd/sound/message happen.)
  * Shift: "King Crimson Jab", main cd 300, Strength 30 amp 15, sound `crimsonjab` (vol 30), `boom(2, 0.7)`, and `world.destroyBlock(look(2), dropBlock=false)`. No L2 needed.
* **B** (L3, ULT-A 1516-1822): "TIME ERASE! (king crimson deactivated)", sound `conscious_time_erasure`, ult cd 600, broadcast "10 seconds of time will be erased", time-stop commands with N = 10 on everything (no stand filter, `effect give @e minecraft:slowness 10 255`), revokes `standon` and despawns KC, clears slowness/jump/mining fatigue/timestop from `@p`, then sets the caster to **SPECTATOR** and NBT `KingCrimsonTimeErasure` = 0; the tick procedure returns them to SURVIVAL at counter 200 (10 s).
* Porting: N jab = Strength + explosion + `block.breakNaturally`/setType AIR (clean). Time skip = teleport + `World#setFullTime` (clean). Time erase = freeze others for 10 s + 10 s spectator for caster; needs state machine on a scheduler (medium); spectator-as-ghost is vanilla-client-friendly.

### 4. Killer Queen  (`killerqueen`, `killerqueenstand`)
* Obtain: random (5%); `KillerQueenStandArrowRightClickedInAirProcedure.java`.
* Summon: SUMMON-A 745-871.
* **N** (MAIN-A 548-775):
  * No shift + L2, if `look(2)` is air: "Planted Ranged Bomb", places **Remote Bomb block** (`block/RemoteBombBlock.java`, hardness 0.1/0.1, persistent) at look(2). No cd.
  * Shift: sound `killerqueenremotedetonation`, "Remote Detonation", `boom(200, 0.2)` (tiny explosion at the block you are looking at up to 200 blocks away). A Remote Bomb block destroyed by an explosion triggers `RemoteBombBlockDestroyedByExplosionProcedure.java`: `boom at the block, power 4.0, BREAK`. So detonation = small blast that chains the planted bombs (no per-bomb ownership).
* **B** (L3, ULT-A 1017-1110), requires **health <= 5.0**: "KILLER QUEEN! BITES THE DUST!", sets health 20, sound `bitesthedusttimereverse`, `effect give @e minecraft:blindness 1 255 true`, broadcast "Time has been reset to the beginning of the day", Instant Health dur 60 amp 255, teleports to world spawn, `world.setDayTime(0)`. **No ult cd applied**.
* Porting: bomb block = TNT-like custom marker block (store locations in PDC; clean). Remote detonation = location tracking + `createExplosion` (clean). Bites the Dust (teleport to spawn, reset time) clean.

### 5. Gold Experience  (`goldenexperience`, `goldexperiencestand`)
* Obtain: random (5%); `GoldExperienceStandArrowRightClickedInAirProcedure.java`.
* Summon: SUMMON-A 872-998.
* **N** (MAIN-A 3077-3818):
  * Shift: "Life Giver", sound `goldenexperiencelifegiver`, main cd 60. Spawns one random mob at **look(6)**: P=0.9 branch: 22.5% each Chicken / Horse / Rabbit / Wolf; 10% branch: 2.5% each Llama / Pig / Panda / **Star Platinum stand entity** (!) (`MAIN-A ~3700-3818`).
  * Sprint + L2: "Gave Yourself Life Energy", main cd 120, sound `goldenexperiencelifegiver`, Strength 90 amp 1, Speed 90 amp 2, Jump Boost 90 amp 2.
  * Else: "Muda!", sound `goldexperiencerush`, Strength 30 amp 2, `boom(2, 0.7)`, no cd.
* **B** (L3, ULT-A 1178-1269): "LIFE SENSOR!", sound `goldenexperiencelifegiver`, ult cd 360, broadcast "All Life Forms Are Visible", `effect give @e[type=!jjbamodtst:goldenexperience] minecraft:glowing 60 255 true`, then `effect clear @p minecraft:glowing`.
* Evolution to GER: via Stand Arrow, see 1.2.
* Porting: mob spawn (clean), Glowing to all entities in world (clean but consider radius). 

### 6. Gold Experience Requiem  (`ger`, `gerstand`)
* Obtain: GE at L3 + random Stand Arrow. Summon: SUMMON-A 1215-1367 (heals to 20, `geractivation` vol 60).
* **Passive**: continuous full heal + absorption (see `GerHealProcedure`, 1.7). Practically invincible.
* **N** (MAIN-A 776-1503):
  * Shift: "Life Giver", sound `geractivation`, **no cd**; same mob table as GE at look(6) but the last 2.5% entry is a **Spider** instead of Star Platinum.
  * Sprint: "Gave Yourself Life Energy", main cd 120, `geractivation`, Strength 90 amp **20**, Speed 90 amp 6, Jump 90 amp 3. (No L2 check.)
  * Else: "Muda!", sound `goldexperiencerush`, Strength 30 amp 5, `boom(2, 0.7)`.
* **B** (L3, ULT-A 1111-1177): "LIFE SENSOR!", sound `geractivation`, broadcast "All Life Forms Are Visible", `effect give @e minecraft:glowing 60 255 true` (including the caster; no clear), **no cd applied**.
* Porting: clean (potions, spawns, per-tick heal).

### 7. Magician's Red  (`magiciansred`, `magiciansredstand`)
* Obtain: random (5%); `MagiciansRedStandArrowProcedure.java`.
* Summon: SUMMON-A 490-617.
* **N** (MAIN-A 109-304):
  * Shift + L2: "Power Boost", sound `block.fire.ambient`, Strength 30 amp 2 (no cd).
  * Else if `look(5)` is air: "Quick Flames", sound `block.fire.ambient`, sets Fire block at look(5). No cd.
* **B** (L3, ULT-A 794-967): "MAGICIAN'S RED CROSSFIRE HURRICANE!", sound `block.fire.ambient`, ult cd 360, `boom(10, 6.0)`, places structure `jjbamodtst:magiciansflames` at (look(10).x-3, look(10).y, look(10).z-3).
* Porting: clean (fire placement, explosion, structure paste). Custom flame ring structure NBT must be converted.

### 8. Silver Chariot  (`silverchariot`, `silverchariotstand`)
* Obtain: random (5%); `SilverChariotStandArrowRightClickedProcedure.java`.
* Summon: SUMMON-A 236-362.
* **N** (MAIN-A 2697-3076):
  * Shift + L2: "Sword Ejection", main cd 360, gives **Sword Tip** in main hand (dmg 10, kb 20, speed 10; sound `chariotslash` when fired).
  * Else, if `look(2)` and `look(3)` are air: "Sword Lunge", main cd 20, sound `chariotslash`, places 2 **SilverChariotAttackLunge** blocks (at look(3), look(2); temp ~10 ticks) and teleports the player to look(2) x/z at the same Y. The lunge block (`SilverChariotAttackLungeEntityCollidesInTheBlockProcedure`): any entity touching it **without** `silverchariotstand` takes 15.0 generic damage per tick of contact.
* **B** (L3, ULT-A 968-1016): "SILVER CHARIOT ARMOR DETATCH!", sound `block.anvil.use`, Speed dur 200 amp 10, **sets own health to 2.0**. No ult cd.
* Porting: lunge = raytrace/AABB damage (simple, no block needed); knife-style projectile for Sword Tip (clean).

### 9. The Hand  (`thehand`, `thehandstand`)
* Obtain: random (5%); `TheHandStandArrowProcedure.java` (item `TheHandArrItem`).
* Summon: SUMMON-A 1916-2042.
* **N** (MAIN-A 2086-2305):
  * Sprint + L2: "Air Erase", sound `handteleport`, teleports to (look(5).x, player Y, look(5).z). No cd.
  * Else: "Block Erase", sound `ora`, sets block at look(2) to **air** (works on any block incl. bedrock). No cd.
* **B** (L3, ULT-A 1270-1371): "DELETE!", sound `handerase`, ult cd 360, places a **Nothingness block** at look(2) (replaces whatever is there; vanishes in ~10 ticks). `NothingnessEntityCollidesInTheBlockProcedure.java`: entities touching it (except The Hand stand entity) - non-players are teleported to Y-2000 (void death); players without `thehandstand` are sent to the `jjbamodtst:thehanddimension` world's spawn (Y+1) with the dimension-change packets (exit path not implemented in the code I read (?)).
* Porting: Block Erase (clean). Air Erase teleport (clean). Nothingness: kill non-players (clean); sending players to a void dimension requires a separate empty world (WorldCreator; doable but a custom world + return logic needed). **Medium.**

### 10. Weather Report  (`weatherreport`, `weatherreportstand`)
* Obtain: random (5%); `WeatherReportStandArrowRightClickedProcedure.java`.
* Summon: SUMMON-A 2043-2212 (`weather rain` on summon, `weather clear` on de-summon).
* **N** (MAIN-A 1801-1927):
  * Sprint + L2: "Wind Boost", Speed 30 amp 5, main cd 60.
  * Shift: "Thunder Storm", runs `weather thunder`. No cd.
  * Else: "Lightning Strike", spawns `LightningBoltEntity` at look(20). No cd.
* **B** (L3, ULT-A 1398-1484): "SHARP WIND STRIKE!", ult cd 440, `boom(2, 1.0)`, Strength 30 amp 5.
* Porting: clean (`World#strikeLightning`, `setStorm`).

### 11. Hierophant Green  (`heirophantgreen`, `heirophantgreenstand`)
* Obtain: random (5%); `HeirophantGreenStandArrowRightClickedProcedure.java`. `HeirophantLongPunchUpdateTickProcedure.java` exists but is not wired to any hook I found (?).
* Summon: SUMMON-A 2213-2339.
* **N** (MAIN-A 2306-2663):
  * Shift + L2, requires `look(3)`,`look(4)`,`look(5)` all air: "Webs", main cd 120, sound `webplacement`, places **Cobweb** at (look(3), look(4), look(5)) x/z at the player's Y.
  * Else: "Emerald Splash", gives **Weak Emerald** in main hand (dmg 3.0, kb 2). No cd.
* **B** (L3, ULT-A 1485-1515): "20 Meter Emerald Splash", ult cd 440, gives **Emerald** in main hand (dmg 1.0, kb 7, speed 5.0, explosion 2.0 on block hit).
* Porting: projectile with custom damage + explosion on hit (clean).

### 12. Sticky Fingers  (`stickyfingers`, `stickyfingersstand`)
* Obtain: random (5%); `StickyFingersStandArrowRightClickedProcedure.java`.
* Summon: SUMMON-B 44-170.
* **N** (MAIN-B 230-334; no sneak/sprint, no cd): "Ari!", Strength 30 amp 4, sound `ari`, `boom(2, 1.0)`.
* **B** (L3, ULT-B 101-256), only if the block at look(3) is not air: "STICKY FINGERS ZIPPER CREATE!", sound `zipperplace`, **replaces** the target block at look(3) with an **open Zipper Block** (`ZipperBlockBlock`, persistent, hardness -1). No cd. Right clicking an open zipper block as a Sticky Fingers owner (`ZipperBlockOnBlockRightClickedProcedure.java`) plays `zipperzip` and turns it into a **closed zipper** (`ZipperBlockClosedBlock`); right click the closed one (`ZipperBlockClosedOnBlockRightClickedProcedure.java`) toggles back. Entities touching a closed zipper that do **not** have `stickyfingersstand` take 15.0 generic damage (`ZipperBlockClosedEntityCollidesInTheBlockProcedure.java`).
* Porting: block toggle + damage on touch (clean via PlayerInteractEvent, EntityMoveEvent/scheduler). Custom block models would need resource pack with replaced vanilla blocks. 

### 13. Aerosmith  (`aerosmith`, `aerosmithstand`)
* Obtain: random (5%); `AerosmithStandArrowRightClickedProcedure.java`.
* Summon: SUMMON-B 171-296.
* **N** (MAIN-B 335-536):
  * Shift + L2: "Bomb", main cd 120, sound `killerqueenremotedetonation`, `boom(10, 3.0)`.
  * Else: "Minigun", sound `areosmithminigun`, `boom(40, 1.0)` (explosion at the block you are looking at up to 40 blocks away). No cd.
* **B** (L3, ULT-B 257-367): "ULTIMATE SENSOR!", sound `aerosmithsonar`, ult cd 120, Blindness dur 1 amp 1, `effect give @e minecraft:glowing 10 255 true`, `effect clear @p minecraft:glowing`, `effect clear @e[type=jjbamodtst:aerosmith] minecraft:glowing`.
* Porting: clean (explosions, glow).

### 14. Tusk Act 4  (`tuskact4`, `tusk4stand`)
* Obtain: random (5%); `TuskAct4StandArrowRightClickedProcedure.java`.
* Summon: SUMMON-B 297-422.
* **N** (MAIN-B 537-674):
  * Shift + L2: if inventory contains Nail Bullets -> "you still have some nail bullets left"; else "Nail Bullets", main cd 120, sets main hand to **5 Nail Bullets** (dmg 3.0, speed 2.0).
  * Else: "Ora!", Strength 30 amp 2, sound `tuskora`, `boom(2, 0.7)`. No cd.
* **B** (L3, ULT-B 368-403): if holding Infinity Nail Bullets: "you still have some nail bullets"; else "Infinite Rotation", ult cd 600, sets main hand to **1 Infinity Nail Bullet** (dmg 0.1; on living hit: `thespin` 99999 ticks, i.e. permanent rotation + 1 dmg/tick until cured).
* Porting: projectile + custom effect via scheduler. `thespin` = repeating task rotating yaw by +20 and damaging 1/tick: client camera rotation via `teleport` packet works on vanilla clients (hacky but possible).

### 15. D4C  (`d4c`, `d4cstand`)
* Obtain: random (5%); `D4CStandArrowRightClickedProcedure.java`.
* Summon: SUMMON-B 423-549.
* **N** (MAIN-B 675-970) requires a **Shield** in the inventory (`Items.SHIELD` (?)); otherwise message "you need a shield in your inventory to travel to a parallel dimension":
  * Sprint: main cd 60. If the current dimension id is 0 (Overworld): "Dimensional Travel", teleports to `jjbamodtst:paralleldinemsion` at its world spawn (Y+1); else teleports to Overworld spawn (`DimensionType.OVERWORLD`). Uses `changeDimension`-style `teleport` with `SChangeGameStatePacket(4,0)` etc.
  * Shift + L2 (not sprint): "Dimensional Leap (d4c deactivated)", `thespin` dur 120 amp 1 on self, sound `dojyann`, teleport to look(20) (x,y,z), Blindness dur 1 amp 1, 3.0 generic damage, grants `standoff`, despawns D4C entity, revokes `standon`. No cd.
* **B** (L3, ULT-B 404-679):
  * Shift + health <= 5 + in the parallel dimension: "SELF REPLACE! (d4c deactivated)", sound `dojyann`, Blindness 60 amp 2, health 20, food level 20, teleport to the **current world's** spawn point (so it stays inside the parallel dimension (?)), message "you switched places with an alternate version of yourself", despawn D4C. No cd.
  * Else with Shield and `look(2)` air: "DOJYAAAN!", sound `ora`, ult cd 600, places a **Parallel Dimension Portal** block at look(2). Touching it (`DimensionSwitchShareProcedure.java`): non-D4C-owners: non-players go to Y-2000 (void); players toggle between Overworld <-> Parallel Dimension (dimension id 0 -> parallel; else Overworld). Block vanishes in ~10 ticks.
  * Without shield (and not the self-replace case): "you need a shield in your inventory to send something to a parallel dimension".
  * **Parallel dimension rules**: on entering (`ParallelDinemsionPlayerEntersDimensionProcedure.java`) players without `d4cstand` get chat "only d4c users can survive inside dimensions in which their alternate selves also exist" and `dualexistancedamadge` dur 99999 amp 1 (6 dmg per tick of effect, see 1.5; no code found that removes it on leaving (?)).
* Porting: **hard**. Needs a second world (`WorldCreator`, Overworld-like with plains/desert/forest only) and travel/portal logic; the lethal "dual existence" effect is a repeating damage task. Sprint+shield key combos need a client key. D4C-side logic with vanilla effects is clean, the world part is medium-hard (world creation on the fly, per-player return point). 

### 16. Crazy Diamond  (`crazydiamond`, `crazydiamondstand`)
* Obtain: random (2.5%); `CrazyDiamondStandArrowOnRightClickedProcedure.java`.
* Summon: SUMMON-B 550-676.
* **N** (MAIN-B 971-1121):
  * Shift + L2: "Reverse Item", main cd 300, sound `reverse`, sets NBT `Damage` = 0 on the **main-hand item** (repairs tool durability).
  * Else: "Dora!", Strength 30 amp 4, sound `crazydiamonddora`, `boom(2, 1.0)`. No cd.
* **B** (L3, ULT-B 680-837), needs `look(2)` air: "REVERSE ENTITY!", sound `reverse`, ult cd 600, places a **Reverse Heal block** at look(2) (~10 ticks). Entities touching it get `+15` HP (`setHealth(hp + 15)`) per tick of contact (`ReverseHealEntityCollidesInTheBlockProcedure.java`, applies to everyone incl. enemies).
* Porting: clean (item damage reset, healing area).

### 17. Purple Haze  (`purplehaze`, `purplehazestand`)
* Obtain: random (2.5%); `PurpleHazeStandArrowRightClickedProcedure.java`.
* Summon: SUMMON-B 677-802. (Death de-summon bug, see 1.7.)
* **N** (MAIN-B 1122-1465):
  * Shift + L2: "Rage Mode Activated", main cd 600, sound `rage`, broadcast "purple haze is enraged", `ragemode` dur 200 amp 1.
  * Else, if `look(2)` is air: "Viral Punch", main cd 100, places **Viral Punch block** at look(2) (~10 ticks), Strength 30 amp 3, sound `viruspunch`, `boom(2, 1.0)`, command `/particle minecraft:entity_effect ~ ~ ~ 1 0 1 300 10` (leading `/`, may fail in the command dispatcher (?)). The Viral Punch block: any entity touching it gets **Poison dur 200 amp 20** (`VirusPunchEntityProcedure.java`; no owner check, so it hits the user too).
  * While `ragemode` is active and the player is **not** sneaking (MAIN-B 2290-2356, runs outside the cooldown block, still blocked by `timestop`): "Enraged Viral Punch!", places the Viral Punch block at look(2) with no air check, Strength 30 amp 3, `viruspunch`, `boom(2, 1.0)`, particle `/particle minecraft:entity_effect ~ ~ ~ 1 1 1 300 30`. No cd.
* **B** (L3, ULT-B 838-999), needs `look(2)` air: "TOXIC HAZE!", sound `ultimatevirus`, ult cd 600, commands `/particle minecraft:entity_effect ~ ~ ~ 2 0 2 300 3000`, `/effect give @e[distance=..4] minecraft:wither 10 255 false`, `/effect clear @p minecraft:wither` (all with leading `/`, which Brigadier rejects in `Commands.handleCommand` (?), so the haze may do nothing in practice).
* Porting: clean for Poison/Wither/particles/explosion; rage mode = custom timer (clean). Note Paper `Bukkit.dispatchCommand` takes no leading `/`.

### 18. Echoes Act 3  (`echoesact3`, `echoesact3stand`)
* Obtain: **Echoes Act 3 Stand Arrow** only (`EchoesAct3StandArrowProcedure.java`, item `EchoesAct3StandArrow3Item`); not in the random table. Lang quirk: `entity.jjbamodtst.echoesact3` = "Purple Haze".
* Summon: SUMMON-B 803-928.
* **N** (MAIN-B 1831-2356; second half):
  * Shift + L2, needs `look(2)` air: "Onomatopoeia - FWOOSH!", main cd 10, sets **Fire** at look(2).
  * Else, needs `look(2)` air: "Onomatopoeia - SPLASH!", main cd 100, places **water** (source block) at look(2).
* **B** (L3, ULT-B 1000-1086): "THREE FREEZE!", sound `echoeseffect`, ult cd 400, commands `/effect give @e[!type=jjbamodtst:echoesact3] minecraft:slowness 100 255 true` and `/effect clear @p minecraft:slowness` (leading `/` plus selector `[!type=...]` is not valid syntax; likely a no-op in the original (?)). Intended: slow everything for 100 s except the stand.
* Porting: clean (place fire/water, slowness potion area).

### 19. Metallica  (no entity, advancement `metallicastand`)
* Obtain: random (5%); `MetallicaStandArrowProcedureProcedure.java`.
* Summon: SUMMON-A 63-167 (toggle only; sound `summonstand`/`unsummonstand`, particle flash; no item, no entity).
* **N** (MAIN-A 1956-2085): if `look(2)` is air: "Iron Poisoning", places **Metal Poisoning block** (`MetalPosisoningBlock`, ~10 ticks); any entity touching it without `metallicastand` takes 6.0 generic damage (`MetalPoisoningEntityProcedure.java`). No cd.
* **B** (L3, ULT-A 1372-1397): "IRON MANUPULATION!", ult cd 300, Invisibility dur 200 amp 255.
* Porting: clean (damage zone, invisibility).

### 20. Anubis  (item stand, `anubisstand`, item `item/AnubisItem.java`)
* Obtain: random (5%); `AnubisStandActivationProcedure.java`.
* Summon: SUMMON-A 1368-1550 gives an **Anubis axe** (unbreakable-ish: durability 99999, enchant glint, tier attack bonus 5.0, constructor damage 1.0 / speed 26.0 (?), see `AxeItem` ctor) and removes it on de-summon.
* Passive (`AnubisToolInHandTickProcedure.java`, per-tick while the item is **selected** in the hotbar): if `anubisstand` and no `timestop`: Strength dur 1 amp 4.
* **N** (MAIN-A 2664-2696): Sprint + L2: "Anubis Sense", Speed 200 amp 2, Jump Boost 200 amp 2. No cd.
* **B**: none (Anubis is not in ULT-A/ULT-B).
* Porting: custom item (CustomModelData sword with attribute modifiers) + 2 effects: clean.

### 21. Hermit Purple  (item stand, `hermit_purple_stand`, item `item/HermitPurpleItem.java`)
* Obtain: **Hermit Purple Stand Arrow** only (`HermitPurpleStandActivateProcedure.java`); not in the random table.
* Summon: SUMMON-A 1551-1733 gives the **Hermit Purple** item (durability 99999; attack damage +8.0 (9 total), attack speed -3.0) and removes it on de-summon.
* **N** (MAIN-B 1466-1830):
  * Shift + L2, needs `look(2)` air: "Hermit Swing!", sound `block.gravel.fall`, main cd 100, teleports to look(3) (x,y,z all from the ray).
  * Else, needs `look(2)` air: "Hermit Grab!", sound `block.gravel.fall`, places a **Hermit Block** at look(2) (~10 ticks). Entities walking on it get `setMotionMultiplier(0.25, 0.05, 0.25)` (cobweb-like slow); entities colliding with it also take 5.0 generic damage (`HermitBlockEntityCollidesInTheBlockProcedure`; no owner exemption (?)). No cd.
* **B** (L3, ULT-B 1087-1151): "HERMIT BOOST!", sound `reverse`, ult cd 400, Speed 200 amp 1, Instant Health 200 amp 1, Strength 200 amp 1, Regeneration 200 amp 1, Jump Boost 200 amp 1.
* Porting: clean (grapple = teleport/ velocity; slow zone = potion/ scheduler).

### 22. The Emperor  (item stand, `theemperorstand`, item `item/TheEmperorItem.java`)
* Obtain: random (5%); `TheEmperorStandArrowRightClickedProcedure.java`.
* Summon: SUMMON-A 1734-1915 gives **The Emperor** item (durability 99999, melee attack damage +1.0, attack speed -3.0) and removes it on de-summon.
* **Right click in air with the item** (`TheEmperorRightClickedInAirProcedure.java`): if `theemperorstand` + `standon`: sound `emperorgunsfx`, `boom(250, 1.0)` at the block pointed at up to **250 blocks**. No cd, no cooldown/timestop check.
* **N** (MAIN-A 1928-1955): Sprint + L2: "Slide", Speed 30 amp 5. No cd.
* **B**: none.
* Porting: hitscan (ray trace) + explosion (clean).

---

# 3. Porting notes summary (Paper, vanilla clients)

| Stand | Maps cleanly | Hard / needs workaround |
|---|---|---|
| Star Platinum | Strength + explosion | Time stop (global freeze, camera not freezable); custom model needs resource pack |
| The World | Strength + explosion, Knives, Road Roller structure | Time stop (7 s); structure NBT conversion; custom model |
| King Crimson | Jab, time skip (setTime), spectator ghost | Time erase (10 s freeze + spectator) timing; "stand" visual |
| Killer Queen | Planted bomb markers + explosions, Bites the Dust (teleport, setTime(0)) | Persisting bomb blocks per owner; stand model |
| Gold Experience | Mob spawns, buffs, glowing | Spawning "Star Platinum" entity mob (just spawn the stand visual) |
| GER | Per-tick heal + absorption, buffs | Custom stand model |
| Magician's Red | Fire, strength, explosion, structure | Structure NBT conversion |
| Silver Chariot | Ray lunge + damage, Sword Tip projectile | Block-based lunge hit detection (use AABB); model |
| The Hand | Block erase, teleport | Void dimension for Nothingness (custom world, return trip) |
| Weather Report | Lightning, weather, speed | none |
| Hierophant Green | Cobwebs, emerald projectiles (explosion on hit) | Projectile custom damage/kb (use Snowball/Arrow + events) |
| Sticky Fingers | Strength + explosion, zipper toggle blocks | Custom block models (re-skin vanilla blocks via resource pack) |
| Aerosmith | Long-range explosions, glowing | none (stand is a model only) |
| Tusk Act 4 | Nail projectiles | `thespin` camera rotation + dmg loop (scheduler); permanent 99999 tick effect |
| D4C | Effects, teleport | **Parallel dimension world**, portal block travel, dual-existence damage, per-player return |
| Crazy Diamond | Item repair, heal block, explosion | none |
| Purple Haze | Poison block, explosion, rage mode timer | Particle/effect commands with leading `/` (drop the slash) |
| Echoes Act 3 | Fire/water placement, slowness | Original ult command is probably broken (decide intended behavior) |
| Metallica | Damage zone + invisibility | none |
| Anubis | Custom axe + hotbar buff | Custom model |
| Hermit Purple | Swing teleport, slow/damage block | Custom model |
| The Emperor | Hitscan explosion | Custom gun model |

Cross-cutting problems:
* **Keybinds M/N/B/V** are client-only `KeyBinding`s: not possible on vanilla clients. Options: use `PlayerSwapHandItemsEvent` (F), `PlayerDropItemEvent` (Q) on a stand "tool", or hotbar-slot selection, or a command / right click on a custom item. Shift/Sprint modifiers are readable server-side (`isSneaking()`, `isSprinting()`).
* **Custom entity models/textures** (18 stand mobs, 22 total): vanilla clients cannot show new models without a resource pack; use an invisible armor stand/ItemDisplay with `CustomModelData` + the mod's model/textures (needs model conversion from the 1.14 `EntityModel` boxes to JSON). Follow behavior (teleport when >5 blocks away) is trivial.
* **Advancement storage**: replace with PDC (`PersistentDataContainer`) keys on the player: `stand` (string), `standOn` (bool), `level` (int); or keep impossible advancements via datapack.
* **Potion cooldown effects**: replace with scheduler-driven cooldown maps (or keep the same effects with `hidden icons`).
* **Custom dimensions** (Parallel Dimension, The Hand): require extra worlds created on demand with `WorldCreator` and custom generators; plan return handling.
* **Structures**: `.nbt` -> modern format via `StructureManager`.
* Command-based abilities (all the `effect give @e ...` calls) can be ported to direct API calls with radius instead of "all entities in all loaded chunks".
