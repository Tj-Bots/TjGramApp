package org.telegram.messenger;
/** Deterministic single-thread callbacks; not an Android Looper simulation. */
public final class AndroidUtilities {
    public static final java.util.List<Runnable> delayed = new java.util.ArrayList<>();
    public static void runOnUIThread(Runnable runnable) { runnable.run(); }
    public static void runOnUIThread(Runnable runnable, long delay) { delayed.add(runnable); }
    public static void cancelRunOnUIThread(Runnable runnable) { delayed.remove(runnable); }
}
