# Owner test guide

This is a separate packaging preview. Do not send it to friends yet.

1. Download the draft preview using your normal browser, with Defender and cloud protection enabled. If it is removed or quarantined, leave it blocked and report the detection and time. Do not add an exclusion or Allow rule.
2. Open the single EXE if the download remains available. Choose your launcher and shortcut preferences. Confirm it installs under LocalAppData/HolyLoisRebornLab.
3. Close and reopen it. Confirm the setup does not repeat. Delete a preview shortcut and confirm it stays deleted after another launch.
4. Check both official Minecraft Launcher and SKlauncher selections. Use a separate preview instance rather than replacing your current working Minecraft profile.
5. A launcher update-channel error is expected until the experimental app-stable feed is promoted. Your installed version should remain usable. A modpack update check is independent of that app update check.
6. After enabling a reviewed signed app channel, start an older preview copy. It should open the newest verified installed app rather than reinstalling the old one. Check that user settings remain intact.

Automated validation includes signature tampering, wrong repository URLs, downgrade and same-version changes, staged-file corruption, replacement failure, interrupted transactions, and startup acknowledgement. Real-process tests exercise version 0.5.0 to 0.5.1 and a simulated new-app startup failure restoring and restarting 0.5.0. These do not replace testing browser downloads on the affected PC.

Do not promote the preview based solely on a clean custom scan. No antivirus detection fix is claimed.
