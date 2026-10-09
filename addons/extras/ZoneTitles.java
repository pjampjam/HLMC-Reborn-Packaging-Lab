package holylois.boombox;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.*;

/**
 * RPG-style zone titles in the upper third of the screen (below Jade's hover box and any boss bars): the structure,
 * biome or dimension on the first line, and "Wilderness" or the land claim (Open Parties and Claims) on the second.
 * Claim borders show at once; a new biome shows after two seconds inside it, and the same biome at most once every
 * 90 seconds, so walking along a border does not flicker. Structures come from the server (StructureZone), each at
 * most once every 5 minutes.
 */
public final class ZoneTitles implements ClientModInitializer {
    static final int FADE_IN = 10, STAY = 50, FADE_OUT = 20, BIOME_SETTLE = 40;
    static final long BIOME_REPEAT_MILLIS = 90_000, STRUCTURE_REPEAT_MILLIS = 300_000;

    record Claim(String key, Component name, int color) {}

    private Identifier lastDimension;
    private String lastBiome, candidateBiome, lastClaim;
    private int candidateTicks, age = Integer.MAX_VALUE;
    private Component top, bottom;
    private int bottomColor;
    private final Map<String, Long> biomeShownAt = new HashMap<>();
    private final Map<String, Long> structureShownAt = new HashMap<>();
    private Claim currentClaim;
    private int stay = STAY;
    /** Title colour: the kind of zone (see colorFor). */
    private int titleColor = Ui.GOLD;
    /** Inside a structure its title wins over biome changes; leaving it shows the biome outside once. */
    private boolean inStructure, leftStructure;

