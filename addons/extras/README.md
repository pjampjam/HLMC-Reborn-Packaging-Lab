# Holy Lois Extras

A boombox for Holy Lois: Reborn that plays internet radio to everyone in earshot (16 blocks at volume 1 up to 48 at volume 10) through Simple Voice Chat.

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

- Better Advancements 0.6.0.78 ignored tab clicks whenever Num Lock or Caps Lock was on (upstream issue #260); tabs now
  switch on any left click. 26.3 numbers mouse buttons from 1, so left click is button 1 (1.1.0 checked for 0).
- The placed boombox uses `block/boombox_front`: 26.3 block models cannot use item-atlas textures.
- Translated text (vanilla and mods) shows a plain hyphen wherever a translation used an em dash (`LanguageDashMixin`).
- Advancement tabs are ordered: Holy Lois, then Minecraft, Nether, End, Adventure, Husbandry, then mods alphabetically.
- Nemo's Enchantments' own "Hold Shift" descriptions are skipped; Enchantment Descriptions shows them directly.
- One-time REI defaults before mods load (`ClientDefaults`): the item list shows only while searching and the developer
  Tags tab is hidden. Each change runs once and only while REI's original value is still set.

- Tool swap (`ToolSwap`, 1.3.1): when the held pickaxe, axe, shovel, hoe, sword or shears breaks, an unenchanted spare of the same kind is
  swapped into the hand slot (lowest material first, then the most worn; spares with 5% or less durability are skipped; enchanted
  tools are never chosen). Inventory Profiles Next treats these tools as blacklisted (`IpnToolSkipMixin`) so only one system acts.
- `R` on an empty slot sorts and no longer also opens a REI recipe: `IpnSortKeyMixin` stamps the key press, `ReiSortGuardMixin`
  swallows REI's handling of that same press.
- FirstPerson (`FirstPersonFixes`): the body offset is shortened by a raycast when a wall is behind the player, and the body is
  switched off while sleeping (the mod skips its own offset in bed) and back on afterwards unless the player turned it off.
- Shader lights (`ShaderLights`, reflection only): when the Iris shader state changes (and once at start) LambDynamicLights is set to
  match: with a shader pack on the first-person light is off (the pack lights held items in the right colour) while dropped items and
  mobs keep their light; with shaders off everything is on, including the first-person light. A mode of OFF is switched to FANCY.
- Bed camera (`BedCameraMixin`): in bed the first-person camera is lifted 0.3 and moved 0.3 forward so it is not inside the head.

## Mod check

Holy Lois runs on its pack and nothing else. While a player joins (configuration phase, next to the pack check) the client reports its
mod ids and the server compares them with `config/holylois-mods.json`:

```
{"mode": "enforce" | "warn" | "off", "missing": "warn" | "kick", "legacy": "allow" | "block", "exempt": ["name"], "allowed": ["modid"]}
```

- A mod that is not in `allowed` turns the player away (mode `enforce`) with a message in English, Russian and Latvian that names the mods
  and points to Repair / check files in the launcher. In mode `warn` it is only logged.
- A missing allowed mod is only logged until `missing` is `kick`.
- A client too old to answer is let in while `legacy` is `allow`. Names in `exempt` skip the check (the owner's workshop profile).
- No file, an empty `allowed` list or mode `off` means no check. Shaders, resource packs and settings are never part of it.
- This stops accidents and casual extras, not a determined cheat client, which can fake its list. The launcher moves extra jars out of the
  game folder and repairs changed pack files when Play is pressed.
- The `allowed` list is the mod ids of the pack, including libraries nested inside jars: `work/make-mod-allowlist.py`.

## Vein mining tweak

LiteMiner on Holy Lois is shapeless only. On the client this mod removes the shape name from the LiteMiner HUD (only the
selected block count remains) and stops the mouse wheel from switching shapes; the server add-on forces shapeless mining.
