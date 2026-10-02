# Holy Lois Login

Client-only Minecraft 26.3 / Fabric Loader 0.19.5 / Java 25 source. Entry point: `holylois.auth.HolyLoisAuthClient`. MIT license.

Compile the production Java files against the exact Minecraft, Fabric Loader and Fabric API classpath, using the compiler output directory for package paths. Exclude `AuthPolicyTest.class` from the release JAR. Put the supplied `fabric.mod.json` and `holylois-auth-ui.mixins.json` at the JAR root. The mixin source belongs to `holylois.auth.mixins`.

The paired server onboarding addon owns authentication state; EasyAuth owns credentials. Passwords use its existing command path and are never saved by this helper. See TEST-GUIDE.md and TECHNICAL-GUIDE.md for behavior and validation.
