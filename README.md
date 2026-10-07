# Holy Lois: Reborn

A Windows launcher and updater for the Holy Lois modpack. Play with a player name (fast start: the app starts Minecraft itself) or with a bought Minecraft account (the app opens Minecraft Launcher), install the pack, then press Play.

## Download

[Download HolyLoisReborn.exe](https://github.com/pjampjam/HLMC-Reborn-Packaging-Lab/releases/latest/download/HolyLoisReborn.exe) - about 67 MB. Windows x64, with its .NET runtime included.

This is the full release. Existing preview installations move to the standard HolyLoisReborn folder, and owned launcher profiles and shortcuts lose their Preview names. The original small installers in HLMC-Reborn remain withdrawn. Do not download those older installers.

1. Open the downloaded EXE. Choose **Player name** (type the name you want in the game) or **Minecraft account**, and optional shortcuts.
2. Close Minecraft, then click Install Holy Lois.
3. Click Play beneath the logo. With a player name, Minecraft opens straight from the app and joins Holy Lois. With a Minecraft account, Minecraft Launcher opens: select Holy Lois: Reborn and press Play there, and the game joins Holy Lois by itself.

Already using Holy Lois? Close and reopen your installed app or its shortcut. Its signed app-stable channel updates it to 1.3.1 before showing the main window. You do not need another installer. An old downloaded copy hands off to the newer installed version after its verified update.

The app is unsigned by a Windows publisher certificate. SmartScreen may show Unknown publisher. Local scans and previous user download tests do not guarantee every antivirus result. If Defender detects a threat, stop and report the detection rather than adding an exclusion.

## Prepared launcher 1.3.2 / pack 1.8.3

One Holy Lois wordmark with a REBORN subtitle, compact crowned HL icons for launcher update/fast-start windows, a centred language chevron and updated EN/RU/LV help. The pack fixes saved skins after rejoining and offers `/party` and `/group` aliases; website guides and skin heads are clearer.

Candidate only. Published app-stable remains 1.3.1 and pack-stable remains 1.8.2. Real skin/marker visuals and the launcher custom Defender scan are still release checks.

## Pack 1.8.2

Extras1.6.2 adds OPAC party-friendly damage/effect guards, a compact optional health/food HUD and private90sec rally marks. Onboarding1.8.2 uses the native daily-coin ledger and sends reward receipts after login. Auth UI1.0.5 handles separate/early auth messages and bounded native voice-handshake retry. The launcher EXE stays1.3.1; its layout/Settings rewrite remains a separate app patch. Published pack 1.8.2 updates independently of the unchanged launcher 1.3.1. Earlier signed releases remain untouched.

## Pack 1.8.1

The signed pack updates independently of launcher 1.3.1. Extras 1.6.1 preserves placed relic data, fixes crate rewards and visible-body rendering, and extends claim protection to radio controls and player-attributed mob damage/pushing. Onboarding 1.8.1 closes combat teleport bypasses and exports selected skin textures for stats. Auth UI 1.0.4 quiets world audio during login/register. Original water/DH behaviour is retained. Source-only help changes await a future EXE.

## Version 1.3.1

- The server-list icon uses the owner's handmade 64px artwork. Website and live-map icons now match the original branding. Gameplay and pack 1.8.0 stay unchanged.

## Version 1.3.0

- **Fast start.** For player-name accounts the app starts Minecraft itself: no SKlauncher, no clicking through another launcher. A start window shows each step (pack check, Java 25, Minecraft and Fabric, sounds and textures, starting, loading mods) with a progress bar, then steps aside once the game window is open. If the game crashes, the app comes back with a crash window.
- Java 25 (Mojang's own build), Minecraft 26.3 and Fabric are fetched from Mojang and Fabric and checked by hash. Fabric files are checked against the signed pack. Identical files that another launcher already has on the PC (Minecraft Launcher, its Microsoft Store version, SKlauncher) are linked or copied instead of downloaded. Later starts only compare file sizes and times, and work offline once everything is in place.
- Memory follows the computer (3 to 8 GB) with Mojang's recommended garbage collector settings. The long Java command goes through an argument file, so Windows' command line limit never cuts it.
- **Player names.** The first start asks for a name (3 to 16 letters, digits or _). SKlauncher players keep the name they already play as, read from their game log, and their game folder, worlds and settings. Settings can change the name with a clear warning (a new name is a new player on the server); every earlier name stays one click away. Three new names a day at most. A name that belongs to a bought Minecraft account is refused, because the server would ask for that account. Names live in the app's data folder; uninstalling keeps them unless "Also forget my player names" is ticked.
- **Join Holy Lois on start** (on by default, in Settings). Fast start passes Minecraft's own join option; for a bought account the Minecraft Launcher profile carries the same option, so the game joins by itself there too. Turn it off for singleplayer.
- SKlauncher players can switch back to **Open SKlauncher** in Settings. A bought account keeps opening Minecraft Launcher: starting the game from this app would need a Microsoft sign-in that Mojang only allows for approved launchers.
- **Help and reports** in Settings: copy a report or save it as a zip on the Desktop, and open Discord. The report has the last start, the game output, the game log and the newest crash report, with the Windows user name, login details and IP addresses removed.
- Opening the app while the game runs brings the hidden window back instead of doing nothing.
- A softer, game-like Play button: green face on a darker lip that presses down. First setup asks "Account or player name" instead of "which launcher".

## Version 1.2.7

- Quick Play is removed. In testing it did not join the server by itself, so Play did the same as Standard. Play opens your Minecraft launcher; you join Holy Lois from Multiplayer in the game, and singleplayer works as before.
- Settings keep **Standard** (opens your launcher) and show **Integrated** as coming later: the app will start the game itself, with its own Microsoft sign-in.
- A leftover `holylois-quickplay.json` from 1.2.6 is deleted the next time you press Play. Pack 1.7.11 embedded (Holy Lois Extras 1.5.1 without Quick Play).

## Version 1.2.6

- Play modes in Settings: Quick Play (removed in 1.2.7, it never joined by itself), **Standard** (only opens your launcher) and **Integrated** (coming later, needs its own Microsoft sign-in).
- When an update needs Minecraft or its launcher closed, the app asks first (**Agree**) and closes them for you.
- New launcher and Minecraft window icons. Pack 1.7.10 embedded.

## Version 1.2.2

- A freshly downloaded copy no longer stops with "An existing installation differs from this copy". It hands over to the installed app, which updates itself from app-stable, or replaces a damaged or unknown installation after keeping a backup in `rollback/`.
- Pack 1.7.1: Enhanced Block Entities chest rendering is off, so chests open cleanly with shaders.

## Version 1.2.1

- A Discord button under the address (a Website button appears once the site has its domain).
- Live server status under the address: a green dot and address when the server is online with the number of players, gold while it is restarting and red when it is offline. Hover it to see who is online.
- How to play lists the current commands (/tpahere, /spawn, /warp, /rules, /audioplayer) and every map, voice, zoom and vein-mining key.
- Known key clashes are fixed once per pack update without touching other controls: the world map moves from M back to J when M is voice mute, and the voice icon and creative toolbar keys give way to the minimap and zoom. F3 debug combinations never count as clashes.
- Xaero minimap settings from the pack change only the options they list, so personal minimap choices stay.
- Pack 1.7.0 adds Macaw's Holidays, Legendary Tooltips, LambDynamicLights, Falling Leaves, Continuity, Shulker Box Tooltip, Status Effect Bars and Controlling, and fixes chest flicker with shaders.

## Version 1.2.0

- New color system: layered charcoal surfaces, warm Holy Lois gold for the next step, a stronger green Play button, red only for removal and failures, and one 8-pixel shape for buttons, fields, menus and panels.
- The selected launcher card gets a warm tint as well as its gold outline and Selected label. Text fields show a gold border while typing.
- Hover and press use neutral washes over every button color, so each action keeps readable text. Keyboard focus rings and reduced-motion support are unchanged.
- Keybinds from newly added mods are seeded once when a pack update introduces them, only if the player has no binding for that control yet and the key is free. Personal and existing controls are never replaced.
- Pack 1.6.0 adds Farmer's Delight cooking, Macaw's Furniture, Xaero's Minimap and World Map (J opens the map, so M stays voice mute) and Jade.

## Version 1.1.0

- Recover stale SKlauncher folder links before copying files, including moved libraries and older external instances.
- Clear verified duplicate app downloads and stages after updates. Keep the working app and one rollback copy.
- Remove completed mod downloads only when the installed file and cache both match the approved hash. Worlds, personal settings and extra mods stay.
- Settings > Clear completed downloads shows progress and the space recovered.
- Matched launcher image frames, brief accessible transitions, clearer text and updated EN/RU/LV play guides.
- Pack 1.5.4 removes inventory profile overlays and matching-item highlights, adds quiet durability tooltips, restores shader-aware DH overdraw and offers Page Down for body view.

[Setup, reset and testing guide](RESET-AND-TEST.md) | [Signing options](SIGNING-OPTIONS.md) | [Modpack admin guide](https://github.com/pjampjam/HLMC-Reborn/blob/main/ADMIN-GUIDE.md)

## Files and updates

The app installs into `%LOCALAPPDATA%\HolyLoisReborn`. Game files and preferences live under its data folder. If an older installation already occupies that folder, migration preserves it as a sibling HolyLoisReborn-backup folder before moving the working preview installation. The backup is retained for recovery. Close Minecraft and its launcher during this one-time move.

Fast start keeps Java and Minecraft in `data\game` (files identical to another launcher's are hard-linked, so they take no extra space), the last start record and game output in `data\logs`, and player names in `data\players.json`. The pack's game folder is the same one as before: `data\instances\Holy Lois Reborn`, or the SKlauncher folder for SKlauncher players.

App updates use this repository's signed app-stable channel. Modpack updates use the existing separate signed HLMC-Reborn pack channel. Direct release-file checks do not use the GitHub REST API quota; hosting and download failures can still occur. The working installed app remains available when the network check fails.

Updates replace only pack-managed files. Worlds, personal voice-device choices and extra client mods or shaders are preserved. Shared defaults are merged once per pack version while unrelated preferences remain. Settings > Clear completed downloads removes proven duplicate downloads while keeping rollback and game data. Settings > Remove launcher app removes the app and its matching shortcuts while keeping game data.

## Build and verification

Use .NET 10 and run build.ps1. Add -Package for a self-contained Windows x64 executable. The core test suite covers signatures, bounded downloads, safe paths, preservation of user data, app replacement and rollback. Public releases include verification.json and their signed app-release.txt catalog.

assets/app-release-public.pem verifies launcher releases; assets/release-public.pem verifies modpack releases. Their private keys are excluded from source control. Keep the launcher key stable between releases. Sign any future Authenticode build before calculating its release hash and catalog; never replace an already published version with changed bytes.

This edition is public for friends. Folder migration, profile naming, shortcut preservation, signed app updates and rollback are tested. Publishing a clean scan result does not establish global antivirus clearance. The repository name remains Packaging Lab to preserve existing trusted update URLs; it is not the installed application name.
