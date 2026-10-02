# Holy Lois: Reborn - Packaging Lab

An experimental one-file Windows launcher for the Holy Lois modpack. This repository is separate from the production HLMC-Reborn repository. The original installer and its Microsoft review remain unchanged.

## What changed

- One self-contained HolyLoisReborn.exe, approximately 67 MB. No separate .NET installation is needed.
- First-run setup chooses Minecraft Launcher or SKlauncher and optional Desktop and Start menu shortcuts.
- The embedded HolyLoisSetup.exe and its startup handoff have been removed.
- App updates use a separate pinned signing key and this repository's dedicated app-stable feed. Normal checks use direct release files, not the GitHub REST API.
- Downloads are bounded and verified against signed metadata, SHA-256 and exact size before execution.
- Replacement uses a temporary invocation of the same verified app. Startup acknowledgement commits the update; failed startup restores and restarts the previous app.
- The preview uses LocalAppData/HolyLoisRebornLab and differently named shortcuts. It refuses to use the production HolyLoisReborn installation folder.
- Modpack signatures, downloads and game settings remain separate from app updates.

## Current status

Prototype only. Public binary distribution is not enabled. Any draft release is for owner review and is not a clearance claim. A local Defender scan does not establish that browser downloads or other PCs will be clear. Do not disable protection, add exclusions or allow detected threats to complete these tests.

The stable app update channel has not been promoted. Until it exists, the prototype keeps its working installed version and reports that its launcher update check did not complete. It can still check the existing signed modpack channel independently.

## Build

Install the .NET 10 SDK. Run build.ps1 for compilation and tests; add -Package for a self-contained Windows x64 test artifact. The build script uses the locally bundled SDK when present. A clean standalone checkout can use dotnet from PATH.

The app public update key is in assets/app-release-public.pem. The private signing key is excluded from source control. Do not generate a different key for each release. The separate assets/release-public.pem verifies the existing production modpack and is a different trust boundary.

## Test and promotion

Read TEST-GUIDE.md. Before promoting this design, test first-run setup, user-controlled shortcuts, official and SKlauncher profiles, normal browser downloads with Defender enabled, and real signed app updates. Continue the original Microsoft investigation if browser detection reproduces.

Once reviewed, the implementation can be migrated to the production project with an explicit installation migration, trusted signing if available, and a tested release channel. Do not replace the production feed merely because one local scan passed.

## Remove the preview

Close the preview. Its app files are under LocalAppData/HolyLoisRebornLab, with optional shortcuts named Holy Lois Reborn Preview. Remove those preview shortcuts and application files using normal File Explorer deletion. Keep its data folder if you want to retain the preview's downloaded pack and preferences. Production Minecraft worlds and the original HolyLoisReborn folder are separate. A dedicated uninstall interface is still pending.
