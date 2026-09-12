package org.telegram.messenger;
import java.util.*;
public class AndroidUtilities {
    public static final List<Runnable> queued = new ArrayList<>();
    public static void runOnUIThread(Runnable action, long delay) { queued.add(action); }
    public static void cancelRunOnUIThread(Runnable action) { queued.removeIf(item -> item == action); }
    public static void drain() {
        for (Runnable action : new ArrayList<>(queued)) { queued.remove(action); action.run(); }
    }
}
