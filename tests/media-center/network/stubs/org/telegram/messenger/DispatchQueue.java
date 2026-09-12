package org.telegram.messenger;
public final class DispatchQueue {
    private final java.util.concurrent.ExecutorService executor;
    public DispatchQueue(String name) {
        executor = java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
    }
    public void postRunnable(Runnable runnable) { executor.execute(runnable); }
}
