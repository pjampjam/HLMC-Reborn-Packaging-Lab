package holylois.boombox;

/** Consumption belongs to a physical press, including repeats, rather than an elapsed-time window. */
public final class SortKeyPress {
    private static int active=-1,consumed=-1;private static Object screen;
    public static void begin(int key,int action,Object current){
        if(action==0){if(consumed==key){consumed=-1;screen=null;}active=-1;return;}
        active=key;if(action==1 && consumed==key){consumed=-1;screen=null;}
    }
    public static void end(){active=-1;}
    /** The slot under the cursor when the keyboard sort ran, and when: on a server the sort's clicks land a little later, so the
     *  recipe key stays swallowed for that slot until the cursor moves to another slot or two seconds pass (owner 2026-10-10). */
    private static Object sortedSlot,sortedScreen;private static long sortedAt;private static int sortedKey=-1;
    public static void sorted(Object current){
        if(active>=0){consumed=active;screen=current;}
        if(active<0)return;
        sortedKey=active;sortedScreen=current;sortedAt=net.minecraft.util.Util.getMillis();
        sortedSlot=current instanceof holylois.boombox.mixins.ContainerHoverAccessor hover?hover.holyLoisHoveredSlot():null;
    }
    public static boolean consumes(int key,Object current){
        if(key==consumed && screen==current)return true;
        if(key!=sortedKey || sortedScreen!=current || net.minecraft.util.Util.getMillis()-sortedAt>2000)return false;
        Object hovered=current instanceof holylois.boombox.mixins.ContainerHoverAccessor hover?hover.holyLoisHoveredSlot():null;
        return hovered!=null && hovered==sortedSlot;
    }
}
