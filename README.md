<p align="center">
  <img src="docs/assets/the-dantes-infinity.webp" alt="The Dante’s Infinity" width="720">
</p>

# The Dante’s Infinity (Fabric, Minecraft 26.1.2)

🇬🇧 English · [🇷🇺 Русский](README.ru.md)

Requirements: **Java 25**. Build: `./gradlew build` → `build/libs/infinity_gauntlet-1.0.7.jar` (+ Fabric API 0.155.3+26.1.2).

```
./gradlew runClient          # test client; stable offline profile Player433
./gradlew runClientGameTest  # visual run with screenshots (build/run/clientGameTest/screenshots)
./gradlew runClientGameTest -PonlyTest=SoulQuestTest  # a single test (headless Linux: xvfb-run -a)
```

## Getting started

- **Uru ore** generates in new Overworld chunks from the bottom of the world up to Y=0. Look around Y=−32 and use a diamond or netherite pickaxe. Smelt raw Uru in a furnace or blast furnace. Fortune increases the drop, and Silk Touch preserves the ore block.
- **Gauntlet crafting** uses a 3×3 grid. From top to bottom: Uru / gold / diamond, gold / redstone / gold, diamond / gold / Uru. Use ingots for both metals. **Infinity Gauntlet Schematics** with the forging pattern can be found in world chests (villages, smiths, strongholds, dungeons, mineshafts, temples, pyramids, shipwrecks, underwater ruins, mansions, ancient cities, bastions, fortresses, End cities) and bought from cartographers, smiths, librarians, and wandering traders; right-clicking the schematic unlocks the Gauntlet, Uru armor and Uru smelting recipes. Right-click with the gauntlet in your main hand to open the stone slots, then press F to move it to your offhand.
- **Uru armor** follows iron armor recipes and is repaired with Uru ingots. A complete set reduces damage from a full gauntlet and loses durability when absorbing a hit.
- All six Infinity Stones have survival sources below. The in-game field guide updates when opened. Use `/gauntlet_guide` to get a copy.

## Stormbreaker
- Craft the axe from five Uru ingots, a Nether Star and a jungle log. The rows are **Uru / Uru / Nether Star**, **Uru / Uru / Uru**, **empty / jungle log / empty**. The first successful craft binds it to the smith.
- During a thunderstorm, an unclaimed Stormbreaker can fall near a player in the Overworld. Players within 110 blocks see a blue compass and the distance to the crash site until someone claims the axe. Right-click the embedded axe to pull it free and become its owner. A world has one Stormbreaker, so either the first craft or the first skyfall closes the other route. The recipe requires a player at a crafting table.
- Hold it in your main hand. **Left click** chains three overhead chops. Hold **left click** while aiming at the ground and release to send a wall of lightning and debris forward, broadside to your aim. The wave smashes through breakable blocks and leaves connected craters where ability block changes are enabled. **Right click** calls lightning in a 7-block radius. **X** throws it horizontally, **H** calls Heaven’s Wrath over a 20-block radius, and **J** toggles flight. **Q** plants the axe while keeping ownership, and **K** recalls it across dimensions or from the void. You can rebind the keys in Controls.
- The flying axe cuts through breakable blocks where block changes are permitted. It returns to its owner when the path is blocked or they change dimensions. If the inventory is full, it returns as soon as a slot is available. Lightning strikes the planted axe periodically, reaching down from the clouds in the Overworld. After death, reclaim it with right-click or summon it with K.
- Carrying the bound axe anywhere in the inventory grants a higher jump and +5 hearts. Holding it gives strength and reduces direct Gauntlet damage, including meteor blasts. It wards off Mind and Soul control and hostile Snap or Planet Destruction transfers. Reality Strike and Inversion can still kill its wielder.

