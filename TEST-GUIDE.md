# Public test guide

Version 1.0.0 is the full release, with tested migration to the HolyLoisReborn application folder.

1. Download public version 1.0.0 using your normal browser, with Defender and cloud protection enabled. If it is removed or quarantined, leave it blocked and report the detection and time. Do not add an exclusion or Allow rule.
2. Open the single EXE if the download remains available. Choose your launcher and shortcut preferences. Confirm it installs under LocalAppData/HolyLoisReborn.
3. Close and reopen it. Confirm the setup does not repeat. Delete a Holy Lois shortcut and confirm it stays deleted after another launch.
4. Check both official Minecraft Launcher and SKlauncher selections. Confirm the owned profile is named Holy Lois: Reborn and unrelated profiles remain.
5. Close and reopen an installed 0.8.0 app to update from the signed app-stable feed before the main screen opens. A modpack update check is independent of that app update check. A network failure keeps your installed app usable.
6. After the update, open an older downloaded preview copy. It should open the newest verified installed app rather than reinstalling the old one. Check that user settings remain intact.

Automated validation includes signature tampering, wrong repository URLs, downgrade and same-version changes, staged-file corruption, replacement failure, interrupted transactions, and startup acknowledgement. Real-process tests exercise version 0.8.0 to 1.0.0 and a simulated new-app startup failure restoring and restarting 0.8.0. These do not replace testing browser downloads on your PC.

A local custom scan passed for the published artifact. Future browser downloads and other PCs can still differ. The app has no Windows publisher certificate.
