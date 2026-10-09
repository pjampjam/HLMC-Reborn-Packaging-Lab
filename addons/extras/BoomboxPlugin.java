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
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            Boombox.stopAll();
            Boombox.voice = null;
        });
    }
}
