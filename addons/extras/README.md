# Holy Lois Extras

A portable boombox for Holy Lois: Reborn. Hold it in either hand and right-click to play internet radio; everyone
within 24 blocks hears it through Simple Voice Chat, and the sound follows the player. Right-click again for the next
station, sneak + right-click to turn it off. It stops when it is no longer held. Voice chat settings get a separate
"Boombox" volume slider.

- Recipe: string on top, iron ingot + jukebox + iron ingot in the middle, amethyst shard + redstone + amethyst shard below.
- Stations live in `config/holylois-boombox.json` on the server (`/boombox reload` for operators, `/boombox stations` for everyone).
  Only plain MP3 streams over HTTP or HTTPS are supported. Station owners' terms apply.
- At most 6 boomboxes play at once.
- Bundles JLayer 1.0.1 (javazoom, LGPL-2.1) unmodified as a nested library for MP3 decoding.

Built on the server with `compile-extras.py` against the installed Minecraft 26.3, Fabric API and Simple Voice Chat.

## Vein mining tweak

LiteMiner on Holy Lois is shapeless only. On the client this mod removes the shape name from the LiteMiner HUD (only the
selected block count remains) and stops the mouse wheel from switching shapes; the server add-on forces shapeless mining.
