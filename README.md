# Holy Lois: Reborn

A Windows launcher and updater for the Holy Lois modpack. Choose Minecraft Launcher or SKlauncher, install the pack, then open your chosen launcher to play.

## Download

[Download HolyLoisReborn.exe 1.2.0](https://github.com/pjampjam/HLMC-Reborn-Packaging-Lab/releases/download/v1.2.0/HolyLoisReborn.exe) - about 67 MB. Windows x64, with its .NET runtime included.

This is the full release. Existing preview installations move to the standard HolyLoisReborn folder, and owned launcher profiles and shortcuts lose their Preview names. The original small installers in HLMC-Reborn remain withdrawn. Do not download those older installers.

1. Open the downloaded EXE. Choose your Minecraft launcher and optional shortcuts.
2. Close Minecraft, then click Install Holy Lois.
3. Click Play beneath the logo. In Minecraft Launcher, select Holy Lois: Reborn. In SKlauncher, open Library and select Holy Lois: Reborn. Click Install there once to prepare Minecraft and Java, then Play.

Already using Holy Lois? Close and reopen your installed app or its shortcut. Its signed app-stable channel updates it to 1.2.0 before showing the main window. You do not need another installer. An old downloaded copy hands off to the newer installed version after its verified update.

The app is unsigned by a Windows publisher certificate. SmartScreen may show Unknown publisher. Local scans and previous user download tests do not guarantee every antivirus result. If Defender detects a threat, stop and report the detection rather than adding an exclusion.

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

App updates use this repository's signed app-stable channel. Modpack updates use the existing separate signed HLMC-Reborn pack channel. Direct release-file checks do not use the GitHub REST API quota; hosting and download failures can still occur. The working installed app remains available when the network check fails.

Updates replace only pack-managed files. Worlds, personal voice-device choices and extra client mods or shaders are preserved. Shared defaults are merged once per pack version while unrelated preferences remain. Settings > Clear completed downloads removes proven duplicate downloads while keeping rollback and game data. Settings > Remove launcher app removes the app and its matching shortcuts while keeping game data.

## Build and verification

Use .NET 10 and run build.ps1. Add -Package for a self-contained Windows x64 executable. The core test suite covers signatures, bounded downloads, safe paths, preservation of user data, app replacement and rollback. Public releases include verification.json and their signed app-release.txt catalog.

assets/app-release-public.pem verifies launcher releases; assets/release-public.pem verifies modpack releases. Their private keys are excluded from source control. Keep the launcher key stable between releases. Sign any future Authenticode build before calculating its release hash and catalog; never replace an already published version with changed bytes.

This edition is public for friends. Folder migration, profile naming, shortcut preservation, signed app updates and rollback are tested. Publishing a clean scan result does not establish global antivirus clearance. The repository name remains Packaging Lab to preserve existing trusted update URLs; it is not the installed application name.