    private boolean broken;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> guarded(() -> tick(mc)));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(StructureZone.TYPE,
            (payload, context) -> guarded(() -> structure(payload)));
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Identifier.fromNamespaceAndPath("holylois", "zone_title"),
            (graphics, delta) -> guarded(() -> render(graphics, delta)));
    }

    /** A bug here must never crash the game: log once and switch the titles off for this session. */
    private void guarded(Runnable action) {
        if (broken) return;
        try { action.run(); }
        catch (RuntimeException | LinkageError error) {
            broken = true;
            org.slf4j.LoggerFactory.getLogger("HolyLois").error("Zone titles turned off after an error", error);
        }
    }

    /** "biome.minecraft.snowy_slopes" from the game's language, else a tidied id ("Snowy Slopes"). */
    static String name(String prefix, Identifier id) {
        String key = prefix + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        if (Language.getInstance().has(key)) return Language.getInstance().getOrDefault(key, key);
        StringBuilder text = new StringBuilder();
        for (String word : id.getPath().substring(id.getPath().lastIndexOf('/') + 1).split("_"))
            if (!word.isEmpty()) text.append(text.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        return text.toString();
    }

    private void show(String first, Claim claim, long now) {
        top = Component.literal(first);
        bottom = claim.name();
        bottomColor = claim.color();
        age = 0;
        stay = STAY;
        titleColor = BIOME;
    }

    /** Entered (or left, empty id) a structure; the same one shows again after 5 minutes at the earliest. */
    private void structure(StructureZone zone) {
        if (zone.structure().isEmpty()) { if (inStructure) { inStructure = false; leftStructure = true; } return; }
        inStructure = true;
        Identifier id = Identifier.tryParse(zone.structure());
        if (id == null) return;
        String key = zone.structure() + "@" + zone.start();
        long now = System.currentTimeMillis();
        if (now - structureShownAt.getOrDefault(key, 0L) < STRUCTURE_REPEAT_MILLIS) return;
        structureShownAt.put(key, now);
        var claim = currentClaim != null ? currentClaim : new Claim("wild", Component.translatable("holylois.zone.wilderness"), 0xBFBFBF);
        // Our names first, then the structure mod's own ("structure.dnt.illager_camp"), then a tidied id.
        String ownKey = "holylois.structure." + id.getNamespace() + "." + id.getPath(), modKey = "structure." + id.getNamespace() + "." + id.getPath();
        String title = Language.getInstance().has(ownKey) || !Language.getInstance().has(modKey) ? name("holylois.structure", id) : Language.getInstance().getOrDefault(modKey, modKey);
        show(title, claim, now);
        stay = STAY + 30;
        titleColor = structureColor(id);
    }

    private void tick(Minecraft mc) {
        if (age < Integer.MAX_VALUE) age++;
        if (mc.player == null || mc.level == null) { lastDimension = null; lastBiome = lastClaim = candidateBiome = null; inStructure = leftStructure = false; return; }
        Identifier dimension = mc.level.dimension().identifier();
        var pos = mc.player.blockPosition();
        String biome = mc.level.getBiome(pos).unwrapKey().map(key -> name("biome", key.identifier())).orElse("");
        Claim claim = FabricLoader.getInstance().isModLoaded("openpartiesandclaims") ? Opac.claimAt(dimension, pos.getX() >> 4, pos.getZ() >> 4) : null;
        if (claim == null) claim = new Claim(lastClaim == null ? "wild" : lastClaim, Component.translatable("holylois.zone.wilderness"), 0xBFBFBF);
        currentClaim = claim;
        long now = System.currentTimeMillis();
        if (!dimension.equals(lastDimension)) {
            lastDimension = dimension; lastBiome = biome; lastClaim = claim.key(); candidateBiome = null;
            show(name("holylois.zone.dimension", dimension), claim, now);
            titleColor = dimensionColor(dimension);
            biomeShownAt.put(biome, now);
            return;
        }
        if (!claim.key().equals(lastClaim)) {
            lastClaim = claim.key(); lastBiome = biome; candidateBiome = null;
            show(biome, claim, now);
            biomeShownAt.put(biome, now);
            return;
        }
        if (leftStructure) {
            leftStructure = false; lastBiome = biome; candidateBiome = null;
            biomeShownAt.put(biome, now);
            show(biome, claim, now);
            return;
        }
        if (inStructure) { lastBiome = biome; candidateBiome = null; return; }
        if (biome.equals(lastBiome)) { candidateBiome = null; return; }
        if (!biome.equals(candidateBiome)) { candidateBiome = biome; candidateTicks = 0; return; }
        if (++candidateTicks < BIOME_SETTLE) return;
        lastBiome = biome; candidateBiome = null;
        if (now - biomeShownAt.getOrDefault(biome, 0L) < BIOME_REPEAT_MILLIS) return;
        biomeShownAt.put(biome, now);
        show(biome, claim, now);
    }

    /** 0..1 opacity for the current age. */
    static float alpha(int age, float partial) { return alpha(age, partial, STAY); }

    static float alpha(int age, float partial, int stay) {
        float t = age + partial;
        if (t < FADE_IN) return t / FADE_IN;
        if (t < FADE_IN + stay) return 1;
        return Math.max(0, 1 - (t - FADE_IN - stay) / FADE_OUT);
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (top == null || age > FADE_IN + stay + FADE_OUT) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float fade = alpha(age, delta.getGameTimeDeltaPartialTick(false), stay);
        if (fade < 0.03f) return;
        // Upper third, under the boss bars (19 px each from y 12) and clear of Jade's hover box at the top centre.
        int bars = ((holylois.boombox.mixins.BossOverlayAccessor) mc.gui.hud.getBossOverlay()).holyLois$events().size();
        int y = Math.max(18 + 19 * bars, Math.round(graphics.guiHeight() * 0.2f));
        int x = graphics.guiWidth() / 2;
        String title = top.getString();
        float scale = 1.6f;
        int titleWidth = Math.round(mc.font.width(title) * scale);
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        outlined(graphics, mc.font, title, -mc.font.width(title) / 2, 0, titleColor, fade);
        pose.popMatrix();
        // A gold rule that fades out to both sides.
        int half = Math.max(24, titleWidth / 2 + 10), ruleY = y + 17;
        for (int step = 0; step < 4; step++) {
            int from = half * step / 4, to = half * (step + 1) / 4;
            int color = Ui.alpha(titleColor, fade * 0.7f * (1 - step / 4f));
            graphics.fill(x - to, ruleY, x - from, ruleY + 1, color);
            graphics.fill(x + from, ruleY, x + to, ruleY + 1, color);
        }
        int sub = bottomColor == 0xBFBFBF ? Ui.MUTED : bottomColor;
        outlined(graphics, mc.font, bottom.getString(), x - mc.font.width(bottom) / 2, y + 22, sub, fade);
    }

    /** Biomes gold; dimensions green, red, lavender; structures by danger: red dungeons, green villages, aqua ruins and the rest. */
    static final int BIOME = Ui.GOLD;
    /** Hostile places by id words (vanilla, YUNG's, Dungeons and Taverns): a watchtower or a ruin is not a dungeon. */
    private static final java.util.List<String> DANGER = java.util.List.of("ancient_city", "trial_chamber", "stronghold", "fortress",
        "bastion", "end_city", "monument", "mansion", "mineshaft", "dungeon", "crypt", "catacomb", "tomb", "illager", "pillager",
        "outpost", "barracks", "hideout", "skeleton", "piglin", "nether_keep", "donjon", "sealing", "witch");

    static int dimensionColor(Identifier dimension) {
        return switch (dimension.getPath()) { case "the_nether" -> 0xFFFF6B4A; case "the_end" -> 0xFFD49EFF; default -> 0xFF7ED3A0; };
    }

    static int structureColor(Identifier id) {
        String path = id.getPath();
        if (path.startsWith("village")) return 0xFF7ED3A0;
        // YUNG's and other dungeon mods: anything that sounds like one.
        if (DANGER.stream().anyMatch(path::contains)) return 0xFFFF7A5C;
        return 0xFF8DD8FF;
    }

    /** Text with a 1 px dark outline all around, readable on snow, sand and sky. */
    static void outlined(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, String text, int x, int y, int color, float fade) {
        int stroke = Ui.alpha(Ui.CANVAS, 0.85f * fade);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
            if (dx != 0 || dy != 0) graphics.text(font, text, x + dx, y + dy, stroke, false);
        graphics.text(font, text, x, y, Ui.alpha(color, fade), false);
    }

    // Separate class so Open Parties and Claims types load only when the mod is present.
    private static final class Opac {
        static Claim claimAt(Identifier dimension, int chunkX, int chunkZ) {
            var manager = xaero.pac.client.api.OpenPACClientAPI.get().getClaimsManager();
            if (manager.isLoading()) return null;
            var claim = manager.get(dimension, chunkX, chunkZ);
            if (claim == null) return new Claim("wild", Component.translatable("holylois.zone.wilderness"), 0xBFBFBF);
            UUID owner = claim.getPlayerId();
            String key = owner + ":" + claim.getSubConfigIndex();
            int color = manager.getColor(claim, dimension);
            if (color == 0) color = 0xFFFFFF;
            if (owner.equals(new UUID(0, 0))) return new Claim(key, Component.translatable("holylois.zone.server"), color);
            if (owner.equals(new UUID(0, 1))) return new Claim(key, Component.translatable("holylois.zone.abandoned"), 0xBFBFBF);
            var info = manager.getPlayerInfo(owner);
            String player = info == null || info.getPlayerUsername() == null ? "?" : info.getPlayerUsername();
            String custom = manager.getCustomName(claim, dimension);
            Component name = custom == null || custom.isBlank() ? Component.translatable("holylois.zone.land", player)
                : Component.translatable("holylois.zone.named", custom, player);
            return new Claim(key, name, color);
        }
    }
}