## Finding the Power Stone
- Now and then (first after ~20 min, then every 20–50 min of world time) a **meteor shower** falls 60–110 blocks from a player in the Overworld; nearby players get a HUD + chat notice with the direction.
- The last meteor is **purple**: it leaves a purple meteorite (diamond pickaxe) with **the Orb** in its core. Taking the Orb out stops the showers for good; a meteorite you ignore **loses its Orb when the next shower starts** (so a stone is never duplicated). During a shower the sky and the sun take on a red cast. Right-click opens it and the Power Stone goes into the backpack. A second Orb opens empty.
- A bare Power Stone in either hand **kills in 2 s** (creative is spared). In the gauntlet or the backpack it is safe.
- Stones (loose, in a gauntlet or extractor, the sealed Orb) stay with the player on death and come back on respawn — **unless another player made the kill**. Dropped stones never despawn and can't be destroyed.
- Config: `powerShower*`, `powerStoneHoldTicks`. Operator commands: `/gauntlet power_shower`, `/gauntlet stones`, `/gauntlet stones reset`. Test: `xvfb-run -a ./gradlew runClientGameTest -PonlyTest=PowerStoneTest`.

## Finding the Space Stone
- After the **Ender Dragon** is killed, a large icy rift opens on the End island (`/gauntlet space_quest rift` to force it). It leads to a small **frozen asteroid** far beyond the galaxy (own dimension, built on first visit).
- It is deadly cold there: without a **full set of iron armor or better** (tag `infinity_gauntlet:cold_protection`: iron, diamond, netherite, Uru) the player freezes. Creative is spared.
- The stone lies at the top of a huge ancient ice tower, **inside the Tesseract** (a blue cube). Taking it shows *"Use the cube to get home"*; right-click sends the holder to Earth. A rift on the asteroid also leads back.
- **Throw the Tesseract into lava**: it explodes, lava around turns to obsidian and the glowing Space Stone appears (once per world; a second cube breaks open empty).
- Art: pack `01_Space_Quest`. Test: `xvfb-run -a ./gradlew runClientGameTest -PonlyTest=SpaceQuestTest`.

## Finding the Reality Stone
- Once a minute, nearby Nether players get one shared roll for red mist (`realityFogChancePerMinute`). One mist event and one stone can exist at a time. The first player to hit the stone in melee or with any projectile gets it.
- Art: pack `02_Reality_Quest` (layered fog density map, stone trail, arrow hit, fog dispel). Debug: `/gauntlet reality_fog`. Smoke test: `-PonlyTest=RealityFogTest`.

## Finding the Soul Stone
- Every world has a **Temple of Soul** (1000-1600 blocks from spawn): a lone snowy mountain in a flat snow waste, a stairway with fences and ruins winding to the summit, a ruined temple with a chasm. The terrain is reshaped as its chunks load. Maps to it (**Map to the Temple of Soul**, right-click to unroll into a real map with a red X) lie in igloo, shipwreck-map, stronghold-library, cartographer, dungeon and mineshaft chests, and cartographers / wandering traders sell them.
- **Drop a tamed animal into the misty chasm** or knock another player in with a hit near the edge. The animal’s owner or the last qualifying attacker starts the ritual. Jumping in alone grants no reward. Once the ritual starts, the sky turns amber, a beam strikes the clouds, darkness, and the ritual participant wakes in the **soul world** (dark shallow sea, red sun). The Soul Stone rises from the water; picking it up shows *"Use the Soul Stone to return to the temple"*, using it brings you back to the summit. Once per world; leaving before taking the stone resets the quest.
- Art: pack `05_Soul_Quest`. Debug: `/gauntlet soul_quest temple_here | locate | map | ritual`. Smoke test: `-PonlyTest=SoulQuestTest`.

## Finding the Time Stone
- In villages, from cartographers and wandering traders, and in chests across ruins, temples, dungeons and mineshafts you can find the **Map to the Temple of Time** (right-click to unroll). It marks the single **Temple of Time** in the world.
- Inside the temple stands a broken **clock mechanism**. To start it, insert (right-click) **three Ancient Gears from the three dimensions**: **Ancient Overworld Gear**, **Ancient Nether Gear**, and **Ancient End Gear**. They drop from mobs in their respective dimensions and appear in stronghold, dungeon, fortress, bastion and End city chests.
- Locking all three gears into the clockwork starts the mechanism and grants the **Time Stone**. Debug: `/gauntlet time_quest temple_here | locate | map | gears`. Smoke test: `-PonlyTest=TimeQuestTest`.

