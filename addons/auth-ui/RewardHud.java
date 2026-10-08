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
    static void register() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(RewardNotice.TYPE, RewardNotice.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(RewardNotice.TYPE, (v, ctx) -> { if (queue.size() < 4) queue.addLast(v); });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> { queue.clear(); current = null; });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.gui.screen() != null || HolyLoisAuthClient.muteWorldAudio()) return;
            if (current == null) { current = queue.pollFirst(); age = 0; }
            if (current != null && ++age > 160) current = null;
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Identifier.fromNamespaceAndPath("holylois", "reward_notice"), (g, delta) -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.gui.screen() != null || mc.gui.hud.isHidden() || HolyLoisAuthClient.muteWorldAudio()) return;
            int width = Math.min(216, g.guiWidth() - 12), x = g.guiWidth() - width - 6, y = g.guiHeight() - 114;
            if (current != null) {
                AuthUi.card(g, x, y, width, 65, AuthUi.GOLD, 1);
                g.text(mc.font, Component.translatable("holylois.reward.title"), x + 9, y + 6, AuthUi.GOLD, false);
                var id = Identifier.tryParse(current.item());
                if (id != null && current.count() > 0 && BuiltInRegistries.ITEM.containsKey(id)) {
                    var item = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
                    g.item(item, x + 9, y + 20);
                    String name = current.count() + " " + item.getHoverName().getString();
                    g.text(mc.font, mc.font.plainSubstrByWidth(name, width - 36), x + 30, y + 24, AuthUi.TEXT, false);
                } else g.text(mc.font, Component.translatable("holylois.reward.coins", current.coins()), x + 9, y + 24, AuthUi.TEXT, false);
                int day = (Math.max(1, current.streak()) - 1) % 7 + 1;
                g.text(mc.font, Component.translatable("holylois.reward.streak", day), x + 9, y + 42, AuthUi.MUTED, false);
                for (int i = 0; i < 7; i++) AuthUi.box(g, x + width - 69 + i * 8, y + 43, 6, 6, i < day ? AuthUi.GOLD : AuthUi.LINE, 0);
                if (current.waiting() > 0) g.text(mc.font, Component.translatable("holylois.reward.waiting", current.waiting()), x + 9, y + 53, AuthUi.SUCCESS, false);
                else if (current.coins() > 0 && current.count() > 0) g.text(mc.font, Component.translatable("holylois.reward.coins", current.coins()), x + 9, y + 53, AuthUi.SUCCESS, false);
                else g.text(mc.font, Component.translatable("holylois.reward.next", 7 - day), x + 9, y + 53, AuthUi.MUTED, false);
            }
            var voice = VoiceRecovery.notice();
            if (voice != null) g.text(mc.font, voice, Math.max(6, g.guiWidth() - mc.font.width(voice) - 6), g.guiHeight() - 15, AuthUi.FOOD);
        });
    }
}
