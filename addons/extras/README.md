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

## Trophy fish, boombox look and the Holy Lois UI (1.7.0)

- Trophy fish (custom_data `holylois_fish`, now with `size`) are drawn bigger by weight in hands, on the ground, in frames and on
  the Farmer's Delight cutting board (FishLook + Fish*Mixin); your own first-person view grows a sixth as much. Slots get a rarity
  glow and the weight in the corner. Mythic: 1/60 of Legendary catches, 1.5-2.5x the species maximum, red, announced with a sound.
  Filleting gives 2-8x the cutting results by size (FilletMixin); `data/holylois/recipe/cutting/thieves_fish.json` lets every Fish
  of Thieves fish be sliced. Eating Rare and better fish gives short effects (Legends.buffs).
- Boombox: new 3D model (make-texture.py writes textures and models); while playing, the speaker cones pulse with the voice chat
  audio level the client already receives (BoomboxPulse, no extra packets).
- Ui (and auth-ui AuthUi): the launcher palette and small rounded shapes for every screen and HUD card. Zone titles sit below Jade,
  colour-coded with an outline; structures come from the server once a second (StructureZone).
- ChestReplay: resets Fresh Animations' chest animation state when a shut chest comes back into view (no replayed close).
- XaeroTpaOption: world map right-click on a player sends `/tpa NAME` for players without /tp rights.
- Capture (operators): `/capture ultra` and `/capture panorama [seconds]` for the title panorama.

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

## Legends, fishing treasure and fish weights (1.6.0, server side)

Nothing here needs client code: names, inscriptions, enchantments and the glint are item components, so they show in any client.

- Structure chests (any loot table matching `chestTables`, by default `*:chests/*`: vanilla, Dungeons and Taverns, Towns and Towers)
  hold one legend item at `chestChance` (1.2% per chest). Each item belongs to a legend (a set), has a name in the legend's colour,
  grey italic inscriptions and optional enchantments, and carries `custom_data` `{holylois_legend: "<item id>", holylois_legend_set: "<legend id>"}`
  so the Holy Lois advancements can find it.
- When vanilla fishing rolls treasure, `treasureChance` (30%) of those catches become a Holy Lois treasure instead: a message in a
  bottle (paper with a short text), a real buried treasure map (`data/holylois/loot_table/gameplay/treasure_map.json`) or a
  fishing-only legend item. Fished-up loot crates (`crateTables`, by default anything with "crate" in its id) hold a fishing-only
  legend item at `crateChance` (5%).
- Items with `"light": true` also carry `{holylois_light: 1b}`; LambDynamicLights lights them when held or dropped through
  `assets/holylois/dynamiclights/item/legend_light.json` (its `match` is a vanilla item predicate).
- Every fish caught (`tables`, by default `minecraft:gameplay/fishing`, so vanilla and Fish of Thieves fish pass once) gets a size
  between the lightest and heaviest weight of its species. Common (about 76%) stays a plain fish and stacks as before. Uncommon (13%)
  gets a green name. Rare (7%), Epic (3%) and Legendary (1%) keep their weight in kg, the angler and the day, and do not stack:
  they are trophies. Luck (Luck of the Sea) makes big fish more likely. A Legendary catch is announced in chat. The data is in
  `custom_data` `{holylois_fish: {rarity, kg, species, by, day}}`; Fish of Thieves keeps its own variant data next to it.
- Files: `config/holylois-legends.json` (legends, items, chances, bottle texts) and `config/holylois-fish.json` (species ranges in kg,
  fish tags and namespaces). Without a file that part is off. Operators: `/legends reload`, `/legends list` and
  `/legends give <item id | bottle | map | fish>` to look at an item without waiting for luck.
- `LegendsTest` checks the rules and the config files that go live (the build runs it).

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
- No file, an empty `allowed` list or mode `off` means no check.
- Fabric Loader's own built-in mods (java, minecraft, fabricloader, mixinextras) are always allowed (1.6.0), so a list made from the
  pack's jars cannot turn players away for them again. Shaders, resource packs and settings are never part of it.
- This stops accidents and casual extras, not a determined cheat client, which can fake its list. The launcher moves extra jars out of the
  game folder and repairs changed pack files when Play is pressed.
- The `allowed` list is the mod ids of the pack, including libraries nested inside jars: `work/make-mod-allowlist.py`.

## Vein mining tweak

LiteMiner on Holy Lois is shapeless only. On the client this mod removes the shape name from the LiteMiner HUD (only the
selected block count remains) and stops the mouse wheel from switching shapes; the server add-on forces shapeless mining.