## Finding the Mind Stone
- Across villages, ruins, temples, dungeons, mineshafts, and from cartographers and wandering traders, you can find the **Map to the Ruined City** (right-click to unroll). It marks the single **Ruined City** in the world.
- Entering the city seals the arena and spawns **10 Mind Zombies**. The barrier blocks escape by teleporting, flying or digging. Environmental damage cannot advance the fight. Defeated players respawn outside the barrier. After a full wipe, the fight restarts on entry and skips the zombie wave if it was already cleared.
- Mind Zombies killed by Stone abilities count toward quest progress. Thanos stays clear of the city until the Mind Stone is claimed, even if you already carry the other five stones. Planet Destruction is blocked during Loki’s encounter.

## Loki — God of Mischief

- After the **10th Mind Zombie** falls, golden light washes over the city and **Loki** steps through a portal. He uses protective wards, daggers and ranged spells that can ground Stormbreaker and Gauntlet flyers. In phase one he periodically teleports onto the ruins, bombards marked areas, then returns to melee. Illusions break in one hit. At 45% health a shared scene reveals **Fenrir**, who bites, strikes with his paws and stuns with a roar. The health bar changes with his form.
- **Arrows, explosions and Infinity Stone attacks** are especially effective. Defeated Fenrir reverts to Loki, who kneels and dissolves into sparks. The **Mind Stone** then forms in front of the closest participant and flies into their inventory. The barrier opens once they receive it, and **650 XP** drops where Loki fell.
- Creative players can test both forms while retaining normal creative damage immunity. Switching a registered arena participant to creative keeps the active fight running. A green-gold victory notice with a horned-helm seal appears after the real Loki dissolves.
- Advancements: **Nice Try, Loki** for shattering an illusion and **God of Mischief** for defeating Loki. Debug: `/gauntlet mind_quest city_here | locate | map | summon`. Smoke test: `-PonlyTest=MindQuestTest`.

