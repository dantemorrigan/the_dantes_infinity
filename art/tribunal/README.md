# Living Tribunal asset pack

The model, gold/lavender atlas, emissive atlas and singularity/halo/edict textures are the assets from checkpoint `5f7ea703` (original local draft `d2cd8a8`). Runtime files live in `assets/infinity_gauntlet/tribunal`, `textures/entity/tribunal` and `textures/tribunal`.

The canonical reference is a giant golden humanoid with a floating, three-faced, cloaked head: [official MARVEL SNAP character spotlight](https://marvelsnap.com/character-spotlight-the-living-tribunal/), plus the three reference images supplied by the user. The three faces, lavender covering and luminous chest seal are preserved in the Minecraft model.

`TribunalRenderer` animates emergence, floating torso/head, attack windups, reaching palm, closing fist, the flying Gauntlet and shattering/departure. The same animated hand transform drives the cinematic camera and particle origin. Telegraphed radii match the server's hit radii.

The supplied MP3 recordings are bundled as Minecraft-compatible Vorbis in `sounds/tribunal`: `arrival.ogg` (Ты слишком долго играл в Бога), `surrender.ogg` (Сдайся), `defeat.ogg` (Это невозможно), and `battle_music.ogg` (Void Battle Cosmic Finale). Voice lines have translated subtitles. The theme streams, loops, fades and stops on scene termination or disconnect.

Smoke test: `ALSOFT_DRIVERS=null xvfb-run -a ./gradlew runClientGameTest -PonlyTest=TribunalTest`. Screenshots and logs appear in `build/run/clientGameTest/`.
