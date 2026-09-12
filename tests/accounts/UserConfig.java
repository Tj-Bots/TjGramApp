package org.telegram.messenger;
public final class UserConfig {
    public static final int MAX_ACCOUNT_COUNT = 4;
    static final UserConfig[] users = {new UserConfig(),new UserConfig(),new UserConfig(),new UserConfig()};
    public long owner; public boolean active;
    public static UserConfig getInstance(int slot) { return users[slot]; }
    public boolean isClientActivated() { return active; }
    public long getClientUserId() { return owner; }
}
