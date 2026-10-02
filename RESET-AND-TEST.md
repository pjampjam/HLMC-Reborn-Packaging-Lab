# Holy Lois: Reborn - setup and maintenance

## Install and play

1. Download HolyLoisReborn.exe from the latest public release.
2. Close Minecraft and Minecraft Launcher or SKlauncher.
3. Open Holy Lois. Choose your launcher and the shortcuts you want.
4. Click Install Holy Lois. When the pack is ready, Play beneath the logo becomes green.
5. Play opens your selected launcher. Select Holy Lois: Reborn, then play inside that launcher.

For SKlauncher 4, follow the app's import instructions, close SKlauncher, and use Link SK game folder to select the imported Holy Lois instance. Repair / check files connects future updates to that folder. If the instance was deleted, reinstall, import it again, and link its new folder.

## Existing installations

Close and reopen your installed Holy Lois app to receive the signed launcher update. Version 1.0.0 moves an owned HolyLoisRebornLab installation to `%LOCALAPPDATA%\HolyLoisReborn`. Pack files, settings, worlds and personal extras move together. If an older installation occupies the destination, it is retained in a sibling HolyLoisReborn-backup folder. No old world data is deleted.

Existing owned Desktop and Start menu shortcuts are renamed Holy Lois Reborn and retargeted. A shortcut you deleted stays deleted. The official launcher profile becomes Holy Lois: Reborn, preserving its memory arguments and unrelated profiles. Its previous profile file is backed up locally and can contain account information, so do not share that backup.

The repository name stays Packaging Lab because older apps trust that update address. It does not appear in the installed application's folder or shortcuts.

## Settings and removal

Settings > Run setup again repeats launcher and shortcut choices without deleting game data. Settings can also create shortcuts explicitly, choose a launcher file, or open the app folder.

Settings > Remove launcher app removes its installed EXE and matching shortcuts. Game files, worlds, settings and download caches stay. The copy in Downloads is separate. Other launchers and modpacks are untouched.

For a fresh setup test, close the app and rename only `%LOCALAPPDATA%\HolyLoisReborn` to a backup name, then open the downloaded EXE. Keep any worlds and extra files you want. Do not delete .minecraft, .sklauncher or a CurseForge profile.

## Updates

The app checks its signed launcher update channel before opening the main window. The modpack has a separate signed channel. A failed network check keeps the working app available. Updates replace only pack-managed files. Shared defaults merge once per pack version; unrelated preferences, worlds, voice-device choices and extra client mods or shaders remain.

## Short test

Check both Minecraft Launcher and SKlauncher, their clean profile names, and green Play when ready. Switch English, Russian and Latvian. Open and cancel Settings dialogs; mouse clicks must not leave a keyboard focus outline, while Tab still displays focus. Delete an unwanted shortcut, reopen the app, and confirm it stays absent.

The EXE is unsigned by a Windows publisher certificate. SmartScreen may show Unknown publisher. Report a Defender detection instead of adding an exclusion. A local scan cannot guarantee every PC's antivirus result.
