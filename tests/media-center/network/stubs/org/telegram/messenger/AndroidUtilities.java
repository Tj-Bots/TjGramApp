package org.telegram.messenger;
/** Test-only callback dispatch; does not simulate the Android UI looper. */
public final class AndroidUtilities {
    public static void runOnUIThread(Runnable runnable) { runnable.run(); }
}
