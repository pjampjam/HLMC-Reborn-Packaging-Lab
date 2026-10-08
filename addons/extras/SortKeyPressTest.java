package holylois.boombox;

public final class SortKeyPressTest {
    private static void check(boolean v,String label){if(!v)throw new IllegalStateException(label);}
    public static void main(String[] args){
        Object chest=new Object();SortKeyPress.begin(82,1,chest);SortKeyPress.sorted(chest);SortKeyPress.end();
        check(SortKeyPress.consumes(82,chest),"the press stays consumed after sorting moves an item under the cursor");
        SortKeyPress.begin(82,2,chest);check(SortKeyPress.consumes(82,chest),"held-key repeat does not open a recipe");SortKeyPress.end();
        check(!SortKeyPress.consumes(85,chest),"other recipe/use keys remain available");check(!SortKeyPress.consumes(82,new Object()),"screen changes do not inherit a consumed press");
        SortKeyPress.begin(82,0,chest);check(!SortKeyPress.consumes(82,chest),"release clears consumption");
        SortKeyPress.begin(82,1,chest);SortKeyPress.end();check(!SortKeyPress.consumes(82,chest),"a fresh press over an item can show its recipe");
        SortKeyPress.sorted(chest);check(!SortKeyPress.consumes(82,chest),"a mouse sort outside keyboard dispatch cannot consume R");
        SortKeyPress.begin(82,1,chest);SortKeyPress.sorted(chest);SortKeyPress.end();
        SortKeyPress.begin(85,0,chest);SortKeyPress.end();check(SortKeyPress.consumes(82,chest),"releasing another key does not release the sort key");
        SortKeyPress.begin(82,0,chest);SortKeyPress.end();
        System.out.println("Sort press consumption, repeat, release, other-key and screen guards pass.");
    }
}
