package holylois.auth;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.TooltipDisplay;

public final class HolyLoisAuthClient implements ClientModInitializer {
    static AuthStatus status;
    public static boolean muteWorldAudio() {
        return status != null && AuthPolicy.quietWorldAudio(status.mode());
    }
    public static boolean isHolyLois(Minecraft client) {
        var server = client.getCurrentServer();
        return status != null || server != null &&
            (server.ip.equalsIgnoreCase("79.76.40.155:25565") || server.ip.equals("79.76.40.155"));
    }
    @Override public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipFlag, lines) -> {
            if (!stack.isDamageableItem() || lines.isEmpty()) return;
            if (!stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).shows(DataComponents.DAMAGE)) return;
            // Vanilla already provides this line for damaged items with advanced tooltips.
            if (tooltipFlag.isAdvanced() && stack.isDamaged()) return;
            int maximum = stack.getMaxDamage();
            int remaining = Math.max(0, maximum - stack.getDamageValue());
            lines.add(Component.translatable("item.durability", remaining, maximum).withStyle(ChatFormatting.GRAY));
        });
        PayloadTypeRegistry.clientboundPlay().register(AuthStatus.TYPE, AuthStatus.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(AuthStatus.TYPE, (payload, context) -> {
            boolean wasMuted = muteWorldAudio();
            status = payload;
            var client = context.client();
            if (!wasMuted && muteWorldAudio()) {
                for (var category : net.minecraft.sounds.SoundSource.values())
                    if (category != net.minecraft.sounds.SoundSource.MASTER) client.getSoundManager().stop(null, category);
            }
            if (payload.mode() == 0) {
                if (client.gui.screen() instanceof HolyLoisAuthScreen) client.gui.setScreen(null);
            } else if (payload.mode() >= 1 && payload.mode() <= 3) {
                if (client.gui.screen() instanceof HolyLoisAuthScreen screen) screen.update(payload);
                else client.gui.setScreen(new HolyLoisAuthScreen(payload));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> status = null);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            var client = Minecraft.getInstance();
            if (isHolyLois(client) && AuthPolicy.routineAuthNotice(message.getString())) return false;
            if (client.gui.screen() instanceof HolyLoisAuthScreen screen && status != null && (status.mode() == 1 || status.mode() == 2)) {
                screen.feedback(message.getString());
                return false;
            }
            return true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (isHolyLois(client))
                net.minecraft.client.gui.components.toasts.SystemToast.forceHide(client.gui.toastManager(),
                    net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.UNSECURE_SERVER_WARNING);
            if (status != null && status.mode() > 0 && client.getConnection() != null && client.player != null
                && !(client.gui.screen() instanceof HolyLoisAuthScreen))
                client.gui.setScreen(new HolyLoisAuthScreen(status));
        });
    }
}
