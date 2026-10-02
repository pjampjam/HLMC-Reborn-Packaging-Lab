# Holy Lois Glint Preload

Client-only source for the targeted Iris 1.11.7 / Minecraft 26.3 enchanted-texture upload workaround. Preloads the item and armor glint textures at client tick start, outside an active render pass. The owner reported no crashes during the post-fix shader test. It is not an official Iris patch.

Compile with Java 25 and the exact Minecraft 26.3, Fabric Loader 0.19.5 and Fabric API classpath. Entry point: `holylois.HolyLoisGlint`. The compiled release JAR includes this source. MIT license.
