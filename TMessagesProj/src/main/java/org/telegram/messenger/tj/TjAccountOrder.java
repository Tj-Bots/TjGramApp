package org.telegram.messenger.tj;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared drawer order, keyed by user identity rather than reusable account slots. */
public final class TjAccountOrder {
    private static final String KEY = "tj_account_order_v1";

    private TjAccountOrder() { }

    public static ArrayList<Integer> activeAccounts() {
        ArrayList<Integer> result = new ArrayList<>();
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated()) result.add(account);
        }
        sort(result);
        return result;
    }

    /** Sort only; callers may intentionally exclude the current account. */
    public static void sort(List<Integer> accounts) {
        Map<Long, Integer> ranks = new HashMap<>();
        String saved = MessagesController.getGlobalMainSettings().getString(KEY, "");
        if (saved != null) {
            String[] ids = saved.split(",");
            for (int i = 0; i < ids.length; i++) {
                try {
                    long id = Long.parseLong(ids[i]);
                    if (id > 0 && !ranks.containsKey(id)) ranks.put(id, i);
                } catch (NumberFormatException ignored) { }
            }
        }
        accounts.sort((a, b) -> {
            int first = ranks.getOrDefault(UserConfig.getInstance(a).getClientUserId(), Integer.MAX_VALUE);
            int second = ranks.getOrDefault(UserConfig.getInstance(b).getClientUserId(), Integer.MAX_VALUE);
            return first != second ? Integer.compare(first, second) : Integer.compare(a, b);
        });
    }

    /** Called only after a complete drawer reorder, never from a filtered selector. */
    public static void save(List<Integer> accounts) {
        StringBuilder order = new StringBuilder();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        for (int account : accounts) {
            UserConfig config = UserConfig.getInstance(account);
            long id = config.getClientUserId();
            if (!config.isClientActivated() || id <= 0 || !seen.add(id)) continue;
            if (order.length() > 0) order.append(',');
            order.append(id);
        }
        MessagesController.getGlobalMainSettings().edit().putString(KEY, order.toString()).apply();
        for (int account : activeAccounts()) org.telegram.messenger.NotificationCenter.getInstance(account)
                .postNotificationName(org.telegram.messenger.NotificationCenter.tjAccountOrderChanged);
    }
}
