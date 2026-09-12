package org.telegram.messenger;
public final class UserConfig {
    public static final int MAX_ACCOUNT_COUNT = 3;
    private static final UserConfig[] configs = {new UserConfig(), new UserConfig(), new UserConfig()};
    public long owner;
    public boolean active;
    public static UserConfig getInstance(int account) { return configs[account]; }
    public boolean isClientActivated() { return active; }
    public long getClientUserId() { return owner; }
}
