# Holy Lois: Reborn

A Windows launcher and updater for the Holy Lois modpack. Choose Minecraft Launcher or SKlauncher, install the pack, then open your chosen launcher to play.

## Download

[Download HolyLoisReborn.exe 0.8.0](https://github.com/pjampjam/HLMC-Reborn-Packaging-Lab/releases/download/v0.8.0/HolyLoisReborn.exe) - about 67 MB. Windows x64, with its .NET runtime included.

This is the public test edition. It keeps the tested Packaging Lab installation folder and separate Minecraft profile. The original small installers in HLMC-Reborn remain withdrawn. Do not download those older installers.

1. Open the downloaded EXE. Choose your Minecraft launcher and optional shortcuts.
2. Close Minecraft, then click Install Holy Lois.
3. Click Play beneath the logo. In Minecraft Launcher, select Holy Lois: Reborn (Preview). For SKlauncher, follow the app's import step and link the imported Holy Lois game folder.

Already using the preview? Close and reopen your installed app or its shortcut. Its signed app-stable channel updates it to 0.8.0 before showing the main window. You do not need another installer. An old downloaded copy hands off to the newer installed version after its verified update.

The app is unsigned by a Windows publisher certificate. SmartScreen may show Unknown publisher. Local scans and previous user download tests do not guarantee every antivirus result. If Defender detects a threat, stop and report the detection rather than adding an exclusion.

## Version 0.8.0

- A larger Play button beneath the logo names your chosen launcher. It becomes green only when that launcher is found and the pack is ready.
- Mouse interaction clears keyboard focus outlines, including after closing dialogs. Tab navigation retains visible focus.
- Consistent rounded controls, stronger action colors and no logo slogan.
- Signed app updates, byte-based download progress and safe rollback remain in place.
- Optional shortcuts stay deleted when you delete them. Settings can explicitly create them again.

[Setup, reset and testing guide](RESET-AND-TEST.md) | [Signing options](SIGNING-OPTIONS.md) | [Modpack admin guide](https://github.com/pjampjam/HLMC-Reborn/blob/main/ADMIN-GUIDE.md)

## Files and updates

The app installs into `%LOCALAPPDATA%\HolyLoisRebornLab`. Game files and preferences live under its data folder. The original `%LOCALAPPDATA%\HolyLoisReborn` installation remains separate.

App updates use this repository's signed app-stable channel. Modpack updates use the existing separate signed HLMC-Reborn pack channel. Direct release-file checks do not use the GitHub REST API quota; hosting and download failures can still occur. The working installed app remains available when the network check fails.

Updates replace only pack-managed files. Worlds, personal voice-device choices and extra client mods or shaders are preserved. Shared defaults are merged once per pack version while unrelated preferences remain. Settings > Remove launcher app removes the app and its matching shortcuts while keeping game data.

## Build and verification

Use .NET 10 and run build.ps1. Add -Package for a self-contained Windows x64 executable. The core test suite covers signatures, bounded downloads, safe paths, preservation of user data, app replacement and rollback. Public releases include verification.json and their signed app-release.txt catalog.

assets/app-release-public.pem verifies launcher releases; assets/release-public.pem verifies modpack releases. Their private keys are excluded from source control. Keep the launcher key stable between releases. Sign any future Authenticode build before calculating its release hash and catalog; never replace an already published version with changed bytes.

This edition is public for friend testing. Promotion into the original application folder requires a separately tested migration. Publishing a clean scan result does not establish global antivirus clearance.
