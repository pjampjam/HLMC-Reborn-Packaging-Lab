# Holy Lois: Reborn launcher and add-ons

Launcher 1.4.0 installs and updates the signed Holy Lois pack for Minecraft 26.3. Pick how you play (I own Minecraft, or Play with a name), install, then Play. One button follows the state (Install, Update, Repair, Play), the account card shows your skin head, and the main screen keeps the most used keys and commands in view. Built-in pack: 1.9.0.

[Download the launcher](https://github.com/pjampjam/HLMC-Reborn-Packaging-Lab/releases/latest/download/HolyLoisReborn.exe). [Player guide](https://github.com/pjampjam/HLMC-Reborn/blob/main/PLAYER-GUIDE.md). [Admin guide](https://github.com/pjampjam/HLMC-Reborn/blob/main/ADMIN-GUIDE.md).

Quick start accepts valid names without a Mojang ownership lookup. Existing Holy Lois accounts require their server password. Admin-granted rename/recovery forms preserve the verified UUID; they do not grant access to someone else's account. Old names remain protected. /logout and account changes wait for combat to end.

R over an item shows its recipe; R over an empty slot sorts without opening a recipe as the slot fills. Matching stones can merge after chest acquisition; rolled variants keep their own components.

## Build

Use the .NET10 SDK on Windows and run `./build.ps1 -Version 1.4.0`. Add `-Package` for the release EXE. Sources under `addons/` target the pinned Fabric26.3 APIs; their server build scripts compile against the installed libraries and run the relevant checks. Do not ship private preview/probe mods.

## Release integrity

The app/catalog and pack manifests use independent RSA-PSS verification keys. Private signing keys, worlds, account data, tokens and personal settings never belong in this repository. Keep signed `assets/` bytes exact; run `scripts/check_public_data.py` before publishing. Updates preserve personal settings/keybinds and use the reviewed pack defaults.

The Windows app has no Authenticode certificate; SmartScreen may show Unknown publisher. A local antivirus result is not a guarantee for every computer.

## Later work

Slipped items (/played, party HUD default, login before the world and more) are tracked for the next batch. This source snapshot matches the tested launcher 1.4.0 with pack 1.9.0.
