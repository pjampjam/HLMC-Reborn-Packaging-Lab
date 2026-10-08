package holylois.boombox;

/** Consumption belongs to a physical press, including repeats, rather than an elapsed-time window. */
public final class SortKeyPress {
    private static int active=-1,consumed=-1;private static Object screen;
    public static void begin(int key,int action,Object current){
        if(action==0){if(consumed==key){consumed=-1;screen=null;}active=-1;return;}
        active=key;if(action==1 && consumed==key){consumed=-1;screen=null;}
    }
    public static void end(){active=-1;}
    public static void sorted(Object current){if(active>=0){consumed=active;screen=current;}}
    public static boolean consumes(int key,Object current){return key==consumed && screen==current;}
}
