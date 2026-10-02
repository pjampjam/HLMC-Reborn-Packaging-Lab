# Packaging preview 0.5.0

This is a separate experiment, not a production modpack release. The Minecraft server, existing launcher installation, modpack channel and Microsoft submissions have not been changed.

## One app instead of a separate installer

The downloaded EXE contains the .NET runtime. It offers launcher and shortcut choices on first run, copies itself to LocalAppData/HolyLoisRebornLab, and opens that installed copy thereafter. It does not embed or start HolyLoisSetup.exe. The production admin publishing interface is disabled in this preview.

## Verified updates

The app checks a separate signed catalog before showing the launcher. Each release specifies its version, repository URL, exact size and SHA-256. A pinned public key verifies the catalog. Older releases and changes to the same version are rejected. The corresponding private key is not in GitHub.

A temporary copy of the same verified EXE waits for the running app to exit, replaces it, and starts the new version. The new app must acknowledge startup. Otherwise, the old executable and its release receipt are restored, and the old app starts again. The previous executable remains available for rollback. Failed startup is not retried continuously.

No shell script, Defender exception, protection change or executable obfuscation is part of this design. The app is unsigned by an Authenticode publisher certificate. The catalog signature protects the update feed; it is not Windows code signing and does not establish antivirus clearance.

## Verification on October 2, 2026

- All 23 automated test groups passed.
- The packaged 0.5.0 app installed itself in a fresh isolated folder. Shortcut choices stayed off after repeating setup.
- A real 0.5.0 to 0.5.1 process update succeeded, committed its signed receipt and preserved a player-settings sentinel.
- A simulated 0.5.1 startup failure restored the real 0.5.0 executable and receipt, restarted it, and preserved the sentinel.
- Both final packaged builds passed local Defender custom scans with real-time protection enabled and definitions 1.459.509.0. This does not verify browser download detection or other PCs.
- The startup-check window was rendered and inspected with the dark launcher colors, centered logo and title, and a thin yellow progress line.
- GitHub Actions could not start the verification job because GitHub reports an account billing lock. No cloud source tests ran; the local 23-group result is independent of that restriction.
- Browser controls did not return a completed download. A manual normal-browser download remains required before making any claim about download-time detection.

## Release status

Version 0.8.0 is a public friend-test release with its signed app-stable channel. A failed channel check keeps the installed app usable. The owner accepted the preceding browser download, interactive setup and both launcher integrations. Automated update and rollback tests supplement those checks. Migration into the original production application folder is still a separate future change.

Normal checks use direct GitHub release URLs, avoiding the REST API's anonymous hourly request quota. GitHub hosting, network failures and other service limits still apply. Offline startup keeps the working installed app.

## Build a signed catalog

After building with build.ps1 -Package, run the publisher's catalog command:

```powershell
dotnet run --project src/HolyLois.Publisher -c Release --no-build -- catalog private/app-release-private.pem publish/preview/HolyLoisReborn.exe 0.5.0 https://github.com/pjampjam/HLMC-Reborn-Packaging-Lab/releases/download/v0.5.0/HolyLoisReborn.exe publish/preview/app-release.txt
```

This writes app-release.txt and app-release.txt.sig locally. It does not publish anything. Use the dedicated app private key, keep it private, and retain it for later releases. Do not substitute the production modpack key.

## Full release 1.0.0

The application name is now Holy Lois: Reborn throughout visible setup, shortcuts and Minecraft profiles. The default application root is HolyLoisReborn. An owned older Lab installation moves with its data; an existing older destination is backed up rather than deleted. Native process tests cover the move, profile retargeting and preserving deleted shortcuts. The trusted repository and signing key remain unchanged for compatibility.