## Thanos — The Mad Titan
- Carry **5 different stones** (loose, in a gauntlet or extractor, the sealed Orb or the Tesseract count) and the Mad Titan comes for you from deep space. He comes only after a **breather of half a game day** (12000 ticks, `thanosDelayTicks`) from the moment you carry the stones; a warning comes ~10 s before he arrives.
- **Overworld:** his ship Sanctuary II glides into the sky, the sky takes a dangerous red cast and the sun turns red; a blue beam lowers Thanos a dozen blocks from you. **Nether** (or deep underground): a rift in reality tears open and he steps out of it. **End:** the ship arrives and the End brightens; a living dragon is struck down by the ship's cannon first.
- He speaks ("I came to pick up mine"; "Give me the stones!" if you have no gauntlet of your own), the boss bar **"Thanos — the Mad Titan"** and the battle theme start, and the fight begins.
- He swings his double sword, hurls you away with a gauntlet backhand, leaps high and slams the ground, and throws the sword like a boomerang. At half health he enrages, deals 35% more damage, and takes 40% less. He alternates an expanding ground shockwave and three blasts aimed at marked positions. The orange rings show where to dodge. A raised shield stops a swing but gets knocked aside.
- His blows ignore most ordinary armor: **without full Uru armor he is almost unbeatable** (Uru takes only 30%). He fears the **gauntlet** most: every ability of a worn gauntlet hits him hard (×1.4; with all six stones ×2.2), and a fully connected **Infinity Beam** can take up to ~45% of his health before rage resistance. Next come **stones used bare in the hand** (×0.7) and **explosions** (×0.7); **netherite and diamond swords** barely hurt him (×0.4 / ×0.3). Ordinary weapons are capped per hit, so Sharpness, Strength, crits, maces and TNT cannot melt him; Stormbreaker’s attacks deal their full base damage before rage resistance. Most other weapons barely scratch him. Tunable: `thanosGauntletMult`, `thanosFullGauntletMult`, `thanosBeamShare`, `thanosLooseStoneMult`, `thanosNetheriteSwordMult`, `thanosDiamondSwordMult`. He wades through water without getting stuck.
- Defeat: he falls to one knee, speaks, and **turns to dust**. He leaves his **empty Infinity Gauntlet** (only if you had not crafted one yourself; otherwise he came without his) and a lot of experience. He never returns (`/gauntlet thanos start | now | spawn` refuse a defeated Thanos until `/gauntlet thanos reset`).
- **His ship falls.** Left without its master, Sanctuary II loses power: it tips nose down, burns and breaks apart in the air, then slams into the ground ~60 blocks away. A shockwave, a scorched crater with a heap of wreckage, the land around on fire, and a rain of **iron and netherite** (ingots, scrap, a few netherite ingots, iron blocks; they don't burn). Afterwards the ship is **built from real blocks** (~40k: blackstone, iron blocks, deepslate tiles, lights; hollow, with campfires smoking on it) where it lies and **stays in the world for good**, in whichever dimension it fell. Off: `thanosShipCrash`, `thanosShipCrater`, `thanosShipWreck`.
- If you die, flee more than ~100 blocks or log out, he withdraws (the music fades) and returns in `thanosReturnMinutes` (default 1 minute) (5).
- Config: `thanos*` (health, damage, multipliers, threshold). Commands: `/gauntlet thanos start | now | spawn | stop | reset`. Test: `-PonlyTest=ThanosTest`. Art and models: `art/thanos/`.

## Language, client settings, advancements, and field guide

- **Settings screen**: Options or Pause menu → **Infinity Gauntlet ⚙** button (bottom left).
  - **Language switcher**: cards for Auto / English / Русский; applies instantly to the whole mod (menus, HUD messages, tooltips, advancements, the book) and is saved as `language` in `config/infinity_gauntlet.json`. Auto follows Minecraft's language.
  - **Boss typography**: names, actions and dialogue use bundled Open Sans Bold, with Cyrillic support and individual colour accents. Ordinary player chat keeps its font. Font licenses are bundled under `assets/infinity_gauntlet/font/`.
  - **Client options**: toggle **Ability Wheel** mode (`Hold` vs `Press`), show or hide the top-left **Ability HUD** card (`showStatusHud`), and cycle **Camera Shake** (`100% / 50% / Off`) and **Screen Flash** (`100% / 50% / Off`).
- **Advancements & Recipe Unlocks**: a dedicated **The Dante’s Infinity** advancement tab tracks Uru mining, Uru armor, crafting the Gauntlet, finding each of the six Infinity Stones, flying into the Solar System, stealing a stone with the Extractor, shattering a Loki illusion, defeating Loki and Thanos, awakening the full Gauntlet, shattering a planet, executing the Infinity Snap, and undoing the Snap with the Time Stone. Raw Uru or Uru ore unlocks smelting. A Uru ingot or the schematic unlocks the Gauntlet and armor recipes.
- **Field guide book**: every player gets a written book (22 pages, available in both languages) the first time they join a world — powers, controls, Loki, Thanos, Uru, the Extractor, settings, and Stormbreaker. Lost it? Type `/gauntlet_guide` (no operator rights needed). Disable with `giveGuideBook: false`.
- Texts live in `lang/en_us.json` / `ru_ru.json`; the book pages are generated by `python3 scripts/gen_guide.py`. Check: `xvfb-run -a ./gradlew runClientGameTest -PonlyTest=GuideLangTest`.

### The Living Tribunal

Defeat Loki and Thanos, wear the awakened gauntlet with all six stones and fly into space: **the Tribunal arrives by itself after about 3 seconds**. Dodge its seven attacks and strike back with the gauntlet when it is exposed. At one heart the gauntlet is confiscated and destroyed and you die; you respawn in space with everything restored. The Tribunal returns 30 seconds later if you stay in space, and you can flee to Earth in that window. During the trial you cannot return to Earth. `/tribunal challenge` (operator) bypasses all conditions but needs space flight and a difficulty above Peaceful; damage is disabled only in creative. In co-op the conditions are personal: each player needs their own Loki and Thanos victories.

## Ability wheel controls

- Equip the gauntlet in your offhand. Hold **V** to open the wheel; point at a stone, then at an ability, and release **V** to select.
- **R** — use the selected action; hold and release for charged strikes, the beam and Reality Inversion. Opening the wheel cancels a charge without attacking.
- **Esc** cancels the selection. **PgUp/PgDn** switch stones, arrows/Tab switch actions, Enter confirms. `abilityWheelToggle: true` in the config makes the wheel open on press instead of hold.
- Left-click the center — insert stones. Selecting Portal or Destinations opens a separate dimension/route menu; left-click/Enter confirms, using it with R is separate. **Shift+R** with a portal selected quickly switches the dimension; the target is shown in the status.
- Telekinesis: R grabs/throws; "Tear apart" and the safe "Release" are separate labeled entries. **Shift+R** with a grabbed mob tears it apart immediately. Switching to another stone safely releases the target.
- Flight stays active when you select another stone. **Ctrl** boosts; to turn it off, select Flight and press R. In space this entry returns you to the atmosphere near Earth, keeping manual flight.
- **Favorites**: point at an ability in the wheel and press **1–6** to pin it. Hold **B**, select a slot, then use the ability with **R**.
- Stone and gauntlet icons show the mod's existing items and the active resource pack. The selected ability card is in the top-left panel.

The old quick keys are now **unbound by default** but still available in the controls settings. Historical factory bindings are cleared once; custom ones are kept. The old right-click charge with the gauntlet in the offhand is enabled separately via `legacyRightClickPower: true`.

Testing the new controls: `xvfb-run -a ./gradlew runClientGameTest -PonlyTest=AbilityWheelTest`. The old tests temporarily switch back to the previous input scheme on their own.

## Infinity Beam — all six stones
- Equip a gauntlet holding all stones in your offhand, wait for it to awaken and select Infinity Beam and **hold R**: 2 s charge, then a 6 s beam. Releasing interrupts the attack.
- Annihilates non-boss mobs in a single hit, scorching and burning everything in its path. Range 72 blocks; full contact with the dragon removes 70% of its max health (140 HP). Misses reduce the damage; crystal healing is independent.
- The beam drills a scorching tunnel up to 9 blocks wide with no item drops; bedrock is preserved and no new chunks are loaded.
- Cooldown 45 s (disabled in creative mode). Parameters: `beamChargeTicks`, `beamFireTicks`, `beamCooldownTicks`, `beamRange`, `beamDamage`, `beamHitRadius`, `beamBreakBlocks`, `beamDrillRadius`, `beamBlocksPerTick`.
- Ability statuses and the rain timer are shown in a separate top-left panel in bold.
- The charge, beam barrel, flash and impact animations come from the supplied Infinity Beam Ultimate Basic; the six colored spirals are built at render time.

## Power Stone
- The gauntlet only works **in the offhand** (F). Right-clicking with the gauntlet in the main hand opens the stone menu.
- **Hold R** with Power Strike selected — charge (purple aura at the fist), **release** — power strike (damage, knockback and cooldown are handled on the server).
- Use the **V** wheel to select a mode: **Power Strike**, **Planet Destruction**, **Sun and Moon Destruction** or **Meteor Stream**.
- Ultimate: "Planet Destruction" mode, full charge (2 s), strike the ground beneath you. Insert the Time Stone into the gauntlet first so you can bring everyone back. Works in the Overworld and the Nether only: **unavailable in the End, during the Loki and Thanos encounters, or near a Wither**. The blocks of the world stay intact, but the destruction is seen by **every player on the server in the Overworld or the Nether** (players in the End are never touched): they all go to Dead Space (`planetDestructionAllPlayers`; off = only players within `participantRadius`), and the Time Stone takes them all home.
- The destruction ends the meteor rain, closes portals in the source world and interrupts its active beams/rewinds. The red sky and fog only apply in the Overworld.
- In space: a large voxel Earth with simplified continents based on [NASA Blue Marble](https://science.nasa.gov/resource/blue-marble/), ocean, polar caps and clouds; current vanilla block textures are used.
- Breakup: 14 uneven large fragments → 54–56 secondary fragments with flashes → 650 small shards. Another 280 particles and 60 debris pieces are spread across the scene. The geometry is deterministic; the Time Stone reassembles it.

## Meteor Stream
- Select **Meteor Stream** in the Power Stone wheel. In the Overworld, hold **R** while aiming at the Sun or Moon to lock on. Then aim the ring at terrain or water and release **R** to fire.
- The ring follows the surface. By default, meteors fall for **60 seconds** within a **24-block radius**. Server settings govern player damage and block changes.

## Sun and Moon Destruction (Power Stone, 3rd mode)
- In "Sun and Moon Destruction" mode, **aim the gauntlet at the sun/moon and hold R for 3 s** — lock-on (a purple thread to the luminary).
- The luminary cracks in 4 stages (original texture + pack cracks), splits into 16 pieces that fall down,
  and the sky within 256 blocks turns red (red-orange by day, dark crimson at night).
- **A 5-minute real-time meteor shower** (survives lag and restarts): craters, explosions, fires, damage.
- A destroyed luminary stays destroyed (stored separately from the sky).
- **Sun destroyed → eternal night with the moon**: darkness falls in ~5 s, and at dawn the time returns to the start of the night (moon phases keep advancing).
  **Moon destroyed** → days as usual, only stars at night.

## Reality Stone — "Reality Inversion"
- Select Reality Inversion and **hold R** (closed fist) — you can move freely while channeling: charge at the fist → red cracks → the world crumbles into cubic fragments and
  turns inside out along your path and gaze. The ground on both sides of your gaze folds into walls, buildings twist, water hangs
  in the air. **Walk and turn your head**: the rift moves and turns with you. Eligible mobs and PvP targets in the vortex burst into red butterflies, and flying arrows turn
  into petals. **Release R** (open fist): the twisted blocks (ground, buildings, and suspended water) remain physically twisted in the world forever.
- Thanos and Loki take damage once per cast when caught in the vortex.
- Radius and block limit are in the config (`reality*`).
- Select Reality Strike and press **R** — a stone strike on the ground: a red wave spreads from the fist. Affected mobs dissolve into red butterflies without drops. Eligible players take lethal damage unless protected. The Ender Dragon, Wither, your pets and named mobs are spared, while Thanos and Loki take damage. The ground within `realityStrikeRadius` (10) becomes redstone blocks, with fire in places. This change is **real** and stays. Cooldown `realityStrikeCooldownTicks`.

## Space Stone
- With Portal selected, **R** opens and closes it. **Shift+R** — choose the dimension: Nether / End / Overworld
  (the current dimension is skipped). Step into the portal to arrive there; it lasts 30 s.
  Nether — coordinates /8 and a safe platform, End — the main island, Overworld — the surface.
- Blink (**R**) — teleport where you look (up to 96 blocks), with a blue flash and a trail of swirls.

## Time Stone
- Day / Night (**R**) — change the time of day (Overworld only): the clock speeds up smoothly over ~6 s, the sun rushes below the horizon and the Moon rises (and vice versa). Cooldown 30 s; after the sun is destroyed, day will not come. Parameters: `timeShiftTicks`, `timeShiftCooldownTicks`.
- **R** — rewind time: the player is "played back" along their path over the last 5 s, health and hunger return to that moment.
- In Dead Space **R** undoes the destruction: the planet and debris reassemble, a green flash, and all participants of the same event return home.

## Mind Stone — telekinesis
- Grab (**R**) on the ground — tear out a chunk of real blocks (radius `mindGrabRadius`, up to `mindMaxBlocks`): any blocks except unbreakable ones (bedrock etc.) and blocks with contents (chests, furnaces). The cluster hangs in front of you and smoothly follows your gaze.
- **R** again — throw: the cluster flies in an arc, hits everything around on impact (`mindThrowDamage`, grows with size), knocks back and crumbles back into real falling blocks.
- Grab (**R**) on a mob — lift and hold (bosses are excluded, player grabs depend on PvP settings). **R** — throw it, **Shift+R** — **tear apart**: the mob trembles, turns red and bursts into pieces of its own model (loot and XP drop).
- Release (**R**) with blocks — just release. On logout, death or removing the gauntlet the blocks fall and are not lost.

## Soul Stone — soul extraction
- Soul Extraction (**R**) on a mob — the soul is pulled out of the body (it hovers), the mob dies, the soul flies to you in an arc and is absorbed.
  Each soul: **+1 ❤ max health and +0.5 damage**, healing; they stack up to `soulMaxStacks` (20), last `soulBuffTicks` (60 s), each new soul extends the timer. Status in the top left.
- Mass Extraction (**R**): a wave within `soulMassRadius` (24) pulls out the souls of all enemies (up to `soulMassMaxTargets`), killing them; all souls fly to you. Cooldown `soulMassCooldownTicks` (45 s).
- Bosses, peaceful mobs (for mass extraction) and named mobs are not affected. The bonus is not saved to player data.

## Debug commands (operator level)
| Command | What it does |
|---|---|
| `/gauntlet give_power` | gauntlet with the Power Stone in the offhand |
| `/gauntlet give_time` | insert the Time Stone into the worn gauntlet |
| `/gauntlet give_stone <stone>` | insert any stone (power, space, reality, soul, mind, time) |
| `/gauntlet fx <effect>` | show a single effect: power_strike, power_impact, power_shockwave, power_sparks_smoke, ground_fracture, planet_rift, detonation_flash, void_dissolve, camera_shake, white_flash, lift_debris |
| `/gauntlet cutscene` / `cutscene stop` | start or stop the destruction cutscene. Starting requires a Time Stone in the worn gauntlet |
| `/gauntlet celestial sun\|moon` | destroy a luminary without aiming |
| `/gauntlet celestial stop` / `restore` | stop the rain / restore the sun and moon |
| `/gauntlet space enter` | go straight to Dead Space |
| `/gauntlet space return [player]` | return a player and reset the state |
| `/gauntlet state` | current state: IDLE/CHARGING/STRIKE/DESTRUCTION/TRANSFER/SPACE |
| `/gauntlet reload_config` | reload `config/infinity_gauntlet.json` |
| `/gauntlet power_shower` | start a Power meteor shower now (Overworld) |
| `/gauntlet reality_fog` | wrap you in the Reality Stone's red mist (Nether) |
| `/gauntlet thanos start\|now\|spawn\|stop\|reset` | Thanos: with a warning / at once / already fighting / send away / allow him again |
| `/gauntlet full` | awakening-ready gauntlet with all six stones in the offhand (not registered as found stones) |
| `/gauntlet stormbreaker_fall` | summon a thunderstorm and drop Stormbreaker nearby (only if none exists yet) |
| `/tribunal challenge\|verdict\|kill` | Tribunal: start (operators bypass all gates) / verdict cutscene / instant win |
| `/gauntlet loki reset\|spawn` | Reset the Ruined City fight or spawn a standalone Loki at the command location in any dimension. The standalone fight grants no quest loot. |

## Settings
`config/infinity_gauntlet.json`: strike damage/radius/knockback/cooldown, ultimate charge and cooldown,
participant radius (`participantRadius`), whole-dimension transfer (`transferWholeDimension`, off), transfer tick,
camera shake and flash strength, space flight parameters, particle limit.

Ideas: [IDEAS.md](IDEAS.md) · API notes: [docs/DEV_NOTES.md](docs/DEV_NOTES.md) · Pack resources: `art/vfx/`, `art/mind_stone/`, `art/soul_stone/`

## The Solar System

Fly beyond the sky with the Space Stone. **Ctrl** boosts flight; the wheel's **Destinations** menu selects a route and **R** performs the warp. Ten unique icons identify the Sun, Mercury, Venus, Earth, Moon, Mars, Jupiter, Saturn, Uranus and Neptune. Approach Earth's atmosphere after a warp to return to the Overworld. Planets rotate slowly on the saved world clock with their sidereal period ratios and axial tilts; Venus and Uranus have retrograde axes. The Sun has an unlit bright surface and a broad white-gold corona.

Gauntlet powers work in three dimensions: Power and Reality strikes produce visible rays and impact flashes, Reality Inversion bends the surrounding starfield and damages once per activation, Beam and Time Flow hit along their paths, Soul powers reach distant targets, and blink and aimed meteor fragments work during flight. Warp selection is separate from casting, so browsing routes never triggers a jump.

## Saves and development launches

An equipped awakened Gauntlet keeps its state when rejoining. Completed advancement syncs do not replay notifications; newly earned and explicitly revoked/re-earned achievements still notify normally.

`./gradlew runClient` uses the stable offline profile **Player433**. Minecraft otherwise chooses a different `Player###` and UUID, making advancements appear new despite the retained singleplayer inventory. For a different development profile use the same `-PdevUsername=YourName` every time. Standard launcher accounts are unaffected.
