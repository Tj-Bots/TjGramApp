package org.telegram.messenger;
public class UserConfig {
    private static final java.util.Map<Integer, Long> owners = new java.util.HashMap<>();
    public static void setOwner(int account, long owner) { owners.put(account, owner); }
    public static UserConfig getInstance(int account) { return new UserConfig(account); }
    private final int account;
    UserConfig(int account) { this.account = account; }
    public long getClientUserId() { return owners.getOrDefault(account, 100L + account); }
    public boolean isClientActivated() { return getClientUserId() > 0; }
}
