# Preview 0.6.0 - reset and test

Your application files are in %USERPROFILE%\AppData\Local\HolyLoisRebornLab. Your preview game and pack files are under data\instances\Holy Lois Reborn. The production application folder HolyLoisReborn is separate.

## Repeat the welcome setup

Open Settings > Run setup again. The app restarts and offers launcher and shortcut choices. Your language, launcher preferences and game files stay available. You do not need to delete a folder.

Deleted shortcuts still stay deleted unless you explicitly create them in Settings or choose them during repeated setup.

## Check the fixes

1. Open the revised preview. The title bar should contain only the window controls.
2. When Minecraft Launcher is found, the main screen should show that status and hide Find launcher and Download launcher. Settings > Choose a different launcher file remains available.
3. Click Repair / check files. The window should remain responsive and show a launcher-profile preparation phase after pack-file verification. You can cancel downloads; an in-progress file commit completes or rolls back safely.
4. Open Minecraft Launcher and choose Holy Lois: Reborn (Preview). Your original Holy Lois profile remains unchanged while testing this separate version.
5. Switch to SKlauncher. The existing import/link steps remain necessary. Link only a Holy Lois instance using Link SK game folder. Update and test that instance separately.
6. Check English, Russian and Latvian using the main-screen language selector. Check keyboard focus, resizing and scrolling.

For a clean first-run test, close the preview and use File Explorer to rename HolyLoisRebornLab to HolyLoisRebornLab-test-backup. Then open the newly downloaded EXE. Renaming keeps your previous data recoverable. Do not delete the working production HolyLoisReborn folder, .minecraft, .sklauncher or your CurseForge profile.

## Remove the app

Settings > Remove launcher app opens a confirmation. It removes the installed preview EXE and shortcuts that point to it. Worlds, pack files, personal settings, download caches and other Minecraft launchers are retained. The downloaded EXE in Downloads is separate and is not removed. Temporary maintenance/update copies can remain as retained files; this preview does not remove unrelated contents of a folder.

If you want to remove all preview data afterward, first copy any worlds, personal screenshots or extra mods you want to keep. Then delete only %USERPROFILE%\AppData\Local\HolyLoisRebornLab using File Explorer. This is optional and is not done by app-only removal. A preview installation entry can remain in Minecraft Launcher; remove only the entry named Holy Lois: Reborn (Preview), never your working production entry.

## Update policy

Updates replace or remove only files recorded as pack-managed. Extra client mods and shader ZIPs stay in place. Worlds, voice-device choices and other personal files are not distributed or deleted. Shared changes are merged once per pack version; unrelated preferences remain. A conflicting unowned file is reported for review rather than silently overwritten.

## Windows publisher warning

The file's Company metadata now says pjampjam. Windows' verified publisher comes from an Authenticode certificate, not this metadata. This preview is still unsigned. SmartScreen can show Unknown publisher even when Defender did not detect malware and a multi-engine scan reports zero detections. Do not describe the preview as globally cleared.

No app-stable preview update channel is public yet. A short notice that preview app updates are unavailable is expected. Your signed modpack update channel is independent.
