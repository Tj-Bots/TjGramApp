package org.telegram.messenger;
import java.util.*;
public class NotificationCenter {
    public static final int dialogFiltersUpdated=1, dialogsNeedReload=2, contactsDidLoad=3, updateInterfaces=4;
    public interface NotificationCenterDelegate { void didReceivedNotification(int id, int account, Object... args); }
    private static final Map<Integer, NotificationCenter> instances = new HashMap<>();
    public static NotificationCenter getInstance(int account) { return instances.computeIfAbsent(account, ignored -> new NotificationCenter()); }
    public final Map<Integer, Set<NotificationCenterDelegate>> listeners = new HashMap<>();
    public void addObserver(NotificationCenterDelegate observer, int id) { listeners.computeIfAbsent(id, ignored -> new HashSet<>()).add(observer); }
    public void removeObserver(NotificationCenterDelegate observer, int id) { if (listeners.containsKey(id)) listeners.get(id).remove(observer); }
    public int size() { return listeners.values().stream().mapToInt(Set::size).sum(); }
    public void post(int id, int account) {
        for (NotificationCenterDelegate observer : new HashSet<>(listeners.getOrDefault(id, Collections.emptySet()))) observer.didReceivedNotification(id, account);
    }
}
