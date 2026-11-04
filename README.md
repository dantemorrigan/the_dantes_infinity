# Infinity Gauntlet (Fabric, Minecraft 26.1.2)

🇬🇧 English · [🇷🇺 Русский](README.ru.md)

Requirements: **Java 25**. Build: `./gradlew build` → `build/libs/infinity_gauntlet-0.1.0.jar` (+ Fabric API 0.155.3+26.1.2).

```
./gradlew runClient          # test client
./gradlew runClientGameTest  # visual run with screenshots (build/run/clientGameTest/screenshots)
./gradlew runClientGameTest -PonlyTest=MindSoulTest  # a single test (headless Linux: xvfb-run -a)
```

## Ability wheel controls

- Equip the gauntlet in your offhand. Hold **V** to open the wheel; point at a stone, then at an ability, and release **V** to select.
- **R** — use the selected action; hold and release for charged strikes, the beam and Reality Inversion. Opening the wheel cancels a charge without attacking.
- **Esc** cancels the selection. **PgUp/PgDn** switch stones, arrows/Tab switch actions, Enter confirms. `abilityWheelToggle: true` in the config makes the wheel open on press instead of hold.
- Left-click the center — insert stones. Selecting Portal or Destinations opens a separate dimension/route menu; left-click/Enter confirms, using it with R is separate. **Shift+R** with a portal selected quickly switches the dimension; the target is shown in the status.
- Telekinesis: R grabs/throws; "Tear apart" and the safe "Release" are separate labeled entries. **Shift+R** with a grabbed mob tears it apart immediately. Switching to another stone safely releases the target.
- Flight stays active when you select another stone. **Ctrl** boosts; to turn it off, select Flight and press R. In space this entry returns you to the atmosphere near Earth, keeping manual flight.
- Stone and gauntlet icons show the mod's existing items and the active resource pack. The selected ability card is in the top-left panel.

The old quick keys described below are now **unbound by default** but still available in the controls settings. Historical factory bindings are cleared once; custom ones are kept. The old right-click charge with the gauntlet in the offhand is enabled separately via `legacyRightClickPower: true`.

Testing the new controls: `xvfb-run -a ./gradlew runClientGameTest -PonlyTest=AbilityWheelTest`. The old tests temporarily switch back to the previous input scheme on their own.

## Infinity Beam — all six stones
- Equip a gauntlet holding all stones in your offhand, wait for it to awaken and **hold H**: 2 s charge, then a 6 s beam. Releasing interrupts the attack.
- Range 72 blocks; full contact with the dragon removes 70% of its max health (140 HP). Misses reduce the damage; crystal healing is independent.
- The beam drills a tunnel up to 9 blocks wide with no item drops; bedrock is preserved and no new chunks are loaded.
- Cooldown 45 s (disabled in creative mode). Parameters: `beamChargeTicks`, `beamFireTicks`, `beamCooldownTicks`, `beamRange`, `beamDamage`, `beamHitRadius`, `beamBreakBlocks`, `beamDrillRadius`, `beamBlocksPerTick`.
- Ability statuses and the rain timer are shown in a separate top-left panel in bold.
- The charge, beam barrel, flash and impact animations come from the supplied Infinity Beam Ultimate Basic; the six colored spirals are built at render time.

