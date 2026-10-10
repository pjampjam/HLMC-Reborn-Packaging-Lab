package holylois.boombox;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

/** Simple Voice Chat entrypoint: gives the boombox its own volume slider in the voice chat settings. */
public final class BoomboxPlugin implements VoicechatPlugin {
    static final String CATEGORY = "boombox";

    @Override public String getPluginId() { return "holylois_boombox"; }

    @Override public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            var api = event.getVoicechat();
            api.registerVolumeCategory(api.volumeCategoryBuilder().setId(CATEGORY).setName("Boombox")
                .setDescription("Music from boomboxes nearby, held or placed").build());
            Boombox.voice = api;
        });
        // Client: loudness of each boombox heard nearby, for the pulsing speaker cones (BoomboxPulse).
        registration.registerEvent(de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent.LocationalSound.class, event -> {
            var at = event.getPosition();
            BoomboxPulse.heard(at.getX(), at.getY(), at.getZ(), event.getRawAudio());
        });
        // A held boombox streams on its own random channel; a player's voice comes on their own id and must not pump the cones.
        registration.registerEvent(de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent.EntitySound.class, event -> {
            if (!event.getId().equals(event.getEntityId())) BoomboxPulse.heardHeld(event.getEntityId(), event.getRawAudio());
        });
        // Your own held boombox: voice chat plays it to you as a plain (static) sound under the channel's random id, not as an
        // entity sound, so without this your own cones and notes never moved. Group voices use player ids and are skipped.
        registration.registerEvent(de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent.StaticSound.class, event -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            var me = mc.player;
            if (me == null || mc.level == null || mc.level.getPlayerByUUID(event.getId()) != null) return;
            if (me.getMainHandItem().is(Boombox.ITEM) || me.getOffhandItem().is(Boombox.ITEM)) BoomboxPulse.heardHeld(me.getUUID(), event.getRawAudio());
        });
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            Boombox.stopAll();
            Boombox.voice = null;
        });
    }
}
