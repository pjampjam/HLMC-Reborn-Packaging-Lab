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
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            Boombox.stopAll();
            Boombox.voice = null;
        });
    }
}
