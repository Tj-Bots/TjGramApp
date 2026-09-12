package org.telegram.messenger;
public final class UserConfig {
    private static final UserConfig instance = new UserConfig();
    public volatile long owner = 10;
    public volatile boolean active = true;
    public static UserConfig getInstance(int account) { return instance; }
    public boolean isClientActivated() { return active; }
    public long getClientUserId() { return owner; }
}
