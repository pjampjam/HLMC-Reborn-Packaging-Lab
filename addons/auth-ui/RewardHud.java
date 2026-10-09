package holylois.auth;

import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Queue the daily receipt until the player is back in the world; retain the ordinary chat receipt. */
final class RewardHud {
    private static final Deque<RewardNotice> queue = new ArrayDeque<>();
    private static RewardNotice current;
    private static int age;
    private static final int DURATION = 160;
    static void register() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(RewardNotice.TYPE, RewardNotice.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(RewardNotice.TYPE, (v, ctx) -> { if (queue.size() < 4) queue.addLast(v); });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> { queue.clear(); current = null; });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.gui.screen() != null || HolyLoisAuthClient.muteWorldAudio()) return;
            if (current == null) { current = queue.pollFirst(); age = 0; }
            if (current != null && ++age > DURATION) current = null;
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Identifier.fromNamespaceAndPath("holylois", "reward_notice"), (g, delta) -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.gui.screen() != null || mc.gui.hud.isHidden() || HolyLoisAuthClient.muteWorldAudio()) return;
            if (current != null) draw(g, mc, age + delta.getGameTimeDeltaPartialTick(false));
            var voice = VoiceRecovery.notice();
            if (voice != null) g.text(mc.font, voice, Math.max(6, g.guiWidth() - mc.font.width(voice) - 6), g.guiHeight() - 15, AuthUi.FOOD);
        });
    }

    /**
     * Centred above the hotbar, no window (owner round 4): a soft dark band that fades out at its edges, the week as a row of
     * stars with a bigger day-7 slot showing the Holy Lootbox (the goal stays in sight all week), today's reward, then the
     * streak. Fades and slides in, today's star pops, the reward pops after it, then everything fades out. time is in ticks.
     */
    private static void draw(GuiGraphicsExtractor g, Minecraft mc, float time) {
        float fade = Math.min(1, time / 8f) * Math.min(1, (DURATION - time) / 20f);
        if (fade <= 0.05f) return;
        // Items cannot take alpha: they grow in and shrink out with the card instead (owner round 5: they popped out).
        float itemFade = fade * fade * (3 - 2 * fade);
        var font = mc.font;
        int width = Math.min(220, g.guiWidth() - 12), height = 80, x = (g.guiWidth() - width) / 2, cx = g.guiWidth() / 2;
        int y = g.guiHeight() - 74 - height + Math.round((1 - Math.min(1, time / 8f)) * 6);
        band(g, x, y, width, height, fade);
        int text = AuthUi.alpha(AuthUi.TEXT, fade), muted = AuthUi.alpha(AuthUi.MUTED, fade);
        g.centeredText(font, Component.translatable("holylois.reward.title"), cx, y + 6, AuthUi.alpha(AuthUi.GOLD, fade));
        int day = (Math.max(1, current.streak()) - 1) % 7 + 1;
        int small = 12, big = 22, gap = 7, rowW = 6 * (small + gap) + big, rowX = cx - rowW / 2, mid = y + 31;
        for (int i = 0; i < 7; i++) {
            boolean last = i == 6, done = i < day - 1, today = i == day - 1;
            int size = last ? big : small, sx = rowX + i * (small + gap), sy = mid - size / 2, scx = sx + size / 2;
            int ring = done || today ? AuthUi.GOLD : AuthUi.LINE_STRONG;
            AuthUi.box(g, sx, sy, size, size, AuthUi.alpha(done ? AuthUi.GOLD_SOFT : AuthUi.CANVAS, fade * 0.85f),
                AuthUi.alpha(ring, fade * (done || today || last ? 1 : 0.6f)));
            if (last) {
                float bob = today ? 0 : (float) Math.sin(time * 0.15f) * 0.6f, grow = 1;
                if (today && time > 10) { float pop = Math.min(1, (time - 10) / 6f); grow = 1 + 0.5f * (1 - pop) * (1 - pop); }
                g.pose().pushMatrix();
                g.pose().translate(scx, mid + bob);
                g.pose().scale(grow * itemFade, grow * itemFade);
                g.item(new ItemStack(lootboxItem()), -8, -8);
                g.pose().popMatrix();
                continue;
            }
            if (done) star(g, scx, mid, AuthUi.alpha(AuthUi.GOLD, fade));
            else if (!today) g.fill(scx - 1, mid - 1, scx + 1, mid + 1, AuthUi.alpha(AuthUi.LINE_STRONG, fade));
            if (today && time > 10) {
                float pop = Math.min(1, (time - 10) / 6f), scale = 1 + 1.6f * (1 - pop) * (1 - pop);
                g.pose().pushMatrix();
                g.pose().translate(scx, mid);
                g.pose().scale(scale, scale);
                star(g, 0, 0, AuthUi.alpha(AuthUi.mix(0xFFFFFFFF, AuthUi.GOLD, pop), fade * pop));
                g.pose().popMatrix();
            }
        }
        String lootbox = Component.translatable("holylois.reward.lootbox").getString();
        g.pose().pushMatrix();
        g.pose().translate(rowX + 6 * (small + gap) + big / 2f, mid + big / 2f + 3);
        g.pose().scale(0.5f, 0.5f);
        g.centeredText(font, lootbox, 0, 0, AuthUi.alpha(day == 7 ? AuthUi.GOLD : AuthUi.MUTED, fade));
        g.pose().popMatrix();
        // Today's reward pops in after the star has landed.
        var id = Identifier.tryParse(current.item());
        int lineY = y + 53;
        if (id != null && current.count() > 0 && BuiltInRegistries.ITEM.containsKey(id)) {
            var item = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            String name = font.plainSubstrByWidth(day == 7 ? lootbox : current.count() + " " + item.getHoverName().getString(), width - 40);
            int group = 20 + font.width(name), gx = cx - group / 2;
            if (time > 16) {
                float pop = Math.min(1, (time - 16) / 5f), scale = (0.4f + 0.6f * pop + 0.25f * (float) Math.sin(pop * Math.PI)) * itemFade;
                g.pose().pushMatrix();
                g.pose().translate(gx + 8, lineY + 4);
                g.pose().scale(scale, scale);
                g.item(item, -8, -8);
                g.pose().popMatrix();
            }
            g.text(font, name, gx + 20, lineY, text, true);
        } else g.centeredText(font, Component.translatable("holylois.reward.coins", current.coins()), cx, lineY, text);
        Component footer;
        int footerColor = AuthUi.alpha(AuthUi.SUCCESS, fade);
        if (current.waiting() > 0) footer = Component.translatable("holylois.reward.waiting", current.waiting());
        else if (current.coins() > 0 && current.count() > 0) footer = Component.translatable("holylois.reward.coins", current.coins());
        else if (day < 7) { footer = Component.translatable("holylois.reward.next", 7 - day); footerColor = muted; }
        else { footer = Component.translatable("holylois.reward.week"); footerColor = AuthUi.alpha(AuthUi.GOLD, fade); }
        g.centeredText(font, Component.translatable("holylois.reward.streak", day).getString() + "  ·  " + footer.getString(), cx, y + 67, footerColor);
    }

    private static Item lootboxItem() {
        var lootbox = Identifier.fromNamespaceAndPath("holylois", "holy_lootbox");
        if (BuiltInRegistries.ITEM.containsKey(lootbox)) return BuiltInRegistries.ITEM.getValue(lootbox);
        var present = Identifier.fromNamespaceAndPath("mcwholidays", "yellow_present");
        return BuiltInRegistries.ITEM.containsKey(present) ? BuiltInRegistries.ITEM.getValue(present) : Items.CHEST;
    }

    /** A dark band like the cinematic bars: strongest in the middle, fading out to the sides and softer at top and bottom. */
    private static void band(GuiGraphicsExtractor g, int x, int y, int width, int height, float fade) {
        int pad = 30, left = x - pad, total = width + 2 * pad;
        for (int col = 0; col < total; col += 2) {
            float edge = Math.min(1, Math.min(col, total - col) / (float) pad), a = 0.62f * fade * edge * edge;
            int solid = AuthUi.alpha(0xFF000000, a), soft = AuthUi.alpha(0xFF000000, a * 0.5f);
            g.fill(left + col, y, left + col + 2, y + 3, soft);
            g.fill(left + col, y + 3, left + col + 2, y + height - 3, solid);
            g.fill(left + col, y + height - 3, left + col + 2, y + height, soft);
        }
    }

    /** A four-point sparkle drawn in pixels, exactly centred on (cx, cy). */
    private static void star(GuiGraphicsExtractor g, int cx, int cy, int color) {
        g.fill(cx - 1, cy - 4, cx + 1, cy + 4, color);
        g.fill(cx - 4, cy - 1, cx + 4, cy + 1, color);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, color);
    }
}
