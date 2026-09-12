package org.telegram.messenger;
/** Deterministic single-thread callbacks; not an Android Looper simulation. */
public final class AndroidUtilities {
    public static void runOnUIThread(Runnable runnable) { runnable.run(); }
}
