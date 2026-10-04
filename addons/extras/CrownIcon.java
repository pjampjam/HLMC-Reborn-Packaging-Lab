package holylois.boombox;

import net.minecraft.server.packs.resources.IoSupplier;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** The Holy Lois crown as the game window icon (used by WindowIconMixin); null keeps the vanilla icon if a size is missing. */
public final class CrownIcon {
    public static final String TITLE = "Holy Lois: Reborn";

    public static List<IoSupplier<InputStream>> sizes() {
        var icons = new ArrayList<IoSupplier<InputStream>>();
        for (int size : new int[] {16, 24, 32, 48, 64, 128, 256}) {
            String path = "/assets/holylois/icons/icon_" + size + "x" + size + ".png";
            if (CrownIcon.class.getResource(path) == null) return null;
            icons.add(() -> CrownIcon.class.getResourceAsStream(path));
        }
        return icons;
    }
}
