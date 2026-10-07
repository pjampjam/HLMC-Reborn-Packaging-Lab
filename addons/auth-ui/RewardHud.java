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
                g.fill(x, y, x + width, y + 65, 0xDE20201A);
                g.fill(x, y, x + 2, y + 65, 0xFFFFD966);
                g.text(mc.font, Component.translatable("holylois.reward.title"), x + 7, y + 6, 0xFFFFD966);
                var id = Identifier.tryParse(current.item());
                if (id != null && current.count() > 0 && BuiltInRegistries.ITEM.containsKey(id)) {
                    var item = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
                    g.item(item, x + 7, y + 21);
                    String name = current.count() + " " + item.getHoverName().getString();
                    g.text(mc.font, mc.font.plainSubstrByWidth(name, width - 34), x + 29, y + 24, 0xFFF1F0E9);
                } else g.text(mc.font, Component.translatable("holylois.reward.coins", current.coins()), x + 7, y + 24, 0xFFF1F0E9);
                int day = (Math.max(1, current.streak()) - 1) % 7 + 1;
                g.text(mc.font, Component.translatable("holylois.reward.streak", day), x + 7, y + 43, 0xFFBFBCAF);
                for (int i = 0; i < 7; i++) g.fill(x + width - 67 + i * 8, y + 43, x + width - 62 + i * 8, y + 48, i < day ? 0xFFFFD966 : 0xFF55544A);
                if (current.waiting() > 0) g.text(mc.font, Component.translatable("holylois.reward.waiting", current.waiting()), x + 7, y + 54, 0xFF91DCA4);
                else if (current.coins() > 0 && current.count() > 0) g.text(mc.font, Component.translatable("holylois.reward.coins", current.coins()), x + 7, y + 54, 0xFF91DCA4);
                else g.text(mc.font, Component.translatable("holylois.reward.next", 7 - day), x + 7, y + 54, 0xFFBFBCAF);
            }
            var voice = VoiceRecovery.notice();
            if (voice != null) g.text(mc.font, voice, Math.max(6, g.guiWidth() - mc.font.width(voice) - 6), g.guiHeight() - 15, 0xFFD6B78E);
        });
    }
}
