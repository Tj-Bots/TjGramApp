package org.telegram.messenger;
public final class NotificationCenter {
    public static final int tjAccountOrderChanged = 1;
    public static int notifications;
    public static NotificationCenter getInstance(int account) { return new NotificationCenter(); }
    public void postNotificationName(int event) { notifications++; }
}
