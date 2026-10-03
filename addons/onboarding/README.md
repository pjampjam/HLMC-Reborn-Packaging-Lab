# Holy Lois Onboarding

Server-only Minecraft 26.3 / Fabric Loader 0.19.5 / Java 25 source. Entry point: `holylois.HolyLois`. Requires Fabric API and EasyAuth 3.4.4. MIT license.

Compile the production Java files against those exact installed APIs, using the compiler output directory for package paths. Exclude `OnboardingTest.class` from the release JAR and put the supplied `fabric.mod.json` at its root. Stop the managed service and take a complete backup before replacing a server JAR.

Version 1.4.0 adds a daily quote. It reads `config/holylois-quotes.txt` (copied from the bundled `holylois-quotes.txt` on first start; edits apply without a restart). When Placeholder API is present, `%holylois:quote%` and `%holylois:quote_author%` are available to Styled Player List. It also shows Latvian name days (`config/holylois-namedays-lv.txt`, Europe/Riga date, from the Latvian State Language Centre's traditional list via the MIT-licensed lv-namedays project) as `%holylois:nameday%` and in the welcome message, with a personal greeting when a username starts with one of the day's names. The bot wall reads `config/holylois-botwall.json` (written by the server's `botwall.py` collector, no IPs) for `%holylois:bots_today%`, `%holylois:bots_latest%` and the operator command `/botwall`. Package `holylois-quotes.txt` and `holylois-namedays-lv.txt` at the JAR root.

The addon reports EasyAuth state to the paired client helper and handles protected first placement and bedless death respawns. It does not replace EasyAuth credential checks. See TEST-GUIDE.md and TECHNICAL-GUIDE.md.
