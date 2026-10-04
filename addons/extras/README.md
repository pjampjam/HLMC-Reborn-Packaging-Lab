# Holy Lois Extras

A boombox for Holy Lois: Reborn that plays internet radio to everyone within 24 blocks through Simple Voice Chat.

- Held (either hand): right-click the air to play or switch station, sneak + right-click to turn it off. The sound
  follows the player. It stops at once when it leaves the inventory and two seconds after it is no longer held.
- Placed (right-click a block with it): right-click plays or switches, sneak + empty hand turns it off. The block state
  keeps the station and on/off; switched-on boomboxes are listed in `world/holylois/boomboxes.json`, stream only while a
  player is within earshot, and resume after a restart.
- While a boombox plays near a player, the server tells that client (`holylois:boombox_near`) and the game music pauses.
- Voice chat settings get a separate "Boombox" volume slider.

- Recipe: string on top, iron ingot + jukebox + iron ingot in the middle, amethyst shard + redstone + amethyst shard below.
- Stations live in `config/holylois-boombox.json` on the server (`/boombox reload` for operators, `/boombox stations` for everyone).
  Only plain MP3 streams over HTTP or HTTPS are supported. Station owners' terms apply.
- At most 6 boomboxes play at once.
- Bundles JLayer 1.0.1 (javazoom, LGPL-2.1) unmodified as a nested library for MP3 decoding.

Built on the server with `compile-extras.py` against the installed Minecraft 26.3, Fabric API and Simple Voice Chat.

## Client fixes

- Better Advancements 0.6.0.78 ignored tab clicks whenever Num Lock or Caps Lock was on; tabs now switch on any left click.
- Advancement tabs are ordered: Holy Lois, then Minecraft, Nether, End, Adventure, Husbandry, then mods alphabetically.
- Nemo's Enchantments' own "Hold Shift" descriptions are skipped; Enchantment Descriptions shows them directly.
- One-time REI defaults before mods load (`ClientDefaults`): the item list shows only while searching and the developer
  Tags tab is hidden. Each change runs once and only while REI's original value is still set.

## Vein mining tweak

LiteMiner on Holy Lois is shapeless only. On the client this mod removes the shape name from the LiteMiner HUD (only the
selected block count remains) and stops the mouse wheel from switching shapes; the server add-on forces shapeless mining.
