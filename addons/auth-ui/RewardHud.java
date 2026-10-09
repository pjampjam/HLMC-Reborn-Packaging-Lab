package holylois.auth;

import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

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
     * Centred above the hotbar: fades and slides in, a star pops into today's slot of the week, the reward item pops after it,
     * then everything fades out. time is in ticks since the card appeared.
     */
    private static void draw(net.minecraft.client.gui.GuiGraphicsExtractor g, Minecraft mc, float time) {
        float fade = Math.min(1, time / 8f) * Math.min(1, (DURATION - time) / 20f);
        if (fade <= 0.05f) return;
        var font = mc.font;
        int width = Math.min(200, g.guiWidth() - 12), height = 74, x = (g.guiWidth() - width) / 2;
        int y = g.guiHeight() - 76 - height + Math.round((1 - Math.min(1, time / 8f)) * 6);
        AuthUi.box(g, x, y, width, height, AuthUi.alpha(AuthUi.SURFACE, 0.9f * fade), AuthUi.alpha(AuthUi.LINE, fade));
        g.fill(x + width / 2 - 16, y, x + width / 2 + 16, y + 1, AuthUi.alpha(AuthUi.GOLD, fade));
        int text = AuthUi.alpha(AuthUi.TEXT, fade), muted = AuthUi.alpha(AuthUi.MUTED, fade), gold = AuthUi.alpha(AuthUi.GOLD, fade);
        g.centeredText(font, Component.translatable("holylois.reward.title"), x + width / 2, y + 7, gold);
        int day = (Math.max(1, current.streak()) - 1) % 7 + 1;
        // Week row: past days filled, today's slot gets the star.
        int slot = 14, step = 18, rowX = x + (width - (7 * step - 4)) / 2, rowY = y + 20;
        for (int i = 0; i < 7; i++) {
            boolean done = i < day - 1, today = i == day - 1;
            AuthUi.box(g, rowX + i * step, rowY, slot, slot, AuthUi.alpha(done ? AuthUi.GOLD_SOFT : AuthUi.CONTROL, fade),
                AuthUi.alpha(done || today ? AuthUi.GOLD : AuthUi.LINE, fade));
            if (done) g.centeredText(font, "✦", rowX + i * step + slot / 2 + 1, rowY + 3, AuthUi.alpha(AuthUi.GOLD, fade * 0.7f));
            if (today && time > 10) {
                float pop = Math.min(1, (time - 10) / 6f), scale = 1 + 1.6f * (1 - pop) * (1 - pop);
                g.pose().pushMatrix();
                g.pose().translate(rowX + i * step + slot / 2f + 1, rowY + slot / 2f);
                g.pose().scale(scale, scale);
                g.centeredText(font, "✦", 0, -4, AuthUi.alpha(AuthUi.GOLD, fade * pop));
                g.pose().popMatrix();
            }
        }
        // The reward itself pops in after the star has landed.
        var id = Identifier.tryParse(current.item());
        int lineY = y + 42;
        if (id != null && current.count() > 0 && BuiltInRegistries.ITEM.containsKey(id)) {
            var item = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            String name = font.plainSubstrByWidth(current.count() + " " + item.getHoverName().getString(), width - 40);
            int group = 20 + font.width(name), gx = x + (width - group) / 2;
            if (time > 16 && fade > 0.5f) {
                float pop = Math.min(1, (time - 16) / 5f), scale = 0.4f + 0.6f * pop + 0.25f * (float) Math.sin(pop * Math.PI);
                g.pose().pushMatrix();
                g.pose().translate(gx + 8, lineY + 4);
                g.pose().scale(scale, scale);
                g.item(item, -8, -8);
                g.pose().popMatrix();
            }
            g.text(font, name, gx + 20, lineY, text, false);
        } else g.centeredText(font, Component.translatable("holylois.reward.coins", current.coins()), x + width / 2, lineY, text);
        Component footer;
        int footerColor = AuthUi.alpha(AuthUi.SUCCESS, fade);
        if (current.waiting() > 0) footer = Component.translatable("holylois.reward.waiting", current.waiting());
        else if (current.coins() > 0 && current.count() > 0) footer = Component.translatable("holylois.reward.coins", current.coins());
        else { footer = Component.translatable("holylois.reward.next", 7 - day); footerColor = muted; }
        g.centeredText(font, Component.translatable("holylois.reward.streak", day).getString() + "  ·  " + footer.getString(), x + width / 2, y + 60,
            footerColor);
    }
}