## Power Stone
- The gauntlet only works **in the offhand** (F). Right-clicking with the gauntlet in the main hand opens the stone menu.
- **Hold right-click** — charge (purple aura at the fist), **release** — power strike (damage, knockback and cooldown are handled on the server).
- **V** — switch mode: power strike → **Planet Destruction** → **Sun and Moon Destruction**.
- Ultimate: "Planet Destruction" mode, full charge (2 s), strike the ground beneath you.
- The destruction ends the meteor rain, closes portals in the source world and interrupts its active beams/rewinds. The red sky and fog only apply in the Overworld.
- In space: a large voxel Earth with simplified continents based on [NASA Blue Marble](https://science.nasa.gov/resource/blue-marble/), ocean, polar caps and clouds; current vanilla block textures are used.
- Breakup: 14 uneven large fragments → 54–56 secondary fragments with flashes → 650 small shards. Another 280 particles and 60 debris pieces are spread across the scene. The geometry is deterministic; the Time Stone reassembles it.

## Sun and Moon Destruction (Power Stone, 3rd mode)
- In "Sun and Moon Destruction" mode, **aim the gauntlet at the sun/moon and hold right-click for 3 s** — lock-on (a purple thread to the luminary).
- The luminary cracks in 4 stages (original texture + pack cracks), splits into 16 pieces that fall down,
  and the sky within 256 blocks turns red (red-orange by day, dark crimson at night).
- **A 5-minute real-time meteor shower** (survives lag and restarts): craters, explosions, fires, damage.
- A destroyed luminary stays destroyed (stored separately from the sky).
- **Sun destroyed → eternal night with the moon**: darkness falls in ~5 s, and at dawn the time returns to the start of the night (moon phases keep advancing).
  **Moon destroyed** → days as usual, only stars at night.

## Reality Stone — "Reality Inversion"
- **Hold G** (closed fist): charge at the fist → red cracks → the world crumbles into cubic fragments and
  turns inside out. The ground on both sides of your gaze folds into walls, buildings twist, water hangs
  in the air. **Turn your head**: the rift follows your hand. Mobs turn into red butterflies, flying arrows
  into petals. **Release G** (open fist): everything flows back, mobs return.
- The world on the server does not change: these are real blocks of the current resource pack, temporarily removed only on your client.
  Mobs always return (release, death, logout, dimension change, even a server restart).
- You can't walk while holding: you control the "hand", i.e. your gaze. Radius and block limit are in the config (`reality*`).
- **Tap G** — a stone strike on the ground: a red wave spreads from the fist. All mobs it reaches (except bosses, your pets and named mobs) turn into red butterflies and dissolve into the air, with no drops. The ground within `realityStrikeRadius` (10) becomes redstone blocks, with fire in places. This change is **real** and stays. Cooldown `realityStrikeCooldownTicks`.

## Space Stone
- **B** — open a portal in front of you (B again — close). **Shift+B** — choose the dimension: Nether / End / Overworld
  (the current dimension is skipped). Step into the portal to arrive there; it lasts 30 s.
  Nether — coordinates /8 and a safe platform, End — the main island, Overworld — the surface.
- **N** — teleport where you look (up to 96 blocks), with a blue flash and a trail of swirls.

## Time Stone
- **Y** — change the time of day (Overworld only): the clock speeds up smoothly over ~6 s, the sun rushes below the horizon and the Moon rises (and vice versa). Cooldown 30 s; after the sun is destroyed, day will not come. Parameters: `timeShiftTicks`, `timeShiftCooldownTicks`.
- **R** — rewind time: the player is "played back" along their path over the last 5 s, health and hunger return to that moment.
- In Dead Space **R** undoes the destruction: the planet and debris reassemble, a green flash, and all participants of the same event return home.

## Mind Stone — telekinesis
- **Z** on the ground — tear out a chunk of real blocks (radius `mindGrabRadius`, up to `mindMaxBlocks`): any blocks except unbreakable ones (bedrock etc.) and blocks with contents (chests, furnaces). The cluster hangs in front of you and smoothly follows your gaze.
- **Z** again — throw: the cluster flies in an arc, hits everything around on impact (`mindThrowDamage`, grows with size), knocks back and crumbles back into real falling blocks.
- **Z** on a mob — lift and hold (bosses and players can't be lifted). **Z** — throw it, **Shift+Z** — **tear apart**: the mob trembles, turns red and bursts into pieces of its own model (loot and XP drop).
- **Shift+Z** with blocks — just release. On logout, death or removing the gauntlet the blocks fall and are not lost.

## Soul Stone — soul extraction
- **O** on a mob — the soul is pulled out of the body (it hovers), the mob dies, the soul flies to you in an arc and is absorbed.
  Each soul: **+1 ❤ max health and +0.5 damage**, healing; they stack up to `soulMaxStacks` (20), last `soulBuffTicks` (60 s), each new soul extends the timer. Status in the top left.
- **Shift+O** — mass extraction: a wave within `soulMassRadius` (24) pulls out the souls of all enemies (up to `soulMassMaxTargets`), killing them; all souls fly to you. Cooldown `soulMassCooldownTicks` (45 s).
- Bosses, peaceful mobs (for mass extraction) and named mobs are not affected. The bonus is not saved to player data.

## Debug commands (operator level)
| Command | What it does |
|---|---|
| `/gauntlet give_power` | gauntlet with the Power Stone in the offhand |
| `/gauntlet give_time` | insert the Time Stone into the worn gauntlet |
| `/gauntlet give_stone <stone>` | insert any stone (power, space, reality, soul, mind, time) |
| `/gauntlet fx <effect>` | show a single effect: power_strike, power_impact, power_shockwave, power_sparks_smoke, ground_fracture, planet_rift, detonation_flash, void_dissolve, camera_shake, white_flash, lift_debris |
| `/gauntlet cutscene` / `cutscene stop` | start/abort the destruction cutscene without checks |
| `/gauntlet celestial sun\|moon` | destroy a luminary without aiming |
| `/gauntlet celestial stop` / `restore` | stop the rain / restore the sun and moon |
| `/gauntlet space enter` | go straight to Dead Space |
| `/gauntlet space return [player]` | return a player and reset the state |
| `/gauntlet state` | current state: IDLE/CHARGING/STRIKE/DESTRUCTION/TRANSFER/SPACE |
| `/gauntlet reload_config` | reload `config/infinity_gauntlet.json` |

## Settings
`config/infinity_gauntlet.json`: strike damage/radius/knockback/cooldown, ultimate charge and cooldown,
participant radius (`participantRadius`), whole-dimension transfer (`transferWholeDimension`, off), transfer tick,
camera shake and flash strength, space flight parameters, particle limit.

Ideas: [IDEAS.md](IDEAS.md) · API notes: [docs/DEV_NOTES.md](docs/DEV_NOTES.md) · Pack resources: `art/vfx/`, `art/mind_stone/`, `art/soul_stone/`
