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
 * RPG-style zone titles at the top of the screen: the biome (or dimension) on the first line, and "Wilderness" or the
 * land claim (Open Parties and Claims) on the second. Claim borders show at once; a new biome shows after two seconds
 * inside it, and the same biome at most once every 90 seconds, so walking along a border does not flicker.
 */
public final class ZoneTitles implements ClientModInitializer {
    static final int FADE_IN = 10, STAY = 50, FADE_OUT = 20, BIOME_SETTLE = 40;
    static final long BIOME_REPEAT_MILLIS = 90_000;

    record Claim(String key, Component name, int color) {}

    private Identifier lastDimension;
    private String lastBiome, candidateBiome, lastClaim;
    private int candidateTicks, age = Integer.MAX_VALUE;
    private Component top, bottom;
    private int bottomColor;
    private final Map<String, Long> biomeShownAt = new HashMap<>();

    private boolean broken;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> guarded(() -> tick(mc)));
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
    }

    private void tick(Minecraft mc) {
        if (age < Integer.MAX_VALUE) age++;
        if (mc.player == null || mc.level == null) { lastDimension = null; lastBiome = lastClaim = candidateBiome = null; return; }
        Identifier dimension = mc.level.dimension().identifier();
        var pos = mc.player.blockPosition();
        String biome = mc.level.getBiome(pos).unwrapKey().map(key -> name("biome", key.identifier())).orElse("");
        Claim claim = FabricLoader.getInstance().isModLoaded("openpartiesandclaims") ? Opac.claimAt(dimension, pos.getX() >> 4, pos.getZ() >> 4) : null;
        if (claim == null) claim = new Claim(lastClaim == null ? "wild" : lastClaim, Component.translatable("holylois.zone.wilderness"), 0xBFBFBF);
        long now = System.currentTimeMillis();
        if (!dimension.equals(lastDimension)) {
            lastDimension = dimension; lastBiome = biome; lastClaim = claim.key(); candidateBiome = null;
            show(name("holylois.zone.dimension", dimension), claim, now);
            biomeShownAt.put(biome, now);
            return;
        }
        if (!claim.key().equals(lastClaim)) {
            lastClaim = claim.key(); lastBiome = biome; candidateBiome = null;
            show(biome, claim, now);
            biomeShownAt.put(biome, now);
            return;
        }
        if (biome.equals(lastBiome)) { candidateBiome = null; return; }
        if (!biome.equals(candidateBiome)) { candidateBiome = biome; candidateTicks = 0; return; }
        if (++candidateTicks < BIOME_SETTLE) return;
        lastBiome = biome; candidateBiome = null;
        if (now - biomeShownAt.getOrDefault(biome, 0L) < BIOME_REPEAT_MILLIS) return;
        biomeShownAt.put(biome, now);
        show(biome, claim, now);
    }

    /** 0..1 opacity for the current age. */
    static float alpha(int age, float partial) {
        float t = age + partial;
        if (t < FADE_IN) return t / FADE_IN;
        if (t < FADE_IN + STAY) return 1;
        return Math.max(0, 1 - (t - FADE_IN - STAY) / FADE_OUT);
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (top == null || age > FADE_IN + STAY + FADE_OUT) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int a = Math.round(alpha(age, delta.getGameTimeDeltaPartialTick(false)) * 255);
        if (a < 8) return;
        // Sit below any boss bars (19 px each, starting at y 12).
        int y = 18 + 19 * ((holylois.boombox.mixins.BossOverlayAccessor) mc.gui.hud.getBossOverlay()).holyLois$events().size();
        int x = graphics.guiWidth() / 2;
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(1.75f, 1.75f);
        graphics.centeredText(mc.font, Component.literal("✦ " + top.getString() + " ✦"), 0, 0, (a << 24) | 0xFFD966);
        pose.popMatrix();
        graphics.centeredText(mc.font, bottom, x, y + 18, (a << 24) | (bottomColor & 0xFFFFFF));
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
